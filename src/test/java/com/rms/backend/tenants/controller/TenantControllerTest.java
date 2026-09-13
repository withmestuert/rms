package com.rms.backend.tenants.controller;

import com.rms.backend.exception.DuplicateResourceException;
import com.rms.backend.exception.GlobalExceptionHandler;
import com.rms.backend.exception.ResourceNotFoundException;
import com.rms.backend.tenants.dto.TenantRequestDto;
import com.rms.backend.tenants.entity.AdvancePaidStatus;
import com.rms.backend.tenants.entity.Tenant;
import com.rms.backend.tenants.service.TenantService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TenantControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TenantService tenantService;

    @InjectMocks
    private TenantController tenantController;

    private Tenant sampleTenant;
    private String validJson;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(tenantController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        sampleTenant = new Tenant(
                "T-201", "Priya Sharma", "9988-7766-5544", "9811223344",
                "Student", "Delhi University", "9811223300",
                "201", 8000, AdvancePaidStatus.PENDING, 6000
        );

        // Note: validJson does NOT include advancePaidStatus, which is server-controlled
        validJson = "{"
                + "\"uid\":\"T-201\","
                + "\"name\":\"Priya Sharma\","
                + "\"aadhaarNo\":\"9988-7766-5544\","
                + "\"mobileNumber\":\"9811223344\","
                + "\"tenantType\":\"Student\","
                + "\"organizationName\":\"Delhi University\","
                + "\"parentContact\":\"9811223300\","
                + "\"roomNo\":\"201\","
                + "\"advancePaid\":8000,"
                + "\"standardRent\":6000"
                + "}";
    }

    @Test
    @DisplayName("POST /api/tenants returns 201 Created on valid request and advancePaidStatus is PENDING")
    void testCreateTenantSuccess() throws Exception {
        when(tenantService.createTenant(any(TenantRequestDto.class))).thenReturn(sampleTenant);

        mockMvc.perform(post("/api/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uid").value("T-201"))
                .andExpect(jsonPath("$.name").value("Priya Sharma"))
                .andExpect(jsonPath("$.roomNo").value("201"))
                .andExpect(jsonPath("$.advancePaid").value(8000))
                .andExpect(jsonPath("$.advancePaidStatus").value("PENDING"))
                .andExpect(jsonPath("$.standardRent").value(6000));
    }

    @Test
    @DisplayName("POST /api/tenants returns 409 Conflict on duplicate resource")
    void testCreateTenantDuplicateConflict() throws Exception {
        when(tenantService.createTenant(any(TenantRequestDto.class)))
                .thenThrow(new DuplicateResourceException("Tenant already exists with UID: T-201"));

        mockMvc.perform(post("/api/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson))
                .andExpect(status().isConflict())
                .andExpect(content().string("Tenant already exists with UID: T-201"));
    }

    @Test
    @DisplayName("GET /api/tenants returns 200 OK with tenant list including advancePaidStatus")
    void testGetAllTenants() throws Exception {
        when(tenantService.getAllTenants()).thenReturn(List.of(sampleTenant));

        mockMvc.perform(get("/api/tenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].uid").value("T-201"))
                .andExpect(jsonPath("$[0].name").value("Priya Sharma"))
                .andExpect(jsonPath("$[0].advancePaidStatus").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/tenants/{uid} returns 200 OK when found")
    void testGetTenantByUidFound() throws Exception {
        when(tenantService.getTenantByUid("T-201")).thenReturn(sampleTenant);

        mockMvc.perform(get("/api/tenants/T-201"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uid").value("T-201"))
                .andExpect(jsonPath("$.tenantType").value("Student"))
                .andExpect(jsonPath("$.advancePaidStatus").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/tenants/{uid} returns 404 Not Found when absent")
    void testGetTenantByUidNotFound() throws Exception {
        when(tenantService.getTenantByUid("UNKNOWN"))
                .thenThrow(new ResourceNotFoundException("Tenant not found with UID: UNKNOWN"));

        mockMvc.perform(get("/api/tenants/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Tenant not found with UID: UNKNOWN"));
    }

    @Test
    @DisplayName("DELETE /api/tenants/{uid} returns 200 OK")
    void testDeleteTenant() throws Exception {
        doNothing().when(tenantService).deleteTenant("T-201");

        mockMvc.perform(delete("/api/tenants/T-201"))
                .andExpect(status().isOk())
                .andExpect(content().string("Tenant deleted successfully"));
    }

    @Test
    @DisplayName("PUT /api/tenants/{uid}/verify-advance returns 200 OK with PAID status")
    void testVerifyAdvancePayment() throws Exception {
        sampleTenant.setAdvancePaidStatus(AdvancePaidStatus.PAID);
        when(tenantService.verifyAdvancePayment("T-201", 10000)).thenReturn(sampleTenant);

        mockMvc.perform(put("/api/tenants/T-201/verify-advance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uid").value("T-201"))
                .andExpect(jsonPath("$.advancePaidStatus").value("PAID"));
    }
}
