package com.nongpi.assistant.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public class UserAuthentication extends AbstractAuthenticationToken {

    private final UserPrincipal principal;

    public UserAuthentication(UserPrincipal principal) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public UserPrincipal getPrincipal() {
        return principal;
    }
}
