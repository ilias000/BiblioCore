# BiblioCore Library Management API

A production-grade RESTful API for managing a public library network, built with Java 25 and Spring Boot 4.

---

## Table of Contents
1. [Quick Start](#quick-start)
2. [Features Overview](#features-overview)
3. [API Reference](#api-reference)
4. [Design Decisions & Rationale](#design-decisions--rationale)
5. [Configuration](#configuration)
6. [Testing](#testing)
7. [Limitations & What I Would Do Differently](#limitations--what-i-would-do-differently)
8. [Future Enhancements](#future-enhancements)

---

## Quick Start

### Prerequisites
- Java 25 (JDK)
- Maven 3.9+ (or use included Maven Wrapper)

### Run Locally (Zero Dependencies)
```bash
# Clone and run - uses H2 in-memory database
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`

### Default Admin Account

A default admin user is automatically created on startup (for `local-h2` and `docker-pg` profiles):

| Field | Value |
|-------|-------|
| Email | `admin@bibliocore.com` |
| Password | `Admin123!` |
| Role | `ROLE_ADMIN` |

### Access Points
| Resource | URL |
|----------|-----|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI Spec | http://localhost:8080/v3/api-docs |
| H2 Console | http://localhost:8080/h2-console |
| Health Check | http://localhost:8080/actuator/health |

**H2 Console Connection**: JDBC URL: `jdbc:h2:mem:biblioCoreDB`, user: `sa`, password: (empty)

### Run with Docker (PostgreSQL)
```bash
docker compose up --build
```

### Postman Collection

A complete Postman collection is included for API testing:

```
postman/
├── BiblioCore_API.postman_collection.json    # All endpoints with sample requests
├── BiblioCore_Local.postman_environment.json # Environment for local H2
├── BiblioCore_Docker.postman_environment.json # Environment for Docker
└── README.md                                  # Usage instructions
```

**Quick setup:**
1. Import all files into Postman
2. Select the appropriate environment
3. Run "Login as Admin" to get a token (auto-saved to environment)
4. All admin endpoints will use the token automatically

See `postman/README.md` for detailed usage instructions.

---

## Features Overview

### Core Functionality (MUST Requirements)

| Feature | Implementation Details |
|---------|----------------------|
| **Book Catalog CRUD** | Full lifecycle with soft-delete. ISBN uniqueness enforced at database level. Authors stored separately with many-to-many relationship for normalization. |
| **Member Management** | Three-state lifecycle (ACTIVE/SUSPENDED/EXPIRED). Configurable loan limits per member. Email uniqueness enforced. |
| **Loan Lifecycle** | Borrow reduces available copies atomically. Return increments copies and calculates fines. 14-day default loan period. |
| **JWT Authentication** | Spring OAuth2 Resource Server with HMAC-SHA256 signed tokens. Role-based access (ROLE_MEMBER, ROLE_ADMIN). |
| **Search & Pagination** | JPA Specifications for dynamic filtering. Supports title (partial), author (partial), genre (exact), availability. Sortable by title or publication date. |
| **Optimistic Locking** | `@Version` annotation prevents concurrent modification. Critical for preventing negative available copies during high-traffic borrow operations. |

### Bonus Features

| Feature | Implementation Details |
|---------|----------------------|
| **Overdue Detection** | Scheduled job runs at configurable cron interval. Bulk updates loan status to OVERDUE. Each transition logged in audit trail. |
| **Fine Calculation** | Computed on return using `BigDecimal` for precision. Rate configurable via properties. |
| **Audit Trail** | Immutable append-only log capturing entity type, ID, action, field name, old/new values, performer, and timestamp. |
| **Waitlist System** | FIFO queue per book. When copy returned, first waiting member notified via Spring Events. |

---

## API Reference

### Authentication Endpoints
```
POST /api/v1/auth/register    # Register new member account
POST /api/v1/auth/login       # Authenticate and receive JWT token
```

### Book Endpoints
```
GET    /api/v1/books                    # Search books (public)
       ?title=...&author=...&genre=...&available=true
       &page=0&size=20&sort=title,asc

GET    /api/v1/books/{id}               # Get by ID (public)
GET    /api/v1/books/isbn/{isbn}        # Get by ISBN (public)
POST   /api/v1/books                    # Create book (admin only)
PUT    /api/v1/books/{id}               # Update book (admin only)
DELETE /api/v1/books/{id}               # Soft-delete book (admin only)
```

### Loan Endpoints
```
POST   /api/v1/loans/borrow             # Borrow book (member)
POST   /api/v1/loans/{id}/return        # Return book (member)
GET    /api/v1/loans/my-loans           # Own loan history (member)
GET    /api/v1/loans/my-loans/active    # Own active loans (member)
GET    /api/v1/loans/{id}               # Loan details (owner or admin)
```

### Waitlist Endpoints
```
POST   /api/v1/waitlist/books/{bookId}  # Join waitlist (member)
DELETE /api/v1/waitlist/books/{bookId}  # Leave waitlist (member)
GET    /api/v1/waitlist/my-waitlist     # Own waitlist entries (member)
```

### Admin Endpoints
```
GET    /api/v1/admin/members                        # List all members
GET    /api/v1/admin/members/{id}                   # Member details
PUT    /api/v1/admin/members/{id}                   # Update member
PATCH  /api/v1/admin/members/{id}/suspend           # Suspend member
PATCH  /api/v1/admin/members/{id}/activate          # Activate member
GET    /api/v1/admin/loans                          # List all loans
GET    /api/v1/admin/loans/overdue                  # List overdue loans
GET    /api/v1/admin/loans/member/{memberId}        # Member's loans
POST   /api/v1/admin/loans/detect-overdue           # Manual overdue scan
GET    /api/v1/admin/waitlist/book/{bookId}         # Book's waitlist
GET    /api/v1/admin/audit/{entityType}/{entityId}  # Entity audit log
```

---

## Design Decisions & Rationale

### 1. Layered Architecture Over Hexagonal

I chose the classic Controller → Service → Repository layering pattern instead of Hexagonal or Clean Architecture.

**My reasoning:**
- For an application of this scope, introducing ports and adapters would be over-engineering. The additional abstraction layers would add boilerplate without proportional benefit.
- Spring Boot is fundamentally designed around this pattern. Fighting the framework leads to awkward workarounds.

**Where I prepared for change:**
- Services depend on repository interfaces, not implementations
- JWT handling is decoupled into `JwtTokenProvider` - swapping to an external OAuth2 provider requires configuring `issuer-uri` instead of local token generation
- MapStruct interfaces abstract the mapping logic, making DTO structure changes isolated

### 2. JPA Auto-Generation Instead of Flyway

I used `spring.jpa.hibernate.ddl-auto=create-drop` for H2 and `update` for PostgreSQL instead of Flyway migrations.

**What I would do in production:**
Flyway or Liquibase. Here's why:
- **Version control for schema**: Every DDL change tracked in git with rollback capability
- **Safe deployments**: Flyway validates migrations before applying them
- **Data migrations**: Schema changes often require data transformations (e.g., splitting a name column into first/last)
- **Team workflow**: Schema changes go through code review like any other code

### 3. Strict Entity-DTO Separation

I never expose JPA entities to the REST layer. Every endpoint uses dedicated Request/Response DTOs with MapStruct handling the translation.

**Why:**
- **Security**: An entity might have fields like `passwordHash` or `version` that should never be serialized. Exposing entities means remembering to add `@JsonIgnore` everywhere - risking security incidents.
- **API stability**: I can refactor my database schema without breaking API contracts. The DTO is my promise to clients.
- **Validation boundary**: Request DTOs carry `@Valid` constraints. Entities carry persistence constraints. These are different concerns.

**Implementation detail:**
I used Java records for DTOs because they're immutable by design and have clean serialization behavior. MapStruct generates Spring beans, so I can inject mappers anywhere with zero runtime reflection overhead.

### 4. JWT via Spring OAuth2 Resource Server

I built JWT authentication using Spring's OAuth2 Resource Server with self-signed tokens rather than a third-party library like jjwt.

**Why:**
- I'm using `NimbusJwtEncoder` to issue tokens locally, but the decoder can validate tokens from any issuer. To migrate to Keycloak or Auth0, I would:
  1. Remove the encoder (tokens issued externally)
  2. Configure `spring.security.oauth2.resourceserver.jwt.issuer-uri`
  3. Done - the rest of the security layer remains unchanged

**Token structure:**
```json
{
  "iss": "biblioCore-api",
  "sub": "user@email.com",
  "userId": 123,
  "role": "ROLE_MEMBER",
  "iat": 1234567890,
  "exp": 1234571490
}
```

The `role` claim is extracted by a custom `JwtAuthenticationConverter` to populate Spring Security's granted authorities.

### 5. Optimistic Locking for Concurrent Borrows

The requirement explicitly mentions handling the race condition where two users try to borrow the last copy simultaneously. I solved this with optimistic locking.

**The problem:**
1. User A reads book: `availableCopies = 1`
2. User B reads book: `availableCopies = 1`
3. User A decrements and saves: `availableCopies = 0`
4. User B decrements and saves: `availableCopies = -1` ← Invalid state

**My solution:**
- Book entity has a `@Version` field
- `BookRepository.findByIdWithLock()` uses `@Lock(LockModeType.OPTIMISTIC)`
- When User B tries to save, Hibernate detects the version mismatch and throws `ObjectOptimisticLockingFailureException`
- My exception handler converts this to a 409 Conflict response
- The client can retry (User B will now see `availableCopies = 0` and get a business error)

**Why optimistic over pessimistic?**
- Most borrow operations don't conflict - pessimistic locking would add unnecessary contention
- Pessimistic locks can cause deadlocks in complex transactions
- Optimistic locking scales better for read-heavy workloads

### 6. BigDecimal for All Monetary Values

Every fine calculation uses `java.math.BigDecimal` with explicit scale and rounding.
- `LoanProperties.finePerDay()` returns `BigDecimal`
- `Loan.fineAmount` is stored as `DECIMAL(10,2)` in the database
- All arithmetic uses `BigDecimal` methods with explicit rounding

### 7. Soft Delete with Active Loan Protection

When a book is "deleted," I set `deleted = true` rather than removing the row.

**Why soft delete:**
- Historical loans reference book IDs. Hard delete would either violate foreign keys or cascade-delete loan history.
- Audit trail shows what book was borrowed, even after deletion.
- Undo is trivial: set `deleted = false`.

**The active loan guard:**
An active loan guard prevents admins from accidentally deleting books with active loans.

### 8. Immutable Audit Trail

I implemented a comprehensive audit trail that records all state changes to loans and member status.

**Schema design:**
```
| Column       | Purpose                              |
|--------------|--------------------------------------|
| entity_type  | "LOAN", "MEMBER", "BOOK", "WAITLIST" |
| entity_id    | ID of affected entity                |
| action       | "CREATED", "UPDATED", "DELETED"      |
| field_name   | Specific field changed (nullable)    |
| old_value    | Previous value (nullable)            |
| new_value    | New value (nullable)                 |
| performed_by | User ID who made change              |
| performed_at | Timestamp (auto-generated)           |
```

**Key implementation decisions:**
- `REQUIRES_NEW` propagation ensures audit writes succeed even if the parent transaction fails
- No UPDATE or DELETE operations on the audit table - it's append-only
- `performed_by` is nullable to allow system-triggered changes (scheduled jobs)

### 9. Event-Driven Waitlist Notifications

When a book becomes available, I notify the next person in the waitlist using Spring's application event system.

**Why events instead of direct notification:**
- Decoupling: The return operation doesn't know about notification mechanisms
- Extensibility: I can add email, push, SMS handlers without modifying the return logic
- Testability: I can verify events are published without testing email delivery

**Current implementation:**
Currently, my publisher just logs to console. In production, I would add listeners for email/push.

### 10. Waitlist Rejoin Behavior

Members can rejoin the waitlist after cancelling or being notified (but not borrowing).

**Design decision:**
When a member rejoins, they go to the **end of the queue** rather than retaining their original position.

| Previous Status | Can Rejoin? | Queue Position |
|-----------------|-------------|----------------|
| `WAITING` | No (already on waitlist) | N/A |
| `CANCELLED` | Yes | End of queue |
| `NOTIFIED` | Yes | End of queue |

**Implementation:**
- `WaitlistEntry` has a `queuedAt` timestamp (separate from `createdAt`)
- On rejoin, `queuedAt` is updated to current time
- Position is calculated by ordering on `queuedAt`
- The unique constraint `(member_id, book_id)` is preserved - we reactivate the existing entry rather than creating a new one

**Why end of queue?**
- Prevents gaming the system (cancel/rejoin to hold a spot indefinitely)
- Fair to other waiting members

---

## Configuration

### Application Properties

| Property | Default | Description |
|----------|---------|-------------|
| `bibliocore.loan.default-limit` | `3` | Maximum concurrent active loans per member |
| `bibliocore.loan.period-days` | `14` | Default loan duration in days |
| `bibliocore.loan.fine-per-day` | `0.20` | Fine amount (EUR) per day overdue |
| `bibliocore.loan.overdue-scan-cron` | `0 0 1 * * *` | Cron for overdue detection (1 AM daily) |
| `bibliocore.security.jwt.access-token-ttl` | `60` | JWT token validity (minutes) |
| `bibliocore.security.jwt.secret` | (required) | HMAC-SHA256 signing key (min 32 bytes) |

### Profiles

| Profile | Database | Use Case |
|---------|----------|----------|
| `local-h2` (default) | H2 in-memory | Local development, zero setup |
| `docker-pg` | PostgreSQL | Docker deployment, production-like |

---

## Testing

```bash
# Run all tests (unit + integration)
./mvnw clean verify

# Run only unit tests
./mvnw test

# Run only integration tests
./mvnw failsafe:integration-test
```

### Test Strategy

| Layer | Scope |
|-------|-------|
| Unit Tests | Service business logic with mocked repositories |
| Integration Tests | Full API lifecycle against H2 database |

### Key Scenarios Tested

- Complete borrow → return lifecycle
- Fine calculation for overdue returns
- Loan limit enforcement
- Duplicate borrow rejection
- Suspended member cannot borrow
- Soft-delete with active loan protection
- Authorization: public, member, admin endpoints
- Waitlist rejoin behavior (end of queue positioning)

### Note: ObjectMapper in Integration Tests

```java
private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
```

**Spring Boot 4 Issue:** `@Autowired ObjectMapper` fails in integration tests due to a known issue with Spring Boot 4's test context initialization.

---

## Limitations & What I Would Do Differently

### Things I Simplified for This Submission

**1. H2 for Integration Tests**
I used H2 instead of Testcontainers with PostgreSQL because Docker might not be available. In a real project, I would use Testcontainers for true database compatibility testing.

**2. Synchronous Audit Logging**
Audit writes happen in the same transaction as the business operation. For high-throughput systems, I would publish events to a message queue and have a separate consumer write to the audit table.

**3. No Email Notifications**
Waitlist notifications are logged to console. Production would need SMTP integration.

**4. No Refresh Tokens**
I implemented only access tokens for simplicity. Production should have refresh tokens.

---

## Future Enhancements

If I were to continue developing this system:

- **Email Notifications**: Async email for overdue reminders and waitlist alerts
- **Refresh Token Flow**: Secure token rotation with revocation support
- **Rate Limiting**: Per-user throttling using Redis
- **Event-Driven Audit**: Async audit via Kafka
- **Reservation System**: Reserve available books for pickup
- **Reading Analytics**: Member reading patterns and recommendations

---

## Project Structure

```
src/main/java/com/iliaspiotopoulos/bibliocore/
├── config/                 # Configuration beans
├── controller/             # REST endpoints
├── dto/request/            # Incoming DTOs
├── dto/response/           # Outgoing DTOs
├── exception/              # Custom exceptions & handler
├── mapper/                 # MapStruct interfaces
├── model/entity/           # JPA entities
├── model/enums/            # Status enumerations
├── repository/             # Spring Data repositories
├── security/               # JWT & Security config
├── service/                # Business logic
└── specification/          # JPA Specifications
```

---