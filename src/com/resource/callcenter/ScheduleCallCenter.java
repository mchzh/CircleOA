package com.resource.callcenter;

import com.resource.inmemorydb.ttl.ImMemoryDBWithTTL;

import java.util.*;

public class ScheduleCallCenter {
    // https://app2.greenhouse.io/tests/b1c7c0298767e099c6a668cfd55bf480?utm_medium=email&utm_source=TakeHomeTest
    // basic object: Assignment -> Catagory + Group  + duration + {dependent: (Catagory + Group)}
    // basic algorithm: topologic sort-> build adjcent list DAG graph with indegree
    // main function:
    // 1. List<String> parser input;
    // 2. loop the list of string: string1 -> category, string 2 -> group, string 3 -> duration
    // 3. build graph and indegree
    // 4. validate garaph to avoid cycle
    // 5. min heap with BFS to check current all nodes with indegree is 0

    public static String scheduleCallCenter(int G, int C, int N, String assignments) {
        if (G < 0 || C <0 || N < 0) {
            // error handling
            System.out.println("G or C or N is negative");
        }
        // parser assignments
        Parser parser = new Parser();
        List<List<String>> records = parser.parseAssignments(assignments);
        records.forEach(System.out::println);

        // build graph
        // adjacent list
        Map<String, Assignment> key2Assignment = new HashMap<>();
        Map<String, List<String>> graph = new HashMap<>(); // DAG
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, Long> criticalPathLength = new HashMap<>();
        for (List<String> record : records) {
            // first pass
            String category = record.get(0).trim();
            String group = record.get(1).trim();
            String key = category + " | " +  group;
            long duration = Long.parseLong(record.get(2).trim());
            Assignment cur = new Assignment(category, group, duration);
            key2Assignment.put(key, cur);
            indegree.put(key,0);
            // second pass for graph
            if (record.size() > 3) {
                // get parent node
                for (int i = 3; i < record.size(); i += 2) {
                    String pre_category = record.get(i).trim();
                    String pre_group = record.get(i+1).trim();
                    String pre_key = pre_category + " | " +  pre_group;
                    graph.computeIfAbsent(pre_key, k -> new ArrayList<>()).add(key);
                    indegree.put(key, indegree.getOrDefault(key, 0) + 1);
                }
            }
        }
        for (String key : key2Assignment.keySet()) {
            System.out.println("assiment info -> " + key + " -> " + key2Assignment.get(key).duration);
            dfsCriticalPath(key, graph, key2Assignment, criticalPathLength);
        }
        for (String key : indegree.keySet()) {
            System.out.println("indegress info -> " + key + " -> " + key2Assignment.get(key).duration + " : " + indegree.get(key));
        }

        //PriorityQueue<Assignment> priorityQueue = new PriorityQueue<>((a, b) -> Long.compare(a.duration, b.duration));
//        PriorityQueue<Assignment> readyQueue =
//                new PriorityQueue<>(
//                        Comparator.comparingLong(
//                                a -> a.duration
//                        )
//                );
        PriorityQueue<Assignment> readyQueue =
                new PriorityQueue<>(
                        (a, b) -> Long.compare(
                                criticalPathLength.get(
                                        b.Category + " | " + b.Group
                                ),
                                criticalPathLength.get(
                                        a.Category + " | " + a.Group
                                )
                        )
                );
        PriorityQueue<RunningTask> runningQueue =
                new PriorityQueue<>(
                        Comparator.comparingLong(
                                r -> r.finishTime
                        )
                );

        for (String key : indegree.keySet()) {
            int val  = indegree.get(key);

            if (val == 0) {
                System.out.println("initial bfs info -> " + key + " -> " + key2Assignment.get(key).duration + " : " + val);
                Assignment cur = key2Assignment.get(key);
                readyQueue.offer(cur);
            }
        }
        //System.out.println(priorityQueue.size());

        long totalunblocktime = 0;
        int step = 0;
        long currentTime = 0;
        while (!readyQueue.isEmpty() ||  !runningQueue.isEmpty()) {
            // add tasks into running queue
            int workerLimit =
                    (N == 0)
                            ? Integer.MAX_VALUE
                            : N;
            while (!readyQueue.isEmpty() && runningQueue.size() < workerLimit) {
                Assignment cur = readyQueue.poll();
                runningQueue.offer(new RunningTask(cur, currentTime+cur.duration));
            }
            // execute the peek task and release the workers with curretime as polled task finishtime
            RunningTask first = runningQueue.poll();

            currentTime = first.finishTime;
            // get all finished task with the same time
            List<RunningTask> completed = new ArrayList<>();
            completed.add(first);

            while (!runningQueue.isEmpty()
                    && runningQueue.peek().finishTime == currentTime) {

                completed.add(runningQueue.poll());
            }
            // add released indgree assignment into ready queue
            //assert task != null;
            for (RunningTask runningTask : completed) {
                assert runningTask != null;
                String curkey = runningTask.a.Category + " | " +  runningTask.a.Group;
                if (graph.containsKey(curkey)) {
                    for (String next : graph.get(curkey)) {
                        Assignment nextAssignment = key2Assignment.get(next);
                        indegree.put(next, indegree.getOrDefault(next, 0) - 1);
                        if (indegree.get(next) == 0) {
                            readyQueue.add(nextAssignment);
                            //priorityQueue.add(nextAssignment);
                        }
                    }
                }
            }


            totalunblocktime = Math.max(totalunblocktime, currentTime);
//            int size = Math.min(priorityQueue.size(), (N == 0 ? Integer.MAX_VALUE : N));
//            System.out.println(size);
//            long cursteptotaltime = 0;
//            List<Assignment> nextlevel = new ArrayList<>();
//            step++;
//            for (int i = 0 ; i < size ; i++) {
//
//                Assignment cur = priorityQueue.poll();
//                System.out.println("step : " + step + " : " + i + "' : " + cur.Group + " : " + cur.Category + " : " + cur.duration);
//                cursteptotaltime = Math.max(cursteptotaltime, cur.duration);
//                String curkey = cur.Category + " | " + cur.Group;
//                // next
//                if (!graph.containsKey(curkey)) continue;
//                for (String next : graph.get(curkey)) {
//                    Assignment nextAssignment = key2Assignment.get(next);
//                    indegree.put(next, indegree.getOrDefault(next, 0) - 1);
//                    if (indegree.get(next) == 0) {
//                        nextlevel.add(nextAssignment);
//                        //priorityQueue.add(nextAssignment);
//                    }
//                }
//            }
//            for (Assignment next : nextlevel) {
//                priorityQueue.offer(next);
//            }
//            totalunblocktime += cursteptotaltime;
        }
        // valid graph
        // schedule tasks with BFS
        return String.valueOf(totalunblocktime);
    }

