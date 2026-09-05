package com.rms.backend.admissions.controller;

import com.rms.backend.admissions.dto.AdmissionRequestDto;
import com.rms.backend.admissions.dto.AdmissionResponseDto;
import com.rms.backend.admissions.service.AdmissionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admissions")
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
            @Valid @RequestBody AdmissionRequestDto dto) {
        AdmissionResponseDto response = admissionService.createEnrollment(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/admissions
     * Retrieve all admissions.
     */
    @GetMapping
    public ResponseEntity<List<AdmissionResponseDto>> getAllAdmissions() {
        return ResponseEntity.ok(admissionService.getAllAdmissions());
    }

    /**
     * GET /api/admissions/{admissionNumber}
     * Retrieve a specific admission by its admission number.
     */
    @GetMapping("/{admissionNumber}")
    public ResponseEntity<AdmissionResponseDto> getAdmissionByNumber(
            @PathVariable String admissionNumber) {
        return ResponseEntity.ok(admissionService.getAdmissionByNumber(admissionNumber));
    }

    /**
     * PUT /api/admissions/{admissionNumber}/confirm
     * Confirm a PENDING admission (transitions to PAID).
     */
    @PutMapping("/{admissionNumber}/confirm")
    public ResponseEntity<AdmissionResponseDto> confirmAdmission(
            @PathVariable String admissionNumber) {
        AdmissionResponseDto response = admissionService.confirmAdmission(admissionNumber);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/admissions/{admissionNumber}
     * Cancel a PENDING admission. Deletes tenant record if no other admission history exists.
     */
    @DeleteMapping("/{admissionNumber}")
    public ResponseEntity<String> cancelPendingAdmission(
            @PathVariable String admissionNumber) {
        admissionService.cancelPendingAdmission(admissionNumber);
        return ResponseEntity.ok("Admission " + admissionNumber + " has been cancelled successfully");
    }
}
