package com.example.demo.controller;

import com.example.demo.model.RouteRequest;
import com.example.demo.model.RouteResponse;
import com.example.demo.service.OpenRouteService;
import com.example.demo.service.TrafficService;
import com.example.demo.service.WeatherService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/route")
@CrossOrigin(origins = "*")
public class RouteController {

    private final OpenRouteService openRouteService;
    private final TrafficService trafficService;
    private final WeatherService weatherService;

    public RouteController(OpenRouteService openRouteService,
                           TrafficService trafficService,
                           WeatherService weatherService) {
        this.openRouteService = openRouteService;
        this.trafficService = trafficService;
        this.weatherService = weatherService;
    }

    // Text-based search endpoint
    @PostMapping("/search")
    public ResponseEntity<?> optimizeByText(@RequestBody RouteRequest req) {
        try {
            if (req == null || req.getStart() == null || req.getEnd() == null) {
                return ResponseEntity.badRequest().body("start and end must be provided");
            }

            RouteResponse.Coordinate start = openRouteService.getCoordinatesForPlace(req.getStart());
            if (start == null) return ResponseEntity.badRequest().body("Start location not found: " + req.getStart());

            RouteResponse.Coordinate end = openRouteService.getCoordinatesForPlace(req.getEnd());
            if (end == null) return ResponseEntity.badRequest().body("End location not found: " + req.getEnd());

            RouteResponse route = openRouteService.getDirections(
                    start.getLat(), start.getLng(),
                    end.getLat(), end.getLng(),
                    req.isAvoidTolls(), req.isAvoidHighways()
            );

            // attach traffic (blocking the reactive Mono)
            try {
                String traffic = trafficService.getTraffic(start.getLat(), start.getLng(), end.getLat(), end.getLng()).block();
                route.setTrafficLevel(traffic);
            } catch (Exception ignored) {
            }

            // attach weather (blocking)
            try {
                String weather = weatherService.getWeather(start.getLat(), start.getLng()).block();
                route.setWeatherInfo(weather);
            } catch (Exception ignored) {
            }

            return ResponseEntity.ok(route);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Internal Server Error: " + e.getMessage());
        }
    }
}
