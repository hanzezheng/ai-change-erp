package com.nongpi.assistant.saas.auth.web;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SwitchTenantRequest(@NotNull UUID tenantId) {
}
