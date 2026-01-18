# Migrations

The migration system enables safe updates to persisted Fact Graphs when the underlying dictionary changes, preserving user data across schema updates.

---

## Purpose & Overview

### Why Migrations Exist

When Fact Graph updates are deployed, they affect:
- Backend servers (traditional deployment)
- **User browsers** (where in-progress tax returns are stored)

Users may have incomplete tax returns stored in their browser's localStorage or IndexedDB. When the Fact Dictionary schema changes (facts added, removed, or modified), these saved graphs must be updated to match the new schema.

Without migrations, users would lose their in-progress work or encounter errors when loading saved data.

### How Migrations Enable Safe Updates

Migrations are programmatic functions that transform saved JSON data to match new dictionary requirements:

- **Preserve user data** across schema changes
- **Apply transformations** to existing values (rename paths, delete invalid data, convert formats)
- **Track which migrations** have been applied to each saved graph
- **Run automatically** when loading saved data

### When to Use Migrations

Create a migration when:

1. **Renaming fact paths** - Update persisted keys to new path names
2. **Deleting facts** - Remove obsolete data from saved graphs
3. **Changing validation rules** - Clean up data that no longer validates
4. **Restructuring collections** - Reorganize collection items
5. **Converting data formats** - Transform values to new type representations

**Do NOT need migrations for:**
- Adding new facts (existing data unaffected)
- Modifying calculations (derived facts recalculated automatically)
- Updating descriptions or metadata

---

## Migration Function Signature

Each migration is a function with this signature:

```scala
private def m<N>_DescriptiveName(data: Map[String, Value]): Map[String, Value]
```

### Parameters

**Input: `data: Map[String, Value]`**
- Keys are fact paths as strings (e.g., `"/age"`, `"/income/wages"`)
- Values are `ujson.Value` objects (JSON representation)
- Represents the entire persisted graph state

**Output: `Map[String, Value]`**
- Transformed data with same structure
- May have added, removed, or modified entries

### Naming Convention

Migrations follow strict naming: `m<N>_DescriptiveName`

- `<N>`: Sequential number (1, 2, 3, ...)
- `DescriptiveName`: CamelCase description of what it does

**Examples:**
- `m1_BlankMigration` - Test migration (no-op)
- `m2_DeleteInvalidAddresses` - Removes non-validating addresses
- `m3_RenameFilingStatus` - Updates fact path names

**Critical:** Numbers must increase monotonically without gaps.

---

## How Migrations Work

### The AllMigrations List

All migrations are registered in `Migrations.scala`:

```scala
private val AllMigrations = List(
  m1_BlankMigration,
  m2_DeleteInvalidAddresses,
  // Future migrations added here
)
```

**Key Properties:**
- List order matters (migrations run sequentially)
- Never reorder the list
- Always append new migrations to the end

### Migration Tracking

Each persisted graph stores how many migrations have been applied:

```json
{
  "facts": { ... },
  "migrations": 2
}
```

- `migrations` field tracks the count (not which specific migrations)
- Starts at 0 for new graphs
- Incremented after each migration runs

### Execution Flow

When loading a saved graph:

```scala
def run(data: Map[String, Value], numMigrations: Int): Map[Path, WritableType] =
  AllMigrations
    .drop(numMigrations)              // Skip already-applied migrations
    .foldLeft(data)((data, migration) => 
      migration(data)                  // Apply each missing migration
    )
    .map((k, v) => 
      (Path(k), read[TypeContainer](v).item)  // Convert to typed data
    )
```

1. **Identify missing migrations** - Compare saved count vs. `TotalMigrations`
2. **Apply each missing migration** - Run sequentially on the data
3. **Update migration count** - Increment to current total
4. **Return transformed data** - Ready for graph loading

---

## Adding New Migrations

Follow these steps to add a migration:

### Step 1: Create Migration Function

Add a new private function to `Migrations.scala`:

```scala
private def m3_RenameIncomePath(data: Map[String, Value]): Map[String, Value] =
  data.map {
    case (key, value) if key == "/oldIncomePath" =>
      ("/newIncomePath", value)
    case (key, value) =>
      (key, value)
  }
```

### Step 2: Add to AllMigrations List

Append (never insert) to the list:

```scala
private val AllMigrations = List(
  m1_BlankMigration,
  m2_DeleteInvalidAddresses,
  m3_RenameIncomePath,  // New migration added at end
)
```

### Step 3: Test with Old Data

Create test with pre-migration data:

```scala
test("m3 renames income path") {
  val oldData = Map(
    "/oldIncomePath" -> ujson.Num(50000)
  )
  
  val result = Migrations.run(oldData, 2)  // Apply migration 3
  
  assert(result.contains(Path("/newIncomePath")))
  assert(!result.contains(Path("/oldIncomePath")))
}
```

### Step 4: Deploy

Once deployed:
- `TotalMigrations` increments automatically
- Users' saved graphs run the new migration on next load
- Migration becomes permanent (cannot be removed)

---

## Critical Rules

### Never Reorder AllMigrations

**Why:** Saved graphs track migration count, not which specific migrations ran.

**Bad Example:**
```scala
// Original list
List(m1_A, m2_B, m3_C)

// Someone reorders (WRONG!)
List(m1_A, m3_C, m2_B)  // Now m2 and m3 are swapped
```

