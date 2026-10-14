package com.rms.backend.properties.service;

import com.rms.backend.exception.DuplicateResourceException;
import com.rms.backend.exception.ResourceNotFoundException;
import com.rms.backend.properties.dto.PropertyArchiveSnapshotDto;
import com.rms.backend.properties.dto.PropertyRequestDto;
import com.rms.backend.properties.dto.PropertyResponseDto;
import com.rms.backend.properties.dto.PropertySoftDeleteRequestDto;
import com.rms.backend.properties.entity.Property;
import com.rms.backend.properties.entity.PropertyArchiveSnapshot;
import com.rms.backend.properties.repository.PropertyArchiveSnapshotRepository;
import com.rms.backend.properties.repository.PropertyRepository;
import com.rms.backend.rooms.entity.Room;
import com.rms.backend.rooms.repository.RoomRepository;
import com.rms.backend.tenants.entity.Tenant;
import com.rms.backend.tenants.repository.TenantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final PropertyArchiveSnapshotRepository snapshotRepository;
    private final RoomRepository roomRepository;
    private final TenantRepository tenantRepository;

    @Autowired(required = false)
    public PropertyService(
            PropertyRepository propertyRepository,
            @Autowired(required = false) PropertyArchiveSnapshotRepository snapshotRepository,
            @Autowired(required = false) RoomRepository roomRepository,
            @Autowired(required = false) TenantRepository tenantRepository
    ) {
        this.propertyRepository = propertyRepository;
        this.snapshotRepository = snapshotRepository;
        this.roomRepository = roomRepository;
        this.tenantRepository = tenantRepository;
    }

    public PropertyService(PropertyRepository propertyRepository) {
        this(propertyRepository, null, null, null);
    }

    public List<PropertyResponseDto> getAllProperties() {
        return getAllProperties(false);
    }

    public List<PropertyResponseDto> getAllProperties(boolean includeDeleted) {
        List<Property> list = propertyRepository.findByIsDeletedFalse();
        if (list == null || list.isEmpty()) {
            list = propertyRepository.findAll().stream()
                    .filter(p -> includeDeleted || !Boolean.TRUE.equals(p.getIsDeleted()))
                    .collect(Collectors.toList());
        } else if (includeDeleted) {
            list = propertyRepository.findAll();
        }
        return list.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    public List<PropertyResponseDto> getArchivedProperties() {
        return propertyRepository.findByIsDeletedTrue().stream()
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

    /**
     * Enterprise Protected Soft-Delete:
     * 1. Validates Admin Security PIN.
     * 2. Validates Exact Property Name Confirmation.
     * 3. Freezes & Documents full property metadata, rooms, tenants into a permanent Audit Snapshot.
     * 4. Transitions Property to DELETED with a 30-day Time-To-Live (TTL) grace period for restoration.
     */
    @Transactional
    public PropertyResponseDto softDeleteProperty(Long id, PropertySoftDeleteRequestDto dto) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found with ID: " + id));

        if (Boolean.TRUE.equals(property.getIsDeleted())) {
            throw new IllegalStateException("Property is already decommissioned and in TTL grace period.");
        }

        // 1. Authorization PIN check (prototype default: 9999)
        String pin = dto.getAdminPin() != null ? dto.getAdminPin().trim() : "";
        if (!"9999".equals(pin)) {
            throw new IllegalArgumentException("Invalid Admin Security PIN. Decommission authorization failed.");
        }

        // 2. Exact Property Name Confirmation Check
        String conf = dto.getConfirmationName() != null ? dto.getConfirmationName().trim() : "";
        if (!property.getName().trim().equalsIgnoreCase(conf)) {
            throw new IllegalArgumentException("Confirmation phrase does not match property name. Expected: '" + property.getName() + "'");
        }

        // 3. Freeze & Document Full Audit Snapshot
        List<Room> rooms = roomRepository != null ? roomRepository.findByPropertyId(id) : List.of();
        List<Tenant> tenants = tenantRepository != null ? tenantRepository.findByPropertyId(id) : List.of();

        int totalRooms = rooms.size();
        int totalResidents = tenants.size();
        double totalRevenue = rooms.stream().mapToDouble(r -> r.getRentPerMonth() != null ? r.getRentPerMonth() : 0.0).sum();
        double totalAdvance = tenants.stream().mapToDouble(t -> t.getAdvancePaid() != null ? t.getAdvancePaid() : 0.0).sum();

        String json = buildSnapshotJson(property, rooms, tenants, dto, totalRooms, totalResidents, totalRevenue, totalAdvance);
        int ttlDays = (dto.getTtlDays() != null && dto.getTtlDays() > 0) ? dto.getTtlDays() : 30;

        PropertyArchiveSnapshot snapshot = new PropertyArchiveSnapshot();
        snapshot.setPropertyId(property.getId());
        snapshot.setPropertyName(property.getName());
        snapshot.setPropertyCode(property.getCode());
        snapshot.setArchivedBy(dto.getDeletedBy() != null ? dto.getDeletedBy() : "ADMIN");
        snapshot.setDeletionReason(dto.getReason());
        snapshot.setTtlDays(ttlDays);
        snapshot.setTotalRooms(totalRooms);
        snapshot.setTotalResidents(totalResidents);
        snapshot.setTotalRevenuePotential(totalRevenue);
        snapshot.setTotalAdvanceHeld(totalAdvance);
        snapshot.setSnapshotJson(json);
        snapshot.setArchivedAt(LocalDateTime.now());

        if (snapshotRepository != null) {
            snapshot = snapshotRepository.save(snapshot);
        }

        // 4. Update property status & TTL
        property.setIsDeleted(true);
        property.setStatus("DELETED");
        property.setDeletedAt(LocalDateTime.now());
        property.setDeletedBy(dto.getDeletedBy() != null ? dto.getDeletedBy() : "ADMIN");
        property.setDeletionReason(dto.getReason());
        property.setTtlExpiresAt(LocalDateTime.now().plusDays(ttlDays));
        if (snapshot.getId() != null) {
            property.setArchiveSnapshotId(snapshot.getId());
        }

        Property updated = propertyRepository.save(property);
        return toResponseDto(updated);
    }

    /**
     * Restore a soft-deleted property back to active status during its TTL grace period.
     */
    @Transactional
    public PropertyResponseDto restoreProperty(Long id) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found with ID: " + id));

        if (!Boolean.TRUE.equals(property.getIsDeleted())) {
            throw new IllegalStateException("Property is already active.");
        }

        property.setIsDeleted(false);
        property.setStatus("ACTIVE");
        property.setDeletedAt(null);
        property.setDeletedBy(null);
        property.setDeletionReason(null);
        property.setTtlExpiresAt(null);

        Property saved = propertyRepository.save(property);
        return toResponseDto(saved);
    }

    /**
     * Retrieve the frozen audit snapshot manifest.
     */
    public PropertyArchiveSnapshotDto getArchivedSnapshot(Long propertyId) {
        if (snapshotRepository == null) {
            throw new ResourceNotFoundException("Snapshot repository not available.");
        }
        PropertyArchiveSnapshot snapshot = snapshotRepository.findTopByPropertyIdOrderByArchivedAtDesc(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("No archive snapshot found for property ID: " + propertyId));

        return PropertyArchiveSnapshotDto.builder()
                .id(snapshot.getId())
                .propertyId(snapshot.getPropertyId())
                .propertyName(snapshot.getPropertyName())
                .propertyCode(snapshot.getPropertyCode())
                .archivedBy(snapshot.getArchivedBy())
                .deletionReason(snapshot.getDeletionReason())
                .ttlDays(snapshot.getTtlDays())
                .totalRooms(snapshot.getTotalRooms())
                .totalResidents(snapshot.getTotalResidents())
                .totalRevenuePotential(snapshot.getTotalRevenuePotential())
                .totalAdvanceHeld(snapshot.getTotalAdvanceHeld())
                .snapshotJson(snapshot.getSnapshotJson())
                .archivedAt(snapshot.getArchivedAt())
                .build();
    }

    @Transactional
    public void deleteProperty(Long id) {
        Property existing = propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found with ID: " + id));

        if (snapshotRepository != null) {
            try {
                PropertySoftDeleteRequestDto autoDto = new PropertySoftDeleteRequestDto(
                        existing.getName(),
                        "9999",
                        "Protected deletion manifest archive",
                        30,
                        "SYSTEM"
                );
                List<Room> rooms = roomRepository != null ? roomRepository.findByPropertyId(id) : List.of();
                List<Tenant> tenants = tenantRepository != null ? tenantRepository.findByPropertyId(id) : List.of();
                int totalRooms = rooms.size();
                int totalResidents = tenants.size();
                double totalRevenue = rooms.stream().mapToDouble(r -> r.getRentPerMonth() != null ? r.getRentPerMonth() : 0.0).sum();
                double totalAdvance = tenants.stream().mapToDouble(t -> t.getAdvancePaid() != null ? t.getAdvancePaid() : 0.0).sum();
                String json = buildSnapshotJson(existing, rooms, tenants, autoDto, totalRooms, totalResidents, totalRevenue, totalAdvance);

                PropertyArchiveSnapshot snapshot = new PropertyArchiveSnapshot();
                snapshot.setPropertyId(existing.getId());
                snapshot.setPropertyName(existing.getName());
                snapshot.setPropertyCode(existing.getCode());
                snapshot.setArchivedBy("SYSTEM");
                snapshot.setDeletionReason("Direct delete archive");
                snapshot.setTtlDays(30);
                snapshot.setTotalRooms(totalRooms);
                snapshot.setTotalResidents(totalResidents);
                snapshot.setTotalRevenuePotential(totalRevenue);
                snapshot.setTotalAdvanceHeld(totalAdvance);
                snapshot.setSnapshotJson(json);
                snapshot.setArchivedAt(LocalDateTime.now());
                snapshotRepository.save(snapshot);
            } catch (Exception ignored) {
            }
        }
        propertyRepository.delete(existing);
    }

    private String buildSnapshotJson(
            Property property,
            List<Room> rooms,
            List<Tenant> tenants,
            PropertySoftDeleteRequestDto dto,
            int totalRooms,
            int totalResidents,
            double totalRevenue,
            double totalAdvance
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"propertyId\": ").append(property.getId()).append(",\n");
        sb.append("  \"propertyName\": \"").append(escapeJson(property.getName())).append("\",\n");
        sb.append("  \"propertyCode\": \"").append(escapeJson(property.getCode())).append("\",\n");
        sb.append("  \"address\": \"").append(escapeJson(property.getAddress())).append("\",\n");
        sb.append("  \"city\": \"").append(escapeJson(property.getCity())).append("\",\n");
        sb.append("  \"totalFloors\": ").append(property.getTotalFloors()).append(",\n");
        sb.append("  \"totalRooms\": ").append(property.getTotalRooms()).append(",\n");
        sb.append("  \"decommissionReason\": \"").append(escapeJson(dto.getReason())).append("\",\n");
        sb.append("  \"archivedBy\": \"").append(escapeJson(dto.getDeletedBy() != null ? dto.getDeletedBy() : "ADMIN")).append("\",\n");
        sb.append("  \"archivedAt\": \"").append(LocalDateTime.now()).append("\",\n");

        sb.append("  \"metrics\": {\n");
        sb.append("    \"totalRoomsConfigured\": ").append(totalRooms).append(",\n");
        sb.append("    \"totalActiveResidents\": ").append(totalResidents).append(",\n");
        sb.append("    \"monthlyPotentialRevenue\": ").append(totalRevenue).append(",\n");
        sb.append("    \"advanceDepositLiability\": ").append(totalAdvance).append("\n");
        sb.append("  },\n");

        sb.append("  \"rooms\": [\n");
        for (int i = 0; i < rooms.size(); i++) {
            Room r = rooms.get(i);
            sb.append("    {");
            sb.append("\"roomNo\": \"").append(escapeJson(r.getRoomNo())).append("\", ");
            sb.append("\"floor\": \"").append(escapeJson(r.getFloor())).append("\", ");
            sb.append("\"roomType\": \"").append(escapeJson(r.getRoomType())).append("\", ");
            sb.append("\"rent\": ").append(r.getRentPerMonth() != null ? r.getRentPerMonth() : 0).append(", ");
            sb.append("\"capacity\": ").append(r.getOccupancy() != null ? r.getOccupancy() : 1);
            sb.append("}");
            if (i < rooms.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        sb.append("  \"tenants\": [\n");
        for (int i = 0; i < tenants.size(); i++) {
            Tenant t = tenants.get(i);
            sb.append("    {");
            sb.append("\"uid\": \"").append(escapeJson(t.getUid())).append("\", ");
            sb.append("\"name\": \"").append(escapeJson(t.getName())).append("\", ");
            sb.append("\"mobile\": \"").append(escapeJson(t.getMobileNumber())).append("\", ");
            sb.append("\"roomNo\": \"").append(escapeJson(t.getRoomNo())).append("\", ");
            sb.append("\"advancePaid\": ").append(t.getAdvancePaid() != null ? t.getAdvancePaid() : 0).append(", ");
            sb.append("\"status\": \"").append(escapeJson(t.getStatus())).append("\"");
            sb.append("}");
            if (i < tenants.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
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
        if (p == null) return null;
        Long daysRemaining = null;
        if (Boolean.TRUE.equals(p.getIsDeleted()) && p.getTtlExpiresAt() != null) {
            long days = ChronoUnit.DAYS.between(LocalDateTime.now(), p.getTtlExpiresAt());
            daysRemaining = Math.max(0, days);
        }

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
                p.getIsDeleted(),
                p.getDeletedAt(),
                p.getDeletedBy(),
                p.getDeletionReason(),
                p.getTtlExpiresAt(),
                daysRemaining,
                p.getArchiveSnapshotId(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
