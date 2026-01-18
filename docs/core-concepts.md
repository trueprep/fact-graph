# Core Concepts

This document explains the fundamental concepts that underpin the Fact Graph system.

---

## Fact Dictionary

A **Fact Dictionary** is a collection of fact definitions represented as XML documents. Each dictionary defines the complete tax logic for a specific domain (e.g., filing status, dependents, income).

**Key Characteristics:**
- **XML Format**: Fact Dictionaries use FXML (Fact XML) markup language
- **Validation**: Validated against `FactDictionaryModule.rng` RelaxNG schema
- **Modularity**: Each XML file is a module that can reference other modules
- **Immutability**: Dictionaries are frozen after loading (no runtime modifications)
- **Versioning**: Dictionaries include metadata for version tracking

**Dictionary Structure:**
```xml
<?xml-model href="./FactDictionaryModule.rng"?>
<FactDictionaryModule>
  <Meta>
    <Version>1.0.0</Version>
    <TestDictionary>false</TestDictionary>
  </Meta>
  <Facts>
    <Fact path="/factPath1">...</Fact>
    <Fact path="/factPath2">...</Fact>
    <!-- More facts -->
  </Facts>
</FactDictionaryModule>
```

**Implementation** (`FactDictionary.scala`):
- `definitions: mutable.Map[Path, FactDefinition]` - Stores fact definitions by abstract path
- `meta: MetaConfigTrait` - Metadata (version, test flag)
- `frozen: Boolean` - Lock state to prevent modifications
- `addDefinition(def: FactDefinition)` - Add fact definition
- `freeze()` - Lock dictionary and validate metadata
- `fromXml(xmlString: String)` - Parse XML into dictionary

**Loading a Dictionary:**
```scala
val xmlString = Source.fromFile("fact_dictionaries/filingStatus.xml").mkString
val dictionary = FactDictionary.importFromXml(xmlString)
dictionary.freeze()  // Required before creating graphs
```

---

## Fact Graph

A **Fact Graph** is an instantiation of a Fact Dictionary applied to a specific user's tax scenario. It represents a single taxpayer's in-progress or completed tax return.

**Key Characteristics:**
- **Instance-Specific**: Each taxpayer gets their own Graph instance
- **Stateful**: Maintains user-provided values and computed results
- **Persistent**: Can be serialized to JSON and restored
- **Navigable**: Facts organized in a tree structure with path-based access
- **Cached**: Computed results cached for performance

**Graph Components:**
- **Root Fact**: Every graph has a root fact at path `/`
- **Dictionary Reference**: Links to the FactDictionary (shared across graphs)
- **Persister**: Stores writable fact values (default: `InMemoryPersister`)
- **Fact Cache**: Maps paths to Fact instances (navigational cache)
- **Result Cache**: Maps paths to computed results (evaluation cache)
- **Override Set**: Tracks facts with active overrides

**Implementation** (`Graph.scala`):
```scala
case class Graph(
  dictionary: FactDictionary,
  persister: Persister = InMemoryPersister()
):
  private val factCache: mutable.Map[Path, Fact] = mutable.Map()
  private val resultCache: mutable.Map[String, Result[?]] = mutable.Map()
  private val overriddenFacts: mutable.Set[Path] = mutable.Set()
```

**Graph API Methods:**
- `get[A](path: String): Result[A]` - Get single fact value
- `getVect[A](path: String): Vector[Result[A]]` - Get collection of values
- `set(path: String, value: WritableType): (Boolean, Seq[LimitViolation])` - Set fact value
- `delete(path: String): Unit` - Delete fact value
- `addToCollection(path: String, uuid: String): Unit` - Add collection item
- `removeFromCollection(path: String, uuid: String): Unit` - Remove collection item
- `save(): (Boolean, Seq[LimitViolation])` - Validate and persist graph
- `explain(path: String): String` - Generate human-readable explanation

