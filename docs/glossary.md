# Glossary

Alphabetized definitions of technical terms used throughout the Fact Graph system.

---

## A

**Abstract Path**  
A path containing wildcards (`*`) that represents multiple concrete paths. Used in fact definitions to define structure for collection members.  
*Example:* `/familyAndHousehold/*/age` defines age for all household members.

**Address**  
A complex writable type for storing postal addresses with MeF-specific validation rules. Includes street address, city, state, and ZIP code.

**Adjusted Gross Income (AGI)**  
A derived fact representing total income minus specific adjustments. Central calculation in tax determination.

---

## B

**BankAccount**  
A complex writable type for storing bank account information including routing number, account number, and account type.

**Boolean**  
A writable type representing true/false values. Used for yes/no questions and conditions.

---

## C

**Collection**  
An ordered list of items identified by UUIDs. Enables modeling of variable-length lists like dependents, W-2 forms, or income sources.  
*Example:* `/familyAndHousehold` contains household members as collection items.

**Collection Item**  
A single entry in a collection, identified by a UUID. Accessed via paths like `/collection/#uuid/property`.

**CompNode**  
(Computation Node) Base trait for all expression nodes in the system. Each CompNode represents an operation or value type with an associated Value type.  
*See:* `compnodes/` directory.

**Concrete Path**  
A fully resolved path with UUIDs substituted for wildcards. Identifies a specific fact instance in the graph.  
*Example:* `/familyAndHousehold/#abc123/age`

**Completeness**  
State indicating whether all dependencies of a fact have been satisfied. Facts can be complete (all dependencies set), placeholder (using default values), or incomplete (cannot be calculated).

---

## D

**Day**  
A writable type for date values, stored in ISO 8601 format (YYYY-MM-DD). Used for birth dates, tax deadlines, etc.

**Days**  
A writable type representing a duration in days. Used for time period calculations.

**Dependency**  
A reference from one fact to another. Creates the computational graph structure.  
*Syntax:* `<Dependency path="/otherFact" />` or `<Dependency module="moduleName" path="/fact" />`

**Derived Fact**  
A fact whose value is calculated from other facts using an expression tree. Cannot be set directly by users.  
*Opposite of:* Writable Fact.

**Dictionary**  
See: Fact Dictionary.

**Dollar**  
A writable type for currency amounts. Stored internally as integer cents to avoid floating-point precision issues.  
*Example:* `Dollar(5000000)` represents $50,000.00.

---

## E

**Ein**  
(Employer Identification Number) A writable type for business tax identification numbers. Format: XX-XXXXXXX.

**Enum**  
A writable type for single-selection from a list of options. References an options path for available values.  
*Example:* `Enum("/filingStatusOptions", "single")`

**EnumOptions**  
An expression that defines the available values for an Enum or MultiEnum type. Can include conditional options.

**Export**  
Attribute marking facts for export to downstream systems (`downstreamFacts="true"`) or MeF (`mef="true"`).

**Expression**  
A tree structure of operations that compute a derived fact's value. Defined as an enum with cases for each operation type.  
*See:* `Expression.scala`, expression-evaluation.md

---

## F

**Fact**  
A single piece of information in the fact graph. Can be writable (user input) or derived (calculated). Each fact has a path, type, and optional validation limits.

**Fact Definition**  
The template for a fact as defined in XML. Not attached to a specific graph instance. Converted to a Fact when instantiated in a graph.

**Fact Dictionary**  
The complete set of fact definitions loaded from XML files. Serves as the blueprint for creating fact graph instances.

