package com.rms.backend.config;

import com.rms.backend.common.SecurityConstants;
import com.rms.backend.common.ApiPaths;
import com.rms.backend.security.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class CorsConfig implements WebMvcConfigurer {
    private final SecurityProperties security;
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping(ApiPaths.API_PATTERN)
                .allowedOrigins(security.getAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization", SecurityConstants.PROPERTY_HEADER, "X-Bootstrap-Key")
                .exposedHeaders("Retry-After", "X-RMS-Authentication")
                .allowCredentials(false).maxAge(3600);
    }
}
