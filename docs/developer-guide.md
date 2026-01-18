# Developer Guide

A comprehensive guide for developers working on the Fact Graph codebase, covering common tasks, testing practices, and code organization.

---

## Common Development Tasks

### Adding a New Fact

Follow these steps to add a fact to the dictionary:

1. **Select Module** - Choose the appropriate XML file in `fact_dictionaries/`
   - Organize by domain: income, deductions, credits, family, etc.
   - Keep related facts together in the same module

2. **Define Structure** - Add `<Fact>` element with metadata

```xml
<Fact path="/newFactPath">
  <Name>Human Readable Name</Name>
  <Description>Detailed description of what this fact represents</Description>
  <Export downstreamFacts="true" />
  
  <!-- Fact definition follows -->
</Fact>
```

3. **Choose Type** - Either `<Writable>` or `<Derived>` (mutually exclusive)

**For user input (Writable):**

```xml
<Writable>
  <Int />  <!-- or Boolean, Dollar, String, Enum, etc. -->
</Writable>
```

**For calculated values (Derived):**

```xml
<Derived>
  <Add>
    <Dependency path="/income/wages" />
    <Dependency path="/income/tips" />
  </Add>
</Derived>
```

4. **Add Validation** - For writable facts, add `<Limits>` if needed

```xml
<Writable>
  <Int />
  <Limits>
    <Min><Int>0</Int></Min>
    <Max><Int>150</Int></Max>
  </Limits>
</Writable>
```

5. **Verify** - Run tests to validate

```bash
sbt test
```

---

### Modifying a Calculation

To update an existing derived fact:

1. **Locate Fact Definition** - Find the fact in its XML file
   - Use file search or grep: `rg "path=\"/factPath\""`
   - Facts are organized by domain in `fact_dictionaries/`

2. **Update Expression** - Modify the `<Derived>` section
   - Ensure proper XML nesting
   - Maintain consistent indentation

3. **Check Dependencies** - Verify all referenced facts exist
   - Use cross-module syntax if needed: `<Dependency module="moduleName" path="/factPath" />`
   - Test with incomplete data to verify graceful handling

4. **Add/Update Tests** - Create test cases for the new calculation

```scala
test("updated calculation") {
  val graph = Graph(dictionary)
  graph.set("/input1", Int(100))
  graph.set("/input2", Int(50))
  val result = graph.get("/calculatedFact")
  assert(result.get == Int(150))
}
```

---

### Adding a New CompNode Type

To create a new operation type (e.g., a new mathematical function):

1. **Create Node Class** - In `compnodes/` directory

```scala
case class MyOperation(operand: Expression[MyType]) extends CompNode:
  type Value = MyType
  def ValueClass = classOf[MyType]
  
  override def get(using factual: Factual): MaybeVector[Result[MyType]] =
    operand.get.map(_.map(value =>
      // Implement operation logic here
      MyType(/* computed value */)
    ))
```

2. **Implement Factory** - Extend `CompNodeFactory`

```scala
object MyOperationFactory extends CompNodeFactory:
  def tryMake(args: CompNodeFactoryArgs): Option[CompNode] =
    if args.config.name == "MyOperation" then
      Some(MyOperation(args.children.head))
    else
      None
```

3. **Register Factory** - Add to `CompNode.defaultFactories` in `CompNode.scala`

```scala
val defaultFactories = List(
  // ... existing factories ...
  MyOperationFactory,
)
```

4. **Update XML Parsing** - Modify `FactConfigElement` to handle the new XML element
   - Add element name to parsing logic
   - Handle child elements appropriately

5. **Update Schema** - Update `FactDictionaryModule.rng` (RelaxNG schema) if needed
   - Define allowed child elements
   - Specify cardinality rules

---

### Adding a New Writable Type

To create a custom type for user input:

1. **Create Type Class** - In `types/` directory

```scala
case class MyType(value: String) extends WritableType:
  def toJson: ujson.Value = ujson.Str(value)
  
  override def toString: String = value

object MyType:
  def fromJson(json: ujson.Value): MyType =
    MyType(json.str)
```

2. **Add CompNode Implementation** - Create corresponding node

```scala
case class MyTypeNode(expr: Expression[MyType]) extends CompNode:
  type Value = MyType
  def ValueClass = classOf[MyType]
```

3. **Create Factory** - For XML parsing

```scala
object MyTypeFactory extends CompNodeFactory:
  def tryMake(args: CompNodeFactoryArgs): Option[CompNode] =
    if args.config.name == "MyType" then
      // Parse from XML and create node
      Some(MyTypeNode(/* ... */))
    else
      None
```

4. **Register Factory** - Add to factory list
5. **Add Serialization** - Ensure JSON serialization works in `TypeContainer`

---

### Debugging Fact Evaluation

The Graph API provides several debugging methods:

**Explain a fact's value:**

```scala
val explanation: String = graph.explain("/factPath")
// Returns human-readable explanation of how value was calculated
```

**Debug with XML representation:**

```scala
val xml: String = graph.debugFact("/factPath")
// Returns XML showing fact definition with current values
```

**Full dependency tree:**

