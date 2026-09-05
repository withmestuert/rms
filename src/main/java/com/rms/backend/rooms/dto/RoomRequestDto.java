package com.rms.backend.rooms.dto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RoomRequestDto {


    @NotBlank(message = "Room number is required")
    private String roomNo;

    @NotBlank(message = "Floor is required")
    private String floor;

    @NotBlank(message = "Room type is required")
    private String roomType;

    @NotNull(message = "Rent per month is required")
    @Min(value = 0, message = "Rent cannot be negative")
    private Integer rentPerMonth;

    @NotNull(message = "Occupancy is required")
    @Min(value = 1, message = "Occupancy must be at least 1")
    private Integer occupancy;

    @NotNull(message = "Availability is required")
    private Boolean available;

    // getters and setters

    public String getRoomNo() {
        return roomNo;
    }

    public void setRoomNo(String roomNo) {
        this.roomNo = roomNo;
    }

    public Boolean getAvailable() {
        return available;
    }

    public void setAvailable(Boolean available) {
        this.available = available;
    }

    public Integer getOccupancy() {
        return occupancy;
    }

    public void setOccupancy(Integer occupancy) {
        this.occupancy = occupancy;
    }

    public Integer getRentPerMonth() {
        return rentPerMonth;
    }

    public void setRentPerMonth(Integer rentPerMonth) {
        this.rentPerMonth = rentPerMonth;
    }

    public String getRoomType() {
        return roomType;
    }

    public void setRoomType(String roomType) {
        this.roomType = roomType;
    }

    public String getFloor() {
        return floor;
    }

    public void setFloor(String floor) {
        this.floor = floor;
    }
}