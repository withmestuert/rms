package com.rms.backend.notifications.service;

import com.rms.backend.tenants.entity.Tenant;
import com.rms.backend.tenants.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppNotificationService {

    private final TenantRepository tenantRepository;

    @Value("${rms.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Value("${rms.external.vacate-form-url:}")
    private String configuredVacateFormUrl;

    @Value("${rms.upi.id:rmsmanager@okhdfcbank}")
    private String upiId;

    /**
     * Builds complete WhatsApp welcome message payload and quick action URLs
     */
    public Map<String, Object> generateWelcomePackage(String tenantUid) {
        Tenant tenant = tenantRepository.findById(tenantUid)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found with UID: " + tenantUid));

        String cleanPhone = tenant.getMobileNumber() != null
                ? tenant.getMobileNumber().replaceAll("[^0-9]", "")
                : "";
        if (cleanPhone.length() == 10) {
            cleanPhone = "91" + cleanPhone; // Default India country code
        }

        // Generate URLs
        String effectiveVacateBase = (configuredVacateFormUrl != null && !configuredVacateFormUrl.isBlank())
                ? configuredVacateFormUrl
                : (frontendBaseUrl + "/vacate-form.html");

        String vacateFormUrl = String.format("%s?tid=%s&room=%s&mobile=%s&aadhaar=%s&name=%s&advance=%d",
                effectiveVacateBase,
                encode(tenant.getUid()),
                encode(tenant.getRoomNo()),
                encode(tenant.getMobileNumber()),
                encode(tenant.getAadhaarNo() != null ? tenant.getAadhaarNo() : ""),
                encode(tenant.getName()),
                tenant.getAdvancePaid() != null ? tenant.getAdvancePaid() : 0);

        String payRentUrl = String.format("%s/?page=rent-and-billing&tid=%s",
                frontendBaseUrl,
                encode(tenant.getUid()));

        String upiPayLink = String.format("upi://pay?pa=%s&pn=RMS&am=%d&cu=INR&tn=Rent_Room_%s",
                upiId,
                tenant.getStandardRent() != null ? tenant.getStandardRent() : 0,
                tenant.getRoomNo());

        String receiptNumber = "REC-ADV-" + System.currentTimeMillis() % 100000;

        // Build WhatsApp Formatted Markdown Text
        StringBuilder msg = new StringBuilder();
        msg.append("🏨 *WELCOME TO YOUR RESIDENCE!*\n");
        msg.append("━━━━━━━━━━━━━━━━━━━━━━━\n");
        msg.append("Dear *").append(tenant.getName()).append("*,\n\n");
        msg.append("Welcome! Your room allocation in *Room ").append(tenant.getRoomNo()).append("* has been officially confirmed.\n\n");

        msg.append("📋 *Resident Profile:*\n");
        msg.append("• *Tenant ID (TID):* `").append(tenant.getUid()).append("`\n");
        msg.append("• *Room Number:* ").append(tenant.getRoomNo()).append("\n");
        msg.append("• *Contact Mobile:* ").append(tenant.getMobileNumber()).append("\n");
        msg.append("• *Aadhaar Number:* ").append(maskAadhaar(tenant.getAadhaarNo())).append("\n\n");

        msg.append("🧾 *Advance Payment Receipt:*\n");
        msg.append("• *Receipt No:* ").append(receiptNumber).append("\n");
        msg.append("• *Advance Amount Paid:* ₹").append(tenant.getAdvancePaid()).append(" (Verified ✅)\n");
        msg.append("• *Standard Monthly Rent:* ₹").append(tenant.getStandardRent()).append("\n");
        msg.append("• *Payment Date:* ").append(LocalDate.now().toString()).append("\n\n");

        msg.append("━━━━━━━━━━━━━━━━━━━━━━━\n");
        msg.append("⚡ *QUICK ACTIONS & SERVICES:*\n\n");

        msg.append("💳 *1. Pay Monthly Rent:*\n");
        msg.append("Click here to pay or view rent invoices:\n");
        msg.append("👉 ").append(payRentUrl).append("\n\n");

        msg.append("🚪 *2. Vacate Request Form:*\n");
        msg.append("When planning to move out, submit your 30-day vacate notice using this verified link:\n");
        msg.append("👉 ").append(vacateFormUrl).append("\n\n");

        msg.append("ℹ️ *Advance Refund Policy:*\n");
        msg.append("• Notice $\\ge$ 30 days: Full advance repayable (minus maintenance/breakage charges).\n");
        msg.append("• Notice < 30 days: Pro-rated refund = `(Advance / 30) * Notice Days`.\n\n");
        msg.append("Have a pleasant stay! Reach out to management anytime.\n");

        String fullMessage = msg.toString();
        String whatsappClickToChatUrl = "https://wa.me/" + cleanPhone + "?text=" + encode(fullMessage);

        Map<String, Object> result = new HashMap<>();
        result.put("tenantUid", tenant.getUid());
        result.put("tenantName", tenant.getName());
        result.put("phone", cleanPhone);
        result.put("message", fullMessage);
        result.put("whatsappUrl", whatsappClickToChatUrl);
        result.put("vacateFormUrl", vacateFormUrl);
        result.put("payRentUrl", payRentUrl);
        result.put("upiPayLink", upiPayLink);
        result.put("receiptNumber", receiptNumber);

        log.info("Generated WhatsApp package for tenant: {} (Phone: {})", tenant.getName(), cleanPhone);
        return result;
    }

    private String encode(String text) {
        if (text == null) return "";
        try {
            return URLEncoder.encode(text, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            return text;
        }
    }

    private String maskAadhaar(String aadhaar) {
        if (aadhaar == null || aadhaar.length() < 4) return "XXXX-XXXX-XXXX";
        String last4 = aadhaar.substring(aadhaar.length() - 4);
        return "XXXX-XXXX-" + last4;
    }
}
