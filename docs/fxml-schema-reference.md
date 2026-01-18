# FXML Schema Reference

FXML (Fact XML) is the markup language for defining Fact Dictionaries. This document provides comprehensive documentation of all FXML elements and their usage.

---

## Document Structure (FactDictionaryModule)

**Root Element:**
```xml
<?xml-model href="./FactDictionaryModule.rng"?>
<FactDictionaryModule>
  <Meta>
    <Version>1.0.0</Version>
    <TestDictionary>false</TestDictionary>
  </Meta>
  <Facts>
    <!-- Fact definitions -->
  </Facts>
</FactDictionaryModule>
```

**Elements:**
- `<?xml-model?>` processing instruction links to RelaxNG schema for validation
- `<FactDictionaryModule>` is the required root element
- `<Meta>` contains dictionary metadata (optional)
- `<Facts>` contains all fact definitions (required)

**Meta Element:**
```xml
<Meta>
  <Version>3.1.0</Version>
  <TestDictionary>true</TestDictionary>
</Meta>
```
- `<Version>`: String version identifier for the dictionary
- `<TestDictionary>`: Boolean flag (true/false) - when true, allows setting derived facts for testing

---

## Fact Element

Each `<Fact>` element defines a single fact in the dictionary.

### Required Attributes

**path** (required):
```xml
<Fact path="/factPath">
  <!-- Fact definition -->
</Fact>
```
- Must be an absolute path starting with `/`
- Can include wildcards for collection templates: `/collection/*/childFact`
- Must be unique within the dictionary module

### Metadata Elements (Name, Description, Export)

**Complete Example:**
```xml
<Fact path="/filingStatus">
  <Name>Filing Status</Name>
  <Description>
    The taxpayer's filing status for the tax year.
    Determines standard deduction and tax brackets.
  </Description>
  <Export downstreamFacts="true" mef="true" />

  <!-- Writable or Derived definition follows -->
</Fact>
```

**Name Element:**
- Human-readable short name for the fact
- Used in UI and error messages
- Optional but recommended

**Description Element:**
- Detailed explanation of the fact's purpose
- Can be multi-line
- Optional but recommended for documentation

**Export Element:**
- Marks facts for export to external systems
- Attributes:
  - `downstreamFacts="true"` - Export to downstream systems
  - `mef="true"` - Export to MeF (Modernized e-File) system
- Optional, omit if fact is internal-only

---

## Writable Facts

**Writable facts** accept user input and are the leaves of the fact graph dependency tree.

**Basic Structure:**
```xml
<Fact path="/age">
  <Name>Age</Name>
  <Writable>
    <Int />
  </Writable>
</Fact>
```

### Type Catalog

**Boolean:**
```xml
<Writable>
  <Boolean />
</Writable>
```
- Values: `true` or `false`
- No parameters

**String:**
```xml
<Writable>
  <String />
</Writable>
```
- Text values
- Can include limits for length and pattern matching

**Int:**
```xml
<Writable>
  <Int />
</Writable>
```
- Integer numbers (whole numbers)
- 32-bit signed integers

**Dollar:**
```xml
<Writable>
  <Dollar />
</Writable>
```
- Currency amounts
- Stored internally as cents (e.g., $100.00 = 10000)
- Supports arithmetic operations

**Day:**
```xml
<Writable>
  <Day />
</Writable>
```
- Date values (year-month-day)
- ISO 8601 format: "2024-01-15"

**Days:**
```xml
<Writable>
  <Days />
</Writable>
```
- Duration in days (integer)
- Used for time periods

**Rational:**
```xml
<Writable>
  <Rational />
</Writable>
```
- Fractional numbers represented as numerator/denominator
- Example: "12/100" represents 0.12
- Avoids floating-point precision issues

**Enum:**
```xml
<Writable>
  <Enum optionsPath="/statusOptions" />
</Writable>
```
- Single selection from a list of options
- `optionsPath` references a fact containing `<EnumOptions>`
- Value stored as string

**MultiEnum:**
```xml
<Writable>
  <MultiEnum optionsPath="/reasonsOptions" />
</Writable>
```
- Multiple selections from a list of options
- Stored as list of strings
- All selected values must be valid options

