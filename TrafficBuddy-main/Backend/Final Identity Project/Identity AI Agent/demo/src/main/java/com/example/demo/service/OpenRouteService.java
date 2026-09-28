package com.example.demo.service;

import com.example.demo.model.RouteResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(OpenRouteService.class);

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
    // FIX (Issue 1, round 2): the Nominatim dead-code bug (see geocodeNominatim below) is
    // fixed, but Nominatim alone still has real coverage gaps for POI-level places (a
    // specific bus stand/station only resolves if that exact node is mapped *and* tagged
    // with a matching name in OpenStreetMap - much less consistent than city/admin
    // boundaries, which are always present). And critically: the Google tier below only
    // runs at all if googleApiKey is configured - if it's blank/missing (Google Maps
    // Platform requires a billing-enabled key), that whole fallback silently no-ops and
    // you're running on Nominatim alone. CONFIRM google.api.key is actually set in your
    // application.properties/environment - if it isn't, only tiers 1-3 below are ever active.
    //
    // Chain, in order:
    // 1) Nominatim, region-scoped (free, no key)
    // 2) Nominatim, raw/unscoped retry (free, no key)
    // 3) Photon (komoot's OSM-based geocoder) - free, no key, specifically tuned for
    //    fuzzy POI/name search where plain Nominatim ranking misses
    // 4) Google Geocoding API (good for addresses/administrative areas) - only if
    //    google.api.key is configured
    // 5) Google Places "Find Place From Text" API (POI-specific) - only if
    //    google.api.key is configured
    //
    // Every tier now logs why it failed (no result / bad status / exception) instead of
    // silently swallowing errors, so future failures are actually diagnosable from logs.
    public RouteResponse.Coordinate getCoordinatesForPlace(String placeName) {
        if (placeName == null || placeName.trim().isEmpty()) return null;
        String cleaned = placeName.trim();

        RouteResponse.Coordinate c = geocodeNominatim(cleaned);
        if (c != null) return c;

        c = geocodePhoton(cleaned);
        if (c != null) return c;

        if (googleApiKey != null && !googleApiKey.isBlank()) {
            c = geocodeGoogle(cleaned);
            if (c != null) return c;
        } else {
            log.warn("Google fallback skipped for '{}' - google.api.key is not configured", cleaned);
        }

        log.warn("Could not resolve place '{}' with any geocoding provider (Nominatim, Photon{})",
                cleaned, (googleApiKey == null || googleApiKey.isBlank()) ? "" : ", Google");
        return null;
    }

    private RouteResponse.Coordinate geocodeNominatim(String placeName) {
        try {
            // BUG FIX: this method built a region-scoped query string ("<place>, India")
            // but then queried Nominatim with the raw, unscoped placeName instead of that
            // query - the "query" variable was never used. Well-known city names are
            // globally unambiguous so this happened to work, but specific POI names
            // (e.g. a particular railway station or bus stand) are common/ambiguous
            // worldwide and need the country/region context to resolve correctly - without
            // it Nominatim frequently returns no usable match. Now we actually send the
            // scoped query.
            String query = placeName + ", India";
            String url = UriComponentsBuilder.fromUriString("https://nominatim.openstreetmap.org/search")
                    .queryParam("q", query)
                    .queryParam("format", "json")
                    .queryParam("limit", 1)
                    .queryParam("countrycodes", "in")
                    .toUriString();

            String resp = webClient.get()
                    .uri(url)
                    .header("User-Agent", "TrafficBuddy/1.0")
                    .retrieve().bodyToMono(String.class).block();

            if (resp == null || resp.isBlank()) return null;

            JsonNode arr = objectMapper.readTree(resp);
            if (!arr.isArray() || arr.size() == 0) {
                log.debug("Nominatim (scoped) found no result for '{}, India' - trying unscoped retry", placeName);
                // Fallback: retry once without the appended country suffix, in case the
                // POI's indexed name doesn't combine well with the appended text (e.g.
                // it already includes a place/city name of its own).
                return geocodeNominatimRaw(placeName);
            }

            JsonNode node = arr.get(0);
            double lat = node.path("lat").asDouble();
            double lon = node.path("lon").asDouble();

            RouteResponse.Coordinate coord = new RouteResponse.Coordinate();
            coord.setLat(lat);
            coord.setLng(lon);
            return coord;
        } catch (Exception e) {
            log.warn("Nominatim (scoped) request failed for '{}': {}", placeName, e.getMessage());
            return null;
        }
    }

    // Fallback used by geocodeNominatim() when the region-scoped query returns nothing.
    private RouteResponse.Coordinate geocodeNominatimRaw(String placeName) {
        try {
            String url = UriComponentsBuilder.fromUriString("https://nominatim.openstreetmap.org/search")
                    .queryParam("q", placeName)
                    .queryParam("format", "json")
                    .queryParam("limit", 1)
                    .queryParam("countrycodes", "in")
                    .toUriString();

            String resp = webClient.get()
                    .uri(url)
                    .header("User-Agent", "TrafficBuddy/1.0")
                    .retrieve().bodyToMono(String.class).block();

            if (resp == null || resp.isBlank()) return null;

            JsonNode arr = objectMapper.readTree(resp);
            if (!arr.isArray() || arr.size() == 0) {
                log.debug("Nominatim (unscoped) also found no result for '{}'", placeName);
                return null;
            }

            JsonNode node = arr.get(0);
            RouteResponse.Coordinate coord = new RouteResponse.Coordinate();
            coord.setLat(node.path("lat").asDouble());
            coord.setLng(node.path("lon").asDouble());
            return coord;
        } catch (Exception e) {
            log.warn("Nominatim (unscoped) request failed for '{}': {}", placeName, e.getMessage());
            return null;
        }
    }

    // NEW: Photon (https://photon.komoot.io) - a free, key-less geocoder built on
    // OpenStreetMap data but with fuzzy matching and ranking specifically tuned for named
    // places and POIs, where plain Nominatim's ranking often misses. This is the fallback
    // that actually helps when Nominatim comes back empty and no Google key is configured.
    private RouteResponse.Coordinate geocodePhoton(String placeName) {
        try {
            String query = placeName + ", India";
            String url = UriComponentsBuilder.fromUriString("https://photon.komoot.io/api/")
                    .queryParam("q", query)
                    .queryParam("limit", 1)
                    .queryParam("lang", "en")
                    .toUriString();

            String resp = webClient.get()
                    .uri(url)
                    .retrieve().bodyToMono(String.class).block();

            if (resp == null || resp.isBlank()) return null;

            JsonNode root = objectMapper.readTree(resp);
            JsonNode features = root.path("features");
            if (!features.isArray() || features.size() == 0) {
                log.debug("Photon found no result for '{}'", query);
                return null;
            }

            JsonNode coords = features.get(0).path("geometry").path("coordinates");
            if (!coords.isArray() || coords.size() < 2) return null;

            // GeoJSON order is [lng, lat]
            RouteResponse.Coordinate coord = new RouteResponse.Coordinate();
            coord.setLng(coords.get(0).asDouble());
            coord.setLat(coords.get(1).asDouble());
            return coord;
        } catch (Exception e) {
            log.warn("Photon request failed for '{}': {}", placeName, e.getMessage());
            return null;
        }
    }

    private RouteResponse.Coordinate geocodeGoogle(String placeName) {
        // Try the Geocoding API first (fast, and good for cities/administrative areas).
        RouteResponse.Coordinate c = geocodeGoogleAddress(placeName);
        if (c != null) return c;

        // FIX (Issue 1): the Geocoding API is address-centric - it's built to resolve
        // postal addresses and administrative areas (cities, states), which is why city
        // names always worked. It does NOT reliably index named points of interest
        // (train stations, bus stands, landmarks, businesses), so it was returning
        // ZERO_RESULTS for those and getCoordinatesForPlace() had no further fallback.
        // The Places "Find Place From Text" API is purpose-built for exactly this kind
        // of named-POI lookup, so we fall back to it here.
        return geocodeGooglePlaces(placeName);
    }

    private RouteResponse.Coordinate geocodeGoogleAddress(String placeName) {
        try {
            String query = URLEncoder.encode(placeName + ", India", StandardCharsets.UTF_8);
            String url = "https://maps.googleapis.com/maps/api/geocode/json?address=" + query + "&key=" + googleApiKey;

            String resp = webClient.get().uri(url).retrieve().bodyToMono(String.class).block();
            if (resp == null || resp.isBlank()) return null;

            JsonNode root = objectMapper.readTree(resp);
            String status = root.path("status").asText();
            if (!"OK".equalsIgnoreCase(status)) {
                // e.g. ZERO_RESULTS, REQUEST_DENIED (bad/unbilled key), OVER_QUERY_LIMIT
                log.warn("Google Geocoding API returned status '{}' for '{}'", status, placeName);
                return null;
            }

            JsonNode loc = root.path("results").get(0).path("geometry").path("location");
            double lat = loc.path("lat").asDouble();
            double lng = loc.path("lng").asDouble();

            RouteResponse.Coordinate c = new RouteResponse.Coordinate();
            c.setLat(lat);
            c.setLng(lng);
            return c;
        } catch (Exception e) {
            log.warn("Google Geocoding API request failed for '{}': {}", placeName, e.getMessage());
            return null;
        }
    }

    // NEW: POI-specific geocoding fallback using Google's Places API, which handles
    // named places (stations, bus stands, landmarks, businesses) far better than the
    // Geocoding API used above.
    private RouteResponse.Coordinate geocodeGooglePlaces(String placeName) {
        try {
            String input = URLEncoder.encode(placeName + ", India", StandardCharsets.UTF_8);
            String url = "https://maps.googleapis.com/maps/api/place/findplacefromtext/json"
                    + "?input=" + input
                    + "&inputtype=textquery"
                    + "&fields=geometry"
                    + "&key=" + googleApiKey;

            String resp = webClient.get().uri(url).retrieve().bodyToMono(String.class).block();
            if (resp == null || resp.isBlank()) return null;

            JsonNode root = objectMapper.readTree(resp);
            String status = root.path("status").asText();
            if (!"OK".equalsIgnoreCase(status)) {
                log.warn("Google Places API returned status '{}' for '{}'", status, placeName);
                return null;
            }

            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.size() == 0) return null;

            JsonNode loc = candidates.get(0).path("geometry").path("location");
            RouteResponse.Coordinate c = new RouteResponse.Coordinate();
            c.setLat(loc.path("lat").asDouble());
            c.setLng(loc.path("lng").asDouble());
            return c;
        } catch (Exception e) {
            log.warn("Google Places API request failed for '{}': {}", placeName, e.getMessage());
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