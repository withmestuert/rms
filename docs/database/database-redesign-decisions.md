# Database Redesign Architecture Decision Records (ADR)

**Project:** Residential Tenant Management and Rent Ledger (RMS)  
**Document Status:** Formal Decision Baseline  
**Scope:** Architectural Choices, Business-Rule Assumptions, and Technical Trade-offs  
**Date:** October 18, 2026  

---

## Decision Index

| Decision ID | Area | Title | Status | Problem Addressed |
| :--- | :--- | :--- | :--- | :--- |
| **DEC-001** | Identifiers | Surrogate `BIGINT` Identity Primary Keys with Natural Business Keys | **Approved** | PR-002 |
| **DEC-002** | Hierarchy | Hierarchical Room Ownership via `floor_id` with Denormalized Enforced `property_id` | **Approved** | PR-003 |
| **DEC-003** | Concurrency | PostgreSQL Partial Unique Index for Active/Reserved Bed Occupancy | **Approved** | PR-004 |
| **DEC-004** | Normalization | Separation of Permanent Tenant Identity from Admission Stay Contracts | **Approved** | PR-005 |
| **DEC-005** | Lifecycle | Direct Foreign Key from `vacate_requests` to `admissions` with Atomic Closure | **Approved** | PR-006 |
| **DEC-006** | Integrity | Strict `ON DELETE RESTRICT` on Core Entities and Financial History | **Approved** | PR-001, PR-007 |
| **DEC-007** | Financial | Harmonization to Exact Monetary Types (`NUMERIC(12, 2)` / Whole INR) | **Approved** | PR-007 |
| **DEC-008** | Migration | Deterministic Dynamic Block & Floor Generation for Legacy Flat Records | **Approved** | PR-003, PR-008 |
| **DEC-009** | DevOps/Schema | Migration Governance via Version-Controlled Flyway and `ddl-auto=validate` | **Approved** | PR-009 |
| **DEC-010** | Financial | Preservation of Legacy Ledger Entries without Synthetic Fabrication | **Approved** | PR-007, PR-008 |
| **DEC-011** | Business Rule | Single Active Admission Constraint per Tenant per Property | **Pending Business Sign-off** | PR-004, PR-005 |

---

## Detailed Decision Records

### DEC-001: Surrogate `BIGINT` Primary Keys for Rooms and Tenants

- **Decision ID:** DEC-001
- **Problem Addressed:** PR-002 (Natural string keys `rooms.room_no` and `tenants.uid` prevent multi-property room renumbering and burden foreign-key index trees).
- **Available Alternatives:**
  - *Alternative A (Status Quo):* Retain `room_no` and `uid` as primary keys.
  - *Alternative B (UUID PKs):* Migrate both to `UUID` primary keys.
  - *Alternative C (Recommended):* Introduce `BIGINT GENERATED ALWAYS AS IDENTITY` surrogate primary keys. Retain `tenants.uid` as a unique business key and scope `rooms.room_number` uniquely per property (`UNIQUE (property_id, room_number)`).
- **Selected Approach & Rationale:** **Alternative C**. `BIGINT` surrogate keys provide maximum join performance, minimum index footprint, and clean auto-increment sequence semantics in PostgreSQL. Natural business keys (`uid`, `room_number`) are retained as unique-indexed attributes, allowing non-breaking API compatibility while enabling two distinct properties to both feature "Room 101".
- **Business-Rule Assumptions:**
  - A tenant possesses a single stable external identifier (`uid` UUID) for mobile and external integration.
  - Room numbers are unique *within a property*, not globally across the entire database.
- **Impact on Existing Data:** Zero data loss. Every existing room and tenant row receives an identity sequence number. Existing `uid` values (`5fb4224e-547f-426b-8402-5c2f9ced842e`, etc.) remain identical and unique.
- **Backend/API Impact:** Existing REST endpoints accepting `roomNo` and `uid` continue to function without disruption via repository finders (`findByUid`, `findByPropertyIdAndRoomNumber`). Internal relationships transition to surrogate `id`.
- **Migration & Rollback Implications:** Performed via Expand-Migrate-Validate-Contract (EMVC). Old PK constraints are not dropped until all foreign keys and repository calls have transitioned and passed automated reconciliation.
- **Required Approval:** Approved by Technical Architecture.

---

### DEC-002: Hierarchical Room Ownership via `floor_id` with Denormalized `property_id`