**Tin:**
```xml
<Writable>
  <Tin />
</Writable>
```
- Tax Identification Number (SSN or EIN)
- Format: "XXX-XX-XXXX" or "XX-XXXXXXX"
- MeF-specific validation

**Ein:**
```xml
<Writable>
  <Ein />
</Writable>
```
- Employer Identification Number
- Format: "XX-XXXXXXX"

**IpPin:**
```xml
<Writable>
  <IpPin />
</Writable>
```
- Identity Protection PIN
- 6-digit numeric code

**Pin:**
```xml
<Writable>
  <Pin />
</Writable>
```
- Generic PIN number
- 5-digit numeric code

**Address:**
```xml
<Writable>
  <Address />
</Writable>
```
- Complete address object
- Includes street, city, state, zip
- MeF-specific validation rules

**EmailAddress:**
```xml
<Writable>
  <EmailAddress />
</Writable>
```
- Email address with validation
- RFC-compliant format checking

**PhoneNumber:**
```xml
<Writable>
  <PhoneNumber />
</Writable>
```
- Phone number with validation
- Format: "XXX-XXX-XXXX"

**BankAccount:**
```xml
<Writable>
  <BankAccount />
</Writable>
```
- Bank account information
- Includes routing and account numbers

**Collection:**
```xml
<Writable>
  <Collection />
</Writable>
```
- Ordered list of items (UUIDs)
- Managed via `addToCollection` / `removeFromCollection` API

### Limits

Limits define validation rules for writable facts. Multiple limits can be applied to a single fact.

**Min (Numeric Minimum):**
```xml
<Writable>
  <Int />
  <Limits>
    <Min>
      <Int>0</Int>
    </Min>
  </Limits>
</Writable>
```
- Validates that value >= specified minimum
- Works with Int, Dollar, Rational

**Max (Numeric Maximum):**
```xml
<Writable>
  <Dollar />
  <Limits>
    <Max>
      <Dollar>100000</Dollar>
    </Max>
  </Limits>
</Writable>
```
- Validates that value <= specified maximum
- Works with Int, Dollar, Rational

**MinLength (String/Collection Minimum Length):**
```xml
<Writable>
  <String />
  <Limits>
    <MinLength>
      <Int>1</Int>
    </MinLength>
  </Limits>
</Writable>
```
- Validates minimum string length or collection size
- Value of 1 effectively makes the fact required

**MaxLength (String Maximum Length):**
```xml
<Writable>
  <String />
  <Limits>
    <MaxLength>
      <Int>100</Int>
    </MaxLength>
  </Limits>
</Writable>
```
- Validates maximum string length
- Only applies to String types

**MaxCollectionSize:**
```xml
<Writable>
  <Collection />
  <Limits>
    <MaxCollectionSize>
      <Int>10</Int>
    </MaxCollectionSize>
  </Limits>
</Writable>
```
- Validates maximum number of items in collection
- Only applies to Collection types

**Match (Regex Pattern):**
```xml
<Writable>
  <String />
  <Limits>
    <Match>
      <![CDATA[^[A-Za-z0-9]+$]]>
    </Match>
  </Limits>
</Writable>
```
- Validates string matches regular expression
- Use CDATA for regex patterns to avoid XML escaping
- Only applies to String types

**Multiple Limits:**
```xml
<Writable>
  <String />
  <Limits>
    <MinLength><Int>3</Int></MinLength>
    <MaxLength><Int>50</Int></MaxLength>
    <Match><![CDATA[^[A-Za-z\s]+$]]></Match>
  </Limits>
</Writable>
```
- All limits must be satisfied
- Violations collected and returned from `graph.save()`

### Placeholder

Placeholders provide default values when a writable fact is incomplete or empty:

```xml
<Fact path="/dependents">
  <Name>Number of Dependents</Name>
  <Writable>
    <Int />
  </Writable>
  <Placeholder>
    <Int>0</Int>
  </Placeholder>
</Fact>
```

**Behavior:**
- If writable fact has no value, placeholder is returned
- Result is marked as `Result.Placeholder(value)` (not `Complete`)
- Allows downstream calculations to proceed with default values
- Common for optional fields with sensible defaults

