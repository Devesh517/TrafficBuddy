package com.example.demo.service;

import com.example.demo.model.RouteResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class OpenRouteService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ors.api.key:}")
    private String orsApiKey;

    @Value("${google.api.key:}")
    private String googleApiKey;

    public OpenRouteService(WebClient webClient) {
        this.webClient = webClient;
    }

    // Main entry: try ORS then Google fallback
    public RouteResponse getDirections(double startLat, double startLng,
                                       double endLat, double endLng,
                                       boolean avoidTolls, boolean avoidHighways) {

        RouteResponse fromOrs = getDirectionsFromORS(startLat, startLng, endLat, endLng, avoidTolls, avoidHighways);
        if (fromOrs != null) return fromOrs;

        if (googleApiKey != null && !googleApiKey.isBlank()) {
            return getDirectionsFromGoogle(startLat, startLng, endLat, endLng);
        }

        throw new RuntimeException("No routing provider available (ORS key missing or failed)");
    }

    // Text -> coordinate
    public RouteResponse.Coordinate getCoordinatesForPlace(String placeName) {
        if (placeName == null || placeName.trim().isEmpty()) return null;
        String cleaned = placeName.trim();

        RouteResponse.Coordinate c = geocodeNominatim(cleaned);
        if (c != null) return c;

        if (googleApiKey != null && !googleApiKey.isBlank()) {
            return geocodeGoogle(cleaned);
        }
        return null;
    }

    private RouteResponse.Coordinate geocodeNominatim(String placeName) {
        try {
            String query = placeName + ", India";
            String url = UriComponentsBuilder.fromUriString("https://nominatim.openstreetmap.org/search")
                    .queryParam("q", placeName)
                    .queryParam("format", "json")
                    .queryParam("limit", 1)
                    .toUriString();


            String resp = webClient.get()
                    .uri(url)
                    .header("User-Agent", "TrafficBuddy/1.0")
                    .retrieve().bodyToMono(String.class).block();

            if (resp == null || resp.isBlank()) return null;

            JsonNode arr = objectMapper.readTree(resp);
            if (!arr.isArray() || arr.size() == 0) return null;

            JsonNode node = arr.get(0);
            double lat = node.path("lat").asDouble();
            double lon = node.path("lon").asDouble();

            RouteResponse.Coordinate coord = new RouteResponse.Coordinate();
            coord.setLat(lat);
            coord.setLng(lon);
            return coord;
        } catch (Exception e) {
            return null;
        }
    }

    private RouteResponse.Coordinate geocodeGoogle(String placeName) {
        try {
            String query = URLEncoder.encode(placeName + ", India", StandardCharsets.UTF_8);
            String url = "https://maps.googleapis.com/maps/api/geocode/json?address=" + query + "&key=" + googleApiKey;

            String resp = webClient.get().uri(url).retrieve().bodyToMono(String.class).block();
            if (resp == null || resp.isBlank()) return null;

            JsonNode root = objectMapper.readTree(resp);
            if (!"OK".equalsIgnoreCase(root.path("status").asText())) return null;

            JsonNode loc = root.path("results").get(0).path("geometry").path("location");
            double lat = loc.path("lat").asDouble();
            double lng = loc.path("lng").asDouble();

            RouteResponse.Coordinate c = new RouteResponse.Coordinate();
            c.setLat(lat);
            c.setLng(lng);
            return c;
        } catch (Exception e) {
            return null;
        }
    }

    // ORS directions
    private RouteResponse getDirectionsFromORS(double startLat, double startLng,
                                               double endLat, double endLng,
                                               boolean avoidTolls, boolean avoidHighways) {
        try {
            if (orsApiKey == null || orsApiKey.isBlank()) return null;

            StringBuilder url = new StringBuilder("https://api.openrouteservice.org/v2/directions/driving-car");
            url.append("?start=").append(startLng).append(",").append(startLat);
            url.append("&end=").append(endLng).append(",").append(endLat);

            if (avoidTolls || avoidHighways) {
                List<String> avoids = new ArrayList<>();
                if (avoidTolls) avoids.add("\"tollways\"");
                if (avoidHighways) avoids.add("\"highways\"");
                String opts = "{\"avoid_features\":[" + String.join(",", avoids) + "]}";
                url.append("&options=").append(URLEncoder.encode(opts, StandardCharsets.UTF_8));
            }

            String resp = webClient.get()
                    .uri(url.toString())
                    .header("Authorization", orsApiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (resp == null || resp.isBlank()) return null;

            return parseOrsResponse(resp);
        } catch (Exception e) {
            return null;
        }
    }

    // Google fallback directions (overview polyline)
    private RouteResponse getDirectionsFromGoogle(double startLat, double startLng,
                                                  double endLat, double endLng) {
        try {
            String url = UriComponentsBuilder.fromUriString("https://maps.googleapis.com/maps/api/directions/json")
                    .queryParam("origin", startLat + "," + startLng)
                    .queryParam("destination", endLat + "," + endLng)
                    .queryParam("mode", "driving")
                    .queryParam("key", googleApiKey)
                    .toUriString();

            String resp = webClient.get().uri(url).retrieve().bodyToMono(String.class).block();
            if (resp == null || resp.isBlank()) return null;

            return parseGoogleDirections(resp);
        } catch (Exception e) {
            return null;
        }
    }

    // parse ORS
    private RouteResponse parseOrsResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode feature = root.path("features").get(0);

            RouteResponse r = new RouteResponse();
            JsonNode summary = feature.path("properties").path("summary");
            r.setDistance(summary.path("distance").asDouble());
            r.setDuration(summary.path("duration").asDouble());
            r.setTrafficLevel("Normal");

            List<RouteResponse.Coordinate> coords = new ArrayList<>();
            for (JsonNode n : feature.path("geometry").path("coordinates")) {
                RouteResponse.Coordinate c = new RouteResponse.Coordinate();
                c.setLng(n.get(0).asDouble());
                c.setLat(n.get(1).asDouble());
                coords.add(c);
            }

            RouteResponse.RouteData rd = new RouteResponse.RouteData();
            rd.setCoordinates(coords);
            r.setRoute(rd);

            List<String> directions = new ArrayList<>();
            JsonNode segments = feature.path("properties").path("segments");
            if (segments.isArray() && segments.size() > 0) {
                for (JsonNode s : segments.get(0).path("steps")) {
                    directions.add(s.path("instruction").asText() + " (" + s.path("distance").asInt() + "m)");
                }
            }
            r.setDirections(directions);
            return r;
        } catch (Exception e) {
            throw new RuntimeException("ORS parse error: " + e.getMessage(), e);
        }
    }

    // parse Google directions (polyline decode)
    private RouteResponse parseGoogleDirections(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!"OK".equalsIgnoreCase(root.path("status").asText()))
                throw new RuntimeException("Google returned: " + root.path("status").asText());

            JsonNode leg = root.path("routes").get(0).path("legs").get(0);
            RouteResponse r = new RouteResponse();
            r.setDistance(leg.path("distance").path("value").asDouble());
            r.setDuration(leg.path("duration").path("value").asDouble());
            r.setTrafficLevel("Normal");

            String poly = root.path("routes").get(0).path("overview_polyline").path("points").asText();
            List<RouteResponse.Coordinate> coords = decodePolyline(poly);

            RouteResponse.RouteData rd = new RouteResponse.RouteData();
            rd.setCoordinates(coords);
            r.setRoute(rd);

            List<String> dirs = new ArrayList<>();
            for (JsonNode step : leg.path("steps")) {
                dirs.add(step.path("html_instructions").asText().replaceAll("<[^>]*>", ""));
            }
            r.setDirections(dirs);
            return r;
        } catch (Exception e) {
            throw new RuntimeException("Google parse error: " + e.getMessage(), e);
        }
    }

    // decode polyline (Google)
    private List<RouteResponse.Coordinate> decodePolyline(String encoded) {
        List<RouteResponse.Coordinate> coordinates = new ArrayList<>();
        int index = 0, lat = 0, lng = 0;

        while (index < encoded.length()) {
            int result = 0, shift = 0, b;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            lat += ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            result = 0;
            shift = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            lng += ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            RouteResponse.Coordinate c = new RouteResponse.Coordinate();
            c.setLat(lat / 1E5);
            c.setLng(lng / 1E5);
            coordinates.add(c);
        }
        return coordinates;
    }
}
