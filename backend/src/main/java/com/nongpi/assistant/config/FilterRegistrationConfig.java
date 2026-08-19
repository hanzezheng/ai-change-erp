package com.nongpi.assistant.config;

import com.nongpi.assistant.security.JwtAuthenticationFilter;
import com.nongpi.assistant.tenant.TenantContextFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 这两个 Filter 只挂在 Spring Security 链上，避免 Servlet 容器再注册一份导致顺序错乱。
 */
@Configuration
public class FilterRegistrationConfig {

    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> disableJwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<TenantContextFilter> disableTenantFilterRegistration(TenantContextFilter filter) {
        FilterRegistrationBean<TenantContextFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
