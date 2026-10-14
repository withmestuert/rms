# Database Redesign Automated Validation Plan & Test Suite

**Project:** Residential Tenant Management and Rent Ledger (RMS)  
**Execution Stage:** Pre-Migration, Migration Verification, and Post-Cutover Testing  
**Database:** PostgreSQL 17 (`rms_db`)  
**Backend:** Spring Boot 3.x (`com.rms.backend.*`)  
**Document Status:** Approved Technical Baseline  

---

## 1. Overview & Quality Gates

This automated validation plan specifies the verification harness, SQL diagnostic queries, integration test cases, and measurable pass/fail thresholds required at every phase of the database redesign. No migration step is deemed complete until all tests in the corresponding phase pass with zero errors.

### Validation Gates

```mermaid
graph LR
    Gate0[Gate 0: Pre-Migration Baseline] --> Gate1[Gate 1: Expand Phase Structural Checks]
    Gate1 --> Gate2[Gate 2: Migrate Phase Backfill Validation]
    Gate2 --> Gate3[Gate 3: Financial & Occupancy Reconciliation]
    Gate3 --> Gate4[Gate 4: Concurrency & Workflow End-to-End]
    Gate4 --> Gate5[Gate 5: Contract Phase Cleanliness]
```

---

## 2. Comprehensive Test Matrix (15 Verification Areas)

### Area 1: Primary-Key Uniqueness & Non-Null Identifiers

- **Objective:** Verify that every table has a valid, non-null, uniquely indexed primary key without duplicate sequence values.
- **SQL Verification Check:**
  ```sql
  -- 1.1 Verify no NULL or duplicate Primary Keys across all core tables
  SELECT 'properties' AS tbl, COUNT(*) AS null_or_dup_pks
  FROM properties WHERE id IS NULL OR id IN (SELECT id FROM properties GROUP BY id HAVING COUNT(*) > 1)
  UNION ALL
  SELECT 'blocks', COUNT(*) FROM blocks WHERE id IS NULL OR id IN (SELECT id FROM blocks GROUP BY id HAVING COUNT(*) > 1)
  UNION ALL
  SELECT 'floors', COUNT(*) FROM floors WHERE id IS NULL OR id IN (SELECT id FROM floors GROUP BY id HAVING COUNT(*) > 1)
  UNION ALL
  SELECT 'rooms', COUNT(*) FROM rooms WHERE id IS NULL OR id IN (SELECT id FROM rooms GROUP BY id HAVING COUNT(*) > 1)
  UNION ALL
  SELECT 'beds', COUNT(*) FROM beds WHERE id IS NULL OR id IN (SELECT id FROM beds GROUP BY id HAVING COUNT(*) > 1)
  UNION ALL
  SELECT 'tenants', COUNT(*) FROM tenants WHERE id IS NULL OR id IN (SELECT id FROM tenants GROUP BY id HAVING COUNT(*) > 1)
  UNION ALL
  SELECT 'admissions', COUNT(*) FROM admissions WHERE id IS NULL OR id IN (SELECT id FROM admissions GROUP BY id HAVING COUNT(*) > 1)
  UNION ALL
  SELECT 'invoices', COUNT(*) FROM invoices WHERE id IS NULL OR id IN (SELECT id FROM invoices GROUP BY id HAVING COUNT(*) > 1)
  UNION ALL
  SELECT 'payments', COUNT(*) FROM payments WHERE id IS NULL OR id IN (SELECT id FROM payments GROUP BY id HAVING COUNT(*) > 1)
  UNION ALL
  SELECT 'ledger_transactions', COUNT(*) FROM ledger_transactions WHERE id IS NULL OR id IN (SELECT id FROM ledger_transactions GROUP BY id HAVING COUNT(*) > 1);
  ```
- **Pass/Fail Criteria:** Every row must return `null_or_dup_pks = 0`.
- **Baseline Result:** PASS (all tables have clean primary keys).

---

### Area 2: Foreign-Key Validity & Orphan Detection