```scala
val tree: String = graph.debugFactRecurse("/factPath")
// Returns complete tree of all dependencies and their values
```

**Example usage:**

```scala
// When debugging a calculation
val result = graph.get("/totalTax")
if result.get != expectedValue then
  println(graph.explain("/totalTax"))
  println(graph.debugFactRecurse("/totalTax"))
```

---

## Testing Guidelines

### Test Locations

Tests are organized by platform:

- **`shared/src/test/scala/`** - Core logic tests (most tests here)
  - Cross-platform functionality
  - Dictionary parsing
  - Graph operations
  - Expression evaluation

- **`js/src/test/scala/`** - JavaScript-specific tests
  - JS interop
  - Browser-specific functionality

- **`jvm/src/test/scala/`** - JVM-specific tests
  - Java interop
  - JVM-specific persisters

---

### Running Tests

```bash
# Run all tests
sbt test

# Run specific test file
sbt "testOnly *MyTestSpec"

# Run specific test case
sbt "testOnly *MyTestSpec -- -z 'test name'"

# Clean build and test
sbt clean test

# Fast compile and test (skip JS optimization)
sbt fastOptJS test

# Watch mode (re-run on file changes)
sbt ~test
```

---

### Test Patterns

#### Dictionary Parsing Tests

Test XML parsing and fact definition creation:

```scala
test("parse fact dictionary with multiple facts") {
  val xml = """<?xml-model href="./FactDictionaryModule.rng"?>
    <FactDictionaryModule>
      <Facts>
        <Fact path="/age">
          <Name>Age</Name>
          <Writable><Int /></Writable>
        </Fact>
      </Facts>
    </FactDictionaryModule>"""
  
  val dict = FactDictionary.fromXml(xml)
  assert(dict("/age").isDefined)
  assert(dict("/age").get.isWritable)
}
```

#### Graph Evaluation Tests

Test fact calculation and dependencies:

```scala
test("calculate derived fact from dependencies") {
  val dict = FactDictionary.fromXml(xmlString)
  val graph = Graph(dict)
  
  // Set input values
  graph.set("/input1", Int(10))
  graph.set("/input2", Int(20))
  
  // Verify calculated result
  val result = graph.get("/sum")
  assert(result.hasValue)
  assert(result.get == Int(30))
}
```

#### Validation Tests

Test limit enforcement:

```scala
test("validate maximum limit violation") {
  val dict = FactDictionary.fromXml(xmlWithLimits)
  val graph = Graph(dict)
  
  // Set value exceeding limit
  graph.set("/age", Int(150))
  
  // Validate
  val (valid, violations) = graph.save()
  
  // Check violations
  assert(!valid)
  assert(violations.exists(_.context.limitName == "Maximum value"))
  assert(violations.head.path.toString == "/age")
}
```

#### Collection Tests

Test collection operations and wildcard expansion:

```scala
test("collection sum with multiple items") {
  val dict = FactDictionary.fromXml(xmlString)
  val graph = Graph(dict)
  
  // Add collection items
  val uuid1 = java.util.UUID.randomUUID().toString
  val uuid2 = java.util.UUID.randomUUID().toString
  
  graph.addToCollection("/items", uuid1)
  graph.addToCollection("/items", uuid2)
  
  // Set values
  graph.set(s"/items/#$uuid1/amount", Dollar(10000))
  graph.set(s"/items/#$uuid2/amount", Dollar(20000))
  
  // Verify sum
  val sum = graph.get("/totalAmount")
  assert(sum.get == Dollar(30000))
}
```

#### Result State Tests

Test completeness tracking:

```scala
test("incomplete result when dependency missing") {
  val dict = FactDictionary.fromXml(xmlString)
  val graph = Graph(dict)
  
  // Don't set required dependency
  val result = graph.get("/derivedFact")
  
  // Should be incomplete
  assert(!result.hasValue)
}

test("placeholder result when placeholder defined") {
  val dict = FactDictionary.fromXml(xmlWithPlaceholder)
  val graph = Graph(dict)
  
  // Get fact without setting writable
  val result = graph.get("/factWithPlaceholder")
  
  // Should have placeholder value
  assert(result.hasValue)
  assert(!result.complete)
}
```

---

## Key Source Files

### Core Architecture

Located in `shared/src/main/scala/gov/irs/factgraph/`:

**`FactDictionary.scala`** - Blueprint and definition storage
- Parses XML files into fact definitions
- Stores definitions by abstract path (with wildcards)
- Provides `importFromXml()` entry point
- Must be frozen before creating graphs

**`Graph.scala`** - Runtime instance of a fact dictionary
- Manages persister and caches
- Provides public API: `set()`, `get()`, `save()`, `delete()`
- Handles wildcard expansion and path resolution
- Debug methods: `explain()`, `debugFact()`, `debugFactRecurse()`

**`Fact.scala`** - Individual fact instance in a graph
- Handles path resolution and navigation
- Evaluates expressions lazily
- Manages parent-child relationships
- Resolves relative paths

**`Expression.scala`** - Expression evaluation engine
- Defines `Expression[A]` enum with all operation types
- Recursive evaluation logic
- Returns `MaybeVector[Result[A]]` to handle collections
- Integrates with Result monad for completeness

