package com.nongpi.assistant.saas.auth.web;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record LoginRequest(
        @NotBlank String login,
        @NotBlank String password,
        UUID tenantId
) {
}
