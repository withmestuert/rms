package com.rms.backend.admissions.controller;

import com.rms.backend.common.SecurityConstants;
import com.rms.backend.common.ApiPaths;
import com.rms.backend.admissions.dto.AdmissionRequestDto;
import com.rms.backend.admissions.dto.AdmissionResponseDto;
import com.rms.backend.admissions.dto.TenantStayCheckDto;
import com.rms.backend.admissions.service.AdmissionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(ApiPaths.API_ADMISSIONS)
public class AdmissionController {

    private final AdmissionService admissionService;

    public AdmissionController(AdmissionService admissionService) {
        this.admissionService = admissionService;
    }

    /**
     * POST /api/admissions
     * Create a new enrollment (pending admission).
     */
    @PostMapping
    public ResponseEntity<AdmissionResponseDto> createEnrollment(
            @Valid @RequestBody AdmissionRequestDto dto,
            @RequestHeader(value = SecurityConstants.PROPERTY_HEADER, required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : (headerPropId != null ? headerPropId : dto.getPropertyId());
        if (effectivePropId != null) {
            dto.setPropertyId(effectivePropId);
        }
        AdmissionResponseDto response = admissionService.createEnrollment(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/admissions/check-existing
     * Check if a resident already exists or has previous stays by Aadhaar or Mobile.
     */
    @GetMapping(ApiPaths.CHECK_EXISTING)
    public ResponseEntity<TenantStayCheckDto> checkExistingTenant(
            @RequestParam(value = "aadhaarNo", required = false) String aadhaarNo,
            @RequestParam(value = "mobileNumber", required = false) String mobileNumber) {
        TenantStayCheckDto response = admissionService.checkExistingTenant(aadhaarNo, mobileNumber);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/admissions
     * Retrieve all admissions (optionally filtered by property).
     */
    @GetMapping
    public ResponseEntity<List<AdmissionResponseDto>> getAllAdmissions(
            @RequestHeader(value = SecurityConstants.PROPERTY_HEADER, required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : headerPropId;
        if (effectivePropId != null) {
            return ResponseEntity.ok(admissionService.getAdmissionsByPropertyId(effectivePropId));
        }
        return ResponseEntity.ok(admissionService.getAllAdmissions());
    }

    /**
     * GET /api/admissions/{admissionNumber}
     * Retrieve a specific admission by its admission number.
     */
    @GetMapping(ApiPaths.ADMISSIONNUMBER)
    public ResponseEntity<AdmissionResponseDto> getAdmissionByNumber(
            @PathVariable String admissionNumber) {
        return ResponseEntity.ok(admissionService.getAdmissionByNumber(admissionNumber));
    }

    /**
     * PUT /api/admissions/{admissionNumber}/confirm
     * Confirm a PENDING admission (transitions to PAID).
     */
    @PutMapping(ApiPaths.ADMISSIONNUMBER_CONFIRM)
    public ResponseEntity<AdmissionResponseDto> confirmAdmission(
            @PathVariable String admissionNumber) {
        AdmissionResponseDto response = admissionService.confirmAdmission(admissionNumber);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/admissions/{admissionNumber}
     * Cancel a PENDING admission. Deletes tenant record if no other admission history exists.
     */
    @DeleteMapping(ApiPaths.ADMISSIONNUMBER)
    public ResponseEntity<String> cancelPendingAdmission(
            @PathVariable String admissionNumber) {
        admissionService.cancelPendingAdmission(admissionNumber);
        return ResponseEntity.ok("Admission " + admissionNumber + " has been cancelled successfully");
    }
}
