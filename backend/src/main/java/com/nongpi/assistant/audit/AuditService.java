package com.nongpi.assistant.audit;

import com.nongpi.assistant.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

@Service
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(UUID tenantId,
                       UUID userId,
                       String action,
                       String targetType,
                       String targetId,
                       AuditResult result,
                       Map<String, Object> metadata) {
        Map<String, Object> safeMetadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        AuditLogEntity entity = new AuditLogEntity(
                tenantId,
                userId,
                action,
                targetType,
                targetId,
                result,
                currentTraceId(),
                safeMetadata.isEmpty() ? null : safeMetadata
        );
        auditLogRepository.save(entity);
        log.info("audit action={} result={} tenant={} user={} target={}:{}",
                action, result, tenantId, userId, targetType, targetId);
    }

    public void success(UUID tenantId, UUID userId, String action, String targetType, String targetId,
                        Map<String, Object> metadata) {
        record(tenantId, userId, action, targetType, targetId, AuditResult.SUCCESS, metadata);
    }

    public void failure(UUID tenantId, UUID userId, String action, String targetType, String targetId,
                        Map<String, Object> metadata) {
        record(tenantId, userId, action, targetType, targetId, AuditResult.FAILURE, metadata);
    }

    private String currentTraceId() {
        String mdc = MDC.get("traceId");
        if (mdc != null && !mdc.isBlank()) {
            return mdc;
        }
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return TraceIdFilter.from(request);
        }
        return null;
    }
}
