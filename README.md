<div align="center">

<img src="https://raw.githubusercontent.com/imkrishnaaaaaaa/kieru-frontend/refs/heads/main/public/kieru_full_logo.webp" alt="Kieru Secure" width="340" height="120">

<h3>Zero-Knowledge Secret Sharing Platform</h3>

<p>Self-destructing secrets with client-side encryption. Share credentials securely, vanish without a trace.</p>

<p>
<a href="https://kieru-secure.vercel.app"><strong>Live Application</strong></a> •
<a href="https://github.com/imkrishnaaaaaaa/kieru-backend/issues"><strong>Report Issue</strong></a> •
<a href="https://github.com/imkrishnaaaaaaa/kieru-backend/issues"><strong>Request Feature</strong></a>
</p>

<p>
<img src="https://img.shields.io/badge/Spring_Boot-3.2-6DB33F?style=flat-square&logo=spring-boot&logoColor=white" alt="Spring Boot">
<img src="https://img.shields.io/badge/Java-17-007396?style=flat-square&logo=openjdk&logoColor=white" alt="Java">
<img src="https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat-square&logo=postgresql&logoColor=white" alt="PostgreSQL">
<img src="https://img.shields.io/badge/Redis-7.2-DC382D?style=flat-square&logo=redis&logoColor=white" alt="Redis">
<img src="https://img.shields.io/badge/Docker-Latest-2496ED?style=flat-square&logo=docker&logoColor=white" alt="Docker">
</p>

</div>

---

## Overview

**Kieru Secure** is an enterprise-grade ephemeral secret-sharing platform that implements true zero-knowledge architecture. The backend stores only encrypted payloads—decryption keys are generated client-side and transmitted via URL fragments, ensuring the server never has access to plaintext data.

### Core Principles

- **Zero-Knowledge Storage** — Server stores ciphertext only, no plaintext ever touches the backend
- **Atomic Expiration** — Secrets self-destruct by time (TTL) or view count with Redis-backed atomic operations
- **Concurrency Safety** — Race condition prevention through distributed Redis counters
- **Stateless Authentication** — Firebase JWT verification with Spring Security filter chain
- **Comprehensive Auditing** — Access logs with IP tracking and device fingerprinting

---

## System Architecture
```
┌─────────────┐
│   Client    │
│  (Browser)  │
└──────┬──────┘
       │ AES-256-GCM Encrypted Payload
       ▼
┌─────────────────────────────────────┐
│      Spring Boot REST API           │
│  ┌───────────────────────────────┐  │
│  │  FirebaseAuthFilter           │  │
│  │  (JWT Verification)           │  │
│  └───────────┬───────────────────┘  │
│              ▼                      │
│  ┌───────────────────────────────┐  │
│  │  Controllers                  │  │
│  │  (Rate Limited via AOP)       │  │
│  └───────────┬───────────────────┘  │
│              ▼                      │
│  ┌───────────────────────────────┐  │
│  │  Service Layer                │  │
│  └───┬───────────────────────┬───┘  │
└──────┼───────────────────────┼──────┘
       │                       │
       ▼                       ▼
┌──────────────┐      ┌────────────────┐
│  PostgreSQL  │      │     Redis      │
│              │      │                │
│ • Users      │      │ • View Counter │
│ • Metadata   │      │ • Rate Limits  │
│ • Payloads   │      │ • Cache        │
│ • Logs       │      │ • Locks        │
└──────────────┘      └────────────────┘
```

### Storage Strategy

| Database | Responsibility | Reason |
|----------|---------------|--------|
| **PostgreSQL** | Users, Secret Metadata, Encrypted Payloads, Access Logs, Daily Stats | ACID compliance, complex queries, persistent storage |
| **Redis** | View Counters, Rate Limit Counters, Subscription Cache | Atomic operations, sub-millisecond latency, distributed locks |

---

## Data Model

