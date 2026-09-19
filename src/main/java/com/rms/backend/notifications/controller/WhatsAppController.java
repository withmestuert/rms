package com.rms.backend.notifications.controller;

import com.rms.backend.notifications.service.WhatsAppNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications/whatsapp")
@RequiredArgsConstructor
public class WhatsAppController {

    private final WhatsAppNotificationService whatsAppNotificationService;

    @GetMapping("/welcome-preview/{tenantUid}")
    public ResponseEntity<Map<String, Object>> getWelcomePreview(@PathVariable String tenantUid) {
        Map<String, Object> preview = whatsAppNotificationService.generateWelcomePackage(tenantUid);
        return ResponseEntity.ok(preview);
    }

    @PostMapping("/send-welcome/{tenantUid}")
    public ResponseEntity<Map<String, Object>> sendWelcomeMessage(@PathVariable String tenantUid) {
        Map<String, Object> result = whatsAppNotificationService.generateWelcomePackage(tenantUid);
        result.put("status", "SENT_OR_PREPARED");
        return ResponseEntity.ok(result);
    }
}
