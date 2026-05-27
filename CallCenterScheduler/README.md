# Call Center Scheduler

A production-quality Java implementation of a parallel DAG-based task scheduler for call-center assignment planning.

---

## Significant Resources

| Resource | Role in Solution |
|----------|-----------------|
| **Kahn's Algorithm** (1962) — _Topological Sorting of Large Networks_ | Cycle detection: BFS-based indegree count confirms the graph is a DAG |
| **Critical Path Method (CPM)** — classic project management algorithm | DFS + memoisation to compute the longest dependency chain from every node |
| **HEFT (Heterogeneous Earliest Finish Time)** — Topcuoglu et al. 2002 | Inspiration for critical-path-aware worker assignment heuristic |
| **Java SE 8 `java.util.Base64`** | Decoding Base64-encoded input strings for safe email transport |
| **Java `PriorityQueue`** | Both the ready-queue (max-heap) and running-queue (min-heap) implementations |
| **Claude (Anthropic AI)** | Architecture review, refactoring guidance, algorithm cross-validation |

---

## Problem Statement

```
schedule(G, C, N, assignments) → String
```

Given a set of call-center assignments — each with a **category**, **group**, **duration**,
and optional **prerequisites** — compute:

1. The **minimum completion time** when using `N` concurrent workers.
2. The **alphabetically sorted top-G group names** that fall within the top-C categories
   (both ranked by total cumulative duration).

`N = 0` means unlimited workers. `G = 0` returns completion time only.

### Input format
```
"category,group,time[,prereqCategory,prereqGroup]*"   ← one assignment
"assignment1;assignment2;..."                          ← full input string
```

Backslash escapes: `\,` for literal comma, `\;` for literal semicolon.
Base64-encoded input is also accepted (auto-detected and decoded).

---

## Project Structure

```
CallCenterScheduler/
├── src/
│   ├── Assignment.java          # Immutable node value object
│   ├── RunningTask.java         # Worker snapshot (assignment + finishTime)
│   ├── Parser.java              # Raw string → structured records
│   ├── Scheduler.java           # DAG build + cycle check + critical path + simulation
│   ├── Analytics.java           # Top-G/C group ranking
│   ├── ScheduleCallCenter.java  # Public API — orchestration + error handling
│   └── Main.java                # Test harness (6 datasets + error cases)
└── README.md
```

### Responsibility Map

```
ScheduleCallCenter          ← entry point; wires all layers; returns "ERROR ..." on failure
    │
    ├── Parser              ← stateless; splits escaped strings; validates field structure
    │
    ├── Scheduler           ← core engine; 4-pass pipeline
    │     ├── Pass 1: register nodes   (assignmentMap + indegree)
    │     ├── Pass 2: add edges        (graph adjacency list)
    │     ├── Pass 3: validateAcyclic  (Kahn's BFS)
    │     ├── Pass 4a: computeCriticalPath (DFS + memo)
    │     └── Pass 4b: simulate        (event-driven N-worker execution)
    │
    └── Analytics           ← stateless; filters + ranks groups by category scope
```

---

## Design Guidance

### Single Responsibility
Each class owns exactly one concern. `Scheduler` knows nothing about output formatting;
`Analytics` knows nothing about execution order; `Parser` knows nothing about graph theory.

### Immutability
`Assignment` and `RunningTask` are immutable value objects (`public final` fields, no setters).
This eliminates accidental mutation bugs across the scheduling pipeline.

### Defensive Copies
`simulate()` works on `liveIndegree` — a copy of the original `indegree` map.
The original is preserved for potential re-use; the simulation never corrupts it.

### Determinism
All comparators include a tie-breaking clause (lexicographic by `category` then `group`)
so output is reproducible regardless of `HashMap` iteration order.

### Error Strategy
All exceptions are caught at the `ScheduleCallCenter` boundary and converted to
`"ERROR <reason>"` strings. Internal methods throw `IllegalArgumentException` with
precise messages — never silent failures or generic stack traces.

---

## Key Data Structures & Algorithms

### 1 — DAG with Indegree Map

The assignment graph is represented as an **adjacency list** (`Map<String, List<String>>`)
where each key is a `"category | group"` composite string and values are its dependents.

```
Input:
  Life/WA Other depends on Medicare/WA King

Graph edge:
  "Medicare | WA King" → ["Life | WA Other"]

Indegree:
  "Life | WA Other" = 1    ← has one unsatisfied prerequisite
  "Medicare | WA King" = 0 ← immediately runnable
```

**Why adjacency list?**
- O(V+E) space — efficient for sparse dependency graphs
- O(1) neighbour lookup per node during BFS and DFS

---

### 2 — Cycle Detection: Kahn's Algorithm

