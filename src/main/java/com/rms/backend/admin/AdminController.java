package com.rms.backend.admin;

import com.rms.backend.common.ApiPaths;
import com.rms.backend.common.SecurityConstants;
import com.rms.backend.security.AuthRequests;
import com.rms.backend.security.ProvisioningService;
import com.rms.backend.properties.entity.Property;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping(ApiPaths.ADMIN)
@RequiredArgsConstructor
public class AdminController {
    private final ProvisioningService provisioning;

    @PostMapping(ApiPaths.BOOTSTRAP)
    @ResponseStatus(HttpStatus.CREATED)
    public ProvisioningService.Account bootstrap(@RequestHeader(value = SecurityConstants.BOOTSTRAP_HEADER, required = false) String key,
                                                 @Valid @RequestBody AuthRequests.CreateAccount dto) {
        return provisioning.bootstrap(key, dto);
    }

    @GetMapping(ApiPaths.OWNERS)
    public List<ProvisioningService.Account> owners() { return provisioning.owners(); }

    @PostMapping(ApiPaths.OWNERS)
    @ResponseStatus(HttpStatus.CREATED)
    public ProvisioningService.Account createOwner(@Valid @RequestBody AuthRequests.CreateAccount dto) {
        return provisioning.createOwner(dto);
    }

    @PutMapping(ApiPaths.OWNER_STATUS)
    public ProvisioningService.Account status(@PathVariable Long id, @Valid @RequestBody AuthRequests.Status dto) {
        return provisioning.setStatus(id, dto.status());
    }

    @PutMapping(ApiPaths.OWNER_PASSWORD)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody AuthRequests.ResetPassword dto) {
        provisioning.resetPassword(id, dto.password());
    }

    @GetMapping(ApiPaths.OWNER_PROPERTIES)
    public List<Property> properties(@PathVariable Long id) { return provisioning.ownerProperties(id); }
}