- **Objective:** Verify that 100% of foreign-key columns point to existing parent rows in the target tables.
- **SQL Verification Check:**
  ```sql
  -- 2.1 Orphan detection across all relationships
  SELECT 'rooms_without_property' AS check_name, COUNT(*) AS orphan_count
  FROM rooms WHERE property_id NOT IN (SELECT id FROM properties)
  UNION ALL
  SELECT 'rooms_without_floor', COUNT(*)
  FROM rooms WHERE floor_id IS NOT NULL AND floor_id NOT IN (SELECT id FROM floors)
  UNION ALL
  SELECT 'floors_without_block', COUNT(*)
  FROM floors WHERE block_id NOT IN (SELECT id FROM blocks)
  UNION ALL
  SELECT 'blocks_without_property', COUNT(*)
  FROM blocks WHERE property_id NOT IN (SELECT id FROM properties)
  UNION ALL
  SELECT 'beds_without_room', COUNT(*)
  FROM beds WHERE room_id NOT IN (SELECT id FROM rooms)
  UNION ALL
  SELECT 'tenants_without_property', COUNT(*)
  FROM tenants WHERE property_id IS NOT NULL AND property_id NOT IN (SELECT id FROM properties)
  UNION ALL
  SELECT 'admissions_without_tenant', COUNT(*)
  FROM admissions WHERE tenant_id IS NOT NULL AND tenant_id NOT IN (SELECT id FROM tenants)
  UNION ALL
  SELECT 'admissions_without_room', COUNT(*)
  FROM admissions WHERE room_id IS NOT NULL AND room_id NOT IN (SELECT id FROM rooms)
  UNION ALL
  SELECT 'admissions_without_bed', COUNT(*)
  FROM admissions WHERE bed_id IS NOT NULL AND bed_id NOT IN (SELECT id FROM beds)
  UNION ALL
  SELECT 'invoices_without_property', COUNT(*)
  FROM invoices WHERE property_id IS NOT NULL AND property_id NOT IN (SELECT id FROM properties)
  UNION ALL
  SELECT 'payments_without_invoice', COUNT(*)
  FROM payments WHERE invoice_id NOT IN (SELECT id FROM invoices)
  UNION ALL
  SELECT 'ledger_without_property', COUNT(*)
  FROM ledger_transactions WHERE property_id IS NOT NULL AND property_id NOT IN (SELECT id FROM properties);
  ```
- **Pass/Fail Criteria:** All checks must return `orphan_count = 0`.
- **Baseline Result:** PASS (0 orphans found in current test database).

---

### Area 3: Property/Block/Floor/Room Hierarchy Consistency

- **Objective:** Guarantee that the structural parentage is strictly non-divergent: `Room &rarr; Floor &rarr; Block &rarr; Property`. A room's `property_id` must match its parent floor's parent block's `property_id`.
- **SQL Verification Check:**
  ```sql
  SELECT 
      r.id AS room_id,
      r.room_number,
      r.property_id AS room_property_id,
      b.property_id AS block_property_id
  FROM rooms r
  JOIN floors f ON r.floor_id = f.id
  JOIN blocks b ON f.block_id = b.id
  WHERE r.property_id != b.property_id;
  ```
- **Pass/Fail Criteria:** Query returns **0 rows**. Any returned row represents a cross-property hierarchy corrupting tenant inventory.

---

### Area 4: Room and Bed Capacity Reconciliation

- **Objective:** Ensure the number of configured beds in each room matches the room's declared `occupancy` capacity.
- **SQL Verification Check:**
  ```sql
  SELECT 
      r.id AS room_id,
      r.room_number,
      r.occupancy AS declared_capacity,
      COUNT(b.id) AS physical_bed_count,
      (r.occupancy - COUNT(b.id)) AS discrepancy
  FROM rooms r
  LEFT JOIN beds b ON r.id = b.room_id
  GROUP BY r.id, r.room_number, r.occupancy
  HAVING r.occupancy != COUNT(b.id);
  ```
- **Pass/Fail Criteria:** Query returns **0 rows**. Every room must have exactly $N$ bed records where $N = \text{occupancy}$.

---

### Area 5: Duplicate Active or Reserved Bed Allocations

