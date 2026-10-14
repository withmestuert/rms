# Table-by-Table & Entity-Relationship Mapping Matrix

**Project:** Residential Tenant Management and Rent Ledger (RMS)  
**Document Purpose:** Comprehensive migration mapping from As-Is baseline schema to Target redesigned architecture.  
**Guiding Principle:** Non-destructive Expand-Migrate-Validate-Contract (EMVC) transitions ensuring 100% backward compatibility and zero data loss.

---

## 1. Table-by-Table Structural Mapping

| Current Table | Current Column | Current Type & Constraints | Target Table | Target Column | Target Type & Constraints | Migration Transformation & Compatibility Action |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`properties`** | `id` | `bigint` PK | `properties` | `id` | `BIGINT GENERATED ALWAYS AS IDENTITY` PK | Retain exact existing primary keys. Sequence restarted from `MAX(id) + 1`. |
| `properties` | `name` | `varchar(255)` UK | `properties` | `name` | `VARCHAR(255)` NOT NULL UNIQUE | Preserved unchanged. |
| `properties` | `code` | `varchar(255)` UK | `properties` | `code` | `VARCHAR(50)` NOT NULL UNIQUE | Preserved unchanged. |
| `properties` | `is_deleted` | `boolean` DEFAULT false | `properties` | `is_deleted` | `BOOLEAN` NOT NULL DEFAULT false | Preserved (supports protected soft-delete). |
| `properties` | `ttl_expires_at`| `timestamp` | `properties` | `ttl_expires_at`| `TIMESTAMPTZ` | Preserved (30-day recovery window). |
| *New Entity* | — | — | **`blocks`** | `id` | `BIGINT GENERATED ALWAYS AS IDENTITY` PK | Created in Phase 2. Default "Main Block" (`BLK-` \|\| `properties.code`) dynamically backfilled. |
| *New Entity* | — | — | `blocks` | `property_id` | `BIGINT` FK &rarr; `properties(id)` | Enforced with `ON DELETE RESTRICT`. |
| *New Entity* | — | — | `blocks` | `name`, `code` | `VARCHAR` NOT NULL | Composite unique constraint: `UNIQUE (property_id, code)`. |
| *New Entity* | — | — | **`floors`** | `id` | `BIGINT GENERATED ALWAYS AS IDENTITY` PK | Created in Phase 2. Dynamically extracted from distinct `rooms.floor`. |
| *New Entity* | — | — | `floors` | `block_id` | `BIGINT` FK &rarr; `blocks(id)` | Enforced with `ON DELETE RESTRICT`. |
| *New Entity* | — | — | `floors` | `floor_number` | `INT` NOT NULL | Extracted integer: `CAST(rooms.floor AS INT)`. Composite UK: `UNIQUE (block_id, floor_number)`. |
| **`rooms`** | `room_no` | `varchar(255)` PK | `rooms` | `id` | `BIGINT GENERATED ALWAYS AS IDENTITY` PK | **Primary Key Migration:** Natural key migrated to surrogate `BIGINT`. |
| `rooms` | `room_no` | `varchar(255)` | `rooms` | `room_number` | `VARCHAR(50)` NOT NULL | Retained as business room number. Composite UK: `UNIQUE (property_id, room_number)`. |
| `rooms` | `floor` | `varchar(255)` | `rooms` | `floor_id` | `BIGINT` FK &rarr; `floors(id)` | Enforced with `ON DELETE RESTRICT`. Legacy `rooms.floor` retained as compatibility field in Expand phase. |
| `rooms` | `property_id` | `bigint` (no FK) | `rooms` | `property_id` | `BIGINT` FK &rarr; `properties(id)` | Retained for high-performance scoped querying. Enforced with `ON DELETE RESTRICT`. |
| `rooms` | `occupancy` | `integer` | `rooms` | `occupancy` | `INT` NOT NULL | Declared room capacity. Preserved. |
| `rooms` | `current_occupancy`| `integer` | `rooms` | *(derived)* | Dynamically calculated | Derived as `COUNT(beds) WHERE status='OCCUPIED'`. Counter column maintained for legacy API reads. |
| `rooms` | `reserved_capacity`| `integer` | `rooms` | *(derived)* | Dynamically calculated | Derived as `COUNT(admissions) WHERE status='PENDING'`. Counter column maintained for legacy API reads. |
| `rooms` | `vacate_date` | `varchar(255)` | `rooms` | *(deprecated)* | Deprecated | Moved to `admissions(vacated_on)` and `vacate_requests(expected_leaving_date)`. Retained as view. |
| *New Entity* | — | — | **`beds`** | `id` | `BIGINT GENERATED ALWAYS AS IDENTITY` PK | Created in Phase 3. The atomic unit of physical occupancy. |
| *New Entity* | — | — | `beds` | `room_id` | `BIGINT` FK &rarr; `rooms(id)` | Enforced with `ON DELETE CASCADE` (deleting a room deletes its beds). |
| *New Entity* | — | — | `beds` | `bed_number` | `VARCHAR(50)` NOT NULL | Composite unique: `UNIQUE (room_id, bed_number)`. |
| *New Entity* | — | — | `beds` | `status` | `VARCHAR(50)` NOT NULL | States: `AVAILABLE`, `OCCUPIED`, `RESERVED`, `MAINTENANCE`. |
| **`tenants`** | `uid` | `varchar(255)` PK | `tenants` | `id` | `BIGINT GENERATED ALWAYS AS IDENTITY` PK | **Primary Key Migration:** Natural key migrated to surrogate `BIGINT`. |
| `tenants` | `uid` | `varchar(255)` | `tenants` | `uid` | `VARCHAR(100)` NOT NULL UNIQUE | Retained as permanent external integration key. |
| `tenants` | `name` | `varchar(255)` | `tenants` | `name` | `VARCHAR(255)` NOT NULL | Preserved. |
| `tenants` | `aadhaar_no` | `varchar(255)` UK | `tenants` | `aadhaar_no` | `VARCHAR(100)` NOT NULL UNIQUE | Preserved as unique resident citizen identification. |
| `tenants` | `mobile_number` | `varchar(255)` | `tenants` | `mobile_number` | `VARCHAR(50)` NOT NULL | Preserved. |
| `tenants` | `room_no` | `varchar(255)` (no FK) | `tenants` | *(decoupled)* | Deprecated from tenant | **Stay Decoupling:** Moved to `admissions(room_id)`. Column retained as read-only view in Phase 4. |
| `tenants` | `standard_rent` | `integer` | `tenants` | *(decoupled)* | Deprecated from tenant | Moved to `admissions(agreed_rent)`. |
| `tenants` | `advance_paid` | `integer` | `tenants` | *(decoupled)* | Deprecated from tenant | Moved to `admissions(deposit_paid)`. |
| `tenants` | `property_id` | `bigint` (no FK) | `tenants` | `property_id` | `BIGINT` FK &rarr; `properties(id)` | Enforced with `ON DELETE RESTRICT`. |
| **`admissions`**| `id` | `bigint` PK | `admissions` | `id` | `BIGINT GENERATED ALWAYS AS IDENTITY` PK | Identity sequence preserved. |
| `admissions`| `admission_number`| `varchar(255)` UK | `admissions` | `admission_number`| `VARCHAR(50)` NOT NULL UNIQUE | Preserved (e.g. `ADM-1001`). |
| `admissions`| `tenant_uid` | `varchar(255)` FK | `admissions` | `tenant_id` | `BIGINT` FK &rarr; `tenants(id)` | Enforced with `ON DELETE RESTRICT`. `tenant_uid` retained in Expand phase. |
| `admissions`| `room_no` | `varchar(255)` FK | `admissions` | `room_id` | `BIGINT` FK &rarr; `rooms(id)` | Enforced with `ON DELETE RESTRICT`. `room_no` retained in Expand phase. |
| `admissions`| *None* | — | `admissions` | `bed_id` | `BIGINT` FK &rarr; `beds(id)` | **Bed Allocation:** Enforced with `ON DELETE RESTRICT`. |
| `admissions`| `property_id` | `bigint` (no FK) | `admissions` | `property_id` | `BIGINT` FK &rarr; `properties(id)` | Enforced with `ON DELETE RESTRICT`. |
| `admissions`| `status` | `varchar(255)` | `admissions` | `status` | `VARCHAR(50)` NOT NULL | States: `PENDING`, `PAID`, `CANCELLED`, `VACATED`. |
| `admissions`| *Index* | — | `admissions` | *(partial index)*| `INDEX uk_admissions_active_bed` | **DEC-003:** Partial unique index: `WHERE status IN ('PENDING', 'PAID') AND bed_id IS NOT NULL`. |
| **`vacate_requests`**| `id` | `bigint` PK | `vacate_requests` | `id` | `BIGINT GENERATED ALWAYS AS IDENTITY` PK | Preserved. |
| `vacate_requests`| `request_id` | `varchar(255)` UK | `vacate_requests` | `request_id` | `VARCHAR(50)` NOT NULL UNIQUE | Preserved. |
| `vacate_requests`| *None* | — | `vacate_requests` | `admission_id` | `BIGINT` FK &rarr; `admissions(id)` | **Lifecycle Linkage:** Mandatory FK enforced with `ON DELETE RESTRICT`. |
| `vacate_requests`| `tenant_uid` | `varchar(255)` (no FK) | `vacate_requests` | `tenant_id` | `BIGINT` FK &rarr; `tenants(id)` | Enforced with `ON DELETE RESTRICT`. |
| `vacate_requests`| `room_no` | `varchar(255)` (no FK) | `vacate_requests` | `room_id` | `BIGINT` FK &rarr; `rooms(id)` | Enforced with `ON DELETE RESTRICT`. |
| `vacate_requests`| *None* | — | `vacate_requests` | `bed_id` | `BIGINT` FK &rarr; `beds(id)` | Set to `NULL` if bed is reconfigured (`ON DELETE SET NULL`). |
| `vacate_requests`| `property_id` | `bigint` (no FK) | `vacate_requests` | `property_id` | `BIGINT` FK &rarr; `properties(id)` | Enforced with `ON DELETE RESTRICT`. |
| `vacate_requests`| `advance_paid` | `double precision` | `vacate_requests` | `advance_paid` | `NUMERIC(12, 2)` NOT NULL | **Monetary Harmonization (DEC-007):** Float converted to exact decimal. |
| `vacate_requests`| `advance_repayable`| `double precision`| `vacate_requests` | `advance_repayable`| `NUMERIC(12, 2)` NOT NULL | Float converted to exact decimal. |
| `vacate_requests`| `status` | `varchar(255)` | `vacate_requests` | `status` | `VARCHAR(50)` NOT NULL | Authoritative enum: `PENDING`, `APPROVED`, `REJECTED`, `COMPLETED`, `CANCELLED`. |
| **`invoices`** | `id` | `bigint` PK | `invoices` | `id` | `BIGINT GENERATED ALWAYS AS IDENTITY` PK | Preserved. |
| `invoices` | `invoice_number`| `varchar(255)` UK | `invoices` | `invoice_number`| `VARCHAR(50)` NOT NULL UNIQUE | Preserved. |
| `invoices` | `tenant_uid` | `varchar(255)` (no FK) | `invoices` | `tenant_id` | `BIGINT` FK &rarr; `tenants(id)` | Added surrogate FK with `ON DELETE RESTRICT`. `tenant_uid` retained for unique constraint. |
| `invoices` | `property_id` | `bigint` (no FK) | `invoices` | `property_id` | `BIGINT` FK &rarr; `properties(id)` | Enforced with `ON DELETE RESTRICT`. |
| `invoices` | `amount` | `integer` | `invoices` | `amount` | `NUMERIC(12, 2)` NOT NULL | Preserved value, typed as exact decimal. |
| `invoices` | `paid_amount` | `integer` | `invoices` | `paid_amount` | `NUMERIC(12, 2)` NOT NULL | Preserved value, typed as exact decimal. |
| **`payments`** | `id` | `bigint` PK | `payments` | `id` | `BIGINT GENERATED ALWAYS AS IDENTITY` PK | Preserved. |
| `payments` | `invoice_id` | `bigint` (no FK) | `payments` | `invoice_id` | `BIGINT` FK &rarr; `invoices(id)` | Enforced with `ON DELETE RESTRICT`. |
| `payments` | `tenant_uid` | `varchar(255)` (no FK) | `payments` | `tenant_id` | `BIGINT` FK &rarr; `tenants(id)` | Added surrogate FK with `ON DELETE RESTRICT`. |
| `payments` | `property_id` | `bigint` (no FK) | `payments` | `property_id` | `BIGINT` FK &rarr; `properties(id)` | Enforced with `ON DELETE RESTRICT`. |
| **`ledger_transactions`**| `id` | `bigint` PK | `ledger_transactions` | `id` | `BIGINT GENERATED ALWAYS AS IDENTITY` PK | Historical transactions strictly preserved. |
| `ledger_transactions`| `property_id` | `bigint` (no FK) | `ledger_transactions` | `property_id` | `BIGINT` FK &rarr; `properties(id)` | Enforced with `ON DELETE RESTRICT`. |
| `ledger_transactions`| `tenant_uid` | `varchar(255)` | `ledger_transactions` | `tenant_id` | `BIGINT` FK &rarr; `tenants(id)` | **Nullable Foreign Key:** Allows historical empty/unlinked entries. |
| `ledger_transactions`| `invoice_id` | `bigint` | `ledger_transactions` | `invoice_id` | `BIGINT` FK &rarr; `invoices(id)` | **Nullable Foreign Key:** Historical rows remain NULL. |
| `ledger_transactions`| `payment_id` | `bigint` | `ledger_transactions` | `payment_id` | `BIGINT` FK &rarr; `payments(id)` | **Nullable Foreign Key:** Historical rows remain NULL. |

