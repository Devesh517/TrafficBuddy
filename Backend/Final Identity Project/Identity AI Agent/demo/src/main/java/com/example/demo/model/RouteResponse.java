package com.example.demo.model;

import java.util.List;

public class RouteResponse {

    private RouteData route;
    private double distance;
    private double duration;
    private String trafficLevel;
    private List<String> directions;
    private String weatherInfo;

    // ===========================
    // INNER CLASS: RouteData
    // ===========================
    public static class RouteData {
        private List<Coordinate> coordinates;

        public List<Coordinate> getCoordinates() {
            return coordinates;
        }

        public void setCoordinates(List<Coordinate> coordinates) {
            this.coordinates = coordinates;
        }
    }

    // ===========================
    // INNER CLASS: Coordinate
    // ===========================
    public static class Coordinate {
        private double lat;
        private double lng;

        public Coordinate() {}

        public Coordinate(double lat, double lng) {
            this.lat = lat;
            this.lng = lng;
        }

        public double getLat() {
            return lat;
        }
        public void setLat(double lat) {
            this.lat = lat;
        }

        public double getLng() {
            return lng;
        }
        public void setLng(double lng) {
            this.lng = lng;
        }
    }

    // GETTERS + SETTERS (outer class)
    public RouteData getRoute() {
        return route;
    }
    public void setRoute(RouteData route) {
        this.route = route;
    }

    public double getDistance() {
        return distance;
    }
    public void setDistance(double distance) {
        this.distance = distance;
    }

    public double getDuration() {
        return duration;
    }
    public void setDuration(double duration) {
        this.duration = duration;
    }

    public String getTrafficLevel() {
        return trafficLevel;
    }
    public void setTrafficLevel(String trafficLevel) {
        this.trafficLevel = trafficLevel;
    }

    public List<String> getDirections() {
        return directions;
    }
    public void setDirections(List<String> directions) {
        this.directions = directions;
    }

    public String getWeatherInfo() {
        return weatherInfo;
    }
    public void setWeatherInfo(String weatherInfo) {
        this.weatherInfo = weatherInfo;
    }
}
