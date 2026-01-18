# Monads and Result Handling

The Fact Graph uses custom monads to handle partial data, collections, and lazy evaluation. Understanding these monads is critical for working with the codebase.

---

## Result[A] (Complete, Placeholder, Incomplete)

**Purpose**: Represents the completeness state of a fact's value.

**Definition** (`monads/Result.scala`):
```scala
enum Result[+A]:
  case Complete(value: A)                    // Value is known and final
  case Placeholder(value: A)                 // Value is default (dependencies incomplete)
  case Incomplete                            // Value cannot be calculated
```

### States

1. **Complete(value)**:
   - Fact has a definitive, final value
   - All dependencies are satisfied
   - User has provided a writable value, or derived fact successfully evaluated
   - Example: User enters age as 30 → `Result.Complete(Int(30))`

2. **Placeholder(value)**:
   - Fact has a temporary/default value
   - Dependencies are incomplete, but placeholder allows calculation to proceed
   - Used when writable fact is empty but has a `<Placeholder>` definition
   - Example: User hasn't entered dependents, placeholder is 0 → `Result.Placeholder(Int(0))`

3. **Incomplete**:
   - Fact cannot be evaluated
   - Missing required dependencies
   - Division by zero or other error conditions
   - Example: Derived fact depends on empty writable with no placeholder → `Result.Incomplete`

### Methods

```scala
// Check completeness
result.complete: Boolean              // true for Complete, false for Placeholder/Incomplete
result.hasValue: Boolean              // true for Complete/Placeholder, false for Incomplete

// Extract value (throws if Incomplete)
result.get: A                         // Extracts value, throws on Incomplete

// Transform value
result.map(f: A => B): Result[B]      // Apply function to value if present
result.flatMap(f: A => Result[B]): Result[B]

// Convert to placeholder
result.asPlaceholder: Result[A]       // Convert Complete → Placeholder
```

### Usage Example

```scala
val result: Result[Int] = graph.get("/age")

result match
  case Result.Complete(age) =>
    println(s"User age: $age")
  case Result.Placeholder(age) =>
    println(s"Using default age: $age")
  case Result.Incomplete =>
    println("Age not available")
```

### Completeness Propagation

- If any dependency is Incomplete, result is Incomplete
- If any dependency is Placeholder, result is Placeholder (unless explicitly Complete)
- Only when all dependencies are Complete is result Complete

---

## MaybeVector[A] (Single, Multiple)

**Purpose**: Handles values that may be a single item or a collection of items (from wildcard paths).

**Definition** (`monads/MaybeVector.scala`):
```scala
enum MaybeVector[+A]:
  case Single(value: A)                      // Single value
  case Multiple(vector: Vector[A], complete: Boolean)  // Multiple values
```

### States

1. **Single(value)**:
   - Represents a single fact value
   - Result of accessing non-wildcard path: `/age`
   - Example: `MaybeVector.Single(Result.Complete(Int(30)))`

2. **Multiple(vector, complete)**:
   - Represents multiple values from a collection
   - Result of accessing wildcard path: `/collection/*/age`
   - `complete` flag indicates if all items were evaluated
   - Example: `MaybeVector.Multiple(Vector(Result.Complete(Int(8)), Result.Complete(Int(12))), true)`

### Methods

```scala
// Extract values
mv.toVector: Vector[A]                // Convert to vector (Single → Vector of 1)
mv.toList: List[A]                    // Convert to list

// Transform values
mv.map(f: A => B): MaybeVector[B]     // Apply function to all values
mv.flatMap(f: A => MaybeVector[B]): MaybeVector[B]

// Check state
mv.isSingle: Boolean                  // true if Single
mv.isMultiple: Boolean                // true if Multiple
```

### Vectorization

Operations can be "vectorized" to work element-wise on collections:

```scala
// vectorize2: Binary operation on MaybeVectors
def vectorize2[A, B, C](
  a: MaybeVector[A],
  b: MaybeVector[B],
  f: (A, B) => C
): MaybeVector[C]

// If both Single: apply f to values
vectorize2(Single(x), Single(y), f) = Single(f(x, y))

// If one Multiple: apply f element-wise
vectorize2(Multiple([x1, x2]), Single(y), f) = Multiple([f(x1, y), f(x2, y)])

// If both Multiple: apply f pairwise (lengths must match)
vectorize2(Multiple([x1, x2]), Multiple([y1, y2]), f) = Multiple([f(x1, y1), f(x2, y2)])
```

