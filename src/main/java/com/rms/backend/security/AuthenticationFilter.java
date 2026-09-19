package com.rms.backend.security;

import com.rms.backend.common.ApiPaths;
import com.rms.backend.common.SecurityConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

final class AuthenticationFilter extends OncePerRequestFilter {
    private final AuthService auth;
    private final SecurityProperties configuration;
    private final LoginRateLimiter limiter = new LoginRateLimiter();

    AuthenticationFilter(AuthService auth, SecurityProperties configuration) {
        this.auth = auth;
        this.configuration = configuration;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean credentialsEndpoint = path.equals(ApiPaths.AUTH + ApiPaths.LOGIN)
                || path.equals(ApiPaths.AUTH + ApiPaths.REGISTER) || path.equals(ApiPaths.ADMIN + ApiPaths.BOOTSTRAP);
        if (credentialsEndpoint && "POST".equals(request.getMethod()) && !limiter.allow(request.getRemoteAddr())) {
            response.setHeader("Retry-After", "60");
            SecurityErrors.write(response, 429, "TOO_MANY_ATTEMPTS");
            return;
        }
        Access.Principal principal = null;
        if (!configuration.isEnabled() && path.startsWith(ApiPaths.API + "/")
                && !credentialsEndpoint && !path.equals(ApiPaths.CLIENT_CONFIG)) {
            try { principal = auth.testingPrincipal(); }
            catch (IllegalStateException ex) {
                SecurityErrors.write(response, 503, "TEST_OWNER_NOT_CONFIGURED");
                return;
            }
            response.setHeader("X-RMS-Authentication", "disabled-for-testing");
        } else {
            String header = request.getHeader(SecurityConstants.AUTHORIZATION);
            if (header != null && header.startsWith(SecurityConstants.BEARER_PREFIX)) {
                principal = auth.authenticate(header.substring(SecurityConstants.BEARER_PREFIX.length())).orElse(null);
            }
        }
        if (principal != null) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role()))));
            if (SecurityConstants.SUB_MEMBER.equals(principal.role())
                    && !SecurityConstants.READ_METHODS.contains(request.getMethod())
                    && !path.equals(ApiPaths.AUTH + ApiPaths.LOGOUT)
                    && !path.equals(ApiPaths.AUTH + ApiPaths.PASSWORD)) {
                SecurityErrors.write(response, 403, "READ_ONLY_ACCOUNT");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
