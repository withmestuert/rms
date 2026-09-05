package com.rms.backend.admissions.service;

import com.rms.backend.admissions.dto.AdmissionRequestDto;
import com.rms.backend.admissions.dto.AdmissionResponseDto;
import com.rms.backend.admissions.entity.Admission;
import com.rms.backend.admissions.entity.AdmissionStatus;
import com.rms.backend.admissions.repository.AdmissionRepository;
import com.rms.backend.exception.DuplicateResourceException;
import com.rms.backend.exception.ResourceNotFoundException;
import com.rms.backend.rooms.entity.Room;
import com.rms.backend.rooms.repository.RoomRepository;
import com.rms.backend.tenants.entity.AdvancePaidStatus;
import com.rms.backend.tenants.entity.Tenant;
import com.rms.backend.tenants.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdmissionServiceTest {

    @Mock
    private AdmissionRepository admissionRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private AdmissionService admissionService;

    private Room sampleRoom;
    private Tenant sampleTenant;
    private Admission sampleAdmission;
    private AdmissionRequestDto newTenantRequest;
    private AdmissionRequestDto existingTenantRequest;

    @BeforeEach
    void setUp() {
        sampleRoom = new Room();
        sampleRoom.setRoomNo("101");
        sampleRoom.setOccupancy(2);
        sampleRoom.setCurrentOccupancy(0);
        sampleRoom.setReservedCapacity(0);
        sampleRoom.setAvailable(true);
        sampleRoom.setRentPerMonth(7500);

        sampleTenant = new Tenant();
        sampleTenant.setUid("T-001");
        sampleTenant.setName("Rahul Kumar");
        sampleTenant.setAadhaarNo("1234-5678-9012");
        sampleTenant.setMobileNumber("9876543210");
        sampleTenant.setTenantType("Working");
        sampleTenant.setOrganizationName("Infosys");
        sampleTenant.setRoomNo("101");
        sampleTenant.setAdvancePaid(10000);
        sampleTenant.setAdvancePaidStatus(AdvancePaidStatus.PENDING);
        sampleTenant.setStandardRent(7500);

        sampleAdmission = new Admission();
        sampleAdmission.setId(1001L);
        sampleAdmission.setAdmissionNumber("ADM-1001");
        sampleAdmission.setTenant(sampleTenant);
        sampleAdmission.setRoom(sampleRoom);
        sampleAdmission.setStatus(AdmissionStatus.PENDING);
        sampleAdmission.setEnrollmentDate(LocalDate.now());
        sampleAdmission.setCreatedAt(LocalDateTime.now());
        sampleAdmission.setUpdatedAt(LocalDateTime.now());

        // Request that creates a brand-new tenant
        newTenantRequest = new AdmissionRequestDto();
        newTenantRequest.setRoomNo("101");
        newTenantRequest.setName("Rahul Kumar");
        newTenantRequest.setAadhaarNo("1234-5678-9012");
        newTenantRequest.setMobileNumber("9876543210");
        newTenantRequest.setTenantType("Working");
        newTenantRequest.setOrganizationName("Infosys");
        newTenantRequest.setAdvancePaid(10000);
        newTenantRequest.setStandardRent(7500);

        // Request that re-enrolls an existing tenant by UID
        existingTenantRequest = new AdmissionRequestDto();
        existingTenantRequest.setRoomNo("101");
        existingTenantRequest.setTenantUid("T-001");
    }

    // =========================================================================
    // createEnrollment tests
    // =========================================================================

    @Test
    @DisplayName("Successfully enroll a new tenant — room reserved and status PENDING")
    void testCreateEnrollment_NewTenant_Success() {
        when(roomRepository.findByRoomNoWithLock("101")).thenReturn(Optional.of(sampleRoom));
        when(tenantRepository.findByAadhaarNo("1234-5678-9012")).thenReturn(Optional.empty());
        when(tenantRepository.findByMobileNumber("9876543210")).thenReturn(Optional.empty());
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> i.getArguments()[0]);
        when(admissionRepository.countByTenant_UidAndStatusIn(anyString(), anyList())).thenReturn(0L);
        when(admissionRepository.findTopByOrderByIdDesc()).thenReturn(Optional.empty());
        when(admissionRepository.save(any(Admission.class))).thenAnswer(i -> i.getArguments()[0]);
        when(roomRepository.save(any(Room.class))).thenAnswer(i -> i.getArguments()[0]);

        AdmissionResponseDto response = admissionService.createEnrollment(newTenantRequest);

        assertNotNull(response);
        assertEquals(AdmissionStatus.PENDING, response.getStatus());
        assertEquals("101", response.getRoomNo());
        assertEquals(1, sampleRoom.getReservedCapacity()); // reservation incremented
        verify(admissionRepository).save(any(Admission.class));
        verify(roomRepository).save(sampleRoom);
    }

    @Test
    @DisplayName("Successfully enroll an existing tenant identified by UID")
    void testCreateEnrollment_ExistingTenant_ByUid_Success() {
        when(roomRepository.findByRoomNoWithLock("101")).thenReturn(Optional.of(sampleRoom));
        when(tenantRepository.findById("T-001")).thenReturn(Optional.of(sampleTenant));
        when(admissionRepository.countByTenant_UidAndStatusIn("T-001", List.of(AdmissionStatus.PENDING, AdmissionStatus.PAID)))
                .thenReturn(0L);
        when(admissionRepository.findTopByOrderByIdDesc()).thenReturn(Optional.empty());
        when(admissionRepository.save(any(Admission.class))).thenAnswer(i -> i.getArguments()[0]);
        when(roomRepository.save(any(Room.class))).thenAnswer(i -> i.getArguments()[0]);

        AdmissionResponseDto response = admissionService.createEnrollment(existingTenantRequest);

        assertNotNull(response);
        assertEquals("T-001", response.getTenantUid());
        assertEquals(AdmissionStatus.PENDING, response.getStatus());
        verify(tenantRepository, never()).save(any(Tenant.class)); // no new tenant saved
    }

    @Test
    @DisplayName("Reject enrollment when room has no available capacity")
    void testCreateEnrollment_RoomFull_ThrowsDuplicateResource() {
        sampleRoom.setCurrentOccupancy(1);
        sampleRoom.setReservedCapacity(1); // fully occupied (2/2)
        when(roomRepository.findByRoomNoWithLock("101")).thenReturn(Optional.of(sampleRoom));

        assertThrows(DuplicateResourceException.class,
                () -> admissionService.createEnrollment(newTenantRequest));

        verify(admissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reject enrollment when room does not exist")
    void testCreateEnrollment_RoomNotFound_ThrowsResourceNotFound() {
        when(roomRepository.findByRoomNoWithLock("101")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> admissionService.createEnrollment(newTenantRequest));
    }

    @Test
    @DisplayName("Reject enrollment when tenant already has an active admission")
    void testCreateEnrollment_TenantHasActiveAdmission_ThrowsDuplicateResource() {
        when(roomRepository.findByRoomNoWithLock("101")).thenReturn(Optional.of(sampleRoom));
        when(tenantRepository.findById("T-001")).thenReturn(Optional.of(sampleTenant));
        when(admissionRepository.countByTenant_UidAndStatusIn("T-001", List.of(AdmissionStatus.PENDING, AdmissionStatus.PAID)))
                .thenReturn(1L);

        assertThrows(DuplicateResourceException.class,
                () -> admissionService.createEnrollment(existingTenantRequest));

        verify(admissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reject enrollment for new tenant when name is missing")
    void testCreateEnrollment_NewTenant_MissingName_ThrowsIllegalArgument() {
        when(roomRepository.findByRoomNoWithLock("101")).thenReturn(Optional.of(sampleRoom));
        when(tenantRepository.findByAadhaarNo("1234-5678-9012")).thenReturn(Optional.empty());
        when(tenantRepository.findByMobileNumber("9876543210")).thenReturn(Optional.empty());

        newTenantRequest.setName(null);

        assertThrows(IllegalArgumentException.class,
                () -> admissionService.createEnrollment(newTenantRequest));
    }

    // =========================================================================
    // confirmAdmission tests
    // =========================================================================

    @Test
    @DisplayName("Successfully confirm a PENDING admission — transitions to PAID")
    void testConfirmAdmission_Success() {
        when(admissionRepository.findByAdmissionNumber("ADM-1001"))
                .thenReturn(Optional.of(sampleAdmission));
        when(roomRepository.findByRoomNoWithLock("101")).thenReturn(Optional.of(sampleRoom));
        when(admissionRepository.save(any(Admission.class))).thenAnswer(i -> i.getArguments()[0]);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> i.getArguments()[0]);
        when(roomRepository.save(any(Room.class))).thenAnswer(i -> i.getArguments()[0]);

        sampleRoom.setReservedCapacity(1);

        AdmissionResponseDto response = admissionService.confirmAdmission("ADM-1001");

        assertEquals(AdmissionStatus.PAID, response.getStatus());
        assertEquals(AdvancePaidStatus.PAID, sampleTenant.getAdvancePaidStatus());
        assertEquals(0, sampleRoom.getReservedCapacity()); // reservation released
        assertEquals(1, sampleRoom.getCurrentOccupancy()); // occupancy incremented
        assertNotNull(sampleAdmission.getConfirmedOn());
    }

    @Test
    @DisplayName("Reject confirm when admission is already PAID")
    void testConfirmAdmission_AlreadyPaid_ThrowsDuplicateResource() {
        sampleAdmission.setStatus(AdmissionStatus.PAID);
        when(admissionRepository.findByAdmissionNumber("ADM-1001"))
                .thenReturn(Optional.of(sampleAdmission));

        assertThrows(DuplicateResourceException.class,
                () -> admissionService.confirmAdmission("ADM-1001"));
    }

    @Test
    @DisplayName("Reject confirm when admission is CANCELLED")
    void testConfirmAdmission_Cancelled_ThrowsDuplicateResource() {
        sampleAdmission.setStatus(AdmissionStatus.CANCELLED);
        when(admissionRepository.findByAdmissionNumber("ADM-1001"))
                .thenReturn(Optional.of(sampleAdmission));

        assertThrows(DuplicateResourceException.class,
                () -> admissionService.confirmAdmission("ADM-1001"));
    }

    @Test
    @DisplayName("Reject confirm when admission number not found")
    void testConfirmAdmission_NotFound_ThrowsResourceNotFound() {
        when(admissionRepository.findByAdmissionNumber("ADM-9999"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> admissionService.confirmAdmission("ADM-9999"));
    }

    // =========================================================================
    // cancelPendingAdmission tests
    // =========================================================================

    @Test
    @DisplayName("Successfully cancel a PENDING admission — tenant deleted when no history")
    void testCancelPendingAdmission_NoHistory_TenantDeleted() {
        sampleRoom.setReservedCapacity(1);
        when(admissionRepository.findByAdmissionNumber("ADM-1001"))
                .thenReturn(Optional.of(sampleAdmission));
        when(roomRepository.findByRoomNoWithLock("101")).thenReturn(Optional.of(sampleRoom));
        when(admissionRepository.countByTenant_UidAndAdmissionNumberNot("T-001", "ADM-1001"))
                .thenReturn(0L);

        admissionService.cancelPendingAdmission("ADM-1001");

        verify(admissionRepository).delete(sampleAdmission);
        verify(tenantRepository).delete(sampleTenant);
        assertEquals(0, sampleRoom.getReservedCapacity()); // slot freed
    }

    @Test
    @DisplayName("Cancel PENDING admission — tenant preserved when other admissions exist")
    void testCancelPendingAdmission_WithHistory_TenantPreserved() {
        sampleRoom.setReservedCapacity(1);
        when(admissionRepository.findByAdmissionNumber("ADM-1001"))
                .thenReturn(Optional.of(sampleAdmission));
        when(roomRepository.findByRoomNoWithLock("101")).thenReturn(Optional.of(sampleRoom));
        when(admissionRepository.countByTenant_UidAndAdmissionNumberNot("T-001", "ADM-1001"))
                .thenReturn(2L);

        admissionService.cancelPendingAdmission("ADM-1001");

        verify(admissionRepository).delete(sampleAdmission);
        verify(tenantRepository, never()).delete(any(Tenant.class));
    }

    @Test
    @DisplayName("Reject cancellation of a PAID admission")
    void testCancelPendingAdmission_PaidAdmission_ThrowsDuplicateResource() {
        sampleAdmission.setStatus(AdmissionStatus.PAID);
        when(admissionRepository.findByAdmissionNumber("ADM-1001"))
                .thenReturn(Optional.of(sampleAdmission));

        assertThrows(DuplicateResourceException.class,
                () -> admissionService.cancelPendingAdmission("ADM-1001"));

        verify(admissionRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Reject cancellation when admission not found")
    void testCancelPendingAdmission_NotFound_ThrowsResourceNotFound() {
        when(admissionRepository.findByAdmissionNumber("ADM-9999"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> admissionService.cancelPendingAdmission("ADM-9999"));
    }

    // =========================================================================
    // getAdmissionByNumber tests
    // =========================================================================

    @Test
    @DisplayName("Get admission by number returns response DTO when found")
    void testGetAdmissionByNumber_Found() {
        when(admissionRepository.findByAdmissionNumber("ADM-1001"))
                .thenReturn(Optional.of(sampleAdmission));

        AdmissionResponseDto response = admissionService.getAdmissionByNumber("ADM-1001");

        assertEquals("ADM-1001", response.getAdmissionNumber());
        assertEquals("T-001", response.getTenantUid());
    }

    @Test
    @DisplayName("Get admission by number throws ResourceNotFoundException when not found")
    void testGetAdmissionByNumber_NotFound() {
        when(admissionRepository.findByAdmissionNumber("ADM-9999"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> admissionService.getAdmissionByNumber("ADM-9999"));
    }

    // =========================================================================
    // Room capacity math
    // =========================================================================

    @Test
    @DisplayName("Room has available capacity when currentOccupancy + reservedCapacity < occupancy")
    void testRoomCapacityMath_Available() {
        sampleRoom.setOccupancy(2);
        sampleRoom.setCurrentOccupancy(0);
        sampleRoom.setReservedCapacity(0);
        assertTrue(sampleRoom.hasAvailableCapacity());
        assertEquals(2, sampleRoom.getEffectiveAvailableCapacity());
    }

    @Test
    @DisplayName("Room has no available capacity when fully occupied")
    void testRoomCapacityMath_Full() {
        sampleRoom.setOccupancy(2);
        sampleRoom.setCurrentOccupancy(1);
        sampleRoom.setReservedCapacity(1);
        assertFalse(sampleRoom.hasAvailableCapacity());
        assertEquals(0, sampleRoom.getEffectiveAvailableCapacity());
    }
}
