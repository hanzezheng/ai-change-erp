package com.nongpi.assistant.erp.connection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ErpConnectionRepository extends JpaRepository<ErpConnectionEntity, UUID> {

    Optional<ErpConnectionEntity> findByTenant_Id(UUID tenantId);

    @Query("""
            select e from ErpConnectionEntity e
            where lower(e.baseUrl) = lower(:baseUrl)
              and coalesce(lower(e.siteName), '') = coalesce(lower(:siteName), '')
              and e.tenant.id <> :excludeTenantId
            """)
    Optional<ErpConnectionEntity> findConflictingSite(@Param("baseUrl") String baseUrl,
                                                      @Param("siteName") String siteName,
                                                      @Param("excludeTenantId") UUID excludeTenantId);

    @Query("""
            select e from ErpConnectionEntity e
            where lower(e.baseUrl) = lower(:baseUrl)
              and coalesce(lower(e.siteName), '') = coalesce(lower(:siteName), '')
            """)
    Optional<ErpConnectionEntity> findBySite(@Param("baseUrl") String baseUrl,
                                             @Param("siteName") String siteName);
}
