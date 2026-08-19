package com.nongpi.assistant.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {

    List<AuditLogEntity> findByActionOrderByCreatedAtDesc(String action);

    List<AuditLogEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