**Complex Placeholders:**
```xml
<Placeholder>
  <Switch>
    <Case>
      <When><Dependency path="/hasChildren" /></When>
      <Then><Int>1</Int></Then>
    </Case>
    <Case>
      <When><True /></When>
      <Then><Int>0</Int></Then>
    </Case>
  </Switch>
</Placeholder>
```
- Placeholders can contain complex expressions
- Evaluated when writable fact is incomplete

### Override

Overrides conditionally replace a fact's value based on a boolean condition:

```xml
<Fact path="/standardDeduction">
  <Name>Standard Deduction</Name>
  <Writable>
    <Dollar />
  </Writable>
  <Override>
    <Condition>
      <Dependency path="/isBlind" />
    </Condition>
    <Default>
      <Add>
        <Dependency path="/standardDeduction" />
        <Dollar>150000</Dollar>
      </Add>
    </Default>
  </Override>
</Fact>
```

**Behavior:**
- If `<Condition>` evaluates to `true`, `<Default>` value is used instead
- Original value is ignored when override is active
- Multiple overrides can be chained (first matching wins)
- Used for conditional adjustments to user-provided values

---

## Derived Facts

**Derived facts** are calculated from other facts using expressions. They form the computational logic of the tax system.

**Basic Structure:**
```xml
<Fact path="/totalIncome">
  <Name>Total Income</Name>
  <Derived>
    <Add>
      <Dependency path="/wages" />
      <Dependency path="/interest" />
    </Add>
  </Derived>
</Fact>
```

### Dependency

References another fact's value:

**Simple Dependency:**
```xml
<Dependency path="/otherFact" />
```

**Relative Paths:**
```xml
<Dependency path="../siblingFact" />
<Dependency path="./childFact" />
<Dependency path="../../grandparentFact" />
```

**Wildcard (Collection):**
```xml
<Dependency path="/collection/*/memberFact" />
```
- Returns `MaybeVector.Multiple` with all member values
- Used with aggregate operations (Count, CollectionSum)

**Cross-Module:**
```xml
<Dependency module="filers" path="/primaryFiler/tin" />
<Dependency module="income" path="/totalIncome" />
```
- Module name is XML filename without `.xml` extension
- Resolved during dictionary loading

**Member-Specific:**
```xml
<Dependency path="/collection/#uuid-123/value" />
```
- References specific collection item

### Switch/Case/When/Then

Conditional logic with multiple cases (first matching case wins):

**Basic Switch:**
```xml
<Switch>
  <Case>
    <When>
      <Dependency path="/isMarried" />
    </When>
    <Then>
      <String>Married</String>
    </Then>
  </Case>
  <Case>
    <When>
      <True />
    </When>
    <Then>
      <String>Single</String>
    </Then>
  </Case>
</Switch>
```

**Multi-Condition Case:**
```xml
<Switch>
  <Case>
    <When>
      <All>
        <Dependency path="/age" />
        <GreaterThanOrEqual>
          <Left><Dependency path="/age" /></Left>
          <Right><Int>65</Int></Right>
        </GreaterThanOrEqual>
      </All>
    </When>
    <Then>
      <String>Senior</String>
    </Then>
  </Case>
  <Case>
    <When><True /></When>
    <Then><String>Adult</String></Then>
  </Case>
</Switch>
```

**Nested Switches:**
```xml
<Switch>
  <Case>
    <When><Dependency path="/condition1" /></When>
    <Then>
      <Switch>
        <Case>
          <When><Dependency path="/subCondition" /></When>
          <Then><Int>100</Int></Then>
        </Case>
        <Case>
          <When><True /></When>
          <Then><Int>50</Int></Then>
        </Case>
      </Switch>
    </Then>
  </Case>
  <Case>
    <When><True /></When>
    <Then><Int>0</Int></Then>
  </Case>
</Switch>
```

**Important Notes:**
- Evaluation stops at first matching case
- Always include a catch-all case with `<When><True /></When>`
- Cases are evaluated in order

### Arithmetic Operators

