package com.rms.backend.billing.service;

import com.rms.backend.billing.dto.CycleGenerationRequestDto;
import com.rms.backend.billing.dto.CycleGenerationResultDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoRentInvoiceSchedulerTest {

    @Mock
    private BillingService billingService;

    @InjectMocks
    private AutoRentInvoiceScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        ReflectionTestUtils.setField(scheduler, "dueDay", 5);
    }

    @Test
    void testRunMonthlyAutoInvoiceGeneration_WhenEnabled() {
        CycleGenerationResultDto mockResult = CycleGenerationResultDto.builder()
                .generatedCount(5)
                .skippedCount(2)
                .totalAmount(40000L)
                .generatedInvoices(Collections.emptyList())
                .build();

        when(billingService.generateCycleInvoices(any(CycleGenerationRequestDto.class))).thenReturn(mockResult);

        scheduler.runMonthlyAutoInvoiceGeneration();

        ArgumentCaptor<CycleGenerationRequestDto> captor = ArgumentCaptor.forClass(CycleGenerationRequestDto.class);
        verify(billingService, times(1)).generateCycleInvoices(captor.capture());

        CycleGenerationRequestDto captured = captor.getValue();
        assertNotNull(captured.getMonthYear());
        assertNotNull(captured.getDueDate());
        assertTrue(captured.getDueDate().endsWith("-05"));

        Map<String, Object> status = scheduler.getSchedulerStatus();
        assertEquals(true, status.get("enabled"));
        assertEquals("SUCCESS", status.get("lastStatus"));
        assertEquals(5, status.get("lastGeneratedCount"));
        assertEquals(2, status.get("lastSkippedCount"));
        assertEquals(40000L, status.get("lastTotalAmount"));
    }

    @Test
    void testRunMonthlyAutoInvoiceGeneration_WhenDisabled() {
        ReflectionTestUtils.setField(scheduler, "enabled", false);

        scheduler.runMonthlyAutoInvoiceGeneration();

        verify(billingService, never()).generateCycleInvoices(any(CycleGenerationRequestDto.class));
    }

    @Test
    void testExecuteAutoCycle_CustomDate() {
        LocalDate customDate = LocalDate.of(2026, 11, 1);
        CycleGenerationResultDto mockResult = CycleGenerationResultDto.builder()
                .generatedCount(3)
                .skippedCount(0)
                .totalAmount(24000L)
                .generatedInvoices(Collections.emptyList())
                .build();

        when(billingService.generateCycleInvoices(any(CycleGenerationRequestDto.class))).thenReturn(mockResult);

        CycleGenerationResultDto result = scheduler.executeAutoCycle(customDate, 10, 1L);

        ArgumentCaptor<CycleGenerationRequestDto> captor = ArgumentCaptor.forClass(CycleGenerationRequestDto.class);
        verify(billingService).generateCycleInvoices(captor.capture());

        CycleGenerationRequestDto captured = captor.getValue();
        assertEquals("November 2026", captured.getMonthYear());
        assertEquals("2026-11-10", captured.getDueDate());
        assertEquals(1L, captured.getPropertyId());
        assertEquals(3, result.getGeneratedCount());
    }
}