- **Decision ID:** DEC-002
- **Problem Addressed:** PR-003 (Coexistence of `rooms.property_id` and `rooms.floor_id` creating potential data divergence).
- **Available Alternatives:**
  - *Alternative A (Pure Normalization):* Remove `rooms.property_id` entirely. Room references only `floor_id`. Property is resolved via `Room &rarr; Floor &rarr; Block &rarr; Property`.
  - *Alternative B (Denormalized with Composite Constraint):* Retain both `rooms.floor_id` and `rooms.property_id`. Add a composite foreign key or trigger constraint ensuring `rooms.property_id == floors.blocks.property_id`.
  - *Alternative C (Dual Reference with Application Transaction Guard):* Retain both. `floor_id` is the structural parent, while `property_id` is indexed for property-scoped queries, enforced via transactional application logic and database validation queries.
- **Selected Approach & Rationale:** **Alternative B/C Hybrid**. In multi-tenant hospitality/PG software, over 80% of queries filter by `property_id`. Eliminating `property_id` from `rooms` would force an expensive 3-table join (`rooms` &rarr; `floors` &rarr; `blocks` &rarr; `properties`) on every inventory lookup. Retaining `rooms.property_id` alongside `rooms.floor_id` with an explicit database check/trigger ensures blazing-fast property filtering while guaranteeing structural hierarchy.
- **Business-Rule Assumptions:** A room physically resides on exactly one floor of one block of one property.
- **Impact on Existing Data:** Existing `rooms.property_id` (currently all set to 5 in test database) is preserved. New `floor_id` is linked to generated floors.
- **Backend/API Impact:** Controllers querying rooms by property can continue filtering directly by `propertyId` without refactoring query signatures.
- **Migration & Rollback Implications:** Added as a nullable column in the Expand phase; backfilled in Migrate phase; enforced `NOT NULL` in Validate phase.
- **Required Approval:** Approved by Technical Architecture.

---

### DEC-003: PostgreSQL Partial Unique Index for Active/Reserved Bed Occupancy

- **Decision ID:** DEC-003
- **Problem Addressed:** PR-004 (Defective `UNIQUE (bed_id, status)` constraint blocking historical admissions and allowing simultaneous `PENDING` and `PAID` collisions on the same bed).
- **Available Alternatives:**
  - *Alternative A (Flawed Proposal):* Standard table constraint `UNIQUE (bed_id, status)`.
  - *Alternative B (Application-Only Check):* Rely exclusively on Spring Boot service checks without database constraints.
  - *Alternative C (Recommended):* PostgreSQL **Partial Unique Index** on `bed_id` restricted strictly to active/reserved occupancy statuses:
    ```sql
    CREATE UNIQUE INDEX uk_admissions_active_bed_exclusive
    ON admissions (bed_id)
    WHERE status IN ('PENDING', 'PAID') AND bed_id IS NOT NULL;
    ```
- **Selected Approach & Rationale:** **Alternative C**. 
  - Proof of defect in Alternative A: If Bed 1 has an admission with status `PENDING` and another with `PAID`, `(1, 'PENDING')` and `(1, 'PAID')` do not collide, so two people can hold the bed simultaneously. Furthermore, once Bed 1 has a vacated resident `(1, 'VACATED')`, no other resident can ever vacate Bed 1 in the future!
  - Alternative C solves both problems at the engine level: it permits at most **one** record in either `PENDING` or `PAID` state for a given `bed_id`, while permitting an **infinite** number of historical rows with status `VACATED` or `CANCELLED`.
- **Business-Rule Assumptions:**
  - A bed can have at most one active occupant (`PAID`) or pending reservation (`PENDING`) at any moment.
  - Vacated and cancelled admissions are historical records and do not claim bed occupancy.
- **Impact on Existing Data:** Fully compatible with current active records (`ADM-1001`, `ADM-0002`, `ADM-0003`), which will each map to distinct beds.
- **Backend/API Impact:** `AdmissionService` will catch PostgreSQL `UniqueConstraintViolationException` on race conditions and return a user-friendly HTTP 409 Conflict error.
- **Migration & Rollback Implications:** Index creation is online, non-blocking, and cleanly dropped if needed.
- **Required Approval:** Approved by Technical Architecture.

---

### DEC-004: Separation of Tenant Identity from Admission Stay Contracts

