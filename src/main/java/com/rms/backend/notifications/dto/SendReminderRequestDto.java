package com.rms.backend.notifications.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendReminderRequestDto {

    @NotEmpty(message = "At least one invoice ID must be provided")
    private List<Long> invoiceIds;

    @Builder.Default
    private String channel = "WHATSAPP"; // WHATSAPP, SMS, EMAIL

    private String customMessage; // Optional override
}
