package com.rms.backend.users.controller;

import com.rms.backend.exception.DuplicateResourceException;
import com.rms.backend.exception.GlobalExceptionHandler;
import com.rms.backend.exception.ResourceNotFoundException;
import com.rms.backend.users.dto.UserRequestDto;
import com.rms.backend.users.dto.UserResponseDto;
import com.rms.backend.users.service.UserService;
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
class UserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private UserResponseDto sampleDto;
    private String validJson;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        sampleDto = new UserResponseDto(
                1L, "rajesh.sharma", "rajesh.sharma@rms.in",
                "Rajesh Sharma", "ADMIN", "9845012345",
                "ACTIVE", LocalDateTime.now(), LocalDateTime.now()
        );

        validJson = "{"
                + "\"username\":\"rajesh.sharma\","
                + "\"email\":\"rajesh.sharma@rms.in\","
                + "\"fullName\":\"Rajesh Sharma\","
                + "\"role\":\"ADMIN\","
                + "\"phone\":\"9845012345\","
                + "\"status\":\"ACTIVE\""
                + "}";
    }

    @Test
    @DisplayName("GET /api/users returns list of users")
    void testGetAllUsers() throws Exception {
        when(userService.getAllUsers()).thenReturn(List.of(sampleDto));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].username").value("rajesh.sharma"))
                .andExpect(jsonPath("$[0].role").value("ADMIN"));
    }

    @Test
    @DisplayName("GET /api/users/{id} returns user when found")
    void testGetUserByIdSuccess() throws Exception {
        when(userService.getUserById(1L)).thenReturn(sampleDto);

        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("rajesh.sharma"));
    }

    @Test
    @DisplayName("GET /api/users/{id} returns 404 when not found")
    void testGetUserByIdNotFound() throws Exception {
        when(userService.getUserById(999L))
                .thenThrow(new ResourceNotFoundException("User not found with ID: 999"));

        mockMvc.perform(get("/api/users/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/users returns 201 Created on valid request")
    void testCreateUserSuccess() throws Exception {
        when(userService.createUser(any(UserRequestDto.class))).thenReturn(sampleDto);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("rajesh.sharma"));
    }

    @Test
    @DisplayName("POST /api/users returns 409 Conflict on duplicate username")
    void testCreateUserDuplicateConflict() throws Exception {
        when(userService.createUser(any(UserRequestDto.class)))
                .thenThrow(new DuplicateResourceException("User already exists with username: rajesh.sharma"));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson))
                .andExpect(status().isConflict())
                .andExpect(content().string("User already exists with username: rajesh.sharma"));
    }

    @Test
    @DisplayName("PUT /api/users/{id} returns updated user")
    void testUpdateUserSuccess() throws Exception {
        when(userService.updateUser(eq(1L), any(UserRequestDto.class))).thenReturn(sampleDto);

        mockMvc.perform(put("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("rajesh.sharma"));
    }

    @Test
    @DisplayName("DELETE /api/users/{id} returns 200 OK on success")
    void testDeleteUserSuccess() throws Exception {
        doNothing().when(userService).deleteUser(1L);

        mockMvc.perform(delete("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("User deleted successfully"));
    }

    @Test
    @DisplayName("DELETE /api/users/{id} returns 404 when user not found")
    void testDeleteUserNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("User not found with ID: 999"))
                .when(userService).deleteUser(999L);

        mockMvc.perform(delete("/api/users/999"))
                .andExpect(status().isNotFound());
    }
}
