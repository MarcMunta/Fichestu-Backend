package com.example.fichestu.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ResendEmailClient {

    private static final URI EMAILS_URI = URI.create("https://api.resend.com/emails");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String fromAddress;
    private final Duration timeout;

    @Autowired
    public ResendEmailClient(
        ObjectMapper objectMapper,
        @Value("${app.resend.api-key:}") String apiKey,
        @Value("${app.resend.from:Fichestu <onboarding@resend.dev>}") String fromAddress,
        @Value("${app.resend.timeout-seconds:15}") long timeoutSeconds
    ) {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build(),
            objectMapper,
            apiKey,
            fromAddress,
            Duration.ofSeconds(timeoutSeconds)
        );
    }

    ResendEmailClient(
        HttpClient httpClient,
        ObjectMapper objectMapper,
        String apiKey,
        String fromAddress,
        Duration timeout
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.fromAddress = fromAddress;
        this.timeout = timeout;
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    public void sendTextEmail(String to, String subject, String text, String idempotencyKey) {
        if (!isConfigured()) {
            throw new IllegalStateException("Resend API key is not configured");
        }

        Map<String, Object> payload = Map.of(
            "from", fromAddress,
            "to", List.of(to),
            "subject", subject,
            "text", text
        );

        HttpRequest request = HttpRequest.newBuilder(EMAILS_URI)
            .timeout(timeout)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", idempotencyKey)
            .POST(HttpRequest.BodyPublishers.ofString(writeJson(payload)))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new EmailDeliveryException("Resend returned HTTP " + response.statusCode() + ": " + response.body());
            }
        } catch (IOException ex) {
            throw new EmailDeliveryException("Resend request failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new EmailDeliveryException("Resend request was interrupted", ex);
        }
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new EmailDeliveryException("Could not serialize email payload", ex);
        }
    }
}
