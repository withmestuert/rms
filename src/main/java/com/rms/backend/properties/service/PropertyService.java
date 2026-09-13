package com.rms.backend.properties.service;

import com.rms.backend.exception.DuplicateResourceException;
import com.rms.backend.exception.ResourceNotFoundException;
import com.rms.backend.properties.dto.PropertyRequestDto;
import com.rms.backend.properties.dto.PropertyResponseDto;
import com.rms.backend.properties.entity.Property;
import com.rms.backend.properties.repository.PropertyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PropertyService {

    private final PropertyRepository propertyRepository;

    public PropertyService(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    public List<PropertyResponseDto> getAllProperties() {
        return propertyRepository.findAll().stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    public PropertyResponseDto getPropertyById(Long id) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found with ID: " + id));
        return toResponseDto(property);
    }

    @Transactional
    public PropertyResponseDto createProperty(PropertyRequestDto dto) {
        if (propertyRepository.existsByName(dto.getName())) {
            throw new DuplicateResourceException("Property already exists with name: " + dto.getName());
        }

        if (dto.getCode() != null && !dto.getCode().isBlank() && propertyRepository.existsByCode(dto.getCode())) {
            throw new DuplicateResourceException("Property already exists with code: " + dto.getCode());
        }

        Property property = new Property();
        mapDtoToEntity(dto, property);

        Property saved = propertyRepository.save(property);
        return toResponseDto(saved);
    }

    @Transactional
    public PropertyResponseDto updateProperty(Long id, PropertyRequestDto dto) {
        Property existing = propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found with ID: " + id));

        // If name changed, check uniqueness
        if (!existing.getName().equalsIgnoreCase(dto.getName()) && propertyRepository.existsByName(dto.getName())) {
            throw new DuplicateResourceException("Property already exists with name: " + dto.getName());
        }

        // If code changed, check uniqueness
        if (dto.getCode() != null && !dto.getCode().equalsIgnoreCase(existing.getCode()) && propertyRepository.existsByCode(dto.getCode())) {
            throw new DuplicateResourceException("Property already exists with code: " + dto.getCode());
        }

        mapDtoToEntity(dto, existing);
        Property updated = propertyRepository.save(existing);
        return toResponseDto(updated);
    }

    @Transactional
    public void deleteProperty(Long id) {
        Property existing = propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found with ID: " + id));
        propertyRepository.delete(existing);
    }

    private void mapDtoToEntity(PropertyRequestDto dto, Property entity) {
        entity.setName(dto.getName());
        entity.setCode(dto.getCode());
        entity.setAddress(dto.getAddress());
        entity.setCity(dto.getCity());
        entity.setState(dto.getState());
        entity.setPincode(dto.getPincode());
        entity.setPropertyType(dto.getPropertyType() != null ? dto.getPropertyType() : "PG");
        entity.setTotalFloors(dto.getTotalFloors());
        entity.setTotalRooms(dto.getTotalRooms());
        entity.setContactNumber(dto.getContactNumber());
        entity.setContactEmail(dto.getContactEmail());
        if (dto.getStatus() != null) {
            entity.setStatus(dto.getStatus());
        }
    }

    private PropertyResponseDto toResponseDto(Property p) {
        return new PropertyResponseDto(
                p.getId(),
                p.getName(),
                p.getCode(),
                p.getAddress(),
                p.getCity(),
                p.getState(),
                p.getPincode(),
                p.getPropertyType(),
                p.getTotalFloors(),
                p.getTotalRooms(),
                p.getContactNumber(),
                p.getContactEmail(),
                p.getStatus(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
