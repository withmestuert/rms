package com.rms.backend.billing.service;

import com.rms.backend.billing.dto.*;
import com.rms.backend.billing.entity.GatewayOrder;
import com.rms.backend.billing.entity.Invoice;
import com.rms.backend.billing.entity.InvoiceStatus;
import com.rms.backend.billing.entity.Payment;
import com.rms.backend.billing.repository.GatewayOrderRepository;
import com.rms.backend.billing.repository.InvoiceRepository;
import com.rms.backend.billing.repository.PaymentRepository;
import com.rms.backend.exception.DuplicateResourceException;
import com.rms.backend.exception.ResourceNotFoundException;
import java.util.List;
import com.rms.backend.properties.entity.Property;
import com.rms.backend.properties.repository.PropertyRepository;
import com.rms.backend.tenants.entity.Tenant;
import com.rms.backend.tenants.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class RazorpayService {

    private final InvoiceRepository invoiceRepository;
    private final GatewayOrderRepository gatewayOrderRepository;
    private final TenantRepository tenantRepository;
    private final PropertyRepository propertyRepository;
    private final PaymentRepository paymentRepository;
    private final BillingService billingService;

    @Value("${razorpay.key.id:rzp_test_rms_mock_key}")
    private String keyId;

    @Value("${razorpay.key.secret:rzp_test_rms_mock_secret}")
    private String keySecret;

    @Value("${razorpay.webhook.secret:rzp_webhook_secret_default}")
    private String webhookSecret;

    @Value("${razorpay.company.name:RMS Living}")
    private String companyName;

    @Value("${razorpay.currency:INR}")
    private String currency;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public boolean isSimulationMode() {
        return keyId == null || keyId.isBlank()
                || keyId.startsWith("rzp_test_rms_mock")
                || keySecret == null
                || keySecret.startsWith("rzp_test_rms_mock");
    }

    /**
     * Create Razorpay Order for a tenant invoice.
     */
    @Transactional
    public RazorpayOrderResponseDto createOrder(RazorpayOrderRequestDto dto) {
        Invoice invoice = invoiceRepository.findByInvoiceNumber(dto.getInvoiceNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + dto.getInvoiceNumber()));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new DuplicateResourceException("Invoice " + invoice.getInvoiceNumber() + " is already marked as PAID");
        }

        int currentPaid = invoice.getPaidAmount() != null ? invoice.getPaidAmount() : 0;
        int outstanding = invoice.getAmount() - currentPaid;
        if (outstanding <= 0) {
            invoice.setStatus(InvoiceStatus.PAID);
            invoiceRepository.save(invoice);
            throw new DuplicateResourceException("Invoice " + invoice.getInvoiceNumber() + " has zero outstanding balance");
        }

        int payAmount = (dto.getAmount() != null && dto.getAmount() > 0)
                ? dto.getAmount()
                : outstanding;

        if (payAmount > outstanding) {
            throw new IllegalArgumentException("Amount ₹" + payAmount + " exceeds outstanding balance ₹" + outstanding);
        }

        long amountInPaise = (long) payAmount * 100L;
        String orderId;
        boolean sim = isSimulationMode();

        if (sim) {
            orderId = "order_sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            log.info("[Simulation] Created simulated Razorpay Order: {} for Invoice: {} (₹{})", orderId, invoice.getInvoiceNumber(), payAmount);
        } else {
            orderId = callRazorpayCreateOrder(amountInPaise, invoice.getInvoiceNumber(), invoice.getTenantUid());
        }

        GatewayOrder order = GatewayOrder.builder()
                .orderId(orderId)
                .invoiceId(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .tenantUid(invoice.getTenantUid())
                .propertyId(invoice.getPropertyId())
                .amount(payAmount)
                .amountInPaise(amountInPaise)
                .currency(currency)
                .gatewayProvider("RAZORPAY")
                .status("CREATED")
                .createdAt(LocalDateTime.now())
                .build();

        gatewayOrderRepository.save(order);

        // Fetch tenant details for checkout prefill
        Optional<Tenant> tenantOpt = tenantRepository.findById(invoice.getTenantUid());
        String tenantContact = tenantOpt.map(Tenant::getMobileNumber).orElse("");

        return RazorpayOrderResponseDto.builder()
                .orderId(orderId)
                .keyId(keyId)
                .amountInPaise(amountInPaise)
                .amountInRupees(payAmount)
                .currency(currency)
                .invoiceNumber(invoice.getInvoiceNumber())
                .tenantName(invoice.getTenantName())
                .tenantContact(tenantContact)
                .companyName(companyName)
                .description("Rent Payment for " + invoice.getMonthYear() + " (Room " + invoice.getRoomNo() + ")")
                .simulationMode(sim)
                .build();
    }

    /**
     * Call live Razorpay Orders API
     */
    private String callRazorpayCreateOrder(long amountInPaise, String receipt, String tenantUid) {
        try {
            String jsonPayload = String.format(
                    "{\"amount\":%d,\"currency\":\"%s\",\"receipt\":\"%s\",\"notes\":{\"invoiceNumber\":\"%s\",\"tenantUid\":\"%s\"}}",
                    amountInPaise, currency, receipt, receipt, tenantUid != null ? tenantUid : ""
            );
            String auth = Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.razorpay.com/v1/orders"))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String extractedId = extractJsonField(response.body(), "id");
                if (extractedId != null && !extractedId.isBlank()) {
                    return extractedId;
                }
                throw new RuntimeException("Could not extract order id from Razorpay response: " + response.body());
            } else {
                log.error("Razorpay API error (status {}): {}", response.statusCode(), response.body());
                throw new RuntimeException("Failed to create Razorpay order: " + response.body());
            }
        } catch (Exception e) {
            log.error("Error communicating with Razorpay API", e);
            throw new RuntimeException("Payment Gateway connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Cryptographically verify payment signature and process authoritative ledger & invoice settlement.
     */
    @Transactional
    public PaymentResponseDto verifyAndProcessPayment(RazorpayVerifyRequestDto dto) {
        GatewayOrder order = gatewayOrderRepository.findByOrderId(dto.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Gateway Order not found: " + dto.getOrderId()));

        if ("PAID".equalsIgnoreCase(order.getStatus())) {
            log.info("Order {} was already marked PAID. Returning existing status.", order.getOrderId());
            Invoice inv = invoiceRepository.findById(order.getInvoiceId()).orElse(null);
            List<Payment> payments = paymentRepository.findByInvoiceId(order.getInvoiceId());
            if (!payments.isEmpty()) {
                Payment latest = payments.get(payments.size() - 1);
                return billingService.toPaymentResponseDto(latest, inv);
            }
            return PaymentResponseDto.builder()
                    .invoiceId(order.getInvoiceId())
                    .invoiceNumber(order.getInvoiceNumber())
                    .amount(order.getAmount())
                    .status("SUCCESS")
                    .build();
        }

        boolean validSignature = verifyRazorpaySignature(dto.getOrderId(), dto.getPaymentId(), dto.getSignature());
        if (!validSignature && !isSimulationMode()) {
            order.setStatus("FAILED");
            order.setErrorDescription("Invalid HMAC-SHA256 signature verification");
            gatewayOrderRepository.save(order);
            throw new IllegalArgumentException("Invalid payment signature. Payment cannot be verified.");
        }

        // Update Gateway Order status
        order.setStatus("PAID");
        order.setRazorpayPaymentId(dto.getPaymentId());
        order.setRazorpaySignature(dto.getSignature());
        order.setPaymentMethod(dto.getPaymentMethod() != null ? dto.getPaymentMethod().toUpperCase() : "UPI");
        order.setVpa(dto.getVpa());
        order.setPaidAt(LocalDateTime.now());
        gatewayOrderRepository.save(order);

        // Authoritatively settle invoice & write double-entry immutable ledger entry
        String effectiveMode = (dto.getPaymentMethod() != null && !dto.getPaymentMethod().isBlank())
                ? dto.getPaymentMethod().toUpperCase()
                : "UPI";

        String effectiveTxnRef = dto.getPaymentId() + (dto.getVpa() != null && !dto.getVpa().isBlank() ? " (" + dto.getVpa() + ")" : "");

        PaymentRequestDto paymentRequest = PaymentRequestDto.builder()
                .invoiceId(order.getInvoiceId())
                .tenantUid(order.getTenantUid())
                .amount(order.getAmount())
                .paymentMode(effectiveMode)
                .transactionReference(effectiveTxnRef)
                .idempotencyKey("RZP-" + dto.getPaymentId())
                .propertyId(order.getPropertyId())
                .build();

        log.info("Authoritatively recording Razorpay payment for invoice {} (amount ₹{}, paymentId: {})",
                order.getInvoiceNumber(), order.getAmount(), dto.getPaymentId());

        return billingService.recordPaymentAuthoritative(paymentRequest);
    }

    /**
     * Compute and verify Razorpay HMAC-SHA256 signature.
     */
    public boolean verifyRazorpaySignature(String orderId, String paymentId, String signature) {
        if (orderId == null || paymentId == null || signature == null) {
            return false;
        }

        if (isSimulationMode() && signature.startsWith("sim_sig_")) {
            return true;
        }

        String data = orderId + "|" + paymentId;
        return calculateHmacSha256(data, keySecret).equalsIgnoreCase(signature);
    }

    /**
     * Authoritative Webhook handler for asynchronous Razorpay notifications.
     */
    @Transactional
    public void processWebhook(String rawPayload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new IllegalArgumentException("Missing X-Razorpay-Signature header");
        }

        if (!isSimulationMode()) {
            String calculated = calculateHmacSha256(rawPayload, webhookSecret);
            if (!calculated.equalsIgnoreCase(signatureHeader)) {
                log.warn("Webhook signature mismatch! Rejecting unverified webhook callback.");
                throw new IllegalArgumentException("Invalid webhook signature");
            }
        }

        try {
            String event = extractJsonField(rawPayload, "event");
            log.info("Received Razorpay Webhook Event: {}", event);

            if ("payment.captured".equalsIgnoreCase(event) || "order.paid".equalsIgnoreCase(event)) {
                String orderId = extractJsonField(rawPayload, "order_id");
                String paymentId = extractJsonField(rawPayload, "id");
                String method = extractJsonField(rawPayload, "method");
                String vpa = extractJsonField(rawPayload, "vpa");

                if (orderId != null && paymentId != null && !orderId.isBlank() && !paymentId.isBlank()) {
                    RazorpayVerifyRequestDto verifyDto = RazorpayVerifyRequestDto.builder()
                            .orderId(orderId)
                            .paymentId(paymentId)
                            .signature(signatureHeader)
                            .paymentMethod(method != null ? method : "UPI")
                            .vpa(vpa)
                            .build();
                    verifyAndProcessPayment(verifyDto);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process Razorpay webhook payload", e);
            throw new RuntimeException("Webhook processing error: " + e.getMessage(), e);
        }
    }

    /**
     * Fetch public invoice information by invoice number for tenant self-service payment page.
     */
    public PublicInvoiceDto getPublicInvoice(String invoiceNumber) {
        Invoice invoice = invoiceRepository.findByInvoiceNumber(invoiceNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceNumber));

        int currentPaid = invoice.getPaidAmount() != null ? invoice.getPaidAmount() : 0;
        int pending = Math.max(0, invoice.getAmount() - currentPaid);

        String tenantPhone = "";
        if (invoice.getTenantUid() != null) {
            tenantPhone = tenantRepository.findById(invoice.getTenantUid())
                    .map(Tenant::getMobileNumber)
                    .orElse("");
        }

        String propName = "RMS Living";
        String propAddress = "";
        String propContact = "";
        if (invoice.getPropertyId() != null) {
            Optional<Property> propOpt = propertyRepository.findById(invoice.getPropertyId());
            if (propOpt.isPresent()) {
                Property prop = propOpt.get();
                propName = prop.getName();
                propAddress = (prop.getAddress() != null ? prop.getAddress() + ", " : "")
                        + (prop.getCity() != null ? prop.getCity() : "");
                propContact = prop.getContactNumber() != null ? prop.getContactNumber() : "";
            }
        }

        return PublicInvoiceDto.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .tenantUid(invoice.getTenantUid())
                .tenantName(invoice.getTenantName())
                .tenantPhone(tenantPhone)
                .roomNo(invoice.getRoomNo())
                .monthYear(invoice.getMonthYear())
                .amount(invoice.getAmount())
                .paidAmount(currentPaid)
                .pendingAmount(pending)
                .dueDate(invoice.getDueDate())
                .status(invoice.getStatus().name())
                .paidOn(invoice.getPaidOn())
                .paymentMode(invoice.getPaymentMode())
                .transactionRef(invoice.getTransactionRef())
                .propertyId(invoice.getPropertyId())
                .propertyName(propName)
                .propertyAddress(propAddress)
                .propertyContact(propContact)
                .build();
    }

    public static String calculateHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate HMAC-SHA256", e);
        }
    }

    /**
     * Regex helper to extract a string field from JSON without external libraries.
     */
    private static String extractJsonField(String json, String key) {
        if (json == null) return null;
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
