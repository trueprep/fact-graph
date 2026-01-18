# Integration Guide

Complete guide for integrating Fact Graph into JavaScript, JVM, and Java applications with practical examples and best practices.

---

## JavaScript Integration

### Loading the Library

**ES Module Import:**

```javascript
import * as fg from './fact-graph.mjs'
```

**Alternative - Dynamic Import:**

```javascript
const fg = await import('./fact-graph.mjs')
```

**Loading from CDN (if published):**

```javascript
import * as fg from 'https://cdn.example.com/fact-graph/3.1.0/fact-graph.mjs'
```

---

### Creating a Dictionary

**Load XML from file:**

```javascript
const xmlString = await fetch('dictionary.xml').then(r => r.text())
const dictionary = fg.FactDictionary.importFromXml(xmlString)
```

**Load multiple modules:**

```javascript
const modules = [
  'filers.xml',
  'income.xml',
  'familyAndHousehold.xml'
]

const xmlStrings = await Promise.all(
  modules.map(file => fetch(`fact_dictionaries/${file}`).then(r => r.text()))
)

// Import and merge
let dictionary = fg.FactDictionary.importFromXml(xmlStrings[0])
for (let i = 1; i < xmlStrings.length; i++) {
  dictionary = dictionary.merge(fg.FactDictionary.importFromXml(xmlStrings[i]))
}

// Freeze before use
dictionary.freeze()
```

---

### Creating a Graph

**Basic creation:**

```javascript
const graph = new fg.Graph(dictionary)
```

**With existing data:**

```javascript
const savedJson = localStorage.getItem('taxReturn')
const graph = fg.Graph.fromJson(dictionary, savedJson)
```

---

### Setting Facts

**Simple types:**

```javascript
// Integer
graph.set('/age', fg.Int(30))

// Boolean
graph.set('/isMarried', fg.Boolean(true))

// String
graph.set('/name', fg.Str('John Doe'))

// Dollar (stored as cents)
graph.set('/income', fg.Dollar(5000000))  // $50,000.00

// Day (date)
graph.set('/dateOfBirth', fg.Day('1990-05-15'))
```

**Enum types:**

```javascript
// Enum - single selection
graph.set('/filingStatus', fg.Enum('/filingStatusOptions', 'single'))

// MultiEnum - multiple selections
graph.set('/incomeTypes', fg.MultiEnum('/incomeTypeOptions', ['wages', 'interest']))
```

**Complex types:**

```javascript
// Address
graph.set('/address', fg.Address({
  streetAddress: '123 Main St',
  city: 'Springfield',
  state: 'IL',
  zipCode: '62701'
}))

// Bank Account
graph.set('/bankAccount', fg.BankAccount({
  routingNumber: '123456789',
  accountNumber: '987654321',
  accountType: 'checking'
}))
```

---

### Getting Facts

**Basic retrieval:**

```javascript
const result = graph.get('/age')

if (result.hasValue) {
  console.log('Age:', result.get.value)  // Access Int value
  console.log('Complete:', result.complete)  // true if no placeholders
} else {
  console.log('Age not set or cannot be calculated')
}
```

**Handling Result states:**

```javascript
const result = graph.get('/calculatedField')

if (result.complete) {
  // Definitive value, all dependencies complete
  displayValue(result.get)
} else if (result.hasValue) {
  // Placeholder value, some dependencies incomplete
  displayPlaceholder(result.get)
} else {
  // No value available
  showError('Cannot calculate value')
}
```

**Getting collection values:**

```javascript
// Get all items in collection with wildcard
const ages = graph.getVect('/familyAndHousehold/*/age')

ages.forEach(result => {
  if (result.hasValue) {
    console.log('Age:', result.get.value)
  }
})
```

---

### Working with Collections

**Add items:**

```javascript
// Generate UUID
const uuid = crypto.randomUUID()

// Add to collection
graph.addToCollection('/familyAndHousehold', uuid)

// Set item properties
graph.set(`/familyAndHousehold/#${uuid}/name`, fg.Str('Alice'))
graph.set(`/familyAndHousehold/#${uuid}/age`, fg.Int(8))
```

**Remove items:**

```javascript
graph.removeFromCollection('/familyAndHousehold', uuid)
```

**Iterate over items:**

```javascript
const collection = graph.get('/familyAndHousehold')

