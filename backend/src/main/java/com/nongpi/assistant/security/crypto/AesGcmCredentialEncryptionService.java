package com.nongpi.assistant.security.crypto;

import com.nongpi.assistant.config.AppProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM 应用级加密。Master Key 来自 {@code APP_CREDENTIAL_ENCRYPTION_KEY}，
 * 经 SHA-256 派生为 256-bit AES 密钥。密钥不入库、不写日志。
 */
@Service
public class AesGcmCredentialEncryptionService implements CredentialEncryptionService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LENGTH = 12;
    private static final byte VERSION = 1;

    private final SecretKey secretKey;
    private final SecureRandom random = new SecureRandom();

    public AesGcmCredentialEncryptionService(AppProperties properties) {
        String configured = properties.credentialEncryption() == null ? null : properties.credentialEncryption().key();
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("APP_CREDENTIAL_ENCRYPTION_KEY 未配置");
        }
        this.secretKey = new SecretKeySpec(sha256(configured), "AES");
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(1 + iv.length + ciphertext.length);
            buffer.put(VERSION);
            buffer.put(iv);
            buffer.put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("凭据加密失败", ex);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new IllegalStateException("凭据密文为空");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            if (decoded.length < 1 + IV_LENGTH + 16) {
                throw new IllegalStateException("凭据密文格式无效");
            }
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte version = buffer.get();
            if (version != VERSION) {
                throw new IllegalStateException("不支持的凭据密文版本");
            }
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] body = new byte[buffer.remaining()];
            buffer.get(body);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(body), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException ex) {
            throw new IllegalStateException("凭据解密失败", ex);
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
