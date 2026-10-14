package com.rms.backend.notifications.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendReminderResultDto {

    private int totalRequested;
    private int successCount;
    private int failedCount;
    private String channel;
    private List<String> dispatchedInvoiceNumbers;
    private List<String> errors;
}
