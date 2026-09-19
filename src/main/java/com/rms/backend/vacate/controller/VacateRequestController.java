package com.rms.backend.vacate.controller;

import com.rms.backend.common.ApiPaths;
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
@RequestMapping(ApiPaths.API_VACATE_REQUESTS)
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

    @GetMapping(ApiPaths.ID)
    public ResponseEntity<VacateResponseDto> getVacateRequestById(@PathVariable Long id) {
        return ResponseEntity.ok(vacateRequestService.getVacateRequestById(id));
    }

    @PutMapping(ApiPaths.ID_APPROVE)
    public ResponseEntity<VacateResponseDto> approveVacateRequest(@PathVariable Long id) {
        return ResponseEntity.ok(vacateRequestService.approveVacateRequest(id));
    }

    @PutMapping(ApiPaths.ID_REJECT)
    public ResponseEntity<VacateResponseDto> rejectVacateRequest(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = (body != null) ? body.get("reason") : null;
        return ResponseEntity.ok(vacateRequestService.rejectVacateRequest(id, reason));
    }

    @PutMapping(ApiPaths.ID_CHARGES)
    public ResponseEntity<VacateResponseDto> updateCharges(
            @PathVariable Long id,
            @RequestBody VacateChargeUpdateDto dto) {
        return ResponseEntity.ok(vacateRequestService.updateCharges(id, dto));
    }

    @PutMapping(ApiPaths.ID_COMPLETE)
    public ResponseEntity<VacateResponseDto> completeVacate(@PathVariable Long id) {
        return ResponseEntity.ok(vacateRequestService.completeVacate(id));
    }
}