if (collection.hasValue) {
  const uuids = collection.get.items  // Array of UUIDs
  
  uuids.forEach(uuid => {
    const name = graph.get(`/familyAndHousehold/#${uuid}/name`)
    const age = graph.get(`/familyAndHousehold/#${uuid}/age`)
    console.log(`${name.get.value} is ${age.get.value} years old`)
  })
}
```

---

### Validation

**Validate all facts:**

```javascript
const [valid, violations] = graph.save()

if (!valid) {
  violations.forEach(violation => {
    console.error(`${violation.path}: ${violation.message}`)
    // Example: "/age: Value must be at least 0 (actual: -5)"
  })
}
```

**Handle violations in UI:**

```javascript
const [valid, violations] = graph.save()

// Clear previous errors
clearAllErrors()

if (!valid) {
  violations.forEach(v => {
    // Show error near the input field
    showError(v.path, v.context.limitName, v.message)
    
    // Highlight field
    highlightField(v.path)
  })
  
  // Prevent submission
  return false
}

// Proceed with submission
submitReturn()
```

---

### Serialization and Persistence

**Save to JSON:**

```javascript
const json = graph.getPersister().toJson()

// Store in localStorage
localStorage.setItem('taxReturn', json)

// Or send to server
await fetch('/api/save', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: json
})
```

**Load from JSON:**

```javascript
// Retrieve from localStorage
const json = localStorage.getItem('taxReturn')

// Restore graph
const graph = fg.Graph.fromJson(dictionary, json)
```

**Export as object:**

```javascript
const data = graph.getPersister().toMap()

// Returns: { "/age": 30, "/name": "John", ... }
```

---

### Complete Example

```javascript
import * as fg from './fact-graph.mjs'

async function main() {
  // 1. Load dictionary
  const xml = await fetch('dictionary.xml').then(r => r.text())
  const dictionary = fg.FactDictionary.importFromXml(xml)
  dictionary.freeze()
  
  // 2. Create or restore graph
  let graph
  const savedData = localStorage.getItem('taxReturn')
  
  if (savedData) {
    graph = fg.Graph.fromJson(dictionary, savedData)
  } else {
    graph = new fg.Graph(dictionary)
  }
  
  // 3. Set user input
  graph.set('/age', fg.Int(30))
  graph.set('/filingStatus', fg.Enum('/filingStatusOptions', 'single'))
  graph.set('/income/wages', fg.Dollar(5000000))  // $50,000
  
  // 4. Get calculated values
  const standardDeduction = graph.get('/standardDeduction')
  if (standardDeduction.hasValue) {
    console.log('Standard Deduction:', standardDeduction.get.value / 100)
  }
  
  const taxOwed = graph.get('/taxOwed')
  if (taxOwed.hasValue) {
    console.log('Tax Owed:', taxOwed.get.value / 100)
  }
  
  // 5. Validate
  const [valid, violations] = graph.save()
  
  if (!valid) {
    console.error('Validation errors:')
    violations.forEach(v => console.error(`  ${v.path}: ${v.message}`))
    return
  }
  
  // 6. Save
  const json = graph.getPersister().toJson()
  localStorage.setItem('taxReturn', json)
  
  console.log('Tax return saved successfully!')
}

main().catch(console.error)
```

---

## JVM/Scala Integration

### Import Statements

```scala
import gov.irs.factgraph._
import gov.irs.factgraph.types._
import gov.irs.factgraph.monads.Result
import gov.irs.factgraph.persisters.InMemoryPersister

