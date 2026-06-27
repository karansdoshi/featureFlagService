# Architecture

Request -> cache -> storage lifecycle for the feature-flag service. See [DESIGN.md](../DESIGN.md)
for the full design and [DECISIONS.md](../DECISIONS.md) for tradeoffs.

## Component view

```mermaid
flowchart LR
  Client["Client"] --> Controllers["Spring MVC Controllers (api)"]
  Controllers --> Validation["@Valid + Jackson FAIL_ON_UNKNOWN"]
  Controllers --> EvalSvc["EvaluationService"]
  Controllers --> FlagSvc["FlagService (CRUD)"]
  Controllers --> OvrSvc["OverrideService"]
  EvalSvc --> Cache["FlagDefinitionCache (ConcurrentHashMap)"]
  FlagSvc --> Cache
  OvrSvc --> Cache
  EvalSvc --> Engine["RuleEngine + RolloutBucketer (pure)"]
  Cache --> Adapter["StorageAdapter (assembles FlagDefinition)"]
  FlagSvc --> Adapter
  OvrSvc --> Adapter
  Adapter --> Repos["JPA repositories: Flag / Rule / Override"]
  Repos --> DB[("H2 file DB (swap to Postgres)")]
```

## Evaluation request lifecycle (cache + precedence + fallback)

```mermaid
flowchart TD
  Client -->|"POST /api/flags/{name}/evaluate + context"| Controller
  Controller --> Validate{"Context valid?"}
  Validate -->|No| Err400["400 Bad Request"]
  Validate -->|Yes| Cache{"Definition in cache?"}
  Cache -->|Hit| Engine["Rule engine (precedence)"]
  Cache -->|Miss| DB{"DB reachable?"}
  DB -->|"Yes, found"| Load["Load + assemble definition, populate cache"]
  Load --> Engine
  DB -->|"Yes, not found"| Fb["200 enabled=false, reason=FALLBACK"]
  DB -->|Down| Fb
  Engine --> Ovr{"Override for userId?"}
  Ovr -->|Yes| ROvr["reason=OVERRIDE -> override state"]
  Ovr -->|No| Rules{"First rule match (by order)?"}
  Rules -->|Yes| RRule["reason=RULE_MATCH -> rule result"]
  Rules -->|No| Roll{"bucket < rolloutPercentage?"}
  Roll -->|Yes| RRoll["reason=ROLLOUT -> ON"]
  Roll -->|No| RDef["reason=DEFAULT -> flag defaultState"]
```

## Write path (cache invalidation)

```mermaid
flowchart TD
  W["POST/PUT/DELETE /api/flags or .../overrides/{userId}"] --> DBup{"DB reachable?"}
  DBup -->|No| Err503["503 Service Unavailable (cache untouched)"]
  DBup -->|Yes| Tx["Persist (source of truth first)"]
  Tx -->|"duplicate name"| Err409["409 Conflict"]
  Tx -->|"flag not found"| Err404["404 Not Found"]
  Tx -->|"ok"| Evict["cache.remove(flagName)"]
  Evict --> Next["Next evaluate -> cache miss -> lazy reload"]
```

## Rollout bucketing

```mermaid
flowchart LR
  In["flagName + ':' + userId"] --> Hash["Murmur3_128 -> asLong()"]
  Hash --> Mod["Math.floorMod(hash, 100) = bucket 0..99"]
  Mod --> Cmp{"bucket < rolloutPercentage?"}
  Cmp -->|Yes| Pass["serve ON (reason=ROLLOUT)"]
  Cmp -->|No| Fail["fall through to default"]
```
