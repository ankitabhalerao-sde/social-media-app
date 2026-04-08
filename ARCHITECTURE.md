# Social Media App - Microservices Architecture

## 🏗️ System Architecture Overview
The system is built as a set of independently deployable microservices, each owning its own data store and communicating through HTTP or Apache Kafka. 

---

## 📋 Table of Contents
- [Teck Stack](#tech-stack)
- [Port Map](#port-map)
- [Implementation status dashboard](#implementation-status-dashboard)
- [Database Design](#database-design)
- [Fraud Detection](#fraud-detection)
- [API Design & Versioning](#api-design-and-versioning)
- [Event Driven Architecture](#event-driven-architecture)
- [Bulk Insert Strategy](#bulk-insert-strategy)
- [Circuit Breaking](#circuit-breaking)
- [Key Design Decisions](#key-design-decisions)
- ## Tech Stack

| Technology           | Version | Why                                                            |
|----------------------|---------|----------------------------------------------------------------|
| Java                 | 21      | Virtual threads (Project Loom) for high-concurrency I/O        |
| Spring Boot          | 3.5.15  | Production-ready auto-configuration, latest stable             |
| Spring Cloud Gateway | 4.3.3   | Reactive API gateway with built-in circuit breaking            |
| PostgreSQL           | 16      | JSONB for flexible user attributes, GIN indexes, COPY protocol |
| Apache Kafka         | 7.6     | Async event streaming for fraud propagation                    |
| Redis                | 7.2     | In-memory action counters for fraud detection                  |
| Flyway               | 11.7.2  | Versioned SQL migrations, one per service                      |
| Resilience4j         | 3.3.1   | Circuit breaker, rate limiter, timeout                         |
| SpringDoc OpenAPI    | 2.8.8   | Swagger UI aggregated at gateway as well as indivisual         |
| Testcontainers       | 1.21.4  | Real database integration tests                                |
| Lombok               | Latest  | Reduces boilerplate on domain models                           |

```

┌────────────────────────────────────────────────────────────────────────────────┐
│                              Client Layer                                      │
│                     Browser / Mobile App / API Consumer                        │
└───────────────────────────────────┬────────────────────────────────────────────┘
                              HTTPS/REST API
                                    │
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              API GATEWAY                                        │
│                              (Port 8080)                                        │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │ • Request Routing • Fraud Check Filter • CORS Handling • Circuit Breaker│    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────────┘
                                    │
                              Internal Network
                                    │
┌────────────────────────────────────────────────────────────────────────────────┐
│                          MICROSERVICES LAYER                                   │
├────────────────┬─────────────────────────────────┬─────────────────────────────┤
│User Service    │Interaction Service              │   Fraud Service             │
│(Port 8081)     │(Port 8082)                      │           (Port 8083)       │
│user_db         │interaction_db                   │  Fraud_db                   │  
│PostgreSQL:5432 │  PostgreSQL:5433                │  PostgreSQL:5434            │                         
│✅COMPLETE      │ ✅COMPLETE                     │  ✅COMPLETE                │ 
│                │                                 │                             │
│• Profiles      │• Visit User/Profile             │  • Detect Fraud             │
│                │• Like User/Profile              │  • Redis Counter            │
│                │• Profile Summary                │  • Fraud Event Log          │
│                │• Block User                     │                             │
┴────────┬───────┴─────────────────┬───────────────┴────────────────────┬────────┘
         │                         │                                    │
         └─────────────────────────┼────────────────────────────────────┘
                                   │
                   ┌───────────────▼───────────────┐
                   │       Apache Kafka  :9092     │
                   │      user.action              │
                   │      fraud.user.marked        │
                   └───────────────┬───────────────┘
                                   │
                   ┌───────────────▼───────────────┐
                   │         Redis  :6379          │
                   │   Fraud action counters       │
                   └───────────────────────────────┘
```

### Port Map
| Component            | Port |
|----------------------|------|
| API Gateway          | 8080 |
| User Service         | 8081 |
| Interaction Service  | 8082 |
| Fraud Service        | 8083 |
| user-postgres        | 5432 |
| interaction-postgres | 5433 |
| fraud-postgres       | 5434 |
| Redis                | 6379 |
| Kafka                | 9092 |
| Kafka UI             | 8090 |

## Implementation Status Dashboard
Each service boundary is drawn around a distinct business capability.
The test is: can this service be deployed, scaled, and failed
independently without affecting the others?

```
user-service        — "I own user identity"
interaction-service — "I own social interactions between users (like and visit)"
fraud-service       — "I own fraud determination"
api-gateway         — "I own the front door"
```

**Project Foundation & Architecture:**

Owns: Multi-module Maven project structure, Parent POM with dependency management, Common module with shared utilities, Docker Compose configuration

**User Service:**

Owns: User profiles, local fraud status copy

Responsibility: Single source of truth for user identity.
No other service writes to the users table.

**Why it holds a local fraud_status column:**
User service needs to answer "what is this user's status?"
without calling fraud-service. The column is kept in sync
via Kafka consumption of `fraud.user.marked`. This is a
deliberate denormalisation for read performance.

**Interaction Service:**

Owns: Profile visits, profile likes, block fraud user, local users list

Responsibility: Records all interactions between users.
Does not call user-service to validate users before
recording — this avoids a synchronous dependency on every
hot path.

**Why it holds a blocked_users table:**
Fraud state must be enforced locally. If interaction-service called fraud-service on every request, a fraud-service outage would make all user interactions fail. The local table is populated via Kafka and is the check point for every write operation.

**Fraud Service:**

Owns: Fraud status, fraud event audit log, Redis counters

Responsibility: The only service that makes fraud determinations. Publishes the result — does not push it
to other services directly.

**API Gateway:**

Owns: Nothing

Responsibility: Single entry point. Routes requests, enforces fraud check on write operations via the
`FraudCheckFilter`, and handles circuit breaking.

---

## Database Design
### Why database per service
Each microservice owns its own PostgreSQL instance. No service can query another service's database directly. Cross-service data needs are satisfied through HTTP calls or Kafka events.

This enforces true service isolation — a schema change in user-service never requires a deployment of interaction-service.

### users table (user-service)
```sql
CREATE TABLE users (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(100) NOT NULL,
    username     VARCHAR(50)  NOT NULL UNIQUE,
    age          SMALLINT     NOT NULL CHECK (age >= 16 AND age <= 120),
    status       VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    extra_fields JSONB        NOT NULL DEFAULT '{}',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```
**Key decisions:**
- `extra_fields JSONB` — The alternative is an Entity-Attribute-Value (EAV) table with one row per field. EAV has three critical problems: every user fetch requires a JOIN, all values are forced into VARCHAR losing type information, and arrays or nested objects are impossible. JSONB stores the entire map in one column with native JSON types, supports a GIN index for efficient key/value queries, and requires no JOIN.
- `status` — This is a denormalised copy of the fraud status owned byfraud-service. It exists here so user-service can answerprofile requests without calling fraud-service. Updatedasynchronously via Kafka — eventual consistency is acceptable for a profile read use case. Avoids cross-service queries at read time.

### profile_visits table (interaction-service)
```sql
CREATE TABLE profile_visits (
    id               UUID    PRIMARY KEY,
    visitor_id       UUID    NOT NULL,
    visited_id       UUID    NOT NULL,
    visit_count      INTEGER NOT NULL DEFAULT 1,
    first_visited_at TIMESTAMPTZ,
    last_visited_at  TIMESTAMPTZ,
    UNIQUE (visitor_id, visited_id)
);
```
**Key decision:** One row per visitor/visited pair with a counter.`ON CONFLICT DO UPDATE` increments the count atomically in a single upsert instead of read-then-write (2 queries → 1 query).

### Visitors query — sort strategy
```sql
SELECT visitor_id, visit_count, first_visited_at, last_visited_at
FROM profile_visits
WHERE visited_id = :userId
ORDER BY last_visited_at DESC
LIMIT :limit OFFSET :offset
```
Sorted by `last_visited_at DESC` first — most recent visitors appear at the top, which is the most relevant ordering for the profile owner. The compound index on `(visited_id, last_visited_at DESC)`allows PostgreSQL to satisfy the WHERE clause and ORDER BY from the index without touching the heap — an index-only scan regardless of table size.

### profile_likes (interaction-service)

```sql
CREATE TABLE profile_likes (
    id       UUID        PRIMARY KEY,
    liker_id UUID        NOT NULL,
    liked_id UUID        NOT NULL,
    liked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_like_pair UNIQUE (liker_id, liked_id)
);
```

The UNIQUE constraint on `(liker_id, liked_id)` combined with `ON CONFLICT DO NOTHING` makes the like operation idempotent at the database level — no application-level duplicate check needed.

### blocked_users (interaction-service)

```sql
CREATE TABLE blocked_users (
    user_id    UUID        PRIMARY KEY,
    blocked_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

Local read model populated by Kafka. Primary key on `user_id` makes the fraud check a single primary keylookup — the fastest possible query.

### fraud_status and fraud_events (fraud-service)

```sql
CREATE TABLE fraud_status (
    user_id    UUID        PRIMARY KEY,
    status     VARCHAR(10) NOT NULL DEFAULT 'CLEAN',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Append-only audit log — never updated or deleted
CREATE TABLE fraud_events (
    id           UUID        PRIMARY KEY,
    user_id      UUID        NOT NULL,
    reason       VARCHAR(100) NOT NULL,
    action_count INTEGER     NOT NULL,
    detected_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

`fraud_status` is the current state, one row per user, upserted on change.
`fraud_events` is the immutable audittrail — one row per detection event, append only. Separating current state from audit history keeps both tables focusedand independently queryable.
---

## Fraud Detection

### Algorithm
After every visit or like, interaction-service publishes a
`user.action` Kafka event. Fraud-service consumes this event and evaluates the actor:
```
1. Check if userId is already FRAUD in Redis-backed status
   → if yes: skip (already handled)

2. Increment Redis counter
   INCR fraud:actions:{userId}

3. Set TTL on first increment only
   EXPIRE fraud:actions:{userId} 600   (10 minutes)

4. If counter >= 100:
   → markAsFraud(userId)
```

### Why Redis for counting
Redis `INCR` is atomic and in-memory — microsecond latency. No database query per action. The TTL on the key creates a natural rolling window — actions older than 10 minutes fall off automatically when the key expires.

Compare to the naive approach:
Naive:  COUNT(*) FROM interactions WHERE user_id = ? 
        AND created_at >= NOW() - INTERVAL '10 minutes'
DB query on EVERY action

Redis:  INCR fraud:actions:{userId} In-memory, no DB, microseconds

### Fraud state propagation — eventual consistency

This is the most architecturally significant part of the system.

**The problem:**
When fraud-service marks a user as FRAUD, three other components need to know: interaction-service (to block actions), user-service (to update local status), and api-gateway (to reject requests before they reach any service).

**The wrong approach — synchronous HTTP:**
fraud-service → HTTP → interaction-service.blockUser()
fraud-service → HTTP → user-service.updateStatus()

This creates tight coupling. If interaction-service is down, the fraud marking fails. The fraud-service now depends on the availability of every other service.

## API Design and Versioning

### Versioning strategy

All endpoints are prefixed with `/api/v1/`. The version is in the URL path for three reasons:

1. **Gateway compatibility** — routes can target specific versions independently without header inspection
2. **Visibility** — version is obvious in logs, browser history, and documentation
3. **Simplicity** — no custom headers or content negotiation

When a breaking change is needed, `/api/v2/` endpoints are added while `/api/v1/` remains active during a migration period. The gateway routes both versions independently allowing gradual client migration.

### Endpoint overview

```
User Service        :8081
  POST   /api/v1/users              Create user profile
  GET    /api/v1/users/{id}         Get user by ID
  POST   /api/v1/users/bulk         Bulk insert up to 10,000 profiles

Interaction Service :8082
  POST   /api/v1/interactions/visit              Record profile visit
  POST   /api/v1/interactions/like               Like a profile
  GET    /api/v1/interactions/{userId}/visitors  Get visitors of a profile

Fraud Service       :8083
  GET    /api/v1/frauds/{userId}/status  Get fraud status
  POST   /api/v1/frauds/{userId}/mark    Manually mark as fraud (admin)
```

### Gateway routing

The gateway routes by path prefix. No service knows about the others — the gateway is the only component aware of the full service topology.

```
/api/v1/users/**         → user-service:8081
/api/v1/interactions/**  → interaction-service:8082
/api/v1/fraud/**         → fraud-service:8083
```

## Event-Driven Architecture
### Kafka topics

| Topic              | Producer            | Consumers                                    |
|--------------------|---------------------|----------------------------------------------|
| `user.action`      | interaction-service | fraud-service                                |
| `fraud.user.marked`| fraud-service       | interaction-service, user-service            |

---

### Why Kafka over synchronous HTTP

**Scenario:** After recording a visit, interaction-service needs to tell fraud-service to evaluate the user.

**Synchronous HTTP approach — rejected:**
```
interaction-service → HTTP POST → fraud-service/evaluate
```
Problems:
- interaction-service response time depends on fraud-service
- If fraud-service is slow, visits become slow
- If fraud-service is down, visits fail
- Tight coupling — interaction-service must know fraud-service's URL

**Kafka approach — chosen:**
```
interaction-service → publishes user.action → returns 201 immediately
fraud-service       → consumes user.action  → evaluates asynchronously
```
Benefits:
- Visit API response time is not affected by fraud evaluation
- Fraud-service can be down and restart later — events replay
- interaction-service has no runtime dependency on fraud-service
- New consumers (analytics, monitoring) can subscribe
  without changing the producer

### Serialisation strategy

Producers send without type headers:
```yaml
spring.json.add.type.headers: false
```

Consumers declare the default target type explicitly:
```yaml
spring.json.value.default.type: com.meet5.fraudservice.dto.ActionEvent
```

This avoids sharing classes between services — each service deserialises into its own local record type.Services are decoupled at the class level.

## Bulk Insert Strategy

Two strategies are used based on batch size, routing in `UserService.bulkCreateUsers`:

```
batch size ≤ 500  →  JDBC batchUpdate
batch size > 500  →  PostgreSQL COPY
```

### JDBC batchUpdate (≤ 500 rows)

```java
namedJdbc.batchUpdate(sql, batchParams);
```

All insert statements are sent to the PostgreSQL driver in a single network round-trip. The driver groups them into one packet. Spring manages the transaction.Supports `ON CONFLICT DO NOTHING` natively.

### PostgreSQL COPY (> 500 rows)

```java
PGConnection pgConn = conn.unwrap(PGConnection.class);
CopyManager copyManager = pgConn.getCopyAPI();
copyManager.copyIn(copySql, new StringReader(csv.toString()));
```

Streams data directly into the PostgreSQL storage layer. Bypasses per-row SQL parsing, query planning, and
execution overhead. Typical throughput: 50,000 to 200,000 rows per second versus ~5,000 rows per second for JDBC batch.

**Staging table pattern:**
PostgreSQL COPY does not support `ON CONFLICT` directly. The solution is to COPY into a plain-text staging table, then INSERT with conflict handling:

```sql
-- 1. COPY into staging (all TEXT columns — no type mismatch)
CREATE TEMP TABLE users_staging (id TEXT, name TEXT, ...) ON COMMIT DELETE ROWS;
COPY users_staging FROM STDIN WITH (FORMAT csv);

-- 2. INSERT from staging with type casts and conflict handling
INSERT INTO users SELECT id::uuid, name, age::smallint, extra_fields::jsonb, ...
FROM users_staging
ON CONFLICT (username) DO NOTHING;
```

**Connection management:**
PostgreSQL COPY requires direct access to the physical connection. HikariCP wraps connections in a proxy so `conn.unwrap(PGConnection.class)` is required to reach the driver-specific API.

`DataSourceUtils.releaseConnection()` is used instead of `conn.close()` — this returns the connection to the HikariCP pool and respects the active Spring transaction rather than closing the physical connection.

---

## Circuit Breaking

Resilience4j circuit breakers wrap all downstream service calls from the gateway.

### State machine

```
         failure rate > 50%
CLOSED ─────────────────────► OPEN
  ▲                              │
  │   test calls succeed         │ wait 10 seconds
  │                              ▼
HALF_OPEN ◄────────────────── OPEN
         some calls pass through
```

- **CLOSED** — normal operation, requests pass through
- **OPEN** — service considered down, requests go to
  fallback controller immediately, returns 503
- **HALF_OPEN** — recovery probe, limited calls pass
  through to test if service recovered

### Configuration

```yaml
resilience4j:
  circuitbreaker:
    instances:
      user-service:
        slidingWindowSize: 10           # evaluate last 10 calls
        minimumNumberOfCalls: 5         # minimum calls before evaluating
        failureRateThreshold: 50        # open if 50% fail
        waitDurationInOpenState: 10s    # wait before half-open
        permittedNumberOfCallsInHalfOpenState: 3
```

### Fraud check — fail open policy

The `FraudCheckFilter` fails **open** when fraud-service is unreachable:

```java
.onErrorResume(ex -> {
    log.error("Fraud service unreachable — failing open");
    return chain.filter(exchange);  // allow request through
});
```

**Why fail open:**
Fail closed (block all requests when fraud-service is down) means a fraud-service outage blocks every write operation for every user on the platform including legitimate users.
The business impact is far greater than the risk of a fraud user completing actions during a brief outage window.
Additionally, most fraud users are already blocked at the interaction-service level via the local `blocked_users`
table, which remains functional regardless of fraud-service availability.

---

## Key Design Decisions
- `NamedParameterJdbcTemplate` was chosen over `JdbcClient` because `JdbcClient` has no native `batchUpdate` support.  Mixing `JdbcClient` for single operations and constructing  a `NamedParameterJdbcTemplate` inside methods for batch  operations is inconsistent. Committing to `NamedParameterJdbcTemplate` throughout gives a single consistent API that handles all cases.
- JSONB over EAV for extra_fields
The Entity-Attribute-Value pattern (separate table with field_key / field_value rows) was explicitly evaluated and rejected for these reasons:

     | Concern          | EAV                                  | JSONB                        |
     |------------------|--------------------------------------|------------------------------|
     | Fetch complexity | JOIN required on every user fetch    | Single column, no JOIN       |
     | Value types      | Everything forced to VARCHAR         | Native JSON types            |
     | Arrays / objects | Impossible without custom encoding   | Native support               |
     | Value length     | Hard-capped at VARCHAR limit         | Up to 255MB                  |
     | Querying         | WHERE field_key = 'x' AND field_value| WHERE extra_fields @> '{...}'|
     | Indexing         | Index per field or full scan         | Single GIN index covers all  |
- Virtual threads (Project Loom)
All services enable virtual threads:
```yaml
spring:
  threads:
    virtual:
      enabled: true
```
Spring Boot's Tomcat thread pool uses virtual threads instead of platform threads. For I/O-bound workloads (database calls, Kafka, Redis) virtual threads allow thousands of concurrent requests with minimal memory overhead compared to a traditional thread-per-request model.
- Test architecture — three layers

| Layer       | Tool           | What it verifies                               |
|-------------|----------------|------------------------------------------------|
| Unit        | Mockito        | Business logic, edge cases, no infrastructure  |
| Controller  | @WebMvcTest    | HTTP status codes, validation, error responses |
| Integration | Testcontainers | SQL correctness, Flyway migrations, constraints|

Integration tests use a shared PostgreSQL container per service — not one container per test class — to minimise
container startup overhead while maintaining full isolation between test runs using `@Transactional` rollback.