import scala.io.Source
import java.nio.file.{Files, Paths}
```

---

### Loading Dictionary

**From file:**

```scala
val xmlString = Source.fromFile("dictionary.xml").mkString
val dictionary = FactDictionary.importFromXml(xmlString)
dictionary.freeze()
```

**From resource:**

```scala
val xmlStream = getClass.getResourceAsStream("/dictionary.xml")
val xmlString = Source.fromInputStream(xmlStream).mkString
val dictionary = FactDictionary.importFromXml(xmlString)
```

**Multiple modules:**

```scala
val modules = List(
  "filers.xml",
  "income.xml",
  "familyAndHousehold.xml"
)

val dictionaries = modules.map { file =>
  val xml = Source.fromFile(s"fact_dictionaries/$file").mkString
  FactDictionary.fromXml(xml)
}

val dictionary = dictionaries.reduce(_ merge _)
dictionary.freeze()
```

---

### Creating Graph

**With default persister:**

```scala
val graph = Graph(dictionary)
```

**With custom persister:**

```scala
val persister = new MyCustomPersister()
val graph = Graph(dictionary, persister)
```

**From existing data:**

```scala
val json = Files.readString(Paths.get("saved.json"))
val graph = Graph.fromJson(dictionary, json)
```

---

### Setting and Getting Facts

**Type-safe operations:**

```scala
// Set facts
graph.set("/age", Int(30))
graph.set("/filingStatus", Enum("/filingStatusOptions", "single"))
graph.set("/income/wages", Dollar(5000000))

// Get facts
val ageResult: Result[Int] = graph.get("/age")
val age: Int = ageResult.get

// Pattern match on Result
graph.get("/calculatedField") match
  case Result.Complete(value) =>
    println(s"Value: $value")
  case Result.Placeholder(value) =>
    println(s"Placeholder: $value")
  case Result.Incomplete =>
    println("Value not available")
```

**Collections:**

```scala
val uuid = java.util.UUID.randomUUID().toString

graph.addToCollection("/familyAndHousehold", uuid)
graph.set(s"/familyAndHousehold/#$uuid/name", Str("Alice"))
graph.set(s"/familyAndHousehold/#$uuid/age", Int(8))

// Get collection
val collection = graph.get("/familyAndHousehold")
val uuids = collection.get.items
```

---

### Validation

```scala
val (valid, violations) = graph.save()

if !valid then
  violations.foreach { v =>
    println(s"${v.path}: ${v.message}")
  }
else
  println("All validations passed")
```

---

### Persistence

```scala
// Save to JSON
val json = graph.getPersister().toJson(indent = 2)
Files.writeString(Paths.get("saved.json"), json)

// Load from JSON
val loadedJson = Files.readString(Paths.get("saved.json"))
val restoredGraph = Graph.fromJson(dictionary, loadedJson)
```

---

### Complete Example

```scala
import gov.irs.factgraph._
import gov.irs.factgraph.types._
import scala.io.Source
import java.nio.file.{Files, Paths}

object TaxCalculator extends App {
  // Load dictionary
  val xml = Source.fromFile("dictionary.xml").mkString
  val dictionary = FactDictionary.importFromXml(xml)
  dictionary.freeze()
  
  // Create graph
  val graph = Graph(dictionary)
  
  // Set user input
  graph.set("/age", Int(30))
  graph.set("/filingStatus", Enum("/filingStatusOptions", "single"))
  graph.set("/income/wages", Dollar(5000000))
  
  // Calculate
  val agi = graph.get("/adjustedGrossIncome")
  println(s"AGI: $${agi.get.value / 100}")
  
  val standardDeduction = graph.get("/standardDeduction")
  println(s"Standard Deduction: $${standardDeduction.get.value / 100}")
  
  val taxOwed = graph.get("/taxOwed")
  println(s"Tax Owed: $${taxOwed.get.value / 100}")
  
  // Validate
  val (valid, violations) = graph.save()
  
