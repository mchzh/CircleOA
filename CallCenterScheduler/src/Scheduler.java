import java.util.*;

/**
 * DAG-based parallel task scheduler.
 *
 * <p>Algorithm (four passes over the input):
 * <ol>
 *   <li><b>Build</b> — adjacency list (graph) + indegree map &nbsp;O(V+E)</li>
 *   <li><b>Validate</b> — cycle detection via Kahn's BFS count &nbsp;O(V+E)</li>
 *   <li><b>Critical path</b> — DFS with memoisation to score every node &nbsp;O(V+E)</li>
 *   <li><b>Simulate</b> — event-driven N-worker execution &nbsp;O((V+E) log V)</li>
 * </ol>
 *
 * <p>Overall: <b>O((V+E) log V)</b> time, <b>O(V+E)</b> space.
 */
public class Scheduler {

    // ── Result ────────────────────────────────────────────────────────────────

    /**
     * Immutable data carrier returned by {@link #schedule}.
     * Contains the makespan and the aggregation data needed by {@link Analytics}.
     */
    public static class Result {
        /** Wall-clock time when the last assignment completes (the "makespan"). */
        public final long completionTime;
        /** category → sum of all assignment durations in that category. */
        public final Map<String, Long> categoryTotals;
        /** group → sum of all assignment durations in that group. */
        public final Map<String, Long> groupTotals;
        /** category → set of group names that belong to it. */
        public final Map<String, Set<String>> categoryToGroups;