**Add:**
```xml
<Add>
  <Dollar>100</Dollar>
  <Dollar>200</Dollar>
  <Dollar>50</Dollar>
</Add>
```
- Sums all child expressions
- Works with: Int, Dollar, Rational, Days
- Variable number of children (2+)
- Result: Dollar(350)

**Subtract:**
```xml
<Subtract>
  <Minuend>
    <Dependency path="/total" />
  </Minuend>
  <Subtrahends>
    <Dollar>100</Dollar>
    <Dollar>50</Dollar>
  </Subtrahends>
</Subtract>
```
- Minuend - sum of all Subtrahends
- Structure: minuend - (subtrahend1 + subtrahend2 + ...)
- Works with: Int, Dollar, Rational, Days

**Multiply:**
```xml
<Multiply>
  <Dependency path="/amount" />
  <Rational>12/100</Rational>
</Multiply>
```
- Multiplies all child expressions
- Works with: Int, Dollar, Rational
- Variable number of children (2+)
- Common for percentage calculations

**Divide:**
```xml
<Divide>
  <Dividend>
    <Dependency path="/total" />
  </Dividend>
  <Divisor>
    <Int>12</Int>
  </Divisor>
</Divide>
```
- Dividend / Divisor
- Works with: Int, Dollar, Rational
- Division by zero returns `Result.Incomplete`

### Comparison Operators

All comparison operators return Boolean values.

**Equal:**
```xml
<Equal>
  <Left><Dependency path="/value1" /></Left>
  <Right><Dependency path="/value2" /></Right>
</Equal>
```
- Returns true if left == right
- Works with all types
- Type-safe: both sides must be same type

**NotEqual:**
```xml
<NotEqual>
  <Left><Dependency path="/value1" /></Left>
  <Right><Int>0</Int></Right>
</NotEqual>
```
- Returns true if left != right
- Works with all types

**GreaterThan:**
```xml
<GreaterThan>
  <Left><Dependency path="/age" /></Left>
  <Right><Int>18</Int></Right>
</GreaterThan>
```
- Returns true if left > right
- Works with: Int, Dollar, Rational, Day, Days

**LessThan:**
```xml
<LessThan>
  <Left><Dependency path="/income" /></Left>
  <Right><Dollar>5000000</Dollar></Right>
</LessThan>
```
- Returns true if left < right
- Works with: Int, Dollar, Rational, Day, Days

**GreaterThanOrEqual:**
```xml
<GreaterThanOrEqual>
  <Left><Dependency path="/age" /></Left>
  <Right><Int>65</Int></Right>
</GreaterThanOrEqual>
```
- Returns true if left >= right
- Works with: Int, Dollar, Rational, Day, Days

**LessThanOrEqual:**
```xml
<LessThanOrEqual>
  <Left><Dependency path="/balance" /></Left>
  <Right><Dollar>100000</Dollar></Right>
</LessThanOrEqual>
```
- Returns true if left <= right
- Works with: Int, Dollar, Rational, Day, Days

### Logical Operators

**All (AND):**
```xml
<All>
  <Dependency path="/condition1" />
  <Dependency path="/condition2" />
  <Dependency path="/condition3" />
</All>
```
- Returns true if ALL children are true
- Short-circuits on first false value
- Variable number of children (1+)
- Empty children list returns true

**Any (OR):**
```xml
<Any>
  <Dependency path="/condition1" />
  <Dependency path="/condition2" />
  <Dependency path="/condition3" />
</Any>
```
- Returns true if ANY child is true
- Short-circuits on first true value
- Variable number of children (1+)
- Empty children list returns false

**Not:**
```xml
<Not>
  <Dependency path="/condition" />
</Not>
```
- Returns opposite of child boolean
- Single child only
- true → false, false → true

**Complex Logic:**
```xml
<All>
  <Any>
    <Dependency path="/conditionA" />
    <Dependency path="/conditionB" />
  </Any>
  <Not>
    <Dependency path="/conditionC" />
  </Not>
  <Dependency path="/conditionD" />
</All>
```
- Nesting allows complex boolean expressions
- Equivalent to: (A || B) && !C && D

### Collection Operators

**Count:**
```xml
<Count>
  <Dependency path="/familyAndHousehold/*/age" />
</Count>
```
- Counts number of items in collection
- Returns Int
- Counts only Complete results (ignores Incomplete)

