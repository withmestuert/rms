package com.rms.backend.reports.controller;

import com.rms.backend.reports.dto.*;
import com.rms.backend.reports.service.AnalyticsService;
import com.rms.backend.reports.service.ReportExportService;
import com.rms.backend.reports.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final AnalyticsService analyticsService;
    private final ReportExportService reportExportService;

    // --- Legacy / Compatibility endpoints ---

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

    // --- Modern Analytical Endpoints ---

    @GetMapping("/analytics/kpis")
    public ResponseEntity<AnalyticsKpiDto> getKpis(
            @RequestHeader(value = "X-Property-Id", required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : headerPropId;
        return ResponseEntity.ok(analyticsService.getKpis(effectivePropId));
    }

    @GetMapping("/analytics/room-type-yield")
    public ResponseEntity<List<RoomTypeYieldDto>> getRoomTypeYield(
            @RequestHeader(value = "X-Property-Id", required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : headerPropId;
        return ResponseEntity.ok(analyticsService.getRoomTypeYields(effectivePropId));
    }

    @GetMapping("/analytics/revenue-trends")
    public ResponseEntity<List<RevenueTrendDto>> getRevenueTrends(
            @RequestHeader(value = "X-Property-Id", required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId,
            @RequestParam(value = "months", defaultValue = "6") int months) {
        Long effectivePropId = queryPropId != null ? queryPropId : headerPropId;
        return ResponseEntity.ok(analyticsService.getRevenueTrends(effectivePropId, months));
    }

    @GetMapping("/analytics/defaulters-aging")
    public ResponseEntity<DefaulterAgingDto> getDefaultersAging(
            @RequestHeader(value = "X-Property-Id", required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : headerPropId;
        return ResponseEntity.ok(analyticsService.getDefaultersAging(effectivePropId));
    }

    @GetMapping("/analytics/deposits-custody")
    public ResponseEntity<SecurityDepositReportDto> getDepositsCustody(
            @RequestHeader(value = "X-Property-Id", required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : headerPropId;
        return ResponseEntity.ok(analyticsService.getSecurityDepositsReport(effectivePropId));
    }

    @GetMapping("/analytics/turnover")
    public ResponseEntity<TurnoverAnalyticsDto> getTurnoverAnalytics(
            @RequestHeader(value = "X-Property-Id", required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : headerPropId;
        return ResponseEntity.ok(analyticsService.getTurnoverAnalytics(effectivePropId));
    }

    // --- CSV Streaming Export Endpoints ---

    @GetMapping(value = "/export/rent-roll", produces = "text/csv")
    public ResponseEntity<byte[]> exportRentRoll(
            @RequestHeader(value = "X-Property-Id", required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : headerPropId;
        byte[] csvData = reportExportService.exportRentRollCsv(effectivePropId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"rent_roll.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvData);
    }

    @GetMapping(value = "/export/defaulters", produces = "text/csv")
    public ResponseEntity<byte[]> exportDefaulters(
            @RequestHeader(value = "X-Property-Id", required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : headerPropId;
        byte[] csvData = reportExportService.exportDefaultersCsv(effectivePropId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"defaulters_aging.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvData);
    }

    @GetMapping(value = "/export/deposits", produces = "text/csv")
    public ResponseEntity<byte[]> exportDeposits(
            @RequestHeader(value = "X-Property-Id", required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : headerPropId;
        byte[] csvData = reportExportService.exportDepositsCsv(effectivePropId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"deposits_custody.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvData);
    }

    @GetMapping(value = "/export/ledger", produces = "text/csv")
    public ResponseEntity<byte[]> exportLedger(
            @RequestHeader(value = "X-Property-Id", required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : headerPropId;
        byte[] csvData = reportExportService.exportFinancialLedgerCsv(effectivePropId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"financial_ledger.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvData);
    }
}
