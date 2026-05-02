package com.example.fichestu.api;

import com.example.fichestu.api.GameDtos.BattleActionRequest;
import com.example.fichestu.api.GameDtos.CooldownResponse;
import com.example.fichestu.api.GameDtos.EnterBallRoomResponse;
import com.example.fichestu.api.GameDtos.GenericMessageResponse;
import com.example.fichestu.api.GameDtos.MarketSnapshotResponse;
import com.example.fichestu.api.GameDtos.MatchStateResponse;
import com.example.fichestu.api.GameDtos.PickBallRequest;
import com.example.fichestu.api.GameDtos.TradeRequest;
import com.example.fichestu.api.GameDtos.WinnerImpactRequest;
import com.example.fichestu.api.GameDtos.WalletResponse;
import com.example.fichestu.service.GameService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    public GameDtos.BootstrapResponse bootstrap() {
        return gameService.bootstrap();
    }

    @GetMapping("/market")
    public MarketSnapshotResponse marketSnapshot() {
        return gameService.marketSnapshot();
    }

    @GetMapping("/match/state")
    public MatchStateResponse getCurrentMatchState() {
        return gameService.currentMatchState();
    }

    @PostMapping("/market/buy")
    public WalletResponse buy(@Valid @RequestBody TradeRequest request) {
        return gameService.buy(request.getToken(), request.getQuantity());
    }

    @PostMapping("/market/sell")
    public WalletResponse sell(@Valid @RequestBody TradeRequest request) {
        return gameService.sell(request.getToken(), request.getQuantity());
    }

    @PostMapping("/ball-room/enter")
    public EnterBallRoomResponse enterBallRoom() {
        return gameService.enterBallRoom();
    }

    @PostMapping("/matches/{matchId}/join")
    public EnterBallRoomResponse joinMatch(@PathVariable Integer matchId) {
        return gameService.joinMatch(matchId);
    }

    @PostMapping("/matches/{matchId}/matchmaking/cancel")
    public EnterBallRoomResponse cancelMatchmaking(@PathVariable Integer matchId) {
        return gameService.cancelMatchmaking(matchId);
    }

    @PostMapping("/matches/{matchId}/matchmaking/abandon")
    public EnterBallRoomResponse abandonMatchmaking(@PathVariable Integer matchId) {
        return gameService.abandonMatchmaking(matchId);
    }

    @PostMapping("/matches/{matchId}/abandon")
    public EnterBallRoomResponse abandonMatch(@PathVariable Integer matchId) {
        return gameService.abandonMatch(matchId);
    }

    @PostMapping("/matches/{matchId}/pick-ball")
    public MatchStateResponse pickBall(
        @PathVariable Integer matchId,
        @Valid @RequestBody PickBallRequest request
    ) {
        return gameService.pickBall(matchId, request.getBallId());
    }

    @PostMapping("/matches/{matchId}/reveal")
    public MatchStateResponse revealMultipliers(@PathVariable Integer matchId) {
        return gameService.revealMultipliers(matchId);
    }

    @PostMapping("/matches/{matchId}/battle/round")
    public MatchStateResponse playRound(
        @PathVariable Integer matchId,
        @Valid @RequestBody BattleActionRequest request
    ) {
        return gameService.playBattleRound(matchId, request.getAction(), request.getSelectedToken());
    }

    @PostMapping("/matches/{matchId}/winner-impact")
    public MatchStateResponse applyWinnerImpact(
        @PathVariable Integer matchId,
        @Valid @RequestBody WinnerImpactRequest request
    ) {
        return gameService.applyWinnerImpact(matchId, request.getToken());
    }

    @PostMapping("/matches/{matchId}/close")
    public GenericMessageResponse closeMatch(@PathVariable Integer matchId) {
        return gameService.closeMatch(matchId);
    }

    @PostMapping("/rewarded/claim")
    public CooldownResponse claimRewarded() {
        return gameService.claimRewarded();
    }
}
