# Architecture Overview

This document provides a comprehensive overview of the Fact Graph system architecture.

---

## System Purpose

Fact Graph is a declarative knowledge graph system designed to model complex tax logic without requiring application code changes. Tax calculations are defined in XML Fact Dictionaries (FXML), which are loaded at runtime and applied to user data to compute tax returns.

**Key Design Goals:**
- **Declarative Logic**: Tax law encoded in XML, not code
- **Cross-Platform**: Single codebase compiles to JavaScript and JVM
- **Type Safety**: Strong typing with runtime checks
- **Lazy Evaluation**: Facts computed only when needed
- **Validation**: Built-in limit system for data validation
- **Persistence**: Serializable state for long-running sessions
- **Migration**: Support for schema evolution across versions

---

## Core Entity Relationships

```
FactDictionary (XML Files)
    │
    ├── Contains multiple FactDefinitions (blueprints)
    │   ├── path: "/filingStatus"
    │   ├── value: CompNode (expression tree)
    │   └── limits: Seq[Limit]
    │
    ▼
Graph (User's Tax Scenario)
    │
    ├── dictionary: FactDictionary
    ├── persister: Persister (stores values)
    ├── factCache: Map[Path, Fact]
    └── resultCache: Map[Path, Result]
    │
    ▼
Fact (Instance of a fact)
    │
    ├── path: Path (e.g., "/filingStatus")
    ├── value: CompNode (expression)
    ├── limits: Seq[Limit]
    ├── graph: Graph (parent reference)
    └── parent: Option[Fact]
    │
    ▼
Expression (Computation)
    │
    ├── Evaluates to: MaybeVector[Result[A]]
    │   │
    │   ├── Result[A]: Complete(v) | Placeholder(v) | Incomplete
    │   └── MaybeVector[A]: Single(x) | Multiple(vector)
    │
    └── Types: Dependency, Switch, Add, Multiply, etc.
```

**Entity Lifecycle:**
1. **Dictionary Loading**: XML files parsed into `FactDictionary` containing `FactDefinition` objects
2. **Graph Creation**: `Graph` instantiated from dictionary with a `Persister`
3. **Fact Resolution**: Paths resolved to `Fact` instances (cached in `factCache`)
4. **Expression Evaluation**: Facts evaluate their `CompNode` expressions lazily (cached in `resultCache`)
5. **Persistence**: `save()` validates all facts and persists to `Persister`

---

## Cross-Platform Design

```
┌─────────────────────────────────────────┐
│         shared/src/main/scala/          │
│      (Core logic - 100% shared)         │
│                                         │
│  FactDictionary, Graph, Fact,          │
│  Expression, CompNodes, Types,          │
│  Limits, Monads, Persisters            │
└─────────────┬───────────────────────────┘
              │
      ┌───────┴───────┐
      │               │
      ▼               ▼
┌───────────┐   ┌───────────┐
│ js/       │   │ jvm/      │
│ Platform  │   │ Platform  │
│ Specific  │   │ Specific  │
└───────────┘   └───────────┘
      │               │
      ▼               ▼
┌───────────┐   ┌───────────┐
│ .mjs      │   │ .jar      │
│ ES Module │   │ JVM Lib   │
└───────────┘   └───────────┘
```

**Cross-Platform Strategy:**
- **Shared Code**: All business logic in `shared/` directory (>95% of codebase)
- **Platform-Specific**: Minimal platform code in `js/` and `jvm/` directories
- **JS Exports**: `@JSExport` annotations mark JavaScript API surface
- **Type Safety**: Scala's type system ensures consistency across platforms
- **Libraries**: Cross-platform libraries (fs2-data-xml, scala-java-time, upickle)

**Build Configuration** (`build.sbt`):
```scala
lazy val factGraph = crossProject(JSPlatform, JVMPlatform)
  .in(file("."))
  .settings(scalaVersion := "3.3.6")
```

---

## Data Flow Summary

**Tax Calculation Flow:**

```
1. User Input
   └─> graph.set("/age", Int(30))
       └─> Persister stores value
           └─> Caches cleared

2. Derived Fact Request
   └─> graph.get("/taxCredit")
       └─> Fact.get() called
           └─> Expression.get() evaluates
               └─> Dependencies resolved recursively
                   ├─> Writable facts: Load from Persister
                   └─> Derived facts: Evaluate expression
                       └─> Result cached in resultCache

3. Validation
   └─> graph.save()
       └─> All writable facts validated against Limits
           └─> Returns (valid: Boolean, violations: Seq[LimitViolation])

4. Persistence
   └─> persister.toJson()
       └─> Serialize state to JSON
           └─> Store for later restoration
```

**Path Resolution Flow:**

```
Path: "/familyAndHousehold/*/age"
  │
  ├─> Parse into PathItems: [Child("familyAndHousehold"), Wildcard, Child("age")]
  │
  ├─> Navigate from root Fact
  │   └─> Get child "familyAndHousehold" (Collection)
  │       └─> Expand Wildcard to UUIDs: [uuid1, uuid2, uuid3]
  │           └─> For each UUID:
  │               └─> Get child "age"
  │
  └─> Return: MaybeVector.Multiple([
        Result.Complete(Int(8)),
        Result.Complete(Int(12)),
        Result.Incomplete
      ])
```

**Expression Evaluation Flow:**

```
<Derived>
  <Add>
    <Dependency path="/income" />
    <Dependency path="/bonus" />
  </Add>
</Derived>

Evaluation:
  1. Add.get() called
  2. Evaluate left dependency: /income → Result.Complete(Dollar(50000))
  3. Evaluate right dependency: /bonus → Result.Complete(Dollar(5000))
  4. Apply BinaryOperator.add: Dollar(50000) + Dollar(5000)
  5. Return: Result.Complete(Dollar(55000))
```

---

## Related Documentation

- [Core Concepts](core-concepts.md) - Understanding facts, graphs, paths, and collections
- [Expression Evaluation](expression-evaluation.md) - Deep dive into the evaluation engine
- [Persistence Guide](persistence-guide.md) - How data is stored and retrieved
