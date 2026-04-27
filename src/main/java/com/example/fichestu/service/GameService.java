package com.example.fichestu.service;

import com.example.fichestu.api.GameDtos.BallOptionDto;
import com.example.fichestu.api.GameDtos.BallPlayerDto;
import com.example.fichestu.api.GameDtos.BallRoomDto;
import com.example.fichestu.api.GameDtos.BattleDto;
import com.example.fichestu.api.GameDtos.BattlePlayerDto;
import com.example.fichestu.api.GameDtos.BootstrapResponse;
import com.example.fichestu.api.GameDtos.CooldownResponse;
import com.example.fichestu.api.GameDtos.EnterBallRoomResponse;
import com.example.fichestu.api.GameDtos.GenericMessageResponse;
import com.example.fichestu.api.GameDtos.MarketSnapshotResponse;
import com.example.fichestu.api.GameDtos.MatchStateResponse;
import com.example.fichestu.api.GameDtos.ProfileStatsDto;
import com.example.fichestu.api.GameDtos.TokenDto;
import com.example.fichestu.api.GameDtos.TransactionDto;
import com.example.fichestu.api.GameDtos.WalletResponse;
import com.example.fichestu.persistence.entity.GameSessionEntity;
import com.example.fichestu.persistence.entity.GameSessionEventEntity;
import com.example.fichestu.persistence.entity.MatchCardEntity;
import com.example.fichestu.persistence.entity.MatchParticipantEntity;
import com.example.fichestu.persistence.entity.MatchParticipantId;
import com.example.fichestu.persistence.entity.TokenEntity;
import com.example.fichestu.persistence.entity.TokenPriceHistoryEntity;
import com.example.fichestu.persistence.entity.TransactionLogEntity;
import com.example.fichestu.persistence.entity.UserEntity;
import com.example.fichestu.persistence.entity.UserWalletEntity;
import com.example.fichestu.persistence.entity.UserWalletId;
import com.example.fichestu.persistence.repository.GameSessionEventRepository;
import com.example.fichestu.persistence.repository.GameSessionRepository;
import com.example.fichestu.persistence.repository.MatchCardRepository;
import com.example.fichestu.persistence.repository.MatchParticipantRepository;
import com.example.fichestu.persistence.repository.TokenPriceHistoryRepository;
import com.example.fichestu.persistence.repository.TokenRepository;
import com.example.fichestu.persistence.repository.TransactionLogRepository;
import com.example.fichestu.persistence.repository.UserRepository;
import com.example.fichestu.persistence.repository.UserWalletRepository;
import com.example.fichestu.security.CurrentUserService;
import com.example.fichestu.support.RandomProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GameService {

    private static final BigDecimal BALL_ENTRY_COST = new BigDecimal("10.00");
    private static final BigDecimal REWARDED_AMOUNT = new BigDecimal("25.00");
    private static final int ROOM_SIZE = 10;
    private static final int BALL_COUNT = 50;
    private static final int INITIAL_HP = 50;
    private static final String TYPE_REWARDED = "REWARDED";
    private static final String EVENT_ROUND_SUMMARY = "ROUND_SUMMARY";

    private final CurrentUserService currentUserService;
    private final PlayerProfileReadService playerProfileReadService;
    private final MarketMaintenanceService marketMaintenanceService;
    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final UserWalletRepository userWalletRepository;
    private final TokenPriceHistoryRepository tokenPriceHistoryRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final GameSessionRepository gameSessionRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final MatchCardRepository matchCardRepository;
    private final GameSessionEventRepository gameSessionEventRepository;
    private final RandomProvider randomProvider;

    public GameService(
        CurrentUserService currentUserService,
        PlayerProfileReadService playerProfileReadService,
        MarketMaintenanceService marketMaintenanceService,
        UserRepository userRepository,
        TokenRepository tokenRepository,
        UserWalletRepository userWalletRepository,
        TokenPriceHistoryRepository tokenPriceHistoryRepository,
        TransactionLogRepository transactionLogRepository,
        GameSessionRepository gameSessionRepository,
        MatchParticipantRepository matchParticipantRepository,
        MatchCardRepository matchCardRepository,
        GameSessionEventRepository gameSessionEventRepository,
        RandomProvider randomProvider
    ) {
        this.currentUserService = currentUserService;
        this.playerProfileReadService = playerProfileReadService;
        this.marketMaintenanceService = marketMaintenanceService;
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.userWalletRepository = userWalletRepository;
        this.tokenPriceHistoryRepository = tokenPriceHistoryRepository;
        this.transactionLogRepository = transactionLogRepository;
        this.gameSessionRepository = gameSessionRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.matchCardRepository = matchCardRepository;
        this.gameSessionEventRepository = gameSessionEventRepository;
        this.randomProvider = randomProvider;
    }

    @Transactional
    public BootstrapResponse bootstrap() {
        UserEntity user = currentUserService.requireUserEntity();
        marketMaintenanceService.syncMarketState();

        List<TokenDto> tokens = buildTokenDtos(user);
        List<com.example.fichestu.api.GameDtos.BadgeDto> badges = playerProfileReadService.loadBadgesForUser(user.getUserId());
        ProfileStatsDto stats = playerProfileReadService.loadStatsForUser(user.getUserId());

        return new BootstrapResponse(
            "Estado inicial cargado",
            true,
            user.getUserId(),
            user.getUsername(),
            user.getFiatBalance(),
            playerProfileReadService.currentRewardedCooldownSeconds(user.getUserId()),
            tokens,
            badges,
            stats
        );
    }

    @Transactional
    public MarketSnapshotResponse marketSnapshot() {
        UserEntity user = currentUserService.requireUserEntity();
        marketMaintenanceService.syncMarketState();

        List<TokenDto> tokens = buildTokenDtos(user);
        return new MarketSnapshotResponse(
            "Mercado cargado",
            true,
            user.getFiatBalance(),
            calculateTotalBalance(user.getFiatBalance(), tokens),
            playerProfileReadService.currentRewardedCooldownSeconds(user.getUserId()),
            (int) transactionLogRepository.countByUserUserIdAndType(user.getUserId(), TYPE_REWARDED),
            tokens,
            buildTransactionDtos(user.getUserId())
        );
    }

    @Transactional
    public WalletResponse buy(String tokenAlias, int quantity) {
        return trade(tokenAlias, quantity, true);
    }

    @Transactional
    public WalletResponse sell(String tokenAlias, int quantity) {
        return trade(tokenAlias, quantity, false);
    }

    @Transactional
    public EnterBallRoomResponse enterBallRoom() {
        UserEntity user = currentUserService.requireUserEntity();
        marketMaintenanceService.syncMarketState();

        Optional<GameSessionEntity> currentSession = findCurrentSession(user.getUserId());
        if (currentSession.isPresent()) {
            GameSessionEntity session = currentSession.get();
            return new EnterBallRoomResponse(
                "Ya tienes una partida activa",
                true,
                session.getMatchId(),
                user.getFiatBalance(),
                buildBallRoomDto(session, user.getUserId())
            );
        }

        ensureSufficientBalance(user, "entrar en la sala");
        debitBallEntry(user);

        GameSessionEntity session = new GameSessionEntity();
        session.setStatus("WAITING");
        session = gameSessionRepository.save(session);

        createParticipant(session, user);
        logEvent(session, "ROOM_CREATED", user.getUsername() + " ha creado la sala.");

        return new EnterBallRoomResponse(
            "Sala creada. Esperando jugadores",
            true,
            session.getMatchId(),
            user.getFiatBalance(),
            buildBallRoomDto(session, user.getUserId())
        );
    }

    @Transactional
    public EnterBallRoomResponse joinMatch(Integer matchId) {
        UserEntity user = currentUserService.requireUserEntity();
        marketMaintenanceService.syncMarketState();

        Optional<GameSessionEntity> currentSession = findCurrentSession(user.getUserId());
        if (currentSession.isPresent()) {
            GameSessionEntity existing = currentSession.get();
            if (!existing.getMatchId().equals(matchId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ya tienes una partida activa");
            }
            return new EnterBallRoomResponse(
                "Ya perteneces a esta sala",
                true,
                existing.getMatchId(),
                user.getFiatBalance(),
                buildBallRoomDto(existing, user.getUserId())
            );
        }

        GameSessionEntity session = gameSessionRepository.findByMatchIdForUpdate(matchId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match no encontrado"));

        long currentPlayers = matchParticipantRepository.countByIdMatchId(matchId);
        if (currentPlayers >= ROOM_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La sala ya esta llena");
        }
        if (!"WAITING".equalsIgnoreCase(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La sala ya no acepta jugadores");
        }

        ensureSufficientBalance(user, "unirte a la sala");
        debitBallEntry(user);
        createParticipant(session, user);

        long newCount = matchParticipantRepository.countByIdMatchId(matchId);
        if (newCount >= ROOM_SIZE) {
            session.setStatus("PICKING");
            gameSessionRepository.save(session);
            logEvent(session, "ROOM_FULL", "La sala ya tiene 10 jugadores. Empieza la seleccion de bolas.");
        } else {
            logEvent(session, "PLAYER_JOINED", user.getUsername() + " se ha unido a la sala.");
        }

        return new EnterBallRoomResponse(
            "Te has unido a la sala",
            true,
            session.getMatchId(),
            user.getFiatBalance(),
            buildBallRoomDto(session, user.getUserId())
        );
    }

    @Transactional(readOnly = true)
    public MatchStateResponse currentMatchState() {
        UserEntity user = currentUserService.requireUserEntity();
        Optional<GameSessionEntity> sessionOptional = findCurrentSession(user.getUserId());

        if (sessionOptional.isEmpty()) {
            return new MatchStateResponse(
                "No hay match activo",
                true,
                null,
                new BallRoomDto("WAITING_ENTRY", "Crea o unete a una sala para empezar.", false, List.of(), List.of()),
                new BattleDto("LOCKED", 0, null, null, null, "ATTACK", true, List.of("Todavia no hay partida activa."), List.of())
            );
        }

        GameSessionEntity session = sessionOptional.get();
        return buildMatchStateResponse(session, user.getUserId(), "Estado del match cargado", null);
    }

    @Transactional
    public MatchStateResponse pickBall(Integer matchId, Integer ballId) {
        UserEntity user = currentUserService.requireUserEntity();
        GameSessionEntity session = loadSessionOwnedByUser(matchId, user.getUserId());

        if (!"PICKING".equalsIgnoreCase(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La sala no esta en fase de seleccion");
        }

        List<MatchParticipantEntity> participants = matchParticipantRepository.findByIdMatchId(matchId);
        if (participants.size() != ROOM_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La sala aun no tiene 10 jugadores");
        }

        MatchParticipantEntity me = participants.stream()
            .filter(participant -> participant.getUser().getUserId().equals(user.getUserId()))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No perteneces a esta sala"));

        if (me.getSelectedBallNumber() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ya elegiste una bola");
        }

        Set<Integer> pickedNumbers = participants.stream()
            .map(MatchParticipantEntity::getSelectedBallNumber)
            .filter(value -> value != null)
            .collect(HashSet::new, HashSet::add, HashSet::addAll);

        if (pickedNumbers.contains(ballId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esa bola ya fue tomada");
        }

        me.setSelectedBallNumber(ballId);
        matchParticipantRepository.save(me);
        logEvent(session, "BALL_PICKED", user.getUsername() + " ha elegido la bola " + ballId + ".");

        long pickedCount = matchParticipantRepository.countByIdMatchIdAndSelectedBallNumberIsNotNull(matchId);
        if (pickedCount == ROOM_SIZE) {
            session.setStatus("READY_REVEAL");
            gameSessionRepository.save(session);
            logEvent(session, "READY_REVEAL", "Todas las bolas han sido elegidas. Ya se pueden revelar multiplicadores.");
        }

        return buildMatchStateResponse(session, user.getUserId(), "Bola seleccionada", null);
    }

    @Transactional
    public MatchStateResponse revealMultipliers(Integer matchId) {
        UserEntity user = currentUserService.requireUserEntity();
        GameSessionEntity session = loadSessionOwnedByUser(matchId, user.getUserId());

        if (!"READY_REVEAL".equalsIgnoreCase(session.getStatus()) && !"REVEALED".equalsIgnoreCase(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aun no se puede revelar");
        }

        if (matchParticipantRepository.countByIdMatchIdAndSelectedBallNumberIsNotNull(matchId) != ROOM_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aun faltan bolas por elegir");
        }

        if (!"REVEALED".equalsIgnoreCase(session.getStatus())) {
            session.setStatus("REVEALED");
            gameSessionRepository.save(session);
            logEvent(session, "MULTIPLIERS_REVEALED", "Los multiplicadores ya son visibles para todos los jugadores.");
        }

        return buildMatchStateResponse(session, user.getUserId(), "Multiplicadores revelados", null);
    }

    @Transactional
    public MatchStateResponse playBattleRound(Integer matchId, String action, String selectedTokenAlias) {
        UserEntity user = currentUserService.requireUserEntity();
        GameSessionEntity session = loadSessionOwnedByUser(matchId, user.getUserId());

        if ("FINISHED".equalsIgnoreCase(session.getStatus())) {
            return buildMatchStateResponse(session, user.getUserId(), "La batalla ya ha terminado", action);
        }
        if (!"REVEALED".equalsIgnoreCase(session.getStatus()) && !"IN_PROGRESS".equalsIgnoreCase(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Battle no desbloqueado");
        }

        session.setStatus("IN_PROGRESS");
        gameSessionRepository.save(session);

        List<MatchParticipantEntity> participants = matchParticipantRepository.findByIdMatchId(matchId);
        Map<Integer, String> actions = new HashMap<>();

        for (MatchParticipantEntity participant : participants) {
            if (!Boolean.TRUE.equals(participant.getAlive())) {
                continue;
            }

            String chosenAction = participant.getUser().getUserId().equals(user.getUserId())
                ? normalizeAction(action)
                : randomAction();

            actions.put(participant.getUser().getUserId(), chosenAction);
            persistCard(session, participant.getUser(), chosenAction);
        }

        Map<Integer, Integer> hpByUser = new HashMap<>();
        for (MatchParticipantEntity participant : participants) {
            hpByUser.put(participant.getUser().getUserId(), participant.getCurrentHp());
        }

        List<String> roundLogs = new ArrayList<>();
        int roundNumber = (int) gameSessionEventRepository.countByMatchMatchIdAndEventType(matchId, EVENT_ROUND_SUMMARY) + 1;

        for (MatchParticipantEntity attacker : participants) {
            Integer attackerId = attacker.getUser().getUserId();
            if (!Boolean.TRUE.equals(attacker.getAlive())) {
                continue;
            }
            if (!"ATTACK".equals(actions.get(attackerId))) {
                continue;
            }

            List<MatchParticipantEntity> possibleTargets = participants.stream()
                .filter(participant -> !participant.getUser().getUserId().equals(attackerId))
                .filter(participant -> (hpByUser.getOrDefault(participant.getUser().getUserId(), 0)) > 0)
                .toList();

            if (possibleTargets.isEmpty()) {
                continue;
            }

            MatchParticipantEntity target = possibleTargets.get(randomProvider.nextInt(possibleTargets.size()));
            int damage = 1 + randomProvider.nextInt(9);
            Integer targetId = target.getUser().getUserId();

            if ("SHIELD".equals(actions.get(targetId))) {
                roundLogs.add(attacker.getUser().getUsername() + " ataca a " + target.getUser().getUsername() + " pero el escudo lo bloquea.");
                continue;
            }

            if ("REBOUND".equals(actions.get(targetId))) {
                int nextHp = Math.max(0, hpByUser.getOrDefault(attackerId, attacker.getCurrentHp()) - damage);
                hpByUser.put(attackerId, nextHp);
                roundLogs.add(target.getUser().getUsername() + " rebota " + damage + " de daño sobre " + attacker.getUser().getUsername() + ".");
                continue;
            }

            int nextHp = Math.max(0, hpByUser.getOrDefault(targetId, target.getCurrentHp()) - damage);
            hpByUser.put(targetId, nextHp);
            roundLogs.add(attacker.getUser().getUsername() + " golpea a " + target.getUser().getUsername() + " por " + damage + " puntos.");
        }

        for (MatchParticipantEntity participant : participants) {
            int hp = hpByUser.getOrDefault(participant.getUser().getUserId(), participant.getCurrentHp());
            participant.setCurrentHp(hp);
            participant.setAlive(hp > 0);
        }
        matchParticipantRepository.saveAll(participants);

        List<MatchParticipantEntity> aliveParticipants = participants.stream()
            .filter(participant -> Boolean.TRUE.equals(participant.getAlive()))
            .toList();

        logEvent(session, EVENT_ROUND_SUMMARY, "Ronda " + roundNumber + " resuelta.");
        for (String roundLog : roundLogs) {
            logEvent(session, "BATTLE_LOG", roundLog);
        }

        if (aliveParticipants.size() <= 1) {
            session.setStatus("FINISHED");
            session.setEndTime(Instant.now());
            MatchParticipantEntity winner = aliveParticipants.isEmpty() ? null : aliveParticipants.get(0);
            session.setWinner(winner == null ? null : winner.getUser());
            gameSessionRepository.save(session);

            if (winner != null) {
                logEvent(session, "WINNER", "Ganador: " + winner.getUser().getUsername() + " con x" + formatMultiplier(winner.getMultiplierWon().doubleValue()) + ".");
                if (winner.getUser().getUserId().equals(user.getUserId()) && selectedTokenAlias != null && !selectedTokenAlias.isBlank()) {
                    applyWinnerImpactInternal(session, selectedTokenAlias, winner.getMultiplierWon(), user);
                } else if (!Boolean.TRUE.equals(session.getImpactApplied())) {
                    logEvent(session, "WINNER_PENDING_IMPACT", "El ganador aun no ha elegido la ficha a impactar.");
                }
            } else {
                logEvent(session, "DRAW", "La partida termina en empate.");
            }
        }

        return buildMatchStateResponse(session, user.getUserId(), "Ronda resuelta", normalizeAction(action));
    }

    @Transactional
    public MatchStateResponse applyWinnerImpact(Integer matchId, String tokenAlias) {
        UserEntity user = currentUserService.requireUserEntity();
        GameSessionEntity session = loadSessionOwnedByUser(matchId, user.getUserId());

        if (!"FINISHED".equalsIgnoreCase(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La partida aun no ha terminado");
        }
        if (session.getWinner() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La partida no tiene ganador");
        }
        if (!session.getWinner().getUserId().equals(user.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el ganador puede aplicar el impacto");
        }
        if (Boolean.TRUE.equals(session.getImpactApplied())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El impacto ya fue aplicado");
        }

        MatchParticipantEntity winnerParticipant = matchParticipantRepository.findByIdMatchId(matchId).stream()
            .filter(participant -> participant.getUser().getUserId().equals(user.getUserId()))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Participante ganador no encontrado"));

        applyWinnerImpactInternal(session, tokenAlias, winnerParticipant.getMultiplierWon(), user);
        return buildMatchStateResponse(session, user.getUserId(), "Impacto aplicado al mercado", null);
    }

    @Transactional
    public GenericMessageResponse closeMatch(Integer matchId) {
        UserEntity user = currentUserService.requireUserEntity();
        GameSessionEntity session = loadSessionOwnedByUser(matchId, user.getUserId());

        if ("FINISHED".equalsIgnoreCase(session.getStatus()) && session.getWinner() != null && !Boolean.TRUE.equals(session.getImpactApplied())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aplica primero el impacto del ganador");
        }

        if (!"CLOSED".equalsIgnoreCase(session.getStatus())) {
            session.setStatus("CLOSED");
            session.setEndTime(Instant.now());
            gameSessionRepository.save(session);
            logEvent(session, "MATCH_CLOSED", "La partida ha sido cerrada.");
        }

        return new GenericMessageResponse("Match cerrado", true);
    }

    @Transactional
    public CooldownResponse claimRewarded() {
        UserEntity user = currentUserService.requireUserEntity();
        int cooldown = playerProfileReadService.currentRewardedCooldownSeconds(user.getUserId());
        if (cooldown > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rewarded no disponible todavia");
        }

        user.setFiatBalance(user.getFiatBalance().add(REWARDED_AMOUNT).setScale(2, RoundingMode.HALF_UP));
        userRepository.save(user);
        logTransaction(user, TYPE_REWARDED, REWARDED_AMOUNT, "Rewarded ad completado");

        return new CooldownResponse(
            "Rewarded aplicado",
            true,
            user.getFiatBalance(),
            playerProfileReadService.currentRewardedCooldownSeconds(user.getUserId()),
            (int) transactionLogRepository.countByUserUserIdAndType(user.getUserId(), TYPE_REWARDED)
        );
    }

    private WalletResponse trade(String tokenAlias, int quantity, boolean isBuy) {
        if (quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad debe ser mayor que cero");
        }

        UserEntity user = currentUserService.requireUserEntity();
        marketMaintenanceService.syncMarketState();

        TokenEntity token = resolveToken(tokenAlias);
        UserWalletEntity wallet = findOrCreateWallet(user, token);
        BigDecimal qty = BigDecimal.valueOf(quantity).setScale(4, RoundingMode.HALF_UP);
        BigDecimal amount = token.getCurrentPrice().multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);

        if (isBuy) {
            if (user.getFiatBalance().compareTo(amount) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saldo insuficiente para comprar");
            }
            user.setFiatBalance(user.getFiatBalance().subtract(amount).setScale(2, RoundingMode.HALF_UP));
            wallet.setQuantity(wallet.getQuantity().add(qty));
            logTransaction(user, "BUY", amount.negate(), "Compra de " + quantity + " " + tokenAlias.toUpperCase(Locale.ROOT));
        } else {
            if (wallet.getQuantity().compareTo(qty) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No tienes suficientes fichas para vender");
            }
            wallet.setQuantity(wallet.getQuantity().subtract(qty));
            user.setFiatBalance(user.getFiatBalance().add(amount).setScale(2, RoundingMode.HALF_UP));
            logTransaction(user, "SELL", amount, "Venta de " + quantity + " " + tokenAlias.toUpperCase(Locale.ROOT));
        }

        userRepository.save(user);
        userWalletRepository.save(wallet);

        List<TokenDto> tokens = buildTokenDtos(user);
        return new WalletResponse(
            isBuy ? "Compra realizada" : "Venta realizada",
            true,
            user.getFiatBalance(),
            calculateTotalBalance(user.getFiatBalance(), tokens),
            tokens
        );
    }

    private void applyWinnerImpactInternal(
        GameSessionEntity session,
        String tokenAlias,
        BigDecimal multiplier,
        UserEntity triggeredBy
    ) {
        TokenEntity token = resolveToken(tokenAlias);
        BigDecimal nextPrice = token.getCurrentPrice().multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
        if (nextPrice.compareTo(new BigDecimal("0.50")) < 0) {
            nextPrice = new BigDecimal("0.50");
        }

        token.setCurrentPrice(nextPrice);
        token.setLastUpdate(Instant.now());
        tokenRepository.save(token);

        TokenPriceHistoryEntity history = new TokenPriceHistoryEntity();
        history.setToken(token);
        history.setPrice(nextPrice);
        tokenPriceHistoryRepository.save(history);

        session.setWinnerTokenAlias(tokenAlias.trim().toUpperCase(Locale.ROOT));
        session.setImpactApplied(true);
        gameSessionRepository.save(session);

        logEvent(session, "WINNER_IMPACT", triggeredBy.getUsername() + " aplica x" + formatMultiplier(multiplier.doubleValue()) + " sobre " + token.getName() + ".");
    }

    private MatchStateResponse buildMatchStateResponse(
        GameSessionEntity session,
        Integer userId,
        String message,
        String selectedAction
    ) {
        return new MatchStateResponse(
            message,
            true,
            session.getMatchId(),
            buildBallRoomDto(session, userId),
            buildBattleDto(session, userId, selectedAction)
        );
    }

    private BallRoomDto buildBallRoomDto(GameSessionEntity session, Integer userId) {
        List<MatchParticipantEntity> participants = matchParticipantRepository.findByIdMatchId(session.getMatchId());
        Map<Integer, Double> multiplierByBall = new HashMap<>();
        for (MatchParticipantEntity participant : participants) {
            if (participant.getSelectedBallNumber() != null && participant.getMultiplierWon() != null) {
                multiplierByBall.put(participant.getSelectedBallNumber(), participant.getMultiplierWon().doubleValue());
            }
        }

        List<BallPlayerDto> players = participants.stream()
            .map(participant -> new BallPlayerDto(
                String.valueOf(participant.getUser().getUserId()),
                participant.getUser().getUserId().equals(userId) ? "Tu" : participant.getUser().getUsername(),
                participant.getUser().getUserId().equals(userId),
                participant.getSelectedBallNumber(),
                isMultiplierVisible(session.getStatus()) && participant.getMultiplierWon() != null
                    ? participant.getMultiplierWon().doubleValue()
                    : null
            ))
            .toList();

        List<BallOptionDto> balls = new ArrayList<>();
        for (int ballNumber = 1; ballNumber <= BALL_COUNT; ballNumber++) {
            Integer currentBall = ballNumber;
            Integer pickedBy = participants.stream()
                .filter(participant -> currentBall.equals(participant.getSelectedBallNumber()))
                .map(participant -> participant.getUser().getUserId())
                .findFirst()
                .orElse(null);

            balls.add(new BallOptionDto(
                ballNumber,
                isMultiplierVisible(session.getStatus()) ? multiplierByBall.getOrDefault(ballNumber, 1.0) : null,
                pickedBy == null ? null : String.valueOf(pickedBy)
            ));
        }

        boolean canReveal = participants.size() == ROOM_SIZE
            && participants.stream().allMatch(participant -> participant.getSelectedBallNumber() != null);

        String phase;
        String statusMessage;
        if ("WAITING".equalsIgnoreCase(session.getStatus())) {
            phase = "WAITING_PLAYERS";
            statusMessage = "Esperando jugadores: " + participants.size() + "/" + ROOM_SIZE + ".";
        } else if ("PICKING".equalsIgnoreCase(session.getStatus()) || "READY_REVEAL".equalsIgnoreCase(session.getStatus())) {
            phase = "PICKING";
            statusMessage = canReveal
                ? "Todos eligieron. Revela multiplicadores para iniciar Battle Royale."
                : "Elige una bola unica. " + participants.size() + "/" + ROOM_SIZE + " jugadores listos.";
        } else if (isMultiplierVisible(session.getStatus())) {
            phase = "REVEALED";
            Optional<MatchParticipantEntity> me = participants.stream()
                .filter(participant -> participant.getUser().getUserId().equals(userId))
                .findFirst();
            double myMultiplier = me.map(value -> value.getMultiplierWon().doubleValue()).orElse(1.0);
            statusMessage = "Tu multiplicador es x" + formatMultiplier(myMultiplier) + ". Pasa al Battle Royale.";
        } else {
            phase = "WAITING_ENTRY";
            statusMessage = "Crea o unete a una sala para empezar.";
        }

        return new BallRoomDto(phase, statusMessage, canReveal, players, balls);
    }

    private BattleDto buildBattleDto(GameSessionEntity session, Integer userId, String selectedAction) {
        List<MatchParticipantEntity> participants = matchParticipantRepository.findByIdMatchId(session.getMatchId());
        String phase;
        if ("IN_PROGRESS".equalsIgnoreCase(session.getStatus())) {
            phase = "IN_PROGRESS";
        } else if ("FINISHED".equalsIgnoreCase(session.getStatus()) || "CLOSED".equalsIgnoreCase(session.getStatus())) {
            phase = "FINISHED";
        } else if ("REVEALED".equalsIgnoreCase(session.getStatus())) {
            phase = "READY";
        } else {
            phase = "LOCKED";
        }

        List<BattlePlayerDto> players = participants.stream()
            .map(participant -> new BattlePlayerDto(
                String.valueOf(participant.getUser().getUserId()),
                participant.getUser().getUserId().equals(userId) ? "Tu" : participant.getUser().getUsername(),
                participant.getUser().getUserId().equals(userId),
                participant.getCurrentHp(),
                participant.getMultiplierWon() == null ? 1.0 : participant.getMultiplierWon().doubleValue(),
                Boolean.TRUE.equals(participant.getAlive())
            ))
            .toList();

        Double winningMultiplier = null;
        String winnerId = null;
        String winnerName = null;
        if (session.getWinner() != null) {
            winnerId = String.valueOf(session.getWinner().getUserId());
            winnerName = session.getWinner().getUsername();
            winningMultiplier = participants.stream()
                .filter(participant -> participant.getUser().getUserId().equals(session.getWinner().getUserId()))
                .map(participant -> participant.getMultiplierWon().doubleValue())
                .findFirst()
                .orElse(1.0);
        }

        List<String> log = gameSessionEventRepository.findTop24ByMatchMatchIdOrderByEventIdDesc(session.getMatchId()).stream()
            .sorted(Comparator.comparing(GameSessionEventEntity::getEventId))
            .map(GameSessionEventEntity::getMessage)
            .toList();
        if (log.isEmpty()) {
            log = List.of("Esperando a que la partida avance.");
        }

        int round = (int) gameSessionEventRepository.countByMatchMatchIdAndEventType(session.getMatchId(), EVENT_ROUND_SUMMARY);

        return new BattleDto(
            phase,
            round,
            winnerId,
            winnerName,
            winningMultiplier,
            selectedAction == null ? "ATTACK" : selectedAction,
            !"LOCKED".equals(phase),
            log,
            players
        );
    }

    private List<TokenDto> buildTokenDtos(UserEntity user) {
        Map<Integer, BigDecimal> walletByToken = new HashMap<>();
        for (UserWalletEntity wallet : userWalletRepository.findByIdUserId(user.getUserId())) {
            walletByToken.put(wallet.getToken().getTokenId(), wallet.getQuantity());
        }

        List<TokenDto> tokens = new ArrayList<>();
        for (TokenEntity token : tokenRepository.findAllByOrderByTokenIdAsc()) {
            List<TokenPriceHistoryEntity> descendingHistory = tokenPriceHistoryRepository.findTop28ByTokenTokenIdOrderByRecordedAtDesc(token.getTokenId());
            List<BigDecimal> history = descendingHistory.stream()
                .sorted(Comparator.comparing(TokenPriceHistoryEntity::getRecordedAt))
                .map(TokenPriceHistoryEntity::getPrice)
                .toList();

            BigDecimal previous = history.size() >= 2 ? history.get(history.size() - 2) : token.getCurrentPrice();
            TokenMeta meta = tokenMeta(token.getName(), token.getColorCode());

            tokens.add(new TokenDto(
                token.getTokenId(),
                token.getName(),
                meta.ticker(),
                meta.colorCode(),
                token.getCurrentPrice(),
                previous,
                walletByToken.getOrDefault(token.getTokenId(), BigDecimal.ZERO),
                history
            ));
        }

        return tokens;
    }

    private BigDecimal calculateTotalBalance(BigDecimal cashBalance, List<TokenDto> tokens) {
        BigDecimal holdingsValue = tokens.stream()
            .map(tokenDto -> tokenDto.currentPrice().multiply(tokenDto.holdings()).setScale(2, RoundingMode.HALF_UP))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return cashBalance.add(holdingsValue).setScale(2, RoundingMode.HALF_UP);
    }

    private List<TransactionDto> buildTransactionDtos(Integer userId) {
        return transactionLogRepository.findTop20ByUserUserIdOrderByCreatedAtDesc(userId).stream()
            .map(log -> new TransactionDto(
                log.getTransactionId(),
                log.getType(),
                log.getAmountFiat(),
                log.getDescription(),
                log.getCreatedAt()
            ))
            .toList();
    }

    private Optional<GameSessionEntity> findCurrentSession(Integer userId) {
        return matchParticipantRepository.findByIdUserId(userId).stream()
            .map(MatchParticipantEntity::getMatch)
            .filter(match -> match != null)
            .filter(match -> !"CLOSED".equalsIgnoreCase(match.getStatus()))
            .max(Comparator.comparing(GameSessionEntity::getMatchId));
    }

    private GameSessionEntity loadSessionOwnedByUser(Integer matchId, Integer userId) {
        GameSessionEntity session = gameSessionRepository.findByMatchIdForUpdate(matchId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match no encontrado"));

        if (!matchParticipantRepository.existsByIdMatchIdAndIdUserId(matchId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No perteneces a esta sala");
        }
        return session;
    }

    private void createParticipant(GameSessionEntity session, UserEntity user) {
        MatchParticipantEntity participant = new MatchParticipantEntity();
        participant.setId(new MatchParticipantId(session.getMatchId(), user.getUserId()));
        participant.setMatch(session);
        participant.setUser(user);
        participant.setCurrentHp(INITIAL_HP);
        participant.setAlive(true);
        participant.setMultiplierWon(randomMultiplier());
        matchParticipantRepository.save(participant);
    }

    private void ensureSufficientBalance(UserEntity user, String actionDescription) {
        if (user.getFiatBalance().compareTo(BALL_ENTRY_COST) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saldo insuficiente para " + actionDescription);
        }
    }

    private void debitBallEntry(UserEntity user) {
        user.setFiatBalance(user.getFiatBalance().subtract(BALL_ENTRY_COST).setScale(2, RoundingMode.HALF_UP));
        userRepository.save(user);
        logTransaction(user, "BALL_ENTRY", BALL_ENTRY_COST.negate(), "Entrada a sala de bolas");
    }

    private void persistCard(GameSessionEntity session, UserEntity owner, String cardType) {
        MatchCardEntity card = new MatchCardEntity();
        card.setMatch(session);
        card.setOwner(owner);
        card.setCardType(cardType);
        card.setCardValue(0);
        card.setUsed(true);
        matchCardRepository.save(card);
    }

    private void logEvent(GameSessionEntity session, String eventType, String message) {
        GameSessionEventEntity event = new GameSessionEventEntity();
        event.setMatch(session);
        event.setEventType(eventType);
        event.setMessage(message);
        gameSessionEventRepository.save(event);
    }

    private void logTransaction(UserEntity user, String type, BigDecimal amount, String description) {
        TransactionLogEntity log = new TransactionLogEntity();
        log.setUser(user);
        log.setType(type);
        log.setAmountFiat(amount.setScale(2, RoundingMode.HALF_UP));
        log.setDescription(description);
        transactionLogRepository.save(log);
    }

    private TokenEntity resolveToken(String tokenAlias) {
        String normalized = tokenAlias == null ? "" : tokenAlias.trim().toUpperCase(Locale.ROOT);
        String tokenName = switch (normalized) {
            case "ROJA", "FRO", "FICHA ROJA" -> "Ficha Roja";
            case "AZUL", "FAZ", "FICHA AZUL" -> "Ficha Azul";
            case "VERDE", "FVD", "FICHA VERDE" -> "Ficha Verde";
            case "DORADA", "FGD", "FICHA DORADA" -> "Ficha Dorada";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token no valido: " + tokenAlias);
        };
        return tokenRepository.findByNameIgnoreCase(tokenName)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Token no encontrado"));
    }

    private UserWalletEntity findOrCreateWallet(UserEntity user, TokenEntity token) {
        UserWalletId id = new UserWalletId(user.getUserId(), token.getTokenId());
        return userWalletRepository.findById(id).orElseGet(() -> {
            UserWalletEntity wallet = new UserWalletEntity();
            wallet.setId(id);
            wallet.setUser(user);
            wallet.setToken(token);
            wallet.setQuantity(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
            return wallet;
        });
    }

    private boolean isMultiplierVisible(String sessionStatus) {
        return "REVEALED".equalsIgnoreCase(sessionStatus)
            || "IN_PROGRESS".equalsIgnoreCase(sessionStatus)
            || "FINISHED".equalsIgnoreCase(sessionStatus)
            || "CLOSED".equalsIgnoreCase(sessionStatus);
    }

    private String normalizeAction(String action) {
        if (action == null) {
            return "ATTACK";
        }
        String normalized = action.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("ATTACK") && !normalized.equals("SHIELD") && !normalized.equals("REBOUND")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Accion invalida");
        }
        return normalized;
    }

    private String randomAction() {
        double value = randomProvider.nextDouble();
        if (value < 0.55) {
            return "ATTACK";
        }
        if (value < 0.80) {
            return "SHIELD";
        }
        return "REBOUND";
    }

    private BigDecimal randomMultiplier() {
        double random = randomProvider.nextDouble();
        double skewed = Math.pow(random, 2.8);
        double value = 0.5 + (skewed * (100.0 - 0.5));
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private String formatMultiplier(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private TokenMeta tokenMeta(String tokenName, String colorCode) {
        String normalized = tokenName == null ? "" : tokenName.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "FICHA ROJA" -> new TokenMeta("FRO", colorCode == null ? "#FF0000" : colorCode);
            case "FICHA AZUL" -> new TokenMeta("FAZ", colorCode == null ? "#0000FF" : colorCode);
            case "FICHA VERDE" -> new TokenMeta("FVD", colorCode == null ? "#00FF00" : colorCode);
            case "FICHA DORADA" -> new TokenMeta("FGD", colorCode == null ? "#FFD700" : colorCode);
            default -> {
                String ticker = tokenName == null ? "TOK" : tokenName.replace("Ficha", "").trim().toUpperCase(Locale.ROOT);
                ticker = ticker.length() >= 3 ? ticker.substring(0, 3) : ticker;
                yield new TokenMeta(ticker, colorCode == null ? "#FFFFFF" : colorCode);
            }
        };
    }

    private record TokenMeta(String ticker, String colorCode) {
    }
}
