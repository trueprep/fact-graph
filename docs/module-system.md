# Module System

How Fact Dictionaries are organized into modules and how cross-module dependencies enable modular fact definitions.

---

## Module Definition

### What is a Module?

A **module** is a single XML file in the `fact_dictionaries/` directory containing related fact definitions.

**Structure:**

```xml
<?xml-model href="./FactDictionaryModule.rng"?>
<FactDictionaryModule>
  <Facts>
    <!-- Fact definitions -->
  </Facts>
</FactDictionaryModule>
```

**Module name** = filename without `.xml` extension

Examples:
- `filers.xml` → module name: `filers`
- `income.xml` → module name: `income`
- `familyAndHousehold.xml` → module name: `familyAndHousehold`

### Characteristics

- **Self-contained** - Each module can define any number of facts
- **Independently parsable** - Module can be loaded and validated separately
- **Cross-referenced** - Modules can reference facts from other modules
- **Domain-organized** - Related facts grouped together

---

## Cross-Module Dependencies

### Syntax

Reference a fact from another module using the `module` attribute:

```xml
<Dependency module="moduleName" path="/factPath" />
```

**Without module attribute** - References fact in same module or already in scope:

```xml
<Dependency path="/localFact" />
```

**With module attribute** - References fact in different module:

```xml
<Dependency module="filers" path="/primaryFiler/tin" />
```

---

### Examples from Real Modules

#### Example 1: Reference Filer Information

From `dependentsBenefitSplit.xml`:

```xml
<Fact path="/familyAndHousehold/*/childMayQualifyForBenefitSplit">
  <Derived>
    <Any>
      <LessThan>
        <Left>
          <Dependency module="familyAndHousehold" path="../ageAtFirstHalfOfYear" />
        </Left>
        <Right>
          <Int>18</Int>
        </Right>
      </LessThan>
      <All>
        <Dependency module="familyAndHousehold" path="../disabilityStatusMayAffectBenefits" />
        <Dependency module="familyAndHousehold" path="../permanentTotalDisability" />
      </All>
    </Any>
  </Derived>
</Fact>
```

**Explanation:**
- Module: `dependentsBenefitSplit`
- References facts from: `familyAndHousehold` module
- Uses relative paths (`../`) to reference sibling facts in collection

#### Example 2: Reference Relationship Information

From `dependentsBenefitSplit.xml`:

```xml
<Fact path="/familyAndHousehold/*/tpIsParent">
  <Description>The relationship to the person is a parental one</Description>
  
  <Derived>
    <Any>
      <Equal>
        <Left>
          <Dependency module="dependentsRelationship" path="../relationship" />
        </Left>
        <Right>
          <Enum optionsPath="/relationshipOptions">biologicalChild</Enum>
        </Right>
      </Equal>
      <Equal>
        <Left>
          <Dependency module="dependentsRelationship" path="../relationship" />
        </Left>
        <Right>
          <Enum optionsPath="/relationshipOptions">adoptedChild</Enum>
        </Right>
      </Equal>
    </Any>
  </Derived>
</Fact>
```

**Explanation:**
- Module: `dependentsBenefitSplit`
- References: `dependentsRelationship` module
- Checks if relationship is parental (biological or adopted child)

#### Example 3: Reference Constants

From `constants.xml`:

```xml
<Fact path="/defaultTaxDay">
  <Name>Tax Day</Name>
  <Description>The due date for refunds and payments</Description>
  <Export downstreamFacts="true" />
  
  <Derived>
    <Day>2025-04-15</Day>
  </Derived>
</Fact>
```

Other modules reference this constant:

```xml
<Dependency module="constants" path="/defaultTaxDay" />
```

**Use case:** Centralize constants that multiple modules need.

---

## Module Organization Patterns

### Organize by Domain

Group related facts together based on tax domain:

**Income-related:**
- `income.xml` - Wage income, tips, other income
- `formW2s.xml` - W-2 form data
- `form1099Misc.xml` - 1099-MISC form data
- `form1099Rs.xml` - 1099-R form data
- `socialSecurity.xml` - Social security income

**Deductions and adjustments:**
- `standardDeduction.xml` - Standard deduction calculations
- `studentLoanAdjustment.xml` - Student loan interest deduction
- `educatorAdjustment.xml` - Educator expense deduction
- `hsa.xml` - Health Savings Account contributions

