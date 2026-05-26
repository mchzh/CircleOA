package com.resouce.taskmanagement;

import java.util.*;

public interface ITaskManage {
    // add tasks
    // get all tasks
    // get task by id
    public Task addTask(String title, String description) throws Exception;
    public List<Task> getAllTasks() throws Exception;
    public Task getAllTaskById(String id) throws Exception;
    public List<Task> getTasksBefore(long timestamp, String keyWord) throws Exception;
    public List<Task> getTasksBefore(long timestamp) throws Exception;
    public List<Task> getTasksByKeyword(String keyword) throws Exception;
}
