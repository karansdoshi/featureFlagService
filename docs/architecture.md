# Architecture

Request -> cache -> storage lifecycle for the feature-flag service. See [DESIGN.md](../DESIGN.md)
for the full rationale and [DECISIONS.md](../DECISIONS.md) for tradeoffs.

## Component view

```mermaid
flowchart LR
  Client["Client"] --> Controller["REST Controller (api)"]
  Controller --> Validation["Bean Validation + Jackson"]
  Controller --> EvalSvc["EvaluationService"]
  Controller --> FlagSvc["FlagService (CRUD)"]
  EvalSvc --> Cache["FlagDefinitionCache (ConcurrentHashMap)"]
  FlagSvc --> Cache
  EvalSvc --> Evaluator["RuleEvaluator + RolloutBucketer (pure)"]
  Cache --> Adapter["FlagRepositoryAdapter"]
  FlagSvc --> Adapter
  Adapter --> Repo["Spring Data JPA Repository"]
  Repo --> DB[("H2 file DB")]
  Readiness["Actuator readiness indicator"] --> Adapter
```

## Evaluation request lifecycle (cache + fallback)

```mermaid
flowchart TD
  Client -->|"POST /flags/{name}/evaluate + context"| Controller
  Controller --> Validate{"Context valid?"}
  Validate -->|No| Err400["400 Bad Request (structured error)"]
  Validate -->|Yes| Cache{"Definition in cache?"}
  Cache -->|Hit| Eval["Evaluate rules in order"]
  Cache -->|Miss| DB{"DB reachable?"}
  DB -->|"Yes, found"| Load["Load definition, populate cache"]
  Load --> Eval
  DB -->|"Yes, not found"| Err404["404 Unknown flag"]
  DB -->|Down| Fallback["Safe-default OFF, degraded=true"]
  Eval --> Match{"First matching rule?"}
  Match -->|Yes| RetVar["reason=MATCHED_RULE -> rule variation"]
  Match -->|No| RetDef["reason=DEFAULT -> flag defaultState"]
  Fallback --> Resp200["200, reason=FALLBACK_SAFE_DEFAULT, X-FeatureFlag-Degraded: true"]
  RetVar --> Resp["200 result"]
  RetDef --> Resp
```

## Write path (cache invalidation)

```mermaid
flowchart TD
  W["POST / PUT / DELETE /flags"] --> Tx["Persist via repository (transaction)"]
  Tx -->|"commit ok"| Evict["Evict flag name from cache"]
  Tx -->|"duplicate name"| Err409["409 Conflict (cache untouched)"]
  Tx -->|"not found"| Err404["404 Not Found (cache untouched)"]
  Tx -->|"DB error"| Err5xx["map error (cache untouched)"]
  Evict --> Next["Next read repopulates from DB (cache-aside)"]
```

## Rollout bucketing

```mermaid
flowchart LR
  In["flagName + ':' + userId"] --> Hash["SHA-256 -> first 8 bytes -> long"]
  Hash --> Mod["Math.floorMod(value, 100) = bucket in 0..99"]
  Mod --> Cmp{"bucket < rolloutPercentage?"}
  Cmp -->|Yes| Pass["rollout gate passes"]
  Cmp -->|No| Fail["rollout gate fails"]
```
