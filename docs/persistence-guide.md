# Persistence Guide

Persistence manages the storage and retrieval of fact values across sessions.

---

## Persister Interface

**Persister Trait** (`persisters/Persister.scala`):

```scala
trait Persister:
  // Store writable fact value
  def setFact(fact: Fact, value: WritableType): Unit

  // Retrieve saved result for a fact
  def getSavedResult[A](path: Path, klass: Class[A]): Result[A]

  // Delete fact value
  def deleteFact(path: Path): Unit

  // Sync with dictionary (remove facts not in dictionary)
  def syncWithDictionary(dictionary: FactDictionary): Unit

  // Validate all facts and return violations
  def save(): (Boolean, Seq[LimitViolation])

  // Serialize to JSON
  def toJson(indent: Int = 0): String

  // Serialize to Map
  def toMap: Map[String, WritableType]
```

### Key Responsibilities

1. **Storage**: Persist writable fact values
2. **Retrieval**: Load previously stored values
3. **Validation**: Enforce limits on save
4. **Serialization**: Convert state to JSON for storage
5. **Deserialization**: Restore state from JSON
6. **Synchronization**: Align with dictionary changes

---

## InMemoryPersister

**Default Implementation** (`persisters/InMemoryPersister.scala`):

```scala
class InMemoryPersister(
  var data: mutable.Map[String, WritableType] = mutable.Map(),
  var graph: Option[Graph] = None
) extends Persister:

  def setFact(fact: Fact, value: WritableType): Unit =
    data(fact.path.toString) = value

  def getSavedResult[A](path: Path, klass: Class[A]): Result[A] =
    data.get(path.toString) match
      case Some(value) if klass.isInstance(value) =>
        Result.Complete(value.asInstanceOf[A])
      case Some(value) =>
        throw FactGraphValidationException(s"Type mismatch at $path")
      case None =>
        Result.Incomplete

  def deleteFact(path: Path): Unit =
    data.remove(path.toString)

  def toJson(indent: Int = 0): String =
    val json = ujson.Obj(
      "facts" -> ujson.Obj(data.map((k, v) => k -> v.toJson).toSeq: _*),
      "migrations" -> ujson.Num(Migrations.TotalMigrations)
    )
    ujson.write(json, indent)
```

### Characteristics

- **In-Memory**: Stores all data in memory (Map[String, WritableType])
- **Fast**: No I/O overhead
- **Volatile**: Data lost when process ends (unless serialized)
- **Default**: Used when no custom persister provided

### Creating Graphs with InMemoryPersister

```scala
// Default: new InMemoryPersister
val graph = Graph(dictionary)

// Explicit:
val persister = InMemoryPersister()
val graph = Graph(dictionary, persister)

// From existing data:
val data = mutable.Map[String, WritableType](
  "/age" -> Int(30),
  "/filingStatus" -> Enum("/filingStatusOptions", "single")
)
val persister = InMemoryPersister(data)
val graph = Graph(dictionary, persister)
```

---

## JSON Serialization Format

### Persister JSON Structure

```json
{
  "facts": {
    "/age": 30,
    "/filingStatus": "single",
    "/income/wages": 5000000,
    "/familyAndHousehold": ["#uuid-1", "#uuid-2"],
    "/familyAndHousehold/#uuid-1/age": 8,
    "/familyAndHousehold/#uuid-1/name": "Alice",
    "/familyAndHousehold/#uuid-2/age": 12,
    "/familyAndHousehold/#uuid-2/name": "Bob"
  },
  "migrations": 2
}
```

### Type Serialization

Each `WritableType` defines its `toJson` representation:

```scala
// Simple types - native JSON types
Boolean(true).toJson              // true
Int(42).toJson                    // 42
Str("hello").toJson               // "hello"

// Dollar - stored as cents (integer)
Dollar(10050).toJson              // 10050

// Rational - string representation
Rational(12, 100).toJson          // "12/100"

// Day - ISO 8601 string
Day(LocalDate.of(2024, 1, 15)).toJson  // "2024-01-15"

// Enum - string value
Enum("/options", "value1").toJson      // "value1"

// MultiEnum - array of strings
MultiEnum("/options", List("a", "b")).toJson  // ["a", "b"]

// Collection - array of UUIDs
Collection(Vector("uuid-1", "uuid-2")).toJson  // ["uuid-1", "uuid-2"]

// Address - object
Address(...).toJson                    // { "street": "...", "city": "...", ... }
```

### Migrations Field

```json
{
  "facts": { ... },
  "migrations": 2
}
```
- Tracks which migrations have been applied
- Incremented when new migrations run
- See migration system documentation for details

### Serialization

```scala
// Save graph state to JSON
val json: String = graph.getPersister().toJson(indent = 2)

// Write to file
Files.write(Paths.get("save.json"), json.getBytes)
```

### Deserialization

```scala
// Read JSON from file
val json = Source.fromFile("save.json").mkString

// Parse and restore
val data = ujson.read(json)
val facts = data("facts").obj.map: (k, v) =>
  k -> WritableType.fromJson(v)  // Convert JSON to WritableType
.toMap

val persister = InMemoryPersister(mutable.Map.from(facts))
val graph = Graph(dictionary, persister)
```

---

## Save/Validate Cycle

### Save Process

```scala
def save(): (Boolean, Seq[LimitViolation]) =
  // 1. Collect all writable facts
  val writableFacts = collectWritableFacts(graph)

  // 2. Validate each fact's limits
  val violations = writableFacts.flatMap: fact =>
    fact.limits.flatMap: limit =>
      limit.run()  // Returns Option[LimitViolation]

  // 3. Determine validity
  val valid = violations.isEmpty

  // 4. Clear result cache
  graph.resultCache.clear()

  // 5. Return result
  (valid, violations)
```

### Usage Pattern

```scala
// 1. User interaction - set multiple facts
graph.set("/age", Int(30))
graph.set("/income", Dollar(5000000))
graph.set("/filingStatus", Enum("/filingStatusOptions", "single"))

// 2. Validate all facts
val (valid, violations) = graph.save()

// 3. Handle result
if valid then
  // Success: serialize and store
  val json = graph.getPersister().toJson()
  saveToDatabase(json)
else
  // Failure: show errors to user
  violations.foreach: v =>
    showError(v.path, v.message)
```

### Validation Timing

1. **Immediate (optional)**: `graph.set(path, value, validate = true)`
   - Single fact validated on set
   - Throws exception on violation
   - Not recommended for UI (poor UX)

2. **Deferred (recommended)**: `graph.save()`
   - All facts validated together
   - Returns all violations at once
   - Better UX: show all errors together

---

## Custom Persister Implementation

```scala
class DatabasePersister(db: Database) extends Persister:
  def setFact(fact: Fact, value: WritableType): Unit =
    db.execute(s"INSERT INTO facts VALUES ('${fact.path}', '${value.toJson}')")

  def getSavedResult[A](path: Path, klass: Class[A]): Result[A] =
    val row = db.query(s"SELECT value FROM facts WHERE path = '${path}'")
    // ... parse and return

  def save(): (Boolean, Seq[LimitViolation]) =
    val result = super.save()  // Validate using trait logic
    if result._1 then
      db.commit()  // Persist to database
    else
      db.rollback()  // Revert on validation failure
    result

  // ... other methods
```

---

## Related Documentation

- [Validation System](validation-system.md) - How limits are checked during save
- [Type System](type-system.md) - How types are serialized to JSON
- [Core Concepts](core-concepts.md) - Understanding graphs and facts
