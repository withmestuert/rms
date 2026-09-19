package com.rms.backend.vacate.controller;

import com.rms.backend.vacate.dto.VacateChargeUpdateDto;
import com.rms.backend.vacate.dto.VacateRequestDto;
import com.rms.backend.vacate.dto.VacateResponseDto;
import com.rms.backend.vacate.entity.VacateStatus;
import com.rms.backend.vacate.service.VacateRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vacate-requests")
@RequiredArgsConstructor
public class VacateRequestController {

    private final VacateRequestService vacateRequestService;

    @PostMapping
    public ResponseEntity<VacateResponseDto> submitVacateRequest(@RequestBody VacateRequestDto dto) {
        VacateResponseDto response = vacateRequestService.submitVacateRequest(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<VacateResponseDto>> getAllVacateRequests(
            @RequestParam(required = false) VacateStatus status,
            @RequestParam(required = false) Long propertyId) {
        return ResponseEntity.ok(vacateRequestService.getAllVacateRequests(status, propertyId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VacateResponseDto> getVacateRequestById(@PathVariable Long id) {
        return ResponseEntity.ok(vacateRequestService.getVacateRequestById(id));
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<VacateResponseDto> approveVacateRequest(@PathVariable Long id) {
        return ResponseEntity.ok(vacateRequestService.approveVacateRequest(id));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<VacateResponseDto> rejectVacateRequest(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = (body != null) ? body.get("reason") : null;
        return ResponseEntity.ok(vacateRequestService.rejectVacateRequest(id, reason));
    }

    @PutMapping("/{id}/charges")
    public ResponseEntity<VacateResponseDto> updateCharges(
            @PathVariable Long id,
            @RequestBody VacateChargeUpdateDto dto) {
        return ResponseEntity.ok(vacateRequestService.updateCharges(id, dto));
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<VacateResponseDto> completeVacate(@PathVariable Long id) {
        return ResponseEntity.ok(vacateRequestService.completeVacate(id));
    }
}