---

## 2. Downstream Financial Foreign Key Dependency Matrix

All relationships involving financial and accounting entities are protected with **`ON DELETE RESTRICT`** to guarantee audit and tax integrity:

| Parent Table | Child Table | Foreign Key Name | Child Column | Referential Action | Business Rationale |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `properties` | `rooms` | `fk_rooms_property` | `property_id` | `ON DELETE RESTRICT` | Prevents deleting property while physical inventory exists. |
| `properties` | `invoices` | `fk_invoices_property` | `property_id` | `ON DELETE RESTRICT` | Prevents deleting property while legal bills exist. |
| `properties` | `payments` | `fk_payments_property` | `property_id` | `ON DELETE RESTRICT` | Prevents deleting property while cash collections exist. |
| `properties` | `ledger_transactions`| `fk_ledger_property` | `property_id` | `ON DELETE RESTRICT` | Prevents deleting property while accounting records exist. |
| `tenants` | `admissions` | `fk_admissions_tenant`| `tenant_id` | `ON DELETE RESTRICT` | Preserves stay history for every resident. |
| `tenants` | `invoices` | `fk_invoices_tenant` | `tenant_id` | `ON DELETE RESTRICT` | Preserves financial receivables per person. |
| `invoices` | `payments` | `fk_payments_invoice` | `invoice_id` | `ON DELETE RESTRICT` | Guarantees payments always link to their originating bill. |
| `invoices` | `ledger_transactions`| `fk_ledger_invoice` | `invoice_id` | `ON DELETE RESTRICT` | Retains audit link between billing and general ledger. |
| `payments` | `ledger_transactions`| `fk_ledger_payment` | `payment_id` | `ON DELETE RESTRICT` | Retains audit link between payment receipt and cash ledger. |
| `admissions` | `vacate_requests`| `fk_vacate_admission` | `admission_id` | `ON DELETE RESTRICT` | Prevents closure records from being orphaned. |

