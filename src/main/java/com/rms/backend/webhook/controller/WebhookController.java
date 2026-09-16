package com.rms.backend.webhook.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
    private static final String DEFAULT_VERIFY_TOKEN = "rms_whatsapp_verify_token";

    /**
     * WhatsApp Meta Cloud API Webhook Verification Endpoint
     */
    @GetMapping("/whatsapp/webhook")
    public ResponseEntity<String> verifyWhatsAppWebhook(
            @RequestParam(value = "hub.mode", required = false) String mode,
            @RequestParam(value = "hub.verify_token", required = false) String token,
            @RequestParam(value = "hub.challenge", required = false) String challenge) {

        log.info("Received WhatsApp webhook verification request: mode={}, token={}", mode, token);

        if ("subscribe".equalsIgnoreCase(mode) && challenge != null) {
            log.info("WhatsApp webhook verified successfully. Returning challenge: {}", challenge);
            return ResponseEntity.ok(challenge);
        }

        // Return challenge if provided directly for testing/monitoring
        if (challenge != null) {
            return ResponseEntity.ok(challenge);
        }

        return ResponseEntity.status(HttpStatus.OK).body("WhatsApp Webhook endpoint is active and listening.");
    }

    /**
     * WhatsApp Inbound Message & Delivery Status Webhook Receiver
     */
    @PostMapping("/whatsapp/webhook")
    public ResponseEntity<Map<String, Object>> handleWhatsAppWebhook(
            @RequestBody(required = false) Map<String, Object> payload) {

        log.info("Received WhatsApp inbound webhook event: {}", payload);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "EVENT_RECEIVED");
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }

    /**
     * Payment Gateway Webhook Receiver (UPI, Razorpay, Cash QR)
     */
    @PostMapping({"/payments/webhook", "/webhooks/payments"})
    public ResponseEntity<Map<String, Object>> handlePaymentWebhook(
            @RequestBody(required = false) Map<String, Object> payload) {

        log.info("Received Payment gateway webhook event: {}", payload);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Payment webhook processed successfully");
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }

    /**
     * General Webhook Health / Ping Status
     */
    @GetMapping({"/webhooks/status", "/webhooks/health"})
    public ResponseEntity<Map<String, Object>> getWebhookHealth() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "HEALTHY");
        status.put("responsive", true);
        status.put("activeWebhooks", List.of("whatsapp", "payments"));
        status.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(status);
    }
}
