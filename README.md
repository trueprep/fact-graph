# Fact Graph

## Legal Disclaimer: Public Repository Access

> **No Endorsement or Warranty**
>
> The Internal Revenue Service (IRS) does not endorse, maintain, or guarantee the accuracy, completeness, or functionality of the code in this repository.
> The IRS assumes no responsibility or liability for any use of the code by external parties, including individuals, developers, or organizations.
> This includes—but is not limited to—any tax consequences, computation errors, data loss, or other outcomes resulting from the use or modification of this code.
>
> Use of the code in this repository is at your own risk. Users of this repository are responsible for complying with any open source or third-party licenses.

## What is the Fact Graph?

The Fact Graph is a production-ready knowledge graph for modeling, among other things, the United States Internal Revenue Code and related tax law.
It can be used in JavaScript as well as any JVM language (Java, Kotlin, Scala, Clojure, etc.).

## Onboarding and Set Up
See [ONBOARDING.md](ONBOARDING.md) for environment/developer setup.

See [the Fact Graph 3.1 ADR](docs/fact-graph-3.1-adr.md) for more information about the fact graph and how it has been changed since early 2025
See [here](docs/from-3.0-to-3.1.md) for a brief description of changes between the older versions of the Fact Graph and the current v3.1 in this repository

## Fact Graph API (local demo)
The repository includes a small REST API wrapper and a docker-compose definition for local testing.

### Run the API
```bash
docker compose up --build
```

### Verify health
```bash
curl http://localhost:8080/health
```

### Default dictionary
The container loads all XML files from `fact_dictionaries/` by default and **merges** them into a single `FactDictionary` at startup (this matches how Direct File ships and uses these dictionaries).

If you want the small demo dictionary instead, set:
`FACT_DICTIONARY_PATH=/app/dictionaries/default.xml`

## Agent-facing API Endpoints

The Fact Graph API includes endpoints designed for AI agents to discover, compute, and explain tax facts. All endpoints return JSON responses.

### Health & Discovery

- **`GET /health`**: Check API health
  ```bash
  curl http://localhost:8080/health
  # {"status":"healthy","version":"3.1.0-SNAPSHOT"}
  ```

- **`GET /paths`**: List all available fact paths
  ```bash
  curl http://localhost:8080/paths
  # {"paths":["/importedPrimaryFilerFirstName","/totalAdjustedGrossIncome",...]}
  ```

### Fact Introspection

- **`GET /fact/definition?path=/factPath`**: Get metadata about a fact
  ```bash
  curl "http://localhost:8080/fact/definition?path=/importedPrimaryFilerFirstName"
  # {"path":"/importedPrimaryFilerFirstName","typeNode":"StringNode","isWritable":true,"rawXml":"..."}
  ```

- **`GET /fact/raw_xml?path=/factPath`**: Get the raw XML definition
  ```bash
  curl "http://localhost:8080/fact/raw_xml?path=/importedPrimaryFilerFirstName"
  # {"path":"/importedPrimaryFilerFirstName","xml":"<Fact path=\"/importedPrimaryFilerFirstName\">..."}
  ```

### Dependency Analysis

- **`GET /fact/deps?path=/factPath`**: Get forward dependencies (what this fact needs)
  ```bash
  curl "http://localhost:8080/fact/deps?path=/totalAdjustedGrossIncome"
  # {"path":"/totalAdjustedGrossIncome","dependencies":[{"path":"/totalWages","module":"income"}]}
  ```

- **`GET /fact/reverse_deps?path=/factPath`**: Get reverse dependencies (what depends on this fact)
  ```bash
  curl "http://localhost:8080/fact/reverse_deps?path=/totalWages"
  # {"path":"/totalWages","reverseDependencies":["/totalAdjustedGrossIncome","/taxableIncome"]}
  ```

### Computation & Explanation

- **`POST /fact/set`**: Set a writable fact value (supports typed JSON)
  ```bash
  # String fact
  curl -X POST http://localhost:8080/fact/set -H "Content-Type: application/json" -d '{"path":"/importedPrimaryFilerFirstName","value":"John"}'
  # {"path":"/importedPrimaryFilerFirstName","value":"John","success":true,"isComplete":true}

  # Dollar fact (accepts number or string)
  curl -X POST http://localhost:8080/fact/set -H "Content-Type: application/json" -d '{"path":"/totalWages","value":75000}'
  # {"path":"/totalWages","value":"75000.00","success":true,"isComplete":true}

  # Date fact (ISO-8601 string)
  curl -X POST http://localhost:8080/fact/set -H "Content-Type: application/json" -d '{"path":"/someDate","value":"2024-01-15"}'
  # {"path":"/someDate","value":"2024-01-15","success":true,"isComplete":true}

  # TIN fact
  curl -X POST http://localhost:8080/fact/set -H "Content-Type: application/json" -d '{"path":"/someTin","value":"123456789"}'
  # {"path":"/someTin","value":"123-45-6789","success":true,"isComplete":true}
  ```