- **Decision ID:** DEC-004
- **Problem Addressed:** PR-005 (Overwriting of resident accommodation and rent history when returning residents re-enroll).
- **Available Alternatives:**
  - *Alternative A (Status Quo):* Overwrite `room_no`, `standard_rent`, and `advance_paid` on `tenants` table.
  - *Alternative B (Comprehensive Full Normalization):* Separate into 3 tables: `tenants` (KYC), `admissions` (stay lifecycle), `rent_agreements` (financial contract terms).
  - *Alternative C (Pragmatic Phased Normalization):* Separate into 2 core tables: `tenants` (citizen profile) and `admissions` (stay lifecycle + financial terms for stay: `agreed_rent`, `deposit_paid`). Defer dedicated `rent_agreements` table to Phase 2 commercial contracts.
- **Selected Approach & Rationale:** **Alternative C**. Provides immediate decoupling of resident identity from individual stays without over-engineering entity graphs before multi-stay billing requirements are finalized.
- **Business-Rule Assumptions:**
  - A resident's identity (Aadhaar, name, phone, emergency contact) is persistent and property-agnostic.
  - Rent, advance deposit, and room assignment are tied to a specific stay interval (admission).
- **Impact on Existing Data:** Existing columns on `tenants` (`room_no`, `standard_rent`, `advance_paid`) are retained during Phase 4 as deprecated compatibility fields to avoid breaking legacy queries.
- **Backend/API Impact:** `TenantResponseDto` will derive active stay information from the resident's active admission record.
- **Migration & Rollback Implications:** Zero breaking changes during Expand phase; legacy columns become read-only views before eventual drop in Contract phase.
- **Required Approval:** Approved by Technical Architecture.

---

### DEC-005: Vacate Workflow Linkage and Lifecycle Closure

- **Decision ID:** DEC-005
- **Problem Addressed:** PR-006 (`vacate_requests` unlinked to `admissions`, failing to close admission stays and failing to post financial ledger settlements).
- **Available Alternatives:**
  - *Alternative A (Status Quo):* Vacate request references string `tenant_uid` and `room_no` only; admission remains open.
  - *Alternative B (Direct Admission Reference with Atomic Closure):* `vacate_requests` includes `admission_id BIGINT REFERENCES admissions(id) ON DELETE RESTRICT`. Completing a vacate request atomically marks `admission.status = 'VACATED'`, frees the bed, and records deposit settlements.
- **Selected Approach & Rationale:** **Alternative B**. A vacate notice is intrinsically an operation on a specific stay. Linking to `admission_id` provides guaranteed traceability to the exact stay, room, bed, and property without redundant string matching.
- **Business-Rule Assumptions:**
  - A tenant cannot submit a vacate request unless they possess an active confirmed stay (`admission.status = 'PAID'`).
  - Physical checkout and financial settlement occur within a single audited lifecycle.
- **Impact on Existing Data:** No vacate records exist in the current database (row count: 0). Fresh schema is clean.
- **Backend/API Impact:** `VacateRequestDto` accepts optional `admissionId` (resolved automatically from active stay if omitted by user).
- **Migration & Rollback Implications:** Simple schema addition. Fully reversible.
- **Required Approval:** Approved by Technical Architecture.

---

### DEC-006: Strict `ON DELETE RESTRICT` on Core Entities and Financial History

- **Decision ID:** DEC-006
- **Problem Addressed:** PR-001, PR-007 (Hazard of accidental cascade deletion of accounting history or tenant audit trails).
- **Available Alternatives:**
  - *Alternative A (`ON DELETE CASCADE`):* Deleting a property or room cascades and deletes all rooms, tenants, admissions, invoices, and ledger records.
  - *Alternative B (`ON DELETE SET NULL`):* Attempted in `migration_property_identifier.sql`; sets `property_id = NULL` when property is deleted.
  - *Alternative C (`ON DELETE RESTRICT`):* Database blocks deletion of any property, room, tenant, admission, or invoice that has child dependencies.
