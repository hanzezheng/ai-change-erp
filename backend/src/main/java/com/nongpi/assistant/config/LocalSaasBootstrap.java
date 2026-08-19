package com.nongpi.assistant.config;

import com.nongpi.assistant.erp.connection.DatabaseErpConnectionProvider;
import com.nongpi.assistant.erp.connection.ErpConnectionEntity;
import com.nongpi.assistant.erp.connection.ErpConnectionRepository;
import com.nongpi.assistant.erp.connection.ErpConnectionStatus;
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
import com.nongpi.assistant.security.crypto.CredentialEncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 仅 local profile 生效。没有引导环境变量时不创建任何默认管理员。
 */
@Component
@Profile("local")
public class LocalSaasBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalSaasBootstrap.class);

    private final Environment environment;
    private final TenantRepository tenantRepository;
    private final AppUserRepository appUserRepository;
    private final MembershipRepository membershipRepository;
    private final ErpConnectionRepository erpConnectionRepository;
    private final PasswordEncoder passwordEncoder;
    private final CredentialEncryptionService credentialEncryptionService;

    public LocalSaasBootstrap(Environment environment,
                              TenantRepository tenantRepository,
                              AppUserRepository appUserRepository,
                              MembershipRepository membershipRepository,
                              ErpConnectionRepository erpConnectionRepository,
                              PasswordEncoder passwordEncoder,
                              CredentialEncryptionService credentialEncryptionService) {
        this.environment = environment;
        this.tenantRepository = tenantRepository;
        this.appUserRepository = appUserRepository;
        this.membershipRepository = membershipRepository;
        this.erpConnectionRepository = erpConnectionRepository;
        this.passwordEncoder = passwordEncoder;
        this.credentialEncryptionService = credentialEncryptionService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String login = trimToNull(environment.getProperty("APP_BOOTSTRAP_LOGIN"));
        if (login == null) {
            log.info("未设置 APP_BOOTSTRAP_LOGIN，跳过 local 引导数据");
            return;
        }
        String password = environment.getProperty("APP_BOOTSTRAP_PASSWORD");
        String tenantName = trimToNull(environment.getProperty("APP_BOOTSTRAP_TENANT_NAME"));
        String erpBaseUrl = trimToNull(environment.getProperty("ERP_BASE_URL"));
        String erpSiteName = trimToNull(environment.getProperty("ERP_SITE_NAME"));
        String erpApiKey = environment.getProperty("ERP_API_KEY");
        String erpApiSecret = environment.getProperty("ERP_API_SECRET");
        if (password == null || password.isBlank() || tenantName == null
                || erpBaseUrl == null || erpApiKey == null || erpApiKey.isBlank()
                || erpApiSecret == null || erpApiSecret.isBlank()) {
            throw new IllegalStateException("local 引导需要 APP_BOOTSTRAP_PASSWORD、APP_BOOTSTRAP_TENANT_NAME、ERP_BASE_URL、ERP_API_KEY、ERP_API_SECRET");
        }

        TenantEntity tenant = tenantRepository.findByName(tenantName)
                .orElseGet(() -> tenantRepository.save(new TenantEntity(UUID.randomUUID(), tenantName, TenantStatus.ACTIVE)));
        AppUserEntity user = appUserRepository.findByLoginIgnoreCase(login)
                .orElseGet(() -> appUserRepository.save(new AppUserEntity(
                        UUID.randomUUID(), login, passwordEncoder.encode(password), login, UserStatus.ACTIVE)));
        if (membershipRepository.findByTenant_IdAndUser_Id(tenant.getId(), user.getId()).isEmpty()) {
            membershipRepository.save(new MembershipEntity(
                    UUID.randomUUID(), tenant, user, MembershipRole.OWNER, MembershipStatus.ACTIVE));
        }
        if (erpConnectionRepository.findByTenant_Id(tenant.getId()).isEmpty()) {
            ErpConnectionEntity connection = new ErpConnectionEntity(UUID.randomUUID(), tenant);
            connection.setBaseUrl(DatabaseErpConnectionProvider.stripTrailingSlash(erpBaseUrl));
            connection.setSiteName(erpSiteName);
            connection.setApiKeyCiphertext(credentialEncryptionService.encrypt(erpApiKey));
            connection.setApiSecretCiphertext(credentialEncryptionService.encrypt(erpApiSecret));
            connection.setSellingPriceList(trimToNull(environment.getProperty("ERP_SELLING_PRICE_LIST", "Standard Selling")));
            connection.setDefaultWarehouse(trimToNull(environment.getProperty("ERP_DEFAULT_WAREHOUSE")));
            connection.setStatus(ErpConnectionStatus.ACTIVE);
            connection.setConnectTimeoutMs(5000);
            connection.setReadTimeoutMs(20000);
            erpConnectionRepository.save(connection);
        }
        log.info("local 引导完成 tenant={} login={}", tenant.getName(), login);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