  if valid then
    // Save
    val json = graph.getPersister().toJson(indent = 2)
    Files.writeString(Paths.get("return.json"), json)
    println("Tax return saved!")
  else
    println("Validation errors:")
    violations.foreach(v => println(s"  ${v.path}: ${v.message}"))
}
```

---

## Java Interop

### Basic Usage

```java
import gov.irs.factgraph.*;
import gov.irs.factgraph.types.*;
import gov.irs.factgraph.monads.Result;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TaxCalculator {
    public static void main(String[] args) throws Exception {
        // Load dictionary
        String xml = Files.readString(Paths.get("dictionary.xml"));
        FactDictionary dict = FactDictionary.importFromXml(xml);
        dict.freeze();
        
        // Create graph
        Graph graph = Graph.apply(dict);
        
        // Set facts
        graph.set("/age", new Int(30));
        graph.set("/filingStatus", 
            new Enum("/filingStatusOptions", "single"));
        graph.set("/income/wages", new Dollar(5000000));
        
        // Get facts
        Result<?> ageResult = graph.get("/age");
        if (ageResult.hasValue()) {
            Int age = (Int) ageResult.get();
            System.out.println("Age: " + age.value());
        }
        
        // Validate
        scala.Tuple2<Boolean, scala.collection.Seq<?>> saveResult = 
            graph.save();
        boolean valid = saveResult._1();
        
        if (valid) {
            // Save to JSON
            String json = graph.getPersister().toJson();
            Files.writeString(Paths.get("return.json"), json);
        }
    }
}
```

---

### Handling Scala Types in Java

**Scala collections to Java:**

```java
import scala.jdk.CollectionConverters;

// Get violations as Java list
scala.collection.Seq<?> violations = saveResult._2();
List<LimitViolation> javaViolations = 
    CollectionConverters.SeqHasAsJava(violations).asJava();

for (LimitViolation v : javaViolations) {
    System.out.println(v.path() + ": " + v.message());
}
```

**Options:**

```java
import scala.Option;

Option<FactDefinition> factDef = dictionary.apply("/factPath");

if (factDef.isDefined()) {
    FactDefinition def = factDef.get();
    // Use definition
}
```

**Pattern matching alternative:**

```java
// Instead of pattern matching, use methods
Result<?> result = graph.get("/factPath");

if (result instanceof Result.Complete) {
    Object value = ((Result.Complete<?>) result).value();
    // Use value
} else if (result instanceof Result.Placeholder) {
    Object placeholder = ((Result.Placeholder<?>) result).value();
    // Show placeholder
} else {
    // Result.Incomplete
    // Handle missing value
}
```

---

## Custom Persister Implementation

### Implementing the Persister Trait

```scala
import gov.irs.factgraph.persisters.Persister
import gov.irs.factgraph.types.WritableType
import gov.irs.factgraph.monads.Result
import gov.irs.factgraph.{Path, Fact, LimitViolation}

import java.sql.{Connection, DriverManager}

class DatabasePersister(connectionString: String) extends Persister {
  private var connection: Connection = _
  private var graphInstance: Option[Graph] = None
  
  def connect(): Unit = {
    connection = DriverManager.getConnection(connectionString)
  }
  
  override def setFact(fact: Fact, value: WritableType): Unit = {
    val stmt = connection.prepareStatement(
      "INSERT OR REPLACE INTO facts (path, value) VALUES (?, ?)"
    )
    stmt.setString(1, fact.path.toString)
    stmt.setString(2, value.toJson.toString)
    stmt.executeUpdate()
  }
  
  override def getSavedResult[A](path: Path, klass: Class[A]): Result[A] = {
    val stmt = connection.prepareStatement(
      "SELECT value FROM facts WHERE path = ?"
    )
    stmt.setString(1, path.toString)
    val rs = stmt.executeQuery()
    
    if rs.next() then
      val json = ujson.read(rs.getString("value"))
      val value = TypeContainer.fromJson(json).item
      
      if klass.isInstance(value) then
        Result.Complete(value.asInstanceOf[A])
      else
        throw new FactGraphValidationException("Type mismatch")
    else
      Result.Incomplete
  }
  
  override def deleteFact(path: Path): Unit = {
    val stmt = connection.prepareStatement("DELETE FROM facts WHERE path = ?")
    stmt.setString(1, path.toString)
    stmt.executeUpdate()
  }
  
