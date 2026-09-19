package com.rms.backend.notifications.controller;

import com.rms.backend.common.ApiPaths;
import com.rms.backend.notifications.service.WhatsAppNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(ApiPaths.API_NOTIFICATIONS_WHATSAPP)
@RequiredArgsConstructor
public class WhatsAppController {

    private final WhatsAppNotificationService whatsAppNotificationService;

    @GetMapping(ApiPaths.WELCOME_PREVIEW_TENANTUID)
    public ResponseEntity<Map<String, Object>> getWelcomePreview(@PathVariable String tenantUid) {
        Map<String, Object> preview = whatsAppNotificationService.generateWelcomePackage(tenantUid);
        return ResponseEntity.ok(preview);
    }

    @PostMapping(ApiPaths.SEND_WELCOME_TENANTUID)
    public ResponseEntity<Map<String, Object>> sendWelcomeMessage(@PathVariable String tenantUid) {
        Map<String, Object> result = whatsAppNotificationService.generateWelcomePackage(tenantUid);
        result.put("status", "SENT_OR_PREPARED");
        return ResponseEntity.ok(result);
    }
}