**`Path.scala`** - Path addressing system
- Parses path strings into `PathItem` components
- Handles absolute/relative paths
- Supports wildcard expansion
- UUID substitution for collection members

**`Migrations.scala`** - Data migration system
- Applies programmatic updates to persisted data
- Maintains `AllMigrations` list
- Critical for user data across schema changes

**`Meta.scala`** - Dictionary metadata
- Version information
- Test dictionary flag
- Migration tracking

---

### Component Directories

**`compnodes/`** - Computation node implementations
- One file per operation type
- Examples: `Add.scala`, `Multiply.scala`, `Switch.scala`, `Dependency.scala`
- Each extends `CompNode` trait
- Implements type-safe operations

**`compnodes/transformations/`** - String manipulation nodes
- `ToUpper.scala` - Convert to uppercase
- `Trim.scala` - Remove whitespace
- `StripChars.scala` - Remove specific characters
- `TruncateNameForMeF.scala` - MeF-specific truncation

**`types/`** - WritableType implementations
- `Dollar.scala` - Currency (stored as cents)
- `Tin.scala` - Tax Identification Number
- `Enum.scala` - Enumeration values
- `Address.scala` - Address with MeF validation
- `BankAccount.scala` - Bank account information

**`limits/`** - Validation logic
- `Min.scala`, `Max.scala` - Numeric bounds
- `Match.scala` - Regex pattern matching
- `MaxLength.scala` - String length limits
- `MaxCollectionSize.scala` - Collection size limits

**`monads/`** - Control flow types
- `Result.scala` - Completeness monad
- `MaybeVector.scala` - Single/multiple value handling
- `Thunk.scala` - Lazy evaluation wrapper

**`persisters/`** - Data storage
- `Persister.scala` - Persistence interface
- `InMemoryPersister.scala` - Default implementation

**`operators/`** - Operator trait definitions
- `AggregateOperator.scala` - Collection aggregation
- `ReduceOperator.scala` - Collection reduction
- `BinaryOperator.scala`, `UnaryOperator.scala` - Standard operations

**`definitions/`** - XML parsing support
- `FactConfigElement.scala` - Parses `<Fact>` elements
- `CompNodeFactory.scala` - Factory trait for nodes

---

## Code Navigation Tips

### Finding Functionality by Task

**Arithmetic operations:**
- `compnodes/Add.scala` - Addition
- `compnodes/Subtract.scala` - Subtraction
- `compnodes/Multiply.scala` - Multiplication
- `compnodes/Divide.scala` - Division

**Comparison operations:**
- `compnodes/Equal.scala`, `compnodes/NotEqual.scala`
- `compnodes/GreaterThan.scala`, `compnodes/LessThan.scala`
- `compnodes/GreaterThanOrEqual.scala`, `compnodes/LessThanOrEqual.scala`

**Logical operations:**
- `compnodes/All.scala` - Logical AND
- `compnodes/Any.scala` - Logical OR
- `compnodes/Not.scala` - Logical NOT

**Collection operations:**
- `compnodes/Count.scala` - Count items
- `compnodes/CollectionSum.scala` - Sum values
- `compnodes/Filter.scala` - Filter by condition
- `compnodes/Find.scala` - Find first match
- `compnodes/IndexOf.scala` - Get item at index

**String operations:**
- `compnodes/Length.scala` - String length
- `compnodes/Paste.scala` - Concatenation
- `compnodes/AsString.scala` - Type conversion
- `compnodes/transformations/` - Transformations

**Date operations:**
- `compnodes/Today.scala` - Current date
- `compnodes/LastDayOfMonth.scala` - Month end date
- `compnodes/AddPayrollMonths.scala` - Date arithmetic

**Math operations:**
- `compnodes/Round.scala`, `compnodes/RoundToInt.scala`
- `compnodes/Ceiling.scala`, `compnodes/Floor.scala`
- `compnodes/Maximum.scala`, `compnodes/Minimum.scala`

---

### Entry Points

**For library consumers:**

Start with dictionary loading:
```scala
val dictionary = FactDictionary.importFromXml(xmlString)
```

Then create a graph:
```scala
val graph = Graph(dictionary)
```

Use public API:
```scala
graph.set("/path", value)
val result = graph.get("/path")
val (valid, violations) = graph.save()
```

**For extending functionality:**

- **New operations**: Add to `compnodes/`, implement factory
- **New types**: Add to `types/`, create corresponding CompNode
- **New limits**: Add to `limits/`, implement factory
- **New persisters**: Implement `Persister` trait

**For debugging:**

- Use `graph.explain("/path")` for calculations
- Use `graph.debugFact("/path")` for XML representation
- Use `graph.debugFactRecurse("/path")` for full dependency tree
- Check test files for examples

---

## Related Documentation

- [FXML Schema Reference](fxml-schema-reference.md) - Complete XML schema documentation
- [Type System](type-system.md) - All writable types and type checking
- [Expression Evaluation](expression-evaluation.md) - How expressions are evaluated
- [Testing Best Practices](../CONTRIBUTING.md) - Contribution guidelines
