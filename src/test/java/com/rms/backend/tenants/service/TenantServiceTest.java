package com.rms.backend.tenants.service;

import com.rms.backend.admissions.entity.Admission;
import com.rms.backend.admissions.entity.AdmissionStatus;
import com.rms.backend.admissions.repository.AdmissionRepository;
import com.rms.backend.exception.DuplicateResourceException;
import com.rms.backend.exception.ResourceNotFoundException;
import com.rms.backend.rooms.entity.Room;
import com.rms.backend.rooms.repository.RoomRepository;
import com.rms.backend.tenants.dto.TenantRequestDto;
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private AdmissionRepository admissionRepository;

    @InjectMocks
    private TenantService tenantService;

    private TenantRequestDto sampleRequest;
    private Room sampleRoom;

    @BeforeEach
    void setUp() {
        sampleRequest = new TenantRequestDto();
        sampleRequest.setUid("T-101");
        sampleRequest.setName("Rahul Kumar");
        sampleRequest.setAadhaarNo("1234-5678-9012");
        sampleRequest.setMobileNumber("9876543210");
        sampleRequest.setTenantType("Working");
        sampleRequest.setOrganizationName("Infosys");
        sampleRequest.setParentContact("9123456780");
        sampleRequest.setRoomNo("101");
        sampleRequest.setAdvancePaid(10000);
        sampleRequest.setStandardRent(7500);

        sampleRoom = new Room();
        sampleRoom.setRoomNo("101");
        sampleRoom.setFloor("1st");
        sampleRoom.setRoomType("Double Sharing");
        sampleRoom.setRentPerMonth(7500);
        sampleRoom.setOccupancy(2);
        sampleRoom.setAvailable(true);
    }

    @Test
    @DisplayName("Successfully create tenant, assign to room, and set advancePaidStatus to PENDING")
    void testCreateTenantSuccess() {
        when(tenantRepository.existsById("T-101")).thenReturn(false);
        when(tenantRepository.existsByAadhaarNo("1234-5678-9012")).thenReturn(false);
        when(tenantRepository.existsByMobileNumber("9876543210")).thenReturn(false);
        when(roomRepository.findById("101")).thenReturn(Optional.of(sampleRoom));
        when(tenantRepository.countByRoomNo("101")).thenReturn(0L);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> i.getArguments()[0]);

        Tenant result = tenantService.createTenant(sampleRequest);

        assertNotNull(result);
        assertEquals("T-101", result.getUid());
        assertEquals("Rahul Kumar", result.getName());
        assertEquals("101", result.getRoomNo());
        assertEquals(10000, result.getAdvancePaid());
        assertEquals(AdvancePaidStatus.PENDING, result.getAdvancePaidStatus());
        assertEquals(7500, result.getStandardRent());
        verify(tenantRepository, times(1)).save(any(Tenant.class));
    }

    @Test
    @DisplayName("Verify advancePaidStatus is automatically set to PENDING upon creation")
    void testCreateTenantAdvancePaidStatusIsAlwaysPending() {
        when(tenantRepository.existsById("T-101")).thenReturn(false);
        when(tenantRepository.existsByAadhaarNo("1234-5678-9012")).thenReturn(false);
        when(tenantRepository.existsByMobileNumber("9876543210")).thenReturn(false);
        when(roomRepository.findById("101")).thenReturn(Optional.of(sampleRoom));
        when(tenantRepository.countByRoomNo("101")).thenReturn(0L);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> i.getArguments()[0]);

        Tenant created = tenantService.createTenant(sampleRequest);

        assertNotNull(created.getAdvancePaidStatus());
        assertEquals(AdvancePaidStatus.PENDING, created.getAdvancePaidStatus());
    }

    @Test
    @DisplayName("Create tenant marks room unavailable when occupancy capacity is reached")
    void testCreateTenantFillsRoomCapacity() {
        when(tenantRepository.existsById("T-101")).thenReturn(false);
        when(tenantRepository.existsByAadhaarNo("1234-5678-9012")).thenReturn(false);
        when(tenantRepository.existsByMobileNumber("9876543210")).thenReturn(false);
        when(roomRepository.findById("101")).thenReturn(Optional.of(sampleRoom));
        when(tenantRepository.countByRoomNo("101")).thenReturn(1L);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> i.getArguments()[0]);

        tenantService.createTenant(sampleRequest);

        assertFalse(sampleRoom.getAvailable());
        verify(roomRepository, times(1)).save(sampleRoom);
    }

    @Test
    @DisplayName("Reject tenant creation when room is already at maximum occupancy")
    void testCreateTenantRoomFull() {
        when(tenantRepository.existsById("T-101")).thenReturn(false);
        when(tenantRepository.existsByAadhaarNo("1234-5678-9012")).thenReturn(false);
        when(tenantRepository.existsByMobileNumber("9876543210")).thenReturn(false);
        when(roomRepository.findById("101")).thenReturn(Optional.of(sampleRoom));
        when(tenantRepository.countByRoomNo("101")).thenReturn(2L);

        assertThrows(DuplicateResourceException.class, () -> {
            tenantService.createTenant(sampleRequest);
        });

        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    @Test
    @DisplayName("Reject tenant creation when UID already exists")
    void testCreateTenantDuplicateUid() {
        when(tenantRepository.existsById("T-101")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> {
            tenantService.createTenant(sampleRequest);
        });
    }

    @Test
    @DisplayName("Reject tenant creation when Aadhaar already exists")
    void testCreateTenantDuplicateAadhaar() {
        when(tenantRepository.existsById("T-101")).thenReturn(false);
        when(tenantRepository.existsByAadhaarNo("1234-5678-9012")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> {
            tenantService.createTenant(sampleRequest);
        });
    }

    @Test
    @DisplayName("Reject tenant creation when mobile number already exists")
    void testCreateTenantDuplicateMobile() {
        when(tenantRepository.existsById("T-101")).thenReturn(false);
        when(tenantRepository.existsByAadhaarNo("1234-5678-9012")).thenReturn(false);
        when(tenantRepository.existsByMobileNumber("9876543210")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> {
            tenantService.createTenant(sampleRequest);
        });

        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    @Test
    @DisplayName("Reject tenant creation when room does not exist")
    void testCreateTenantRoomNotFound() {
        when(tenantRepository.existsById("T-101")).thenReturn(false);
        when(tenantRepository.existsByAadhaarNo("1234-5678-9012")).thenReturn(false);
        when(tenantRepository.existsByMobileNumber("9876543210")).thenReturn(false);
        when(roomRepository.findById("101")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            tenantService.createTenant(sampleRequest);
        });
    }

    @Test
    @DisplayName("Deleting tenant frees up room availability")
    void testDeleteTenantFreesRoom() {
        Tenant tenant = new Tenant();
        tenant.setUid("T-101");
        tenant.setRoomNo("101");

        sampleRoom.setAvailable(false);

        when(tenantRepository.findById("T-101")).thenReturn(Optional.of(tenant));
        when(roomRepository.findById("101")).thenReturn(Optional.of(sampleRoom));

        tenantService.deleteTenant("T-101");

        verify(tenantRepository, times(1)).delete(tenant);
        assertTrue(sampleRoom.getAvailable());
        verify(roomRepository, times(1)).save(sampleRoom);
    }

    @Test
    @DisplayName("verifyAdvancePayment updates status to PAID and updates amount when provided")
    void testVerifyAdvancePaymentWithoutPendingAdmissions() {
        Tenant tenant = new Tenant();
        tenant.setUid("T-101");
        tenant.setAdvancePaid(5000);
        tenant.setAdvancePaidStatus(AdvancePaidStatus.PENDING);

        when(tenantRepository.findById("T-101")).thenReturn(Optional.of(tenant));
        when(admissionRepository.findByTenant_Uid("T-101")).thenReturn(Collections.emptyList());
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> i.getArgument(0));

        Tenant result = tenantService.verifyAdvancePayment("T-101", 10000);

        assertNotNull(result);
        assertEquals(AdvancePaidStatus.PAID, result.getAdvancePaidStatus());
        assertEquals(10000, result.getAdvancePaid());
        verify(tenantRepository, times(1)).save(tenant);
    }

    @Test
    @DisplayName("verifyAdvancePayment confirms pending admission and adjusts room capacity")
    void testVerifyAdvancePaymentWithPendingAdmission() {
        Tenant tenant = new Tenant();
        tenant.setUid("T-101");
        tenant.setAdvancePaid(5000);
        tenant.setAdvancePaidStatus(AdvancePaidStatus.PENDING);

        Room room = new Room();
        room.setRoomNo("101");
        room.setOccupancy(2);
        room.setCurrentOccupancy(0);
        room.setReservedCapacity(1);
        room.setAvailable(true);

        Admission admission = new Admission();
        admission.setAdmissionNumber("ADM-001");
        admission.setStatus(AdmissionStatus.PENDING);
        admission.setRoom(room);

        when(tenantRepository.findById("T-101")).thenReturn(Optional.of(tenant));
        when(admissionRepository.findByTenant_Uid("T-101")).thenReturn(List.of(admission));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> i.getArgument(0));

        Tenant result = tenantService.verifyAdvancePayment("T-101", null);

        assertNotNull(result);
        assertEquals(AdvancePaidStatus.PAID, result.getAdvancePaidStatus());
        assertEquals(AdmissionStatus.PAID, admission.getStatus());
        assertNotNull(admission.getConfirmedOn());
        assertEquals(0, room.getReservedCapacity());
        assertEquals(1, room.getCurrentOccupancy());
        verify(admissionRepository, times(1)).save(admission);
        verify(roomRepository, times(1)).save(room);
        verify(tenantRepository, times(1)).save(tenant);
    }

    @Test
    @DisplayName("verifyAdvancePayment throws ResourceNotFoundException when tenant does not exist")
    void testVerifyAdvancePaymentNotFound() {
        when(tenantRepository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> tenantService.verifyAdvancePayment("nonexistent", 5000));
        verify(tenantRepository, never()).save(any());
    }
}
