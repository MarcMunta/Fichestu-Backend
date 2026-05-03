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
    private static final BigDecimal MIN_TOKEN_PRICE = new BigDecimal("0.50");
    private static final BigDecimal MAX_TOKEN_PRICE = new BigDecimal("5000.00");
    private static final BigDecimal MAX_MARKET_IMPACT_MULTIPLIER = new BigDecimal("3.00");
    private static final double MIN_BALL_MULTIPLIER = 0.50;
    private static final double MAX_BALL_MULTIPLIER = 3.00;
    private static final int ROOM_SIZE = 10;
    private static final int BALL_COUNT = 50;
    private static final int INITIAL_HP = 50;
    private static final int MATCHMAKING_SECONDS = 15;
    private static final String TYPE_REWARDED = "REWARDED";
    private static final String EVENT_ROUND_SUMMARY = "ROUND_SUMMARY";
    private static final String STATUS_MATCHMAKING = "MATCHMAKING";
    private static final String STATUS_PICKING = "PICKING";
    private static final String STATUS_CLOSED = "CLOSED";

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
    private final NotificationService notificationService;

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
        RandomProvider randomProvider,
        NotificationService notificationService
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
        this.notificationService = notificationService;
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
            abandonSessionForReentry(user, currentSession.get());
        }

        Optional<GameSessionEntity> availableSession = findAvailableMatchmakingSession();
        if (availableSession.isPresent()) {
            return joinAvailableMatchmakingSession(user, availableSession.get().getMatchId());
        }

        ensureSufficientBalance(user, "entrar en la sala");
        debitBallEntry(user);

        GameSessionEntity session = new GameSessionEntity();
        session.setStatus(STATUS_MATCHMAKING);
        session.setMatchmakingDeadline(Instant.now().plusSeconds(MATCHMAKING_SECONDS));
        session = gameSessionRepository.save(session);

        createParticipant(session, user);
        logEvent(session, "MATCHMAKING_STARTED", user.getUsername() + " ha pagado la entrada y entra en matchmaking.");
        notificationService.create(
            user,
            "Matchmaking iniciado",
            "Entrada pagada. Buscando jugadores para la sala de bolas.",
            "MATCHMAKING"
        );

        return new EnterBallRoomResponse(
            "Entrada pagada. Buscando jugadores",
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
            resolveMatchmakingIfReady(existing);
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
        resolveMatchmakingIfReady(session);

        long currentPlayers = matchParticipantRepository.countByIdMatchId(matchId);
        if (currentPlayers >= ROOM_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La sala ya esta llena");
        }
        if (!STATUS_MATCHMAKING.equalsIgnoreCase(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La sala ya no acepta jugadores");
        }

        ensureSufficientBalance(user, "unirte a la sala");
        debitBallEntry(user);
        createParticipant(session, user);

        long newCount = matchParticipantRepository.countByIdMatchId(matchId);
        logEvent(session, "PLAYER_JOINED", user.getUsername() + " se ha unido al matchmaking.");
        notificationService.create(
            user,
            "Te has unido a la sala",
            "Entrada pagada. Esperando que el matchmaking termine.",
            "MATCHMAKING"
        );
        if (newCount >= ROOM_SIZE) {
            resolveMatchmakingIfReady(session);
        } else {
            session.setMatchmakingDeadline(Instant.now().plusSeconds(MATCHMAKING_SECONDS));
            gameSessionRepository.save(session);
        }

        return new EnterBallRoomResponse(
            "Te has unido a la sala",
            true,
            session.getMatchId(),
            user.getFiatBalance(),
            buildBallRoomDto(session, user.getUserId())
        );
    }

    @Transactional
    public EnterBallRoomResponse cancelMatchmaking(Integer matchId) {
        UserEntity user = currentUserService.requireUserEntity();
        GameSessionEntity session = loadSessionOwnedByUser(matchId, user.getUserId());

        if (!STATUS_MATCHMAKING.equalsIgnoreCase(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El matchmaking ya no se puede cancelar");
        }

        MatchParticipantEntity participant = matchParticipantRepository.findById(new MatchParticipantId(matchId, user.getUserId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No perteneces a esta sala"));
        matchParticipantRepository.delete(participant);
        refundBallEntry(user);
        logEvent(session, "MATCHMAKING_CANCELLED", user.getUsername() + " ha cancelado el matchmaking.");
        notificationService.create(
            user,
            "Matchmaking cancelado",
            "Matchmaking cancelado",
            "MATCHMAKING_CANCELLED"
        );
        closeIfMatchmakingIsEmpty(session);

        return new EnterBallRoomResponse(
            "Matchmaking cancelado",
            true,
            null,
            user.getFiatBalance(),
            new BallRoomDto("WAITING_ENTRY", "Matchmaking cancelado", false, null, List.of(), List.of())
        );
    }

    @Transactional
    public EnterBallRoomResponse abandonMatchmaking(Integer matchId) {
        UserEntity user = currentUserService.requireUserEntity();
        GameSessionEntity session = loadSessionOwnedByUser(matchId, user.getUserId());

        if (!STATUS_MATCHMAKING.equalsIgnoreCase(session.getStatus())) {
            return new EnterBallRoomResponse(
                "La sala ya ha avanzado",
                true,
                session.getMatchId(),
                user.getFiatBalance(),
                buildBallRoomDto(session, user.getUserId())
            );
        }

        MatchParticipantEntity participant = matchParticipantRepository.findById(new MatchParticipantId(matchId, user.getUserId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No perteneces a esta sala"));
        matchParticipantRepository.delete(participant);
        logEvent(session, "MATCHMAKING_ABANDONED", user.getUsername() + " ha abandonado el matchmaking.");
        closeIfMatchmakingIsEmpty(session);

        return new EnterBallRoomResponse(
            "Has abandonado el matchmaking",
            true,
            null,
            user.getFiatBalance(),
            new BallRoomDto("WAITING_ENTRY", "Has abandonado la sala. La entrada no se devuelve al salir de la app.", false, null, List.of(), List.of())
        );
    }

    @Transactional
    public EnterBallRoomResponse abandonMatch(Integer matchId) {
        UserEntity user = currentUserService.requireUserEntity();
        GameSessionEntity session = loadSessionOwnedByUser(matchId, user.getUserId());

        if (STATUS_CLOSED.equalsIgnoreCase(session.getStatus()) || "FINISHED".equalsIgnoreCase(session.getStatus())) {
            return new EnterBallRoomResponse(
                "La partida ya estaba cerrada",
                true,
                null,
                user.getFiatBalance(),
                new BallRoomDto("WAITING_ENTRY", "No hay partida activa.", false, null, List.of(), List.of())
            );
        }

        MatchParticipantEntity participant = matchParticipantRepository.findById(new MatchParticipantId(matchId, user.getUserId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No perteneces a esta sala"));

        if (STATUS_MATCHMAKING.equalsIgnoreCase(session.getStatus())) {
            matchParticipantRepository.delete(participant);
            logEvent(session, "MATCH_ABANDONED", user.getUsername() + " ha abandonado el matchmaking.");
            closeIfMatchmakingIsEmpty(session);
            return new EnterBallRoomResponse(
                "Has abandonado la sala",
                true,
                null,
                user.getFiatBalance(),
                new BallRoomDto("WAITING_ENTRY", "Has salido de la partida. La entrada no se devuelve al salir de la app.", false, null, List.of(), List.of())
            );
        }

        UserEntity bot = findReplacementBotUser(session);
        MatchParticipantEntity replacement = new MatchParticipantEntity();
        replacement.setId(new MatchParticipantId(matchId, bot.getUserId()));
        replacement.setMatch(session);
        replacement.setUser(bot);
        replacement.setSelectedBallNumber(participant.getSelectedBallNumber());
        replacement.setMultiplierWon(participant.getMultiplierWon());
        replacement.setCurrentHp(participant.getCurrentHp());
        replacement.setAlive(participant.getAlive());

        matchParticipantRepository.delete(participant);
        matchParticipantRepository.save(replacement);
        List<MatchParticipantEntity> participants = matchParticipantRepository.findByIdMatchId(matchId);
        if (STATUS_PICKING.equalsIgnoreCase(session.getStatus())) {
            assignBotBallPicks(session, participants);
            if (matchParticipantRepository.countByIdMatchIdAndSelectedBallNumberIsNotNull(matchId) >= ROOM_SIZE) {
                session.setStatus("READY_REVEAL");
                gameSessionRepository.save(session);
            }
        }
        closeIfOnlyBotsRemain(session);
        logEvent(session, "MATCH_ABANDONED", user.getUsername() + " ha salido. Un bot ocupa su plaza.");

        return new EnterBallRoomResponse(
            "Has salido de la partida",
            true,
            null,
            user.getFiatBalance(),
            new BallRoomDto("WAITING_ENTRY", "Has salido de la partida. La entrada no se devuelve al salir de la app.", false, null, List.of(), List.of())
        );
    }

    @Transactional
    public MatchStateResponse currentMatchState() {
        UserEntity user = currentUserService.requireUserEntity();
        Optional<GameSessionEntity> sessionOptional = findCurrentSession(user.getUserId());

        if (sessionOptional.isEmpty()) {
            return new MatchStateResponse(
                "No hay match activo",
                true,
                null,
                new BallRoomDto("WAITING_ENTRY", "Crea o unete a una sala para empezar.", false, null, List.of(), List.of()),
                new BattleDto("LOCKED", 0, null, null, null, "ATTACK", true, List.of("Todavia no hay partida activa."), List.of())
            );
        }

        GameSessionEntity session = sessionOptional.get();
        resolveMatchmakingIfReady(session);
        return buildMatchStateResponse(session, user.getUserId(), "Estado del match cargado", null);
    }

    @Transactional
    public MatchStateResponse pickBall(Integer matchId, Integer ballId) {
        UserEntity user = currentUserService.requireUserEntity();
        GameSessionEntity session = loadSessionOwnedByUser(matchId, user.getUserId());
        resolveMatchmakingIfReady(session);

        if (!STATUS_PICKING.equalsIgnoreCase(session.getStatus())) {
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
        notificationService.create(
            user,
            "Bola confirmada",
            "Has elegido la bola " + ballId + ".",
            "BALL_PICKED"
        );

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
        resolveMatchmakingIfReady(session);

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
            notificationService.create(
                user,
                "Multiplicadores revelados",
                "Ya puedes pasar al Battle Royale.",
                "BALL_REVEAL"
            );
        }

        return buildMatchStateResponse(session, user.getUserId(), "Multiplicadores revelados", null);
    }

    @Transactional
    public MatchStateResponse playBattleRound(Integer matchId, String action, Integer cardPower, Integer targetUserId, String selectedTokenAlias) {
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
        Map<Integer, Integer> damageByUser = new HashMap<>();

        for (MatchParticipantEntity participant : participants) {
            if (!Boolean.TRUE.equals(participant.getAlive())) {
                continue;
            }

            String chosenAction = participant.getUser().getUserId().equals(user.getUserId())
                ? normalizeAction(action)
                : randomAction();
            int chosenPower = participant.getUser().getUserId().equals(user.getUserId())
                ? normalizeCardPower(cardPower)
                : randomBattlePower();

            actions.put(participant.getUser().getUserId(), chosenAction);
            damageByUser.put(participant.getUser().getUserId(), chosenPower);
            persistCard(session, participant.getUser(), chosenAction);
        }

        Map<Integer, Integer> hpByUser = new HashMap<>();
        for (MatchParticipantEntity participant : participants) {
            hpByUser.put(participant.getUser().getUserId(), participant.getCurrentHp());
        }

        List<String> roundLogs = new ArrayList<>();
        int roundNumber = (int) gameSessionEventRepository.countByMatchMatchIdAndEventType(matchId, EVENT_ROUND_SUMMARY) + 1;

        for (MatchParticipantEntity participant : participants) {
            Integer participantId = participant.getUser().getUserId();
            if (!Boolean.TRUE.equals(participant.getAlive())) {
                continue;
            }
            String participantAction = actions.get(participantId);
            if ("SHIELD".equals(participantAction)) {
                roundLogs.add(participant.getUser().getUsername() + " usa defensa.");
            } else if ("REBOUND".equals(participantAction)) {
                roundLogs.add(participant.getUser().getUsername() + " prepara rebote.");
            }
        }

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

            MatchParticipantEntity target = resolveBattleTarget(attackerId, user.getUserId(), targetUserId, possibleTargets);
            int damage = damageByUser.getOrDefault(attackerId, randomBattlePower());
            Integer targetId = target.getUser().getUserId();

            if ("SHIELD".equals(actions.get(targetId))) {
                roundLogs.add(attacker.getUser().getUsername() + " ataca a " + target.getUser().getUsername() + " con " + damage + " de dano, pero defensa bloquea todo.");
                continue;
            }

            if ("REBOUND".equals(actions.get(targetId))) {
                int nextHp = Math.max(0, hpByUser.getOrDefault(attackerId, attacker.getCurrentHp()) - damage);
                hpByUser.put(attackerId, nextHp);
                roundLogs.add(attacker.getUser().getUsername() + " ataca a " + target.getUser().getUsername() + " con " + damage + " de dano, pero rebote devuelve ese dano a " + attacker.getUser().getUsername() + ".");
                continue;
            }

            int nextHp = Math.max(0, hpByUser.getOrDefault(targetId, target.getCurrentHp()) - damage);
            hpByUser.put(targetId, nextHp);
            roundLogs.add(attacker.getUser().getUsername() + " golpea a " + target.getUser().getUsername() + " por " + damage + " puntos.");
        }

        for (MatchParticipantEntity participant : participants) {
            boolean wasAlive = Boolean.TRUE.equals(participant.getAlive());
            int hp = hpByUser.getOrDefault(participant.getUser().getUserId(), participant.getCurrentHp());
            participant.setCurrentHp(hp);
            participant.setAlive(hp > 0);
            if (wasAlive && hp <= 0) {
                roundLogs.add(participant.getUser().getUsername() + " ha sido derrotado.");
            }
        }
        matchParticipantRepository.saveAll(participants);

        List<MatchParticipantEntity> aliveParticipants = participants.stream()
            .filter(participant -> Boolean.TRUE.equals(participant.getAlive()))
            .toList();

        logEvent(session, EVENT_ROUND_SUMMARY, String.format(Locale.ROOT, "Ronda %02d", roundNumber));
        for (String roundLog : roundLogs) {
            logEvent(session, "BATTLE_LOG", roundLog);
        }

        if (hasNoAliveHumanParticipants(aliveParticipants)) {
            session.setStatus("FINISHED");
            session.setEndTime(Instant.now());
            session.setWinner(null);
            gameSessionRepository.save(session);
            logEvent(session, "BATTLE_CLOSED", "Partida cerrada automaticamente: solo quedaban bots vivos.");
            notificationService.create(
                user,
                "Battle finalizada",
                "Has sido eliminado. La partida se ha cerrado porque no quedaban jugadores reales.",
                "BATTLE_FINISHED"
            );
            return buildMatchStateResponse(session, user.getUserId(), "Has sido eliminado", action);
        }

        if (aliveParticipants.size() <= 1) {
            session.setStatus("FINISHED");
            session.setEndTime(Instant.now());
            MatchParticipantEntity winner = aliveParticipants.isEmpty() ? null : aliveParticipants.get(0);
            session.setWinner(winner == null ? null : winner.getUser());
            gameSessionRepository.save(session);

            if (winner != null) {
                logEvent(session, "WINNER", "Ganador: " + winner.getUser().getUsername() + " con x" + formatMultiplier(winner.getMultiplierWon().doubleValue()) + ".");
                notificationService.create(
                    user,
                    "Battle finalizada",
                    "Ganador: " + winner.getUser().getUsername() + " con x" + formatMultiplier(winner.getMultiplierWon().doubleValue()) + ".",
                    winner.getUser().getUserId().equals(user.getUserId()) ? "BATTLE_WIN" : "BATTLE_FINISHED"
                );
                if (selectedTokenAlias != null && !selectedTokenAlias.isBlank()) {
                    applyWinnerImpactInternal(session, selectedTokenAlias, winner.getMultiplierWon(), winner.getUser());
                } else if (!Boolean.TRUE.equals(session.getImpactApplied())) {
                    logEvent(session, "WINNER_PENDING_IMPACT", "El ganador aun no ha elegido la ficha a impactar.");
                }
            } else {
                logEvent(session, "DRAW", "La partida termina en empate.");
                notificationService.create(
                    user,
                    "Battle finalizada",
                    "La partida termina en empate.",
                    "BATTLE_FINISHED"
                );
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
        notificationService.create(
            user,
            "Rewarded aplicado",
            "Has recibido " + REWARDED_AMOUNT + " FTC en tu saldo.",
            "REWARDED"
        );

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
            notificationService.create(
                user,
                "Compra realizada",
                "Has comprado " + quantity + " " + token.getName() + " por " + amount + " FTC.",
                "MARKET_BUY"
            );
        } else {
            if (wallet.getQuantity().compareTo(qty) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No tienes suficientes fichas para vender");
            }
            wallet.setQuantity(wallet.getQuantity().subtract(qty));
            user.setFiatBalance(user.getFiatBalance().add(amount).setScale(2, RoundingMode.HALF_UP));
            logTransaction(user, "SELL", amount, "Venta de " + quantity + " " + tokenAlias.toUpperCase(Locale.ROOT));
            notificationService.create(
                user,
                "Venta realizada",
                "Has vendido " + quantity + " " + token.getName() + " por " + amount + " FTC.",
                "MARKET_SELL"
            );
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
        BigDecimal marketMultiplier = multiplier.min(MAX_MARKET_IMPACT_MULTIPLIER);
        BigDecimal nextPrice = token.getCurrentPrice().multiply(marketMultiplier).setScale(2, RoundingMode.HALF_UP);
        if (nextPrice.compareTo(MIN_TOKEN_PRICE) < 0) {
            nextPrice = MIN_TOKEN_PRICE;
        }
        if (nextPrice.compareTo(MAX_TOKEN_PRICE) > 0) {
            nextPrice = MAX_TOKEN_PRICE;
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

        logEvent(session, "WINNER_IMPACT", triggeredBy.getUsername() + " aplica x" + formatMultiplier(marketMultiplier.doubleValue()) + " sobre " + token.getName() + ".");
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
                multiplierByBall.put(participant.getSelectedBallNumber(), safeMultiplier(participant.getMultiplierWon()));
            }
        }

        List<BallPlayerDto> players = participants.stream()
            .map(participant -> new BallPlayerDto(
                String.valueOf(participant.getUser().getUserId()),
                participant.getUser().getUserId().equals(userId) ? "Tu" : participant.getUser().getUsername(),
                participant.getUser().getUserId().equals(userId),
                participant.getSelectedBallNumber(),
                isMultiplierVisible(session.getStatus()) && participant.getMultiplierWon() != null
                    ? safeMultiplier(participant.getMultiplierWon())
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
        if (STATUS_MATCHMAKING.equalsIgnoreCase(session.getStatus())) {
            phase = "MATCHMAKING";
            statusMessage = "Buscando jugadores: " + participants.size() + "/" + ROOM_SIZE + ".";
        } else if ("WAITING".equalsIgnoreCase(session.getStatus())) {
            phase = "WAITING_PLAYERS";
            statusMessage = "Esperando jugadores: " + participants.size() + "/" + ROOM_SIZE + ".";
        } else if (STATUS_PICKING.equalsIgnoreCase(session.getStatus()) || "READY_REVEAL".equalsIgnoreCase(session.getStatus())) {
            long pickedCount = participants.stream()
                .filter(participant -> participant.getSelectedBallNumber() != null)
                .count();
            phase = "PICKING";
            statusMessage = canReveal
                ? "Todos eligieron. Revela multiplicadores para iniciar Battle Royale."
                : "Elige una bola unica. " + pickedCount + "/" + ROOM_SIZE + " jugadores listos.";
        } else if (isMultiplierVisible(session.getStatus())) {
            phase = "REVEALED";
            Optional<MatchParticipantEntity> me = participants.stream()
                .filter(participant -> participant.getUser().getUserId().equals(userId))
                .findFirst();
            double myMultiplier = me.map(value -> safeMultiplier(value.getMultiplierWon())).orElse(1.0);
            statusMessage = "Tu multiplicador es x" + formatMultiplier(myMultiplier) + ". Pasa al Battle Royale.";
        } else {
            phase = "WAITING_ENTRY";
            statusMessage = "Crea o unete a una sala para empezar.";
        }

        Long deadlineEpochMs = session.getMatchmakingDeadline() == null ? null : session.getMatchmakingDeadline().toEpochMilli();
        return new BallRoomDto(phase, statusMessage, canReveal, deadlineEpochMs, players, balls);
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
                safeMultiplier(participant.getMultiplierWon()),
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
                .map(participant -> safeMultiplier(participant.getMultiplierWon()))
                .findFirst()
                .orElse(1.0);
        }

        List<String> log = gameSessionEventRepository.findTop80ByMatchMatchIdOrderByEventIdDesc(session.getMatchId()).stream()
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
            .filter(participant -> {
                GameSessionEntity match = participant.getMatch();
                if (match == null || STATUS_CLOSED.equalsIgnoreCase(match.getStatus())) {
                    return false;
                }
                boolean battleAlreadyStarted = "IN_PROGRESS".equalsIgnoreCase(match.getStatus())
                    || "FINISHED".equalsIgnoreCase(match.getStatus());
                return !battleAlreadyStarted || Boolean.TRUE.equals(participant.getAlive());
            })
            .map(MatchParticipantEntity::getMatch)
            .max(Comparator.comparing(GameSessionEntity::getMatchId));
    }

    private Optional<GameSessionEntity> findAvailableMatchmakingSession() {
        Instant now = Instant.now();
        return gameSessionRepository.findByStatusOrderByMatchIdAsc(STATUS_MATCHMAKING).stream()
            .filter(session -> session.getMatchmakingDeadline() == null || session.getMatchmakingDeadline().isAfter(now))
            .filter(session -> matchParticipantRepository.countByIdMatchId(session.getMatchId()) < ROOM_SIZE)
            .findFirst();
    }

    private EnterBallRoomResponse joinAvailableMatchmakingSession(UserEntity user, Integer matchId) {
        GameSessionEntity session = gameSessionRepository.findByMatchIdForUpdate(matchId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match no encontrado"));
        resolveMatchmakingIfReady(session);

        long currentPlayers = matchParticipantRepository.countByIdMatchId(matchId);
        if (currentPlayers >= ROOM_SIZE || !STATUS_MATCHMAKING.equalsIgnoreCase(session.getStatus())) {
            return enterBallRoom();
        }

        ensureSufficientBalance(user, "unirte a la sala");
        debitBallEntry(user);
        createParticipant(session, user);

        long newCount = matchParticipantRepository.countByIdMatchId(matchId);
        logEvent(session, "PLAYER_JOINED", user.getUsername() + " se ha unido al matchmaking.");
        if (newCount >= ROOM_SIZE) {
            resolveMatchmakingIfReady(session);
        } else {
            session.setMatchmakingDeadline(Instant.now().plusSeconds(MATCHMAKING_SECONDS));
            gameSessionRepository.save(session);
        }

        return new EnterBallRoomResponse(
            "Te has unido a una sala abierta",
            true,
            session.getMatchId(),
            user.getFiatBalance(),
            buildBallRoomDto(session, user.getUserId())
        );
    }

    private GameSessionEntity loadSessionOwnedByUser(Integer matchId, Integer userId) {
        GameSessionEntity session = gameSessionRepository.findByMatchIdForUpdate(matchId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match no encontrado"));

        if (!matchParticipantRepository.existsByIdMatchIdAndIdUserId(matchId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No perteneces a esta sala");
        }
        return session;
    }

    private void abandonSessionForReentry(UserEntity user, GameSessionEntity session) {
        if (STATUS_CLOSED.equalsIgnoreCase(session.getStatus()) || "FINISHED".equalsIgnoreCase(session.getStatus())) {
            return;
        }

        MatchParticipantEntity participant = matchParticipantRepository.findById(new MatchParticipantId(session.getMatchId(), user.getUserId()))
            .orElse(null);
        if (participant == null) {
            return;
        }

        if (STATUS_MATCHMAKING.equalsIgnoreCase(session.getStatus()) || "WAITING".equalsIgnoreCase(session.getStatus())) {
            matchParticipantRepository.delete(participant);
            logEvent(session, "MATCH_ABANDONED", user.getUsername() + " ha abandonado una sala anterior al volver a entrar.");
            closeIfMatchmakingIsEmpty(session);
            return;
        }

        UserEntity bot = findReplacementBotUser(session);
        MatchParticipantEntity replacement = new MatchParticipantEntity();
        replacement.setId(new MatchParticipantId(session.getMatchId(), bot.getUserId()));
        replacement.setMatch(session);
        replacement.setUser(bot);
        replacement.setSelectedBallNumber(participant.getSelectedBallNumber());
        replacement.setMultiplierWon(participant.getMultiplierWon());
        replacement.setCurrentHp(participant.getCurrentHp());
        replacement.setAlive(participant.getAlive());

        matchParticipantRepository.delete(participant);
        matchParticipantRepository.save(replacement);

        List<MatchParticipantEntity> participants = matchParticipantRepository.findByIdMatchId(session.getMatchId());
        if (STATUS_PICKING.equalsIgnoreCase(session.getStatus())) {
            assignBotBallPicks(session, participants);
            if (matchParticipantRepository.countByIdMatchIdAndSelectedBallNumberIsNotNull(session.getMatchId()) >= ROOM_SIZE) {
                session.setStatus("READY_REVEAL");
                gameSessionRepository.save(session);
            }
        }
        closeIfOnlyBotsRemain(session);
        logEvent(session, "MATCH_ABANDONED", user.getUsername() + " ha vuelto a entrar. Un bot ocupa su plaza anterior.");
    }

    private void resolveMatchmakingIfReady(GameSessionEntity session) {
        if ("WAITING".equalsIgnoreCase(session.getStatus())) {
            session.setStatus(STATUS_MATCHMAKING);
            if (session.getMatchmakingDeadline() == null) {
                session.setMatchmakingDeadline(Instant.now().plusSeconds(MATCHMAKING_SECONDS));
            }
            gameSessionRepository.save(session);
        }
        if (!STATUS_MATCHMAKING.equalsIgnoreCase(session.getStatus())) {
            return;
        }

        List<MatchParticipantEntity> participants = matchParticipantRepository.findByIdMatchId(session.getMatchId());
        boolean isFull = participants.size() >= ROOM_SIZE;
        boolean isExpired = session.getMatchmakingDeadline() != null && !session.getMatchmakingDeadline().isAfter(Instant.now());
        if (!isFull && !isExpired) {
            return;
        }

        if (!isFull) {
            fillRoomWithBots(session, participants);
            participants = matchParticipantRepository.findByIdMatchId(session.getMatchId());
        }

        assignBotBallPicks(session, participants);
        session.setStatus(STATUS_PICKING);
        gameSessionRepository.save(session);
        logEvent(session, "SELECTION_STARTED", "Matchmaking completado. Empieza la seleccion de bolas.");
    }

    private void fillRoomWithBots(GameSessionEntity session, List<MatchParticipantEntity> participants) {
        Set<Integer> currentUserIds = participants.stream()
            .map(participant -> participant.getUser().getUserId())
            .collect(HashSet::new, HashSet::add, HashSet::addAll);

        int slot = 1;
        while (currentUserIds.size() < ROOM_SIZE) {
            UserEntity bot = findOrCreateBotUser(session.getMatchId(), slot);
            slot++;
            if (currentUserIds.contains(bot.getUserId())) {
                continue;
            }
            createParticipant(session, bot);
            currentUserIds.add(bot.getUserId());
        }
        logEvent(session, "BOTS_FILLED", "Se han completado los huecos con bots.");
    }

    private UserEntity findOrCreateBotUser(Integer matchId, int slot) {
        String username = "Bot_" + matchId + "_" + slot;
        return userRepository.findByUsername(username).orElseGet(() -> {
            UserEntity bot = new UserEntity();
            bot.setUsername(username);
            bot.setEmail("bot+" + matchId + "_" + slot + "@fichestu.local");
            bot.setPasswordHash("BOT");
            bot.setRole("BOT");
            bot.setFiatBalance(BigDecimal.ZERO);
            return userRepository.save(bot);
        });
    }

    private UserEntity findReplacementBotUser(GameSessionEntity session) {
        int slot = ROOM_SIZE + 1;
        while (true) {
            UserEntity bot = findOrCreateBotUser(session.getMatchId(), slot);
            if (!matchParticipantRepository.existsByIdMatchIdAndIdUserId(session.getMatchId(), bot.getUserId())) {
                return bot;
            }
            slot++;
        }
    }

    private void assignBotBallPicks(GameSessionEntity session, List<MatchParticipantEntity> participants) {
        Set<Integer> pickedNumbers = participants.stream()
            .map(MatchParticipantEntity::getSelectedBallNumber)
            .filter(value -> value != null)
            .collect(HashSet::new, HashSet::add, HashSet::addAll);

        int nextBall = 1;
        for (MatchParticipantEntity participant : participants) {
            if (!"BOT".equalsIgnoreCase(participant.getUser().getRole()) || participant.getSelectedBallNumber() != null) {
                continue;
            }
            while (pickedNumbers.contains(nextBall) && nextBall <= BALL_COUNT) {
                nextBall++;
            }
            if (nextBall <= BALL_COUNT) {
                participant.setSelectedBallNumber(nextBall);
                pickedNumbers.add(nextBall);
                matchParticipantRepository.save(participant);
            }
        }
        logEvent(session, "BOT_BALLS_PICKED", "Los bots han elegido sus bolas automaticamente.");
    }

    private void closeIfMatchmakingIsEmpty(GameSessionEntity session) {
        if (matchParticipantRepository.countByIdMatchId(session.getMatchId()) > 0) {
            return;
        }
        session.setStatus(STATUS_CLOSED);
        session.setEndTime(Instant.now());
        gameSessionRepository.save(session);
    }

    private void closeIfOnlyBotsRemain(GameSessionEntity session) {
        if (STATUS_CLOSED.equalsIgnoreCase(session.getStatus()) || "FINISHED".equalsIgnoreCase(session.getStatus())) {
            return;
        }
        List<MatchParticipantEntity> participants = matchParticipantRepository.findByIdMatchId(session.getMatchId());
        if (participants.isEmpty() || participants.stream().anyMatch(participant -> !isBotUser(participant.getUser()))) {
            return;
        }
        session.setStatus("FINISHED");
        session.setEndTime(Instant.now());
        session.setWinner(null);
        gameSessionRepository.save(session);
        logEvent(session, "BATTLE_CLOSED", "Partida cerrada automaticamente: solo quedaban bots.");
    }

    private boolean hasNoAliveHumanParticipants(List<MatchParticipantEntity> aliveParticipants) {
        return aliveParticipants.stream().noneMatch(participant -> !isBotUser(participant.getUser()));
    }

    private boolean isBotUser(UserEntity user) {
        return user != null && "BOT".equalsIgnoreCase(user.getRole());
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

    private void refundBallEntry(UserEntity user) {
        user.setFiatBalance(user.getFiatBalance().add(BALL_ENTRY_COST).setScale(2, RoundingMode.HALF_UP));
        userRepository.save(user);
        logTransaction(user, "BALL_ENTRY_REFUND", BALL_ENTRY_COST, "Devolucion por cancelar matchmaking");
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

    private int normalizeCardPower(Integer cardPower) {
        int fallback = randomBattlePower();
        if (cardPower == null) {
            return fallback;
        }
        return Math.max(1, Math.min(10, cardPower));
    }

    private int randomBattlePower() {
        return 1 + randomProvider.nextInt(10);
    }

    private MatchParticipantEntity resolveBattleTarget(
        Integer attackerId,
        Integer currentUserId,
        Integer requestedTargetUserId,
        List<MatchParticipantEntity> possibleTargets
    ) {
        if (attackerId.equals(currentUserId) && requestedTargetUserId != null) {
            return possibleTargets.stream()
                .filter(participant -> participant.getUser().getUserId().equals(requestedTargetUserId))
                .findFirst()
                .orElseGet(() -> possibleTargets.get(randomProvider.nextInt(possibleTargets.size())));
        }
        return possibleTargets.get(randomProvider.nextInt(possibleTargets.size()));
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
        double value = MIN_BALL_MULTIPLIER + (skewed * (MAX_BALL_MULTIPLIER - MIN_BALL_MULTIPLIER));
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private double safeMultiplier(BigDecimal multiplier) {
        if (multiplier == null) {
            return 1.0;
        }
        return multiplier
            .max(BigDecimal.valueOf(MIN_BALL_MULTIPLIER))
            .min(BigDecimal.valueOf(MAX_BALL_MULTIPLIER))
            .doubleValue();
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