**Example - Addition with Collections:**
```scala
// Paths:
// /collection/*/base = [100, 200, 300]
// /bonus = 50

// Expression: Add(/collection/*/base, /bonus)
val bases = Multiple(Vector(100, 200, 300), true)
val bonus = Single(50)

// Vectorized addition:
vectorize2(bases, bonus, _ + _)
// Result: Multiple(Vector(150, 250, 350), true)
```

---

## Thunk[A] (Lazy Evaluation)

**Purpose**: Wrapper for lazy evaluation of expressions.

**Definition** (`monads/Thunk.scala`):
```scala
class Thunk[+A](private val compute: () => A):
  lazy val get: A = compute()
```

### Characteristics

- **Lazy**: Expression not evaluated until `get` is called
- **Memoized**: Result cached after first evaluation
- **Performance**: Critical for large graphs with many facts
- **Usage**: Every `Expression` has a `getThunk()` method that returns `Thunk[MaybeVector[Result[A]]]`

### Why Thunks?

Without lazy evaluation, creating a fact graph would immediately evaluate all derived facts, even those never accessed. Thunks defer computation until needed.

**Example:**
```scala
// Expression defines getThunk
def getThunk(using Factual): Thunk[MaybeVector[Result[A]]] =
  Thunk(() => this.get)

// Fact stores thunk
val thunk: Thunk[MaybeVector[Result[Int]]] = expr.getThunk

// Evaluation deferred until:
val result = thunk.get  // Now expression evaluates
```

**Call Stack:**
```
graph.get("/derivedFact")
  → Fact.get()
    → Expression.getThunk()  # Returns Thunk (not yet evaluated)
      → thunk.get            # Triggers evaluation
        → Expression.get()   # Actual computation
          → Dependencies evaluated recursively
```

---

## Vectorization Methods

Vectorization allows operations to seamlessly work with both single values and collections.

### vectorize2 (Binary operations)

```scala
def vectorize2[A, B, C](
  a: MaybeVector[Result[A]],
  b: MaybeVector[Result[B]],
  f: (A, B) => C
): MaybeVector[Result[C]]
```

**Behavior:**
- **Both Single**: Apply `f` to unwrapped values
- **One Multiple**: Apply `f` to each element with the single value
- **Both Multiple**: Apply `f` element-wise (vectors must have same length)
- **Completeness**: Result is Complete only if both inputs are Complete

**Example - Multiply:**
```xml
<Multiply>
  <Dependency path="/prices/*/amount" />  <!-- [100, 200, 300] -->
  <Rational>15/100</Rational>              <!-- 0.15 -->
</Multiply>
```

```scala
val prices = Multiple(Vector(
  Result.Complete(Dollar(10000)),
  Result.Complete(Dollar(20000)),
  Result.Complete(Dollar(30000))
), true)

val rate = Single(Result.Complete(Rational(15, 100)))

// Vectorize multiplication
vectorize2(prices, rate, (a, b) => a * b)

// Result: Multiple(Vector(
//   Result.Complete(Dollar(1500)),
//   Result.Complete(Dollar(3000)),
//   Result.Complete(Dollar(4500))
// ), true)
```

### vectorize4 (4-ary operations)

```scala
def vectorize4[A, B, C, D, E](
  a: MaybeVector[Result[A]],
  b: MaybeVector[Result[B]],
  c: MaybeVector[Result[C]],
  d: MaybeVector[Result[D]],
  f: (A, B, C, D) => E
): MaybeVector[Result[E]]
```

### vectorizeList (Variable arity)

```scala
def vectorizeList[A, B](
  items: List[MaybeVector[Result[A]]],
  f: List[A] => B
): MaybeVector[Result[B]]
```
- Handles operations with variable number of arguments (Add, Multiply, All, Any)
- If any item is Multiple, all must have same length

### vectorizeListTuple2 (List of pairs)

```scala
def vectorizeListTuple2[A, B, C](
  items: List[(MaybeVector[Result[A]], MaybeVector[Result[B]])],
  f: List[(A, B)] => C
): MaybeVector[Result[C]]
```
- Used for operations with paired arguments (ConditionalList, EnumOptions)

### Length Validation

All vectorization methods validate that Multiple vectors have matching lengths:
```scala
// Valid: Same length
Multiple(Vector(1, 2, 3)) + Multiple(Vector(4, 5, 6))  ✓

// Invalid: Different lengths
Multiple(Vector(1, 2, 3)) + Multiple(Vector(4, 5))     ✗
// Throws: IllegalArgumentException
```

---

## Related Documentation

- [Expression Evaluation](expression-evaluation.md) - How expressions use these monads
- [Core Concepts](core-concepts.md) - Understanding collections and wildcards
- [Architecture Overview](architecture-overview.md) - System design principles
