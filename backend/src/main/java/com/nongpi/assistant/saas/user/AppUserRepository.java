package com.nongpi.assistant.saas.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUserEntity, UUID> {

    Optional<AppUserEntity> findByLoginIgnoreCase(String login);

    boolean existsByLoginIgnoreCase(String login);
}
