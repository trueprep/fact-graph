# Type System

The Fact Graph type system provides strong typing with runtime validation. All values in the system are instances of `WritableType` or derived from expressions that produce typed results.

---

## Writable Types Catalog

### Primitive Types

**Boolean** (`types/Boolean.scala`):
```scala
case class Boolean(value: scala.Boolean) extends WritableType
```
- Stores: `true` or `false`
- Usage: Flags, conditions, yes/no questions
- Operations: All, Any, Not, Equal, NotEqual
- FXML: `<Boolean />`

**Str** (`types/Str.scala`):
```scala
case class Str(value: String) extends WritableType
```
- Stores: Text strings
- Usage: Names, descriptions, free-text input
- Operations: Length, Paste, Trim, ToUpper, StripChars
- FXML: `<String />`
- Note: Type name is `Str` (not String) to avoid Scala keyword conflict

**Int** (`types/Int.scala`):
```scala
case class Int(value: scala.Int) extends WritableType
```
- Stores: 32-bit signed integers (-2,147,483,648 to 2,147,483,647)
- Usage: Counts, ages, years, quantities
- Operations: Add, Subtract, Multiply, Divide, comparisons, Maximum, Minimum
- FXML: `<Int />`

### Financial Types

**Dollar** (`types/Dollar.scala`):
```scala
case class Dollar(cents: scala.Int) extends WritableType
```
- Stores: Currency amounts as integer cents
- Internal representation: $100.00 = `Dollar(10000)`
- Usage: Money, income, deductions, credits
- Operations: Add, Subtract, Multiply, Divide, comparisons, Round, Maximum, Minimum
- FXML: `<Dollar />`
- Precision: Avoids floating-point errors by using integer cents

**Rational** (`types/Rational.scala`):
```scala
case class Rational(numerator: scala.Int, denominator: scala.Int) extends WritableType
```
- Stores: Fractional numbers as numerator/denominator
- Format: "12/100" represents 12/100 = 0.12
- Usage: Percentages, rates, precise fractions
- Operations: Add, Subtract, Multiply, Divide, comparisons, Round
- FXML: `<Rational />`
- Precision: Avoids floating-point precision issues

### Date/Time Types

**Day** (`types/Day.scala`):
```scala
case class Day(date: LocalDate) extends WritableType
```
- Stores: Calendar dates (year, month, day)
- Format: ISO 8601 (YYYY-MM-DD): "2024-01-15"
- Usage: Birth dates, filing dates, tax year dates
- Operations: Comparisons, LastDayOfMonth, AddPayrollMonths
- FXML: `<Day />`

**Days** (`types/Days.scala`):
```scala
case class Days(value: scala.Int) extends WritableType
```
- Stores: Duration in days (integer)
- Usage: Time periods, durations, intervals
- Operations: Add, Subtract, comparisons
- FXML: `<Days />`

### Enum Types

**Enum** (`types/Enum.scala`):
```scala
case class Enum(optionsPath: String, value: String) extends WritableType
```
- Stores: Single selection from predefined options
- Components:
  - `optionsPath`: Path to EnumOptions fact
  - `value`: Selected option string
- Usage: Filing status, states, categorical selections
- FXML: `<Enum optionsPath="/pathToOptions" />`
- Validation: Value must exist in options list

**MultiEnum** (`types/MultiEnum.scala`):
```scala
case class MultiEnum(optionsPath: String, values: List[String]) extends WritableType
```
- Stores: Multiple selections from predefined options
- Components:
  - `optionsPath`: Path to EnumOptions fact
  - `values`: List of selected option strings
- Usage: Multiple reasons, multiple selections
- FXML: `<MultiEnum optionsPath="/pathToOptions" />`
- Validation: All values must exist in options list

### Tax-Specific Types

**Tin** (`types/Tin.scala`):
```scala
case class Tin(value: String) extends WritableType
```
- Stores: Tax Identification Number (SSN or EIN)
- Format: "XXX-XX-XXXX" (SSN) or "XX-XXXXXXX" (EIN)
- Usage: Taxpayer identification
- FXML: `<Tin />`
- Validation: MeF-specific format rules

**Ein** (`types/Ein.scala`):
```scala
case class Ein(value: String) extends WritableType
```
- Stores: Employer Identification Number
- Format: "XX-XXXXXXX"
- Usage: Business identification
- FXML: `<Ein />`

