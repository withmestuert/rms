package com.rms.backend.tenants.controller;

import com.rms.backend.tenants.dto.TenantRequestDto;
import com.rms.backend.tenants.entity.Tenant;
import com.rms.backend.tenants.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    public ResponseEntity<Tenant> createTenant(
            @Valid @RequestBody TenantRequestDto tenantRequestDTO) {

        Tenant createdTenant = tenantService.createTenant(tenantRequestDTO);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(createdTenant);
    }

    @GetMapping
    public List<Tenant> getAllTenants() {
        return tenantService.getAllTenants();
    }

    @GetMapping("/{uid}")
    public Tenant getTenantByUid(@PathVariable String uid) {
        return tenantService.getTenantByUid(uid);
    }

    @GetMapping("/room/{roomNo}")
    public List<Tenant> getTenantsByRoom(@PathVariable String roomNo) {
        return tenantService.getTenantsByRoomNo(roomNo);
    }

    @PutMapping("/{uid}")
    public Tenant updateTenant(
            @PathVariable String uid,
            @Valid @RequestBody TenantRequestDto tenantRequestDTO) {

        return tenantService.updateTenant(uid, tenantRequestDTO);
    }

    @DeleteMapping("/{uid}")
    public ResponseEntity<String> deleteTenant(
            @PathVariable String uid) {

        tenantService.deleteTenant(uid);

        return ResponseEntity.ok("Tenant deleted successfully");
    }
}
