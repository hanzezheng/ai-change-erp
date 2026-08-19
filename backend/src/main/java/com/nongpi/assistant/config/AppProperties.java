package com.nongpi.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 运行期配置。租户、用户与 ERP 连接已进入 PostgreSQL，不再从 YAML 列表加载。
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, CredentialEncryption credentialEncryption) {

    public record Jwt(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
        public Jwt {
            accessTokenTtl = accessTokenTtl == null ? Duration.ofMinutes(15) : accessTokenTtl;
            refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(30) : refreshTokenTtl;
        }
    }

    public record CredentialEncryption(String key) {
    }
}