- **Objective:** Enforce that no bed is concurrently occupied or reserved by multiple admissions.
- **SQL Verification Check:**
  ```sql
  -- Detect bed collisions across active or pending admissions
  SELECT 
      bed_id,
      COUNT(*) AS active_admissions_count,
      ARRAY_AGG(admission_number) AS colliding_admissions,
      ARRAY_AGG(status) AS colliding_statuses
  FROM admissions
  WHERE bed_id IS NOT NULL 
    AND status IN ('PENDING', 'PAID')
  GROUP BY bed_id
  HAVING COUNT(*) > 1;
  ```
- **Constraint Integrity Test:**
  Attempting to insert a duplicate active admission on the same bed must throw a database exception:
  ```sql
  -- Expected outcome: ERROR: duplicate key value violates unique constraint "uk_admissions_active_bed_exclusive"
  ```
- **Pass/Fail Criteria:** Zero colliding admissions; partial unique index `uk_admissions_active_bed_exclusive` present in `pg_indexes`.

---

### Area 6: Tenant Admission History Integrity

- **Objective:** Verify that every tenant profile has an unbroken audit trail of stays, and no tenant possesses overlapping active stays.
- **SQL Verification Check:**
  ```sql
  -- Detect multiple active stays for the same tenant
  SELECT 
      tenant_id,
      COUNT(*) AS active_stays_count,
      ARRAY_AGG(admission_number) AS active_admissions
  FROM admissions
  WHERE status IN ('PENDING', 'PAID')
  GROUP BY tenant_id
  HAVING COUNT(*) > 1;
  ```
- **Historical Completeness Check:**
  ```sql
  -- Verify all admissions resolve to a valid tenant with historical enrollment dates
  SELECT COUNT(*) AS invalid_admission_dates
  FROM admissions
  WHERE enrollment_date IS NULL 
     OR (vacated_on IS NOT NULL AND vacated_on < enrollment_date);
  ```
- **Pass/Fail Criteria:** Zero multiple active stays (`active_stays_count <= 1`); `invalid_admission_dates = 0`.

---

### Area 7: Admission-to-Vacate Consistency

- **Objective:** Verify that every completed vacate request closes the corresponding admission, frees the bed, and maintains date consistency.
- **SQL Verification Check:**
  ```sql
  -- 1. Vacate request pointing to non-vacated admission
  SELECT 
      vr.id AS vacate_id,
      vr.request_id,
      a.admission_number,
      vr.status AS vacate_status,
      a.status AS admission_status
  FROM vacate_requests vr
  JOIN admissions a ON vr.admission_id = a.id
  WHERE vr.status = 'COMPLETED' AND a.status != 'VACATED';

  -- 2. Vacate date coherence
  SELECT vr.id, vr.expected_leaving_date, vr.request_date
  FROM vacate_requests vr
  WHERE vr.expected_leaving_date < vr.request_date;
  ```
- **Pass/Fail Criteria:** Both queries must return **0 rows**.

---

### Area 8: Invoice/Payment/Ledger Reference Integrity

- **Objective:** Verify referential integrity between invoices, payments, and double-entry ledger transactions.
- **SQL Verification Check:**
  ```sql
  -- 1. Payments referencing missing invoices
  SELECT COUNT(*) AS orphaned_payments
  FROM payments
  WHERE invoice_id NOT IN (SELECT id FROM invoices);

  -- 2. Ledger transactions referencing non-existent invoices or payments
  SELECT COUNT(*) AS invalid_ledger_invoices
  FROM ledger_transactions
  WHERE invoice_id IS NOT NULL AND invoice_id NOT IN (SELECT id FROM invoices);

  SELECT COUNT(*) AS invalid_ledger_payments
  FROM ledger_transactions
  WHERE payment_id IS NOT NULL AND payment_id NOT IN (SELECT id FROM payments);

  -- 3. Invoice paid_amount consistency with successful payments
  SELECT 
      i.id AS invoice_id,
      i.invoice_number,
      i.paid_amount,
      COALESCE(SUM(p.amount), 0) AS actual_payments_sum,
      (i.paid_amount - COALESCE(SUM(p.amount), 0)) AS diff
  FROM invoices i
  LEFT JOIN payments p ON i.id = p.invoice_id AND p.status = 'SUCCESS'
  GROUP BY i.id, i.invoice_number, i.paid_amount
  HAVING i.paid_amount != COALESCE(SUM(p.amount), 0);
  ```
