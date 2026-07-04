package com.nextide.model;

public class BuildResult {
    public enum Status { PENDING, RUNNING, SUCCESS, FAILED }

    private Status status;
    private String log;
    private long startTime;
    private long endTime;

    public BuildResult() {
        this.status = Status.PENDING;
        this.log = "";
        this.startTime = System.currentTimeMillis();
    }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getLog() { return log; }
    public void appendLog(String line) { this.log += line; }
    public void setLog(String log) { this.log = log; }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    public long getDurationMs() { return endTime - startTime; }
}
