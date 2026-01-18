# Validation System

The validation system enforces data integrity through **Limits** applied to writable facts.

---

## Limit Types

Limits are validation rules that facts must satisfy. Each limit type is implemented in `limits/` directory.

### Available Limit Types

**Min** (`limits/Min.scala`):
- Validates: value >= minimum
- Applies to: Int, Dollar, Rational
- FXML: `<Min><Int>0</Int></Min>`

**Max** (`limits/Max.scala`):
- Validates: value <= maximum
- Applies to: Int, Dollar, Rational
- FXML: `<Max><Dollar>100000</Dollar></Max>`

**MinLength** (`limits/MinLength.scala`):
- Validates: string.length >= minLength OR collection.size >= minSize
- Applies to: Str, Collection
- FXML: `<MinLength><Int>1</Int></MinLength>`

**MaxLength** (`limits/MaxLength.scala`):
- Validates: string.length <= maxLength
- Applies to: Str
- FXML: `<MaxLength><Int>100</Int></MaxLength>`

**MaxCollectionSize** (`limits/MaxCollectionSize.scala`):
- Validates: collection.size <= maxSize
- Applies to: Collection
- FXML: `<MaxCollectionSize><Int>10</Int></MaxCollectionSize>`

**Match** (`limits/Match.scala`):
- Validates: string matches regex pattern
- Applies to: Str
- FXML: `<Match><![CDATA[^[A-Za-z]+$]]></Match>`

### Limit Implementation Pattern

```scala
case class Min(threshold: CompNode, context: LimitContext) extends Limit:
  val limiter: BooleanNode = LessThan(/* current value */, threshold)

  def run(using Factual): Option[LimitViolation] =
    if limiter.get.hasValue && limiter.get.get == Boolean(true) then
      Some(LimitViolation(context))
    else
      None
```

---

## LimitContext Structure

**LimitContext** encapsulates information about a validation limit:

```scala
case class LimitContext(
  limitName: String,        // Human-readable name ("Minimum value", "Maximum length")
  limitLevel: LimitLevel,   // Severity: Error or Warning
  actual: CompNode,         // The actual value being validated
  limit: CompNode           // The threshold/limit value
)

enum LimitLevel:
  case Error      // Validation failure prevents submission
  case Warning    // Validation failure shows warning but allows submission
```

**Example:**
```scala
// Min limit: value must be >= 0
LimitContext(
  limitName = "Minimum value",
  limitLevel = LimitLevel.Error,
  actual = Dependency("/age"),        // Actual value: user's input
  limit = Constant(Int(0))            // Limit: must be >= 0
)
```

**Usage in Violations:**
```scala
case class LimitViolation(
  context: LimitContext,
  path: Path,
  message: String
)

// When limit fails:
LimitViolation(
  context = limitContext,
  path = Path("/age"),
  message = "Age must be at least 0 (actual: -5)"
)
```

---

## LimitViolation

**LimitViolation** represents a failed validation:

```scala
case class LimitViolation(
  context: LimitContext,    // Limit details
  path: Path,               // Path to violating fact
  message: String           // Human-readable error message
)
```

### Validation Flow

```
1. User sets value: graph.set("/age", Int(-5))
2. Value stored in persister
3. graph.save() called
4. For each writable fact:
   - Evaluate all limits
   - If limiter returns true → violation
5. Return (valid: Boolean, violations: Seq[LimitViolation])
```

### Example

```scala
// Set invalid value
graph.set("/age", Int(-5))

// Validate
val (valid, violations) = graph.save()

if !valid then
  violations.foreach: v =>
    println(s"${v.path}: ${v.message}")
    // Output: /age: Age must be at least 0 (actual: -5)
```

### Collecting Violations

```scala
// Persister.save() implementation
def save(): (Boolean, Seq[LimitViolation]) =
  val violations = writableFacts.flatMap: fact =>
    fact.limits.flatMap: limit =>
      limit.run()  // Returns Option[LimitViolation]

  val valid = violations.isEmpty
  (valid, violations)
```

---

## Intrinsic Limits

Some CompNodes have **intrinsic limits** - validation rules built into the type itself:

### EnumNode

```scala
override def getIntrinsicLimits(using Factual): Seq[Limit] =
  Seq(
    EnumOptionsContainsLimit(
      value = this,
      options = Dependency(optionsPath)
    )
  )
```
- Automatically validates that enum value exists in options
- Applied without explicit `<Limits>` element in FXML

### AddressNode

- MeF-specific validation rules
- State must be valid US state
- ZIP code format validation

### TinNode

- SSN format: XXX-XX-XXXX
- EIN format: XX-XXXXXXX

---

## When Limits Are Checked

1. **graph.set()**: Immediate validation (optional, controlled by parameter)
2. **graph.save()**: Full validation of all writable facts
3. **Persister.save()**: Calls validation before persisting

### Disabling Validation

```scala
// Set without immediate validation
graph.set("/age", Int(-5), validate = false)
// Value stored, no error yet

// Validation happens later
val (valid, violations) = graph.save()
// Now violations are collected
```

---

## Related Documentation

- [FXML Schema Reference](fxml-schema-reference.md) - How to define limits in FXML
- [Type System](type-system.md) - Types and their validation rules
- [Persistence Guide](persistence-guide.md) - How validation integrates with save
