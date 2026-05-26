package com.resouce.taskmanagement;

import java.util.*;
import java.util.stream.Collectors;

public class TaskManage implements ITaskManage {
    List<Task> tasks = new ArrayList<>();
    int idCounter = 0;

    @Override
    public Task addTask(String title, String description) throws Exception {
        String id = String.valueOf(idCounter++);
        //String timestamp = getCurrentTimestamp();
        long now = System.currentTimeMillis();
        Task t = new Task(id, title, "PENDING", description, now);
        tasks.add(t);

        return t;
    }

    // For testing: add task with specific timestamp
    public Task addTask(String title, String description, long timestamp) {
        String id   = String.valueOf(idCounter++);
        Task   task = new Task(id, title, description, "PENDING", timestamp);
        tasks.add(task);
        return task;
    }

    // ── Level 2 Core: Filter + Search + Sort ─────────────────────────────


    @Override
    public List<Task> getAllTasks() throws Exception {
        return Collections.unmodifiableList(tasks);
    }

    @Override
    public Task getAllTaskById(String id) throws Exception {
        return tasks.stream().filter(t -> t.getId().equals(id)).findFirst().orElse(null);
    }

    @Override
    public List<Task> getTasksBefore(long timestamp, String keyWord) throws Exception {
        // filter -> sort -> collection
        return tasks.stream()
                .filter(t -> t.getCreatedAt() <= timestamp)
                .filter(t -> t.getTitle().toLowerCase().contains(keyWord.toLowerCase()))
                .sorted(Comparator.comparingLong(Task::getCreatedAt).thenComparing(t -> Integer.parseInt(t.getId())))
                .toList();
    }

    @Override
    public List<Task> getTasksBefore(long timestamp) throws Exception {
        return tasks.stream()
                .filter(t -> t.getCreatedAt() <= timestamp)
                .sorted(Comparator.comparingLong(Task::getCreatedAt).thenComparing(t -> Integer.parseInt(t.getId())))
                .toList();
    }

    @Override
    public List<Task> getTasksByKeyword(String keyword) throws Exception {
        return tasks.stream()
                .filter(t -> t.getTitle().toLowerCase().contains(keyword.toLowerCase()))
                .sorted(Comparator.comparingLong(Task::getCreatedAt).thenComparing(t -> Integer.parseInt(t.getId())))
                .toList();
    }

    public void printAllTasks() throws Exception {
        if (tasks.isEmpty()) {
            System.out.println("No tasks found");
            return;
        }
        tasks.forEach(System.out::println);
    }

    public void printTasks(List<Task> taskList) {
        if (taskList.isEmpty()) {
            System.out.println("No tasks found.");
            return;
        }
        taskList.forEach(System.out::println);
    }

    private String getCurrentTimestamp() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new java.util.Date());
    }
}
