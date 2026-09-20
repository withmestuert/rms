package com.rms.backend.reports.controller;

import com.rms.backend.reports.dto.DemographicsReportDto;
import com.rms.backend.reports.dto.MonthlyIntakeDto;
import com.rms.backend.reports.dto.PropertyPerformanceDto;
import com.rms.backend.reports.dto.ReportSummaryDto;
import com.rms.backend.reports.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/summary")
    public ResponseEntity<ReportSummaryDto> getSummary(
            @RequestHeader(value = "X-Property-Id", required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : headerPropId;
        return ResponseEntity.ok(reportService.getSummary(effectivePropId));
    }

    @GetMapping("/property-performance")
    public ResponseEntity<List<PropertyPerformanceDto>> getPropertyPerformance(
            @RequestHeader(value = "X-Property-Id", required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : headerPropId;
        return ResponseEntity.ok(reportService.getPropertyPerformance(effectivePropId));
    }

    @GetMapping("/monthly-intake")
    public ResponseEntity<List<MonthlyIntakeDto>> getMonthlyIntake(
            @RequestHeader(value = "X-Property-Id", required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId,
            @RequestParam(value = "year", required = false) String year) {
        Long effectivePropId = queryPropId != null ? queryPropId : headerPropId;
        return ResponseEntity.ok(reportService.getMonthlyIntake(effectivePropId, year));
    }

    @GetMapping("/demographics")
    public ResponseEntity<DemographicsReportDto> getDemographics(
            @RequestHeader(value = "X-Property-Id", required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : headerPropId;
        return ResponseEntity.ok(reportService.getDemographics(effectivePropId));
    }
}
