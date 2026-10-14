package com.rms.backend.notifications.service;

import com.rms.backend.billing.entity.Invoice;
import com.rms.backend.billing.entity.InvoiceStatus;
import com.rms.backend.billing.repository.InvoiceRepository;
import com.rms.backend.notifications.dto.*;
import com.rms.backend.notifications.entity.ReminderLog;
import com.rms.backend.notifications.repository.ReminderLogRepository;
import com.rms.backend.properties.entity.Property;
import com.rms.backend.properties.repository.PropertyRepository;
import com.rms.backend.tenants.entity.Tenant;
import com.rms.backend.tenants.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RentReminderService {

    private final InvoiceRepository invoiceRepository;
    private final TenantRepository tenantRepository;
    private final PropertyRepository propertyRepository;
    private final ReminderLogRepository reminderLogRepository;

    @Value("${rms.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    /**
     * Get list of pending rent collection reminders categorized by urgency.
     */
    @Transactional(readOnly = true)
    public List<RentReminderItemDto> getPendingRemindersQueue(Long propertyId, String filterCategory) {
        List<Invoice> invoices = propertyId != null
                ? invoiceRepository.findByPropertyId(propertyId)
                : invoiceRepository.findAll();

        // Filter to only pending or partially paid invoices with positive outstanding balance
        LocalDate today = LocalDate.now();
        List<RentReminderItemDto> items = new ArrayList<>();

        // Cache properties and tenants
        Map<Long, Property> propertyMap = propertyRepository.findAll().stream()
                .collect(Collectors.toMap(Property::getId, p -> p, (a, b) -> a));

        Map<String, Tenant> tenantMap = tenantRepository.findAll().stream()
                .collect(Collectors.toMap(Tenant::getUid, t -> t, (a, b) -> a));

        for (Invoice inv : invoices) {
            int currentPaid = inv.getPaidAmount() != null ? inv.getPaidAmount() : 0;
            int pending = inv.getAmount() - currentPaid;

            if (inv.getStatus() == InvoiceStatus.PAID || pending <= 0) {
                continue;
            }

            LocalDate dueDate = parseDate(inv.getDueDate());
            long daysOffset = dueDate != null ? ChronoUnit.DAYS.between(dueDate, today) : 0;

            String category = determineReminderType(daysOffset);
            String urgency = determineUrgency(category);

            if (filterCategory != null && !filterCategory.isBlank() && !"ALL".equalsIgnoreCase(filterCategory)) {
                if (!filterCategory.equalsIgnoreCase(category)) {
                    continue;
                }
            }

            Tenant tenant = inv.getTenantUid() != null ? tenantMap.get(inv.getTenantUid()) : null;
            String phone = tenant != null && tenant.getMobileNumber() != null ? tenant.getMobileNumber() : "";
            Property prop = inv.getPropertyId() != null ? propertyMap.get(inv.getPropertyId()) : null;
            String propName = prop != null ? prop.getName() : "RMS Living";

            String paymentLink = frontendBaseUrl + "/pay/" + inv.getInvoiceNumber();
            String formattedMsg = buildWhatsAppMessage(inv.getTenantName(), inv.getRoomNo(), inv.getMonthYear(),
                    pending, inv.getDueDate(), propName, paymentLink, category);

            String cleanPhone = phone.replaceAll("[^0-9]", "");
            if (cleanPhone.length() == 10) {
                cleanPhone = "91" + cleanPhone;
            }
            String clickToChatUrl = !cleanPhone.isBlank()
                    ? "https://wa.me/" + cleanPhone + "?text=" + URLEncoder.encode(formattedMsg, StandardCharsets.UTF_8)
                    : "https://api.whatsapp.com/send?text=" + URLEncoder.encode(formattedMsg, StandardCharsets.UTF_8);

            // Fetch last reminder log
            List<ReminderLog> logs = reminderLogRepository.findByInvoiceIdOrderBySentAtDesc(inv.getId());
            LocalDateTime lastSent = !logs.isEmpty() ? logs.get(0).getSentAt() : null;

            items.add(RentReminderItemDto.builder()
                    .invoiceId(inv.getId())
                    .invoiceNumber(inv.getInvoiceNumber())
                    .tenantUid(inv.getTenantUid())
                    .tenantName(inv.getTenantName())
                    .mobileNumber(phone)
                    .roomNo(inv.getRoomNo())
                    .monthYear(inv.getMonthYear())
                    .totalAmount(inv.getAmount())
                    .paidAmount(currentPaid)
                    .pendingAmount(pending)
                    .dueDate(inv.getDueDate())
                    .reminderType(category)
                    .daysOffset((int) daysOffset)
                    .urgency(urgency)
                    .paymentLink(paymentLink)
                    .formattedWhatsAppMessage(formattedMsg)
                    .clickToChatUrl(clickToChatUrl)
                    .lastReminderSentAt(lastSent)
                    .totalRemindersSent(logs.size())
                    .propertyId(inv.getPropertyId())
                    .propertyName(propName)
                    .build());
        }

        // Sort: Most critical overdue first
        items.sort(Comparator.comparingInt(RentReminderItemDto::getDaysOffset).reversed());
        return items;
    }

    /**
     * Dispatch reminders in bulk and log authoritative audit history.
     */
    @Transactional
    public SendReminderResultDto dispatchReminders(SendReminderRequestDto dto, Long propertyId) {
        List<Long> invoiceIds = dto.getInvoiceIds() != null ? dto.getInvoiceIds() : Collections.emptyList();
        String channel = dto.getChannel() != null ? dto.getChannel().trim().toUpperCase() : "WHATSAPP";

        List<String> dispatched = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int success = 0;

        for (Long invId : invoiceIds) {
            Optional<Invoice> invOpt = invoiceRepository.findById(invId);
            if (invOpt.isEmpty()) {
                errors.add("Invoice not found with ID: " + invId);
                continue;
            }

            Invoice inv = invOpt.get();
            int currentPaid = inv.getPaidAmount() != null ? inv.getPaidAmount() : 0;
            int pending = inv.getAmount() - currentPaid;

            if (pending <= 0) {
                errors.add("Invoice " + inv.getInvoiceNumber() + " has zero pending balance");
                continue;
            }

            LocalDate dueDate = parseDate(inv.getDueDate());
            long daysOffset = dueDate != null ? ChronoUnit.DAYS.between(dueDate, LocalDate.now()) : 0;
            String category = determineReminderType(daysOffset);

            String propName = "RMS Living";
            if (inv.getPropertyId() != null) {
                Property p = propertyRepository.findById(inv.getPropertyId()).orElse(null);
                if (p != null) propName = p.getName();
            }

            String paymentLink = frontendBaseUrl + "/pay/" + inv.getInvoiceNumber();
            String msgBody = (dto.getCustomMessage() != null && !dto.getCustomMessage().isBlank())
                    ? dto.getCustomMessage().trim()
                    : buildWhatsAppMessage(inv.getTenantName(), inv.getRoomNo(), inv.getMonthYear(),
                    pending, inv.getDueDate(), propName, paymentLink, category);

            Tenant tenant = inv.getTenantUid() != null ? tenantRepository.findById(inv.getTenantUid()).orElse(null) : null;
            String phone = tenant != null ? tenant.getMobileNumber() : "";

            ReminderLog logEntity = ReminderLog.builder()
                    .invoiceId(inv.getId())
                    .invoiceNumber(inv.getInvoiceNumber())
                    .tenantUid(inv.getTenantUid())
                    .tenantName(inv.getTenantName())
                    .mobileNumber(phone)
                    .propertyId(inv.getPropertyId())
                    .channel(channel)
                    .reminderType(category)
                    .daysOffset((int) daysOffset)
                    .amountDue(pending)
                    .messageBody(msgBody)
                    .paymentLink(paymentLink)
                    .status("SENT")
                    .sentAt(LocalDateTime.now())
                    .build();

            reminderLogRepository.save(logEntity);
            dispatched.add(inv.getInvoiceNumber());
            success++;
        }

        log.info("Dispatched {}/{} rent reminders via channel {}", success, invoiceIds.size(), channel);

        return SendReminderResultDto.builder()
                .totalRequested(invoiceIds.size())
                .successCount(success)
                .failedCount(errors.size())
                .channel(channel)
                .dispatchedInvoiceNumbers(dispatched)
                .errors(errors)
                .build();
    }

    /**
     * Get collection KPI statistics.
     */
    @Transactional(readOnly = true)
    public ReminderStatsDto getReminderStats(Long propertyId) {
        List<Invoice> invoices = propertyId != null
                ? invoiceRepository.findByPropertyId(propertyId)
                : invoiceRepository.findAll();

        LocalDate today = LocalDate.now();
        long pendingCount = 0;
        long pendingAmt = 0;
        long dueToday = 0;
        long upcoming = 0;
        long overdueMild = 0;
        long overdueCritical = 0;

        for (Invoice inv : invoices) {
            int currentPaid = inv.getPaidAmount() != null ? inv.getPaidAmount() : 0;
            int pending = inv.getAmount() - currentPaid;

            if (inv.getStatus() == InvoiceStatus.PAID || pending <= 0) {
                continue;
            }

            pendingCount++;
            pendingAmt += pending;

            LocalDate dueDate = parseDate(inv.getDueDate());
            long daysOffset = dueDate != null ? ChronoUnit.DAYS.between(dueDate, today) : 0;

            if (daysOffset == 0) {
                dueToday++;
            } else if (daysOffset < 0 && daysOffset >= -3) {
                upcoming++;
            } else if (daysOffset > 0 && daysOffset <= 15) {
                overdueMild++;
            } else if (daysOffset > 15) {
                overdueCritical++;
            }
        }

        LocalDateTime startOfDay = today.atStartOfDay();
        long sentToday = propertyId != null
                ? reminderLogRepository.countByPropertyIdAndSentAtAfter(propertyId, startOfDay)
                : reminderLogRepository.countBySentAtAfter(startOfDay);

        long totalLogged = reminderLogRepository.count();

        return ReminderStatsDto.builder()
                .totalPendingInvoices(pendingCount)
                .totalPendingAmount(pendingAmt)
                .dueTodayCount(dueToday)
                .upcomingCount(upcoming)
                .overdueMildCount(overdueMild)
                .overdueCriticalCount(overdueCritical)
                .remindersSentToday(sentToday)
                .totalRemindersLogged(totalLogged)
                .build();
    }

    /**
     * Get reminder history logs.
     */
    @Transactional(readOnly = true)
    public List<ReminderLog> getReminderLogs(Long propertyId, String tenantUid, Long invoiceId) {
        if (invoiceId != null) {
            return reminderLogRepository.findByInvoiceIdOrderBySentAtDesc(invoiceId);
        }
        if (tenantUid != null && !tenantUid.isBlank()) {
            return reminderLogRepository.findByTenantUidOrderBySentAtDesc(tenantUid.trim());
        }
        if (propertyId != null) {
            return reminderLogRepository.findByPropertyIdOrderBySentAtDesc(propertyId);
        }
        return reminderLogRepository.findAll();
    }

    private String determineReminderType(long daysOffset) {
        if (daysOffset < 0) {
            return "UPCOMING_DUE";
        } else if (daysOffset == 0) {
            return "DUE_TODAY";
        } else if (daysOffset <= 15) {
            return "OVERDUE_MILD";
        } else if (daysOffset <= 30) {
            return "OVERDUE_URGENT";
        } else {
            return "FINAL_NOTICE";
        }
    }

    private String determineUrgency(String category) {
        return switch (category) {
            case "UPCOMING_DUE" -> "LOW";
            case "DUE_TODAY" -> "NORMAL";
            case "OVERDUE_MILD" -> "HIGH";
            case "OVERDUE_URGENT", "FINAL_NOTICE" -> "CRITICAL";
            default -> "NORMAL";
        };
    }

    private String buildWhatsAppMessage(String tenantName, String roomNo, String monthYear,
                                        int pendingAmount, String dueDate, String propName,
                                        String paymentLink, String category) {
        StringBuilder sb = new StringBuilder();
        switch (category) {
            case "UPCOMING_DUE" -> {
                sb.append("🏨 *").append(propName).append(" - Rent Notice*\n");
                sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n");
                sb.append("Hello *").append(tenantName).append("*,\n\n");
                sb.append("This is an advance notice that your monthly rent for *Room ").append(roomNo).append("* (").append(monthYear).append(") is due on *").append(dueDate).append("*.\n\n");
                sb.append("💵 *Amount Payable:* ₹").append(String.format("%,d", pendingAmount)).append("\n\n");
                sb.append("You can pay conveniently online via UPI or Cards with zero convenience fee:\n");
                sb.append("👉 ").append(paymentLink).append("\n\n");
                sb.append("Thank you for your cooperation!");
            }
            case "DUE_TODAY" -> {
                sb.append("🔔 *RENT PAYMENT DUE TODAY*\n");
                sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n");
                sb.append("Dear *").append(tenantName).append("* (Room ").append(roomNo).append("),\n\n");
                sb.append("Your rent payment of *₹").append(String.format("%,d", pendingAmount)).append("* for ").append(monthYear).append(" is *due today* (").append(dueDate).append(").\n\n");
                sb.append("⚡ Tap below to clear with instant UPI verification:\n");
                sb.append("👉 ").append(paymentLink).append("\n\n");
                sb.append("Please ignore if already paid.");
            }
            case "OVERDUE_MILD" -> {
                sb.append("⚠️ *RENT OVERDUE REMINDER*\n");
                sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n");
                sb.append("Dear *").append(tenantName).append("*,\n\n");
                sb.append("Your rent payment of *₹").append(String.format("%,d", pendingAmount)).append("* for Room ").append(roomNo).append(" (").append(monthYear).append(") was due on *").append(dueDate).append("* and remains pending.\n\n");
                sb.append("Kindly clear the balance promptly to avoid late fees:\n");
                sb.append("👉 ").append(paymentLink).append("\n\n");
                sb.append("Regards,\n*").append(propName).append(" Management*");
            }
            case "OVERDUE_URGENT" -> {
                sb.append("🚨 *URGENT: OUTSTANDING RENT BALANCE*\n");
                sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n");
                sb.append("Dear *").append(tenantName).append("*,\n\n");
                sb.append("Your rent of *₹").append(String.format("%,d", pendingAmount)).append("* for Room ").append(roomNo).append(" is significantly overdue since *").append(dueDate).append("*.\n\n");
                sb.append("Please pay immediately using this direct link to avoid room audit escalation:\n");
                sb.append("👉 ").append(paymentLink).append("\n\n");
                sb.append("Contact property desk if you have any questions.");
            }
            case "FINAL_NOTICE" -> {
                sb.append("🛑 *FINAL NOTICE: DELINQUENT RENT ACCOUNT*\n");
                sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n");
                sb.append("Attention: *").append(tenantName).append("* (Room ").append(roomNo).append(")\n\n");
                sb.append("Your account has an unsettled overdue balance of *₹").append(String.format("%,d", pendingAmount)).append("*.\n");
                sb.append("Immediate clearance is mandatory to avoid suspension of hostel amenities and notice generation.\n\n");
                sb.append("Pay securely now:\n");
                sb.append("👉 ").append(paymentLink).append("\n");
            }
            default -> {
                sb.append("Hello ").append(tenantName).append(", your rent of ₹").append(pendingAmount).append(" is pending: ").append(paymentLink);
            }
        }
        return sb.toString();
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            try {
                return LocalDate.parse(dateStr.trim(), DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            } catch (Exception e2) {
                return null;
            }
        }
    }
}
