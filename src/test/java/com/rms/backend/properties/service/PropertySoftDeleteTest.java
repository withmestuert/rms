package com.rms.backend.properties.service;

import com.rms.backend.properties.dto.PropertyArchiveSnapshotDto;
import com.rms.backend.properties.dto.PropertyResponseDto;
import com.rms.backend.properties.dto.PropertySoftDeleteRequestDto;
import com.rms.backend.properties.entity.Property;
import com.rms.backend.properties.entity.PropertyArchiveSnapshot;
import com.rms.backend.properties.repository.PropertyArchiveSnapshotRepository;
import com.rms.backend.properties.repository.PropertyRepository;
import com.rms.backend.rooms.entity.Room;
import com.rms.backend.rooms.repository.RoomRepository;
import com.rms.backend.tenants.entity.Tenant;
import com.rms.backend.tenants.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PropertySoftDeleteTest {

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private PropertyArchiveSnapshotRepository snapshotRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private PropertyService propertyService;

    private Property sampleProperty;
    private Room sampleRoom;
    private Tenant sampleTenant;

    @BeforeEach
    void setUp() {
        sampleProperty = new Property();
        sampleProperty.setId(10L);
        sampleProperty.setName("Olive Green PG");
        sampleProperty.setCode("OG-PG-10");
        sampleProperty.setStatus("ACTIVE");
        sampleProperty.setIsDeleted(false);
        sampleProperty.setTotalRooms(12);
        sampleProperty.setTotalFloors(3);

        sampleRoom = new Room("101", "1", "Single", 8500, 1, 10L, true);
        sampleTenant = new Tenant("T-900", "Aditya Rao", "123456789012", "9876543210", "student", null, null, "101", 15000, com.rms.backend.tenants.entity.AdvancePaidStatus.PAID, 8500, 10L);
    }

    @Test
    @DisplayName("softDeleteProperty successfully creates snapshot and sets TTL when authorized")
    void testSoftDeleteSuccess() {
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(sampleProperty));
        when(roomRepository.findByPropertyId(10L)).thenReturn(List.of(sampleRoom));
        when(tenantRepository.findByPropertyId(10L)).thenReturn(List.of(sampleTenant));

        PropertyArchiveSnapshot savedSnapshot = new PropertyArchiveSnapshot();
        savedSnapshot.setId(101L);
        when(snapshotRepository.save(any(PropertyArchiveSnapshot.class))).thenReturn(savedSnapshot);

        when(propertyRepository.save(any(Property.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PropertySoftDeleteRequestDto request = new PropertySoftDeleteRequestDto(
                "Olive Green PG",
                "9999",
                "Lease expiration",
                30,
                "SUPER_ADMIN"
        );

        PropertyResponseDto response = propertyService.softDeleteProperty(10L, request);

        assertNotNull(response);
        assertTrue(response.getIsDeleted());
        assertEquals("DELETED", response.getStatus());
        assertEquals("SUPER_ADMIN", response.getDeletedBy());
        assertEquals("Lease expiration", response.getDeletionReason());
        assertNotNull(response.getTtlExpiresAt());
        assertTrue(response.getTtlExpiresAt().isAfter(LocalDateTime.now().plusDays(28)));

        verify(snapshotRepository, times(1)).save(any(PropertyArchiveSnapshot.class));
        verify(propertyRepository, times(1)).save(sampleProperty);
    }

    @Test
    @DisplayName("softDeleteProperty throws exception when Admin PIN is invalid")
    void testSoftDeleteInvalidPin() {
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(sampleProperty));

        PropertySoftDeleteRequestDto request = new PropertySoftDeleteRequestDto(
                "Olive Green PG",
                "0000", // invalid PIN
                "Test",
                30,
                "ADMIN"
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                propertyService.softDeleteProperty(10L, request));

        assertTrue(ex.getMessage().contains("Invalid Admin Security PIN"));
        verify(snapshotRepository, never()).save(any());
    }

    @Test
    @DisplayName("softDeleteProperty throws exception when confirmation name does not match")
    void testSoftDeleteNameMismatch() {
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(sampleProperty));

        PropertySoftDeleteRequestDto request = new PropertySoftDeleteRequestDto(
                "Wrong Name PG",
                "9999",
                "Test",
                30,
                "ADMIN"
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                propertyService.softDeleteProperty(10L, request));

        assertTrue(ex.getMessage().contains("Confirmation phrase does not match"));
        verify(snapshotRepository, never()).save(any());
    }

    @Test
    @DisplayName("restoreProperty successfully reactivates soft-deleted property")
    void testRestorePropertySuccess() {
        sampleProperty.setIsDeleted(true);
        sampleProperty.setStatus("DELETED");
        sampleProperty.setDeletedAt(LocalDateTime.now().minusDays(5));
        sampleProperty.setTtlExpiresAt(LocalDateTime.now().plusDays(25));

        when(propertyRepository.findById(10L)).thenReturn(Optional.of(sampleProperty));
        when(propertyRepository.save(any(Property.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PropertyResponseDto restored = propertyService.restoreProperty(10L);

        assertNotNull(restored);
        assertFalse(restored.getIsDeleted());
        assertEquals("ACTIVE", restored.getStatus());
        assertNull(restored.getDeletedAt());
        assertNull(restored.getTtlExpiresAt());

        verify(propertyRepository, times(1)).save(sampleProperty);
    }

    @Test
    @DisplayName("restoreProperty throws exception if property is already active")
    void testRestoreActivePropertyFails() {
        sampleProperty.setIsDeleted(false);
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(sampleProperty));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                propertyService.restoreProperty(10L));

        assertTrue(ex.getMessage().contains("Property is already active"));
        verify(propertyRepository, never()).save(any());
    }

    @Test
    @DisplayName("getArchivedSnapshot returns manifest snapshot successfully")
    void testGetArchivedSnapshotSuccess() {
        PropertyArchiveSnapshot snapshot = new PropertyArchiveSnapshot();
        snapshot.setId(200L);
        snapshot.setPropertyId(10L);
        snapshot.setPropertyName("Olive Green PG");
        snapshot.setArchivedBy("ADMIN");
        snapshot.setTtlDays(30);
        snapshot.setTotalRooms(12);
        snapshot.setTotalResidents(10);
        snapshot.setSnapshotJson("{\"propertyId\": 10, \"rooms\": []}");
        snapshot.setArchivedAt(LocalDateTime.now());

        when(snapshotRepository.findTopByPropertyIdOrderByArchivedAtDesc(10L)).thenReturn(Optional.of(snapshot));

        PropertyArchiveSnapshotDto result = propertyService.getArchivedSnapshot(10L);

        assertNotNull(result);
        assertEquals(200L, result.getId());
        assertEquals("Olive Green PG", result.getPropertyName());
        assertEquals(12, result.getTotalRooms());
        assertEquals(10, result.getTotalResidents());
        assertTrue(result.getSnapshotJson().contains("\"propertyId\": 10"));
    }
}
