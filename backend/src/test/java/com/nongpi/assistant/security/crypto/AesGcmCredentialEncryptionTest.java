package com.nongpi.assistant.security.crypto;

import com.nongpi.assistant.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ERP 凭据 AES-GCM 加解密")
class AesGcmCredentialEncryptionTest {

    private final AesGcmCredentialEncryptionService service = new AesGcmCredentialEncryptionService(
            new AppProperties(
                    new AppProperties.Jwt("test-jwt-hmac-secret-key-32bytes-min", Duration.ofMinutes(15), Duration.ofDays(30)),
                    new AppProperties.CredentialEncryption("unit-test-master-key")));

    @Test
    @DisplayName("同一明文每次加密密文不同，但都能解密回原文")
    void encryptsWithRandomIv() {
        String first = service.encrypt("erp-api-secret");
        String second = service.encrypt("erp-api-secret");
        assertThat(first).isNotEqualTo(second);
        assertThat(service.decrypt(first)).isEqualTo("erp-api-secret");
        assertThat(service.decrypt(second)).isEqualTo("erp-api-secret");
        assertThat(first).doesNotContain("erp-api-secret");
    }

    @Test
    @DisplayName("损坏的密文不能解密")
    void rejectsTamperedCiphertext() {
        String ciphertext = service.encrypt("erp-api-secret");
        assertThatThrownBy(() -> service.decrypt(ciphertext.substring(0, ciphertext.length() - 4) + "xxxx"))
                .isInstanceOf(IllegalStateException.class);
    }
}
