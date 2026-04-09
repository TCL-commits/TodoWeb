package com.example.demo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class WebhookIntegrationService {

    private final RestClient restClient;

    @Value("${app.integration.webhook-url:}")
    private String webhookUrl;

    @Value("${app.integration.webhook-enabled:false}")
    private boolean webhookEnabled;

    public WebhookIntegrationService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public void publish(String eventType, Map<String, Object> payload) {
        if (!webhookEnabled || webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("eventType", eventType);
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("payload", payload);

        try {
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Webhook delivery failed for event {}: {}", eventType, ex.getMessage());
        }
    }
}
