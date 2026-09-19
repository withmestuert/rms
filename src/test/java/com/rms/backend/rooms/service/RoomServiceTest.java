package com.rms.backend.rooms.service;

import com.rms.backend.admissions.entity.Admission;
import com.rms.backend.admissions.repository.AdmissionRepository;
import com.rms.backend.exception.DuplicateResourceException;
import com.rms.backend.exception.ResourceNotFoundException;
import com.rms.backend.rooms.entity.Room;
import com.rms.backend.rooms.repository.RoomRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, com.rms.backend.security.OwnerTestContext.class})
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private AdmissionRepository admissionRepository;

    @InjectMocks
    private RoomService roomService;

    private Room emptyRoom;

    @BeforeEach
    void setUp() {
        emptyRoom = new Room("999", "9", "SINGLE", 5000, 1, true);
        emptyRoom.setPropertyId(1L);
        emptyRoom.setCurrentOccupancy(0);
        emptyRoom.setReservedCapacity(0);
    }

    @Test
    @DisplayName("deleteRoom succeeds when room is empty and has no tenant/admission references")
    void testDeleteRoomSuccess() {
        when(roomRepository.findById("999")).thenReturn(Optional.of(emptyRoom));
        when(tenantRepository.countByRoomNo("999")).thenReturn(0L);
        when(admissionRepository.findByRoom_RoomNo("999")).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> roomService.deleteRoom("999"));
        verify(roomRepository, times(1)).delete(emptyRoom);
    }

    @Test
    @DisplayName("deleteRoom throws ResourceNotFoundException when room does not exist")
    void testDeleteRoomNotFound() {
        when(roomRepository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> roomService.deleteRoom("nonexistent"));
        verify(roomRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteRoom throws DuplicateResourceException when room has occupants")
    void testDeleteRoomOccupied() {
        emptyRoom.setCurrentOccupancy(1);
        when(roomRepository.findById("999")).thenReturn(Optional.of(emptyRoom));

        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class, () -> roomService.deleteRoom("999"));
        assertTrue(ex.getMessage().contains("currently occupied"));
        verify(roomRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteRoom throws DuplicateResourceException when room has reserved capacity")
    void testDeleteRoomReserved() {
        emptyRoom.setReservedCapacity(1);
        when(roomRepository.findById("999")).thenReturn(Optional.of(emptyRoom));

        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class, () -> roomService.deleteRoom("999"));
        assertTrue(ex.getMessage().contains("active reservations"));
        verify(roomRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteRoom throws DuplicateResourceException when tenants are assigned")
    void testDeleteRoomWithTenants() {
        when(roomRepository.findById("999")).thenReturn(Optional.of(emptyRoom));
        when(tenantRepository.countByRoomNo("999")).thenReturn(2L);

        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class, () -> roomService.deleteRoom("999"));
        assertTrue(ex.getMessage().contains("tenant(s) are assigned"));
        verify(roomRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteRoom throws DuplicateResourceException when admissions exist")
    void testDeleteRoomWithAdmissions() {
        when(roomRepository.findById("999")).thenReturn(Optional.of(emptyRoom));
        when(tenantRepository.countByRoomNo("999")).thenReturn(0L);
        when(admissionRepository.findByRoom_RoomNo("999")).thenReturn(List.of(new Admission()));

        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class, () -> roomService.deleteRoom("999"));
        assertTrue(ex.getMessage().contains("admission record(s) reference it"));
        verify(roomRepository, never()).delete(any());
    }
}
