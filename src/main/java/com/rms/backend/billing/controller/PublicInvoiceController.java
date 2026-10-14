package com.rms.backend.billing.controller;

import com.rms.backend.billing.dto.PublicInvoiceDto;
import com.rms.backend.billing.service.RazorpayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/invoices")
@RequiredArgsConstructor
@Slf4j
public class PublicInvoiceController {

    private final RazorpayService razorpayService;

    @GetMapping("/{invoiceNumber}")
    public ResponseEntity<PublicInvoiceDto> getPublicInvoice(@PathVariable String invoiceNumber) {
        log.info("Fetching public invoice for self-service payment: {}", invoiceNumber);
        PublicInvoiceDto dto = razorpayService.getPublicInvoice(invoiceNumber);
        return ResponseEntity.ok(dto);
    }
}
