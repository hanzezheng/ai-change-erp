package com.nongpi.assistant.support;

import com.nongpi.assistant.audit.AuditLogRepository;
import com.nongpi.assistant.erp.connection.ErpConnectionEntity;
import com.nongpi.assistant.erp.connection.ErpConnectionRepository;
import com.nongpi.assistant.erp.connection.ErpConnectionStatus;
import com.nongpi.assistant.saas.auth.RefreshTokenRepository;
import com.nongpi.assistant.saas.membership.MembershipEntity;
import com.nongpi.assistant.saas.membership.MembershipRepository;
import com.nongpi.assistant.saas.membership.MembershipRole;
import com.nongpi.assistant.saas.membership.MembershipStatus;
import com.nongpi.assistant.saas.tenant.TenantEntity;
import com.nongpi.assistant.saas.tenant.TenantRepository;
import com.nongpi.assistant.saas.tenant.TenantStatus;
import com.nongpi.assistant.saas.user.AppUserEntity;
import com.nongpi.assistant.saas.user.AppUserRepository;
import com.nongpi.assistant.saas.user.UserStatus;
import com.nongpi.assistant.security.JwtService;
import com.nongpi.assistant.security.UserPrincipal;
import com.nongpi.assistant.security.crypto.CredentialEncryptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractSaasIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        System.setProperty("api.version", "1.44");
        if (System.getenv("DOCKER_HOST") == null) {
            System.setProperty("DOCKER_HOST", "unix:///var/run/docker.sock");
        }
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("nongpi")
                .withUsername("nongpi")
                .withPassword("nongpi");
        POSTGRES.start();
    }

    protected static final String JWT_SECRET = "test-jwt-hmac-secret-key-32bytes-min";
    protected static final String ENCRYPTION_KEY = "test-credential-master-key-32b!!";

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> JWT_SECRET);
        registry.add("app.credential-encryption.key", () -> ENCRYPTION_KEY);
    }

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected TenantRepository tenantRepository;
    @Autowired
    protected AppUserRepository appUserRepository;
    @Autowired
    protected MembershipRepository membershipRepository;
    @Autowired
    protected ErpConnectionRepository erpConnectionRepository;
    @Autowired
    protected RefreshTokenRepository refreshTokenRepository;
    @Autowired
    protected AuditLogRepository auditLogRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected CredentialEncryptionService credentialEncryptionService;
    @Autowired
    protected JwtService jwtService;

    @BeforeEach
    void cleanSaasData() {
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        membershipRepository.deleteAll();
        erpConnectionRepository.deleteAll();
        appUserRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    protected TenantEntity newTenant(String name, TenantStatus status) {
        return tenantRepository.save(new TenantEntity(UUID.randomUUID(), name, status));
    }

    protected AppUserEntity newUser(String login, String password, UserStatus status) {
        return appUserRepository.save(new AppUserEntity(
                UUID.randomUUID(), login, passwordEncoder.encode(password), login, status));
    }

    protected MembershipEntity newMembership(TenantEntity tenant,
                                             AppUserEntity user,
                                             MembershipRole role,
                                             MembershipStatus status) {
        MembershipEntity saved = membershipRepository.save(
                new MembershipEntity(UUID.randomUUID(), tenant, user, role, status));
        return membershipRepository.findWithUserAndTenantById(saved.getId()).orElseThrow();
    }

    protected ErpConnectionEntity newErpConnection(TenantEntity tenant,
                                                   String baseUrl,
                                                   String apiKey,
                                                   String apiSecret) {
        ErpConnectionEntity entity = new ErpConnectionEntity(UUID.randomUUID(), tenant);
        entity.setBaseUrl(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        entity.setSiteName(tenant.getName());
        entity.setApiKeyCiphertext(credentialEncryptionService.encrypt(apiKey));
        entity.setApiSecretCiphertext(credentialEncryptionService.encrypt(apiSecret));
        entity.setSellingPriceList("Standard Selling");
        entity.setDefaultWarehouse("主仓库 - T");
        entity.setStatus(ErpConnectionStatus.ACTIVE);
        entity.setConnectTimeoutMs(500);
        entity.setReadTimeoutMs(500);
        return erpConnectionRepository.save(entity);
    }

    protected String accessToken(AppUserEntity user, MembershipEntity membership) {
        UserPrincipal principal = new UserPrincipal(
                user.getId(),
                membership.getTenant().getId(),
                membership.getId(),
                membership.getRole(),
                user.getLogin(),
                user.getDisplayName(),
                membership.getTenant().getName()
        );
        return jwtService.issueAccessToken(principal);
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }
}
