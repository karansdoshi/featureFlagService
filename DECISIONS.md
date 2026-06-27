# Decision Log

Every entry records the decision, the reasoning, and the alternative that was rejected. This
is the script for defending the build in the Architecture & Decision Review. The authoritative
design is [DESIGN.md](DESIGN.md); this log explains the *why* behind it and the
environment-specific deviations.

---

## Environment

### D-000: Build under the current folder, not `/workspaces`
- **Decision:** Use `/Users/s-coding-interview/DigitalOceanCodingRound` as the project root.
- **Why:** `/workspaces` does not exist on this machine; that path came from a generic
  devcontainer template. The user confirmed the current folder.

### D-001: Java 21 (LTS) + Spring Boot 3.5.x
- **Why:** 21 is the current LTS; Spring Boot 3.x needs Java 17+. LTS keeps the demo reproducible.

### D-002: Toolchain installed by direct tarball download into `.tools/` (deviation)
- **Decision:** Download Temurin 21 + Maven 3.9.9 tarballs into a gitignored `.tools/` and put
  them on `PATH` via `.tools/env.sh`.
- **Why this deviates:** the plan said "Homebrew." On this box Homebrew is not installed,
  `sudo` is blocked, and SDKMAN needs Bash 4 (macOS ships 3.2). The sandbox only allows writes
  inside the workspace, so a user-local, no-sudo install is the only viable path. Same end state.

### D-003: Maven (not Gradle)
- **Why:** declarative POM I can read line-by-line; Spring Initializr default.
- **Rejected:** Gradle — DSL hides more behind plugins/conventions.

### D-004: H2 file-based, swappable to Postgres via config (not Postgres-in-Docker now)
- **Why:** zero external deps, fastest standup. JPA + env-var datasource config means production
  can point at Postgres with no code change. DB-down is exercised via a runtime toggle
  (`featureflags.storage.simulate-outage`) that makes the storage adapter throw
  `DataAccessException` — deterministic and unit-testable.
- **Rejected:** Postgres-in-Docker as the dev default — authentic outage demo but slower standup.

### D-015: GitHub push deferred; everything committed locally first
- **Decision:** `git init` locally, commit at every gate, prepare `scripts/push.sh` for the
  final push. The user creates the repo + authenticates and runs the push.
- **Why:** `gh` is not installed, auth needs the user's secret credentials, and the sandbox
  blocks writes to `~/.config`. Commit identity is passed per-commit via `GIT_AUTHOR_*` env vars
  so the global git config is never modified.

---

## Domain & persistence

### D-006: Normalized schema (FeatureFlag / Rule / FlagOverride)
- **Decision:** three tables. `Rule` has a surrogate `id` and a `flagName` FK ordered by
  `order`; `FlagOverride` uses a composite PK `(flagName, userId)` — no surrogate id needed.
- **Why:** the design models rules and per-user overrides as first-class, separately-mutable
  rows (override endpoints write a single `(flag,user)` row without rewriting the flag). A
  normalized model expresses this cleanly and makes overrides cheap to set/delete.
- **Rejected:** serializing rules+overrides into a single JSON column — simpler to read whole,
  but per-user override writes would require rewriting the whole aggregate and lose row-level
  semantics.

### D-007: Rule = single (attribute, operator, value/values, result), ordered; first-match-wins
- **Why:** matches the design payload exactly. `value` holds the single operand for
  `EQUALS`/`NOT_EQUALS`; `values` (comma-separated in storage, array in JSON) holds the set for
  `IN`. Simple, predictable, easy to validate and test.
- **Rejected:** multi-condition rules / boolean expression trees — more power, far more parser
  and test surface than the time box and the design call for.

### D-008: `order` column is named `rule_order` in SQL
- **Why:** `ORDER` is a reserved SQL keyword; quoting it everywhere is fragile. The JSON field
  stays `order` to match the design; only the physical column is renamed.

---

## Cache

### D-009: Cache full FlagDefinition (rules + overrides + rollout) keyed by name; evict on any write
- **Why:** the evaluator needs the whole definition (overrides included) to apply precedence, so
  the cache value is the assembled definition. Any write that affects a flag — including an
  override set/delete — evicts `cache[flagName]`, so the cache never serves a stale override.
- **Rejected:** caching evaluation results — context cardinality (userId × tier × region) makes
  hit rates collapse and invalidation intractable.

### D-010: Cache-aside + write-invalidate, no TTL
- **Why:** we observe every write, so explicit eviction keeps the cache coherent with the DB
  with no expiry. `ConcurrentHashMap` gives atomic get/put/remove with no locking.
- **Rejected:** write-through (more failure states) and TTL (staleness windows for no gain).
  TTL via Caffeine is noted as a "with more time" defense-in-depth item.

---

## Evaluation precedence & fallback

### D-011: Precedence override → rules → rollout → default
- **Why:** most explicit signal wins. Overrides are explicit operator intent; rules are
  deliberate targeting (not gated by rollout); rollout governs only unmatched/general traffic;
  default is the safety net.

### D-012: Evaluation favors availability; unknown flag and DB-down both return 200 FALLBACK
- **Decision:** evaluation never returns 5xx. Unknown flag (DB reachable) → 200
  `{enabled:false, reason:FALLBACK}`; DB down + uncached → 200 FALLBACK; DB down but cached →
  serve the cached definition (log a warning). Writes against a down DB → 503.
- **Why:** a flag service must never bring its callers down. Degraded-but-available beats
  correct-but-down; OFF is the safe default direction. Writes legitimately fail loudly (503)
  because there is no safe degraded write.
- **Note:** this returns 200 (not 404) for an unknown flag on *evaluate*, per the design's
  validation table — the caller just wants a decision. CRUD reads of an unknown flag still 404.

---

## Percentage rollout

### D-013: Bucket via `floorMod(Murmur3_128(flagName + ":" + userId), 100) < pct`
- **Why Murmur3 (Guava):** near-uniform distribution over 0–99, so observed rollout matches the
  configured percentage. `String.hashCode() % 100` clusters and would skew the rollout.
- **Why salt with flag name:** decorrelates buckets across flags so the same low-bucket users
  are not first into every rollout.
- **Sticky & deterministic:** pure function of (flag, user); ramping the percentage up only adds
  users, never removes them — zero stored per-user state.
- **Cost:** adds the Guava dependency for one hash; accepted because correct distribution is the
  whole point of percentage rollout.
- **Rejected:** `String.hashCode()` (poor distribution); a JDK `MessageDigest` SHA-256 hash
  (works, but Murmur3 is the design's stated choice and is faster for short keys).

---

## Production-readiness

### D-014: Structured JSON logging via Logback + logstash-logback-encoder
- **Why:** the design's stated choice; emits one JSON object per log line for ingestion by ELK
  and friends, configured in `logback-spring.xml`.
- **Rejected:** Spring Boot 3.4+ built-in structured logging — simpler/no dependency, but the
  design specifies logstash-logback-encoder, so we follow it.

### D-016: Spring Boot Actuator `/actuator/health`
- **Why:** standard, lightweight liveness/health with a built-in DB health contributor.
- **Rejected:** hand-rolled health endpoint.
