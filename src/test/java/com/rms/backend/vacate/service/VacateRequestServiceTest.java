package com.rms.backend.vacate.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, com.rms.backend.security.OwnerTestContext.class})
class VacateRequestServiceTest {

    @Mock
    private VacateRequestRepository vacateRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private VacateRequestService vacateService;

    private Tenant sampleTenant;
    private Room sampleRoom;

    @BeforeEach
    void setUp() {
        sampleTenant = new Tenant();
        sampleTenant.setPropertyId(1L);
        sampleTenant.setUid("T-101");
        sampleTenant.setName("Rahul Kumar");
        sampleTenant.setAadhaarNo("123456789012");
        sampleTenant.setMobileNumber("9876543210");
        sampleTenant.setRoomNo("102");
        sampleTenant.setAdvancePaid(12000);
        sampleTenant.setStandardRent(8000);

        sampleRoom = new Room();
        sampleRoom.setPropertyId(1L);
        sampleRoom.setRoomNo("102");
        sampleRoom.setOccupancy(2);
        sampleRoom.setCurrentOccupancy(2);
        sampleRoom.setAvailable(false);
    }

    @Test
    @DisplayName("Repay calculation: 30 days notice gives 100% full refund")
    void testRepayCalculation30Days() {
        double advance = 12000.0;
        int noticeDays = 30;
        double repayable = VacateRequestService.calculateAdvanceRepayable(advance, noticeDays, 0, 0);
        assertEquals(12000.0, repayable, 0.01);
    }

    @Test
    @DisplayName("Repay calculation: 20 days notice pro-rates exactly (12000 / 30) * 20 = 8000")
    void testRepayCalculationLessThan30Days() {
        double advance = 12000.0;
        int noticeDays = 20;
        double repayable = VacateRequestService.calculateAdvanceRepayable(advance, noticeDays, 0, 0);
        assertEquals(8000.0, repayable, 0.01);
    }

    @Test
    @DisplayName("Repay calculation: Deducts maintenance and breakage charges")
    void testRepayCalculationWithDeductions() {
        double advance = 12000.0;
        int noticeDays = 20; // Gross = 8000
        double maintenance = 500.0;
        double breakage = 1000.0;
        double repayable = VacateRequestService.calculateAdvanceRepayable(advance, noticeDays, maintenance, breakage);
        assertEquals(6500.0, repayable, 0.01);
    }

    @Test
    @DisplayName("Repay calculation: Never drops below 0 when deductions exceed repayable amount")
    void testRepayCalculationLowerBoundZero() {
        double advance = 3000.0;
        int noticeDays = 10; // Gross = 1000
        double repayable = VacateRequestService.calculateAdvanceRepayable(advance, noticeDays, 800, 500);
        assertEquals(0.0, repayable, 0.01);
    }

    @Test
    @DisplayName("Submit vacate request resolves tenant by UID and computes pro-rated refund")
    void testSubmitVacateRequest() {
        when(tenantRepository.findById("T-101")).thenReturn(Optional.of(sampleTenant));
        when(vacateRepository.findTopByOrderByIdDesc()).thenReturn(Optional.empty());
        when(vacateRepository.save(any(VacateRequest.class))).thenAnswer(invocation -> {
            VacateRequest vr = invocation.getArgument(0);
            vr.setId(1L);
            return vr;
        });

        VacateRequestDto dto = VacateRequestDto.builder()
                .tenantUid("T-101")
                .requestDate(LocalDate.of(2026, 9, 5))
                .expectedLeavingDate(LocalDate.of(2026, 9, 25)) // 20 days notice
                .reason("Relocating for job")
                .build();

        VacateResponseDto result = vacateService.submitVacateRequest(dto);

        assertNotNull(result);
        assertEquals("VR-1001", result.getRequestId());
        assertEquals("Rahul Kumar", result.getTenantName());
        assertEquals("102", result.getRoomNo());
        assertEquals(20, result.getNoticeDays());
        assertEquals(8000.0, result.getAdvanceRepayable(), 0.01);
        assertEquals(VacateStatus.PENDING, result.getStatus());
    }

    @Test
    @DisplayName("Approve vacate request updates room status to vacate_notice")
    void testApproveVacateRequest() {
        VacateRequest request = VacateRequest.builder().propertyId(1L)
                .id(1L)
                .requestId("VR-1001")
                .tenantUid("T-101")
                .tenantName("Rahul Kumar")
                .roomNo("102")
                .requestDate(LocalDate.of(2026, 9, 5))
                .expectedLeavingDate(LocalDate.of(2026, 9, 25))
                .noticeDays(20)
                .advancePaid(12000.0)
                .advanceRepayable(8000.0)
                .status(VacateStatus.PENDING)
                .build();

        when(vacateRepository.findById(1L)).thenReturn(Optional.of(request));
        when(roomRepository.findById("102")).thenReturn(Optional.of(sampleRoom));
        when(vacateRepository.save(any(VacateRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VacateResponseDto approved = vacateService.approveVacateRequest(1L);

        assertEquals(VacateStatus.APPROVED, approved.getStatus());
        assertEquals("vacate_notice", sampleRoom.getVacateStatus());
        assertEquals("2026-09-25", sampleRoom.getVacateDate());
        assertEquals("Rahul Kumar", sampleRoom.getVacatingResident());
        verify(roomRepository, times(1)).save(sampleRoom);
    }
}