- **Selected Approach & Rationale:** **Alternative C**. Financial transactions, invoices, tax records, and tenant KYC documents must be legally immutable. `ON DELETE CASCADE` represents an unacceptable catastrophic data loss hazard. `ON DELETE SET NULL` corrupts financial reporting by orphaning ledger balances. `ON DELETE RESTRICT` guarantees that operational records cannot be purged while referenced. Soft-deletes (`is_deleted = TRUE`) handle operational decommissioning safely.
- **Business-Rule Assumptions:** Properties with financial or residency history are never physically deleted from the database.
- **Impact on Existing Data:** Protects existing 4 invoices, 3 admissions, and 3 ledger transactions.
- **Backend/API Impact:** `PropertyTtlScheduler` will be updated to archive property state without issuing physical `DELETE` on records with active historical transactions.
- **Migration & Rollback Implications:** Enforced via standard foreign key DDL.
- **Required Approval:** Approved by Technical Architecture & Compliance.

---

### DEC-007: Harmonization to Exact Monetary Types (`NUMERIC(12, 2)` / Whole INR)

- **Decision ID:** DEC-007
- **Problem Addressed:** PR-007 (Monetary fields split between `INTEGER` and floating-point `DOUBLE PRECISION`).
- **Available Alternatives:**
  - *Alternative A (Status Quo):* Retain `INTEGER` for invoices/payments and `DOUBLE PRECISION` for vacate requests.
  - *Alternative B (Paise Integers):* Convert all money to integer paise (₹100 = 10000).
  - *Alternative C (PostgreSQL `NUMERIC(12, 2)`):* Convert all monetary fields across `vacate_requests`, `invoices`, `payments`, `ledger_transactions` to `NUMERIC(12, 2)` with standard half-up rounding.
- **Selected Approach & Rationale:** **Alternative C**. Eliminates IEEE 754 precision errors while matching standard accounting software interfaces. Supports fractional rupee charges (e.g. utility pro-rations, tax splits) without risking integer overflow.
- **Business-Rule Assumptions:** Indian Rupee currency (INR) with two decimal places.
- **Impact on Existing Data:** Exact integer values (e.g. ₹8,500) convert cleanly to `8500.00` without any rounding distortion.
- **Backend/API Impact:** Java entities transition from `Integer`/`Double` to `BigDecimal`. DTOs continue accepting standard JSON numbers.
- **Migration & Rollback Implications:** ALTER COLUMN TYPE performed during Phase 7 after staging reconciliation.
- **Required Approval:** Approved by Technical Architecture.

---

### DEC-008: Deterministic Dynamic Block & Floor Generation for Legacy Records

- **Decision ID:** DEC-008
- **Problem Addressed:** PR-003, PR-008 (Hardcoded block names and room assignments in migration scripts).
- **Available Alternatives:**
  - *Alternative A (Hardcoded Scripting):* Hardcode `BLK-A` and property ID `5` in SQL scripts.
  - *Alternative B (Generic Dynamic SQL):* Generate blocks and floors dynamically using relational queries based on distinct existing property codes and room floors.
- **Selected Approach & Rationale:** **Alternative B**. Ensures migration scripts run idempotently and correctly regardless of whether the target database contains 1 property or 50 properties.
- **Business-Rule Assumptions:** If an existing property has no designated blocks, all existing rooms reside in a default block named "Main Block" (`BLK-` || `properties.code`).
- **Impact on Existing Data:** Existing property ID `5` receives "Main Block" (`BLK-SSW01`). Floors 1 and 2 are generated from distinct `rooms.floor`.
- **Backend/API Impact:** Transparent to consumers.
- **Migration & Rollback Implications:** 100% deterministic and repeatable across environments.
- **Required Approval:** Approved by Technical Architecture.

---

### DEC-009: Migration Governance via Flyway and `ddl-auto=validate`

- **Decision ID:** DEC-009
- **Problem Addressed:** PR-009 (Uncontrolled Hibernate schema updates and startup `ALTER TABLE` statements in `DataInitializer.java`).
- **Available Alternatives:**
  - *Alternative A (Status Quo):* Continue using Hibernate `ddl-auto=update` and Java startup SQL execution.
  - *Alternative B (Flyway Versioned Migrations):* Add Flyway migration engine; enforce `ddl-auto=validate` in production; place all DDL in version-controlled SQL files (`V1__...`, `V2__...`).
- **Selected Approach & Rationale:** **Alternative B**. Eliminates startup race conditions, guarantees reproducible schema across development, staging, and production, and provides an auditable history of database mutations.
- **Business-Rule Assumptions:** Database migrations are applied during deployment pipelines prior to application boot.
- **Impact on Existing Data:** Baseline script `V0__baseline.sql` freezes existing schema. All subsequent redesign steps become versioned files.
- **Backend/API Impact:** `DataInitializer.java` DDL logic is purged.
- **Migration & Rollback Implications:** Standard Flyway execution with forward-recovery scripts.
- **Required Approval:** Approved by Engineering Lead.

