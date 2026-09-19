package com.rms.backend.tenants.controller;

import com.rms.backend.common.SecurityConstants;
import com.rms.backend.common.ApiPaths;
import com.rms.backend.tenants.dto.TenantRequestDto;
import com.rms.backend.tenants.entity.Tenant;
import com.rms.backend.tenants.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(ApiPaths.API_TENANTS)
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    public ResponseEntity<Tenant> createTenant(
            @Valid @RequestBody TenantRequestDto tenantRequestDTO,
            @RequestHeader(value = SecurityConstants.PROPERTY_HEADER, required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {

        Long effectivePropId = queryPropId != null ? queryPropId : (headerPropId != null ? headerPropId : tenantRequestDTO.getPropertyId());
        if (effectivePropId != null) {
            tenantRequestDTO.setPropertyId(effectivePropId);
        }

        Tenant createdTenant = tenantService.createTenant(tenantRequestDTO);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(createdTenant);
    }

    @GetMapping
    public List<Tenant> getAllTenants(
            @RequestHeader(value = SecurityConstants.PROPERTY_HEADER, required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : headerPropId;
        if (effectivePropId != null) {
            return tenantService.getTenantsByPropertyId(effectivePropId);
        }
        return tenantService.getAllTenants();
    }

    @GetMapping(ApiPaths.UID)
    public Tenant getTenantByUid(@PathVariable String uid) {
        return tenantService.getTenantByUid(uid);
    }

    @GetMapping(ApiPaths.ROOM_ROOMNO)
    public List<Tenant> getTenantsByRoom(@PathVariable String roomNo) {
        return tenantService.getTenantsByRoomNo(roomNo);
    }

    @PutMapping(ApiPaths.UID)
    public Tenant updateTenant(
            @PathVariable String uid,
            @Valid @RequestBody TenantRequestDto tenantRequestDTO) {

        return tenantService.updateTenant(uid, tenantRequestDTO);
    }

    @DeleteMapping(ApiPaths.UID)
    public ResponseEntity<String> deleteTenant(
            @PathVariable String uid) {

        tenantService.deleteTenant(uid);

        return ResponseEntity.ok("Tenant deleted successfully");
    }

    @PutMapping(ApiPaths.UID_VERIFY_ADVANCE)
    public ResponseEntity<Tenant> verifyAdvancePayment(
            @PathVariable String uid,
            @RequestBody(required = false) java.util.Map<String, Integer> request) {

        Integer amount = (request != null && request.containsKey("amount")) ? request.get("amount") : null;
        Tenant verified = tenantService.verifyAdvancePayment(uid, amount);

        return ResponseEntity.ok(verified);
    }
}
