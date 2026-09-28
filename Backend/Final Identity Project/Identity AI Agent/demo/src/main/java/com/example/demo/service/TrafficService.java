package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class TrafficService {

    private final WebClient webClient;

    @Value("${ors.api.key:}")
    private String orsKey;

    public TrafficService(WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<String> getTraffic(double slat, double slng, double elat, double elng) {
        if (orsKey == null || orsKey.isBlank()) {
            return Mono.just("Traffic data unavailable (ORS key missing)");
        }

        String url = "https://api.openrouteservice.org/v2/directions/driving-car"
                + "?start=" + slng + "," + slat
                + "&end=" + elng + "," + elat;

        return webClient.get()
                .uri(url)
                .header("Authorization", orsKey)
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(e -> Mono.just("Traffic data unavailable"));
    }
}
