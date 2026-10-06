package com.uvend.web.security;

import com.uvend.domain.entity.AppUser;
import com.uvend.domain.repo.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/** 极简 HTTP Basic 认证过滤器（无状态），账号来自 app_user 表。 */
@Component
public class BasicAuthFilter extends OncePerRequestFilter {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public BasicAuthFilter(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        extract(request).ifPresent(pair -> userRepository.findByUsername(pair[0])
                .filter(AppUser::getActive)
                .filter(u -> passwordEncoder.matches(pair[1], u.getPasswordHash()))
                .ifPresent(u -> {
                    AuthUser principal = new AuthUser(u.getId(), u.getUsername(), u.getDisplayName(),
                            u.getRole(), u.getSupplierId(), List.of("ROLE_" + u.getRole().name()));
                    var auth = new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }));
        chain.doFilter(request, response);
    }

    private Optional<String[]> extract(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Basic ")) return Optional.empty();
        try {
            String decoded = new String(Base64.getDecoder().decode(header.substring(6)), StandardCharsets.UTF_8);
            int idx = decoded.indexOf(':');
            if (idx <= 0) return Optional.empty();
            return Optional.of(new String[]{decoded.substring(0, idx), decoded.substring(idx + 1)});
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