| Entity | Purpose |
|--------|---------|
| `User` | Profile, subscription tier, login provider, session version |
| `SecretMetadata` | Lifecycle state, view limits, expiry timestamp, ownership |
| `SecretPayload` | AES-encrypted content + optional password hash (1:1 with metadata) |
| `SecretAccessLog` | Access attempts with IP address, user agent, timestamps |
| `DailyStatistic` | Aggregated metrics for analytics dashboard |

---

## Technology Stack

<table>
  <tr>
    <th>Layer</th>
    <th>Technology</th>
    <th>Purpose</th>
  </tr>
  <tr>
    <td><strong>Backend Framework</strong></td>
    <td>Spring Boot 3.2, Java 17</td>
    <td>REST API, dependency injection, scheduling</td>
  </tr>
  <tr>
    <td><strong>Frontend</strong></td>
    <td>React 18, TypeScript</td>
    <td>SPA with client-side encryption (Web Crypto API)</td>
  </tr>
  <tr>
    <td><strong>Databases</strong></td>
    <td>PostgreSQL 16 (Neon), Redis 7.2 (Upstash)</td>
    <td>Persistent storage + in-memory cache</td>
  </tr>
  <tr>
    <td><strong>Authentication</strong></td>
    <td>Firebase Admin SDK, Spring Security</td>
    <td>Stateless JWT verification</td>
  </tr>
  <tr>
    <td><strong>Rate Limiting</strong></td>
    <td>Custom AOP + Redis Counters</td>
    <td>Annotation-based throttling with auto-lockout</td>
  </tr>
  <tr>
    <td><strong>Observability</strong></td>
    <td>Logback + Better Stack (Logtail)</td>
    <td>Structured JSON logging with MDC context</td>
  </tr>
  <tr>
    <td><strong>Deployment</strong></td>
    <td>Docker (Multi-stage), Render</td>
    <td>Containerized cloud hosting</td>
  </tr>
</table>

---

## API Reference

### Public Endpoints

| Method | Endpoint | Description | Rate Limit |
|--------|----------|-------------|------------|
| `POST` | `/api/auth/login` | Verify Firebase token, sync user | Unlimited |
| `POST` | `/api/secrets/{id}/access` | Unlock secret with optional password | 50/hour per IP |
| `GET` | `/api/secrets/validation?id={id}` | Check if secret exists and is active | 100/hour per IP |
| `GET` | `/api/assets/subscriptions` | Get subscription plan names | 1000/hour |
| `GET` | `/api/assets/subscriptions/char-limits` | Character limits per plan | 1000/hour |
| `GET` | `/api/assets/subscriptions/file-size-limits` | File size limits per plan | 1000/hour |
| `GET` | `/api/assets/subscriptions/daily-secret-limits` | Daily creation limits | 1000/hour |

### Authenticated Endpoints

**Requires:** `Authorization: Bearer <firebase-id-token>`

| Method | Endpoint | Description | Rate Limit |
|--------|----------|-------------|------------|
| `POST` | `/api/secrets/create` | Create new secret | 10/day (user) |
| `POST` | `/api/secrets/update-password/{id}` | Update secret password | 20/hour |
| `POST` | `/api/auth/logout` | Invalidate session | Unlimited |
| `GET` | `/api/dashboard/secrets?page=0&size=10&onlyActive=true` | List user's secrets (paginated) | 100/hour |
| `GET` | `/api/dashboard/secrets/{id}/{limit}/logs` | Fetch access logs for secret | 50/hour |
| `DELETE` | `/api/dashboard/secrets/delete/{id}` | Soft-delete secret | 20/hour |

### Example Request
```bash
curl -X POST https://api.kieru-secure.vercel.app/api/secrets/create \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6..." \
  -H "Content-Type: application/json" \
  -d '{
    "content": "U2FsdGVkX1+vupppZksvRf5...",
    "secretName": "Database Credentials",
    "maxViews": 1,
    "expiresAt": 1735689600000,
    "password": "optional_pass123"
  }'
```

**Response:**
```json
{
  "secretId": "a8f3b2c1",
  "secretName": "Database Credentials",
  "expiresAt": 1735689600000,
  "maxViews": 1,
  "isSuccess": true,
  "viewTimeInSeconds": 120,
  "httpStatus": "CREATED"
}
```

