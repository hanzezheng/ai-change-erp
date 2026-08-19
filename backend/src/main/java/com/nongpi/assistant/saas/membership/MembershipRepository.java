package com.nongpi.assistant.saas.membership;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<MembershipEntity, UUID> {

    @Query("""
            select m from MembershipEntity m
            join fetch m.user
            join fetch m.tenant
            where m.id = :id
            """)
    Optional<MembershipEntity> findWithUserAndTenantById(@Param("id") UUID id);

    @Query("""
            select m from MembershipEntity m
            join fetch m.tenant
            where m.user.id = :userId
            """)
    List<MembershipEntity> findByUserIdWithTenant(@Param("userId") UUID userId);

    @Query("""
            select m from MembershipEntity m
            join fetch m.user
            join fetch m.tenant
            where m.tenant.id = :tenantId and m.user.id = :userId
            """)
    Optional<MembershipEntity> findByTenant_IdAndUser_Id(@Param("tenantId") UUID tenantId,
                                                         @Param("userId") UUID userId);

    List<MembershipEntity> findByTenant_IdOrderByCreatedAtAsc(UUID tenantId);

    @Query("""
            select m from MembershipEntity m
            join fetch m.user
            where m.tenant.id = :tenantId
            order by m.createdAt asc
            """)
    List<MembershipEntity> findByTenantIdWithUser(@Param("tenantId") UUID tenantId);

    boolean existsByTenant_IdAndUser_Id(UUID tenantId, UUID userId);

    long countByTenant_IdAndRoleAndStatus(UUID tenantId, MembershipRole role, MembershipStatus status);
}
