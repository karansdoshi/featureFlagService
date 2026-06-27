# Feature Flag Service — Design Document

## Overview

A REST API service that stores feature flags, manages flag states, and evaluates
feature availability based on user context. Supports targeting rules, per-user
overrides, and deterministic percentage rollout.

---

## REST APIs

### Flag Management

| Method   | Path                  | Description              |
|----------|-----------------------|--------------------------|
| `POST`   | `/api/flags`          | Create a flag            |
| `GET`    | `/api/flags`          | List all flags           |
| `GET`    | `/api/flags/{name}`   | Get a flag               |
| `PUT`    | `/api/flags/{name}`   | Update a flag            |
| `DELETE` | `/api/flags/{name}`   | Delete a flag            |

### Evaluation

| Method | Path                        | Description                        |
|--------|-----------------------------|------------------------------------|
| `POST` | `/api/flags/{name}/evaluate`| Evaluate one flag for a user       |

### Overrides

| Method   | Path                                        | Description                  |
|----------|---------------------------------------------|------------------------------|
| `PUT`    | `/api/flags/{name}/overrides/{userId}`      | Set override for (flag, user)|
| `DELETE` | `/api/flags/{name}/overrides/{userId}`      | Remove override               |

### Health

| Method | Path               | Description  |
|--------|--------------------|--------------|
| `GET`  | `/actuator/health` | Liveness     |

---

## Key Payloads

### Create / Update flag

```json
POST /api/flags
{
  "name": "new-checkout",
  "defaultState": "OFF",
  "rolloutPercentage": 20,
  "rules": [
    {
      "attribute": "subscriptionTier",
      "operator": "EQUALS",
      "value": "PREMIUM",
      "result": "ON",
      "order": 1
    },
    {
      "attribute": "region",
      "operator": "IN",
      "values": ["IN", "US"],
      "result": "ON",
      "order": 2
    }
  ]
}
```

### Evaluate

```json
POST /api/flags/new-checkout/evaluate
{
  "userId": "u-123",
  "subscriptionTier": "FREE",
  "region": "IN"
}

// Response
{
  "flag": "new-checkout",
  "enabled": true,
  "reason": "ROLLOUT"
}
```

The `reason` field is always present. Possible values:
`OVERRIDE` | `RULE_MATCH` | `ROLLOUT` | `DEFAULT` | `FALLBACK`

### Set override

```json
PUT /api/flags/new-checkout/overrides/u-123
{
  "state": "ON"
}
```

---

## Data Model

```
FeatureFlag
  name             String (PK)
  defaultState     Enum {ON, OFF}
  rolloutPercentage Int (0..100)
  createdAt        Timestamp
  updatedAt        Timestamp

Rule
  id               Long (PK)
  flagName         String (FK -> FeatureFlag)
  attribute        String        -- e.g. "subscriptionTier"
  operator         Enum {EQUALS, IN, NOT_EQUALS}
  value            String        -- single value for EQUALS / NOT_EQUALS
  values           String        -- comma-separated list for IN
  result           Enum {ON, OFF}
  order            Int           -- evaluation order, ascending

FlagOverride
  flagName         String (PK, FK -> FeatureFlag)
  userId           String (PK)
  state            Enum {ON, OFF}
```

Composite primary key on `FlagOverride(flagName, userId)` — no surrogate id needed.
Rules are ordered by the `order` field; first match wins.

---

## Evaluation Precedence

Resolution order — most explicit signal wins:

```
1. Per-user override    (flagName + userId) → return immediately if found
2. Targeting rules      first match in order → return rule result
3. Percentage rollout   hash(flagName:userId) % 100 < rolloutPct → return ON
4. Default state        flag's configured default
```

If the DB is unavailable at any point, fall back to the last cached definition.
If no cached definition exists, return `{ enabled: false, reason: "FALLBACK" }`.

### Why this order

- Overrides are explicit operator intent — they must always win.
- Rules are deliberate product targeting — they should not be gated by rollout.
- Rollout applies to unmatched traffic only — it controls gradual exposure
  for the general population, not for explicitly targeted cohorts.
- Default is the safety net when nothing else resolves.

---

## Architecture

### Layers

```
HTTP Layer        Spring MVC controllers, request validation (@Valid)
Service Layer     EvaluationService, FlagService — business logic + cache
Cache Layer       ConcurrentHashMap<String, FlagDefinition> keyed by flag name
Storage Layer     Spring Data JPA over H2 (swap to Postgres via config)
```

