package com.rms.backend.properties.controller;

import com.rms.backend.common.ApiPaths;
import com.rms.backend.properties.dto.PropertyRequestDto;
import com.rms.backend.properties.dto.PropertyResponseDto;
import com.rms.backend.properties.service.PropertyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(ApiPaths.API_PROPERTIES)
public class PropertyController {

    private final PropertyService propertyService;

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @GetMapping
    public ResponseEntity<List<PropertyResponseDto>> getAllProperties() {
        return ResponseEntity.ok(propertyService.getAllProperties());
    }

    @GetMapping(ApiPaths.ID)
    public ResponseEntity<PropertyResponseDto> getPropertyById(@PathVariable Long id) {
        return ResponseEntity.ok(propertyService.getPropertyById(id));
    }

    @PostMapping
    public ResponseEntity<PropertyResponseDto> createProperty(@Valid @RequestBody PropertyRequestDto dto) {
        PropertyResponseDto created = propertyService.createProperty(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping(ApiPaths.ID)
    public ResponseEntity<PropertyResponseDto> updateProperty(
            @PathVariable Long id,
            @Valid @RequestBody PropertyRequestDto dto) {
        PropertyResponseDto updated = propertyService.updateProperty(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping(ApiPaths.ID)
    public ResponseEntity<String> deleteProperty(@PathVariable Long id) {
        propertyService.deleteProperty(id);
        return ResponseEntity.ok("Property deleted successfully");
    }
}
