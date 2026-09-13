package com.rms.backend.users.service;

import com.rms.backend.exception.DuplicateResourceException;
import com.rms.backend.exception.ResourceNotFoundException;
import com.rms.backend.users.dto.UserRequestDto;
import com.rms.backend.users.dto.UserResponseDto;
import com.rms.backend.users.entity.User;
import com.rms.backend.users.repository.UserRepository;
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
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User sampleUser;
    private UserRequestDto sampleRequest;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId(1L);
        sampleUser.setUsername("rajesh.sharma");
        sampleUser.setEmail("rajesh.sharma@rms.in");
        sampleUser.setFullName("Rajesh Sharma");
        sampleUser.setRole("ADMIN");
        sampleUser.setPhone("9845012345");
        sampleUser.setStatus("ACTIVE");
        sampleUser.setCreatedAt(LocalDateTime.now());
        sampleUser.setUpdatedAt(LocalDateTime.now());

        sampleRequest = new UserRequestDto();
        sampleRequest.setUsername("rajesh.sharma");
        sampleRequest.setEmail("rajesh.sharma@rms.in");
        sampleRequest.setFullName("Rajesh Sharma");
        sampleRequest.setRole("ADMIN");
        sampleRequest.setPhone("9845012345");
        sampleRequest.setStatus("ACTIVE");
    }

    @Test
    @DisplayName("getAllUsers returns list of user responses")
    void testGetAllUsers() {
        when(userRepository.findAll()).thenReturn(List.of(sampleUser));

        List<UserResponseDto> users = userService.getAllUsers();

        assertNotNull(users);
        assertEquals(1, users.size());
        assertEquals("rajesh.sharma", users.get(0).getUsername());
        assertEquals("ADMIN", users.get(0).getRole());
    }

    @Test
    @DisplayName("getUserById returns user when found")
    void testGetUserByIdSuccess() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        UserResponseDto result = userService.getUserById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("rajesh.sharma", result.getUsername());
    }

    @Test
    @DisplayName("getUserById throws ResourceNotFoundException when user does not exist")
    void testGetUserByIdNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getUserById(999L));
    }

    @Test
    @DisplayName("createUser succeeds with unique username and email")
    void testCreateUserSuccess() {
        when(userRepository.existsByUsername("rajesh.sharma")).thenReturn(false);
        when(userRepository.existsByEmail("rajesh.sharma@rms.in")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(10L);
            return u;
        });

        UserResponseDto result = userService.createUser(sampleRequest);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        assertEquals("rajesh.sharma", result.getUsername());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("createUser throws DuplicateResourceException if username already taken")
    void testCreateUserDuplicateUsername() {
        when(userRepository.existsByUsername("rajesh.sharma")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> userService.createUser(sampleRequest));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("createUser throws DuplicateResourceException if email already taken")
    void testCreateUserDuplicateEmail() {
        when(userRepository.existsByUsername("rajesh.sharma")).thenReturn(false);
        when(userRepository.existsByEmail("rajesh.sharma@rms.in")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> userService.createUser(sampleRequest));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("updateUser updates user details when valid")
    void testUpdateUserSuccess() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        sampleRequest.setFullName("Rajesh S.");
        UserResponseDto result = userService.updateUser(1L, sampleRequest);

        assertNotNull(result);
        assertEquals("Rajesh S.", result.getFullName());
        verify(userRepository, times(1)).save(sampleUser);
    }

    @Test
    @DisplayName("updateUser throws DuplicateResourceException if new username belongs to another user")
    void testUpdateUserDuplicateUsername() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        sampleRequest.setUsername("priya.nair");
        when(userRepository.existsByUsername("priya.nair")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> userService.updateUser(1L, sampleRequest));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("deleteUser successfully deletes user by ID")
    void testDeleteUserSuccess() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        assertDoesNotThrow(() -> userService.deleteUser(1L));
        verify(userRepository, times(1)).delete(sampleUser);
    }

    @Test
    @DisplayName("deleteUser throws ResourceNotFoundException when user does not exist")
    void testDeleteUserNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.deleteUser(999L));
        verify(userRepository, never()).delete(any());
    }
}