- **`POST /fact/get`**: Get a fact value (including computed/derived facts)
  ```bash
  curl -X POST http://localhost:8080/fact/get -H "Content-Type: application/json" -d '{"path":"/totalAdjustedGrossIncome"}'
  # {"path":"/totalAdjustedGrossIncome","value":"75000.00","success":true,"isComplete":true}
  ```

#### Understanding Result States

The `/fact/get` endpoint returns an `isComplete` boolean that indicates whether the value is definitive or provisional:

**Complete (Definitive Value)**
```json
{
  "path": "/totalAdjustedGrossIncome",
  "value": "75000.00",
  "success": true,
  "isComplete": true
}
```
All dependencies are satisfied. The value is definitive and reliable.

**Placeholder (Provisional Value)**
```json
{
  "path": "/estimatedTax",
  "value": "0.00",
  "success": true,
  "isComplete": false
}
```
Some dependencies are missing, but a best-effort value is provided (e.g., from a `<Placeholder>` node in the dictionary). `isComplete: false` but `value` is present.

**Incomplete (No Value)**
```json
{
  "path": "/requiredCalculation",
  "value": null,
  "success": true,
  "isComplete": false
}
```
Cannot compute any value. Both `isComplete: false` and `value: null`.

**Client-side handling:**
```javascript
if (response.isComplete) {
  // Definitive value - use with confidence
} else if (response.value !== null) {
  // Placeholder - show but mark as provisional/estimated
} else {
  // Incomplete - no value available, more input needed
}
```

- **`GET /fact/explain?path=/factPath&includeXml=true`**: Get structured explanation
  ```bash
  curl "http://localhost:8080/fact/explain?path=/totalAdjustedGrossIncome&includeXml=true"
  # {
  #   "path": "/totalAdjustedGrossIncome",
  #   "currentValue": "75000.00",
  #   "isComplete": true,
  #   "dependencies": [
  #     {"path": "/totalWages", "currentValue": "75000.00", "isComplete": true, "rawXml": "..."},
  #     {"path": "/totalAdjustments", "currentValue": "0.00", "isComplete": true, "rawXml": "..."}
  #   ],
  #   "rawXml": "..."
  # }
  ```

### Graph State Management

- **`GET /graph`**: Export current graph state as JSON
  ```bash
  curl http://localhost:8080/graph
  # {"factPath": {"item": "factValue"}, ...}
  ```

- **`POST /graph/reset`**: Reset graph to empty state (keeps dictionary)
  ```bash
  curl -X POST http://localhost:8080/graph/reset
  # {"success": true}
  ```

- **`POST /graph/load`**: Load graph state from JSON
  ```bash
  curl -X POST http://localhost:8080/graph/load -H "Content-Type: application/json" -d '{"json": "..."}'
  # {"success": true}
  ```

- **`POST /graph/snapshot`**: Get snapshot with metadata
  ```bash
  curl -X POST http://localhost:8080/graph/snapshot
  # {"snapshot": "...", "timestamp": 1705123456789, "factCount": 150}
  ```

- **`POST /graph/diff`**: Compare two snapshots
  ```bash
  curl -X POST http://localhost:8080/graph/diff -H "Content-Type: application/json" -d '{"beforeSnapshot": "...", "afterSnapshot": "..."}'
  # {"changedPaths": ["/totalWages"], "addedPaths": ["/newFact"], "removedPaths": []}
  ```

### Collection Management

- **`POST /collection/add`**: Add item to collection
  ```bash
  curl -X POST http://localhost:8080/collection/add \
    -H "Content-Type: application/json" \
    -d '{"path":"/formW2s","uuid":"w2-2024-001"}'
  # {"path":"/formW2s/#w2-2024-001","success":true}
  ```

- **`POST /collection/remove`**: Remove item from collection
  ```bash
  curl -X POST http://localhost:8080/collection/remove \
    -H "Content-Type: application/json" \
    -d '{"path":"/formW2s","uuid":"w2-2024-001"}'
  # {"success":true}
  ```

