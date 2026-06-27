# Feature Flag Service

A production-ready REST feature-flag service in Java / Spring Boot. It stores flags, evaluates
them against a user context (per-user overrides, ordered targeting rules, and deterministic
percentage rollout), caches flag definitions in memory, and degrades gracefully when the
database is unavailable.

- Design: [DESIGN.md](DESIGN.md)
- Decision log (tradeoffs + rejected alternatives): [DECISIONS.md](DECISIONS.md)
- Architecture diagrams: [docs/architecture.md](docs/architecture.md)

## Stack

- Java 21, Spring Boot 3.5 (Web, Data JPA, Validation, Actuator)
- H2 (file-based; swappable to Postgres via env vars)
- In-memory cache: Caffeine (write-invalidate + TTL safety net + size bound + stats)
- Murmur3 (Guava) for rollout bucketing
- Structured JSON logging via logstash-logback-encoder
- JUnit 5 + Mockito

## Prerequisites

Any JDK 21 and Maven 3.9+. This workspace also ships a self-contained toolchain (no Homebrew /
sudo needed); activate it with:

```bash
source .tools/env.sh   # puts the bundled Temurin 21 + Maven on PATH
```

## Build & test

```bash
mvn clean verify        # compiles and runs all 36 unit + integration tests
```

## Run

```bash
mvn spring-boot:run
# or
mvn clean package && java -jar target/feature-flag-service-0.1.0.jar
```

The service listens on `http://localhost:8080`. Health: `GET /actuator/health`.

### Configuration (env vars, no hardcoded creds)

| Variable | Default | Purpose |
|----------|---------|---------|
| `SERVER_PORT` | `8080` | HTTP port |
| `SPRING_DATASOURCE_URL` | `jdbc:h2:file:./data/featureflags` | JDBC URL (point at Postgres in prod) |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | `sa` / empty | DB credentials |
| `FEATUREFLAGS_SIMULATE_OUTAGE` | `false` | When `true`, the storage layer throws, exercising the fallback/503 paths |

## API

Base path: `/api/flags`.

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/flags` | Create a flag (201, 409 if exists) |
| GET | `/api/flags` | List flags |
| GET | `/api/flags/{name}` | Get a flag (404 if unknown) |
| PUT | `/api/flags/{name}` | Update a flag (404 if unknown) |
| DELETE | `/api/flags/{name}` | Delete a flag (204) |
| POST | `/api/flags/{name}/evaluate` | Evaluate for a user context |
| PUT | `/api/flags/{name}/overrides/{userId}` | Set a per-user override (204) |
| DELETE | `/api/flags/{name}/overrides/{userId}` | Remove a per-user override (204) |

### Create a flag

```bash
curl -sX POST localhost:8080/api/flags -H 'Content-Type: application/json' -d '{
  "name": "new-checkout",
  "defaultState": "OFF",
  "rolloutPercentage": 20,
  "rules": [
    { "attribute": "subscriptionTier", "operator": "EQUALS", "value": "PREMIUM", "result": "ON", "order": 1 },
    { "attribute": "region", "operator": "IN", "values": ["IN", "US"], "result": "ON", "order": 2 }
  ]
}'
```

### Evaluate

```bash
curl -sX POST localhost:8080/api/flags/new-checkout/evaluate \
  -H 'Content-Type: application/json' -d '{
    "userId": "u-123",
    "subscriptionTier": "FREE",
    "region": "IN"
  }'
```

```json
{ "flag": "new-checkout", "enabled": true, "reason": "RULE_MATCH" }
```

`reason` is one of `OVERRIDE | RULE_MATCH | ROLLOUT | DEFAULT | FALLBACK`. Precedence:
**override -> first matching rule (by `order`) -> percentage rollout -> default**.

### Example payloads and expected results

Against the `new-checkout` flag above (`defaultState=OFF`, `rolloutPercentage=20`):

```bash
# Rule match on region IN -> ON / RULE_MATCH
{ "userId": "u-1", "subscriptionTier": "FREE", "region": "IN" }

# No rule match (region DE, tier FREE) -> rollout bucket decides, else DEFAULT (OFF)
{ "userId": "u-2", "subscriptionTier": "FREE", "region": "DE" }
```

### Per-user override

```bash
curl -sX PUT localhost:8080/api/flags/new-checkout/overrides/u-123 \
  -H 'Content-Type: application/json' -d '{ "state": "ON" }'
# Now evaluating for u-123 returns reason OVERRIDE regardless of rules/rollout.

curl -sX DELETE localhost:8080/api/flags/new-checkout/overrides/u-123   # remove it
```

### Graceful fallback demo

```bash
FEATUREFLAGS_SIMULATE_OUTAGE=true mvn spring-boot:run
```

- Evaluating a flag already cached -> still served (degraded-but-available).
- Evaluating an uncached flag while the DB is down -> `200 { "enabled": false, "reason": "FALLBACK" }`.
- Any write (create/update/delete/override) -> `503 Service Unavailable`.

(Note: an unknown flag while the DB is *healthy* returns `404` — that's a client error, distinct from the DB-down `FALLBACK`.)

## Validation & status codes

| Scenario | Status |
|----------|--------|
| Missing/blank context field, unknown JSON field, invalid enum, bad rollout %, unknown operator/attribute | 400 |
| Unknown flag on CRUD or evaluate | 404 |
| Duplicate flag name on create | 409 |
| DB unavailable on write | 503 |
| DB-down (uncached) on evaluate | 200 with `reason=FALLBACK` |

## Docker

```bash
docker build -t feature-flag-service .
docker run -p 8080:8080 -v "$PWD/data:/app/data" feature-flag-service
```

## Project layout

```
domain       pure model + evaluation types (no framework)
evaluation   RuleEngine (precedence) + RolloutBucketer (Murmur3)
storage      JPA entities, repositories, FlagStore (mapping + outage guard)
cache        FlagDefinitionCache (Caffeine)
service      FlagService, OverrideService, EvaluationService
api          controllers, DTOs, mapper, global exception handler
```

## CI

GitHub Actions builds and runs the full test suite on every push/PR to `main`
(see [.github/workflows/ci.yml](.github/workflows/ci.yml)).
