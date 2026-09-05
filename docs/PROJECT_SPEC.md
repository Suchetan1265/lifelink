# LifeLink — Blood Donor–Hospital Matching Platform

Full project specification: functional use cases, workflow, database design, API endpoints, caching, auth, async notifications, frontend, and local setup.

**Stack:** Spring Boot 3 (Java 17+) · PostgreSQL · Redis · RabbitMQ · React (Vite) · JWT auth · Spring State Machine · Quartz/Spring Scheduler

---

## 1. Roles

| Role | Who | Can do |
|---|---|---|
| DONOR | Individual donor | Manage profile/availability, accept/decline matches, view history |
| HOSPITAL | Hospital staff account | Raise/manage blood requests, confirm donors, record fulfillment |
| BLOOD_BANK | Blood bank admin | Manage stock inventory, handle escalated requests |
| ADMIN | Platform admin | Verify hospitals/blood banks, view system stats |

---

## 2. Functional Use Cases (step by step)

### A. Donor journey
1. **Register** — email, phone, password, blood group, city, location (lat/lng), preferred travel radius (km).
2. **Login** — receives access token (15 min) + refresh token (7 days).
3. **Set availability** — a simple ON/OFF toggle ("I can donate right now"). OFF removes them from matching.
4. **Get matched** — when a hospital raises a compatible request nearby, donor receives a notification (email/SMS/push) with request details.
5. **Accept or decline** the match. Accepting tells the hospital "I'm willing to come."
6. **Get confirmed** — hospital picks one (or more) accepted donors; donor gets confirmation with hospital address and needed-by time.
7. **Donate** — hospital records the donation. System sets donor's `last_donation_date` and computes `next_eligible_date = +90 days`. Donor is automatically excluded from matching until then.
8. **View history & eligibility** — past donations, days until eligible again.

### B. Hospital journey
1. **Register hospital** — name, address, location, license number. Account stays `PENDING` until ADMIN verifies (prevents fake hospitals).
2. **Raise a request** — blood group, units needed, urgency (CRITICAL / HIGH / NORMAL), needed-by timestamp, notes. Rate-limited (e.g., max 10 requests/hour per hospital) via Redis.
3. **System matches automatically** (see Matching Engine below) and notifies top-N donors.
4. **Watch responses come in** — dashboard shows each notified donor's status (NOTIFIED / ACCEPTED / DECLINED).
5. **Confirm a donor** — pick from accepted donors → request becomes CONFIRMED, other accepted donors get a "thanks, covered" notification.
6. **Record fulfillment** — donation happened → request FULFILLED, donation row written, donor eligibility updated.
7. **Cancel** anytime before fulfillment (audit-logged with reason).

### C. Blood Bank journey
1. **Register + get verified** by ADMIN.
2. **Maintain inventory** — units on hand per blood group.
3. **Receive escalations** — if a request isn't CONFIRMED within X hours (X depends on urgency: CRITICAL=2h, HIGH=6h, NORMAL=24h), the system escalates it to blood banks within radius.
4. **Accept escalation** → request CONFIRMED (source = blood bank). **Fulfill** → inventory decremented, request FULFILLED.

### D. Admin journey
1. Verify/reject pending hospital and blood bank registrations.
2. View platform stats (requests by status, fulfillment rate, avg time-to-fulfill, donor counts by blood group/city).
3. Deactivate abusive accounts.

### E. System (automatic) behaviors
1. **Matching engine** (runs on request creation):
   - Compute compatible donor blood groups (e.g., request for A+ can take A+, A-, O+, O-).
   - Query Redis GEO set for available donors of those groups within radius of the hospital.
   - Filter: `available = true`, `next_eligible_date <= today`, not already matched to an active request.
   - Rank by distance, notify top N (e.g., 20) via RabbitMQ → notification workers.
   - Request moves RAISED → MATCHED when the first donor accepts.
2. **Escalation job** (Quartz, every 15 min): requests still RAISED/MATCHED past their urgency deadline → ESCALATED, notify nearby blood banks.
3. **Expiry job** (Quartz, every 15 min): requests past `needed_by` and not FULFILLED → EXPIRED, notify hospital.
4. **Eligibility refresh job** (daily 00:05): donors whose `next_eligible_date` = today → re-add to Redis availability sets, send "you're eligible again" notification.

---

