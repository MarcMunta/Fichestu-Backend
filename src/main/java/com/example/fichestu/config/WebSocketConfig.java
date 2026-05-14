package com.example.fichestu.config;

import com.example.fichestu.realtime.MatchRealtimeService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MatchRealtimeService matchRealtimeService;

    public WebSocketConfig(MatchRealtimeService matchRealtimeService) {
        this.matchRealtimeService = matchRealtimeService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(matchRealtimeService, "/ws/matches")
            .setAllowedOriginPatterns("*");
    }
}
