package com.nongpi.assistant.saas.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nongpi.assistant.common.error.BusinessErrorCode;
import com.nongpi.assistant.common.error.BusinessException;
import com.nongpi.assistant.erp.client.ErpWriteOutcomeUnknownException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

@Service
public class IdempotencyService {

    public static final String CREATE_ORDER = "CREATE_ORDER";
    public static final String CREATE_PAYMENT = "CREATE_PAYMENT";

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate requiresNew;

    public IdempotencyService(IdempotencyRecordRepository repository,
                              ObjectMapper objectMapper,
                              PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public String hash(Object request) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(request);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("无法计算幂等请求哈希", ex);
        }
    }

    public void requireKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST, "必须提供 Idempotency-Key");
        }
        if (idempotencyKey.getBytes(StandardCharsets.UTF_8).length > 128) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST, "Idempotency-Key 过长");
        }
    }

    public IdempotencyRecordEntity begin(UUID tenantId, String operation, String key, String requestHash) {
        requireKey(key);
        try {
            return requiresNew.execute(status ->
                    repository.saveAndFlush(new IdempotencyRecordEntity(tenantId, operation, key, requestHash)));
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            IdempotencyRecordEntity existing = requiresNew.execute(status -> repository
                    .findByTenantIdAndOperationAndIdempotencyKey(tenantId, operation, key)
                    .orElse(null));
            if (existing == null) {
                throw new BusinessException(BusinessErrorCode.INTERNAL_ERROR, "幂等记录冲突但无法读取");
            }
            return interpretExisting(existing, requestHash);
        }
    }

    public <T> T executeWrite(IdempotencyRecordEntity record,
                              Supplier<String> mutation,
                              Function<String, T> afterCommit) {
        String resourceId;
        try {
            resourceId = mutation.get();
        } catch (RuntimeException ex) {
            if (isUnknownOutcome(ex)) {
                markUnknown(record.getId());
                throw new BusinessException(BusinessErrorCode.IDEMPOTENCY_OUTCOME_UNKNOWN,
                        BusinessErrorCode.IDEMPOTENCY_OUTCOME_UNKNOWN.defaultMessage());
            }
            abandon(record.getId());
            throw ex;
        }
        if (resourceId == null || resourceId.isBlank()) {
            markUnknown(record.getId());
            throw new BusinessException(BusinessErrorCode.IDEMPOTENCY_OUTCOME_UNKNOWN,
                    BusinessErrorCode.IDEMPOTENCY_OUTCOME_UNKNOWN.defaultMessage());
        }
        succeed(record.getId(), resourceId);
        try {
            return afterCommit.apply(resourceId);
        } catch (RuntimeException ex) {
            // ERP create 已成功并固化 SUCCEEDED(resourceId)。enrichment / detail 失败不得删除幂等记录。
            throw ex;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(UUID id, String resourceId) {
        IdempotencyRecordEntity entity = repository.findById(id).orElseThrow();
        entity.markSucceeded(resourceId);
        repository.save(entity);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markUnknown(UUID id) {
        repository.findById(id).ifPresent(entity -> {
            entity.markUnknown();
            repository.save(entity);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void abandon(UUID id) {
        repository.deleteById(id);
    }

    private IdempotencyRecordEntity interpretExisting(IdempotencyRecordEntity existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new BusinessException(BusinessErrorCode.IDEMPOTENCY_CONFLICT);
        }
        if (existing.getStatus() == IdempotencyStatus.SUCCEEDED) {
            return existing;
        }
        if (existing.getStatus() == IdempotencyStatus.UNKNOWN) {
            throw new BusinessException(BusinessErrorCode.IDEMPOTENCY_OUTCOME_UNKNOWN);
        }
        throw new BusinessException(BusinessErrorCode.IDEMPOTENCY_IN_PROGRESS);
    }

    private static boolean isUnknownOutcome(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof ErpWriteOutcomeUnknownException) {
                return true;
            }
            if (current instanceof HttpTimeoutException
                    || current instanceof SocketTimeoutException
                    || current instanceof ResourceAccessException) {
                String message = current.getMessage() == null ? "" : current.getMessage().toLowerCase();
                if (current instanceof ResourceAccessException
                        && (message.contains("connection refused") || message.contains("connect timed out"))) {
                    current = current.getCause();
                    continue;
                }
                if (current instanceof ResourceAccessException) {
                    Throwable cause = current.getCause();
                    if (cause instanceof HttpTimeoutException
                            || cause instanceof SocketTimeoutException
                            || (cause != null && timeoutText(cause))) {
                        return true;
                    }
                    if (timeoutText(current)) {
                        return true;
                    }
                    current = current.getCause();
                    continue;
                }
                return true;
            }
            if (timeoutText(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean timeoutText(Throwable ex) {
        String name = ex.getClass().getName().toLowerCase();
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        return name.contains("timeout") || message.contains("timed out") || message.contains("timeout");
    }
}
