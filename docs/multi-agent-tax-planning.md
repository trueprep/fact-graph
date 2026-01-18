# Multi-Agent Tax & Wealth Planning System

**Status:** Proposal
**Date:** 2026-01-18
**Purpose:** Design document for a multi-agent system using pydantic-ai and LangGraph to provide tax and wealth planning advice backed by the fact-graph

---

## Background

The fact-graph is a sophisticated tax calculation engine with:
- XML-based fact definitions with strong typing (`Dollar`, `Day`, `Boolean`, `Enum`, etc.)
- Computed nodes with expression evaluation and dependency management
- Validation rules (soft/hard errors)
- Collection types (arrays of facts)
- Multiple fact dictionaries covering tax domains (EITC, CDCC, retirement, etc.)

### Goal
Create a conversational AI system that can:
1. Answer tax and wealth planning questions
2. Use the fact-graph as its computational backend
3. Intelligently gather missing information from users
4. Provide accurate, validated tax advice

---

## Proposed Architecture

### High-Level Flow

```
                         ┌──────────────────┐
                         │      USER        │
                         └────────┬─────────┘
                                  │ query
                         ┌────────▼─────────┐
                         │     PLANNER      │ ← Creates plan, delegates to sub-agents
                         └────────┬─────────┘
                                  │
              ┌───────────────────┼───────────────────┐
              ▼                   ▼                   ▼
     ┌────────────────┐  ┌────────────────┐  ┌────────────────┐
     │  Tax Calc      │  │  Credits &     │  │  Retirement    │
     │  Sub-Agent     │  │  Deductions    │  │  Sub-Agent     │
     │                │  │  Sub-Agent     │  │                │
     └───────┬────────┘  └───────┬────────┘  └───────┬────────┘
             │                   │                   │
             └───────────────────┼───────────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │       FACT-GRAPH        │ ← Shared state (per conversation)
                    │  (read/write/compute)   │
                    └────────────┬────────────┘
                                 │
                    ┌────────────▼────────────┐
              ┌────►│       EVALUATOR         │◄────────────────┐
              │     │  - Is plan sound?       │                 │
              │     │  - Do we have enough    │                 │
              │     │    data to execute?     │                 │
              │     │  - Spin counter++       │                 │
              │     └────────────┬────────────┘                 │
              │                  │                              │
              │     ┌────────────┴────────────┐                 │
              │     ▼                         ▼                 │
              │  [ready]              [needs info]              │
              │     │                         │                 │
              │     ▼                         ▼                 │
              │  ┌──────────┐          ┌─────────────┐          │
              │  │ EXECUTE  │          │  ASK USER   │──────────┘
              │  │ & ANSWER │          │ (+ escalate │   (cycle back
              │  └────┬─────┘          │  if spinning│    to planner
              │       │                │  too long)  │    with new info)
              │       ▼                └─────────────┘
              │   [done]
              │
              └─── (if evaluator says "plan needs revision")
```

### Component Definitions

#### 1. **Planner Node** (LangGraph State Node)
- **Input:** User query + conversation history + fact-graph state
- **Responsibility:**
  - Parse user intent
  - Create execution plan
  - Delegate to appropriate sub-agents
  - Synthesize sub-agent results into coherent answer
- **Does NOT:** Directly interact with fact-graph
- **Output:** Structured plan + sub-agent results

#### 2. **Sub-Agents** (Pydantic-AI Agents)

There are two categories of sub-agents:

##### A. **Fact-Graph Interaction Sub-Agents (1-3 specialized agents)**
- **Purpose:** Dedicated agents that understand and manage fact-graph operations
- **Examples:**
  - Fact Schema Navigator - understands fact structure, types, and relationships
  - Fact Data Manager - handles reading/writing/computing fact values
  - Fact Validator - checks completeness and validates fact states
- **Responsibilities:**
  - Maintain deep understanding of fact-graph schema and semantics
  - Translate domain questions into fact-graph queries
  - Handle all direct fact-graph tool interactions
  - Provide structured fact data to domain sub-agents
  - Manage fact-graph state consistency