```java
// Seed BFS with all zero-indegree nodes
Queue<String> bfsQueue = new ArrayDeque<>();
for (entry : indegreeCopy) { if (value == 0) bfsQueue.offer(key); }

// Process — decrement neighbours' indegree
int processedCount = 0;
while (!bfsQueue.isEmpty()) {
    String current = bfsQueue.poll();
    processedCount++;
    for (String dependent : graph.get(current)) {
        if (--indegreeCopy[dependent] == 0) bfsQueue.offer(dependent);
    }
}

// If not all nodes processed → cycle exists
if (processedCount != totalNodes) throw new IllegalArgumentException("Cyclic dependency");
```

**Complexity:** O(V + E)

---

### 3 — Critical Path: DFS + Memoisation

```
criticalPath(node) = node.duration + max( criticalPath(child) for each child )
criticalPath(leaf) = leaf.duration
```

```
Example (SAMPLE dataset):
  Medicare/WA King  (43061) → Life/WA Other (70944)
  criticalPath("Medicare | WA King") = 43061 + 70944 = 114005   ← longest chain

  Medicare/OR Lake  (1304)  → Life/OR Other (12806)
  criticalPath("Medicare | OR Lake")  = 1304  + 12806 = 14110
```

Memoisation ensures each node is computed **exactly once** — O(V+E) total.

---

### 4 — Event-Driven Scheduling: Two-Queue Simulation

```
readyQueue   — PriorityQueue (MAX-heap by criticalPath score)
               Ensures the most critical task is assigned to a free worker first.

runningQueue — PriorityQueue (MIN-heap by finishTime)
               The clock advances to the next task completion event.
```

```
Simulation loop:

  WHILE readyQueue or runningQueue is non-empty:

    1. ASSIGN: fill free worker slots from readyQueue → runningQueue

    2. ADVANCE CLOCK: poll earliest-finishing task from runningQueue
                      currentTime = task.finishTime

    3. BATCH DRAIN: collect ALL tasks finishing at exactly currentTime
                    (simultaneous completions processed atomically)

    4. UNBLOCK: for each completed task, decrement dependents' liveIndegree
                if liveIndegree reaches 0 → offer to readyQueue
```

**Why max-heap by critical path (not FIFO or shortest-job-first)?**

| Policy | Schedules first | Effect |
|--------|----------------|--------|
| FIFO | insertion order | arbitrary delays on critical chain |
| Shortest job first | smallest duration | optimises average latency, not makespan |
| **Critical path first** | **longest downstream chain** | **minimises total completion time** |

```
SAMPLE dataset with 2 workers at t=0:

  readyQueue: [WA King(114005), OR Lake(14110), Jefferson(5444)]
                      ↑ highest critical path — assigned first

  Workers assigned:  WA King(43061)  +  OR Lake(1304)
  Result: makespan = 43061 + 70944 = 114005  ✅  (optimal)
```

**Complexity:** O((V+E) log V) — each node enters/exits the heap once.

---

### 5 — Analytics: Group Ranking

```java
// Step 1: rank categories DESC by total duration
List<String> rankedCategories = rankByTotalDescending(categoryTotals);
List<String> topCategories    = rankedCategories.subList(0, C);

// Step 2: collect groups belonging to top-C categories
Set<String> eligibleGroupNames = new LinkedHashSet<>();
for (String cat : topCategories) eligibleGroupNames.addAll(categoryToGroups.get(cat));

// Step 3: filter groupTotals to eligible groups only
// Step 4: rank filtered groups DESC, take top G
// Step 5: sort alphabetically (spec requirement)
Collections.sort(result);
```

**Why filter groups by category scope first?**
A group like "WA King" may have high duration but belong to a lower-ranked category.
The spec requires groups to be ranked *within* the top-C category scope, not globally.

---

### 6 — Base64 Auto-Detection

The spec provides input in both literal and Base64 forms (Base64 = email transport only,
not encryption). Detection heuristic:

```
input contains ',' or ';'  →  literal  (pass through)
input has no delimiters     →  attempt Base64.decode()
decode fails                →  return as-is (Parser will report the error)
```

---

## Test Coverage

### Dataset 1 — Spec Sample (two independent chains)

```
Home/OR Jefferson (5444)
Medicare/OR Lake  (1304) ──► Life/OR Other  (12806)
Medicare/WA King  (43061)──► Life/WA Other  (70944)
```

| Call | Expected | Result |
|------|----------|--------|
| `schedule(2,1,0,SAMPLE)` | `114005,OR Other,WA Other` | ✅ |
| `schedule(2,2,1,SAMPLE)` | `133559,WA King,WA Other` | ✅ |
| `schedule(3,3,2,SAMPLE)` | `114005,OR Other,WA King,WA Other` | ✅ |
| `schedule(0,0,0,SAMPLE)` | `114005` | ✅ |

