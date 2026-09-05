package com.rms.backend.admissions.controller;

import com.rms.backend.admissions.dto.AdmissionResponseDto;
import com.rms.backend.admissions.entity.AdmissionStatus;
import com.rms.backend.admissions.service.AdmissionService;
import com.rms.backend.exception.DuplicateResourceException;
import com.rms.backend.exception.GlobalExceptionHandler;
import com.rms.backend.exception.ResourceNotFoundException;
import com.rms.backend.tenants.entity.AdvancePaidStatus;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AdmissionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AdmissionService admissionService;

    @InjectMocks
    private AdmissionController admissionController;

    private AdmissionResponseDto sampleResponse;

    // Minimal valid JSON body for creating an enrollment of a new tenant
    private static final String VALID_NEW_ENROLLMENT_JSON =
            "{" +
            "\"roomNo\":\"101\"," +
            "\"name\":\"Rahul Kumar\"," +
            "\"aadhaarNo\":\"1234-5678-9012\"," +
            "\"mobileNumber\":\"9876543210\"," +
            "\"tenantType\":\"Working\"," +
            "\"organizationName\":\"Infosys\"," +
            "\"advancePaid\":10000," +
            "\"standardRent\":7500" +
            "}";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(admissionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        sampleResponse = new AdmissionResponseDto();
        sampleResponse.setAdmissionNumber("ADM-1001");
        sampleResponse.setTenantUid("T-001");
        sampleResponse.setTenantName("Rahul Kumar");
        sampleResponse.setAadhaarNo("1234-5678-9012");
        sampleResponse.setMobileNumber("9876543210");
        sampleResponse.setRoomNo("101");
        sampleResponse.setRoomRent(7500);
        sampleResponse.setAdvancePaid(10000);
        sampleResponse.setAdvancePaidStatus(AdvancePaidStatus.PENDING);
        sampleResponse.setStatus(AdmissionStatus.PENDING);
        sampleResponse.setEnrollmentDate(LocalDate.now());
        sampleResponse.setCreatedAt(LocalDateTime.now());
        sampleResponse.setUpdatedAt(LocalDateTime.now());
    }

    // =========================================================================
    // POST /api/admissions
    // =========================================================================

    @Test
    @DisplayName("POST /api/admissions returns 201 Created on successful enrollment")
    void testCreateEnrollment_Success_Returns201() throws Exception {
        when(admissionService.createEnrollment(any())).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/admissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_NEW_ENROLLMENT_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.admissionNumber").value("ADM-1001"))
                .andExpect(jsonPath("$.tenantUid").value("T-001"))
                .andExpect(jsonPath("$.roomNo").value("101"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.advancePaidStatus").value("PENDING"));
    }

    @Test
    @DisplayName("POST /api/admissions returns 400 when roomNo is missing")
    void testCreateEnrollment_MissingRoomNo_Returns400() throws Exception {
        String bodyWithoutRoom = "{\"name\":\"Rahul Kumar\"}";

        mockMvc.perform(post("/api/admissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithoutRoom))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/admissions returns 409 when room is full")
    void testCreateEnrollment_RoomFull_Returns409() throws Exception {
        when(admissionService.createEnrollment(any()))
                .thenThrow(new DuplicateResourceException("Room 101 has no available capacity"));

        mockMvc.perform(post("/api/admissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_NEW_ENROLLMENT_JSON))
                .andExpect(status().isConflict())
                .andExpect(content().string("Room 101 has no available capacity"));
    }

    @Test
    @DisplayName("POST /api/admissions returns 404 when room does not exist")
    void testCreateEnrollment_RoomNotFound_Returns404() throws Exception {
        when(admissionService.createEnrollment(any()))
                .thenThrow(new ResourceNotFoundException("Room not found: 101"));

        mockMvc.perform(post("/api/admissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_NEW_ENROLLMENT_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Room not found: 101"));
    }

    // =========================================================================
    // GET /api/admissions
    // =========================================================================

    @Test
    @DisplayName("GET /api/admissions returns 200 with list of admissions")
    void testGetAllAdmissions_Returns200() throws Exception {
        when(admissionService.getAllAdmissions()).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/admissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].admissionNumber").value("ADM-1001"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/admissions returns 200 with empty list when no admissions")
    void testGetAllAdmissions_EmptyList_Returns200() throws Exception {
        when(admissionService.getAllAdmissions()).thenReturn(List.of());

        mockMvc.perform(get("/api/admissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // =========================================================================
    // GET /api/admissions/{admissionNumber}
    // =========================================================================

    @Test
    @DisplayName("GET /api/admissions/{admissionNumber} returns 200 when found")
    void testGetAdmissionByNumber_Found_Returns200() throws Exception {
        when(admissionService.getAdmissionByNumber("ADM-1001")).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/admissions/ADM-1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admissionNumber").value("ADM-1001"))
                .andExpect(jsonPath("$.tenantName").value("Rahul Kumar"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/admissions/{admissionNumber} returns 404 when not found")
    void testGetAdmissionByNumber_NotFound_Returns404() throws Exception {
        when(admissionService.getAdmissionByNumber("ADM-9999"))
                .thenThrow(new ResourceNotFoundException("Admission not found: ADM-9999"));

        mockMvc.perform(get("/api/admissions/ADM-9999"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Admission not found: ADM-9999"));
    }

    // =========================================================================
    // PUT /api/admissions/{admissionNumber}/confirm
    // =========================================================================

    @Test
    @DisplayName("PUT /api/admissions/{admissionNumber}/confirm returns 200 on success")
    void testConfirmAdmission_Success_Returns200() throws Exception {
        AdmissionResponseDto confirmedResponse = new AdmissionResponseDto();
        confirmedResponse.setAdmissionNumber("ADM-1001");
        confirmedResponse.setStatus(AdmissionStatus.PAID);
        confirmedResponse.setAdvancePaidStatus(AdvancePaidStatus.PAID);
        confirmedResponse.setConfirmedOn(LocalDateTime.now());

        when(admissionService.confirmAdmission("ADM-1001")).thenReturn(confirmedResponse);

        mockMvc.perform(put("/api/admissions/ADM-1001/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.advancePaidStatus").value("PAID"));
    }

    @Test
    @DisplayName("PUT /api/admissions/{admissionNumber}/confirm returns 409 if already PAID")
    void testConfirmAdmission_AlreadyPaid_Returns409() throws Exception {
        when(admissionService.confirmAdmission("ADM-1001"))
                .thenThrow(new DuplicateResourceException("Admission is already confirmed and PAID"));

        mockMvc.perform(put("/api/admissions/ADM-1001/confirm"))
                .andExpect(status().isConflict())
                .andExpect(content().string("Admission is already confirmed and PAID"));
    }

    @Test
    @DisplayName("PUT /api/admissions/{admissionNumber}/confirm returns 404 when not found")
    void testConfirmAdmission_NotFound_Returns404() throws Exception {
        when(admissionService.confirmAdmission("ADM-9999"))
                .thenThrow(new ResourceNotFoundException("Admission not found: ADM-9999"));

        mockMvc.perform(put("/api/admissions/ADM-9999/confirm"))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // DELETE /api/admissions/{admissionNumber}
    // =========================================================================

    @Test
    @DisplayName("DELETE /api/admissions/{admissionNumber} returns 200 on successful cancellation")
    void testCancelAdmission_Success_Returns200() throws Exception {
        doNothing().when(admissionService).cancelPendingAdmission("ADM-1001");

        mockMvc.perform(delete("/api/admissions/ADM-1001"))
                .andExpect(status().isOk())
                .andExpect(content().string("Admission ADM-1001 has been cancelled successfully"));
    }

    @Test
    @DisplayName("DELETE /api/admissions/{admissionNumber} returns 409 if admission is PAID")
    void testCancelAdmission_PaidAdmission_Returns409() throws Exception {
        doThrow(new DuplicateResourceException("Cannot cancel a PAID admission through the pending cancellation workflow"))
                .when(admissionService).cancelPendingAdmission("ADM-1001");

        mockMvc.perform(delete("/api/admissions/ADM-1001"))
                .andExpect(status().isConflict())
                .andExpect(content().string("Cannot cancel a PAID admission through the pending cancellation workflow"));
    }

    @Test
    @DisplayName("DELETE /api/admissions/{admissionNumber} returns 404 when not found")
    void testCancelAdmission_NotFound_Returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Admission not found: ADM-9999"))
                .when(admissionService).cancelPendingAdmission("ADM-9999");

        mockMvc.perform(delete("/api/admissions/ADM-9999"))
                .andExpect(status().isNotFound());
    }
}
