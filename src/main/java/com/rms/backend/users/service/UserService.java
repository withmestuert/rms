package com.rms.backend.users.service;

import com.rms.backend.common.SecurityConstants;
import com.rms.backend.security.Access;
import com.rms.backend.exception.DuplicateResourceException;
import com.rms.backend.exception.ResourceNotFoundException;
import com.rms.backend.users.dto.UserRequestDto;
import com.rms.backend.users.dto.UserResponseDto;
import com.rms.backend.users.entity.User;
import com.rms.backend.users.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwords;
    private final com.rms.backend.security.AuthSessionRepository sessions;

    public UserService(UserRepository userRepository, org.springframework.security.crypto.password.PasswordEncoder passwords, com.rms.backend.security.AuthSessionRepository sessions) {
        this.passwords = passwords;
        this.sessions = sessions;
        this.userRepository = userRepository;
    }

    public List<UserResponseDto> getAllUsers() {
        Long ownerId = Access.owner();
        return (Access.bypassed() ? userRepository.findAll() : userRepository.findByOwnerIdOrId(ownerId, ownerId)).stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    public UserResponseDto getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));
        if (!Access.current().userId().equals(id)) checkManagedUser(user);
        return toResponseDto(user);
    }

    @Transactional
    public UserResponseDto createUser(UserRequestDto dto) {
        Long ownerId = Access.owner();
        validateGrants(dto);
        com.rms.backend.security.AuthService.validatePassword(dto.getPassword());
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new DuplicateResourceException("User already exists with username: " + dto.getUsername());
        }

        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new DuplicateResourceException("User already exists with email: " + dto.getEmail());
        }

        User user = new User();
        user.setOwnerId(ownerId);
        mapDtoToEntity(dto, user);

        User saved = userRepository.save(user);
        return toResponseDto(saved);
    }

    @Transactional
    public UserResponseDto updateUser(Long id, UserRequestDto dto) {
        User existing = userRepository.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));

        checkManagedUser(existing);
        validateGrants(dto);
        sessions.deleteByUserId(id);
        existing.setAuthenticationVersion(existing.getAuthenticationVersion() + 1);
        // If username changed, check uniqueness
        if (!existing.getUsername().equalsIgnoreCase(dto.getUsername()) && userRepository.existsByUsername(dto.getUsername())) {
            throw new DuplicateResourceException("User already exists with username: " + dto.getUsername());
        }

        // If email changed, check uniqueness
        if (!existing.getEmail().equalsIgnoreCase(dto.getEmail()) && userRepository.existsByEmail(dto.getEmail())) {
            throw new DuplicateResourceException("User already exists with email: " + dto.getEmail());
        }

        mapDtoToEntity(dto, existing);
        User updated = userRepository.save(existing);
        return toResponseDto(updated);
    }

    @Transactional
    public void deleteUser(Long id) {
        User existing = userRepository.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));
        checkManagedUser(existing);
        sessions.deleteByUserId(id);
        userRepository.delete(existing);
    }

    private void checkManagedUser(User user) {
        Long ownerId = Access.owner();
        if (!Access.bypassed() && (!ownerId.equals(user.getOwnerId()) || SecurityConstants.OWNER.equals(user.getRole())))
            throw new org.springframework.security.access.AccessDeniedException("Cannot manage this user");
    }

    private void validateGrants(UserRequestDto dto) {
        Access.owner();
        if (dto.getRole() != null && !java.util.Set.of(SecurityConstants.REPRESENTATIVE, SecurityConstants.SUB_MEMBER).contains(dto.getRole()))
            throw new IllegalArgumentException("Role must be REPRESENTATIVE or SUB_MEMBER");
        if (dto.getStatus() != null && !java.util.Set.of(SecurityConstants.ACTIVE, SecurityConstants.INACTIVE).contains(dto.getStatus()))
            throw new IllegalArgumentException("Status must be ACTIVE or INACTIVE");
        if (dto.getPropertyIds() != null) dto.getPropertyIds().forEach(Access::read);
    }

    private void mapDtoToEntity(UserRequestDto dto, User entity) {
        entity.setUsername(dto.getUsername());
        entity.setEmail(dto.getEmail());
        entity.setFullName(dto.getFullName());
        entity.setRole(dto.getRole() != null ? dto.getRole() : (entity.getRole() != null ? entity.getRole() : SecurityConstants.SUB_MEMBER));
        if (dto.getPropertyIds() != null) entity.setPropertyIds(new java.util.HashSet<>(dto.getPropertyIds()));
        if (dto.getPassword() != null) {
            com.rms.backend.security.AuthService.validatePassword(dto.getPassword());
            entity.setPasswordHash(passwords.encode(dto.getPassword()));
        }
        entity.setPhone(dto.getPhone());
        if (dto.getStatus() != null) {
            entity.setStatus(dto.getStatus());
        }
    }

    private UserResponseDto toResponseDto(User u) {
        UserResponseDto result = new UserResponseDto(
                u.getId(),
                u.getUsername(),
                u.getEmail(),
                u.getFullName(),
                u.getRole(),
                u.getPhone(),
                u.getStatus(),
                u.getCreatedAt(),
                u.getUpdatedAt()
        );
        result.setOwnerId(u.getOwnerId());
        result.setPropertyIds(java.util.Set.copyOf(u.getPropertyIds()));
        return result;
    }
}