---

## 3. Data Transformation & Compatibility Rules

### Transformation Rule 1: Dynamic Block Backfill
```sql
-- Generate default block for any property currently lacking blocks
INSERT INTO blocks (property_id, name, code, status, created_at, updated_at)
SELECT 
    p.id, 
    'Main Block', 
    'BLK-' || p.code, 
    'ACTIVE', 
    NOW(), 
    NOW()
FROM properties p
WHERE NOT EXISTS (SELECT 1 FROM blocks b WHERE b.property_id = p.id);
```

### Transformation Rule 2: Dynamic Floor Extraction
```sql
-- Extract integer floors from existing rooms.floor text
INSERT INTO floors (block_id, floor_number, name, status, created_at, updated_at)
SELECT DISTINCT 
    b.id,
    CAST(r.floor AS INT),
    'Floor ' || r.floor,
    'ACTIVE',
    NOW(),
    NOW()
FROM rooms r
JOIN blocks b ON b.property_id = r.property_id
WHERE r.floor ~ '^[0-9]+$'
  AND NOT EXISTS (
      SELECT 1 FROM floors f 
      WHERE f.block_id = b.id AND f.floor_number = CAST(r.floor AS INT)
  );
```

### Transformation Rule 3: Deterministic Bed Inventory Generation
```sql
-- Auto-generate beds for each slot in room capacity
INSERT INTO beds (room_id, bed_number, base_rent, status, created_at, updated_at)
SELECT 
    r.id,
    'Bed ' || seq.n,
    r.rent_per_month,
    'AVAILABLE',
    NOW(),
    NOW()
FROM rooms r
CROSS JOIN LATERAL generate_series(1, r.occupancy) AS seq(n)
WHERE NOT EXISTS (
    SELECT 1 FROM beds b WHERE b.room_id = r.id AND b.bed_number = 'Bed ' || seq.n
);
```

