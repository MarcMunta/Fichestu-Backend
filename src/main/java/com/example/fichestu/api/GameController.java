package com.example.fichestu.api;

import com.example.fichestu.api.GameDtos.BattleActionRequest;
import com.example.fichestu.api.GameDtos.CooldownResponse;
import com.example.fichestu.api.GameDtos.EnterBallRoomResponse;
import com.example.fichestu.api.GameDtos.GenericMessageResponse;
import com.example.fichestu.api.GameDtos.MatchStateResponse;
import com.example.fichestu.api.GameDtos.PickBallRequest;
import com.example.fichestu.api.GameDtos.TradeRequest;
import com.example.fichestu.api.GameDtos.WalletResponse;
import com.example.fichestu.service.GameService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/game")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping("/bootstrap")
    public GameDtos.BootstrapResponse bootstrap(@RequestHeader("Authorization") String authorization) {
        return gameService.bootstrap(authorization);
    }

    @GetMapping("/match/state")
    public MatchStateResponse getCurrentMatchState(@RequestHeader("Authorization") String authorization) {
        return gameService.currentMatchState(authorization);
    }

    @PostMapping("/market/buy")
    public WalletResponse buy(
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody TradeRequest request
    ) {
        return gameService.buy(authorization, request.getToken(), request.getQuantity());
    }

    @PostMapping("/market/sell")
    public WalletResponse sell(
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody TradeRequest request
    ) {
        return gameService.sell(authorization, request.getToken(), request.getQuantity());
    }

    @PostMapping("/ball-room/enter")
    public EnterBallRoomResponse enterBallRoom(@RequestHeader("Authorization") String authorization) {
        return gameService.enterBallRoom(authorization);
    }

    @PostMapping("/matches/{matchId}/pick-ball")
    public MatchStateResponse pickBall(
        @RequestHeader("Authorization") String authorization,
        @PathVariable Integer matchId,
        @Valid @RequestBody PickBallRequest request
    ) {
        return gameService.pickBall(authorization, matchId, request.getBallId());
    }

    @PostMapping("/matches/{matchId}/reveal")
    public MatchStateResponse revealMultipliers(
        @RequestHeader("Authorization") String authorization,
        @PathVariable Integer matchId
    ) {
        return gameService.revealMultipliers(authorization, matchId);
    }

    @PostMapping("/matches/{matchId}/battle/round")
    public MatchStateResponse playRound(
        @RequestHeader("Authorization") String authorization,
        @PathVariable Integer matchId,
        @Valid @RequestBody BattleActionRequest request
    ) {
        return gameService.playBattleRound(authorization, matchId, request.getAction(), request.getSelectedToken());
    }

    @PostMapping("/matches/{matchId}/close")
    public GenericMessageResponse closeMatch(
        @RequestHeader("Authorization") String authorization,
        @PathVariable Integer matchId
    ) {
        return gameService.closeMatch(authorization, matchId);
    }

    @PostMapping("/rewarded/claim")
    public CooldownResponse claimRewarded(@RequestHeader("Authorization") String authorization) {
        return gameService.claimRewarded(authorization);
    }
}
