package com.rms.backend.vacate.service;

import com.rms.backend.exception.ResourceNotFoundException;
import com.rms.backend.rooms.entity.Room;
import com.rms.backend.rooms.repository.RoomRepository;
import com.rms.backend.tenants.entity.Tenant;
import com.rms.backend.tenants.repository.TenantRepository;
import com.rms.backend.vacate.dto.VacateChargeUpdateDto;
import com.rms.backend.vacate.dto.VacateRequestDto;
import com.rms.backend.vacate.dto.VacateResponseDto;
import com.rms.backend.vacate.entity.VacateRequest;
import com.rms.backend.vacate.entity.VacateStatus;
import com.rms.backend.vacate.repository.VacateRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VacateRequestService {

    private final VacateRequestRepository vacateRepository;
    private final TenantRepository tenantRepository;
    private final RoomRepository roomRepository;

    @Transactional
    public VacateResponseDto submitVacateRequest(VacateRequestDto dto) {
        log.info("Processing vacate request submission: {}", dto);

        // 1. Resolve tenant
        Tenant tenant = null;
        if (dto.getTenantUid() != null && !dto.getTenantUid().isBlank()) {
            tenant = tenantRepository.findById(dto.getTenantUid().trim()).orElse(null);
        }
        if (tenant == null && dto.getAadhaarNo() != null && !dto.getAadhaarNo().isBlank()) {
            tenant = tenantRepository.findByAadhaarNo(dto.getAadhaarNo().trim()).orElse(null);
        }
        if (tenant == null && dto.getMobileNumber() != null && !dto.getMobileNumber().isBlank()) {
            tenant = tenantRepository.findByMobileNumber(dto.getMobileNumber().trim()).orElse(null);
        }

        // 2. Resolve identifiers and advance amount
        String tenantUid = (tenant != null) ? tenant.getUid() : dto.getTenantUid();
        String tenantName = (dto.getTenantName() != null && !dto.getTenantName().isBlank())
                ? dto.getTenantName().trim()
                : (tenant != null ? tenant.getName() : "Resident");
        String roomNo = (dto.getRoomNo() != null && !dto.getRoomNo().isBlank())
                ? dto.getRoomNo().trim()
                : (tenant != null ? tenant.getRoomNo() : "N/A");
        String mobile = (dto.getMobileNumber() != null && !dto.getMobileNumber().isBlank())
                ? dto.getMobileNumber().trim()
                : (tenant != null ? tenant.getMobileNumber() : "");
        String aadhaar = (dto.getAadhaarNo() != null && !dto.getAadhaarNo().isBlank())
                ? dto.getAadhaarNo().trim()
                : (tenant != null ? tenant.getAadhaarNo() : "");
        Long propertyId = dto.getPropertyId() != null
                ? dto.getPropertyId()
                : (tenant != null ? tenant.getPropertyId() : null);

        double advancePaid = (dto.getAdvancePaid() != null && dto.getAdvancePaid() > 0)
                ? dto.getAdvancePaid()
                : (tenant != null && tenant.getAdvancePaid() != null ? tenant.getAdvancePaid().doubleValue() : 0.0);

        // 3. Resolve dates
        LocalDate reqDate = (dto.getRequestDate() != null) ? dto.getRequestDate() : LocalDate.now();
        LocalDate leaveDate = (dto.getExpectedLeavingDate() != null) ? dto.getExpectedLeavingDate() : reqDate.plusDays(30);

        // Calculate notice days
        long daysBetween = ChronoUnit.DAYS.between(reqDate, leaveDate);
        int noticeDays = (int) Math.max(0, daysBetween);

        double maintenance = (dto.getMaintenanceCharge() != null) ? dto.getMaintenanceCharge() : 0.0;
        double breakage = (dto.getBreakageCharge() != null) ? dto.getBreakageCharge() : 0.0;

        // 4. Repay calculation formula
        double repayable = calculateAdvanceRepayable(advancePaid, noticeDays, maintenance, breakage);

        // 5. Generate Request ID
        String requestId = generateRequestId();

        VacateRequest vacateRequest = VacateRequest.builder()
                .requestId(requestId)
                .tenantUid(tenantUid)
                .tenantName(tenantName)
                .roomNo(roomNo)
                .mobileNumber(mobile)
                .aadhaarNo(aadhaar)
                .propertyId(propertyId)
                .requestDate(reqDate)
                .expectedLeavingDate(leaveDate)
                .noticeDays(noticeDays)
                .advancePaid(advancePaid)
                .maintenanceCharge(maintenance)
                .breakageCharge(breakage)
                .advanceRepayable(repayable)
                .status(VacateStatus.PENDING)
                .reason(dto.getReason())
                .notes(dto.getNotes())
                .createdAt(LocalDateTime.now())
                .build();

        VacateRequest saved = vacateRepository.save(vacateRequest);
        log.info("Vacate request successfully created: ID={}, RequestId={}", saved.getId(), saved.getRequestId());

        return toResponseDto(saved);
    }

    /**
     * Advance Repayable Formula:
     * If noticeDays >= 30: Full advance repayable minus charges.
     * If noticeDays < 30: (advance_paid / 30.0) * noticeDays minus charges.
     */
    public static double calculateAdvanceRepayable(double advancePaid, int noticeDays, double maintenance, double breakage) {
        if (advancePaid <= 0) {
            return 0.0;
        }

        double grossRepay;
        if (noticeDays >= 30) {
            grossRepay = advancePaid;
        } else {
            grossRepay = (advancePaid / 30.0) * noticeDays;
        }

        double totalDeductions = maintenance + breakage;
        double netRepay = grossRepay - totalDeductions;
        return Math.max(0.0, Math.round(netRepay * 100.0) / 100.0);
    }

    @Transactional(readOnly = true)
    public List<VacateResponseDto> getAllVacateRequests(VacateStatus status, Long propertyId) {
        List<VacateRequest> list;
        if (status != null) {
            list = vacateRepository.findByStatusOrderByCreatedAtDesc(status);
        } else {
            list = vacateRepository.findAllByOrderByCreatedAtDesc();
        }

        if (propertyId != null) {
            list = list.stream()
                    .filter(v -> propertyId.equals(v.getPropertyId()))
                    .collect(Collectors.toList());
        }

        return list.stream().map(this::toResponseDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public VacateResponseDto getVacateRequestById(Long id) {
        VacateRequest v = vacateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vacate request not found with ID: " + id));
        return toResponseDto(v);
    }

    @Transactional
    public VacateResponseDto approveVacateRequest(Long id) {
        VacateRequest v = vacateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vacate request not found with ID: " + id));

        v.setStatus(VacateStatus.APPROVED);
        v.setUpdatedAt(LocalDateTime.now());

        // Update room status to vacate_notice and attach vacateDate & vacatingResident
        if (v.getRoomNo() != null && !v.getRoomNo().isBlank()) {
            roomRepository.findById(v.getRoomNo()).ifPresent(room -> {
                room.setVacateStatus("vacate_notice");
                room.setVacateDate(v.getExpectedLeavingDate().toString());
                room.setVacatingResident(v.getTenantName());
                roomRepository.save(room);
                log.info("Room {} marked with vacate notice for {}", room.getRoomNo(), v.getExpectedLeavingDate());
            });
        }

        VacateRequest updated = vacateRepository.save(v);
        return toResponseDto(updated);
    }

    @Transactional
    public VacateResponseDto rejectVacateRequest(Long id, String reason) {
        VacateRequest v = vacateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vacate request not found with ID: " + id));

        v.setStatus(VacateStatus.REJECTED);
        v.setNotes((v.getNotes() != null ? v.getNotes() + " | " : "") + "Rejected: " + (reason != null ? reason : ""));
        v.setUpdatedAt(LocalDateTime.now());

        VacateRequest updated = vacateRepository.save(v);
        return toResponseDto(updated);
    }

    @Transactional
    public VacateResponseDto updateCharges(Long id, VacateChargeUpdateDto dto) {
        VacateRequest v = vacateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vacate request not found with ID: " + id));

        if (dto.getMaintenanceCharge() != null) {
            v.setMaintenanceCharge(dto.getMaintenanceCharge());
        }
        if (dto.getBreakageCharge() != null) {
            v.setBreakageCharge(dto.getBreakageCharge());
        }
        if (dto.getNotes() != null) {
            v.setNotes(dto.getNotes());
        }

        // Recalculate
        double newRepayable = calculateAdvanceRepayable(
                v.getAdvancePaid(),
                v.getNoticeDays(),
                v.getMaintenanceCharge(),
                v.getBreakageCharge()
        );
        v.setAdvanceRepayable(newRepayable);
        v.setUpdatedAt(LocalDateTime.now());

        VacateRequest updated = vacateRepository.save(v);
        return toResponseDto(updated);
    }

    @Transactional
    public VacateResponseDto completeVacate(Long id) {
        VacateRequest v = vacateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vacate request not found with ID: " + id));

        v.setStatus(VacateStatus.COMPLETED);
        v.setUpdatedAt(LocalDateTime.now());

        // 1. Mark tenant INACTIVE
        if (v.getTenantUid() != null) {
            tenantRepository.findById(v.getTenantUid()).ifPresent(t -> {
                t.setStatus("INACTIVE");
                tenantRepository.save(t);
                log.info("Tenant {} marked INACTIVE upon vacate completion", t.getUid());
            });
        }

        // 2. Clear room vacate status and adjust occupancy
        if (v.getRoomNo() != null) {
            roomRepository.findById(v.getRoomNo()).ifPresent(room -> {
                room.setVacateStatus(null);
                room.setVacateDate(null);
                room.setVacatingResident(null);
                long activeCount = tenantRepository.countActiveByRoomNo(room.getRoomNo());
                room.setCurrentOccupancy((int) activeCount);
                room.recalculateAvailability();
                roomRepository.save(room);
                log.info("Room {} occupancy updated to {}", room.getRoomNo(), room.getCurrentOccupancy());
            });
        }

        VacateRequest updated = vacateRepository.save(v);
        return toResponseDto(updated);
    }

    private String generateRequestId() {
        Optional<VacateRequest> last = vacateRepository.findTopByOrderByIdDesc();
        long nextId = last.map(r -> r.getId() + 1).orElse(1001L);
        return String.format("VR-%d", nextId);
    }

    private VacateResponseDto toResponseDto(VacateRequest v) {
        String breakdown;
        if (v.getNoticeDays() >= 30) {
            breakdown = String.format("Full advance: ₹%.0f - Charges (₹%.0f + ₹%.0f) = ₹%.2f",
                    v.getAdvancePaid(), v.getMaintenanceCharge(), v.getBreakageCharge(), v.getAdvanceRepayable());
        } else {
            breakdown = String.format("Notice < 30 days (%d days): (₹%.0f / 30) * %d - Charges (₹%.0f + ₹%.0f) = ₹%.2f",
                    v.getNoticeDays(), v.getAdvancePaid(), v.getNoticeDays(),
                    v.getMaintenanceCharge(), v.getBreakageCharge(), v.getAdvanceRepayable());
        }

        return VacateResponseDto.builder()
                .id(v.getId())
                .requestId(v.getRequestId())
                .tenantUid(v.getTenantUid())
                .tenantName(v.getTenantName())
                .roomNo(v.getRoomNo())
                .mobileNumber(v.getMobileNumber())
                .aadhaarNo(v.getAadhaarNo())
                .propertyId(v.getPropertyId())
                .requestDate(v.getRequestDate())
                .expectedLeavingDate(v.getExpectedLeavingDate())
                .noticeDays(v.getNoticeDays())
                .advancePaid(v.getAdvancePaid())
                .maintenanceCharge(v.getMaintenanceCharge())
                .breakageCharge(v.getBreakageCharge())
                .advanceRepayable(v.getAdvanceRepayable())
                .status(v.getStatus())
                .reason(v.getReason())
                .notes(v.getNotes())
                .calculationBreakdown(breakdown)
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .build();
    }
}