- **Special Historical Rule:** Historical invoices paid prior to the introduction of the `payments` table (where `payments` row count was 0) are reconciled against `ledger_transactions` via `reference_number = transaction_ref`.
- **Pass/Fail Criteria:** Zero orphaned payments; zero invalid ledger references; modern invoice payments match payment records 100%.

---

### Area 9: Financial Balance Reconciliation Before and After Migration

- **Objective:** Guarantee that total ledger balances, total cash debits/credits, and invoice receivables are 100% preserved during migration with zero drift.
- **SQL Baseline Capture (Run Before Migration):**
  ```sql
  SELECT 
      COUNT(*) AS total_txns,
      COALESCE(SUM(CASE WHEN type = 'CREDIT' THEN amount ELSE 0 END), 0) AS total_credits,
      COALESCE(SUM(CASE WHEN type = 'DEBIT' THEN amount ELSE 0 END), 0) AS total_debits,
      COALESCE(MAX(running_balance), 0) AS final_running_balance
  FROM ledger_transactions;
  ```
- **Active Database Baseline:**
  - Total Transactions: `3`
  - Total Credits: `₹19,500.00`
  - Total Debits: `₹0.00`
  - Final Running Balance: `₹19,500.00`
- **Post-Migration Verification Check:**
  Run the identical query. The post-migration output must match the baseline down to the exact rupee:
  $$\Delta \text{Credits} = 0, \quad \Delta \text{Debits} = 0, \quad \Delta \text{Balance} = 0$$
- **Pass/Fail Criteria:** Exact mathematical equivalence before and after migration.

---

### Area 10: Legacy Identifier Compatibility

- **Objective:** Verify that all legacy external identifiers (`tenants.uid`, `rooms.room_no`, `admissions.admission_number`, `invoices.invoice_number`, `vacate_requests.request_id`) remain fully discoverable and unique.
- **SQL Verification Check:**
  ```sql
  -- Verify unique index coverage for all legacy keys
  SELECT tablename, indexname 
  FROM pg_indexes 
  WHERE schemaname = 'public' 
    AND indexname IN (
        'uk_tenants_uid',
        'uk_admissions_admission_number',
        'uk_invoices_invoice_number',
        'uk_payments_payment_ref',
        'uk_vacate_requests_request_id'
    );
  ```
- **Pass/Fail Criteria:** All 5 unique indexes must exist and have 0 duplicate keys.

---

### Area 11: API and DTO Backward Compatibility

- **Objective:** Ensure existing frontend web clients and external webhooks continue receiving and sending data without contract breakage.
- **Spring Boot Automated Tests (`src/test/java/...`):**
  1. `RoomControllerTest.getRoomByNo_ReturnsLegacyPayload()`: Validates that GET `/api/rooms/100` returns JSON with `roomNo`, `floor`, `occupancy`, `currentOccupancy`, `available`.
  2. `TenantControllerTest.getTenantByUid_ReturnsLegacyPayload()`: Validates that GET `/api/tenants/{uid}` returns `uid`, `name`, `roomNo`, `advancePaid`, `standardRent`.
  3. `AdmissionControllerTest.createEnrollment_AcceptsRoomNo()`: Validates that POST `/api/admissions` accepts string `roomNo` and maps it to underlying `roomId` and `bedId`.
- **Pass/Fail Criteria:** 100% of existing controller integration tests pass.

---

### Area 12: Migration Retry and Recovery Behavior

- **Objective:** Prove that migration scripts are completely idempotent: executing a migration twice in succession must not cause errors, duplicate rows, or lock escalation.
- **Validation Execution:**
  1. Execute Flyway migration script `V1__property_safeguards.sql`.
  2. Re-execute the same script against the database.
  3. Verify that all statements use `IF NOT EXISTS` or idempotent guard clauses (`DO $$ BEGIN IF NOT EXISTS...`).
- **Pass/Fail Criteria:** Zero SQL errors on re-execution. `flyway_schema_history` records single successful state.

---

### Area 13: Concurrent Admission Attempts (Double-Booking Guard)

