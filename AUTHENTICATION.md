# RMS authentication and administration

The backend serves a separate administrator UI at **`/admin/index.html`** (locally, `http://localhost:8080/admin/index.html`). The React application in `../rms-frontend/frontend` provides owner/staff login and links to this page. Root administration does not automatically grant access to residents or financial data.

| Role | Scope and permissions |
| --- | --- |
| ROOT | Create PG owners, list their properties, activate/deactivate owners and reset owner passwords |
| OWNER | Manage their PGs and staff accounts; assign representatives/sub-members to multiple PGs |
| REPRESENTATIVE | Manage operations only in assigned PGs belonging to their owner |
| SUB_MEMBER | Read assigned PG data; change their own password and sign out |

## First-time setup

1. Configure the database and set `RMS_BOOTSTRAP_KEY` to a randomly generated secret with at least 32 characters. There are no built-in credentials.
2. Start the server with authentication enabled (the default).
3. Open `/admin/index.html`. Enter the setup key and create the root administrator.
4. Sign in as root, then create PG-owner accounts. Owners sign in to the main React application, create PGs in Settings and add staff with PG assignments.
5. Remove `RMS_BOOTSTRAP_KEY` from deployment configuration after setup. The database singleton also prevents creating a second root, including concurrent setup requests.

Public owner self-registration is **disabled by default**. The legacy `/api/auth/register` endpoint only works when `RMS_SELF_REGISTRATION_ENABLED=true` is intentionally configured. The admin page never exposes the setup secret, password hashes or access tokens. Browser tokens are held in memory, so refreshing the page requires signing in again.

## Testing switch

To disable **both authentication and authorization** against isolated test data:

```sh
export SPRING_PROFILES_ACTIVE=local
export RMS_SECURITY_ENABLED=false
export RMS_SECURITY_TEST_OWNER_ID=1
bash mvnw spring-boot:run
```

`RMS_SECURITY_TEST_OWNER_ID` must identify an existing, active OWNER. Provision that owner with security enabled first. Bypass requests receive a synthetic test principal and can access all PGs; newly created data is associated with the configured test owner. A missing/inactive test owner produces HTTP 503. The UI displays a testing banner, and API responses carry `X-RMS-Authentication: disabled-for-testing`.

The server refuses to start with security disabled unless **every** active profile is `local` or `test`. A combination such as `prod,test` is rejected. No request header or frontend setting can turn security off. Resource consistency checks still run. Global database cleanup and the unverified webhook endpoints remain blocked in both modes.

To restore authentication:

```sh
export RMS_SECURITY_ENABLED=true
```

Restart the backend and reload the browser after changing this flag. Never expose the testing instance to untrusted networks or connect it to production data.

## API and constants

All Java controller mappings are maintained in `common/ApiPaths.java`; authentication roles, headers and limits are in `common/SecurityConstants.java`. The frontend has `src/config/apiPaths.ts` and `src/config/constants.ts`. The backend admin UI obtains its route map from `/api/client-config`, so its API URLs follow the Java definitions.

- `POST /api/admin/bootstrap` — one-time root creation; requires `X-Bootstrap-Key`.
- `GET/POST /api/admin/owners` — list/create owners (root only).
- `GET /api/admin/owners/{id}/properties` — inspect an owner's PGs (root only).
- `PUT /api/admin/owners/{id}/status` — `{"status":"ACTIVE"}` or `INACTIVE`.
- `PUT /api/admin/owners/{id}/password` — `{"password":"new-long-password"}`.
- `POST /api/auth/login` — `{"username":"owner","password":"long-password"}`; returns `accessToken`, `tokenType`, `expiresAt`.
- `GET /api/auth/me` — current user, role, owner and effective property grants.
- `POST /api/auth/logout` — revoke the current token.
- `POST /api/auth/password` — `{"currentPassword":"old-long-password","newPassword":"new-long-password"}`; revokes all of this user's sessions.
- `POST/PUT /api/users[/{id}]` — owner-managed staff accounts. Supply username, email, fullName, role (`REPRESENTATIVE` or `SUB_MEMBER`) and `propertyIds`. Password is required on creation; optional on updates. Omitted role/assignments are retained on updates, while `propertyIds: []` revokes all grants.

Account creation payload:

```json
{"username":"pg-owner","email":"owner@example.com","fullName":"PG Owner","password":"a-long-unique-password"}
```

Passwords require 12 characters minimum and 72 UTF-8 bytes maximum. Protected calls use `Authorization: Bearer <token>`. Property headers and query parameters are selectors, never proof of access. API 401 means authentication is needed; 403 means the account lacks permission; 429 means login/setup rate limiting.

## Hardening and deployment

- BCrypt passwords; 256-bit random bearer tokens with only SHA-256 hashes stored; eight-hour default expiry, configurable with `RMS_TOKEN_TTL` (5 minutes–24 hours).
- Database-backed account lockout after five failed logins for 15 minutes, serialized per account. Unknown accounts undergo dummy password verification. A bounded, per-instance IP limiter also protects login/setup; configure trusted ingress rate limiting for aggregate multi-replica protection. The application uses the direct peer address, not arbitrary forwarded headers.
- Account and owner authentication versions prevent revoked tokens being revived after account changes. Account status and property grants are reloaded per request. Expired sessions are deleted hourly.
- Root lifecycle audit events record actor/target IDs, never passwords or tokens. Ship server logs to your retained audit system.
- Explicit CORS allowlist (`RMS_ALLOWED_ORIGINS`, comma-separated); stateless bearer authentication; no authentication cookies. Admin page CSP blocks inline scripts, external resources and framing. Serve behind HTTPS; Spring Security emits HSTS for secure requests.
- Use `SPRING_PROFILES_ACTIVE=prod` in production: Hibernate validates schema rather than changing it, SQL logging is off, and server errors omit stack traces. Apply reviewed PostgreSQL migrations before startup. Existing business schema provisioning remains your deployment responsibility; this project does not introduce an automatic migration runner.
- Startup's legacy data-repair script is off by default. It runs only when `rms.legacy-repair.enabled=true` is explicitly set. Do not use it as a migration mechanism.

Apply `migration_authentication.sql` and then `migration_authentication_hardening.sql` when upgrading existing databases. Back up first. Assign existing PGs to verified owners explicitly; unowned production records remain inaccessible. Review existing property IDs for consistency. Legacy ADMIN/PROPERTY_MANAGER/STAFF accounts are not automatically promoted. Existing globally unique room numbers and resident identifiers remain unchanged.

This hardens the authentication and administration flows; deployment still requires real PostgreSQL migration validation, TLS/secret management, backups and load testing for the expected workload. It is not a blanket certification of all pre-existing billing/residency behavior.

## Verification

Run `bash mvnw test` in the backend. Tests cover root provisioning, disabled public registration, owner isolation, multiple PG grants, read-only accounts, revocation, credential changes, lockout, bypass operation and rejection of production bypass. Frontend checks: `npm run lint` and `npm run build`.