**Credits:**
- `ctcOdc.xml` - Child Tax Credit and Other Dependent Credit
- `eitc.xml` - Earned Income Tax Credit
- `cdcc.xml` - Child and Dependent Care Credit
- `saversCredits.xml` - Retirement savings credit
- `ptc.xml` - Premium Tax Credit

**Household and filers:**
- `filers.xml` - Primary filer and spouse information
- `familyAndHousehold.xml` - Dependents and household members
- `filingStatus.xml` - Filing status determination
- `dependentsRelationship.xml` - Dependent relationship rules
- `dependentsBenefitSplit.xml` - Special benefit split rules

**Tax calculations:**
- `taxCalculations.xml` - Tax computation
- `schedule2.xml` - Additional taxes
- `schedule3.xml` - Additional credits

**Logistics:**
- `flow.xml` - UI flow and navigation
- `validations.xml` - Cross-field validation rules
- `refundPrefs.xml` - Refund preferences
- `paymentMethod.xml` - Payment method selection
- `signing.xml` - Return signing
- `constants.xml` - Shared constants

---

### Keep Related Facts Together

Facts that reference each other heavily should be in the same module:

**Good:**

```xml
<!-- familyAndHousehold.xml -->
<Fact path="/familyAndHousehold/*/age">...</Fact>
<Fact path="/familyAndHousehold/*/dateOfBirth">...</Fact>
<Fact path="/familyAndHousehold/*/isDependent">...</Fact>
```

All related to household members, natural to group together.

**Avoid:**

```xml
<!-- Don't scatter tightly coupled facts across modules -->
<!-- Module A -->
<Fact path="/person/age">...</Fact>

<!-- Module B -->
<Fact path="/person/dateOfBirth">...</Fact>  <!-- Should be with age -->
```

---

### Minimize Cross-Module Dependencies

While cross-module references are supported, minimize them when possible:

**Reasons:**
1. **Easier to understand** - Module can be read independently
2. **Easier to test** - Fewer dependencies to mock/setup
3. **Easier to refactor** - Changes contained within module
4. **Better performance** - Fewer module loads required

**When cross-module references are appropriate:**
- Referencing constants (e.g., `constants.xml`)
- Referencing core entities (e.g., filer information)
- Avoiding duplication of complex calculations

**Example of good cross-module use:**

```xml
<!-- Multiple modules need tax year -->
<Dependency module="constants" path="/taxYear" />

<!-- Better than duplicating in each module -->
```

---

## Resolution Order

### How the System Finds Facts

1. **Parse module attribute** - If present, look in specified module
2. **Resolve path** - Navigate from root using path components
3. **Handle wildcards** - Expand to collection member UUIDs
4. **Handle relative paths** - Resolve `../` relative to current fact
5. **Return fact** - Return `Fact` instance or `Result.Incomplete`

### Example Resolution

```xml
<Dependency module="filers" path="/primaryFiler/tin" />
```

Steps:
1. Load `filers` module (if not already loaded)
2. Find root fact `/` in filers module
3. Navigate to child `primaryFiler`
4. Navigate to child `tin`
5. Return that fact

---

### What Happens if Module is Missing

If a referenced module doesn't exist:

```scala
// Loading phase
val dictionary = FactDictionary.importFromXml(xmlString)
```

**Result:** Exception thrown during dictionary loading

```
FactGraphValidationException: Module 'nonexistent' not found
```

**When caught:** During dictionary construction, before graph creation

**Solution:** Ensure all referenced modules are loaded:

```scala
// Load all modules
val modules = List(
  "filers.xml",
  "income.xml",
  "familyAndHousehold.xml"
).map(file => FactDictionary.fromXml(loadFile(file)))

// Merge into single dictionary
val dictionary = modules.reduce(_ merge _)
```

---

### Circular Dependency Handling

**Question:** What if Module A references Module B, which references Module A?

**Answer:** Circular dependencies at the **module level** are allowed (Module A can reference Module B and vice versa).

**Restriction:** Circular dependencies at the **fact level** are not allowed and will cause evaluation issues.

**Example - Allowed:**

```xml
<!-- moduleA.xml -->
<Fact path="/factA">
  <Derived>
    <Dependency module="moduleB" path="/factB" />
  </Derived>
</Fact>

<!-- moduleB.xml -->
<Fact path="/factB">
  <Derived>
    <Dependency module="moduleA" path="/factC" />  <!-- Different fact -->
  </Derived>
</Fact>

<Fact path="/factC">
  <Derived>
    <Int>42</Int>
  </Derived>
</Fact>
```

**Example - Not Allowed:**

