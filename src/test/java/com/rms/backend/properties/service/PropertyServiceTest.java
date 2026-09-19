package com.rms.backend.properties.service;

import com.rms.backend.exception.DuplicateResourceException;
import com.rms.backend.exception.ResourceNotFoundException;
import com.rms.backend.properties.dto.PropertyRequestDto;
import com.rms.backend.properties.dto.PropertyResponseDto;
import com.rms.backend.properties.entity.Property;
import com.rms.backend.properties.repository.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, com.rms.backend.security.OwnerTestContext.class})
class PropertyServiceTest {

    @Mock
    private PropertyRepository propertyRepository;

    @InjectMocks
    private PropertyService propertyService;

    private Property sampleProperty;
    private PropertyRequestDto sampleRequest;

    @BeforeEach
    void setUp() {
        sampleProperty = new Property();
        sampleProperty.setOwnerId(99L);
        sampleProperty.setId(1L);
        sampleProperty.setName("Greenwood PG Phase 1");
        sampleProperty.setCode("GW-PG-01");
        sampleProperty.setAddress("12th Main, Indiranagar");
        sampleProperty.setCity("Bengaluru");
        sampleProperty.setState("Karnataka");
        sampleProperty.setPincode("560038");
        sampleProperty.setPropertyType("PG");
        sampleProperty.setTotalFloors(4);
        sampleProperty.setTotalRooms(32);
        sampleProperty.setContactNumber("9876543210");
        sampleProperty.setContactEmail("greenwood@rms.in");
        sampleProperty.setStatus("ACTIVE");
        sampleProperty.setCreatedAt(LocalDateTime.now());
        sampleProperty.setUpdatedAt(LocalDateTime.now());

        sampleRequest = new PropertyRequestDto();
        sampleRequest.setName("Greenwood PG Phase 1");
        sampleRequest.setCode("GW-PG-01");
        sampleRequest.setAddress("12th Main, Indiranagar");
        sampleRequest.setCity("Bengaluru");
        sampleRequest.setState("Karnataka");
        sampleRequest.setPincode("560038");
        sampleRequest.setPropertyType("PG");
        sampleRequest.setTotalFloors(4);
        sampleRequest.setTotalRooms(32);
        sampleRequest.setContactNumber("9876543210");
        sampleRequest.setContactEmail("greenwood@rms.in");
        sampleRequest.setStatus("ACTIVE");
    }

    @Test
    @DisplayName("getAllProperties returns list of response DTOs")
    void testGetAllProperties() {
        when(propertyRepository.findByOwnerId(99L)).thenReturn(List.of(sampleProperty));

        List<PropertyResponseDto> result = propertyService.getAllProperties();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Greenwood PG Phase 1", result.get(0).getName());
        assertEquals("GW-PG-01", result.get(0).getCode());
    }

    @Test
    @DisplayName("getPropertyById returns property when found")
    void testGetPropertyByIdSuccess() {
        when(propertyRepository.findById(1L)).thenReturn(Optional.of(sampleProperty));

        PropertyResponseDto result = propertyService.getPropertyById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Greenwood PG Phase 1", result.getName());
    }

    @Test
    @DisplayName("getPropertyById denies IDs outside the caller scope")
    void testGetPropertyByIdNotFound() {

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> propertyService.getPropertyById(999L));
    }

    @Test
    @DisplayName("createProperty successfully persists and returns new property")
    void testCreatePropertySuccess() {
        when(propertyRepository.existsByName("Greenwood PG Phase 1")).thenReturn(false);
        when(propertyRepository.existsByCode("GW-PG-01")).thenReturn(false);
        when(propertyRepository.save(any(Property.class))).thenAnswer(invocation -> {
            Property p = invocation.getArgument(0);
            p.setId(10L);
            return p;
        });

        PropertyResponseDto result = propertyService.createProperty(sampleRequest);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        assertEquals("Greenwood PG Phase 1", result.getName());
        verify(propertyRepository, times(1)).save(any(Property.class));
    }

    @Test
    @DisplayName("createProperty throws DuplicateResourceException if name already exists")
    void testCreatePropertyDuplicateName() {
        when(propertyRepository.existsByName("Greenwood PG Phase 1")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> propertyService.createProperty(sampleRequest));
        verify(propertyRepository, never()).save(any(Property.class));
    }

    @Test
    @DisplayName("createProperty throws DuplicateResourceException if code already exists")
    void testCreatePropertyDuplicateCode() {
        when(propertyRepository.existsByName("Greenwood PG Phase 1")).thenReturn(false);
        when(propertyRepository.existsByCode("GW-PG-01")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> propertyService.createProperty(sampleRequest));
        verify(propertyRepository, never()).save(any(Property.class));
    }

    @Test
    @DisplayName("updateProperty successfully updates existing property")
    void testUpdatePropertySuccess() {
        when(propertyRepository.findById(1L)).thenReturn(Optional.of(sampleProperty));
        when(propertyRepository.save(any(Property.class))).thenAnswer(invocation -> invocation.getArgument(0));

        sampleRequest.setName("Greenwood PG Phase 2");
        when(propertyRepository.existsByName("Greenwood PG Phase 2")).thenReturn(false);

        PropertyResponseDto result = propertyService.updateProperty(1L, sampleRequest);

        assertNotNull(result);
        assertEquals("Greenwood PG Phase 2", result.getName());
        verify(propertyRepository, times(1)).save(sampleProperty);
    }

    @Test
    @DisplayName("updateProperty throws DuplicateResourceException if new name already taken")
    void testUpdatePropertyDuplicateName() {
        when(propertyRepository.findById(1L)).thenReturn(Optional.of(sampleProperty));
        sampleRequest.setName("Another Existing PG");
        when(propertyRepository.existsByName("Another Existing PG")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> propertyService.updateProperty(1L, sampleRequest));
        verify(propertyRepository, never()).save(any(Property.class));
    }

    @Test
    @DisplayName("deleteProperty successfully deletes property by ID")
    void testDeletePropertySuccess() {
        when(propertyRepository.findById(1L)).thenReturn(Optional.of(sampleProperty));

        assertDoesNotThrow(() -> propertyService.deleteProperty(1L));
        verify(propertyRepository, times(1)).delete(sampleProperty);
    }

    @Test
    @DisplayName("deleteProperty denies IDs outside the caller scope")
    void testDeletePropertyNotFound() {

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> propertyService.deleteProperty(999L));
        verify(propertyRepository, never()).delete(any());
    }
}
