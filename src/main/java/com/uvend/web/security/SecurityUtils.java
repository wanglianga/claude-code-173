package com.uvend.web.security;

import com.uvend.domain.enums.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;

public final class SecurityUtils {
    private SecurityUtils() {}

    public static AuthUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser u)) {
            throw new IllegalStateException("未认证");
        }
        return u;
    }

    public static Long currentUserId() {
        return current().id();
    }

    public static void requireAny(Role... roles) {
        AuthUser u = current();
        if (Arrays.stream(roles).noneMatch(r -> u.role() == r)) {
            throw new org.springframework.security.access.AccessDeniedException("需要角色: " + Arrays.toString(roles));
        }
    }
}
