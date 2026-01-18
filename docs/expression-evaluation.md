# Expression Evaluation

Expressions form the computational core of the Fact Graph, representing all derived fact logic.

---

## Expression Enum Cases

**Expression** is defined as a Scala 3 enum with cases for each operation type:

```scala
enum Expression[A]:
  // Constants
  case Constant(value: A)

  // Writable values
  case Writable(fact: Fact)

  // Dependencies
  case Dependency(path: Path, module: Option[String])

  // Conditionals
  case Switch(cases: List[(Expression[Boolean], Expression[A])])

  // Unary operations
  case Unary[A, B](expr: Expression[A], op: UnaryOperator[A, B])

  // Binary operations
  case Binary[A, B, C](
    left: Expression[A],
    right: Expression[B],
    op: BinaryOperator[A, B, C]
  )

  // Aggregate operations (collections)
  case Aggregate[A, B](
    expr: Expression[A],
    op: AggregateOperator[A, B]
  )

  // Collection operations
  case Filter(collectionPath: Path, condition: Expression[Boolean])
  case Find(collectionPath: Path, condition: Expression[Boolean])
  case Collect(items: List[Expression[A]])

  // ... additional cases
```

### Key Expression Cases

1. **Constant(value)**: Literal value (`<Int>100</Int>`)
2. **Writable(fact)**: Reference to writable fact storage
3. **Dependency(path, module)**: Reference to another fact (`<Dependency path="..." />`)
4. **Switch(cases)**: Conditional logic (`<Switch><Case>...</Case></Switch>`)
5. **Unary(expr, op)**: Single-argument operation (Not, Length, etc.)
6. **Binary(left, right, op)**: Two-argument operation (Add, Equal, etc.)
7. **Aggregate(expr, op)**: Collection aggregation (Count, CollectionSum)

---

## Evaluation Flow

**Primary Evaluation Method:**
```scala
def get(using Factual): MaybeVector[Result[A]]
```

### Evaluation Steps

1. **Entry Point**: `graph.get("/factPath")` called
2. **Path Resolution**: Navigate fact tree to target fact
3. **Expression Retrieval**: Get fact's CompNode expression
4. **Thunk Creation**: `expr.getThunk()` creates lazy wrapper
5. **Evaluation**: `thunk.get` triggers computation
6. **Dependency Resolution**: Recursively evaluate dependencies
7. **Result Combination**: Combine results using operation logic
8. **Cache Storage**: Store result in `resultCache`
9. **Return**: Return `Result[A]` to caller

### Example Evaluation - Simple Addition

```xml
<Derived>
  <Add>
    <Dependency path="/income" />
    <Dependency path="/bonus" />
  </Add>
</Derived>
```

**Evaluation trace:**
```scala
// 1. AddNode.get() called
AddNode(children = [
  DependencyNode("/income"),
  DependencyNode("/bonus")
]).get

// 2. Evaluate dependencies
val incomeResult = DependencyNode("/income").get
  → Navigate to /income fact
  → Get from persister
  → Result.Complete(Dollar(50000))

val bonusResult = DependencyNode("/bonus").get
  → Navigate to /bonus fact
  → Get from persister
  → Result.Complete(Dollar(5000))

// 3. Vectorize addition
vectorizeList(
  [Single(Complete(50000)), Single(Complete(5000))],
  values => values.sum
)

// 4. Result
Single(Complete(Dollar(55000)))
```

### Example Evaluation - Collection Aggregation

```xml
<Derived>
  <CollectionSum>
    <Dependency path="/expenses/*/amount" />
  </Dependency>
</Derived>
```

**Evaluation trace:**
```scala
// 1. CollectionSumNode.get() called
// 2. Evaluate wildcard dependency
DependencyNode("/expenses/*/amount").get
  → Get collection: /expenses = Collection([uuid1, uuid2, uuid3])
  → Expand wildcard to:
      /expenses/#uuid1/amount → Complete(Dollar(100))
      /expenses/#uuid2/amount → Complete(Dollar(200))
      /expenses/#uuid3/amount → Complete(Dollar(150))
  → Result: Multiple(Vector(100, 200, 150), true)

// 3. Aggregate operation
CollectionSumOperator.aggregate([100, 200, 150])
  → Sum: 100 + 200 + 150 = 450

// 4. Result
Single(Complete(Dollar(450)))
```