**Example workflow:**
```bash
# Add a W-2 to the collection
curl -X POST http://localhost:8080/collection/add \
  -H "Content-Type: application/json" \
  -d '{"path":"/formW2s","uuid":"w2-2024-001"}'

# Set facts on the W-2 item
curl -X POST http://localhost:8080/fact/set \
  -H "Content-Type: application/json" \
  -d '{"path":"/formW2s/#w2-2024-001/writableWages","value":50000}'
```

### Supported Fact Types

The `/fact/set` and `/facts/set` endpoints support these fact types with automatic type coercion:

**Simple Types:**
- `StringNode`: JSON string
- `IntNode`: JSON number or numeric string
- `BooleanNode`: JSON boolean or "true"/"false" string
- `DollarNode`: JSON number or currency string (e.g. "75000", "75,000.00")
- `DayNode`: ISO-8601 date string (e.g. "2024-01-15")
- `TinNode`: 9-digit string (formatted as XXX-XX-XXXX)
- `EinNode`: 9-digit string (formatted as XX-XXXXXXX)
- `IpPinNode`: IP PIN string
- `PhoneNumberNode`: Phone number string (E.164 format)
- `EmailAddressNode`: Email string

**Enum Types:**
- `EnumNode`: JSON string (single selection from predefined options)
  ```bash
  curl -X POST http://localhost:8080/fact/set \
    -H "Content-Type: application/json" \
    -d '{"path":"/filingStatus","value":"single"}'
  # {"path":"/filingStatus","value":"single","success":true,"isComplete":true}
  ```
  The enum options path is automatically determined from the fact definition.

- `MultiEnumNode`: JSON array of strings (multiple selections from predefined options)
  ```bash
  curl -X POST http://localhost:8080/fact/set \
    -H "Content-Type: application/json" \
    -d '{"path":"/incomeTypes","value":["wages","interest"]}'
  # {"path":"/incomeTypes","value":"wages, interest","success":true,"isComplete":true}
  ```

**Complex Types:**
- `AddressNode`: JSON object with address fields
  ```bash
  curl -X POST http://localhost:8080/fact/set \
    -H "Content-Type: application/json" \
    -d '{"path":"/address","value":{"streetAddress":"123 Main St","city":"Springfield","postalCode":"62701","stateOrProvence":"IL","streetAddressLine2":"","country":"United States of America"}}'
  ```
  Required fields: `streetAddress`, `city`, `postalCode`, `stateOrProvence`
  Optional fields: `streetAddressLine2` (defaults to ""), `country` (defaults to "United States of America")

- `BankAccountNode`: JSON object with bank account fields
  ```bash
  curl -X POST http://localhost:8080/fact/set \
    -H "Content-Type: application/json" \
    -d '{"path":"/bankAccount","value":{"accountType":"Checking","routingNumber":"021000021","accountNumber":"1234567890"}}'
  ```
  Required fields: `accountType` ("Checking" or "Savings"), `routingNumber` (9 digits), `accountNumber` (5-17 alphanumeric characters)

Type validation errors return structured error messages for agent handling.

## Contributing
See [CONTRIBUTING.md](CONTRIBUTING.md) for details.

## Repository Update Frequency

This repository is updated frequently. Development occurs in a private repository and approved changes to `main` are pushed to this repository in real-time.

## Useful documentation
* [ScalaTest](https://www.scalatest.org/) - the testing framework we use
* [scala-xml](https://www.scala-lang.org/api/2.12.19/scala-xml/scala/xml/) - the standard implementation of XML (don't be put off by the sparse-seeming API docs, the function definitions have very good examples)


## Authorities
Legal foundations for this work include:
* Source Code Harmonization And Reuse in Information Technology Act" of 2024, Public Law 118 - 187
* OMB Memorandum M-16-21, “Federal Source Code Policy: Achieving Efficiency,
Transparency, and Innovation through Reusable and Open Source Software,” August 8,
2016
* Federal Acquisition Regulation (FAR) Part 27 – Patents, Data, and Copyrights
* Digital Government Strategy: “Digital Government: Building a 21st Century Platform to
Better Serve the American People,” May 23, 2012
* Federal Information Technology Acquisition Reform Act (FITARA), December 2014
(National Defense Authorization Act for Fiscal Year 2015, Title VIII, Subtitle D)
* E-Government Act of 2002, Public Law 107-347
* Clinger-Cohen Act of 1996, Public Law 104-106
