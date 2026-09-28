package com.example.demo.model;

public class RouteRequest {
    private String start;        // place name typed by user
    private String end;          // place name typed by user
    private boolean avoidTolls;
    private boolean avoidHighways;

    public RouteRequest() {}

    public String getStart() {
        return start;
    }
    public void setStart(String start) {
        this.start = start;
    }

    public String getEnd() {
        return end;
    }
    public void setEnd(String end) {
        this.end = end;
    }

    public boolean isAvoidTolls() {
        return avoidTolls;
    }
    public void setAvoidTolls(boolean avoidTolls) {
        this.avoidTolls = avoidTolls;
    }

    public boolean isAvoidHighways() {
        return avoidHighways;
    }
    public void setAvoidHighways(boolean avoidHighways) {
        this.avoidHighways = avoidHighways;
    }
}
