package com.uvend.web.security;

import com.uvend.domain.enums.Role;

import java.util.List;

/** 认证主体（无状态，不使用 SecurityContext 持久会话）。 */
public record AuthUser(Long id, String username, String displayName, Role role, Long supplierId,
                       List<String> roles) {
    public boolean hasAny(String... required) {
        for (String r : required) {
            if (role.name().equals(r)) return true;
        }
        return false;
    }
}