  override def save(): (Boolean, Seq[LimitViolation]) = {
    // Validate using inherited logic
    val result = super.save()
    
    if result._1 then
      connection.commit()
    else
      connection.rollback()
    
    result
  }
  
  override def toJson(indent: Int = 0): String = {
    val stmt = connection.createStatement()
    val rs = stmt.executeQuery("SELECT path, value FROM facts")
    
    val facts = scala.collection.mutable.Map[String, ujson.Value]()
    while rs.next() do
      facts(rs.getString("path")) = ujson.read(rs.getString("value"))
    
    val json = ujson.Obj(
      "facts" -> ujson.Obj(facts.toSeq: _*),
      "migrations" -> ujson.Num(Migrations.TotalMigrations)
    )
    
    ujson.write(json, indent)
  }
  
  override def toMap: Map[String, WritableType] = {
    val stmt = connection.createStatement()
    val rs = stmt.executeQuery("SELECT path, value FROM facts")
    
    val result = scala.collection.mutable.Map[String, WritableType]()
    while rs.next() do
      val json = ujson.read(rs.getString("value"))
      result(rs.getString("path")) = TypeContainer.fromJson(json).item
    
    result.toMap
  }
  
  override def syncWithDictionary(dictionary: FactDictionary): Unit = {
    // Remove facts not in dictionary
    val stmt = connection.createStatement()
    val rs = stmt.executeQuery("SELECT path FROM facts")
    
    while rs.next() do
      val path = Path(rs.getString("path"))
      if dictionary(path).isEmpty then
        deleteFact(path)
  }
  
  override def setGraph(graph: Graph): Unit = {
    graphInstance = Some(graph)
  }
  
  override def getGraph(): Option[Graph] = graphInstance
}
```

---

### Usage

```scala
// Create custom persister
val persister = new DatabasePersister("jdbc:sqlite:taxdata.db")
persister.connect()

// Create graph with custom persister
val graph = Graph(dictionary, persister)

// Use normally
graph.set("/age", Int(30))
val (valid, violations) = graph.save()  // Saves to database
```

---

## API Surface Summary

### Public API (Library Consumers)

**Dictionary operations:**
- `FactDictionary.importFromXml(xmlString)` - Load dictionary from XML
- `dictionary.merge(other)` - Combine dictionaries
- `dictionary.freeze()` - Lock dictionary for use

**Graph operations:**
- `Graph(dictionary)` - Create graph
- `Graph(dictionary, persister)` - Create with custom persister
- `Graph.fromJson(dictionary, json)` - Restore from JSON

**Fact operations:**
- `graph.set(path, value)` - Set writable fact
- `graph.get(path)` - Get fact value
- `graph.getVect(path)` - Get collection values
- `graph.delete(path)` - Delete fact value

**Collection operations:**
- `graph.addToCollection(path, uuid)` - Add item
- `graph.removeFromCollection(path, uuid)` - Remove item

**Validation:**
- `graph.save()` - Validate all facts, returns `(Boolean, Seq[LimitViolation])`

**Debugging:**
- `graph.explain(path)` - Human-readable explanation
- `graph.debugFact(path)` - XML with current values
- `graph.debugFactRecurse(path)` - Full dependency tree

**Persistence:**
- `graph.getPersister().toJson()` - Serialize to JSON
- `graph.getPersister().toMap` - Export as Map

---

### Internal API (Extending Functionality)

**For adding operations:**
- `CompNode` trait - Base for computation nodes
- `CompNodeFactory` trait - Factory for parsing
- `Expression[A]` enum - Expression types

**For adding types:**
- `WritableType` trait - Base for value types
- Type-specific CompNode implementations

**For adding validation:**
- `Limit` trait - Base for validation rules
- `LimitFactory` trait - Factory for parsing

**For custom storage:**
- `Persister` trait - Persistence interface

---

## Related Documentation

- [Type System](type-system.md) - All available types and their usage
- [FXML Schema Reference](fxml-schema-reference.md) - Dictionary XML format
- [Validation System](validation-system.md) - Understanding limit violations
- [Persistence Guide](persistence-guide.md) - Data storage and JSON format
