package com.example.fichestu.api;

import com.example.fichestu.api.GameDtos.BattleActionRequest;
import com.example.fichestu.api.GameDtos.CooldownResponse;
import com.example.fichestu.api.GameDtos.EnterBallRoomResponse;
import com.example.fichestu.api.GameDtos.MarketSnapshotResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RealGameController {

    private final GameService gameService;

    public RealGameController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping("/market")
    public MarketSnapshotResponse market() {
        return gameService.marketSnapshot();
    }

    @PostMapping("/market/buy")
    public WalletResponse buy(@Valid @RequestBody TradeRequest request) {
        return request.getTokenId() == null
            ? gameService.buy(request.getToken(), request.getQuantity())
            : gameService.buy(request.getTokenId(), request.getQuantity());
    }

    @PostMapping("/market/sell")
    public WalletResponse sell(@Valid @RequestBody TradeRequest request) {
        return request.getTokenId() == null
            ? gameService.sell(request.getToken(), request.getQuantity())
            : gameService.sell(request.getTokenId(), request.getQuantity());
    }

    @PostMapping("/rewards/claim")
    public CooldownResponse claimReward() {
        return gameService.claimRewarded();
    }

    @PostMapping("/games/ball-room/join")
    public EnterBallRoomResponse joinBallRoom() {
        return gameService.enterBallRoom();
    }

    @GetMapping("/games/ball-room/{matchId}")
    public MatchStateResponse getBallRoom(@PathVariable Integer matchId) {
        return gameService.matchState(matchId);
    }

    @PostMapping("/games/ball-room/{matchId}/pick")
    public MatchStateResponse pick(@PathVariable Integer matchId, @Valid @RequestBody PickBallRequest request) {
        return gameService.pickBall(matchId, request.getBallId());
    }

    @PostMapping("/games/ball-room/{matchId}/reveal")
    public MatchStateResponse reveal(@PathVariable Integer matchId) {
        return gameService.revealMultipliers(matchId);
    }

    @GetMapping("/games/battle/{matchId}")
    public MatchStateResponse getBattle(@PathVariable Integer matchId) {
        return gameService.matchState(matchId);
    }

    @PostMapping("/games/battle/{matchId}/action")
    public MatchStateResponse action(@PathVariable Integer matchId, @Valid @RequestBody BattleActionRequest request) {
        return gameService.submitBattleAction(
            matchId,
            request.getAction(),
            request.getSelectedToken(),
            request.getTokenId()
        );
    }

    @PostMapping("/games/battle/{matchId}/resolve-round")
    public MatchStateResponse resolveRound(@PathVariable Integer matchId) {
        return gameService.resolveBattleRound(matchId);
    }
}
