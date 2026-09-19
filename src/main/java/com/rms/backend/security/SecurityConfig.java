package com.rms.backend.security;

import com.rms.backend.common.ApiPaths;
import com.rms.backend.common.SecurityConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(SecurityConstants.BCRYPT_STRENGTH); }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthService auth, SecurityProperties configuration) throws Exception {
        return http.cors(c -> {}).csrf(c -> c.disable()) // Only bearer headers, never cookie-based authentication.
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(c -> c.disable())
            .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives(
                    "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
                            + "connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.GET, ApiPaths.ADMIN_ASSETS, ApiPaths.CLIENT_CONFIG).permitAll()
                .requestMatchers(HttpMethod.POST, ApiPaths.AUTH + ApiPaths.REGISTER, ApiPaths.AUTH + ApiPaths.LOGIN,
                        ApiPaths.ADMIN + ApiPaths.BOOTSTRAP).permitAll()
                .requestMatchers(ApiPaths.API_SYSTEM + "/**", ApiPaths.API + "/webhooks/**",
                        ApiPaths.API + ApiPaths.VACATE_WEBHOOK, ApiPaths.API + ApiPaths.PAYMENTS_WEBHOOK,
                        ApiPaths.API + ApiPaths.WHATSAPP_WEBHOOK).denyAll()
                .requestMatchers(ApiPaths.API_PATTERN).authenticated()
                .anyRequest().denyAll())
            .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> SecurityErrors.write(res, 401, "AUTHENTICATION_REQUIRED"))
                .accessDeniedHandler((req, res, ex) -> SecurityErrors.write(res, 403, "ACCESS_DENIED")))
            .addFilterBefore(new AuthenticationFilter(auth, configuration), UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
