package com.example.demo.service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class WeatherService {

    private final WebClient webClient;

    public WeatherService(WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<String> getWeather(double lat, double lon) {
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&current_weather=true";

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(e -> Mono.just("Weather data unavailable"));
    }
}
