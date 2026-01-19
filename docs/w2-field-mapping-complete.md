# Complete W-2 Field Mapping: Extraction Pipeline → Fact Graph

## Overview
This document provides the complete mapping of all 38 W-2 extracted fields to their corresponding Fact Graph paths and types.

**Convention:** For each W-2, create a UUID and add to the `/formW2s` collection. All paths below use `#<uuid>` as a placeholder for the actual UUID.

---

## Core Amount Fields (Boxes 1-11)

| # | Extracted Field | Fact Graph Path | Type | Box | Notes |
|---|----------------|-----------------|------|-----|-------|
| 2 | `wage_income` | `/formW2s/#<uuid>/writableWages` | Dollar | 1 | Wages, tips, other compensation |
| 3 | `federal_tax_withheld` | `/formW2s/#<uuid>/writableFederalWithholding` | Dollar | 2 | Federal income tax withheld |
| 4 | `social_security_wages` | `/formW2s/#<uuid>/writableOasdiWages` | Dollar | 3 | Social Security wages |
| 5 | `social_security_tax_withheld` | `/formW2s/#<uuid>/writableOasdiWithholding` | Dollar | 4 | Social Security tax withheld |
| 6 | `medicare_wages_and_tips` | `/formW2s/#<uuid>/writableMedicareWages` | Dollar | 5 | Medicare wages and tips |
| 7 | `medicare_tax_withheld` | `/formW2s/#<uuid>/writableMedicareWithholding` | Dollar | 6 | Medicare tax withheld |
| 8 | `social_security_tips` | `/formW2s/#<uuid>/writableOasdiTips` | Dollar | 7 | Social Security tips |
| 9 | `allocated_tips` | `/formW2s/#<uuid>/writableAllocatedTips` | Dollar | 8 | Allocated tips |
| 10 | `dependent_care_benefits` | `/formW2s/#<uuid>/writableDependentCareBenefits` | Dollar | 10 | Dependent care benefits |
| 11 | `nonqualified_plans` | `/formW2s/#<uuid>/writableNonQualifiedPlans` | Dollar | 11 | Nonqualified plans |

---

## Box 12 - Retirement/Tax Code Amounts (a-d)

Box 12 uses **specific named facts for each code type**, not a collection structure. The most common codes are pre-defined:

### Box 12 Code Mapping
| # | Extracted Field | Fact Graph Path | Type | Notes |
|---|----------------|-----------------|------|-------|
| 12 | `box_12a_code` | **Map to specific path based on code** | - | See code→path table below |
| 13 | `box_12a_amount` | **Corresponds to code path** | Dollar | Amount for code A |
| 14 | `box_12b_code` | **Map to specific path based on code** | - | See code→path table below |
| 15 | `box_12b_amount` | **Corresponds to code path** | Dollar | Amount for code B |
| 16 | `box_12c_code` | **Map to specific path based on code** | - | See code→path table below |
| 17 | `box_12c_amount` | **Corresponds to code path** | Dollar | Amount for code C |
| 18 | `box_12d_code` | **Map to specific path based on code** | - | See code→path table below |
| 19 | `box_12d_amount` | **Corresponds to code path** | Dollar | Amount for code D |

### Common Box 12 Code → Fact Path Mapping

| Code | Fact Graph Path | Description |
|------|-----------------|-------------|
| A | `/formW2s/#<uuid>/uncollectedOasdiTaxOnTips` | Uncollected social security or RRTA tax on tips |
| B | `/formW2s/#<uuid>/uncollectedMedicareTaxOnTips` | Uncollected Medicare tax on tips |
| C | `/formW2s/#<uuid>/taxableLifeInsuranceOver50k` | Taxable cost of group-term life insurance over $50,000 |
| D | `/formW2s/#<uuid>/401kDeferrals` | Elective deferrals under section 401(k) |
| E | `/formW2s/#<uuid>/403bDeferrals` | Elective deferrals under section 403(b) |

**Note:** There are many other Box 12 codes defined in `formW2s.xml`. If you encounter codes beyond A-E, you'll need to map them individually or implement a lookup table. The XML has paths for codes like `457bDeferrals`, `501c18dDeferrals`, `hsaContributions`, `adoptionBenefits`, etc.

---

## Box 13 - Checkboxes

| # | Extracted Field | Fact Graph Path | Type | Box | Notes |
|---|----------------|-----------------|------|-----|-------|
| 20 | `statutory_employee` | `/formW2s/#<uuid>/statutoryEmployee` | Boolean | 13 | Statutory employee checkbox |
| 21 | `retirement_plan` | `/formW2s/#<uuid>/retirementPlan` | Boolean | 13 | Retirement plan checkbox |
| 22 | `third_party_sick_pay` | `/formW2s/#<uuid>/thirdPartySickPay` | Boolean | 13 | Third-party sick pay checkbox |

---

## Box 14 - Other (State-Specific Codes)