### Request lifecycle — evaluate

```
Client
  → POST /api/flags/{name}/evaluate
  → Controller: validate context payload (@Valid)
  → EvaluationService
      → Cache lookup by flag name
          HIT  → proceed to rule engine
          MISS → load from DB → populate cache → proceed to rule engine
               → DB down? → serve last-cached OR return FALLBACK
      → Rule engine: apply precedence (override → rules → rollout → default)
  → Return { enabled, reason }
```

### Request lifecycle — write (create / update / delete)

```
Client
  → POST/PUT/DELETE /api/flags/{name}
  → Controller: validate payload
  → FlagService
      → Persist to DB (source of truth updated first)
      → Evict cache entry for flag name
  → Next evaluation sees cache miss → lazy reload from DB
```

---

## Caching Design

**What is cached:** Flag definitions (rules + overrides + rolloutPercentage),
keyed by flag name. NOT evaluation results — results are context-dependent and
their cardinality (userId × tier × region) would make hit rates collapse and
invalidation intractable.

**Strategy:** Cache-aside with explicit eviction on write.

**Thread safety:** `ConcurrentHashMap` — atomic get/put/remove with no locking
overhead. No TTL at this stage; eviction is purely write-triggered.

**Invalidation:** On any write to a flag (create / update / delete / override change),
evict `cache.remove(flagName)`. The next evaluation repopulates lazily.

**Fallback on DB unavailable:**
1. Cache hit exists → serve it (stale-but-available, log a warning)
2. Cache miss + DB down → return `{ enabled: false, reason: "FALLBACK" }`

The principle: a flag service must never bring down its callers. Degraded-but-available
beats correct-but-down.

---

## Percentage Rollout Design

**Goal:** Deterministically assign each user a stable bucket (0–99) per flag,
and serve ON if bucket < rolloutPercentage.

**Algorithm:**

```java
boolean inRollout(String flagName, String userId, int rolloutPercentage) {
    if (rolloutPercentage <= 0)   return false;
    if (rolloutPercentage >= 100) return true;
    String key = flagName + ":" + userId;
    long hash = Hashing.murmur3_128()
                       .hashString(key, StandardCharsets.UTF_8)
                       .asLong();
    int bucket = (int) Math.floorMod(hash, 100);
    return bucket < rolloutPercentage;
}
```

**Key properties:**

- **Deterministic:** Pure function of (flag, user) — same inputs always give same bucket.
- **Sticky:** Ramping 10% → 20% → 50% only ever adds users. Nobody who saw ON loses it.
- **Decorrelated:** `flagName` is included in the hash key so a user's bucket differs
  per flag. Without it, the same low-bucket users would be first into every rollout.
- **Accurate distribution:** Murmur3 gives near-uniform buckets over 0–99.
  `String.hashCode() % 100` clusters — actual rollout would deviate significantly
  from the configured percentage.

**Scope:** Rollout applies to users who did not match any targeting rule.
Rule-scoped percentage rollout (ramping within a matched segment) is a natural
next extension — it would require `rolloutPercentage` to move from a flag-level
field to a rule variation type.

---

## Validation Contract

| Scenario                        | HTTP Status | reason       |
|---------------------------------|-------------|--------------|
| Missing required context field  | 400         | —            |
| Unknown flag name on evaluate   | 200         | `FALLBACK`   |
| Unknown flag name on CRUD       | 404         | —            |
| DB unavailable on evaluate      | 200         | `FALLBACK`   |
| DB unavailable on write         | 503         | —            |

Evaluation never returns 5xx to the caller — flag unavailability is a degraded
state, not an error from the caller's perspective.

---

## Production Readiness

- Health endpoint via Spring Actuator (`/actuator/health`)
- Structured JSON logging (Logback + logstash-logback-encoder)
- All config via environment variables / `application.yml` (no hardcoded values)
- Graceful shutdown (Spring Boot `server.shutdown=graceful`)
- Dockerfile (Ubuntu-compatible, multi-stage build)
- GitHub Actions CI: build → test → lint on every push

---

## What I Would Add With More Time

- TTL on cache (Caffeine) as defense-in-depth against missed invalidations
- Rule-scoped percentage rollout (rollout as a rule variation type)
- Audit log for flag changes (who changed what and when)
- Postgres as the production persistence target (H2 for local/test)
- Metrics endpoint (`/actuator/metrics`) with evaluation latency + cache hit rate
