package com.rms.backend.reports.service;

import com.rms.backend.admissions.entity.Admission;
import com.rms.backend.admissions.repository.AdmissionRepository;
import com.rms.backend.billing.entity.Invoice;
import com.rms.backend.billing.repository.InvoiceRepository;
import com.rms.backend.properties.entity.Property;
import com.rms.backend.properties.repository.PropertyRepository;
import com.rms.backend.reports.dto.DemographicsReportDto;
import com.rms.backend.reports.dto.MonthlyIntakeDto;
import com.rms.backend.reports.dto.PropertyPerformanceDto;
import com.rms.backend.reports.dto.ReportSummaryDto;
import com.rms.backend.rooms.repository.RoomRepository;
import com.rms.backend.tenants.entity.Tenant;
import com.rms.backend.tenants.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final TenantRepository tenantRepository;
    private final AdmissionRepository admissionRepository;
    private final InvoiceRepository invoiceRepository;

    private static final List<String> PALETTE = List.of(
            "#091426", "#2563eb", "#059669", "#d97706", "#7c3aed", "#e11d48", "#0891b2", "#475569"
    );

    @Transactional(readOnly = true)
    public ReportSummaryDto getSummary(Long propertyId) {
        List<Invoice> invoices = propertyId != null
                ? invoiceRepository.findByPropertyId(propertyId)
                : invoiceRepository.findAll();

        List<Tenant> tenants = propertyId != null
                ? tenantRepository.findActiveByPropertyId(propertyId)
                : tenantRepository.findAllActive();

        List<Admission> admissions = propertyId != null
                ? admissionRepository.findByPropertyId(propertyId)
                : admissionRepository.findAll();

        long targetRent = invoices.stream().mapToLong(i -> i.getAmount() != null ? i.getAmount() : 0).sum();
        long collectedRent = invoices.stream().mapToLong(i -> i.getPaidAmount() != null ? i.getPaidAmount() : 0).sum();

        if (targetRent == 0 && !tenants.isEmpty()) {
            targetRent = tenants.stream().mapToLong(t -> t.getStandardRent() != null ? t.getStandardRent() : 8000).sum();
        }

        double efficiency = targetRent > 0
                ? Math.round(((double) collectedRent / targetRent) * 1000.0) / 10.0
                : 0.0;

        int totalEnrolments = !admissions.isEmpty() ? admissions.size() : tenants.size();

        int workingCount = 0;
        int otherCount = 0;
        Map<String, Integer> hometownCounts = new HashMap<>();

        for (Tenant t : tenants) {
            String type = t.getTenantType();
            boolean isWorking = type == null || !"student".equalsIgnoreCase(type.trim());
            if (isWorking) {
                workingCount++;
            } else {
                otherCount++;
            }

            String org = t.getOrganizationName() != null && !t.getOrganizationName().isBlank()
                    ? t.getOrganizationName().trim()
                    : "Corporate / Independent";
            hometownCounts.put(org, hometownCounts.getOrDefault(org, 0) + 1);
        }

        int totalDemographics = workingCount + otherCount;
        int workingPercent = totalDemographics > 0 ? (int) Math.round(((double) workingCount / totalDemographics) * 100) : 0;
        int otherPercent = totalDemographics > 0 ? 100 - workingPercent : 0;

        String topHometown = "Bangalore";
        int topHometownPercent = 0;
        if (!hometownCounts.isEmpty()) {
            Map.Entry<String, Integer> topEntry = hometownCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElse(null);
            if (topEntry != null) {
                topHometown = topEntry.getKey();
                topHometownPercent = totalDemographics > 0
                        ? (int) Math.round(((double) topEntry.getValue() / totalDemographics) * 100)
                        : 0;
            }
        }

        return ReportSummaryDto.builder()
                .totalTargetRent(targetRent)
                .totalCollectedRent(collectedRent)
                .portfolioEfficiency(efficiency)
                .totalEnrolments(totalEnrolments)
                .workingTenantsCount(workingCount)
                .otherTenantsCount(otherCount)
                .workingPercent(workingPercent)
                .otherPercent(otherPercent)
                .totalHometowns(hometownCounts.size())
                .topHometown(topHometown)
                .topHometownPercent(topHometownPercent)
                .build();
    }

    @Transactional(readOnly = true)
    public List<PropertyPerformanceDto> getPropertyPerformance(Long propertyId) {
        List<Property> properties;
        if (propertyId != null) {
            properties = propertyRepository.findByIdAndIsDeletedFalse(propertyId)
                    .map(List::of)
                    .orElseGet(List::of);
        } else {
            properties = propertyRepository.findByIsDeletedFalse();
        }

        List<PropertyPerformanceDto> result = new ArrayList<>();
        for (Property prop : properties) {
            int totalRooms = roomRepository.findByPropertyId(prop.getId()).size();
            if (totalRooms == 0 && prop.getTotalRooms() != null) {
                totalRooms = prop.getTotalRooms();
            }

            List<Invoice> propInvoices = invoiceRepository.findByPropertyId(prop.getId());
            long targetRent = propInvoices.stream().mapToLong(i -> i.getAmount() != null ? i.getAmount() : 0).sum();
            long collectedRent = propInvoices.stream().mapToLong(i -> i.getPaidAmount() != null ? i.getPaidAmount() : 0).sum();

            if (targetRent == 0) {
                List<Tenant> propTenants = tenantRepository.findActiveByPropertyId(prop.getId());
                targetRent = propTenants.stream().mapToLong(t -> t.getStandardRent() != null ? t.getStandardRent() : 8000).sum();
            }

            double rate = targetRent > 0
                    ? Math.round(((double) collectedRent / targetRent) * 1000.0) / 10.0
                    : 0.0;

            String status = rate >= 95.0 ? "High Yield"
                    : rate >= 90.0 ? "On Target"
                    : rate >= 75.0 ? "Optimal"
                    : "Below Target";

            result.add(PropertyPerformanceDto.builder()
                    .id(prop.getId())
                    .name(prop.getName())
                    .totalRooms(totalRooms)
                    .targetRent(targetRent)
                    .collectedRent(collectedRent)
                    .rate(rate)
                    .status(status)
                    .build());
        }

        return result;
    }

    @Transactional(readOnly = true)
    public List<MonthlyIntakeDto> getMonthlyIntake(Long propertyId, String year) {
        List<Admission> admissions = propertyId != null
                ? admissionRepository.findByPropertyId(propertyId)
                : admissionRepository.findAll();

        int[] monthlyCounts = new int[12];

        for (Admission admission : admissions) {
            if (admission.getEnrollmentDate() != null) {
                int monthIndex = admission.getEnrollmentDate().getMonthValue() - 1;
                if (monthIndex >= 0 && monthIndex < 12) {
                    monthlyCounts[monthIndex]++;
                }
            } else if (admission.getCreatedAt() != null) {
                int monthIndex = admission.getCreatedAt().getMonthValue() - 1;
                if (monthIndex >= 0 && monthIndex < 12) {
                    monthlyCounts[monthIndex]++;
                }
            }
        }

        List<MonthlyIntakeDto> dtoList = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String monthName = Month.of(i + 1).getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            dtoList.add(MonthlyIntakeDto.builder()
                    .month(monthName)
                    .count(monthlyCounts[i])
                    .label(monthName)
                    .build());
        }

        return dtoList;
    }

    @Transactional(readOnly = true)
    public DemographicsReportDto getDemographics(Long propertyId) {
        List<Tenant> tenants = propertyId != null
                ? tenantRepository.findActiveByPropertyId(propertyId)
                : tenantRepository.findAllActive();

        Map<String, Integer> profMap = new HashMap<>();
        Map<String, Integer> homeMap = new HashMap<>();

        Map<Long, String> propertyCityMap = propertyRepository.findAll().stream()
                .filter(p -> p.getCity() != null)
                .collect(Collectors.toMap(Property::getId, Property::getCity, (c1, c2) -> c1));

        for (Tenant t : tenants) {
            String prof = t.getOrganizationName() != null && !t.getOrganizationName().isBlank()
                    ? t.getOrganizationName().trim()
                    : (t.getTenantType() != null ? t.getTenantType().trim() : "Working Professional");
            profMap.put(prof, profMap.getOrDefault(prof, 0) + 1);

            String city = t.getPropertyId() != null && propertyCityMap.containsKey(t.getPropertyId())
                    ? propertyCityMap.get(t.getPropertyId())
                    : "Bangalore";
            homeMap.put(city, homeMap.getOrDefault(city, 0) + 1);
        }

        int totalTenants = tenants.size();

        List<DemographicsReportDto.ProfessionStatDto> profList = profMap.entrySet().stream()
                .map(e -> DemographicsReportDto.ProfessionStatDto.builder()
                        .name(e.getKey())
                        .count(e.getValue())
                        .percent(totalTenants > 0 ? (int) Math.round(((double) e.getValue() / totalTenants) * 100) : 0)
                        .build())
                .sorted((a, b) -> Integer.compare(b.getCount(), a.getCount()))
                .collect(Collectors.toList());

        List<DemographicsReportDto.HometownStatDto> homeList = new ArrayList<>();
        int colorIdx = 0;
        List<Map.Entry<String, Integer>> sortedHome = homeMap.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .toList();

        for (Map.Entry<String, Integer> e : sortedHome) {
            homeList.add(DemographicsReportDto.HometownStatDto.builder()
                    .city(e.getKey())
                    .count(e.getValue())
                    .percent(totalTenants > 0 ? (int) Math.round(((double) e.getValue() / totalTenants) * 100) : 0)
                    .color(PALETTE.get(colorIdx % PALETTE.size()))
                    .build());
            colorIdx++;
        }

        return DemographicsReportDto.builder()
                .professions(profList)
                .hometowns(homeList)
                .build();
    }
}