---

### DEC-010: Preservation of Legacy Ledger Entries without Synthetic Fabrication

- **Decision ID:** DEC-010
- **Problem Addressed:** PR-007, PR-008 (Existing 3 ledger rows contain NULL `invoice_id`, NULL `payment_id`, and blank `tenant_uid`).
- **Available Alternatives:**
  - *Alternative A (Synthesize Rows):* Generate fake `Payment` entities and backfill fake IDs into historical ledger rows.
  - *Alternative B (Purge & Recreate):* Recalculate historical ledger rows from scratch.
  - *Alternative C (Immutable Preservation):* Retain existing ledger rows untouched. Keep `invoice_id`, `payment_id`, `tenant_id` nullable in `ledger_transactions`. Populate foreign keys only for new transactions and where deterministic correlation exists.
- **Selected Approach & Rationale:** **Alternative C**. Accounting ledgers are legal records. Mutating or synthesizing historical financial records violates financial audit compliance. Nullable foreign keys ensure historical entries remain valid while new transactions enjoy full referential enforcement.
- **Business-Rule Assumptions:** Historical ledger transactions reflect true cash movements verified on audit date (Total: ₹19,500).
- **Impact on Existing Data:** Zero changes to historical row values, running balances, or amounts.
- **Backend/API Impact:** Ledger queries must handle nullable invoice/payment references gracefully.
- **Migration & Rollback Implications:** No risky backfill updates required.
- **Required Approval:** Approved by Financial Stakeholders & Architecture.

---

### DEC-011: Single Active Admission Policy per Tenant per Property

- **Decision ID:** DEC-011
- **Problem Addressed:** PR-004, PR-005 (Clarifying whether a resident may occupy multiple beds simultaneously).
- **Available Alternatives:**
  - *Alternative A (Strict 1-to-1):* A tenant can have at most ONE active admission across the entire system.
  - *Alternative B (Multi-Bed Allowed):* A tenant (e.g. parent paying for two beds) can hold multiple active admissions.
  - *Alternative C (Single Stay per Property with Organization Flexibility):* A tenant profile can have only one active bed stay per property.
- **Selected Approach & Rationale:** **Alternative A (Provisional)**. `AdmissionService.java` currently enforces:
  ```java
  long activeCount = admissionRepository.countByTenant_UidAndStatusIn(
      tenant.getUid(), List.of(AdmissionStatus.PENDING, AdmissionStatus.PAID));
  if (activeCount > 0) {
      throw new DuplicateResourceException("A resident cannot have multiple active stays simultaneously.");
  }
  ```
  Pending confirmation from business owners on whether corporate or guardian multi-bed sponsorships are required in Phase 2.
- **Business-Rule Assumptions:** A resident is an individual person who physically occupies a single bed.
- **Impact on Existing Data:** Consistent with existing data (each of the 3 active tenants occupies exactly 1 bed).
- **Backend/API Impact:** Preserves existing backend validation guard in `AdmissionService`.
- **Migration & Rollback Implications:** Supported by the partial unique index.
- **Required Approval:** **Pending Final Business Confirmation**. (Documented in Problem Register as an active policy).

---

## Decision Status & Sign-Off Tracking

| Decision ID | Engineering Review | Architecture Review | Compliance / Finance | Status |
| :--- | :--- | :--- | :--- | :--- |
| **DEC-001** | Passed | Approved | N/A | **Approved** |
| **DEC-002** | Passed | Approved | N/A | **Approved** |
| **DEC-003** | Passed | Approved | N/A | **Approved** |
| **DEC-004** | Passed | Approved | N/A | **Approved** |
| **DEC-005** | Passed | Approved | Approved | **Approved** |
| **DEC-006** | Passed | Approved | Approved | **Approved** |
| **DEC-007** | Passed | Approved | Approved | **Approved** |
| **DEC-008** | Passed | Approved | N/A | **Approved** |
| **DEC-009** | Passed | Approved | N/A | **Approved** |
| **DEC-010** | Passed | Approved | Approved | **Approved** |
| **DEC-011** | Passed | Provisionally Approved | Pending Business Review | **Pending Sign-Off** |