### Dataset 2 — Diamond (node with two parents)

```
Intake/WA North (1000) ─┐
                         ├──► Review/WA Central (5000) ──► Close/WA South (2000)
Intake/OR West  (3000) ─┘
```

| Workers | Expected | Reasoning |
|---------|----------|-----------|
| N=2 | 10000 | Both Intake run in parallel; Review starts at t=3000 |
| N=1 | 11000 | Serial: 1000+3000+5000+2000 |

### Dataset 3 — Wide Chain (serial pipeline)

```
Prep(500) → Process(1500) → Validate(800) → Approve(1200) → Ship(300)
```

Extra workers provide zero benefit — always 4300 regardless of N.

### Dataset 4 — Parallel Only (no dependencies)

```
G1(200)  G2(500)  G3(300)  G4(800)   — all independent
```

| N | Expected | Notes |
|---|----------|-------|
| 4 | 800 | All run at t=0 |
| 2 | 1000 | G4+G2 → G3 → G1 |
| 1 | 1800 | Sequential: 800+500+300+200 |

### Dataset 5 — Mixed (fan-out + fan-in + independent node)

```
Setup/Root (100) ──► Work/Alpha (400) ─┐
                └──► Work/Beta  (600) ─┴──► Final/Done (200)
Audit/Side  (900)  [independent]
```

| N | Expected | Critical path |
|---|----------|---------------|
| 3 | 900 | max(Audit=900, Root→Beta→Final=900) |
| 1 | 2200 | Audit first, then sequential chain |

### Dataset 6 — Base64-encoded input

```
SG9tZSxPUiBKZWZm...Cg== → decoded → identical to Dataset 1
```
Results match Dataset 1 exactly. ✅

### Error Cases

| Input | Expected |
|-------|----------|
| `N = -1` | `ERROR N must be non-negative` |
| Missing prerequisite | `ERROR Prerequisite not found: Ghost | Node` |
| Cycle A→B→A | `ERROR Cyclic dependency detected in assignments` |
| Empty string | `ERROR assignments string is empty` |
| Non-numeric duration | `ERROR Invalid duration 'notANumber'` |

---

## How to Run

```bash
# Compile
cd CallCenterScheduler/src
javac *.java

# Run test harness
java Main

# Clean compiled files
rm *.class
```

**Java requirement:** Java 8+ (uses `java.util.Base64`, `java.util.PriorityQueue`, lambdas).

---

## TODO / Future Optimization Considerations

### Correctness

- [ ] **G > C case**: if `G > number of groups in top-C categories`, currently returns
      fewer than G results silently. Consider padding with a warning or error.
- [ ] **Duplicate group names across categories**: a group name appearing in two categories
      currently accumulates duration across both. Clarify spec intent and add test.
- [ ] **Zero-duration tasks**: allowed by spec (`duration >= 0`), but untested at edge
      cases (e.g. a chain of 1000 zero-duration tasks with N=1).

### Performance

- [ ] **Large N with many ready tasks**: the inner `while (!readyQueue.isEmpty() && size < N)`
      loop is O(k log V) where k = tasks assigned per tick. For N=∞ with 10,000 tasks,
      consider bulk-draining with a single `drainTo()` pass.
- [ ] **Stack overflow on deep DFS**: `computeCriticalPath` is recursive. For graphs with
      chains longer than ~5,000 nodes, convert to iterative DFS with an explicit stack.
- [ ] **Memory**: `assignmentMap` retains all nodes after scheduling. For streaming use-cases,
      nodes could be released after all their dependents are unblocked.

### Features

- [ ] **Return per-task start/finish times** alongside the makespan for Gantt-chart output.
- [ ] **Support weighted workers** (some workers are faster): currently all workers are
      identical; extend `RunningTask` with a `workerId` and per-worker speed multiplier.
- [ ] **Cancellation / failure handling**: if an assignment fails mid-execution, dependents
      should be skipped or re-queued. Requires a task state machine (`PENDING → RUNNING → DONE/FAILED`).
- [ ] **Dynamic task injection**: add new assignments after scheduling has started (requires
      thread-safe queues if moved to real parallel execution).
- [ ] **REST API wrapper**: expose `schedule()` as a `POST /schedule` endpoint using a
      lightweight framework (Spark Java, Spring Boot) for service integration.
- [ ] **Real parallel execution**: replace `PriorityQueue` with `PriorityBlockingQueue`,
      replace `HashMap` with `ConcurrentHashMap`, and drive workers with `ExecutorService`
      for true multi-threaded execution instead of simulation.

### Observability

- [ ] **Structured logging**: replace `System.out` in `Main` with SLF4J + Logback for
      log-level control in production.
- [ ] **Metrics**: expose scheduling latency, queue depth, and worker utilisation via
      Micrometer/Prometheus for operational visibility.