**Creating a Graph:**
```scala
val dictionary = FactDictionary.importFromXml(xmlString)
val graph = Graph(dictionary)

// With custom persister
val customPersister = MyDatabasePersister()
val graph = Graph(dictionary, customPersister)
```

---

## Fact Definition vs Fact Instance

Understanding the distinction between `FactDefinition` and `Fact` is critical:

**FactDefinition** (Blueprint):
- Lives in the `FactDictionary`
- Represents the template/schema for a fact
- Contains the expression tree (CompNode) as a builder function
- Not attached to any specific graph
- Immutable and shared across all graphs
- Location: `FactDefinition.scala`

**Fact** (Instance):
- Lives in a `Graph`
- Represents a specific instance of a fact for a user
- Has a reference to its parent Fact and child Facts
- Connected to the Persister for value storage
- Evaluates the expression tree from FactDefinition
- Location: `Fact.scala`

**Relationship:**
```scala
// FactDefinition (in dictionary)
case class FactDefinition(
  path: Path,                                  // Abstract path with wildcards
  cnBuilder: Factual ?=> CompNode,             // Expression builder
  limitsBuilder: Factual ?=> Seq[Limit],       // Limits builder
  dictionary: FactDictionary
):
  def attachToGraph(using Factual): Fact = ... // Creates Fact instance

// Fact (in graph)
case class Fact(
  value: CompNode,                             // Evaluated expression
  path: Path,                                  // Concrete path with UUIDs
  limits: Seq[Limit],                          // Evaluated limits
  graph: Graph,                                // Parent graph
  parent: Option[Fact],                        // Parent fact
  meta: Factual.Meta                           // Metadata
)
```

**Lifecycle:**
```
1. XML Parsing:
   <Fact path="/age"> → FactDefinition(path="/age", cnBuilder=...)

2. Graph Creation:
   FactDefinition.attachToGraph() → Fact(path="/age", value=IntNode, ...)

3. Fact Resolution:
   graph.get("/age") → Navigates to Fact instance → Evaluates expression
```

---

## Paths

**Paths** are string identifiers that uniquely reference facts within the graph. They use a file-system-like syntax with forward slashes as delimiters.

### Path Syntax

**Absolute Paths** (start with `/`):
```
/filingStatus
/familyAndHousehold/primaryFiler/age
/income/wages/totalWages
```

**Relative Paths**:
```
../siblingFact          # Parent's child
./childFact             # Current fact's child
../../grandparent       # Two levels up
```

**Wildcard Paths** (collection iteration):
```
/familyAndHousehold/*/age           # All members' ages
/expenses/*/amount                  # All expense amounts
/schedule/*/line/*/value            # Nested collections
```

**Member Paths** (specific collection item):
```
/familyAndHousehold/#uuid-123/age   # Specific member's age
/collection/#abc-def/value          # Specific item's value
```

**Module-Prefixed Paths** (cross-module references):
```xml
<Dependency module="filers" path="/primaryFiler/tin" />
<Dependency module="income" path="/totalIncome" />
```

### PathItem Types

Paths are parsed into sequences of `PathItem` components:

```scala
enum PathItem:
  case Child(name: String)      // Named child: "age", "filingStatus"
  case Wildcard                 // Collection wildcard: *
  case Member(uuid: String)     # Specific collection member: #uuid
  case Parent                   # Parent reference: ..
  case Unknown(raw: String)     # Unparsed segment
```

**Parsing Example:**
```
Path: "/familyAndHousehold/*/age"
  ↓
PathItems: [
  PathItem.Child("familyAndHousehold"),
  PathItem.Wildcard,
  PathItem.Child("age")
]
```

**Implementation** (`Path.scala`):
```scala
case class Path(_items: List[PathItem], absolute: Boolean):
  def isAbstract: Boolean = _items.exists(_.isWildcard)
  def isWildcard: Boolean = _items.lastOption.exists(_.isWildcard)
  def asAbstract: Path = ... // Convert UUIDs to wildcards
  def populateWildcards(uuids: Vector[String]): Vector[Path] = ...
```

### Abstract vs Concrete Paths