**CollectionSum:**
```xml
<CollectionSum>
  <Dependency path="/expenses/*/amount" />
</CollectionSum>
```
- Sums all values in collection
- Works with: Int, Dollar, Rational
- Skips Incomplete results
- Returns same type as elements

**Filter:**
```xml
<Filter path="/familyAndHousehold">
  <Dependency path="isDependent" />
</Filter>
```
- Filters collection based on boolean condition
- `path` attribute specifies collection
- Child expression evaluated for each member (relative path)
- Returns Collection with matching UUIDs

**Find:**
```xml
<Find path="/familyAndHousehold">
  <Dependency path="isPrimaryFiler" />
</Find>
```
- Finds first matching item in collection
- Returns single result (not a vector)
- Returns Incomplete if no match found

**IndexOf:**
```xml
<IndexOf>
  <Collection>
    <Filter path="/familyAndHousehold">
      <Dependency path="isChild" />
    </Filter>
  </Collection>
  <Index><Int>0</Int></Index>
</IndexOf>
```
- Gets item at specific index from collection
- `<Collection>` contains collection expression
- `<Index>` contains Int index (0-based)
- Returns Incomplete if index out of bounds

### Enum Operators

**EnumOptions:**
```xml
<Fact path="/filingStatusOptions">
  <Derived>
    <EnumOptions>
      <String>single</String>
      <String>marriedFilingJointly</String>
      <String>marriedFilingSeparately</String>
      <String>headOfHousehold</String>
      <EnumOption>
        <Condition>
          <Dependency path="/spouseDeceased" />
        </Condition>
        <Value>
          <String>qualifyingWidow</String>
        </Value>
      </EnumOption>
    </EnumOptions>
  </Derived>
</Fact>
```
- Defines available enum options
- Static options: `<String>` elements
- Conditional options: `<EnumOption>` with `<Condition>` and `<Value>`
- Conditional options only included if condition is true
- Referenced by `<Enum optionsPath="..." />`

**EnumOptionsContains:**
```xml
<EnumOptionsContains>
  <Enum>
    <Dependency path="/statusOptions" />
  </Enum>
  <String>single</String>
</EnumOptionsContains>
```
- Checks if enum options include specific value
- Returns Boolean
- Used for validation

**EnumOptionsSize:**
```xml
<EnumOptionsSize>
  <Dependency path="/statusOptions" />
</EnumOptionsSize>
```
- Returns number of available options
- Returns Int
- Useful for conditional logic based on option count

### String Operators

**Length:**
```xml
<Length>
  <Dependency path="/name" />
</Length>
```
- Returns length of string
- Returns Int
- Empty string returns 0

**Paste:**
```xml
<Paste>
  <String>Hello, </String>
  <Dependency path="/name" />
  <String>!</String>
</Paste>
```
- Concatenates multiple strings
- Variable number of children (1+)
- Converts non-strings to strings automatically

**AsString:**
```xml
<AsString>
  <Dependency path="/numericValue" />
</AsString>
```
- Converts any value to its string representation
- Int(100) → "100"
- Dollar(5000) → "50.00"
- Boolean(true) → "true"

**AsDecimalString:**
```xml
<AsDecimalString>
  <Dependency path="/dollarAmount" />
</AsDecimalString>
```
- Converts Dollar to decimal string
- Dollar(10050) → "100.50"
- Includes decimal point and cents

### Transformations

String transformation operations (implemented in `compnodes/transformations/`):

**Trim:**
```xml
<Trim>
  <Dependency path="/userInput" />
</Trim>
```
- Removes leading and trailing whitespace
- "  hello  " → "hello"

**ToUpper:**
```xml
<ToUpper>
  <Dependency path="/name" />
</ToUpper>
```
- Converts string to uppercase
- "John Doe" → "JOHN DOE"

**StripChars:**
```xml
<StripChars>
  <Dependency path="/phoneNumber" />
  <String>-() </String>
</StripChars>
```
- Removes specified characters from string
- First child: source string
- Second child: characters to remove
- "(555) 123-4567" → "5551234567"

