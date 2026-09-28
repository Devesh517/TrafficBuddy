package com.example.demo.model;

/**
 * Model for an unused endpoint (optimizeByText) to resolve compilation warnings.
 */
public class RouteTextRequest {
    private String textQuery;

    public RouteTextRequest() {}

    // Getters and Setters
    public String getTextQuery() { return textQuery; }
    public void setTextQuery(String textQuery) { this.textQuery = textQuery; }
}