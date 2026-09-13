package com.rms.backend.properties.controller;

import com.rms.backend.exception.DuplicateResourceException;
import com.rms.backend.exception.GlobalExceptionHandler;
import com.rms.backend.exception.ResourceNotFoundException;
import com.rms.backend.properties.dto.PropertyRequestDto;
import com.rms.backend.properties.dto.PropertyResponseDto;
import com.rms.backend.properties.service.PropertyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PropertyControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PropertyService propertyService;

    @InjectMocks
    private PropertyController propertyController;

    private PropertyResponseDto sampleDto;
    private String validJson;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(propertyController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        sampleDto = new PropertyResponseDto(
                1L, "Greenwood PG Phase 1", "GW-PG-01",
                "12th Main, Indiranagar", "Bengaluru", "Karnataka", "560038",
                "PG", 4, 32, "9876543210", "greenwood@rms.in",
                "ACTIVE", LocalDateTime.now(), LocalDateTime.now()
        );

        validJson = "{"
                + "\"name\":\"Greenwood PG Phase 1\","
                + "\"code\":\"GW-PG-01\","
                + "\"address\":\"12th Main, Indiranagar\","
                + "\"city\":\"Bengaluru\","
                + "\"state\":\"Karnataka\","
                + "\"pincode\":\"560038\","
                + "\"propertyType\":\"PG\","
                + "\"totalFloors\":4,"
                + "\"totalRooms\":32,"
                + "\"contactNumber\":\"9876543210\","
                + "\"contactEmail\":\"greenwood@rms.in\","
                + "\"status\":\"ACTIVE\""
                + "}";
    }

    @Test
    @DisplayName("GET /api/properties returns list of properties")
    void testGetAllProperties() throws Exception {
        when(propertyService.getAllProperties()).thenReturn(List.of(sampleDto));

        mockMvc.perform(get("/api/properties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].name").value("Greenwood PG Phase 1"))
                .andExpect(jsonPath("$[0].code").value("GW-PG-01"));
    }

    @Test
    @DisplayName("GET /api/properties/{id} returns property when found")
    void testGetPropertyByIdSuccess() throws Exception {
        when(propertyService.getPropertyById(1L)).thenReturn(sampleDto);

        mockMvc.perform(get("/api/properties/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Greenwood PG Phase 1"));
    }

    @Test
    @DisplayName("GET /api/properties/{id} returns 404 when not found")
    void testGetPropertyByIdNotFound() throws Exception {
        when(propertyService.getPropertyById(999L))
                .thenThrow(new ResourceNotFoundException("Property not found with ID: 999"));

        mockMvc.perform(get("/api/properties/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/properties returns 201 Created on valid request")
    void testCreatePropertySuccess() throws Exception {
        when(propertyService.createProperty(any(PropertyRequestDto.class))).thenReturn(sampleDto);

        mockMvc.perform(post("/api/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Greenwood PG Phase 1"));
    }

    @Test
    @DisplayName("POST /api/properties returns 409 Conflict on duplicate name")
    void testCreatePropertyDuplicateConflict() throws Exception {
        when(propertyService.createProperty(any(PropertyRequestDto.class)))
                .thenThrow(new DuplicateResourceException("Property already exists with name: Greenwood PG Phase 1"));

        mockMvc.perform(post("/api/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson))
                .andExpect(status().isConflict())
                .andExpect(content().string("Property already exists with name: Greenwood PG Phase 1"));
    }

    @Test
    @DisplayName("PUT /api/properties/{id} returns updated property")
    void testUpdatePropertySuccess() throws Exception {
        when(propertyService.updateProperty(eq(1L), any(PropertyRequestDto.class))).thenReturn(sampleDto);

        mockMvc.perform(put("/api/properties/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Greenwood PG Phase 1"));
    }

    @Test
    @DisplayName("DELETE /api/properties/{id} returns 200 OK on success")
    void testDeletePropertySuccess() throws Exception {
        doNothing().when(propertyService).deleteProperty(1L);

        mockMvc.perform(delete("/api/properties/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("Property deleted successfully"));
    }

    @Test
    @DisplayName("DELETE /api/properties/{id} returns 404 when property not found")
    void testDeletePropertyNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Property not found with ID: 999"))
                .when(propertyService).deleteProperty(999L);

        mockMvc.perform(delete("/api/properties/999"))
                .andExpect(status().isNotFound());
    }
}