---

## Rate Limiting

Implemented via custom `@RateLimit` annotation with AOP (Aspect-Oriented Programming).

**Features:**
- Fixed-window counter stored in Redis
- Automatic user lockout on abuse (configurable duration)
- Multiple scopes: `USER`, `IP`, `ANONYMOUS`, `GLOBAL`

**Implementation:**
```java
@RateLimit(type = RateLimitType.USER, requests = 10, windowSeconds = 86400)
public ResponseEntity<SecretMetadataResponseDTO> createSecret(...) { }
```

**Location:**
- Annotation: `src/main/java/com/kieru/backend/annotation/RateLimit.java`
- Aspect: `src/main/java/com/kieru/backend/aspect/RateLimitAspect.java`

---

## Background Jobs

| Job | Cron Schedule (UTC) | Purpose |
|-----|---------------------|---------|
| `DailyAnalyticsJob` | `0 30 0 * * *` | Aggregate daily usage metrics |
| `SecretCleanupJob` | `0 0 */6 * * *` | Expire secrets past TTL |
| `UserCleanupJob` | `0 30 1 * * *` / `0 35 1 * * *` | Remove inactive anonymous users |
| `SystemMaintenanceJob` | `0 0 2 * * *` | Database + Redis health checks |

---

## Environment Variables

Create a `.env` file in the project root:
```bash
# Application
APP_PROFILE=dev

# Database (PostgreSQL)
DB_URL=jdbc:postgresql://localhost:5432/kieru_db
DB_USER=postgres
DB_PASS=your_password
DB_DRIVER=org.postgresql.Driver
HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect
JPA_DDL=update
JPA_SHOW_SQL=false

# Redis
REDIS_URL=rediss://default:password@redis-host:6379
REDIS_PORT=6379
REDIS_SSL=true

# Firebase Authentication
FB_CREDS={"type":"service_account","project_id":"...","private_key":"..."}

# Logging
lOG_TOKEN=your_logtail_source_token
LOGTAIL_SOURCE_TOKEN=your_logtail_source_token
```

**Note:** `application.properties` maps `LOGTAIL_SOURCE_TOKEN` from `lOG_TOKEN`. Set both to the same value.

---

## Local Development

### Prerequisites

- Java 17+
- Maven 3.8+
- Docker (optional, for local databases)

### Setup
```bash
# 1. Clone repository
git clone https://github.com/imkrishnaaaaaaa/kieru-backend.git
cd kieru-backend

# 2. Configure environment
cp .env.example .env
# Edit .env with your credentials

# 3. Run application
./mvnw spring-boot:run
```

Application will start on `http://localhost:8080`

---

## Deployment

### Docker
```bash
# Build image
docker build -t kieru-backend .

# Run container
docker run -p 8080:8080 \
  --env-file .env \
  kieru-backend
```

### Docker Compose
```bash
docker-compose up -d
```

---

## Observability

**Structured Logging:**
- JSON format with MDC (Mapped Diagnostic Context)
- Integrated with Better Stack (Logtail) for centralized log management

**MDC Fields:**
- `userId` — Authenticated user ID
- `clientIp` — Request origin IP
- `secretId` — Secret being accessed
- `job` — Background job name
- `duration_ms` — Request processing time

**Configuration:** `src/main/resources/logback-spring.xml`

---

## Project Information

**Author:** [Murali Krishna Sana](https://github.com/imkrishnaaaaaaa)

**Contact:** [imkrishna1311@gmail.com](mailto:imkrishna1311@gmail.com)

**Repository:** [github.com/imkrishnaaaaaaa/kieru-backend](https://github.com/imkrishnaaaaaaa/kieru-backend)

**Live Application:** [kieru-secure.vercel.app](https://kieru-secure.vercel.app)

---

<div align="center">

### Report Issues or Suggest Features

Found a bug or have an idea? [Open an issue](https://github.com/imkrishnaaaaaaa/kieru-backend/issues)

---

**Built with Spring Boot • Secured with Zero-Knowledge Encryption**

© 2026 Kieru Secure. All rights reserved.

</div>
