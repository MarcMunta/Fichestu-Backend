package com.example.fichestu.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GoogleAppsScriptEmailClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String webAppUrl;
    private final String secret;
    private final Duration timeout;

    @Autowired
    public GoogleAppsScriptEmailClient(
        ObjectMapper objectMapper,
        @Value("${app.google-mail-script.url:}") String webAppUrl,
        @Value("${app.google-mail-script.secret:}") String secret,
        @Value("${app.google-mail-script.timeout-seconds:15}") long timeoutSeconds
    ) {
        this(
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build(),
            objectMapper,
            webAppUrl,
            secret,
            Duration.ofSeconds(timeoutSeconds)
        );
    }

    GoogleAppsScriptEmailClient(
        HttpClient httpClient,
        ObjectMapper objectMapper,
        String webAppUrl,
        String secret,
        Duration timeout
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.webAppUrl = webAppUrl == null ? "" : webAppUrl.trim();
        this.secret = secret == null ? "" : secret.trim();
        this.timeout = timeout;
    }

    public boolean isConfigured() {
        return !webAppUrl.isBlank() && !secret.isBlank();
    }

    public void sendTextEmail(String to, String subject, String text, String idempotencyKey) {
        if (!isConfigured()) {
            throw new IllegalStateException("Google Apps Script mail endpoint is not configured");
        }

        Map<String, Object> payload = Map.of(
            "secret", secret,
            "to", to,
            "subject", subject,
            "text", text,
            "idempotencyKey", idempotencyKey
        );

        HttpRequest request = HttpRequest.newBuilder(URI.create(webAppUrl))
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(writeJson(payload)))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new EmailDeliveryException("Google Apps Script returned HTTP " + response.statusCode() + ": " + response.body());
            }
            validateSuccessResponse(response.body());
        } catch (IOException ex) {
            throw new EmailDeliveryException("Google Apps Script mail request failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new EmailDeliveryException("Google Apps Script mail request was interrupted", ex);
        }
    }

    private void validateSuccessResponse(String responseBody) {
        try {
            JsonNode body = objectMapper.readTree(responseBody);
            if (!body.path("success").asBoolean(false)) {
                String message = body.path("message").asText("unknown error");
                throw new EmailDeliveryException("Google Apps Script rejected email: " + message);
            }
        } catch (JsonProcessingException ex) {
            throw new EmailDeliveryException("Could not parse Google Apps Script response", ex);
        }
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new EmailDeliveryException("Could not serialize Google Apps Script email payload", ex);
        }
    }
}
