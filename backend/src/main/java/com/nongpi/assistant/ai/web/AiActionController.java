package com.nongpi.assistant.ai.web;

import com.nongpi.assistant.ai.dto.AiActionRequest;
import com.nongpi.assistant.ai.dto.AiActionResponse;
import com.nongpi.assistant.ai.dto.AiTranscribeResponse;
import com.nongpi.assistant.ai.service.AiActionService;
import com.nongpi.assistant.ai.service.AiSpeechService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ai")
public class AiActionController {

    private final AiActionService aiActionService;
    private final AiSpeechService aiSpeechService;

    public AiActionController(AiActionService aiActionService, AiSpeechService aiSpeechService) {
        this.aiActionService = aiActionService;
        this.aiSpeechService = aiSpeechService;
    }

    @PostMapping("/actions")
    public AiActionResponse actions(@Valid @RequestBody AiActionRequest request) {
        return aiActionService.handle(request);
    }

    @PostMapping(value = "/speech/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AiTranscribeResponse transcribe(@RequestPart("file") MultipartFile file) {
        return aiSpeechService.transcribe(file);
    }
}