        Result(long completionTime,
               Map<String, Long> categoryTotals,
               Map<String, Long> groupTotals,
               Map<String, Set<String>> categoryToGroups) {
            this.completionTime   = completionTime;
            this.categoryTotals   = categoryTotals;
            this.groupTotals      = groupTotals;
            this.categoryToGroups = categoryToGroups;
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Schedules all assignments using at most {@code N} concurrent workers.
     *
     * @param N       number of workers; 0 means unlimited
     * @param records parsed records from {@link Parser}
     * @return        scheduling {@link Result}
     * @throws IllegalArgumentException on duplicate keys, unknown prerequisites, or cycles
     */
    public Result schedule(int N, List<List<String>> records) {

        // ── Pass 1: register all nodes ────────────────────────────────────────
        Map<String, Assignment>   assignmentMap = new LinkedHashMap<>();
        Map<String, List<String>> graph         = new HashMap<>();
        Map<String, Integer>      indegree      = new HashMap<>();

        for (List<String> rec : records) {
            String category = rec.get(0).trim();
            String group    = rec.get(1).trim();
            long   duration = Long.parseLong(rec.get(2).trim());
            Assignment node = new Assignment(category, group, duration);

            if (assignmentMap.containsKey(node.key())) {
                throw new IllegalArgumentException(
                    "Duplicate assignment key: " + node.key());
            }
            assignmentMap.put(node.key(), node);
            indegree.put(node.key(), 0);
        }

        // ── Pass 2: add directed edges (parent → child) ───────────────────────
        for (List<String> rec : records) {
            String childKey = rec.get(0).trim()
                            + Assignment.KEY_SEPARATOR
                            + rec.get(1).trim();

            for (int i = 3; i < rec.size(); i += 2) {
                String parentKey = rec.get(i).trim()
                                 + Assignment.KEY_SEPARATOR
                                 + rec.get(i + 1).trim();

                if (!assignmentMap.containsKey(parentKey)) {
                    throw new IllegalArgumentException(
                        "Prerequisite not found: " + parentKey
                        + " (required by " + childKey + ")");
                }
                graph.computeIfAbsent(parentKey, k -> new ArrayList<>()).add(childKey);
                indegree.merge(childKey, 1, Integer::sum);
            }
        }

        // ── Pass 3: cycle detection (Kahn's count) ────────────────────────────
        validateAcyclic(assignmentMap.size(), graph, indegree);

        // ── Pass 4a: critical-path score for every node ───────────────────────
        Map<String, Long> criticalPath = new HashMap<>();
        for (String key : assignmentMap.keySet()) {
            computeCriticalPath(key, graph, assignmentMap, criticalPath);
        }

        // ── Pass 4b: event-driven N-worker simulation ─────────────────────────
        long makespan = simulate(N, assignmentMap, graph, indegree, criticalPath);

        // ── Aggregate totals for Analytics ────────────────────────────────────
        Map<String, Long>         categoryTotals  = new HashMap<>();
        Map<String, Long>         groupTotals     = new HashMap<>();
        Map<String, Set<String>>  categoryToGroups = new HashMap<>();

        for (Assignment a : assignmentMap.values()) {
            categoryTotals.merge(a.category, a.duration, Long::sum);
            groupTotals.merge(a.group, a.duration, Long::sum);
            categoryToGroups
                .computeIfAbsent(a.category, k -> new HashSet<>())
                .add(a.group);
        }

        return new Result(makespan, categoryTotals, groupTotals, categoryToGroups);
    }

    // ── Private: simulation ───────────────────────────────────────────────────

    /**
     * Runs the event-driven worker simulation.
     *
     * <p>Two queues drive execution:
     * <ul>
     *   <li>{@code readyQueue} — max-heap by critical-path score;
     *       ensures the most critical task is assigned to a free worker first.</li>
     *   <li>{@code runningQueue} — min-heap by finish time;
     *       the clock advances to the earliest completion event.</li>
     * </ul>
     */
    private long simulate(int N,
                          Map<String, Assignment>   assignmentMap,
                          Map<String, List<String>> graph,
                          Map<String, Integer>      indegree,
                          Map<String, Long>         criticalPath) {

        int workerLimit = (N == 0) ? Integer.MAX_VALUE : N;

        // Max-heap: longest critical path first; ties broken by name (deterministic)
        PriorityQueue<Assignment> readyQueue = new PriorityQueue<>(
            Comparator.<Assignment, Long>comparing(
                a -> criticalPath.get(a.key()), Comparator.reverseOrder()
            ).thenComparing(a -> a.category)
             .thenComparing(a -> a.group)
        );

        // Min-heap: earliest finishing task first
        PriorityQueue<RunningTask> runningQueue = new PriorityQueue<>(
            Comparator.comparingLong(t -> t.finishTime)
        );

        // Defensive copy — simulation must not corrupt the original indegree map
        Map<String, Integer> liveIndegree = new HashMap<>(indegree);

        // Seed: all nodes with no prerequisites are immediately ready
        for (Map.Entry<String, Integer> entry : liveIndegree.entrySet()) {
            if (entry.getValue() == 0) {
                readyQueue.offer(assignmentMap.get(entry.getKey()));
            }
        }

        long currentTime = 0;
        long makespan    = 0;

        while (!readyQueue.isEmpty() || !runningQueue.isEmpty()) {

            // Assign free workers to the highest-priority ready tasks
            while (!readyQueue.isEmpty() && runningQueue.size() < workerLimit) {
                Assignment next = readyQueue.poll();
                runningQueue.offer(new RunningTask(next, currentTime + next.duration));
            }

            // Advance clock to the next completion event
            RunningTask earliest = runningQueue.poll();
            currentTime = earliest.finishTime;
            makespan    = Math.max(makespan, currentTime);

            // Drain all tasks that finish at exactly the same time (simultaneous batch)
            List<RunningTask> completedBatch = new ArrayList<>();
            completedBatch.add(earliest);
            while (!runningQueue.isEmpty()
                   && runningQueue.peek().finishTime == currentTime) {
                completedBatch.add(runningQueue.poll());
            }

            // Release dependents for every task in the completed batch
            for (RunningTask completedTask : completedBatch) {
                String completedKey = completedTask.assignment.key();
                for (String dependentKey : graph.getOrDefault(completedKey, Collections.emptyList())) {
                    liveIndegree.merge(dependentKey, -1, Integer::sum);
                    if (liveIndegree.get(dependentKey) == 0) {
                        readyQueue.offer(assignmentMap.get(dependentKey));
                    }
                }
            }
        }

        return makespan;
    }

    // ── Private: graph validation ─────────────────────────────────────────────

    /**
     * Detects cycles using Kahn's algorithm.
     *
     * <p>Processes nodes in BFS order starting from indegree-0 roots.
     * If the total nodes processed is less than {@code totalNodes}, at least
     * one cycle exists.
     *
     * @throws IllegalArgumentException if a cycle is detected
     */
    private void validateAcyclic(int                       totalNodes,
                                 Map<String, List<String>> graph,
                                 Map<String, Integer>      indegree) {
        Map<String, Integer> indegreeCopy = new HashMap<>(indegree);
        Queue<String>        bfsQueue     = new ArrayDeque<>();

        for (Map.Entry<String, Integer> entry : indegreeCopy.entrySet()) {
            if (entry.getValue() == 0) bfsQueue.offer(entry.getKey());
        }

        int processedCount = 0;
        while (!bfsQueue.isEmpty()) {
            String current = bfsQueue.poll();
            processedCount++;
            for (String dependent : graph.getOrDefault(current, Collections.emptyList())) {
                indegreeCopy.merge(dependent, -1, Integer::sum);
                if (indegreeCopy.get(dependent) == 0) bfsQueue.offer(dependent);
            }
        }

        if (processedCount != totalNodes) {
            throw new IllegalArgumentException(
                "Cyclic dependency detected in assignments");
        }
    }

    // ── Private: critical path ────────────────────────────────────────────────

    /**
     * Computes the critical-path length for {@code node} using DFS + memoisation.
     *
     * <pre>
     *   criticalPath(node) = node.duration + max(criticalPath(child) for each child)
     *   criticalPath(leaf) = leaf.duration
     * </pre>
     *
     * @return the length of the longest path starting at {@code node}
     */
    private long computeCriticalPath(String                    node,
                                     Map<String, List<String>> graph,
                                     Map<String, Assignment>   assignmentMap,
                                     Map<String, Long>         memo) {
        if (memo.containsKey(node)) return memo.get(node);

        long maxDownstreamPath = 0;
        for (String child : graph.getOrDefault(node, Collections.emptyList())) {
            maxDownstreamPath = Math.max(
                maxDownstreamPath,
                computeCriticalPath(child, graph, assignmentMap, memo)
            );
        }

        long pathLength = assignmentMap.get(node).duration + maxDownstreamPath;
        memo.put(node, pathLength);
        return pathLength;
    }
}