    private static long dfsCriticalPath(
            String node,
            Map<String, List<String>> graph,
            Map<String, Assignment> assignments,
            Map<String, Long> memo
    ) {

        if (memo.containsKey(node)) {
            return memo.get(node);
        }

        long maxChild = 0;

        for (String child :
                graph.getOrDefault(node, List.of())) {

            maxChild = Math.max(
                    maxChild,
                    dfsCriticalPath(
                            child,
                            graph,
                            assignments,
                            memo
                    )
            );
        }

        long result =
                assignments.get(node).duration
                        + maxChild;

        memo.put(node, result);

        return result;
    }

    /*
     * ============================
     * Test Cases
     * ============================
     */
    public static void main(String[] args) {

//        String sample =
//                "Home,OR Jefferson,5444;"
//                        + "Medicare,OR Lake,1304;"
//                        + "Medicare,WA King,43061;"
//                        + "Life,OR Other,12806,Medicare,OR Lake;"
//                        + "Life,WA Other,70944,Medicare,WA King";
//
//        System.out.println(scheduleCallCenter(2,1,0,sample));

            String sample =
                    "Home,OR Jefferson,5444;"
                            + "Medicare,OR Lake,1304;"
                            + "Medicare,WA King,43061;"
                            + "Life,OR Other,12806,Medicare,OR Lake;"
                            + "Life,WA Other,70944,Medicare,WA King";

            System.out.println(scheduleCallCenter(3,3,2,sample));
        System.out.println(scheduleCallCenter(2,1,0,sample));
    }
}
