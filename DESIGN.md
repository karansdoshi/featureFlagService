# Feature Flag Service - Design

A production-ready REST feature-flag service: CRUD for flags, rule-based evaluation against
a user context, in-memory caching of flag definitions, graceful fallback when the database is
unavailable, and deterministic percentage rollouts.

This document is the source of truth for the design. Every tradeoff and the rejected
alternative is recorded in [DECISIONS.md](DECISIONS.md). The visual lifecycle lives in
[docs/architecture.md](docs/architecture.md).

---

## 1. Stack

- Java 21 (LTS) + Spring Boot 3.5.x: Web, Data JPA, Validation, Actuator.
- Build: Maven (declarative POM I can read top-to-bottom; Spring Initializr default).
- Persistence: H2 file-based via Spring Data JPA.
- Cache: a plain `ConcurrentHashMap` wrapped in a small cache component.
- Tests: JUnit 5 + Mockito.

See DECISIONS.md (D-001..D-005) for why each was chosen over the alternative.

---

## 2. Domain model

A **Flag** is an aggregate root. It owns an ordered list of **Rules**; each Rule owns a list
of **Conditions** and a resulting variation.

```
Flag
  name            : String   (unique identifier, primary key)
  description      : String
  defaultState     : boolean  (the fallthrough variation if no rule matches)
  rules            : List<Rule>  (ORDERED; evaluated first to last)

Rule
  conditions        : List<Condition>   (ALL must match -> rule matches; AND semantics)
  variation         : Variation         (ON | OFF; result when this rule matches)
  rolloutPercentage : Integer?          (0..100; optional sticky percentage gate)

Condition
  attribute : Attribute   (USER_ID | SUBSCRIPTION_TIER | REGION)
  operator  : Operator    (EQUALS | IN | NOT_IN)
  values    : List<String>

EvaluationContext (request input)
  userId           : String   (required, non-blank)
  subscriptionTier : String   (required, non-blank)
  region           : String   (required, non-blank)
```

### 2.1 Persistence shape

The Flag aggregate is stored as a **single row**: scalar columns (`name`, `description`,
`default_state`) plus the `rules` list serialized to a JSON column (`rules_json`). The only
access pattern is whole-aggregate read/write keyed by name, which is exactly what cache-aside
by name needs; we never run SQL against rule internals. See DECISIONS.md D-006 (rejected:
fully normalized Flag/Rule/Condition tables).

---

## 3. Evaluation semantics (reviewer Q1)

Evaluation is **ordered, first-match-wins**:

1. Walk `rules` in declared order.
2. A rule **matches** when **all** of its conditions match the context **AND** the rollout
   gate passes (no `rolloutPercentage`, or the user falls inside the bucket - see section 7).
3. The first matching rule's `variation` is the result. Evaluation stops there.
4. If **no** rule matches, return the flag's `defaultState`.

`defaultState` is the terminal fallthrough, not "rule 0". This keeps the mental model simple:
rules are overrides layered on top of a default.

A condition matches by operator:
- `EQUALS`: the context attribute equals `values[0]`.
- `IN`: the context attribute is one of `values`.
- `NOT_IN`: the context attribute is none of `values`.

---

## 4. What we cache (reviewer Q2)

**We cache flag DEFINITIONS keyed by name. We never cache evaluation RESULTS.**

- Results depend on `(userId, subscriptionTier, region, rollout bucket)`. With percentage
  rollout this is effectively per-user, so a result cache explodes in cardinality and its hit
  rate collapses.
- Invalidating result entries on a flag edit is intractable (you would have to evict every
  context permutation for that flag).
- Definitions are few and bounded. Evaluating rules in memory is cheap CPU work, so caching
  the definition and evaluating per-request gives us the DB-offload win without the cardinality
  problem.

---

## 5. Cache strategy + invalidation (reviewer Q3, Q4)

- **Cache-aside (lazy load).** The read path checks the cache; on a miss it loads from the DB
  and populates the cache, then returns.
- **Thread-safety** comes from `ConcurrentHashMap`. Reads are lock-free; `computeIfAbsent`
  guards population.
- **No TTL.** TTL exists to bound staleness when you cannot observe writes. We observe every
  write (all mutations go through this service), so we invalidate explicitly and the cache is
  always coherent with the DB. The definition set is small, so memory pressure is a non-issue.
- **Write-invalidate, not write-through.** On create/update/delete we write to the DB inside a
  transaction and, only after a successful commit, **evict** that flag's entry. The next read
  repopulates from the source of truth.
  - Why evict instead of overwrite: fewer states and no risk of caching a value whose persist
    later fails or whose transaction rolls back. See DECISIONS.md D-008.
- **Invalidation timing is exact and testable:** eviction happens in the service layer
  immediately after the repository call returns successfully, and does **not** happen if the
  write throws. This is unit-tested by mocking the repository and asserting `evict(name)` is
  (not) called.

---

## 6. Graceful fallback contract (reviewer Q5)

The evaluation endpoint favors **availability**: callers always get a deterministic ON/OFF.

| Situation | Behavior | HTTP |
|---|---|---|
| Definition in cache | Evaluate from cache (DB state irrelevant) | 200 |
| Cache miss, DB reachable, flag found | Load + populate cache, evaluate | 200 |
| Cache miss, DB reachable, flag not found | Unknown flag is a client error | 404 |
| Cache miss, DB **down**, no cached definition | We cannot know the configured default -> serve **safe-default OFF**, mark degraded | 200 + `degraded=true` + `X-FeatureFlag-Degraded: true` |

