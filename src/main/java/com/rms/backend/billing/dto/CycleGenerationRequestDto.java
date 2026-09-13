package com.rms.backend.billing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CycleGenerationRequestDto {

    @NotBlank(message = "Billing month/year is required")
    private String monthYear; // e.g. "October 2024"

    @NotBlank(message = "Due date is required")
    private String dueDate; // YYYY-MM-DD
}