**Abstract Path**:
- Contains wildcards (`*`)
- Represents a pattern matching multiple facts
- Used in `FactDefinition` to define templates
- Example: `/familyAndHousehold/*/age`

**Concrete Path**:
- Contains specific UUIDs (`#uuid`)
- Represents a single fact instance
- Used in `Fact` instances in the graph
- Example: `/familyAndHousehold/#uuid-123/age`

**Conversion:**
```scala
// Abstract → Concrete (wildcard expansion)
val abstractPath = Path.fromString("/collection/*/value")
val uuids = Vector("uuid-1", "uuid-2", "uuid-3")
val concretePaths: Vector[Path] = abstractPath.populateWildcards(uuids)
// Results:
//   /collection/#uuid-1/value
//   /collection/#uuid-2/value
//   /collection/#uuid-3/value

// Concrete → Abstract (UUID removal)
val concretePath = Path.fromString("/collection/#uuid-1/value")
val abstractPath: Path = concretePath.asAbstract
// Result: /collection/*/value
```

---

## Collections

**Collections** are ordered lists of items within the fact graph, where each item is identified by a UUID.

### UUID Identification

- **UUID Format**: Collections use string UUIDs (e.g., `"550e8400-e29b-41d4-a716-446655440000"`)
- **Ordering**: Collections maintain insertion order
- **Storage**: Stored in Persister as `Collection(Vector[String])`
- **Uniqueness**: Each UUID should be unique within a collection

**Collection Operations:**
```scala
// Add item to collection
graph.addToCollection("/familyAndHousehold", "uuid-123")
graph.addToCollection("/familyAndHousehold", "uuid-456")

// Remove item from collection
graph.removeFromCollection("/familyAndHousehold", "uuid-123")

// Get collection (returns Collection type)
val collection: Result[Collection] = graph.get("/familyAndHousehold")
// Collection(Vector("uuid-456"))
```

### Wildcard Expansion

Wildcards in paths are expanded to all UUIDs in the corresponding collection:

```scala
// Setup: Collection has 3 items
graph.addToCollection("/expenses", "uuid-1")
graph.addToCollection("/expenses", "uuid-2")
graph.addToCollection("/expenses", "uuid-3")

// Wildcard path: /expenses/*/amount
// Expands to:
//   /expenses/#uuid-1/amount
//   /expenses/#uuid-2/amount
//   /expenses/#uuid-3/amount

// Retrieve all amounts
val amounts: Vector[Result[Dollar]] = graph.getVect("/expenses/*/amount")
// Returns vector with 3 results
```

**Expression Evaluation with Wildcards:**
```xml
<!-- Sum all expense amounts -->
<CollectionSum>
  <Dependency path="/expenses/*/amount" />
</CollectionSum>

<!-- Evaluates to sum of all amounts in collection -->
```

### Member Access

Access specific collection items using the `#UUID` syntax:

```scala
// Set value for specific member
graph.set("/familyAndHousehold/#uuid-123/age", Int(8))
graph.set("/familyAndHousehold/#uuid-123/name", Str("Alice"))

// Get value for specific member
val age: Result[Int] = graph.get("/familyAndHousehold/#uuid-123/age")
```

**Nested Collections:**
```
/schedule/*/line/*/value

First wildcard:  /schedule/*
  Expands to:    /schedule/#sched-1
                 /schedule/#sched-2

Second wildcard: /schedule/#sched-1/line/*
  Expands to:    /schedule/#sched-1/line/#line-a
                 /schedule/#sched-1/line/#line-b

Final paths:     /schedule/#sched-1/line/#line-a/value
                 /schedule/#sched-1/line/#line-b/value
                 /schedule/#sched-2/line/#line-c/value
                 /schedule/#sched-2/line/#line-d/value
```

---

## Related Documentation

- [Architecture Overview](architecture-overview.md) - System design and data flow
- [FXML Schema Reference](fxml-schema-reference.md) - How to define facts in XML
- [Type System](type-system.md) - Available data types and their behavior