Rationale:
- A flag that is already cached keeps serving correctly during a DB outage - that is the whole
  point of the cache.
- When we have never seen the flag and the DB is down, we genuinely do not know its default
  (it lives in the DB). Returning **OFF** is the safe direction: we never accidentally enable a
  feature during an outage. We return 200 (not 503) so client code that just wants a flag
  decision keeps working, and we set `degraded=true` so callers/operators can detect it.
- **503 is reserved for where it belongs:** the Actuator **readiness** probe reports DOWN/503
  when the DB is unreachable, so orchestrators stop routing new traffic to a degraded instance.
  This separates "this instance is degraded" (readiness, 503) from "give me a flag decision"
  (evaluation, always 200).

See DECISIONS.md D-009 for the rejected alternative (return 503 from evaluation) and D-010 for
404-vs-OFF on a definitively-unknown flag.

---

## 7. Percentage rollout determinism (reviewer Q6)

A rule may carry `rolloutPercentage` in `[0, 100]`. The rollout gate is:

```
bucket = Math.floorMod(hash(flagName + ":" + userId), 100)
passes = bucket < rolloutPercentage
```

- **Hash function:** SHA-256 of `flagName + ":" + userId`, take the first 8 bytes as a long.
  - Why not `String.hashCode()`: weak avalanche / poor distribution and it can be negative.
  - SHA-256 is in the JDK (no dependency), uniform, and stable across JVMs/runs.
- **Why include the flag name in the hash:** it decorrelates rollouts across flags. Without it,
  the same user maps to the same bucket for every flag, so one "unlucky" user would be inside
  (or outside) the first N% of *every* rollout simultaneously. Salting with the flag name makes
  each flag's rollout independent - a user can be in flag A's 10% but not flag B's 10%.
- **Why it is stable across calls:** the hash is a pure function of `(flagName, userId)` with no
  RNG and no clock. The same inputs always yield the same bucket, so a user's exposure is
  "sticky" with **zero per-user state stored**.
- **Modulo bias:** `% 100` over a value drawn from a ~2^64 range is non-uniform only at the
  ~2^64 / 100 boundary, an utterly negligible skew. We accept it rather than add
  rejection-sampling complexity. Documented in DECISIONS.md D-011.

The rollout gate is part of "does this rule match": a rule with conditions + a rollout passes
only if the conditions match AND the bucket passes.

---

## 8. Validation contract (reviewer Q7)

- Bean Validation (`@NotBlank`) on the context DTO + `@Valid` on the controller parameter.
- Jackson configured with `FAIL_ON_UNKNOWN_PROPERTIES=true` so unknown context fields are
  rejected rather than silently ignored.
- A single `@RestControllerAdvice` maps exceptions to a structured JSON error body:
  `{ timestamp, status, error, message, path, fieldErrors[] }`.

Status codes by error class:

| Class | Example | Status |
|---|---|---|
| Validation | missing/blank `userId`/`subscriptionTier`/`region`, unknown JSON field, malformed JSON, invalid enum | 400 |
| Unknown flag | CRUD or evaluation against a name that does not exist (DB reachable) | 404 |
| Duplicate | create a flag whose name already exists | 409 |
| Degraded readiness | readiness probe while DB unreachable | 503 |
| Degraded evaluation | DB down + uncached flag | 200 + `degraded=true` |
| Unexpected | uncaught error | 500 |

---

## 9. API surface

```
POST   /api/v1/flags                  create            201 / 409
GET    /api/v1/flags                  list              200
GET    /api/v1/flags/{name}           read              200 / 404
PUT    /api/v1/flags/{name}           update            200 / 404
DELETE /api/v1/flags/{name}           delete            204 / 404
POST   /api/v1/flags/{name}/evaluate  evaluate context  200 / 404
GET    /actuator/health                                 liveness + readiness
GET    /actuator/health/readiness                       DB-aware readiness
```

Evaluation response body:
```json
{ "flagName": "new-checkout", "enabled": true, "reason": "MATCHED_RULE",
  "matchedRuleIndex": 0, "degraded": false }
```
`reason` is one of `MATCHED_RULE`, `DEFAULT`, `FALLBACK_SAFE_DEFAULT`.

---

## 10. Production-readiness

- **Health/readiness:** Spring Boot Actuator with liveness + a custom DB-aware readiness
  indicator (chosen as the standard, lightweight option; D-012).
- **Structured logging:** Spring Boot 3.4+ built-in JSON logging
  (`logging.structured.format.console`) - no extra dependency.
- **Config:** `application.yml` with env-var overrides (`SPRING_DATASOURCE_*`, etc.); no
  hardcoded credentials.
- **Graceful shutdown:** `server.shutdown=graceful` + a lifecycle timeout so in-flight
  requests drain.
- **Dockerfile:** multi-stage (Maven build -> JRE runtime), runs as a non-root user.

---

## 11. Module / package layout

```
com.digitalocean.featureflags
  api          REST controllers + DTOs + global exception handler
  domain       Flag, Rule, Condition, Variation, Attribute, Operator, EvaluationContext, result
  evaluation   RuleEvaluator (pure), RolloutBucketer (SHA-256)
  storage      JPA entity, repository, FlagRepositoryAdapter (maps DataAccess errors)
  cache        FlagDefinitionCache (ConcurrentHashMap)
  service      FlagService (CRUD + cache-aside + invalidation), EvaluationService (fallback)
  config       Jackson, health/readiness, app properties
```

The **evaluation** package is pure (no Spring, no IO) so rule logic and rollout bucketing are
trivially unit-testable - which matters because rule evaluation and cache invalidation are the
explicitly-tested areas.