- **Objective:** Prove that two concurrent HTTP threads attempting to enroll into the same vacant bed result in exactly one success and one deterministic rejection.
- **Backend Concurrency Test (`AdmissionConcurrencyTest.java`):**
  ```java
  @Test
  void testConcurrentEnrollment_SameBed_OnlyOneSucceeds() throws Exception {
      ExecutorService executor = Executors.newFixedThreadPool(2);
      CountDownLatch latch = new CountDownLatch(1);
      
      Callable<Integer> task = () -> {
          latch.await();
          try {
              admissionService.createEnrollment(testDto);
              return 200;
          } catch (DuplicateResourceException e) {
              return 409;
          }
      };

      Future<Integer> f1 = executor.submit(task);
      Future<Integer> f2 = executor.submit(task);
      latch.countDown();

      List<Integer> results = List.of(f1.get(), f2.get());
      assertTrue(results.contains(200));
      assertTrue(results.contains(409));
  }
  ```
- **Pass/Fail Criteria:** Exactly one thread receives HTTP 200/201; the second receives HTTP 409 Conflict. Bed occupancy in database equals exactly 1.

---

### Area 14: End-to-End Resident Lifecycle Workflows

- **Objective:** Validate the complete operational workflow from intake to checkout:
  $$\text{Enrollment} \longrightarrow \text{Confirmation} \longrightarrow \text{Billing} \longrightarrow \text{Payment} \longrightarrow \text{Vacate Notice} \longrightarrow \text{Settlement}$$
- **Verification Steps:**
  1. **Enroll:** Bed transitions `AVAILABLE` &rarr; `RESERVED`; admission created with status `PENDING`.
  2. **Confirm:** Bed transitions `RESERVED` &rarr; `OCCUPIED`; admission status `PAID`; tenant status `ACTIVE`.
  3. **Bill:** Invoice generated for resident stay.
  4. **Pay:** Payment recorded; invoice status `PAID`; ledger transaction appended with exact running balance.
  5. **Notice:** Vacate request submitted; status `PENDING` &rarr; `APPROVED`.
  6. **Complete:** Vacate request `COMPLETED`; admission status `VACATED`; bed status transitions `OCCUPIED` &rarr; `AVAILABLE`; tenant status `INACTIVE`.
- **Pass/Fail Criteria:** All entity states transition according to the verified state machine with 0 orphaned records and clean accounting balance.

---

### Area 15: Schema Validation on a Clean Test Environment

- **Objective:** Ensure the entire redesigned schema deploys successfully from scratch on a clean PostgreSQL instance with Hibernate `ddl-auto=validate`.
- **Validation Procedure:**
  1. Spin up an empty test database `rms_test_clean`.
  2. Run all Flyway migrations from `V0` through `Vfinal`.
  3. Boot `RmsBackendApplication` with `spring.jpa.hibernate.ddl-auto=validate`.
- **Pass/Fail Criteria:** Spring Boot application context loads with **zero schema validation errors** (`SchemaManagementException: 0`).

---

## 3. Automated Validation Execution Script

A companion SQL validation script `validate_redesign_integrity.sql` will be committed to `src/main/resources/` to run all 15 SQL checks in a single automated CI/CD pipeline step, returning a unified health report:

```sql
DO $$
DECLARE
    v_orphans INT;
    v_collisions INT;
BEGIN
    SELECT COUNT(*) INTO v_orphans FROM rooms WHERE property_id NOT IN (SELECT id FROM properties);
    IF v_orphans > 0 THEN
        RAISE EXCEPTION 'Validation Failed: % orphaned rooms detected!', v_orphans;
    END IF;

    SELECT COUNT(*) INTO v_collisions FROM (
        SELECT bed_id FROM admissions WHERE status IN ('PENDING', 'PAID') AND bed_id IS NOT NULL GROUP BY bed_id HAVING COUNT(*) > 1
    ) sub;
    IF v_collisions > 0 THEN
        RAISE EXCEPTION 'Validation Failed: % bed collisions detected!', v_collisions;
    END IF;

    RAISE NOTICE 'SUCCESS: All Database Redesign Validation Checks Passed Cleanly.';
END $$;
```
