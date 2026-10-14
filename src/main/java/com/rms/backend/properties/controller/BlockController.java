package com.rms.backend.properties.controller;

import com.rms.backend.exception.DuplicateResourceException;
import com.rms.backend.exception.ResourceNotFoundException;
import com.rms.backend.properties.entity.Block;
import com.rms.backend.properties.entity.Floor;
import com.rms.backend.properties.entity.Property;
import com.rms.backend.properties.repository.BlockRepository;
import com.rms.backend.properties.repository.FloorRepository;
import com.rms.backend.properties.repository.PropertyRepository;
import com.rms.backend.rooms.entity.Bed;
import com.rms.backend.rooms.entity.Room;
import com.rms.backend.rooms.repository.BedRepository;
import com.rms.backend.rooms.repository.RoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class BlockController {

    private final BlockRepository blockRepository;
    private final FloorRepository floorRepository;
    private final BedRepository bedRepository;
    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;

    public BlockController(BlockRepository blockRepository,
                           FloorRepository floorRepository,
                           BedRepository bedRepository,
                           PropertyRepository propertyRepository,
                           RoomRepository roomRepository) {
        this.blockRepository = blockRepository;
        this.floorRepository = floorRepository;
        this.bedRepository = bedRepository;
        this.propertyRepository = propertyRepository;
        this.roomRepository = roomRepository;
    }

    // -------------------------------------------------------------
    // HIERARCHY INITIALIZATION & OVERVIEW
    // -------------------------------------------------------------

    @PostMapping("/properties/{propertyId}/initialize-hierarchy")
    @Transactional
    public ResponseEntity<Map<String, Object>> initializeHierarchy(@PathVariable Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found with ID: " + propertyId));

        // 1. Ensure at least one Block exists
        List<Block> blocks = blockRepository.findByPropertyId(propertyId);
        Block defaultBlock;
        if (blocks.isEmpty()) {
            String code = property.getCode() != null && !property.getCode().isBlank()
                    ? "BLK-" + property.getCode()
                    : "BLK-" + propertyId;
            defaultBlock = Block.builder()
                    .propertyId(propertyId)
                    .name("Main Block")
                    .code(code)
                    .status("ACTIVE")
                    .build();
            defaultBlock = blockRepository.save(defaultBlock);
            blocks.add(defaultBlock);
        } else {
            defaultBlock = blocks.get(0);
        }

        // 2. Ensure Floors exist for 1..totalFloors
        int floorCount = (property.getTotalFloors() != null && property.getTotalFloors() > 0)
                ? property.getTotalFloors()
                : 3;

        List<Floor> existingFloors = floorRepository.findByBlockId(defaultBlock.getId());
        List<Floor> allFloors = new ArrayList<>(existingFloors);

        for (int i = 1; i <= floorCount; i++) {
            final int floorNum = i;
            boolean exists = existingFloors.stream().anyMatch(f -> f.getFloorNumber() != null && f.getFloorNumber() == floorNum);
            if (!exists) {
                Floor f = Floor.builder()
                        .blockId(defaultBlock.getId())
                        .floorNumber(floorNum)
                        .name("Floor " + floorNum)
                        .status("ACTIVE")
                        .build();
                allFloors.add(floorRepository.save(f));
            }
        }
        allFloors.sort(Comparator.comparingInt(Floor::getFloorNumber));

        // 3. Re-link any existing rooms that have null floorId
        List<Room> rooms = roomRepository.findByPropertyId(propertyId);
        for (Room r : rooms) {
            if (r.getFloorId() == null && r.getFloor() != null) {
                try {
                    int rFloorNum = Integer.parseInt(r.getFloor().trim());
                    allFloors.stream()
                            .filter(f -> f.getFloorNumber() != null && f.getFloorNumber() == rFloorNum)
                            .findFirst()
                            .ifPresent(matchedFloor -> {
                                r.setFloorId(matchedFloor.getId());
                                roomRepository.save(r);
                            });
                } catch (NumberFormatException ignored) {}
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("propertyId", propertyId);
        result.put("propertyName", property.getName());
        result.put("block", defaultBlock);
        result.put("floors", allFloors);
        result.put("totalFloorsInitialized", allFloors.size());
        result.put("message", "Property blocks and floors hierarchy initialized successfully");

        return ResponseEntity.ok(result);
    }

    @GetMapping("/properties/{propertyId}/floors")
    public ResponseEntity<List<Floor>> getFloorsByProperty(@PathVariable Long propertyId) {
        List<Block> blocks = blockRepository.findByPropertyId(propertyId);
        if (blocks.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        List<Long> blockIds = blocks.stream().map(Block::getId).collect(Collectors.toList());
        List<Floor> floors = floorRepository.findByBlockIdInOrderByFloorNumberAsc(blockIds);
        return ResponseEntity.ok(floors);
    }

    @GetMapping("/properties/{propertyId}/hierarchy")
    public ResponseEntity<Map<String, Object>> getPropertyHierarchy(@PathVariable Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found with ID: " + propertyId));

        List<Block> blocks = blockRepository.findByPropertyId(propertyId);
        List<Room> rooms = roomRepository.findByPropertyId(propertyId);

        List<Map<String, Object>> blockData = blocks.stream().map(block -> {
            List<Floor> floors = floorRepository.findByBlockIdOrderByFloorNumberAsc(block.getId());
            List<Map<String, Object>> floorData = floors.stream().map(floor -> {
                List<Room> floorRooms = rooms.stream()
                        .filter(r -> (floor.getId().equals(r.getFloorId())) ||
                                (r.getFloor() != null && r.getFloor().trim().equals(String.valueOf(floor.getFloorNumber()))))
                        .collect(Collectors.toList());

                List<Map<String, Object>> roomDetails = floorRooms.stream().map(rm -> {
                    List<Bed> beds = rm.getId() != null
                            ? bedRepository.findByRoomIdOrderByBedNumberAsc(rm.getId())
                            : Collections.emptyList();
                    Map<String, Object> rmMap = new LinkedHashMap<>();
                    rmMap.put("room", rm);
                    rmMap.put("beds", beds);
                    return rmMap;
                }).collect(Collectors.toList());

                Map<String, Object> flMap = new LinkedHashMap<>();
                flMap.put("floor", floor);
                flMap.put("rooms", roomDetails);
                return flMap;
            }).collect(Collectors.toList());

            Map<String, Object> blkMap = new LinkedHashMap<>();
            blkMap.put("block", block);
            blkMap.put("floors", floorData);
            return blkMap;
        }).collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("property", property);
        response.put("blocks", blockData);
        response.put("totalBlocks", blocks.size());
        response.put("totalRooms", rooms.size());

        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------
    // BLOCKS CRUD
    // -------------------------------------------------------------

    @GetMapping("/properties/{propertyId}/blocks")
    public ResponseEntity<List<Block>> getBlocksByProperty(@PathVariable Long propertyId) {
        List<Block> blocks = blockRepository.findByPropertyId(propertyId);
        return ResponseEntity.ok(blocks);
    }

    @PostMapping("/properties/{propertyId}/blocks")
    public ResponseEntity<Block> createBlock(@PathVariable Long propertyId, @RequestBody Map<String, String> payload) {
        String name = payload.get("name");
        String code = payload.get("code");
        if (name == null || name.isBlank() || code == null || code.isBlank()) {
            throw new IllegalArgumentException("Block name and code are required");
        }
        if (blockRepository.existsByPropertyIdAndCode(propertyId, code.trim())) {
            throw new IllegalArgumentException("Block already exists with code: " + code + " in this property");
        }
        Block block = Block.builder()
                .propertyId(propertyId)
                .name(name.trim())
                .code(code.trim().toUpperCase())
                .status("ACTIVE")
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(blockRepository.save(block));
    }

    @GetMapping("/blocks/{blockId}")
    public ResponseEntity<Block> getBlockById(@PathVariable Long blockId) {
        Block block = blockRepository.findById(blockId)
                .orElseThrow(() -> new ResourceNotFoundException("Block not found with ID: " + blockId));
        return ResponseEntity.ok(block);
    }

    @PutMapping("/blocks/{blockId}")
    public ResponseEntity<Block> updateBlock(@PathVariable Long blockId, @RequestBody Map<String, String> payload) {
        Block block = blockRepository.findById(blockId)
                .orElseThrow(() -> new ResourceNotFoundException("Block not found with ID: " + blockId));

        String name = payload.get("name");
        String code = payload.get("code");
        String status = payload.get("status");

        if (name != null && !name.isBlank()) {
            block.setName(name.trim());
        }
        if (code != null && !code.isBlank() && !code.trim().equalsIgnoreCase(block.getCode())) {
            if (blockRepository.existsByPropertyIdAndCode(block.getPropertyId(), code.trim().toUpperCase())) {
                throw new IllegalArgumentException("Block code already in use: " + code);
            }
            block.setCode(code.trim().toUpperCase());
        }
        if (status != null && !status.isBlank()) {
            block.setStatus(status.trim().toUpperCase());
        }

        return ResponseEntity.ok(blockRepository.save(block));
    }

    @DeleteMapping("/blocks/{blockId}")
    public ResponseEntity<Map<String, String>> deleteBlock(@PathVariable Long blockId) {
        Block block = blockRepository.findById(blockId)
                .orElseThrow(() -> new ResourceNotFoundException("Block not found with ID: " + blockId));

        List<Floor> floors = floorRepository.findByBlockId(blockId);
        if (!floors.isEmpty()) {
            throw new IllegalStateException("Cannot delete block: it contains " + floors.size() + " floor(s). Delete floors first.");
        }

        blockRepository.delete(block);
        return ResponseEntity.ok(Map.of("message", "Block deleted successfully", "blockId", String.valueOf(blockId)));
    }

    // -------------------------------------------------------------
    // FLOORS CRUD
    // -------------------------------------------------------------

    @GetMapping("/blocks/{blockId}/floors")
    public ResponseEntity<List<Floor>> getFloorsByBlock(@PathVariable Long blockId) {
        List<Floor> floors = floorRepository.findByBlockIdOrderByFloorNumberAsc(blockId);
        return ResponseEntity.ok(floors);
    }

    @PostMapping("/blocks/{blockId}/floors")
    public ResponseEntity<Floor> createFloor(@PathVariable Long blockId, @RequestBody Map<String, Object> payload) {
        Object floorNumObj = payload.get("floorNumber");
        String name = (String) payload.get("name");
        if (floorNumObj == null) {
            throw new IllegalArgumentException("Floor number is required");
        }
        int floorNum = Integer.parseInt(floorNumObj.toString());
        if (floorRepository.existsByBlockIdAndFloorNumber(blockId, floorNum)) {
            throw new IllegalArgumentException("Floor " + floorNum + " already exists in this block");
        }
        Floor floor = Floor.builder()
                .blockId(blockId)
                .floorNumber(floorNum)
                .name(name != null && !name.isBlank() ? name.trim() : "Floor " + floorNum)
                .status("ACTIVE")
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(floorRepository.save(floor));
    }

    @GetMapping("/floors/{floorId}")
    public ResponseEntity<Floor> getFloorById(@PathVariable Long floorId) {
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor not found with ID: " + floorId));
        return ResponseEntity.ok(floor);
    }

    @PutMapping("/floors/{floorId}")
    public ResponseEntity<Floor> updateFloor(@PathVariable Long floorId, @RequestBody Map<String, Object> payload) {
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor not found with ID: " + floorId));

        if (payload.containsKey("name")) {
            String name = (String) payload.get("name");
            if (name != null) floor.setName(name.trim());
        }
        if (payload.containsKey("status")) {
            String status = (String) payload.get("status");
            if (status != null) floor.setStatus(status.trim().toUpperCase());
        }
        if (payload.containsKey("floorNumber")) {
            int newNum = Integer.parseInt(payload.get("floorNumber").toString());
            if (newNum != floor.getFloorNumber()) {
                if (floorRepository.existsByBlockIdAndFloorNumber(floor.getBlockId(), newNum)) {
                    throw new IllegalArgumentException("Floor number " + newNum + " already exists in this block");
                }
                floor.setFloorNumber(newNum);
            }
        }

        return ResponseEntity.ok(floorRepository.save(floor));
    }

    @DeleteMapping("/floors/{floorId}")
    public ResponseEntity<Map<String, String>> deleteFloor(@PathVariable Long floorId) {
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor not found with ID: " + floorId));

        // Check if any room is on this floor
        List<Room> rooms = roomRepository.findAll().stream()
                .filter(r -> floorId.equals(r.getFloorId()))
                .collect(Collectors.toList());
        if (!rooms.isEmpty()) {
            throw new IllegalStateException("Cannot delete floor: it has " + rooms.size() + " active room(s). Reassign or delete rooms first.");
        }

        floorRepository.delete(floor);
        return ResponseEntity.ok(Map.of("message", "Floor deleted successfully", "floorId", String.valueOf(floorId)));
    }

    // -------------------------------------------------------------
    // BED STATUS
    // -------------------------------------------------------------

    @PatchMapping("/beds/{bedId}/status")
    public ResponseEntity<Bed> updateBedStatus(@PathVariable Long bedId, @RequestBody Map<String, String> payload) {
        String newStatus = payload.get("status");
        if (newStatus == null || newStatus.isBlank()) {
            throw new IllegalArgumentException("Bed status is required");
        }
        Bed bed = bedRepository.findById(bedId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed not found with ID: " + bedId));

        bed.setStatus(newStatus.trim().toUpperCase());
        return ResponseEntity.ok(bedRepository.save(bed));
    }
}
