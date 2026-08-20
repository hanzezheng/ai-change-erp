package com.nongpi.assistant.ai.web;

import com.nongpi.assistant.ai.dto.AiActionRequest;
import com.nongpi.assistant.ai.dto.AiActionResponse;
import com.nongpi.assistant.ai.service.AiActionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiActionController {

    private final AiActionService aiActionService;

    public AiActionController(AiActionService aiActionService) {
        this.aiActionService = aiActionService;
    }

    @PostMapping("/actions")
    public AiActionResponse actions(@Valid @RequestBody AiActionRequest request) {
        return aiActionService.handle(request);
    }
}
