# Decision Log

Every entry records the decision, the reasoning, and the alternative that was rejected. This
is the script for defending the build in the Architecture & Decision Review.

---

## Environment

### D-000: Build under the current folder, not `/workspaces`
- **Decision:** Use `/Users/s-coding-interview/DigitalOceanCodingRound` as the project root.
- **Why:** `/workspaces` does not exist on this machine; that path came from a generic
  devcontainer template that does not match this box. The user confirmed the current folder.
- **Rejected:** Creating `/workspaces` - pointless indirection on a non-container host.

### D-001: Java 21 (LTS) + Spring Boot 3.5.x
- **Why:** 21 is the current LTS; Spring Boot 3.x requires Java 17+. LTS keeps the demo
  reproducible.
- **Rejected:** Java 17 (older LTS, fine but no reason to trail), Java 23 (non-LTS).

### D-002: Toolchain installed by direct tarball download into `.tools/` (deviation)
- **Decision:** Download Temurin 21 + Maven 3.9.9 tarballs into a gitignored `.tools/` folder
  and put them on `PATH` via `.tools/env.sh`.
- **Why this deviates from the plan:** the approved plan said "install via Homebrew." On this
  box Homebrew is **not installed**, `sudo` is **blocked**, and SDKMAN refused to run (it needs
  Bash 4; macOS ships Bash 3.2). The sandbox also only allows writes inside the workspace, so a
  user-local, no-sudo install under `.tools/` is the only viable path. It reaches the identical
  end state (Temurin 21 + Maven on `PATH`).
- **Rejected:** Homebrew bootstrap (needs sudo to create `/opt/homebrew`); SDKMAN (needs Bash 4).

### D-003: Maven (not Gradle)
- **Why:** declarative POM I can read line-by-line and defend; Spring Initializr default.
- **Rejected:** Gradle - the Groovy/Kotlin DSL hides more behind plugins/conventions, which is
  the opposite of what a "defend every line" review rewards.

### D-004: H2 file-based (not Postgres-in-Docker)
- **Why:** zero external dependencies and the fastest standup. The DB-down path is demoed
  cleanly via a runtime toggle (`storage.simulate-outage`) that makes the storage adapter throw
  `DataAccessResourceFailureException` - deterministic and unit-testable, no container juggling.
- **Rejected:** Postgres-in-Docker - the `docker stop` outage demo is authentic but adds a
  runtime dependency and slower standup; the toggle gives an equally convincing, more
  controllable demo. (Docker is still used for packaging via the Dockerfile.)

### D-005: `ConcurrentHashMap` cache (not Caffeine)
- **Why:** thread-safe, fully explainable, no dependency. We do not need TTL or size eviction
  because we invalidate explicitly on writes and the flag-definition set is small and bounded.
- **Rejected:** Caffeine - its TTL/eviction solve a staleness/memory problem we do not have
  here; it would be a dependency I would have to justify for zero benefit.

---

## Domain & persistence

### D-006: Flag stored as one row with `rules` as a JSON column
- **Why:** the only access pattern is whole-aggregate read/write by name, which matches
  cache-aside by name. We never query rule internals in SQL, so normalization buys nothing.
- **Rejected:** normalized Flag/Rule/Condition tables - more entities, joins, and mapping code
  for an access pattern we do not have. Noted as the alternative if rule-level SQL querying
  were ever required.

### D-007: Rule = AND of conditions; ordered list of rules is first-match-wins
- **Why:** simplest semantics that still expresses real targeting. "All conditions must match"
  is intuitive; OR is expressed by adding another rule. First-match-wins with a terminal
  default is a well-understood model (mirrors LaunchDarkly-style targeting).
- **Rejected:** arbitrary boolean expression trees (powerful but a parser/evaluator I would
  have to defend and test far beyond the time box).

---

## Cache

### D-008: Write-invalidate (evict) instead of write-through
- **Why:** fewer states and safer. Evicting and letting the next read repopulate from the DB
  means we never cache a value whose transaction later rolls back, and there is no
  "cache says X, DB says Y" window created by our own writes.
- **Rejected:** write-through (update cache with the new value on write) - marginally fewer DB
  reads after a write, at the cost of more failure modes to reason about.

### D-009: No TTL
- **Why:** TTL bounds staleness when you cannot see writes. We see every write, so explicit
  invalidation keeps the cache coherent with no expiry needed.
- **Rejected:** short TTL "just in case" - would add staleness windows and timing-dependent
  tests for no correctness gain.

---

## Fallback & status codes

### D-010: Evaluation favors availability (200 + degraded), readiness owns 503
- **Why:** the evaluation endpoint must always hand callers a deterministic decision. During a
  DB outage we serve cached definitions normally; for an uncached flag we return safe-default
  OFF with `degraded=true` (200). The readiness probe returns 503 so orchestrators stop routing
  to the degraded instance. This cleanly separates the two concerns.
- **Rejected:** returning 503 from evaluation - breaks every caller that just wants a flag
  decision, which defeats the purpose of having a fallback.

### D-011: Unknown flag returns 404 (not 200 OFF)
- **Decision:** evaluating a flag that definitively does not exist (DB reachable) returns 404.
- **Why:** surfaces typos/misconfiguration instead of silently returning OFF and hiding a bug.
- **Rejected:** 200 OFF for unknown flags - "safe" but masks configuration errors. Flagged as
  defensible either way; switchable on request.

---

## Percentage rollout

### D-012: Bucket via `floorMod(SHA-256(flagName + ":" + userId), 100) < pct`
- **Why SHA-256:** uniform distribution, in the JDK (no dependency), stable across JVMs.
- **Why salt with flag name:** decorrelates rollouts so one user is not in/out of every flag's
  rollout simultaneously.
- **Why deterministic:** pure function of inputs -> sticky exposure with zero stored per-user
  state.
- **Modulo bias:** negligible over a ~2^64 range vs 100; accepted rather than adding
  rejection sampling.
- **Rejected:** `String.hashCode() % 100` (poor distribution, can be negative); persisting a
  per-user assignment (needs storage + invalidation for no benefit).

---

## Production-readiness

### D-013: Spring Boot Actuator for health/readiness
- **Why:** standard, lightweight, well-understood; custom readiness indicator pings the DB.
- **Rejected:** hand-rolled `/health` endpoints - reinventing a solved problem.

### D-014: Spring Boot built-in structured (JSON) logging
- **Why:** available in Boot 3.4+ with no extra dependency (`logging.structured.format.console`).
- **Rejected:** `logstash-logback-encoder` - extra dependency for the same outcome here.

---

## Workflow

### D-015: GitHub push deferred; everything committed locally first
- **Decision:** `git init` locally, commit at every gate, and prepare a `scripts/push.sh` for
  the final push. The user will create the repo + authenticate and run the push at the end.
- **Why:** `gh` is not installed, GitHub auth needs the user's credentials (a secret I cannot
  supply), and the sandbox blocks writes to `~/.config`. Local commits keep the history intact
  and ready to push the moment a remote exists. The user explicitly chose this path.
- **Commit identity:** passed per-commit via `GIT_AUTHOR_*`/`GIT_COMMITTER_*` env vars
  (`s-coding-interview <s-coding-interview@users.noreply.github.com>`) so the global git config
  is never modified.
- **Rejected:** pasting a PAT into the session (works but puts a secret in chat history).
