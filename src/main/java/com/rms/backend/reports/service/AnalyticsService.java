package com.rms.backend.reports.service;

import com.rms.backend.admissions.entity.Admission;
import com.rms.backend.admissions.repository.AdmissionRepository;
import com.rms.backend.billing.entity.Invoice;
import com.rms.backend.billing.entity.InvoiceStatus;
import com.rms.backend.billing.entity.Payment;
import com.rms.backend.billing.repository.InvoiceRepository;
import com.rms.backend.billing.repository.PaymentRepository;
import com.rms.backend.properties.entity.Property;
import com.rms.backend.properties.repository.PropertyRepository;
import com.rms.backend.reports.dto.*;
import com.rms.backend.rooms.entity.Bed;
import com.rms.backend.rooms.entity.Room;
import com.rms.backend.rooms.repository.BedRepository;
import com.rms.backend.rooms.repository.RoomRepository;
import com.rms.backend.tenants.entity.Tenant;
import com.rms.backend.tenants.repository.TenantRepository;
import com.rms.backend.vacate.entity.VacateRequest;
import com.rms.backend.vacate.entity.VacateStatus;
import com.rms.backend.vacate.repository.VacateRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;
    private final TenantRepository tenantRepository;
    private final AdmissionRepository admissionRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final VacateRequestRepository vacateRequestRepository;

    @Transactional(readOnly = true)
    public AnalyticsKpiDto getKpis(Long propertyId) {
        List<Room> rooms = propertyId != null
                ? roomRepository.findByPropertyId(propertyId)
                : roomRepository.findAll();

        List<Long> roomIds = rooms.stream()
                .map(Room::getId)
                .filter(Objects::nonNull)
                .toList();

        List<Bed> beds = roomIds.isEmpty() ? Collections.emptyList() : bedRepository.findByRoomIdIn(roomIds);
        Map<Long, Room> roomMap = rooms.stream()
                .filter(r -> r.getId() != null)
                .collect(Collectors.toMap(Room::getId, r -> r, (r1, r2) -> r1));

        int totalBeds = beds.size();
        int occupiedBeds = 0;
        int reservedBeds = 0;
        int availableBeds = 0;
        int maintenanceBeds = 0;

        double potentialMonthlyRevenue = 0.0;
        double monthlyLostRevenue = 0.0;

        for (Bed bed : beds) {
            String status = bed.getStatus() != null ? bed.getStatus().toUpperCase() : "AVAILABLE";
            Room parentRoom = roomMap.get(bed.getRoomId());
            double bedRent = (bed.getBaseRent() != null && bed.getBaseRent() > 0)
                    ? bed.getBaseRent()
                    : (parentRoom != null && parentRoom.getRentPerMonth() != null ? parentRoom.getRentPerMonth() : 7000.0);

            potentialMonthlyRevenue += bedRent;

            switch (status) {
                case "OCCUPIED" -> occupiedBeds++;
                case "RESERVED" -> reservedBeds++;
                case "MAINTENANCE" -> {
                    maintenanceBeds++;
                    monthlyLostRevenue += bedRent;
                }
                default -> {
                    availableBeds++;
                    monthlyLostRevenue += bedRent;
                }
            }
        }

        double occupancyRate = totalBeds > 0
                ? Math.round(((double) occupiedBeds / totalBeds) * 1000.0) / 10.0
                : 0.0;

        List<Invoice> invoices = propertyId != null
                ? invoiceRepository.findByPropertyId(propertyId)
                : invoiceRepository.findAll();

        long totalBilled = invoices.stream().mapToLong(i -> i.getAmount() != null ? i.getAmount() : 0).sum();
        long totalCollected = invoices.stream().mapToLong(i -> i.getPaidAmount() != null ? i.getPaidAmount() : 0).sum();
        long totalPending = totalBilled - totalCollected;

        double collectionEfficiency = totalBilled > 0
                ? Math.round(((double) totalCollected / totalBilled) * 1000.0) / 10.0
                : 0.0;

        double potentialRevPab = totalBeds > 0
                ? Math.round((potentialMonthlyRevenue / totalBeds) * 10.0) / 10.0
                : 0.0;

        double realizedRevPab = totalBeds > 0
                ? Math.round(((potentialMonthlyRevenue - monthlyLostRevenue) / totalBeds) * 10.0) / 10.0
                : 0.0;

        double yieldRealizationRate = potentialMonthlyRevenue > 0
                ? Math.round(((potentialMonthlyRevenue - monthlyLostRevenue) / potentialMonthlyRevenue) * 1000.0) / 10.0
                : 0.0;

        List<Tenant> activeTenants = propertyId != null
                ? tenantRepository.findActiveByPropertyId(propertyId)
                : tenantRepository.findAllActive();

        return AnalyticsKpiDto.builder()
                .totalBeds(totalBeds)
                .occupiedBeds(occupiedBeds)
                .reservedBeds(reservedBeds)
                .availableBeds(availableBeds)
                .maintenanceBeds(maintenanceBeds)
                .occupancyRate(occupancyRate)
                .totalBilledAmount(totalBilled)
                .totalCollectedAmount(totalCollected)
                .totalPendingAmount(totalPending)
                .collectionEfficiency(collectionEfficiency)
                .potentialMonthlyRevenue(potentialMonthlyRevenue)
                .potentialRevPab(potentialRevPab)
                .realizedRevPab(realizedRevPab)
                .monthlyLostRevenue(monthlyLostRevenue)
                .yieldRealizationRate(yieldRealizationRate)
                .activeTenantsCount(activeTenants.size())
                .build();
    }

    @Transactional(readOnly = true)
    public List<RoomTypeYieldDto> getRoomTypeYields(Long propertyId) {
        List<Room> rooms = propertyId != null
                ? roomRepository.findByPropertyId(propertyId)
                : roomRepository.findAll();

        List<Long> roomIds = rooms.stream().map(Room::getId).filter(Objects::nonNull).toList();
        List<Bed> beds = roomIds.isEmpty() ? Collections.emptyList() : bedRepository.findByRoomIdIn(roomIds);

        Map<Long, List<Bed>> roomBedsMap = beds.stream()
                .filter(b -> b.getRoomId() != null)
                .collect(Collectors.groupingBy(Bed::getRoomId));

        Map<String, List<Room>> roomsByType = rooms.stream()
                .collect(Collectors.groupingBy(r -> r.getRoomType() != null && !r.getRoomType().isBlank()
                        ? r.getRoomType().trim()
                        : "Standard"));

        List<RoomTypeYieldDto> result = new ArrayList<>();

        for (Map.Entry<String, List<Room>> entry : roomsByType.entrySet()) {
            String type = entry.getKey();
            List<Room> typeRooms = entry.getValue();

            int totalRooms = typeRooms.size();
            int totalBeds = 0;
            int occupiedBeds = 0;
            int availableBeds = 0;
            int reservedBeds = 0;
            double rentSum = 0;
            int rentCount = 0;

            for (Room r : typeRooms) {
                if (r.getRentPerMonth() != null && r.getRentPerMonth() > 0) {
                    rentSum += r.getRentPerMonth();
                    rentCount++;
                }

                List<Bed> rBeds = roomBedsMap.getOrDefault(r.getId(), Collections.emptyList());
                totalBeds += rBeds.size();
                for (Bed b : rBeds) {
                    String st = b.getStatus() != null ? b.getStatus().toUpperCase() : "AVAILABLE";
                    switch (st) {
                        case "OCCUPIED" -> occupiedBeds++;
                        case "RESERVED" -> reservedBeds++;
                        default -> availableBeds++;
                    }
                }
            }

            double avgRent = rentCount > 0 ? Math.round((rentSum / rentCount) * 10.0) / 10.0 : 7000.0;
            double potentialRev = totalBeds * avgRent;
            double realizedRev = occupiedBeds * avgRent;
            double lostRev = (totalBeds - occupiedBeds) * avgRent;
            double revPab = totalBeds > 0 ? Math.round((realizedRev / totalBeds) * 10.0) / 10.0 : 0.0;
            double occupancyRate = totalBeds > 0 ? Math.round(((double) occupiedBeds / totalBeds) * 1000.0) / 10.0 : 0.0;

            result.add(RoomTypeYieldDto.builder()
                    .roomType(type)
                    .totalRooms(totalRooms)
                    .totalBeds(totalBeds)
                    .occupiedBeds(occupiedBeds)
                    .availableBeds(availableBeds)
                    .reservedBeds(reservedBeds)
                    .occupancyRate(occupancyRate)
                    .averageRentPerBed(avgRent)
                    .potentialRevenue(potentialRev)
                    .realizedRevenue(realizedRev)
                    .lostRevenue(lostRev)
                    .revPab(revPab)
                    .build());
        }

        result.sort((a, b) -> Double.compare(b.getPotentialRevenue(), a.getPotentialRevenue()));
        return result;
    }

    @Transactional(readOnly = true)
    public List<RevenueTrendDto> getRevenueTrends(Long propertyId, int months) {
        List<Invoice> invoices = propertyId != null
                ? invoiceRepository.findByPropertyId(propertyId)
                : invoiceRepository.findAll();

        Map<String, List<Invoice>> monthMap = invoices.stream()
                .filter(i -> i.getMonthYear() != null && !i.getMonthYear().isBlank())
                .collect(Collectors.groupingBy(Invoice::getMonthYear));

        List<RevenueTrendDto> trends = new ArrayList<>();

        for (Map.Entry<String, List<Invoice>> entry : monthMap.entrySet()) {
            String mLabel = entry.getKey();
            List<Invoice> invList = entry.getValue();

            long billed = invList.stream().mapToLong(i -> i.getAmount() != null ? i.getAmount() : 0).sum();
            long collected = invList.stream().mapToLong(i -> i.getPaidAmount() != null ? i.getPaidAmount() : 0).sum();
            long pending = billed - collected;
            double rate = billed > 0 ? Math.round(((double) collected / billed) * 1000.0) / 10.0 : 0.0;
            long paymentsCount = invList.stream().filter(i -> i.getPaidAmount() != null && i.getPaidAmount() > 0).count();

            trends.add(RevenueTrendDto.builder()
                    .month(mLabel)
                    .monthLabel(mLabel)
                    .billedAmount(billed)
                    .collectedAmount(collected)
                    .pendingAmount(pending)
                    .collectionRate(rate)
                    .paymentsCount((int) paymentsCount)
                    .build());
        }

        // Return up to requested months
        if (trends.size() > months) {
            trends = trends.subList(trends.size() - months, trends.size());
        }
        return trends;
    }

    @Transactional(readOnly = true)
    public DefaulterAgingDto getDefaultersAging(Long propertyId) {
        List<Invoice> invoices = propertyId != null
                ? invoiceRepository.findByPropertyId(propertyId)
                : invoiceRepository.findAll();

        Map<String, Tenant> tenantMap = tenantRepository.findAll().stream()
                .collect(Collectors.toMap(Tenant::getUid, t -> t, (t1, t2) -> t1));

        Map<Long, Bed> bedMap = bedRepository.findAll().stream()
                .collect(Collectors.toMap(Bed::getId, b -> b, (b1, b2) -> b1));

        long currentOverdue = 0;
        long overdue16to30 = 0;
        long overdue31to60 = 0;
        long overdue60Plus = 0;
        long totalOverdueAmount = 0;

        List<DefaulterAgingDto.DefaulterItem> defaulters = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (Invoice inv : invoices) {
            int amount = inv.getAmount() != null ? inv.getAmount() : 0;
            int paid = inv.getPaidAmount() != null ? inv.getPaidAmount() : 0;
            int pending = amount - paid;

            if (pending <= 0 || inv.getStatus() == InvoiceStatus.PAID) {
                continue;
            }

            int daysOverdue = 0;
            if (inv.getDueDate() != null && !inv.getDueDate().isBlank()) {
                try {
                    LocalDate dueDate = LocalDate.parse(inv.getDueDate().trim().substring(0, 10));
                    if (dueDate.isBefore(today)) {
                        daysOverdue = (int) ChronoUnit.DAYS.between(dueDate, today);
                    }
                } catch (Exception ignored) {
                    daysOverdue = 5;
                }
            }

            String category;
            if (daysOverdue <= 15) {
                category = "0-15 Days";
                currentOverdue += pending;
            } else if (daysOverdue <= 30) {
                category = "16-30 Days";
                overdue16to30 += pending;
            } else if (daysOverdue <= 60) {
                category = "31-60 Days";
                overdue31to60 += pending;
            } else {
                category = "60+ Days";
                overdue60Plus += pending;
            }

            totalOverdueAmount += pending;

            Tenant t = tenantMap.get(inv.getTenantUid());
            String mobile = t != null ? t.getMobileNumber() : "";
            Long bId = t != null ? t.getBedId() : null;
            Bed bed = bId != null ? bedMap.get(bId) : null;
            String bedNum = bed != null ? bed.getBedNumber() : "-";

            defaulters.add(DefaulterAgingDto.DefaulterItem.builder()
                    .tenantUid(inv.getTenantUid())
                    .tenantName(inv.getTenantName())
                    .mobileNumber(mobile)
                    .roomNo(inv.getRoomNo())
                    .bedNumber(bedNum)
                    .bedId(bId)
                    .invoiceNumber(inv.getInvoiceNumber())
                    .monthYear(inv.getMonthYear())
                    .dueDate(inv.getDueDate())
                    .amount(amount)
                    .paidAmount(paid)
                    .pendingAmount(pending)
                    .daysOverdue(daysOverdue)
                    .agingCategory(category)
                    .build());
        }

        defaulters.sort((a, b) -> Integer.compare(b.getDaysOverdue(), a.getDaysOverdue()));

        DefaulterAgingDto.AgingSummary summary = DefaulterAgingDto.AgingSummary.builder()
                .currentOverdue(currentOverdue)
                .overdue16to30(overdue16to30)
                .overdue31to60(overdue31to60)
                .overdue60Plus(overdue60Plus)
                .totalOverdueAmount(totalOverdueAmount)
                .totalDefaultersCount(defaulters.size())
                .build();

        return DefaulterAgingDto.builder()
                .summary(summary)
                .defaulters(defaulters)
                .build();
    }

    @Transactional(readOnly = true)
    public SecurityDepositReportDto getSecurityDepositsReport(Long propertyId) {
        List<Tenant> tenants = propertyId != null
                ? tenantRepository.findActiveByPropertyId(propertyId)
                : tenantRepository.findAllActive();

        Map<Long, Bed> bedMap = bedRepository.findAll().stream()
                .collect(Collectors.toMap(Bed::getId, b -> b, (b1, b2) -> b1));

        List<VacateRequest> vacateRequests = propertyId != null
                ? vacateRequestRepository.findByPropertyIdOrderByCreatedAtDesc(propertyId)
                : vacateRequestRepository.findAllByOrderByCreatedAtDesc();

        Map<String, VacateRequest> vacateMap = new HashMap<>();
        for (VacateRequest vr : vacateRequests) {
            if (vr.getTenantUid() != null && !vacateMap.containsKey(vr.getTenantUid())) {
                vacateMap.put(vr.getTenantUid(), vr);
            }
        }

        long totalDeposits = 0;
        long refundLiability = 0;
        int tenantsWithDeposits = 0;
        List<SecurityDepositReportDto.DepositItem> items = new ArrayList<>();

        for (Tenant t : tenants) {
            int deposit = t.getAdvancePaid() != null ? t.getAdvancePaid() : 0;
            if (deposit > 0) {
                totalDeposits += deposit;
                tenantsWithDeposits++;
            }

            Bed b = t.getBedId() != null ? bedMap.get(t.getBedId()) : null;
            String bedNum = b != null ? b.getBedNumber() : "-";

            VacateRequest vr = vacateMap.get(t.getUid());
            String vacateStatus = "ACTIVE";
            double repayable = deposit;

            if (vr != null) {
                if (vr.getStatus() == VacateStatus.PENDING) {
                    vacateStatus = "NOTICE_PENDING";
                    repayable = vr.getAdvanceRepayable() != null ? vr.getAdvanceRepayable() : deposit;
                    refundLiability += (long) repayable;
                } else if (vr.getStatus() == VacateStatus.APPROVED) {
                    vacateStatus = "NOTICE_APPROVED";
                    repayable = vr.getAdvanceRepayable() != null ? vr.getAdvanceRepayable() : deposit;
                    refundLiability += (long) repayable;
                } else if (vr.getStatus() == VacateStatus.COMPLETED) {
                    vacateStatus = "VACATED";
                    repayable = 0;
                }
            }

            items.add(SecurityDepositReportDto.DepositItem.builder()
                    .tenantUid(t.getUid())
                    .tenantName(t.getName())
                    .mobileNumber(t.getMobileNumber())
                    .roomNo(t.getRoomNo())
                    .bedNumber(bedNum)
                    .depositAmount(deposit)
                    .advanceStatus(t.getAdvancePaidStatus() != null ? t.getAdvancePaidStatus().name() : "PAID")
                    .checkInDate(t.getRoomNo() != null ? "Active" : "-")
                    .vacateStatus(vacateStatus)
                    .repayableEstimate(repayable)
                    .build());
        }

        long netRetained = totalDeposits - refundLiability;

        return SecurityDepositReportDto.builder()
                .totalDepositsHeld(totalDeposits)
                .totalTenantsWithDeposits(tenantsWithDeposits)
                .pendingRefundLiability(refundLiability)
                .netRetainedDeposits(netRetained)
                .deposits(items)
                .build();
    }

    @Transactional(readOnly = true)
    public TurnoverAnalyticsDto getTurnoverAnalytics(Long propertyId) {
        List<Admission> admissions = propertyId != null
                ? admissionRepository.findByPropertyId(propertyId)
                : admissionRepository.findAll();

        List<VacateRequest> vacates = propertyId != null
                ? vacateRequestRepository.findByPropertyIdOrderByCreatedAtDesc(propertyId)
                : vacateRequestRepository.findAllByOrderByCreatedAtDesc();

        Map<String, Integer> vacateReasons = new HashMap<>();
        for (VacateRequest vr : vacates) {
            String r = vr.getReason() != null && !vr.getReason().isBlank() ? vr.getReason().trim() : "Other / Relocation";
            vacateReasons.put(r, vacateReasons.getOrDefault(r, 0) + 1);
        }

        int totalAdm = admissions.size();
        int totalVac = (int) vacates.stream().filter(v -> v.getStatus() == VacateStatus.COMPLETED).count();

        double retentionRate = totalAdm > 0
                ? Math.round((Math.max(0, totalAdm - totalVac) / (double) totalAdm) * 1000.0) / 10.0
                : 100.0;

        return TurnoverAnalyticsDto.builder()
                .averageStayDurationMonths(7.5) // Indicative historical benchmark
                .totalAdmissionsCount(totalAdm)
                .totalVacatesCount(totalVac)
                .retentionRate(retentionRate)
                .vacateReasons(vacateReasons)
                .build();
    }
}
