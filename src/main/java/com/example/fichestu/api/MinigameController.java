package com.example.fichestu.api;

import com.example.fichestu.api.MinigameDtos.FinishMinigameRequest;
import com.example.fichestu.api.MinigameDtos.MinigameAccessListResponse;
import com.example.fichestu.api.MinigameDtos.MinigameAccessResponse;
import com.example.fichestu.api.MinigameDtos.MinigameFinishResponse;
import com.example.fichestu.api.MinigameDtos.MinigameStartResponse;
import com.example.fichestu.api.MinigameDtos.StartMinigameRequest;
import com.example.fichestu.service.MinigameService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/minigames")
public class MinigameController {

    private final MinigameService minigameService;

    public MinigameController(MinigameService minigameService) {
        this.minigameService = minigameService;
    }

    @GetMapping("/access")
    public MinigameAccessListResponse accessList() {
        return minigameService.accessList();
    }

    @GetMapping("/{gameType}/access")
    public MinigameAccessResponse access(@PathVariable String gameType) {
        return minigameService.access(gameType);
    }

    @PostMapping("/attempts/start")
    public MinigameStartResponse start(@Valid @RequestBody StartMinigameRequest request) {
        return minigameService.start(request);
    }

    @PostMapping("/attempts/{attemptId}/finish")
    public MinigameFinishResponse finish(
        @PathVariable Integer attemptId,
        @Valid @RequestBody FinishMinigameRequest request
    ) {
        return minigameService.finish(attemptId, request);
    }
}
