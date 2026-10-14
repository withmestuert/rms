package com.rms.backend.billing.controller;

import com.rms.backend.billing.dto.*;
import com.rms.backend.billing.service.RazorpayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments/razorpay")
@RequiredArgsConstructor
@Slf4j
public class RazorpayController {

    private final RazorpayService razorpayService;

    @PostMapping("/create-order")
    public ResponseEntity<RazorpayOrderResponseDto> createOrder(
            @Valid @RequestBody RazorpayOrderRequestDto dto) {
        log.info("Creating Razorpay order for invoice: {}", dto.getInvoiceNumber());
        RazorpayOrderResponseDto response = razorpayService.createOrder(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/verify-payment")
    public ResponseEntity<PaymentResponseDto> verifyPayment(
            @Valid @RequestBody RazorpayVerifyRequestDto dto) {
        log.info("Verifying Razorpay payment for order: {}, paymentId: {}", dto.getOrderId(), dto.getPaymentId());
        PaymentResponseDto response = razorpayService.verifyAndProcessPayment(dto);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        log.info("Received Razorpay Webhook notification");
        razorpayService.processWebhook(rawPayload, signature);
        return ResponseEntity.ok("OK");
    }
}
