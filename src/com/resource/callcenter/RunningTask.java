package com.resource.callcenter;

public class RunningTask {
    Assignment a;
    long finishTime;

    public RunningTask(Assignment a, long finishTime) {
        this.a = a;
        this.finishTime = finishTime;
    }
}
