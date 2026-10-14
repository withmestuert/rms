package com.rms.backend.properties.controller;

import com.rms.backend.properties.dto.PropertyArchiveSnapshotDto;
import com.rms.backend.properties.dto.PropertyRequestDto;
import com.rms.backend.properties.dto.PropertyResponseDto;
import com.rms.backend.properties.dto.PropertySoftDeleteRequestDto;
import com.rms.backend.properties.service.PropertyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    private final PropertyService propertyService;

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @GetMapping
    public ResponseEntity<List<PropertyResponseDto>> getAllProperties(
            @RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted) {
        if (!includeDeleted) {
            return ResponseEntity.ok(propertyService.getAllProperties());
        }
        return ResponseEntity.ok(propertyService.getAllProperties(includeDeleted));
    }

    @GetMapping("/archived")
    public ResponseEntity<List<PropertyResponseDto>> getArchivedProperties() {
        return ResponseEntity.ok(propertyService.getArchivedProperties());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PropertyResponseDto> getPropertyById(@PathVariable Long id) {
        return ResponseEntity.ok(propertyService.getPropertyById(id));
    }

    @PostMapping
    public ResponseEntity<PropertyResponseDto> createProperty(@Valid @RequestBody PropertyRequestDto dto) {
        PropertyResponseDto created = propertyService.createProperty(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PropertyResponseDto> updateProperty(
            @PathVariable Long id,
            @Valid @RequestBody PropertyRequestDto dto) {
        PropertyResponseDto updated = propertyService.updateProperty(id, dto);
        return ResponseEntity.ok(updated);
    }

    /**
     * Protected Soft Deletion with Pre-Deletion Snapshot Manifest and TTL Grace Period.
     */
    @PostMapping("/{id}/soft-delete")
    public ResponseEntity<PropertyResponseDto> softDeleteProperty(
            @PathVariable Long id,
            @RequestBody PropertySoftDeleteRequestDto dto) {
        PropertyResponseDto decommissioned = propertyService.softDeleteProperty(id, dto);
        return ResponseEntity.ok(decommissioned);
    }

    /**
     * One-click restore for soft-deleted properties during their TTL grace period.
     */
    @PostMapping("/{id}/restore")
    public ResponseEntity<PropertyResponseDto> restoreProperty(@PathVariable Long id) {
        PropertyResponseDto restored = propertyService.restoreProperty(id);
        return ResponseEntity.ok(restored);
    }

    /**
     * Download or inspect the permanent audit manifest snapshot for a property.
     */
    @GetMapping("/{id}/snapshot")
    public ResponseEntity<PropertyArchiveSnapshotDto> getPropertySnapshot(@PathVariable Long id) {
        return ResponseEntity.ok(propertyService.getArchivedSnapshot(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteProperty(@PathVariable Long id) {
        propertyService.deleteProperty(id);
        return ResponseEntity.ok("Property deleted successfully");
    }
}
