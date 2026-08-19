package com.nongpi.assistant.security;

import com.nongpi.assistant.common.error.BusinessErrorCode;
import com.nongpi.assistant.common.error.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static UserPrincipal requireUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof UserAuthentication userAuthentication) {
            return userAuthentication.getPrincipal();
        }
        throw new BusinessException(BusinessErrorCode.TOKEN_INVALID);
    }
}
