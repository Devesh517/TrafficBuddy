package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() {
        // Make sure to set a reasonable in-memory buffer for larger responses
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(conf -> conf.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        return WebClient.builder()
                .defaultHeader("User-Agent", "TrafficBuddy/1.0 (contact: your-email@example.com)")
                .defaultHeader("Accept-Language", "en")
                .exchangeStrategies(strategies)
                .build();
    }
}