Result: Graphs that already ran m2 and m3 will re-run them in wrong order, corrupting data.

**Solution:** Always append new migrations to the end.

### Numbers Must Be Monotonic

Each migration number must be exactly one higher than the previous:

**Correct:**
```scala
m1_First
m2_Second
m3_Third
```

**Incorrect:**
```scala
m1_First
m3_Third   // Skipped m2!
m4_Fourth
```

The number serves as explicit documentation of order and helps catch reordering mistakes.

### Migrations Are Permanent

Once deployed, a migration:
- Cannot be removed (would break existing data)
- Cannot be modified (users may have already run it)
- Cannot be skipped (would create data inconsistency)

If a migration has a bug, create a new migration to fix it.

---

## Examples

### Example 1: Blank Migration (Identity Function)

The simplest migration - returns data unchanged:

```scala
private def m1_BlankMigration(data: Map[String, Value]): Map[String, Value] =
  data
```

**Purpose:** Test the migration mechanism without changing data.

**Use case:** Verify the migration system works before deploying actual transformations.

---

### Example 2: Delete Invalid Addresses

Remove addresses that don't match MeF validation rules:

```scala
private def m2_DeleteInvalidAddresses(data: Map[String, Value]): Map[String, Value] =
  data.filterNot((_, value) =>
    value("$type").value == "gov.irs.factgraph.persisters.AddressWrapper" &&
      !value("item")("streetAddress").str.matches("[A-Za-z0-9]( ?[A-Za-z0-9\\-/])*")
  )
```

**Explanation:**
1. Filter out entries where the value is an Address
2. Check if `streetAddress` matches the required MeF regex
3. Remove entries that don't match

**Why needed:** MeF validation rules changed, existing saved addresses might be invalid.

**Result:** Users' invalid addresses removed, they'll be prompted to re-enter.

---

### Example 3: Rename Fact Path (Hypothetical)

Rename a fact path when the dictionary structure changes:

```scala
private def m3_RenameDependentPath(data: Map[String, Value]): Map[String, Value] =
  data.map {
    case (key, value) if key.startsWith("/dependent") =>
      (key.replace("/dependent", "/familyAndHousehold"), value)
    case (key, value) =>
      (key, value)
  }
```

**Explanation:**
1. Find all keys starting with `/dependent`
2. Replace prefix with `/familyAndHousehold`
3. Keep other keys unchanged

**Use case:** Dictionary reorganized, moved dependents under different path.

---

### Example 4: Convert Data Format (Hypothetical)

Transform values to a new representation:

```scala
private def m4_ConvertCentsToDollars(data: Map[String, Value]): Map[String, Value] =
  data.map {
    case (key, value) if key.endsWith("/amount") =>
      // Assuming old format stored as string, new format as number (cents)
      val cents = (value.str.toDouble * 100).toInt
      (key, ujson.Num(cents))
    case (key, value) =>
      (key, value)
  }
```

**Explanation:**
1. Find all fact paths ending with `/amount`
2. Convert from dollar string to cents integer
3. Update value format

**Use case:** Type representation changed from String to Dollar (stored as Int cents).

---

## Best Practices

### Test with Production-Like Data

Before deploying:

1. Export sample production data (anonymized)
2. Run migration on sample data
3. Verify results match expectations
4. Check for edge cases (empty values, null, extreme numbers)

```scala
test("migration handles edge cases") {
  val testData = Map(
    "/path1" -> ujson.Null,
    "/path2" -> ujson.Str(""),
    "/path3" -> ujson.Num(999999999)
  )
  
  val result = m3_MyMigration(testData)
  
  // Verify no crashes, reasonable handling
}
```

### Consider Data Loss Implications

Migrations that delete data should be carefully considered:

**Questions to ask:**
- Will users need to re-enter this data?
- Is there a way to preserve partial information?
- Should we show a message about the change?

**Example - Preserve what's possible:**

```scala
// Instead of deleting entire address
data.filterNot(isInvalidAddress)

// Try to preserve partial data
data.map {
  case (key, address) if isInvalidAddress(address) =>
    (key, sanitizeAddress(address))  // Keep valid parts
  case other => other
}
```

### Document the "Why"

Include comments explaining:
- What dictionary change prompted the migration
- What data is affected
- Why this transformation approach was chosen

```scala
// m5_RemoveTaxYear2023Data
// When: 2025 tax season started
// Why: Remove old tax year data to free space, users finished 2023 returns
// Impact: Only affects completed returns older than threshold
private def m5_RemoveTaxYear2023Data(data: Map[String, Value]): Map[String, Value] =
  data.filterNot((key, _) => key.startsWith("/taxYear2023"))
```

### Keep Migrations Focused

Each migration should do one thing:

**Good:**
```scala
m5_DeleteInvalidPhoneNumbers  // One focused task
```

**Avoid:**
```scala
m5_CleanupVariousIssues  // Too broad, hard to test
```

If multiple changes are needed, create multiple migrations.

---

## Related Documentation

- [Persistence Guide](persistence-guide.md) - How saved data is structured
- [Type System](type-system.md) - Type representations in JSON
- [Developer Guide](developer-guide.md) - Testing strategies
