package com.nongpi.assistant.security.crypto;

/**
 * ERP 凭据加解密。实现可替换为 KMS / Vault，调用方只依赖本接口。
 */
public interface CredentialEncryptionService {

    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