**Fact Graph**  
A runtime instance of a fact dictionary populated with actual data. Represents a specific scenario (e.g., one taxpayer's return).

**Factual**  
A Scala implicit context parameter that provides access to the current graph instance during fact resolution and evaluation.

**Freeze**  
The act of locking a fact dictionary to prevent further modifications. Required before creating graphs. Validates metadata and ensures consistency.

**FXML**  
(Fact XML) The XML markup language used to define fact dictionaries. Schema defined in `FactDictionaryModule.rng`.

---

## G

**Graph**  
See: Fact Graph.

---

## I

**Incomplete**  
A Result state indicating a fact's value cannot be calculated due to missing dependencies.  
*See:* Result.

**Int**  
A writable type for integer numbers.

**IpPin**  
(Identity Protection PIN) A writable type for IRS-issued identity protection PINs.

---

## L

**Limit**  
A validation rule applied to writable facts. Enforces constraints like minimum/maximum values, string length, or regex patterns.  
*See:* validation-system.md

**Limit Context**  
A structure containing information about a validation limit: name, severity level, actual value, and threshold value.

**Limit Violation**  
An object representing a failed validation. Contains the limit context, fact path, and error message. Returned from `graph.save()`.

---

## M

**MaybeVector**  
A monad representing values that might be single or multiple (for collections). Used throughout the system to handle wildcard expansion.  
*States:* Single(value) or Multiple(vector, completeness).

**MeF**  
(Modernized e-File) The IRS electronic filing system. Facts can be marked for MeF export with `mef="true"` attribute.

**Member**  
A PathItem type representing a specific collection item via UUID.  
*Syntax:* `#uuid` in paths.

**Meta**  
Metadata about a fact dictionary including version, tax year, and test dictionary flag.

**Migration**  
A programmatic function that transforms persisted graph data to match updated dictionary requirements. Essential for preserving user data across schema changes.  
*See:* migrations.md

**Module**  
A single XML file in `fact_dictionaries/` containing related fact definitions. Module name equals filename without `.xml` extension.  
*See:* module-system.md

**MultiEnum**  
A writable type for multiple selections from a list of options. Stores array of selected values.

---

## O

**Override**  
A conditional replacement of a fact's value based on a boolean condition. Allows context-specific value substitution.  
*Syntax:* `<Override><Condition>...</Condition><Default>...</Default></Override>`

---

## P

**Parent**  
A PathItem type representing upward navigation in the path hierarchy.  
*Syntax:* `..` in paths.

**Path**  
A string identifier for a fact, similar to filesystem paths. Can be absolute (`/fact`) or relative (`../fact`), and may contain wildcards (`*`) or UUIDs (`#uuid`).

**PathItem**  
A component of a parsed path. Types: Child (name), Wildcard (*), Member (#uuid), Parent (..).

**Persister**  
An interface for storing and retrieving fact values. Handles serialization, validation, and persistence to storage backend.  
*Default implementation:* InMemoryPersister.

**PhoneNumber**  
A writable type for telephone numbers with validation.

**Pin**  
A writable type for personal identification numbers.

**Placeholder**  
1. A Result state indicating a value is available but may change when dependencies become complete.  
2. An XML element defining default values for writable facts when dependencies are incomplete.  
*See:* Result.

---

## R

**Rational**  
A writable type for fractional numbers, stored as numerator/denominator.  
*Format:* "12/100" represents 12/100.

**Result**  
A monad representing the completeness state of a fact's value.  
*States:* Complete(value), Placeholder(value), or Incomplete.  
*See:* monads-and-result-handling.md

---

## S

**Str** (String)  
A writable type for text values.

**Switch**  
A conditional expression that evaluates cases in order, returning the first matching result. Similar to switch/case or if/else-if logic.  
*Syntax:* `<Switch><Case><When>...</When><Then>...</Then></Case></Switch>`

---

## T

**Thunk**  
A wrapper for lazy evaluation of expressions. Memoizes results for performance. Critical for large graphs where not all facts need immediate computation.  
*See:* monads-and-result-handling.md

**Tin**  
(Tax Identification Number) A writable type for SSN or EIN. Includes format validation.  
*Formats:* SSN (XXX-XX-XXXX) or EIN (XX-XXXXXXX).

**Type**  
See: Writable Type.

---

## U

**UUID**  
(Universally Unique Identifier) Used to identify collection items. Appears in paths with `#` prefix.  
*Example:* `/familyAndHousehold/#abc123/name`

---

## V

**Validation**  
The process of checking writable facts against their defined limits. Returns violations for any facts that don't meet constraints.  
*See:* validation-system.md

**Vectorization**  
The process of applying operations element-wise across collections. Methods like `vectorize2`, `vectorize4` lift operations to work on MaybeVector values.

---

## W

**Wildcard**  
The `*` symbol in paths representing all members of a collection. Expands to multiple concrete paths during evaluation.  
*Example:* `/familyAndHousehold/*/age` expands to `/familyAndHousehold/#uuid1/age`, `/familyAndHousehold/#uuid2/age`, etc.

**Writable Fact**  
A fact whose value can be set by users or applications. Includes type specification and optional validation limits.  
*Opposite of:* Derived Fact.

**Writable Type**  
A concrete type that can be stored in the persister. All writable types extend the WritableType trait and implement JSON serialization.  
*Examples:* Boolean, Int, String, Dollar, Enum, Address.

---

## Related Documentation

- [Core Concepts](core-concepts.md) - Foundational system concepts
- [Type System](type-system.md) - Detailed type information
- [FXML Schema Reference](fxml-schema-reference.md) - Complete XML schema
- [Architecture Overview](architecture-overview.md) - System design and relationships
