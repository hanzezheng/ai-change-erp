package com.nongpi.assistant.security;

import com.nongpi.assistant.config.AppProperties;
import com.nongpi.assistant.saas.membership.MembershipRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    public static final String CLAIM_TENANT_ID = "tid";
    public static final String CLAIM_MEMBERSHIP_ID = "mid";
    public static final String CLAIM_ROLE = "role";

    private final SecretKey secretKey;
    private final Duration accessTokenTtl;
    private final Clock clock;

    public JwtService(AppProperties properties, Clock clock) {
        String secret = properties.jwt() == null ? null : properties.jwt().secret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("APP_JWT_SECRET 必须至少 32 个字符");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = properties.jwt().accessTokenTtl();
        this.clock = clock;
    }

    public String issueAccessToken(UserPrincipal principal) {
        Instant now = clock.instant();
        Instant expires = now.plus(accessTokenTtl);
        return Jwts.builder()
                .subject(principal.userId().toString())
                .claim(CLAIM_TENANT_ID, principal.tenantId().toString())
                .claim(CLAIM_MEMBERSHIP_ID, principal.membershipId().toString())
                .claim(CLAIM_ROLE, principal.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expires))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 测试用：签发指定过期时间的 Access Token。
     * issuedAt 必须早于 exp，否则解析器可能把它当成非法 Token 而不是过期 Token。
     */
    public String issueAccessToken(UserPrincipal principal, Instant expiresAt) {
        Instant issuedAt = expiresAt.minus(accessTokenTtl);
        return Jwts.builder()
                .subject(principal.userId().toString())
                .claim(CLAIM_TENANT_ID, principal.tenantId().toString())
                .claim(CLAIM_MEMBERSHIP_ID, principal.membershipId().toString())
                .claim(CLAIM_ROLE, principal.role().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    public ParsedAccessToken parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return ParsedAccessToken.from(claims);
        } catch (ExpiredJwtException ex) {
            throw new TokenExpiredException();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new TokenInvalidException();
        }
    }

    public record ParsedAccessToken(UUID userId, UUID tenantId, UUID membershipId, MembershipRole role) {
        static ParsedAccessToken from(Claims claims) {
            try {
                return new ParsedAccessToken(
                        UUID.fromString(claims.getSubject()),
                        UUID.fromString(claims.get(CLAIM_TENANT_ID, String.class)),
                        UUID.fromString(claims.get(CLAIM_MEMBERSHIP_ID, String.class)),
                        MembershipRole.valueOf(claims.get(CLAIM_ROLE, String.class))
                );
            } catch (RuntimeException ex) {
                throw new TokenInvalidException();
            }
        }
    }

    public static final class TokenExpiredException extends RuntimeException {
        TokenExpiredException() {
            super("access token expired");
        }
    }

    public static final class TokenInvalidException extends RuntimeException {
        TokenInvalidException() {
            super("access token invalid");
        }
    }
}
