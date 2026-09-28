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
public class GoogleMapsService {

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${google.api.key:}")
    private String googleApiKey;

    public GoogleMapsService(WebClient webClient) {
        this.webClient = webClient;
    }

    public RouteResponse.Coordinate geocode(String placeName) {
        try {
            if (googleApiKey == null || googleApiKey.isBlank()) return null;
            String query = URLEncoder.encode(placeName + ", India", StandardCharsets.UTF_8);
            String url = "https://maps.googleapis.com/maps/api/geocode/json?address=" + query + "&key=" + googleApiKey;
            String resp = webClient.get().uri(url).retrieve().bodyToMono(String.class).block();
            if (resp == null || resp.isBlank()) return null;
            JsonNode root = mapper.readTree(resp);
            if (!"OK".equalsIgnoreCase(root.path("status").asText())) return null;
            JsonNode loc = root.path("results").get(0).path("geometry").path("location");
            RouteResponse.Coordinate c = new RouteResponse.Coordinate();
            c.setLat(loc.path("lat").asDouble());
            c.setLng(loc.path("lng").asDouble());
            return c;
        } catch (Exception e) {
            return null;
        }
    }

    public RouteResponse getDirections(double slat, double slng, double elat, double elng) {
        try {
            if (googleApiKey == null || googleApiKey.isBlank()) return null;
            String url = UriComponentsBuilder.fromUriString("https://maps.googleapis.com/maps/api/directions/json")
                    .queryParam("origin", slat + "," + slng)
                    .queryParam("destination", elat + "," + elng)
                    .queryParam("mode", "driving")
                    .queryParam("key", googleApiKey)
                    .toUriString();
            String resp = webClient.get().uri(url).retrieve().bodyToMono(String.class).block();
            if (resp == null || resp.isBlank()) return null;
            JsonNode root = mapper.readTree(resp);
            if (!"OK".equalsIgnoreCase(root.path("status").asText())) return null;

            JsonNode leg = root.path("routes").get(0).path("legs").get(0);
            RouteResponse r = new RouteResponse();
            r.setDistance(leg.path("distance").path("value").asDouble());
            r.setDuration(leg.path("duration").path("value").asDouble());

            String poly = root.path("routes").get(0).path("overview_polyline").path("points").asText();
            List<RouteResponse.Coordinate> coords = decodePolyline(poly);

            RouteResponse.RouteData rd = new RouteResponse.RouteData();
            rd.setCoordinates(coords);
            r.setRoute(rd);

            return r;
        } catch (Exception e) {
            return null;
        }
    }

    private List<RouteResponse.Coordinate> decodePolyline(String encoded) {
        List<RouteResponse.Coordinate> poly = new ArrayList<>();
        int index = 0, lat = 0, lng = 0;
        while (index < encoded.length()) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1F) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;
            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1F) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;
            RouteResponse.Coordinate c = new RouteResponse.Coordinate();
            c.setLat(lat / 1E5);
            c.setLng(lng / 1E5);
            poly.add(c);
        }
        return poly;
    }
}
