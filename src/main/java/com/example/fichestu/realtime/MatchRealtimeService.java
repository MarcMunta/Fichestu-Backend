package com.example.fichestu.realtime;

import com.example.fichestu.security.JwtService;
import com.example.fichestu.service.JwtTokenRevocationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Service
public class MatchRealtimeService extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final JwtService jwtService;
    private final JwtTokenRevocationService jwtTokenRevocationService;
    private final Map<Integer, Set<WebSocketSession>> sessionsByMatch = new ConcurrentHashMap<>();
    private final Map<String, Integer> matchBySession = new ConcurrentHashMap<>();

    public MatchRealtimeService(
        ObjectMapper objectMapper,
        JwtService jwtService,
        JwtTokenRevocationService jwtTokenRevocationService
    ) {
        this.objectMapper = objectMapper;
        this.jwtService = jwtService;
        this.jwtTokenRevocationService = jwtTokenRevocationService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = queryParam(session, "token");
        if (token == null || jwtTokenRevocationService.isRevoked(token) || jwtService.parseClaims(token).isEmpty()) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("invalid token"));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode json = objectMapper.readTree(message.getPayload());
        String type = json.path("type").asText("");
        if (!"SUBSCRIBE_MATCH".equalsIgnoreCase(type)) {
            return;
        }

        int matchId = json.path("matchId").asInt(0);
        if (matchId <= 0) {
            return;
        }

        unsubscribe(session);
        sessionsByMatch.computeIfAbsent(matchId, key -> ConcurrentHashMap.newKeySet()).add(session);
        matchBySession.put(session.getId(), matchId);
        send(session, Map.of(
            "type", "SUBSCRIBED",
            "matchId", matchId,
            "serverNowEpochMs", Instant.now().toEpochMilli()
        ));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        unsubscribe(session);
    }

    public void publishMatchChanged(Integer matchId, String event) {
        if (matchId == null) {
            return;
        }
        Set<WebSocketSession> sessions = sessionsByMatch.get(matchId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        Map<String, Object> payload = Map.of(
            "type", "MATCH_CHANGED",
            "matchId", matchId,
            "event", event,
            "serverNowEpochMs", Instant.now().toEpochMilli()
        );
        sessions.removeIf(session -> !send(session, payload));
    }

    private void unsubscribe(WebSocketSession session) {
        Integer matchId = matchBySession.remove(session.getId());
        if (matchId == null) {
            return;
        }
        Set<WebSocketSession> sessions = sessionsByMatch.get(matchId);
        if (sessions != null) {
            sessions.remove(session);
        }
    }

    private boolean send(WebSocketSession session, Map<String, ?> payload) {
        if (!session.isOpen()) {
            return false;
        }
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private String queryParam(WebSocketSession session, String key) {
        if (session.getUri() == null || session.getUri().getQuery() == null) {
            return null;
        }
        for (String pair : session.getUri().getQuery().split("&")) {
            int separator = pair.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String name = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8);
            if (key.equals(name)) {
                return URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