```xml
<!-- moduleA.xml -->
<Fact path="/factA">
  <Derived>
    <Dependency module="moduleB" path="/factB" />
  </Derived>
</Fact>

<!-- moduleB.xml -->
<Fact path="/factB">
  <Derived>
    <Dependency module="moduleA" path="/factA" />  <!-- Circular! -->
  </Derived>
</Fact>
```

**Result:** Infinite loop during evaluation, stack overflow

**Best practice:** Design fact dependencies as a directed acyclic graph (DAG).

---

## Available Modules

Current modules in the fact_dictionaries/ directory:

| Module | Purpose |
|--------|---------|
| `cdcc` | Child and Dependent Care Credit |
| `constants` | Shared constants (tax year, dates, etc.) |
| `copy` | Copy functionality |
| `ctcOdc` | Child Tax Credit and Other Dependent Credit |
| `dependentsBenefitSplit` | Special benefit split rules |
| `dependentsRelationship` | Dependent relationship determination |
| `educatorAdjustment` | Educator expense deduction |
| `eitc` | Earned Income Tax Credit |
| `elderlyAndDisabled` | Credit for elderly and disabled |
| `estimatedPayments` | Estimated tax payments |
| `familyAndHousehold` | Household members and dependents |
| `filers` | Primary and spouse filer information |
| `filingStatus` | Filing status determination |
| `flow` | UI flow and navigation logic |
| `form1099Misc` | 1099-MISC form data |
| `form1099Rs` | 1099-R retirement distributions |
| `formW2s` | W-2 wage and tax statements |
| `hsa` | Health Savings Account |
| `imported` | Imported data handling |
| `income` | Income calculations |
| `interest` | Interest income |
| `mefTypes` | MeF-specific type definitions |
| `paymentMethod` | Payment method selection |
| `ptc` | Premium Tax Credit |
| `refundPrefs` | Refund preferences |
| `saversCredits` | Retirement savings contributions credit |
| `schedule2` | Additional taxes (Schedule 2) |
| `schedule3` | Additional credits (Schedule 3) |
| `signing` | Return signing |
| `socialSecurity` | Social security benefits |
| `spouseSection` | Spouse-specific information |
| `standardDeduction` | Standard deduction calculation |
| `studentLoanAdjustment` | Student loan interest deduction |
| `taxCalculations` | Tax computation |
| `unemployment` | Unemployment compensation |
| `validations` | Cross-field validation rules |

---

## Best Practices

### Module Naming

- Use **camelCase** for module names
- Choose **descriptive** names that indicate domain
- Keep names **concise** but clear

**Good:**
- `filingStatus` - Clear purpose
- `ctcOdc` - Standard abbreviation
- `familyAndHousehold` - Descriptive

**Avoid:**
- `module1` - Not descriptive
- `filing_status` - Use camelCase
- `misc` - Too vague

---

### Module Size

**Guidelines:**
- Keep modules **focused** on a single domain
- Prefer **smaller, focused modules** over large, monolithic ones
- Split modules if they exceed ~500 facts or ~2000 lines

**Reasons:**
- Easier to navigate and understand
- Faster parsing and loading
- Clearer ownership and responsibility
- Easier to test in isolation

---

### Documentation

Add clear descriptions to cross-module references:

```xml
<Fact path="/myCalculation">
  <Description>
    Calculates benefit using age from familyAndHousehold module
    and income from income module.
  </Description>
  
  <Derived>
    <Multiply>
      <Dependency module="familyAndHousehold" path="/age" />
      <Dependency module="income" path="/wages" />
    </Multiply>
  </Derived>
</Fact>
```

This helps maintainers understand the relationships.

---

### Testing Cross-Module Dependencies

When testing facts with cross-module dependencies:

```scala
test("fact with cross-module dependency") {
  // Load both modules
  val module1 = FactDictionary.fromXml(loadFile("module1.xml"))
  val module2 = FactDictionary.fromXml(loadFile("module2.xml"))
  
  // Merge dictionaries
  val dict = module1.merge(module2)
  
  // Create graph and test
  val graph = Graph(dict)
  graph.set("/inputFromModule2", Int(100))
  
  val result = graph.get("/derivedInModule1")
  assert(result.get == Int(200))
}
```

Always load all required modules for the test.

---

## Related Documentation

- [FXML Schema Reference](fxml-schema-reference.md) - Complete XML schema and elements
- [Core Concepts](core-concepts.md) - Understanding paths and dependencies
- [Developer Guide](developer-guide.md) - Adding facts and organizing code
