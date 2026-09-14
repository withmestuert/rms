package com.rms.backend.config;

import com.rms.backend.admissions.repository.AdmissionRepository;
import com.rms.backend.billing.repository.InvoiceRepository;
import com.rms.backend.billing.repository.LedgerTransactionRepository;
import com.rms.backend.properties.repository.PropertyRepository;
import com.rms.backend.rooms.repository.RoomRepository;
import com.rms.backend.tenants.repository.TenantRepository;
import com.rms.backend.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class SystemCleanupController {

    private final InvoiceRepository invoiceRepository;
    private final LedgerTransactionRepository ledgerRepository;
    private final AdmissionRepository admissionRepository;
    private final TenantRepository tenantRepository;
    private final RoomRepository roomRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;

    @PostMapping("/clean-database")
    @Transactional
    public ResponseEntity<Map<String, Object>> cleanDatabase() {
        log.warn("Wiping all tables for clean manual test flow...");

        invoiceRepository.deleteAll();
        ledgerRepository.deleteAll();
        admissionRepository.deleteAll();
        tenantRepository.deleteAll();
        roomRepository.deleteAll();
        propertyRepository.deleteAll();
        userRepository.deleteAll();

        log.info("Database successfully cleared of all records.");
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Database wiped completely clean. Ready for manual testing."
        ));
    }
}
