package com.resouce.taskmanagement;

import java.text.SimpleDateFormat;
import java.util.*;

public class Task {
    // final is assigen once
    private final String id;

    public Task(String id, String title, String status, String description, long createdAt) {
        this.id = id;
        this.title = title;
        this.status = status;
        this.description = description;
        this.createdAt = createdAt;
    }

    private final String title;
    private  String status;
    private final String description;
    //private final String createdAt;
    private final long   createdAt;

    public String getTitle() {
        return title;
    }

    public String getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public String getId() {
        return id;
    }

    public String getTimestampFormatted() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(createdAt));
    }

    @Override
    public String toString() {
        return String.format("Task{id='%s', title='%s', description='%s', " +
                        "status='%s', createdAt='%s'}",
                id, title, description, status, getTimestampFormatted());
    }
}
