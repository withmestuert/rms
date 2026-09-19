package com.rms.backend.admin;

import com.rms.backend.common.ApiPaths;
import com.rms.backend.common.SecurityConstants;
import com.rms.backend.security.ProvisioningService;
import com.rms.backend.security.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ClientConfigurationController {
    private final SecurityProperties configuration;
    private final ProvisioningService provisioning;

    @GetMapping(ApiPaths.CLIENT_CONFIG)
    public Map<String, Object> configuration() {
        return Map.of("authenticationEnabled", configuration.isEnabled(), "setupAvailable", provisioning.setupAvailable(),
                "adminUrl", ApiPaths.ADMIN_UI, "bootstrapHeader", SecurityConstants.BOOTSTRAP_HEADER,
                "paths", Map.of("login", ApiPaths.AUTH + ApiPaths.LOGIN, "logout", ApiPaths.AUTH + ApiPaths.LOGOUT,
                        "me", ApiPaths.AUTH + ApiPaths.ME, "password", ApiPaths.AUTH + ApiPaths.PASSWORD,
                        "bootstrap", ApiPaths.ADMIN + ApiPaths.BOOTSTRAP, "owners", ApiPaths.ADMIN + ApiPaths.OWNERS,
                        "ownerStatus", ApiPaths.ADMIN + ApiPaths.OWNER_STATUS,
                        "ownerPassword", ApiPaths.ADMIN + ApiPaths.OWNER_PASSWORD,
                        "ownerProperties", ApiPaths.ADMIN + ApiPaths.OWNER_PROPERTIES));
    }
}
