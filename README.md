# Social Media Backend — Microservices Architecture
A production-grade social media backend built with Java 21, Spring Boot 3.5.15,
and a full microservices architecture. Designed to demonstrate advanced backend
engineering across database design, API development, fraud detection,
event-driven architecture, and distributed systems patterns.

## 📋 Table of Contents
- [Project Architecture](#project-architecture)
- [Services](#services)
- [Project Structure](#project-structure)
- [Quick Start Guide](#quick-start-guide)
- [Testing](#testing)

---
## Project Architecture
A **microservices architecture** with separate backend services

```
DevSocial Platform
│
└─Backend Services
    ├── User Service (Port 8081)
    ├── InterAction Service (Port 8082)
    ├── Fraud Service (Port 8083)
    └── 🚪 API Gateway (Port 8080)
```
---

## Services

### User Service - Port 8081
Owns the `users_db` PostgreSQL database. Responsible for user profile
management. The only service that writes to the users table.

**Endpoints:**
- `POST   /api/v1/users         Create a user profile`
- `GET    /api/v1/users/{id}    Get user by ID`
- `POST   /api/v1/users/bulk    Bulk insert profiles`

**Kafka:** Consumes `fraud.user.marked` — updates local fraud_status
column when a user is marked as fraud by the fraud service.
---

### Interaction Service — port 8082
Owns the `interaction_db` PostgreSQL database. Records all visits
and likes between users.

**Endpoints:**
- `POST  /api/v1/interactions/visit   Record a profile visit`
- `POST  /api/v1/interactions/like    Like a profile`
- `GET   /api/v1/interactions/{userId}/visitors  Get visitors of a profile`

**Kafka:**
- Produces `user.action` — published after every visit and like
- Consumes `fraud.user.marked` — populates local `blocked_users` table

---

### Fraud Service — port 8083
Owns the `fraud_db` PostgreSQL database and Redis counters.
Detects fraudulent behaviour based on interaction thresholds.

**Endpoints:**
- `GET   /api/v1/frauds/{userId}/status   Get fraud status`
- `POST  /api/v1/frauds/{userId}/mark     Manually mark as fraud (admin)`

**Kafka:**
- Consumes `user.action` — evaluates each action against threshold
- Produces `fraud.user.marked` — published when fraud is detected

---

### API Gateway — port 8080
Single entry point for all client requests. Routes to downstream
services, enforces fraud checking, and handles circuit breaking.

**Routes:**
- `/api/v1/users/**         → user-service:8081`
- `/api/v1/interactions/**  → interaction-service:8082`
- `/api/v1/frauds/**         → fraud-service:8083`

---

## Project Structure
```
social-media-app/
├── pom.xml                     ← parent — dependency management only
├── docker-compose.yml          ← all infrastructure
├── README.md
├── ARCHITECTURE.md
├── .gitignore
├── user-service/               ← port 8081, users_db
│   ├── src/main/java/com/meet5/userservice/
│   │   ├── config/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/         ← NamedParameterJdbcTemplate + COPY
│   │   ├── domain/
│   │   ├── dto/
│   │   ├── exception/
│   │   ├── util/
│   │   └── kafka/              ← FraudEventConsumer
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/
│   └── pom.xml
├── interaction-service/        ← port 8082, interaction_db
│   ├── src/main/java/com/meet5/interactionservice/
│   │   ├── config/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── dto/
│   │   ├── exception/
│   │   └── kafka/              ← FraudEventConsumer + ActionEventPublisher
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/
│   └── pom.xml
├── fraud-service/              ← port 8083, fraud_db + Redis
│   ├── src/main/java/com/meet5/fraudservice/
│   │   ├── config/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── domain/
│   │   ├── dto/
│   │   ├── config/
│   │   ├── exception/
│   │   └── kafka/              ← ActionEventConsumer
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/
│   └── pom.xml
└── api-gateway/                ← port 8080
    ├── src/main/java/com/meet5/apigateway/
    │   ├── controller/         ← FallbackController
    │   ├── filter/             ← FraudCheckFilter + RequestLoggingFilter
    │   └── config/             ← WebClientConfig + CorsConfig
    ├── src/main/resources/
    │   ├── application.yml
    └── pom.xml
```
---
## Quick Start Guide

### Prerequisites
- Java 21
- Maven 3.9+
- Docker Desktop

### Start infrastructure
```bash
# Clone the repository
git clone https://github.com/aneebhalerao/social-media-app.git
cd social-media-app

# Start all containers
docker-compose up -d

# Verify all containers are running:
docker ps

# Expected Containers
user_postgres        :5432
interaction_postgres :5433
fraud_postgres       :5434
redis                :6379
zookeeper            :2181
kafka                :9092
kafka_ui             :8090
```

### Start Services
Start in this order — user-service and interaction-service
can start in any order, but fraud-service should start before
sending interactions so fraud detection is active.
```bash
# Terminal 1
cd user-service && mvn spring-boot:run

# Terminal 2
cd interaction-service && mvn spring-boot:run

# Terminal 3
cd fraud-service && mvn spring-boot:run

# Terminal 4
cd api-gateway && mvn spring-boot:run
```
### Swagger UI

| Service | Direct | Via Gateway |
|---|---|---|
| All services aggregated | — | http://localhost:8080/swagger-ui.html |
| user-service | http://localhost:8081/swagger-ui.html | — |
| interaction-service | http://localhost:8082/swagger-ui.html | — |
| fraud-service | http://localhost:8083/swagger-ui.html | — |

### Kafka UI
http://localhost:8090 — browse topics, messages, consumer groups


--- 

## Testing

### Testing the Fraud Detection Flow
```bash
# 1. Create a user
curl -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Test User","username":"testuser1","age":25}'

# Note the userId from the response

# 2. Create a second user to be the target
curl -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Target User","username":"target1","age":25}'

# 3. Send 100 likes from user1 to different targets
# In a loop — or via Swagger UI

# 4. Check fraud status
curl http://localhost:8080/api/v1/frauds/{userId}/status

# Expected after 100 actions:
# {"status":"FRAUD","blocked":true}

# 5. Try another action — should be blocked
curl -X POST http://localhost:8080/api/v1/interactions/like \
  -H "Content-Type: application/json" \
  -H "X-User-Id: {userId}" \
  -d '{"likerId":"{userId}","likedId":"{targetId}"}'

# Expected: 403 FRAUD_BLOCKED
```
### Running Tests
```bash
# All tests
mvn test

# Single service
mvn test -pl user-service
mvn test -pl interaction-service
mvn test -pl fraud-service
```
**Three test layers:**
- Unit — Mockito, no Spring context, tests business logic in isolation
- Controller slice — `@WebMvcTest`, tests HTTP layer with mocked service
- Integration — Testcontainers, real PostgreSQL, tests SQL and Flyway
---




