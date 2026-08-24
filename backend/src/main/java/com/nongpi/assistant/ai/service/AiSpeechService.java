package com.nongpi.assistant.ai.service;

import com.nongpi.assistant.ai.client.AiServiceClient;
import com.nongpi.assistant.ai.dto.AiTranscribeResponse;
import com.nongpi.assistant.common.error.BusinessErrorCode;
import com.nongpi.assistant.common.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class AiSpeechService {

    private final AiServiceClient aiServiceClient;

    public AiSpeechService(AiServiceClient aiServiceClient) {
        this.aiServiceClient = aiServiceClient;
    }

    public AiTranscribeResponse transcribe(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST, "音频文件不能为空");
        }
        try {
            AiTranscribeResponse response = aiServiceClient.transcribe(
                    file.getBytes(),
                    file.getOriginalFilename()
            );
            if (response == null || response.text() == null || response.text().isBlank()) {
                throw new BusinessException(BusinessErrorCode.ASR_UNAVAILABLE, "未能识别语音内容");
            }
            return response;
        } catch (IOException ex) {
            throw new BusinessException(
                    BusinessErrorCode.ASR_UNAVAILABLE,
                    "读取音频失败",
                    Map.of(),
                    ex
            );
        }
    }
}