### Transformation Rule 4: Active Resident Bed Mapping
```sql
-- Map active admissions to available beds in the assigned room in deterministic order
WITH active_admissions AS (
    SELECT 
        a.id AS admission_id,
        a.room_no,
        ROW_NUMBER() OVER (PARTITION BY a.room_no ORDER BY a.enrollment_date, a.id) AS rn
    FROM admissions a
    WHERE a.status IN ('PENDING', 'PAID')
),
available_beds AS (
    SELECT 
        b.id AS bed_id,
        r.room_no,
        ROW_NUMBER() OVER (PARTITION BY r.room_no ORDER BY b.id) AS rn
    FROM beds b
    JOIN rooms r ON b.room_id = r.id
)
UPDATE admissions a
SET bed_id = ab.bed_id
FROM active_admissions aa
JOIN available_beds ab ON aa.room_no = ab.room_no AND aa.rn = ab.rn
WHERE a.id = aa.admission_id;

-- Transition allocated beds to OCCUPIED
UPDATE beds b
SET status = 'OCCUPIED'
WHERE b.id IN (SELECT bed_id FROM admissions WHERE status = 'PAID' AND bed_id IS NOT NULL);
```

---

## 4. Exception Handling & Ambiguity Policies

| Potential Inconsistency | Automated Exception Policy | Human Action Required |
| :--- | :--- | :--- |
| **Room Occupancy Exceeded:** A room has more active tenants than its declared `occupancy` capacity. | Flag room into `migration_exceptions` table. Automatically increase `occupancy` to match resident count. | Administrator reviews room physical layout and rebalances inventory. |
| **Non-Numeric Floor String:** `rooms.floor` contains values like `'Ground'`, `'Basement'`, `'Terrace'`. | Map `'Ground'` to floor `0`; map `'Basement'` to floor `-1`; map other non-numeric text to sequential floors (>100) and log exception. | Staff renames floor labels via admin UI. |
| **Unlinked Ledger Row:** Historical ledger entry where `invoice_id` or `payment_id` cannot be correlated. | Preserve ledger entry with NULL foreign keys. Never delete or fabricate records. | Documented in audit register as verified baseline legacy cash movement. |
| **Dangling Vacate Request:** Vacate notice filed for a resident with no active admission. | Retain `vacate_requests` row. Leave `admission_id = NULL` and flag for staff investigation. | Staff manually confirms past stay dates. |
