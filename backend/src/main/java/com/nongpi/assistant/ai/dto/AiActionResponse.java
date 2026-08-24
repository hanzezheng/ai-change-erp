package com.nongpi.assistant.ai.dto;

import java.util.List;
import java.util.Map;

public record AiActionResponse(
        String actionId,
        String actionType,
        String status,
        String targetPage,
        Map<String, Object> resolvedEntities,
        List<AmbiguityDto> ambiguities,
        Map<String, Object> payload,
        String asrText,
        String provider,
        String model,
        String message
) {
    public record AmbiguityDto(
            String field,
            String expression,
            List<Map<String, Object>> candidates
    ) {
    }
}
