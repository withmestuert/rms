package com.rms.backend.users.service;

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

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserResponseDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    public UserResponseDto getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));
        return toResponseDto(user);
    }

    @Transactional
    public UserResponseDto createUser(UserRequestDto dto) {
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new DuplicateResourceException("User already exists with username: " + dto.getUsername());
        }

        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new DuplicateResourceException("User already exists with email: " + dto.getEmail());
        }

        User user = new User();
        mapDtoToEntity(dto, user);

        User saved = userRepository.save(user);
        return toResponseDto(saved);
    }

    @Transactional
    public UserResponseDto updateUser(Long id, UserRequestDto dto) {
        User existing = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));

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
        User existing = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));
        userRepository.delete(existing);
    }

    private void mapDtoToEntity(UserRequestDto dto, User entity) {
        entity.setUsername(dto.getUsername());
        entity.setEmail(dto.getEmail());
        entity.setFullName(dto.getFullName());
        entity.setRole(dto.getRole() != null ? dto.getRole() : "PROPERTY_MANAGER");
        entity.setPhone(dto.getPhone());
        if (dto.getStatus() != null) {
            entity.setStatus(dto.getStatus());
        }
    }

    private UserResponseDto toResponseDto(User u) {
        return new UserResponseDto(
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
    }
}