## 3. Request Lifecycle (Spring State Machine)

**States:** `RAISED → MATCHED → CONFIRMED → FULFILLED` plus `ESCALATED`, `EXPIRED`, `CANCELLED`

**Transitions (events):**

| From | Event | To | Triggered by |
|---|---|---|---|
| RAISED | DONOR_ACCEPTED | MATCHED | Donor accepts a match |
| RAISED / MATCHED | ESCALATE_TIMEOUT | ESCALATED | Quartz job (deadline passed) |
| MATCHED | HOSPITAL_CONFIRMED | CONFIRMED | Hospital picks a donor |
| ESCALATED | BANK_ACCEPTED | CONFIRMED | Blood bank accepts |
| CONFIRMED | DONATION_RECORDED | FULFILLED | Hospital/bank records donation |
| RAISED / MATCHED / ESCALATED | NEEDED_BY_PASSED | EXPIRED | Quartz job |
| any non-terminal | CANCEL | CANCELLED | Hospital |

Every transition writes a row to `request_status_history` (audit trail) and publishes a notification event to RabbitMQ. Guards enforce rules (e.g., can't confirm a donor who didn't accept; can't fulfill from a bank without stock).

---

## 4. Database Design (PostgreSQL)

Use **Flyway** migrations (`src/main/resources/db/migration/V1__init.sql` …).

```sql
users              (id PK, email UNIQUE, phone, password_hash, role, status, created_at)
refresh_tokens     (id PK, user_id FK, token_hash, expires_at, revoked, created_at)

donors             (user_id PK/FK, full_name, blood_group, lat, lng, city,
                    radius_km, available BOOL, last_donation_date, next_eligible_date)

hospitals          (id PK, user_id FK, name, license_no, address, lat, lng,
                    verified BOOL DEFAULT false)

blood_banks        (id PK, user_id FK, name, address, lat, lng, verified BOOL)
blood_inventory    (id PK, blood_bank_id FK, blood_group, units,
                    UNIQUE(blood_bank_id, blood_group))

requests           (id PK, hospital_id FK, blood_group, units, urgency,
                    needed_by TIMESTAMPTZ, status, notes,
                    created_at, escalated_at, closed_at)

request_matches    (id PK, request_id FK, donor_id FK,
                    status,          -- NOTIFIED / ACCEPTED / DECLINED / CONFIRMED
                    distance_km, notified_at, responded_at,
                    UNIQUE(request_id, donor_id))

donations          (id PK, request_id FK, donor_id FK NULL, blood_bank_id FK NULL,
                    units, donated_at)

request_status_history (id PK, request_id FK, from_status, to_status,
                        changed_by FK users, reason, changed_at)

notifications      (id PK, user_id FK, type, title, body, read BOOL, created_at)
```

**Key indexes:**
- `donors(blood_group) WHERE available = true`
- `requests(status, needed_by)` — for the Quartz jobs
- `request_matches(donor_id, status)` — donor's pending matches
- `request_status_history(request_id)`

Blood groups and statuses as Postgres `VARCHAR` + Java enums (simpler than PG enums with JPA).

---

## 5. API Endpoints (Spring Boot, base `/api`)

### Auth
| Method | Path | Who | What |
|---|---|---|---|
| POST | `/auth/register/donor` | public | Create donor account |
| POST | `/auth/register/hospital` | public | Create hospital (status PENDING) |
| POST | `/auth/register/bloodbank` | public | Create blood bank (PENDING) |
| POST | `/auth/login` | public | → `{accessToken, refreshToken}` |
| POST | `/auth/refresh` | public | Rotate refresh token, new access token |
| POST | `/auth/logout` | any | Revoke refresh token |
| GET | `/auth/me` | any | Current user profile + role |

### Donor
| Method | Path | What |
|---|---|---|
| GET / PUT | `/donors/me` | View / update profile (blood group, location, radius) |
| PATCH | `/donors/me/availability` | `{available: true/false}` — also updates Redis GEO set |
| GET | `/donors/me/matches` | Requests I've been matched to (with status) |
| POST | `/matches/{matchId}/accept` | Accept → fires DONOR_ACCEPTED |
| POST | `/matches/{matchId}/decline` | Decline |
| GET | `/donors/me/donations` | Donation history |
| GET | `/donors/me/eligibility` | `{eligible, nextEligibleDate, daysRemaining}` |

### Hospital
| Method | Path | What |
|---|---|---|
| POST | `/requests` | Raise request (rate-limited via Redis) → triggers matching |
| GET | `/requests` | My requests, filter `?status=&page=&size=` |
| GET | `/requests/{id}` | Detail + current state |
| GET | `/requests/{id}/matches` | Matched donors + their responses |
| POST | `/requests/{id}/matches/{matchId}/confirm` | Fires HOSPITAL_CONFIRMED |
| POST | `/requests/{id}/fulfill` | `{donorId or bloodBankId, units}` → FULFILLED, writes donation, updates eligibility |
| POST | `/requests/{id}/cancel` | `{reason}` → CANCELLED |
| GET | `/requests/{id}/history` | Full audit trail |

### Blood Bank
| Method | Path | What |
|---|---|---|
| GET / PUT | `/bloodbanks/me/inventory` | View / set units per blood group |
| GET | `/bloodbanks/me/escalations` | Escalated requests within radius |
| POST | `/escalations/{requestId}/accept` | Fires BANK_ACCEPTED |
| POST | `/escalations/{requestId}/fulfill` | Decrement inventory → FULFILLED |

### Admin
| Method | Path | What |
|---|---|---|
| GET | `/admin/verifications?type=hospital\|bloodbank` | Pending registrations |
| POST | `/admin/verifications/{id}/approve` / `/reject` | Verify |
| GET | `/admin/stats` | Platform metrics (Redis-cached, 5 min TTL) |
| PATCH | `/admin/users/{id}/status` | Enable/disable account |

### Shared
| Method | Path | What |
|---|---|---|
| GET | `/notifications?unread=true` | My notifications |
| PATCH | `/notifications/{id}/read` | Mark read |
| GET | `/meta/blood-groups` | Enum + compatibility matrix (for frontend dropdowns) |

---

## 6. Auth Design (JWT + refresh, role-based)

- **Access token**: JWT, 15 min, HS256 signed with `JWT_SECRET`, claims: `sub` (userId), `role`, `email`.
- **Refresh token**: random 256-bit string, 7 days, stored **hashed** in `refresh_tokens`. `/auth/refresh` rotates it (old one revoked). Logout revokes.
- **Spring Security**: stateless filter chain, `JwtAuthFilter` → sets `Authentication`; method security with `@PreAuthorize("hasRole('HOSPITAL')")` etc.
- **Passwords**: BCrypt.
- OAuth2 Google login: optional add-on later via `spring-boot-starter-oauth2-client` — skip for v1.

## 7. Redis Usage

| Key pattern | Type | Purpose |
|---|---|---|
| `donors:geo:{bloodGroup}` | GEO set (GEOADD/GEOSEARCH) | Available donors' locations per blood group — the matching hot path |
| `donor:{id}:profile` | Hash, TTL 1h | Cached donor lookup during match ranking |
| `stats:admin` | String (JSON), TTL 5 min | Expensive aggregate dashboard queries |
| `rl:requests:{hospitalId}` | Counter, TTL 1h | Rate limit: max 10 requests/hour |
| `rt:blacklist:{jti}` | String, TTL = token life | Optional: revoked access tokens |

Write-through discipline: availability toggle, donation recorded, and eligibility job all update the GEO sets immediately; Postgres remains the source of truth.

## 8. RabbitMQ (async notifications)

- Exchange `notifications` (topic). Routing keys: `notify.email`, `notify.sms`, `notify.push`.
- Producers: matching engine (donor matched), state machine listeners (confirmed/fulfilled/expired), escalation job (bank alert), eligibility job.
- Consumers: `EmailWorker` (real SMTP via Mailtrap.io free tier for dev), `SmsWorker` (log-only mock, or Twilio trial), `PushWorker` (stub).
- Dead-letter queue for failed sends + retry with backoff. This is a great interview talking point.

## 9. Frontend (React + Vite)

Pages by role:
- **Public**: landing, login, register (donor / hospital / bank tabs)
- **Donor**: dashboard (availability toggle, eligibility countdown, pending match cards with Accept/Decline), donation history
- **Hospital**: raise-request form, requests table with live status chips, request detail (donor responses, confirm button, timeline from status history), map of matched donors (Leaflet + OpenStreetMap — free, no API key)
- **Blood bank**: inventory editor, escalations list
- **Admin**: verification queue, stats dashboard (charts via Recharts)

Plumbing: React Router; Axios instance with interceptor that auto-refreshes on 401 using the refresh token; role-based route guards; TanStack Query for data fetching/polling (poll request detail every 10 s — simpler than WebSockets for v1).

## 10. Local Setup (Windows)

### Tools
1. **Java 17+** — Eclipse Temurin JDK (adoptium.net). Set `JAVA_HOME`, add `%JAVA_HOME%\bin` to `Path`.
2. **Node.js LTS** — nodejs.org (for React).
3. **PostgreSQL** — already installed. Add `C:\Program Files\PostgreSQL\<version>\bin` to `Path` (for `psql`). Use **pgAdmin** (bundled) as GUI — **SQLyog is MySQL-only and cannot connect to Postgres**; alternative GUI: DBeaver Community.
4. **Docker Desktop** — easiest way to run Redis + RabbitMQ on Windows (Redis has no official Windows build).
5. **Git** — already installed. Run once:
   `git config --global user.name "Your Name"` and `git config --global user.email "you@example.com"`
6. **Editor**: VS Code works well for both halves — install "Extension Pack for Java" + "Spring Boot Extension Pack". (IntelliJ IDEA Community is the other common choice for Spring.)

### Database
```sql
-- in psql or pgAdmin query tool:
CREATE DATABASE lifelink;
CREATE USER lifelink_app WITH PASSWORD 'changeme';
GRANT ALL PRIVILEGES ON DATABASE lifelink TO lifelink_app;
```

### docker-compose.yml (project root)
```yaml
services:
  redis:
    image: redis:7
    ports: ["6379:6379"]
  rabbitmq:
    image: rabbitmq:3-management
    ports: ["5672:5672", "15672:15672"]   # 15672 = web console (guest/guest)
```
Run: `docker compose up -d`

### Spring Boot config (`application.yml`)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/lifelink
    username: ${DB_USER:lifelink_app}
    password: ${DB_PASSWORD}
  jpa:
    hibernate.ddl-auto: validate   # Flyway owns the schema
  data.redis:
    host: localhost
    port: 6379
  rabbitmq:
    host: localhost
    port: 5672
app:
  jwt:
    secret: ${JWT_SECRET}
    access-ttl-minutes: 15
    refresh-ttl-days: 7
```

### Environment variables
System-level (Windows → "Edit the system environment variables"):
- `JAVA_HOME` = JDK install dir; add `%JAVA_HOME%\bin` and Postgres `bin` to `Path`.

App secrets — do NOT hardcode; set per-run (VS Code launch config, or a git-ignored `.env`):
- `DB_PASSWORD` — your postgres app-user password
- `JWT_SECRET` — long random string (≥ 64 chars)
- `MAIL_USER` / `MAIL_PASSWORD` — Mailtrap credentials (when you add email)

### Ports
| Service | Port |
|---|---|
| Spring Boot API | 8080 |
| React dev server | 5173 |
| PostgreSQL | 5432 |
| Redis | 6379 |
| RabbitMQ | 5672 (console 15672) |

React dev proxy: in `vite.config.js`, proxy `/api` → `http://localhost:8080` (avoids CORS in dev).

## 11. Build Order (4 weeks)

- **Week 1 — Foundation**: repo + Spring Initializr project (web, security, data-jpa, validation, flyway, postgresql, data-redis, amqp, quartz), Flyway V1 schema, entities/repositories, JWT auth end-to-end (register/login/refresh/me), role guards. React scaffold + login/register pages.
- **Week 2 — Core domain**: request CRUD, matching engine (start with plain SQL distance filter, then swap in Redis GEO), request_matches accept/decline/confirm, Spring State Machine wiring + status history. Hospital dashboard + donor match cards in React.
- **Week 3 — Infra features**: Redis GEO sets + profile cache + rate limiter, Quartz escalation/expiry/eligibility jobs, RabbitMQ notification pipeline with email worker (Mailtrap). Blood bank inventory + escalation flow.
- **Week 4 — Polish**: admin verification + stats (cached), Leaflet map view, notification bell, audit timeline UI, seed data script, README with architecture diagram, deploy (Render/Railway free tier) if time allows.

Start every week by committing to git; keep `main` always runnable.