**IpPin** (`types/IpPin.scala`):
```scala
case class IpPin(value: String) extends WritableType
```
- Stores: Identity Protection PIN
- Format: 6-digit numeric code
- Usage: IRS identity protection
- FXML: `<IpPin />`

**Pin** (`types/Pin.scala`):
```scala
case class Pin(value: String) extends WritableType
```
- Stores: Generic PIN number
- Format: 5-digit numeric code
- Usage: Self-select PINs
- FXML: `<Pin />`

### Contact Types

**Address** (`types/Address.scala`):
```scala
case class Address(
  street: String,
  city: String,
  state: String,
  zip: String,
  // ... additional fields
) extends WritableType
```
- Stores: Complete mailing address
- Components: Street, city, state, ZIP, country, etc.
- Usage: Taxpayer addresses
- FXML: `<Address />`
- Validation: MeF-specific address rules

**EmailAddress** (`types/EmailAddress.scala`):
```scala
case class EmailAddress(value: String) extends WritableType
```
- Stores: Email address
- Format: RFC-compliant email format
- Usage: Contact information
- FXML: `<EmailAddress />`
- Validation: Email format checking

**PhoneNumber** (`types/PhoneNumber.scala`):
```scala
case class PhoneNumber(value: String) extends WritableType
```
- Stores: Phone number
- Format: "XXX-XXX-XXXX"
- Usage: Contact information
- FXML: `<PhoneNumber />`

### Financial Account Types

**BankAccount** (`types/BankAccount.scala`):
```scala
case class BankAccount(
  routingNumber: String,
  accountNumber: String,
  accountType: String
) extends WritableType
```
- Stores: Bank account information for refunds/payments
- Components: Routing number, account number, account type
- Usage: Direct deposit, payment information
- FXML: `<BankAccount />`

### Collection Type

**Collection** (`types/Collection.scala`):
```scala
case class Collection(uuids: Vector[String]) extends WritableType
```
- Stores: Ordered list of UUIDs
- Components: Vector of UUID strings
- Usage: Lists of dependents, expenses, forms
- FXML: `<Collection />`
- Operations: Count, Filter, Find, addToCollection, removeFromCollection

---

## Value Representation

All types extend the `WritableType` trait:

```scala
trait WritableType:
  def toJson: ujson.Value
  def fromJson(json: ujson.Value): WritableType
```

**Type Hierarchy:**
```
WritableType (trait)
  ├── Boolean
  ├── Str
  ├── Int
  ├── Dollar
  ├── Day
  ├── Days
  ├── Rational
  ├── Enum
  ├── MultiEnum
  ├── Tin
  ├── Ein
  ├── IpPin
  ├── Pin
  ├── Address
  ├── EmailAddress
  ├── PhoneNumber
  ├── BankAccount
  └── Collection
```

**JSON Serialization:**
Each type implements `toJson` for persistence:

```scala
// Simple types
Boolean(true).toJson          // ujson.Bool(true)
Int(42).toJson                // ujson.Num(42)
Str("hello").toJson           // ujson.Str("hello")

// Complex types
Dollar(10050).toJson          // ujson.Num(10050)  # cents
Rational(12, 100).toJson      // ujson.Str("12/100")
Collection(Vector("uuid1")).toJson  // ujson.Arr([ujson.Str("uuid1")])
```

---

## Type Checking Behavior

**Compile-Time Type Safety:**
- Expressions are strongly typed: `Expression[Boolean]`, `Expression[Int]`, etc.
- CompNodes declare their value type: `type Value = Boolean`
- Type mismatches caught during expression construction

**Runtime Type Checking:**
- `graph.set()` validates value type matches fact definition
- Type checking uses `ValueClass` for runtime reflection:
  ```scala
  def ValueClass: Class[?] = classOf[Boolean]
  ```
- Mismatched types throw `FactGraphValidationException`

**Type Coercion:**
- No implicit type coercion (explicit only)
- Use `AsString` to convert to string
- Use `AsDecimalString` for Dollar → String
- Arithmetic operations require matching types

**Example Type Checking:**
```scala
// Fact defined as Int
<Fact path="/age">
  <Writable><Int /></Writable>
</Fact>

// Valid
graph.set("/age", Int(30))  ✓

// Invalid - type mismatch
graph.set("/age", Str("30"))  ✗
// Throws: FactGraphValidationException
```

---

## Related Documentation

- [FXML Schema Reference](fxml-schema-reference.md) - How to use types in FXML
- [Validation System](validation-system.md) - Type-specific validation rules
- [Persistence Guide](persistence-guide.md) - How types are serialized
