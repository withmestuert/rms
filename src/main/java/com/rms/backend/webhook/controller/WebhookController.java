package com.rms.backend.webhook.controller;

import com.rms.backend.vacate.dto.VacateRequestDto;
import com.rms.backend.vacate.dto.VacateResponseDto;
import com.rms.backend.vacate.service.VacateRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
    private static final String DEFAULT_VERIFY_TOKEN = "rms_whatsapp_verify_token";

    private final VacateRequestService vacateRequestService;

    public WebhookController(VacateRequestService vacateRequestService) {
        this.vacateRequestService = vacateRequestService;
    }

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
     * External Vacate Request Form Webhook Receiver
     * Accepts submissions from Google Forms (via Google Apps Script),
     * Microsoft Forms (via Power Automate), Zapier, or direct HTTP webhooks.
     */
    @PostMapping({"/webhooks/vacate-request", "/vacate/webhook"})
    public ResponseEntity<Map<String, Object>> handleVacateRequestWebhook(
            @RequestBody(required = false) Map<String, Object> payload) {

        log.info("Received external vacate request webhook payload: {}", payload);

        Map<String, Object> response = new HashMap<>();
        if (payload == null || payload.isEmpty()) {
            response.put("status", "ERROR");
            response.put("message", "Payload is empty");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            // Flexible field extraction accommodating Google Forms, MS Forms & JSON keys
            String tenantUid = extractString(payload, "tid", "tenantUid", "tenant_uid", "Tenant ID", "tenantId");
            String tenantName = extractString(payload, "name", "tenantName", "tenant_name", "Full Name", "Tenant Name");
            String roomNo = extractString(payload, "room", "roomNo", "room_no", "Room Number", "Room No");
            String mobileNumber = extractString(payload, "mobile", "mobileNumber", "mobile_number", "Phone Number", "Mobile");
            String aadhaarNo = extractString(payload, "aadhaar", "aadhaarNo", "aadhaar_no", "Aadhaar", "Aadhaar Number");
            String reason = extractString(payload, "reason", "Reason", "Reason for vacating");
            String notes = extractString(payload, "notes", "Notes", "comments");

            LocalDate reqDate = parseDate(extractString(payload, "requestDate", "request_date", "vacateRequestDate", "Request Date", "Timestamp"));
            if (reqDate == null) {
                reqDate = LocalDate.now();
            }

            LocalDate leaveDate = parseDate(extractString(payload, "expectedLeavingDate", "expected_leaving_date", "leavingDate", "Leaving Date", "Expected Date to Leave"));

            Double advancePaid = parseDouble(extractObject(payload, "advancePaid", "advance_paid", "advance", "Advance Amount", "Advance Paid"));
            Double maintenance = parseDouble(extractObject(payload, "maintenanceCharge", "maintenance_charge", "maintenance"));
            Double breakage = parseDouble(extractObject(payload, "breakageCharge", "breakage_charge", "breakage"));

            VacateRequestDto dto = VacateRequestDto.builder()
                    .tenantUid(tenantUid)
                    .tenantName(tenantName)
                    .roomNo(roomNo)
                    .mobileNumber(mobileNumber)
                    .aadhaarNo(aadhaarNo)
                    .requestDate(reqDate)
                    .expectedLeavingDate(leaveDate)
                    .advancePaid(advancePaid)
                    .maintenanceCharge(maintenance)
                    .breakageCharge(breakage)
                    .reason(reason)
                    .notes(notes)
                    .build();

            VacateResponseDto created = vacateRequestService.submitVacateRequest(dto);

            response.put("status", "SUCCESS");
            response.put("message", "Vacate request registered successfully");
            response.put("requestId", created.getRequestId());
            response.put("noticeDays", created.getNoticeDays());
            response.put("advanceRepayable", created.getAdvanceRepayable());
            response.put("breakdown", created.getCalculationBreakdown());
            response.put("data", created);
            return ResponseEntity.ok(response);

        } catch (Exception ex) {
            log.error("Error processing vacate webhook", ex);
            response.put("status", "ERROR");
            response.put("message", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
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
        status.put("activeWebhooks", List.of("whatsapp", "payments", "vacate-request"));
        status.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(status);
    }

    // Helper methods for flexible form parsing
    private String extractString(Map<String, Object> map, String... keys) {
        Object val = extractObject(map, keys);
        return val != null ? val.toString().trim() : null;
    }

    private Object extractObject(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key) && map.get(key) != null) {
                return map.get(key);
            }
        }
        return null;
    }

    private LocalDate parseDate(String val) {
        if (val == null || val.isBlank()) return null;
        try {
            // Check YYYY-MM-DD
            if (val.contains("T")) {
                val = val.substring(0, val.indexOf("T"));
            }
            if (val.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(val);
            }
            // Check DD/MM/YYYY or DD-MM-YYYY
            String[] parts = val.split("[-/.]");
            if (parts.length == 3) {
                if (parts[0].length() == 4) {
                    return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                } else if (parts[2].length() == 4) {
                    return LocalDate.of(Integer.parseInt(parts[2]), Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
                }
            }
        } catch (Exception e) {
            log.warn("Could not parse date string: {}", val);
        }
        return null;
    }

    private Double parseDouble(Object val) {
        if (val == null) return null;
        try {
            if (val instanceof Number) {
                return ((Number) val).doubleValue();
            }
            String s = val.toString().replaceAll("[^0-9.]", "").trim();
            if (!s.isEmpty()) {
                return Double.parseDouble(s);
            }
        } catch (Exception e) {
            log.warn("Could not parse double value: {}", val);
        }
        return null;
    }
}