---

## Caching Behavior

The Graph maintains two caches to optimize performance:

### Fact Cache

`factCache: Map[Path, Fact]`
- **Purpose**: Navigational cache - stores Fact instances by path
- **Population**: Facts added on first access via path resolution
- **Invalidation**: Never cleared (facts are immutable structural entities)
- **Benefit**: Avoids re-creating Fact objects for same path

### Result Cache

`resultCache: Map[String, Result[?]]`
- **Purpose**: Computation cache - stores evaluated results
- **Population**: Results stored after expression evaluation
- **Invalidation**: Cleared when `save()` called or writable facts modified
- **Benefit**: Avoids re-computing expensive derived facts

### Cache Usage

```scala
class Graph:
  private val factCache: mutable.Map[Path, Fact] = mutable.Map()
  private val resultCache: mutable.Map[String, Result[?]] = mutable.Map()

  def get[A](path: String): Result[A] =
    // Check result cache first
    val cacheKey = path
    resultCache.get(cacheKey) match
      case Some(cached) => return cached.asInstanceOf[Result[A]]
      case None => ()

    // Evaluate and cache
    val fact = resolveFact(path)  // Uses factCache
    val result = fact.get          // Evaluates expression
    resultCache(cacheKey) = result
    result
```

### Cache Invalidation

```scala
def set(path: String, value: WritableType): Unit =
  persister.setFact(fact, value)
  resultCache.clear()  // Clear all cached results

def save(): (Boolean, Seq[LimitViolation]) =
  val result = persister.save()
  resultCache.clear()  // Clear after validation
  result
```

**Why Clear on Save?**
- Writable facts may have changed during user interaction
- Derived facts depending on changed facts need re-evaluation
- Ensures consistency between persisted state and computed results

---

## Thunk Resolution

### Thunk Resolution Process

1. **Thunk Creation**:
```scala
def getThunk(using Factual): Thunk[MaybeVector[Result[A]]] =
  Thunk(() => this.get)
```

2. **Deferred Evaluation**:
```scala
val thunk: Thunk[MaybeVector[Result[Int]]] = expr.getThunk
// Expression NOT evaluated yet
```

3. **Forced Evaluation**:
```scala
val result: MaybeVector[Result[Int]] = thunk.get
// NOW expression evaluates (and result is memoized)
```

4. **Memoization**:
```scala
class Thunk[+A](private val compute: () => A):
  lazy val get: A = compute()  // 'lazy' ensures single evaluation
```

### Benefits

- **Performance**: Facts evaluated only when accessed
- **Circular Dependencies**: Thunks enable lazy resolution patterns
- **Memory**: Unevaluated thunks use minimal memory
- **Selective Evaluation**: Only relevant facts computed

### Thunk in Fact Evaluation

```scala
// Fact.scala
def get[A](using Factual): Result[A] =
  val thunk = value.expr.getThunk  // Create thunk
  val mv = thunk.get               // Force evaluation
  mv match
    case MaybeVector.Single(result) => result
    case MaybeVector.Multiple(_, _) =>
      throw Exception("Expected single value, got collection")
```

### Thunk Chain Example

```
graph.get("/finalCalculation")
  → Thunk[/finalCalculation]
    → .get forces evaluation
      → DependencyNode("/intermediate").getThunk
        → Thunk[/intermediate]
          → .get forces evaluation
            → DependencyNode("/base").getThunk
              → Thunk[/base]
                → .get forces evaluation
                  → Writable: load from persister
```

### Thunks and Collections

```scala
// Collection wildcard creates multiple thunks
DependencyNode("/collection/*/value").get

// For each UUID in collection:
uuids.map: uuid =>
  val fact = resolveFact(s"/collection/#$uuid/value")
  fact.value.expr.getThunk  // Separate thunk per item
```

---

## Related Documentation

- [Monads and Result Handling](monads-and-result-handling.md) - Result, MaybeVector, Thunk details
- [FXML Schema Reference](fxml-schema-reference.md) - Expression types in FXML
- [Architecture Overview](architecture-overview.md) - System design and data flow