- **Key Insight:** These agents act as an abstraction layer, so domain agents don't need to understand fact-graph internals
- **Tools Available:** See "Fact-Graph Tools" section below

##### B. **Domain Sub-Agents**
- **Purpose:** Domain-specific experts that handle conceptual task blocks
- **Examples:**
  - Tax Calculation Sub-Agent (basic tax math)
  - Credits & Deductions Sub-Agent (EITC, CDCC, etc.)
  - Retirement Sub-Agent (401k, IRA, RMDs)
  - Income Sub-Agent (W2, 1099, etc.)
- **Responsibilities:**
  - Own a specific tax/financial domain
  - Request fact data from Fact-Graph Interaction Sub-Agents
  - Apply domain-specific reasoning and calculations
  - Identify what information is needed (not where it's stored)
  - Return structured results to planner
- **Does NOT:** Directly interact with fact-graph (delegates to Fact-Graph Interaction Sub-Agents)

#### 3. **Evaluator Node** (LangGraph State Node)
- **Input:** Current plan + fact-graph state + execution history
- **Responsibilities:**
  - Validate plan soundness (logic correct?)
  - Check data sufficiency (do we have enough facts populated?)
  - Track cycle count (are we spinning?)
  - Decide: ready to execute, need more info, or revise plan
- **State Tracking:**
  ```python
  class EvaluatorState:
      spin_count: int           # How many cycles through the loop
      missing_facts: list[str]  # Facts we still need from user
      plan_issues: list[str]    # Problems with current plan
      escalation_level: int     # 0=normal, 1=warn, 2=force-gather
  ```
- **Escalation Logic:** When spin_count exceeds threshold (e.g., 3), shift prompt to: "We've been cycling. Gather ALL remaining required facts in one comprehensive ask."

#### 4. **Execute Node** (LangGraph State Node)
- **Input:** Validated plan + complete fact-graph
- **Responsibility:**
  - Trigger final computations
  - Run validations
  - Format answer for user
- **Output:** Final tax/wealth planning advice

#### 5. **Ask User Node** (LangGraph State Node)
- **Input:** List of missing facts from evaluator
- **Responsibility:**
  - Format user-friendly questions
  - Present to user
  - Collect responses
  - Update fact-graph
- **Output:** Updated conversation state → cycle back to planner

---

## Technology Stack

### Pydantic-AI
- **Purpose:** Type-safe agent definition and tool creation
- **Usage:**
  - Define sub-agents with structured inputs/outputs
  - Create type-safe tools for fact-graph interaction
  - Ensure LLM calls have validated schemas

### LangGraph
- **Purpose:** Orchestrate state machine flow between conceptual blocks
- **Usage:**
  - StateGraph with nodes for: Planner, Evaluator, Execute, Ask User
  - Conditional edges based on evaluator decisions
  - Maintain conversation-level fact-graph state

### Fact-Graph Integration
- **Lifecycle:** One fact-graph instance per conversation
- **Access Pattern:** Sub-agents interact via tools; planner/evaluator read state

---

## Fact-Graph Tools

**Access Pattern:** Only Fact-Graph Interaction Sub-Agents (1-3 specialized agents) have direct access to these tools. Domain sub-agents request fact data through these intermediary agents.

The Fact-Graph Interaction Sub-Agents will have access to these tools (implemented as pydantic-ai tools):

| Tool | Signature | Purpose |
|------|-----------|---------|
| `get_fact_schema` | `(path: str) -> FactSchema` | Get type/description/constraints of a fact |
| `read_fact` | `(path: str) -> Optional[FactValue]` | Get current value (including computed) |
| `write_fact` | `(path: str, value: Any) -> Result` | Set a writable fact |
| `list_missing_facts` | `(paths: list[str]) -> list[str]` | Which of these are unfilled? |
| `get_validations` | `(paths: list[str]) -> list[Validation]` | Any errors/warnings on these facts? |
| `compute_fact` | `(path: str) -> FactValue` | Trigger computation and return result |
| `search_facts` | `(query: str) -> list[FactPath]` | Find facts by description/concept |

**Why the abstraction layer?**
- Domain agents focus on tax logic, not fact-graph mechanics
- Fact-graph complexity is encapsulated in specialized agents
- Easier to update fact-graph interactions without touching domain logic
- Fact-Graph Interaction agents can optimize queries and cache results

---

## Design Decisions

### 1. **Node vs Sub-Agent Split**
- **Nodes** = Conceptual flow control (state machine blocks): Planner, Evaluator, Execute, Ask User
- **Sub-Agents** = Domain experts with tools (invoked by planner): Tax Calc, Credits, Retirement, etc.
- **Rationale:** Separates orchestration logic from domain knowledge

### 2. **Fact-Graph Interaction Layer (1-3 Specialized Sub-Agents)**
- **Why:** Fact-graph has complex semantics (types, computed values, dependencies, validations)
- **Approach:** Create 1-3 sub-agents that deeply understand fact-graph operations
- **Benefit:** Domain agents focus on tax logic; fact-graph agents handle data mechanics
- **Implementation Options:**
  - **Single Agent:** One "Fact-Graph Manager" that handles all operations
  - **Two Agents:** Split into "Reader/Querier" and "Writer/Validator"
  - **Three Agents:** "Schema Navigator", "Data Manager", "Validator" (most granular)
- **Decision Point:** Start with one agent; split if it becomes overloaded

### 3. **Planner Does Not Touch Fact-Graph Directly**
- **Why:** Separation of concerns. Planner orchestrates; sub-agents execute.
- **Benefit:** Easier to test, reason about, and extend

### 4. **Evaluator Tracks Spin Count**
- **Why:** Prevent infinite loops from missing info or bad plans
- **Mechanism:** Escalate prompt intensity as cycles increase
- **Future:** Could add hard cap with graceful failure

### 5. **One Fact-Graph Per Conversation**
- **Why:** Simplifies state management for MVP
- **Limitation:** No persistence across sessions (future enhancement)
- **Implication:** Long conversations accumulate state; may need reset mechanism

### 6. **Sub-Agent Granularity: Conceptual Grouping**
- **Approach:** Group related tax concepts (not one agent per XML file)
- **Example:** "Credits & Deductions" agent handles EITC, CDCC, Saver's Credit
- **Rationale:** Better for reasoning; avoids over-fragmentation

---

## Assumptions

1. **Fact-Graph API Exists or Will Be Created**
   - The tools listed above assume programmatic access to fact-graph
   - May need Python bindings or REST API to the Scala implementation

2. **Fact Dictionaries Provide Sufficient Coverage**
   - Existing dictionaries (EITC, CDCC, etc.) cover most tax scenarios
   - Additional dictionaries can be added as needed

3. **Conversation-Scoped State is Sufficient**
   - Users start fresh each conversation
   - No need for cross-session persistence (for now)

4. **LLM Can Handle Tax Complexity**
   - Assumes modern LLMs (GPT-4, Claude) can reason about tax logic
   - Sub-agents provide structure to manage complexity

5. **No Branching/Multi-Turn Complexity Initially**
   - Linear conversation flow (ask question → get answer → ask follow-up)
   - No support for mid-conversation pivots or "what-if" branching (yet)

6. **Infinite Loop Protection via Escalation (Not Hard Cap)**
   - Design assumes escalating prompts will prevent spinning
   - Could add hard cap (e.g., 10 cycles) in future

7. **Sub-Agents Don't Directly Communicate**
   - All coordination goes through planner
   - No peer-to-peer sub-agent calls

---

## Open Questions & Future Exploration

### Architecture

1. **Sub-Agent Granularity**
   - How many domain sub-agents? One per fact dictionary vs. conceptual grouping?
   - For Fact-Graph Interaction agents: Start with one or immediately split into 2-3?
   - Should there be a "router" sub-agent that dispatches to specialized agents?

2. **Evaluator Sophistication**
   - Should evaluator have access to historical conversation context?
   - Should it use semantic similarity to detect "user is frustrated" signals?

3. **Execute Node Complexity**
   - Is execution a single step, or does it need sub-stages (compute → validate → format)?
   - Should there be a separate "Validation" node?

### Fact-Graph Integration

4. **Python Bindings**
   - Does fact-graph need a Python API, or can we use REST/gRPC?
   - Performance implications of cross-language calls?

5. **Fact-Graph Initialization**
   - How is a new fact-graph instance created?
   - Are there templates or default states?

6. **Computed Fact Caching**
   - How do we avoid recomputing expensive facts repeatedly?
   - Should sub-agents cache results across calls?

7. **Validation Handling**
   - When validations fail, who decides how to handle: evaluator or sub-agent?
   - Should validation errors trigger automatic re-planning?

### User Experience

8. **Question Batching**
   - Should "Ask User" node always batch questions, or ask incrementally?
   - Trade-off: user overwhelm vs. efficiency

9. **Explanation Depth**
   - How much detail should answers include about fact-graph derivations?
   - Should users be able to request "show your work"?

10. **Error Recovery**
    - What happens if user provides contradictory information?
    - Should evaluator detect and flag inconsistencies?

### Implementation Details

11. **State Persistence Format**
    - How is fact-graph state serialized for LangGraph state?
    - JSON? Pickle? Custom format?

12. **Prompt Engineering**
    - How are sub-agent prompts structured to ensure they use tools correctly?
    - Should prompts include examples of good/bad tool usage?

13. **Testing Strategy**
    - How do we test the full graph without actual LLM calls?
    - Mock sub-agents? Deterministic LLM responses?

14. **Cost Management**
    - With multiple agents and cycles, token costs could be high
    - Should there be budget limits or cheaper models for some nodes?

### Scaling & Production

15. **Multi-User Handling**
    - If deployed, how are conversation states isolated?
    - Database-backed state store?

16. **Fact-Graph Performance at Scale**
    - Can one instance handle many facts efficiently?
    - Memory/CPU constraints?

17. **Monitoring & Observability**
    - How do we debug failures in the graph?
    - LangSmith integration? Custom tracing?

18. **Security & Privacy**
    - Tax data is sensitive—how is it protected?
    - Should fact-graph state be encrypted?

---

## Next Steps

To move from proposal to implementation:

1. **Prototype Fact-Graph Python API**
   - Create minimal Python bindings or REST wrapper
   - Implement core tools: read_fact, write_fact, compute_fact

2. **Build Simplest Sub-Agent**
   - Single domain (e.g., "Tax Calculation")
   - Pydantic-AI agent with 2-3 tools
   - Test in isolation

3. **Implement Minimal LangGraph Flow**
   - Two nodes: Planner → Execute (skip evaluator/ask user initially)
   - Hardcode fact-graph state
   - Validate end-to-end flow

4. **Add Evaluator & Ask User Nodes**
   - Introduce cycle detection
   - Test with incomplete data scenarios

5. **Expand Sub-Agents**
   - Add Credits, Retirement, etc.
   - Test planner delegation logic

6. **Iterate on Prompts & Tools**
   - Refine based on real usage
   - Add missing tools as gaps are discovered

---

## References

- **Fact-Graph Documentation:**
  - `docs/architecture-overview.md`
  - `docs/core-concepts.md`
  - `docs/expression-evaluation.md`
  - `docs/validation-system.md`
  - `docs/developer-guide.md`

- **Fact Dictionaries:** `fact_dictionaries/*.xml`

- **Technology Docs:**
  - [Pydantic-AI](https://ai.pydantic.dev/)
  - [LangGraph](https://langchain-ai.github.io/langgraph/)

---

## Document History

| Date | Author | Changes |
|------|--------|---------|
| 2026-01-18 | Initial | Created proposal based on design discussion |