| # | Extracted Field | Fact Graph Path | Type | Notes |
|---|----------------|-----------------|------|-------|
| 23 | `box_14_other` (List) | **Map each entry to specific code path** | - | See Box 14 code mapping below |
| 31 | `code` (Box14Entry) | **Determines which path to use** | - | Code determines the fact path |
| 32 | `amount` (Box14Entry) | **Corresponds to code path** | Dollar | Amount for the code |

### Common Box 14 Code → Fact Path Mapping

Box 14 has **many state-specific codes**. Here are some common ones:

| Code | Fact Graph Path | Description |
|------|-----------------|-------------|
| 414_H | `/formW2s/#<uuid>/414_H` | NY retirement contribution |
| ADDITIONAL_MEDICARE_TAX | `/formW2s/#<uuid>/ADDITIONAL_MEDICARE_TAX` | Additional Medicare Tax |
| MEDICARE_TAX | `/formW2s/#<uuid>/MEDICARE_TAX` | Medicare Tax |
| RRTA_COMPENSATION | `/formW2s/#<uuid>/RRTA_COMPENSATION` | Railroad Retirement compensation |
| TIER_1_TAX | `/formW2s/#<uuid>/TIER_1_TAX` | Tier 1 Railroad Retirement Tax |
| TIER_2_TAX | `/formW2s/#<uuid>/TIER_2_TAX` | Tier 2 Railroad Retirement Tax |
| BOX14_MD_STPICKUP | `/formW2s/#<uuid>/BOX14_MD_STPICKUP` | Maryland state pickup |
| BOX14_NJ_FLI | `/formW2s/#<uuid>/BOX14_NJ_FLI` | NJ Family Leave Insurance |
| ERS | `/formW2s/#<uuid>/ERS` | NY Employees' Retirement System |
| NYRET | `/formW2s/#<uuid>/NYRET` | NY retirement |

**Note:** Box 14 has ~30+ defined codes in the XML, mostly for NY, NJ, and MD. The structure is NOT a collection—each code has its own named writable Dollar fact.

---

## Employer Information

| # | Extracted Field | Fact Graph Path | Type | Box | Notes |
|---|----------------|-----------------|------|-----|-------|
| 25 | `employer_fed_id_number` | `/formW2s/#<uuid>/ein` | EIN | b | Format: XX-XXXXXXX |
| 26 | `employer_name` | `/formW2s/#<uuid>/employerName` | String | c | Max 75 chars, regex validated |
| 27-30 | `employer_address`, `employer_city`, `employer_state`, `employer_zip_code` | `/formW2s/#<uuid>/employerAddress` | Address | c | Composite Address type |

**Address Type Structure:** The Address writable type is a composite. You'll need to assemble your 4 separate fields into a single Address object when setting the fact.

---

## State and Local Information (Boxes 15-20)

**Note:** Unlike Box 12/14, state/local appears to be single-value per W-2 (not a repeating collection in the dictionary structure we found).

| # | Extracted Field | Fact Graph Path | Type | Box | Notes |
|---|----------------|-----------------|------|-----|-------|
| 33 | `state` | `/formW2s/#<uuid>/writableState` | Enum | 15 | State code (uses `/incomeFormStateOptions`) |
| 34 | `employer_state_id_number` | `/formW2s/#<uuid>/writableStateEmployerId` | String | 15 | Employer's state ID number |
| 35 | `state_income_tax` | `/formW2s/#<uuid>/writableStateWithholding` | Dollar | 17 | State income tax withheld |
| 36 | `local_wages_and_tips` | `/formW2s/#<uuid>/writableLocalWages` | Dollar | 18 | Local wages, tips, etc. |
| 37 | `local_income_tax` | `/formW2s/#<uuid>/writableLocalWithholding` | Dollar | 19 | Local income tax withheld |
| 38 | `locality_name` | `/formW2s/#<uuid>/writableLocality` | String | 20 | Locality name |

**Important:** Box 16 (state wages) is mapped via `/formW2s/#<uuid>/writableStateWages` (not in your extraction list but may be useful).

**Multi-State Handling:** If your extraction produces multiple state/local entries per W-2, you may need to create multiple W-2 instances or extend the mapping strategy. The current dictionary appears to support one state entry per W-2.

---

## Filing Type / Form Metadata

| # | Extracted Field | Fact Graph Path | Type | Notes |
|---|----------------|-----------------|------|-------|
| 1 | `filing_type` | `/formW2s/#<uuid>/nonstandardOrCorrectedChoice` | Enum | Uses `/w2NonstandardCorrectedOptions` |

**Filing Type Enum Values:**
- `neither` - Standard W-2
- `corrected` - Corrected W-2
- `nonstandard` - Nonstandard W-2
- `both` - Both corrected and nonstandard

---

## Derived Facts to Evaluate (Intermediate Values)

After setting writable facts, evaluate these derived facts for cohesiveness checks:

### Per-Box Canonical Derived Facts
| Writable Path | Derived Path | Purpose |
|---------------|--------------|---------|
| `/formW2s/#<uuid>/writableWages` | `/formW2s/#<uuid>/wages` | Rounded/normalized wages |
| `/formW2s/#<uuid>/writableFederalWithholding` | `/formW2s/#<uuid>/federalWithholding` | Rounded federal withholding |
| `/formW2s/#<uuid>/writableOasdiWages` | `/formW2s/#<uuid>/oasdiWages` | Rounded SS wages |
| `/formW2s/#<uuid>/writableOasdiWithholding` | `/formW2s/#<uuid>/oasdiWithholding` | Rounded SS withholding |
| `/formW2s/#<uuid>/writableMedicareWages` | `/formW2s/#<uuid>/medicareWages` | Rounded Medicare wages |
| `/formW2s/#<uuid>/writableMedicareWithholding` | `/formW2s/#<uuid>/medicareWithholding` | Rounded Medicare withholding |

### Cross-W-2 Rollups (from `formW2s.xml`)
- `/primaryFilerWages` - Sum of all primary filer W-2 wages
- `/secondaryFilerWages` - Sum of all secondary filer W-2 wages (if MFJ)
- `/allCombatPay` - Total combat pay across all W-2s

### Higher-Level Rollups (from `income.xml`)
- `/employerIncomeSubtotal` - Total employer income (Form 1040, Line 1z)
- `/totalIncome` - Total income (Form 1040, Line 9)

### Rule/Flag Facts (Knockout Conditions)
Check these boolean facts—if `true`, indicates an error/inconsistency:
- `/formW2s/anyFilerExceedsMaxOasdiWages` - SS wages exceed annual cap
- `/formW2s/knockoutMedicareWages` - Medicare wages are invalid
- `/formW2s/flowKnockoutAllocatedTips` - Allocated tips knockout
- `/formW2s/flowKnockoutNonQualifiedPlans` - Non-qualified plans knockout
- `/formW2s/knockoutForBox12Value` - Box 12 value knockout
- `/formW2s/flowKnockoutThirdPartySickPay` - Third-party sick pay knockout
- `/formW2s/flowKnockoutStatutoryEmployee` - Statutory employee knockout

---

## Data Type Normalization Requirements

### Dollar Type
- **Input:** String like `"$50,000.00"` or float `50000.0`
- **Output:** Integer representing cents: `5000000`
- **Edge Cases:** Handle "$", commas, negative values, missing decimals

### EIN Type
- **Input:** String like `"123456789"` or `"12-3456789"`
- **Output:** String in format `"XX-XXXXXXX"` (e.g., `"12-3456789"`)
- **Validation:** Must be exactly 9 digits

### Boolean Type
- **Input:** Various (checkbox checked, "yes"/"no", true/false)
- **Output:** Scala/JSON boolean `true` or `false`

### Address Type
- **Input:** 4 separate fields (address, city, state, zip)
- **Output:** Address composite object
- **Structure:** TBD—need to investigate Address writable type schema in Fact Graph

### Enum Type
- **State:** Must match options in `/incomeFormStateOptions`
- **Filing Type:** Must match options in `/w2NonstandardCorrectedOptions`: `neither`, `corrected`, `nonstandard`, `both`

### String Type
- **Employer Name:** Max 75 chars, regex: `(([A-Za-z0-9#\-\(\)]|&|')\s?)*([A-Za-z0-9#\-\(\)]|&|')`
- **Locality:** Max length validation via MeF type

---

## Implementation Strategy

### Phase 1: Core Fields (Minimal PoC)
Map and test these first:
- Boxes 1-6 (wages, withholding, SS/Medicare wages/taxes)
- EIN
- Employer name
- Filing type

### Phase 2: Extended Fields
- Boxes 7-11 (tips, dependent care, non-qualified plans)
- Box 13 checkboxes
- State/local information

### Phase 3: Complex Codes
- Box 12 codes (needs code→path lookup table)
- Box 14 codes (state-specific, needs code→path lookup table)

### Phase 4: Multi-Entry Support
- If extraction produces multiple state/local entries per W-2, determine if you need:
  - Multiple W-2 instances, or
  - Extension to dictionary structure

---

## Open Questions for Fact Graph Team

1. **Address Type Schema:** What is the exact structure/API for setting an Address writable type?
2. **Box 12/14 Code Coverage:** Do we need to support all ~30+ Box 14 codes, or can we start with a subset?
3. **Multi-State W-2s:** If a W-2 has income from multiple states, should we create multiple W-2 collection items or is there collection support within state/local facts?
4. **Box 9 (allocated tips) Knockout:** What conditions trigger `/formW2s/flowKnockoutAllocatedTips`?
5. **Performance:** Expected latency for setting ~20 writable facts + evaluating ~15 derived facts per W-2?

---

## Summary

- **Total Writable Facts per W-2:** ~20-30 (depending on Box 12/14 codes present)
- **Total Derived Facts to Check:** ~15-20
- **Collection Structure:** Each W-2 = one item in `/formW2s` collection, identified by UUID
- **Complex Mappings:** Box 12 and Box 14 require code→path lookup tables
- **State/Local:** Appears to be single-entry per W-2 in current dictionary structure

---

**Document Version:** 1.0  
**Date:** 2026-01-18  
**Source:** `fact_dictionaries/formW2s.xml`, `fact_dictionaries/income.xml`
