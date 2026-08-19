package com.nongpi.assistant.security;

import com.nongpi.assistant.saas.membership.MembershipRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("roles")
public class RoleGuard {

    public boolean atLeast(String role) {
        MembershipRole required = MembershipRole.valueOf(role);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof UserAuthentication userAuthentication)) {
            return false;
        }
        return userAuthentication.getPrincipal().role().atLeast(required);
    }
}