**TruncateNameForMeF:**
```xml
<TruncateNameForMeF>
  <Dependency path="/fullName" />
</TruncateNameForMeF>
```
- Truncates name to MeF length limits
- MeF-specific business logic
- Used for compliance with e-file requirements

### Date Operators

**Today:**
```xml
<Today />
```
- Returns current date
- No parameters
- Type: Day

**LastDayOfMonth:**
```xml
<LastDayOfMonth>
  <Dependency path="/someDate" />
</LastDayOfMonth>
```
- Returns last day of the month for given date
- Input: Day
- Output: Day
- Example: 2024-01-15 → 2024-01-31

**AddPayrollMonths:**
```xml
<AddPayrollMonths>
  <Dependency path="/startDate" />
  <Int>6</Int>
</AddPayrollMonths>
```
- Adds specified number of months to date
- Uses payroll calendar logic (specific to IRS requirements)
- First child: starting date
- Second child: number of months

### Math Operators

**Round:**
```xml
<Round>
  <Dependency path="/decimalValue" />
</Round>
```
- Rounds to nearest whole dollar
- Works with: Dollar, Rational
- Returns same type
- Banker's rounding (round half to even)

**RoundToInt:**
```xml
<RoundToInt>
  <Dependency path="/rationalValue" />
</RoundToInt>
```
- Rounds to nearest integer
- Works with: Rational
- Returns Int

**Ceiling:**
```xml
<Ceiling>
  <Dependency path="/value" />
</Ceiling>
```
- Rounds up to next whole number
- Works with: Dollar, Rational
- Dollar: rounds up to next dollar

**Floor:**
```xml
<Floor>
  <Dependency path="/value" />
</Floor>
```
- Rounds down to previous whole number
- Works with: Dollar, Rational
- Dollar: rounds down to previous dollar

**Maximum:**
```xml
<Maximum>
  <Dependency path="/value1" />
  <Dependency path="/value2" />
  <Dependency path="/value3" />
</Maximum>
```
- Returns largest value from children
- Works with: Int, Dollar, Rational
- Variable number of children (2+)

**Minimum:**
```xml
<Minimum>
  <Dependency path="/value1" />
  <Dependency path="/value2" />
  <Dependency path="/value3" />
</Minimum>
```
- Returns smallest value from children
- Works with: Int, Dollar, Rational
- Variable number of children (2+)

**GreaterOf:**
```xml
<GreaterOf>
  <Dependency path="/value1" />
  <Dependency path="/value2" />
</GreaterOf>
```
- Returns greater of two values
- Exactly 2 children
- Works with: Int, Dollar, Rational

**LesserOf:**
```xml
<LesserOf>
  <Dependency path="/value1" />
  <Dependency path="/value2" />
</LesserOf>
```
- Returns lesser of two values
- Exactly 2 children
- Works with: Int, Dollar, Rational

### Completeness Checks

**IsComplete:**
```xml
<IsComplete>
  <Dependency path="/someFact" />
</IsComplete>
```
- Checks if fact has a complete value
- Returns Boolean
- true: Result is Complete
- false: Result is Placeholder or Incomplete
- Used for conditional logic based on data completeness

### ConditionalList

Returns a list of strings where corresponding boolean conditions are true:

```xml
<ConditionalList>
  <Condition>
    <Dependency path="/hasChildren" />
  </Condition>
  <Value>
    <String>Dependent exemption available</String>
  </Value>
  <Condition>
    <Dependency path="/isBlind" />
  </Condition>
  <Value>
    <String>Additional standard deduction</String>
  </Value>
  <Condition>
    <Dependency path="/isOver65" />
  </Condition>
  <Value>
    <String>Senior exemption available</String>
  </Value>
</ConditionalList>
```
- Pairs of `<Condition>` and `<Value>` elements
- Each condition is evaluated
- If condition is true, corresponding value added to result list
- Returns: MultiEnum (list of strings)
- Used for collecting applicable items from conditional criteria

---

## Related Documentation

- [Core Concepts](core-concepts.md) - Understanding facts, graphs, paths, and collections
- [Type System](type-system.md) - Detailed information on all writable types
- [Validation System](validation-system.md) - How limits work and validation behavior
