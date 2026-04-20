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
import com.example.fichestu.api.GameDtos.MatchStateResponse;
import com.example.fichestu.api.GameDtos.ProfileStatsDto;
import com.example.fichestu.api.GameDtos.TokenDto;
import com.example.fichestu.api.GameDtos.WalletResponse;
import com.example.fichestu.persistence.entity.GameSessionEntity;
import com.example.fichestu.persistence.entity.MatchCardEntity;
import com.example.fichestu.persistence.entity.MatchParticipantEntity;
import com.example.fichestu.persistence.entity.MatchParticipantId;
import com.example.fichestu.persistence.entity.TokenEntity;
import com.example.fichestu.persistence.entity.TokenPriceHistoryEntity;
import com.example.fichestu.persistence.entity.TransactionLogEntity;
import com.example.fichestu.persistence.entity.UserEntity;
import com.example.fichestu.persistence.entity.UserWalletEntity;
import com.example.fichestu.persistence.entity.UserWalletId;
import com.example.fichestu.persistence.repository.GameSessionRepository;
import com.example.fichestu.persistence.repository.MatchCardRepository;
import com.example.fichestu.persistence.repository.MatchParticipantRepository;
import com.example.fichestu.persistence.repository.TokenPriceHistoryRepository;
import com.example.fichestu.persistence.repository.TokenRepository;
import com.example.fichestu.persistence.repository.TransactionLogRepository;
import com.example.fichestu.persistence.repository.UserRepository;
import com.example.fichestu.persistence.repository.UserWalletRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GameService {

    private static final BigDecimal BALL_ENTRY_COST = new BigDecimal("10.00");
    private static final int INITIAL_HP = 50;
    private static final String TYPE_REWARDED = "REWARDED";
    private static final List<String> BOT_NAMES = List.of("Rayo", "Magma", "Nova", "Loki", "Bora", "Pik", "Kron", "Vela", "Tora");
    private static final Set<String> FINISHED_STATES = Set.of("FINISHED", "CLOSED");

    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final UserWalletRepository userWalletRepository;
    private final TokenPriceHistoryRepository tokenPriceHistoryRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final GameSessionRepository gameSessionRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final MatchCardRepository matchCardRepository;

    private final Map<Integer, List<String>> battleLogsByMatch = new ConcurrentHashMap<>();

    public GameService(
        UserRepository userRepository,
        TokenRepository tokenRepository,
        UserWalletRepository userWalletRepository,
        TokenPriceHistoryRepository tokenPriceHistoryRepository,
        TransactionLogRepository transactionLogRepository,
        GameSessionRepository gameSessionRepository,
        MatchParticipantRepository matchParticipantRepository,
        MatchCardRepository matchCardRepository
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.userWalletRepository = userWalletRepository;
        this.tokenPriceHistoryRepository = tokenPriceHistoryRepository;
        this.transactionLogRepository = transactionLogRepository;
        this.gameSessionRepository = gameSessionRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.matchCardRepository = matchCardRepository;
    }

    @Transactional
    public BootstrapResponse bootstrap(String authToken) {
        UserEntity user = resolveUser(authToken);
        tickMarketPrices();
        List<TokenDto> tokens = buildTokenDtos(user);
        ProfileStatsDto stats = buildProfileStats(user.getUserId());

        return new BootstrapResponse(
            "Estado inicial cargado",
            true,
            user.getUserId(),
            user.getUsername(),
            user.getFiatBalance(),
            0,
            tokens,
            List.of(),
            stats
        );
    }

    @Transactional
    public WalletResponse buy(String authToken, String tokenAlias, int quantity) {
        return trade(authToken, tokenAlias, quantity, true);
    }

    @Transactional
    public WalletResponse sell(String authToken, String tokenAlias, int quantity) {
        return trade(authToken, tokenAlias, quantity, false);
    }

    @Transactional
    public EnterBallRoomResponse enterBallRoom(String authToken) {
        UserEntity user = resolveUser(authToken);

        if (user.getFiatBalance().compareTo(BALL_ENTRY_COST) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saldo insuficiente para entrar en la sala");
        }

        closeActiveSessionIfAny(user.getUserId());

        user.setFiatBalance(user.getFiatBalance().subtract(BALL_ENTRY_COST).setScale(2, RoundingMode.HALF_UP));
        userRepository.save(user);

        GameSessionEntity session = new GameSessionEntity();
        session.setStatus("PICKING");
        session = gameSessionRepository.save(session);

        List<UserEntity> participants = new ArrayList<>();
        participants.add(user);
        participants.addAll(ensureBotUsers());

        for (UserEntity participantUser : participants) {
            MatchParticipantEntity participant = new MatchParticipantEntity();
            participant.setId(new MatchParticipantId(session.getMatchId(), participantUser.getUserId()));
            participant.setMatch(session);
            participant.setUser(participantUser);
            participant.setCurrentHp(INITIAL_HP);
            participant.setAlive(true);
            participant.setMultiplierWon(randomMultiplier());
            matchParticipantRepository.save(participant);
        }

        logTransaction(user, "BALL_ENTRY", BALL_ENTRY_COST.negate(), "Entrada a sala de bolas match=" + session.getMatchId());

        BallRoomDto ballRoom = buildBallRoomDto(session, user.getUserId());
        return new EnterBallRoomResponse(
            "Has entrado en la sala de bolas",
            true,
            session.getMatchId(),
            user.getFiatBalance(),
            ballRoom
        );
    }

    @Transactional(readOnly = true)
    public MatchStateResponse currentMatchState(String authToken) {
        UserEntity user = resolveUser(authToken);
        Optional<GameSessionEntity> sessionOptional = findCurrentSession(user.getUserId());

        if (sessionOptional.isEmpty()) {
            return new MatchStateResponse(
                "No hay match activo",
                true,
                null,
                new BallRoomDto("WAITING_ENTRY", "Paga EUR 10 para entrar en la sala.", false, List.of(), List.of()),
                new BattleDto("LOCKED", 0, null, null, null, "ATTACK", true, List.of("Completa primero el sorteo de bolas."), List.of())
            );
        }

        GameSessionEntity session = sessionOptional.get();
        BallRoomDto room = buildBallRoomDto(session, user.getUserId());
        BattleDto battle = buildBattleDto(session, user.getUserId(), null);
        return new MatchStateResponse("Estado del match cargado", true, session.getMatchId(), room, battle);
    }

    @Transactional
    public MatchStateResponse pickBall(String authToken, Integer matchId, Integer ballId) {
        UserEntity user = resolveUser(authToken);
        GameSessionEntity session = loadSessionOwnedByUser(matchId, user.getUserId());

        if (!"PICKING".equalsIgnoreCase(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La sala no esta en fase de seleccion");
        }

        List<MatchParticipantEntity> participants = matchParticipantRepository.findByIdMatchId(matchId);
        MatchParticipantEntity me = participants.stream()
            .filter(p -> p.getUser().getUserId().equals(user.getUserId()))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No perteneces a esta sala"));

        if (me.getSelectedBallNumber() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ya elegiste una bola");
        }

        Set<Integer> picked = participants.stream()
            .map(MatchParticipantEntity::getSelectedBallNumber)
            .filter(v -> v != null)
            .collect(java.util.stream.Collectors.toSet());

        if (picked.contains(ballId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esa bola ya fue tomada");
        }

        me.setSelectedBallNumber(ballId);
        matchParticipantRepository.save(me);
        picked.add(ballId);

        List<Integer> freeBalls = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            if (!picked.contains(i)) {
                freeBalls.add(i);
            }
        }
        Collections.shuffle(freeBalls);

        int freeIndex = 0;
        for (MatchParticipantEntity participant : participants) {
            if (!participant.getUser().getUserId().equals(user.getUserId()) && participant.getSelectedBallNumber() == null) {
                if (freeIndex < freeBalls.size()) {
                    participant.setSelectedBallNumber(freeBalls.get(freeIndex++));
                    matchParticipantRepository.save(participant);
                }
            }
        }

        boolean everyonePicked = matchParticipantRepository.findByIdMatchId(matchId).stream()
            .allMatch(p -> p.getSelectedBallNumber() != null);

        if (everyonePicked) {
            session.setStatus("READY_REVEAL");
            gameSessionRepository.save(session);
        }

        BallRoomDto room = buildBallRoomDto(session, user.getUserId());
        BattleDto battle = buildBattleDto(session, user.getUserId(), null);
        return new MatchStateResponse("Bola seleccionada", true, session.getMatchId(), room, battle);
    }

    @Transactional
    public MatchStateResponse revealMultipliers(String authToken, Integer matchId) {
        UserEntity user = resolveUser(authToken);
        GameSessionEntity session = loadSessionOwnedByUser(matchId, user.getUserId());

        if (!"READY_REVEAL".equalsIgnoreCase(session.getStatus()) && !"REVEALED".equalsIgnoreCase(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aun no se puede revelar");
        }

        session.setStatus("REVEALED");
        gameSessionRepository.save(session);

        BallRoomDto room = buildBallRoomDto(session, user.getUserId());
        BattleDto battle = buildBattleDto(session, user.getUserId(), null);
        return new MatchStateResponse("Multiplicadores revelados", true, session.getMatchId(), room, battle);
    }

    @Transactional
    public MatchStateResponse playBattleRound(String authToken, Integer matchId, String action, String selectedTokenAlias) {
        UserEntity user = resolveUser(authToken);
        GameSessionEntity session = loadSessionOwnedByUser(matchId, user.getUserId());

        if (!"REVEALED".equalsIgnoreCase(session.getStatus()) && !"IN_PROGRESS".equalsIgnoreCase(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Battle no desbloqueado");
        }

        session.setStatus("IN_PROGRESS");
        gameSessionRepository.save(session);

        List<MatchParticipantEntity> participants = matchParticipantRepository.findByIdMatchId(matchId);
        Map<Integer, String> actions = new HashMap<>();
        for (MatchParticipantEntity participant : participants) {
            if (Boolean.TRUE.equals(participant.getAlive())) {
                String card = participant.getUser().getUserId().equals(user.getUserId())
                    ? normalizeAction(action)
                    : randomAction();
                actions.put(participant.getUser().getUserId(), card);
                persistCard(session, participant.getUser(), card);
            }
        }

        List<String> roundLogs = new ArrayList<>();
        Map<Integer, Integer> hp = new HashMap<>();
        for (MatchParticipantEntity participant : participants) {
            hp.put(participant.getUser().getUserId(), participant.getCurrentHp());
        }

        for (MatchParticipantEntity attacker : participants) {
            Integer attackerId = attacker.getUser().getUserId();
            if (!Boolean.TRUE.equals(attacker.getAlive())) {
                continue;
            }
            if (!"ATTACK".equals(actions.get(attackerId))) {
                continue;
            }

            List<MatchParticipantEntity> targets = participants.stream()
                .filter(p -> !p.getUser().getUserId().equals(attackerId))
                .filter(p -> hp.getOrDefault(p.getUser().getUserId(), 0) > 0)
                .toList();

            if (targets.isEmpty()) {
                continue;
            }

            MatchParticipantEntity target = targets.get((int) (Math.random() * targets.size()));
            Integer targetId = target.getUser().getUserId();
            int damage = 1 + (int) (Math.random() * 9);
            String targetCard = actions.get(targetId);

            if ("SHIELD".equals(targetCard)) {
                roundLogs.add(attacker.getUser().getUsername() + " ataca " + target.getUser().getUsername() + " pero el escudo bloquea.");
            } else if ("REBOUND".equals(targetCard)) {
                int nextHp = Math.max(0, hp.get(attackerId) - damage);
                hp.put(attackerId, nextHp);
                roundLogs.add(target.getUser().getUsername() + " rebota " + damage + " a " + attacker.getUser().getUsername() + ".");
            } else {
                int nextHp = Math.max(0, hp.get(targetId) - damage);
                hp.put(targetId, nextHp);
                roundLogs.add(attacker.getUser().getUsername() + " golpea " + target.getUser().getUsername() + " por " + damage + ".");
            }
        }

        for (MatchParticipantEntity participant : participants) {
            int currentHp = hp.getOrDefault(participant.getUser().getUserId(), 0);
            participant.setCurrentHp(currentHp);
            participant.setAlive(currentHp > 0);
            matchParticipantRepository.save(participant);
        }

        List<MatchParticipantEntity> alive = matchParticipantRepository.findByIdMatchId(matchId).stream()
            .filter(p -> Boolean.TRUE.equals(p.getAlive()))
            .toList();

        if (alive.size() <= 1) {
            session.setStatus("FINISHED");
            session.setEndTime(Instant.now());
            if (alive.size() == 1) {
                session.setWinner(alive.get(0).getUser());
                roundLogs.add("Ganador: " + alive.get(0).getUser().getUsername());
                applyWinnerImpact(selectedTokenAlias, alive.get(0).getMultiplierWon());
            } else {
                roundLogs.add("Empate total: nadie sobrevive.");
            }
            gameSessionRepository.save(session);
        }

        appendBattleLogs(matchId, roundLogs);
        BallRoomDto room = buildBallRoomDto(session, user.getUserId());
        BattleDto battle = buildBattleDto(session, user.getUserId(), actions.get(user.getUserId()));
        return new MatchStateResponse("Ronda ejecutada", true, session.getMatchId(), room, battle);
    }

    @Transactional
    public CooldownResponse claimRewarded(String authToken) {
        UserEntity user = resolveUser(authToken);
        BigDecimal reward = new BigDecimal("25.00");
        user.setFiatBalance(user.getFiatBalance().add(reward).setScale(2, RoundingMode.HALF_UP));
        userRepository.save(user);
        logTransaction(user, TYPE_REWARDED, reward, "Rewarded ad completado");

        int claimed = (int) transactionLogRepository.countByUserUserIdAndType(user.getUserId(), TYPE_REWARDED);
        return new CooldownResponse(
            "Rewarded aplicado",
            true,
            user.getFiatBalance(),
            30,
            claimed
        );
    }

    @Transactional
    public GenericMessageResponse closeMatch(String authToken, Integer matchId) {
        UserEntity user = resolveUser(authToken);
        GameSessionEntity session = loadSessionOwnedByUser(matchId, user.getUserId());
        session.setStatus("CLOSED");
        session.setEndTime(Instant.now());
        gameSessionRepository.save(session);
        return new GenericMessageResponse("Match cerrado", true);
    }

    private WalletResponse trade(String authToken, String tokenAlias, int quantity, boolean isBuy) {
        UserEntity user = resolveUser(authToken);
        TokenEntity token = resolveToken(tokenAlias);
        UserWalletEntity wallet = findOrCreateWallet(user, token);
        BigDecimal qty = BigDecimal.valueOf(quantity).setScale(4, RoundingMode.HALF_UP);
        BigDecimal amount = token.getCurrentPrice().multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);

        if (isBuy) {
            if (user.getFiatBalance().compareTo(amount) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saldo insuficiente para comprar");
            }
            user.setFiatBalance(user.getFiatBalance().subtract(amount));
            wallet.setQuantity(wallet.getQuantity().add(qty));
            logTransaction(user, "BUY", amount.negate(), "Compra de " + quantity + " " + tokenAlias.toUpperCase(Locale.ROOT));
        } else {
            if (wallet.getQuantity().compareTo(qty) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No tienes suficientes fichas para vender");
            }
            wallet.setQuantity(wallet.getQuantity().subtract(qty));
            user.setFiatBalance(user.getFiatBalance().add(amount));
            logTransaction(user, "SELL", amount, "Venta de " + quantity + " " + tokenAlias.toUpperCase(Locale.ROOT));
        }

        user.setFiatBalance(user.getFiatBalance().setScale(2, RoundingMode.HALF_UP));
        userRepository.save(user);
        userWalletRepository.save(wallet);

        List<TokenDto> tokens = buildTokenDtos(user);
        BigDecimal total = user.getFiatBalance().add(tokens.stream()
            .map(tokenDto -> tokenDto.currentPrice().multiply(tokenDto.holdings()).setScale(2, RoundingMode.HALF_UP))
            .reduce(BigDecimal.ZERO, BigDecimal::add));

        return new WalletResponse(
            isBuy ? "Compra realizada" : "Venta realizada",
            true,
            user.getFiatBalance(),
            total.setScale(2, RoundingMode.HALF_UP),
            tokens
        );
    }

    private UserEntity resolveUser(String authToken) {
        String token = authToken == null ? "" : authToken.trim();
        if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }

        if (token.startsWith("user-")) {
            try {
                Integer userId = Integer.parseInt(token.substring("user-".length()));
                return userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesion invalida"));
            } catch (NumberFormatException ex) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesion invalida");
            }
        }

        return userRepository.findByUsername(token)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesion invalida"));
    }

    private TokenEntity resolveToken(String tokenAlias) {
        String normalized = tokenAlias == null ? "" : tokenAlias.trim().toUpperCase(Locale.ROOT);
        int tokenId = switch (normalized) {
            case "ROJA", "FRO" -> 1;
            case "AZUL", "FAZ" -> 2;
            case "VERDE", "FVD" -> 3;
            case "DORADA", "FGD" -> 4;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token no valido: " + tokenAlias);
        };
        return tokenRepository.findById(tokenId)
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

    private void tickMarketPrices() {
        List<TokenEntity> tokens = tokenRepository.findAllByOrderByTokenIdAsc();
        for (TokenEntity token : tokens) {
            double factor = 0.95 + (Math.random() * 0.13);
            BigDecimal nextPrice = token.getCurrentPrice()
                .multiply(BigDecimal.valueOf(factor))
                .setScale(2, RoundingMode.HALF_UP);
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
        }
    }

    private List<TokenDto> buildTokenDtos(UserEntity user) {
        Map<Integer, BigDecimal> walletByToken = new HashMap<>();
        for (UserWalletEntity wallet : userWalletRepository.findByIdUserId(user.getUserId())) {
            walletByToken.put(wallet.getToken().getTokenId(), wallet.getQuantity());
        }

        List<TokenDto> tokens = new ArrayList<>();
        for (TokenEntity token : tokenRepository.findAllByOrderByTokenIdAsc()) {
            List<TokenPriceHistoryEntity> histDesc = tokenPriceHistoryRepository.findTop28ByTokenTokenIdOrderByRecordedAtDesc(token.getTokenId());
            List<BigDecimal> history = histDesc.stream()
                .sorted(Comparator.comparing(TokenPriceHistoryEntity::getRecordedAt))
                .map(TokenPriceHistoryEntity::getPrice)
                .toList();

            BigDecimal previous = history.size() >= 2 ? history.get(history.size() - 2) : token.getCurrentPrice();
            TokenMeta meta = tokenMeta(token.getTokenId(), token.getName(), token.getColorCode());

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

    private ProfileStatsDto buildProfileStats(Integer userId) {
        List<MatchParticipantEntity> participations = matchParticipantRepository.findByIdUserId(userId);
        int ballRoomsPlayed = participations.size();
        int battlesPlayed = 0;
        int battlesWon = 0;
        double bestMultiplier = 1.0;
        double totalMultiplier = 0.0;
        int withMultiplier = 0;

        for (MatchParticipantEntity participation : participations) {
            GameSessionEntity session = participation.getMatch();
            if (session != null && FINISHED_STATES.contains(session.getStatus().toUpperCase(Locale.ROOT))) {
                battlesPlayed++;
                if (session.getWinner() != null && userId.equals(session.getWinner().getUserId())) {
                    battlesWon++;
                }
            }

            if (participation.getMultiplierWon() != null) {
                double m = participation.getMultiplierWon().doubleValue();
                bestMultiplier = Math.max(bestMultiplier, m);
                totalMultiplier += m;
                withMultiplier++;
            }
        }

        double average = withMultiplier == 0 ? 1.0 : totalMultiplier / withMultiplier;
        int rewardedAds = (int) transactionLogRepository.countByUserUserIdAndType(userId, TYPE_REWARDED);

        return new ProfileStatsDto(
            ballRoomsPlayed,
            battlesPlayed,
            battlesWon,
            round2(bestMultiplier),
            round2(average),
            rewardedAds
        );
    }

    private Optional<GameSessionEntity> findCurrentSession(Integer userId) {
        return matchParticipantRepository.findByIdUserId(userId).stream()
            .map(MatchParticipantEntity::getMatch)
            .filter(match -> match != null)
            .filter(match -> !FINISHED_STATES.contains(match.getStatus().toUpperCase(Locale.ROOT)))
            .max(Comparator.comparing(GameSessionEntity::getMatchId));
    }

    private GameSessionEntity loadSessionOwnedByUser(Integer matchId, Integer userId) {
        GameSessionEntity session = gameSessionRepository.findById(matchId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match no encontrado"));

        boolean belongs = matchParticipantRepository.findByIdMatchId(matchId).stream()
            .anyMatch(p -> p.getUser().getUserId().equals(userId));
        if (!belongs) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes operar este match");
        }
        return session;
    }

    private List<UserEntity> ensureBotUsers() {
        List<UserEntity> bots = new ArrayList<>();
        for (int i = 0; i < BOT_NAMES.size(); i++) {
            String username = BOT_NAMES.get(i);
            String email = "bot_" + (i + 1) + "@fichestu.local";
            UserEntity bot = userRepository.findByEmail(email).orElseGet(() -> {
                UserEntity user = new UserEntity();
                user.setUsername(username);
                user.setEmail(email);
                user.setPasswordHash("BOT");
                user.setRole("BOT");
                user.setFiatBalance(BigDecimal.ZERO);
                return userRepository.save(user);
            });
            bots.add(bot);
        }
        return bots;
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
            .map(p -> new BallPlayerDto(
                String.valueOf(p.getUser().getUserId()),
                p.getUser().getUserId().equals(userId) ? "Tu" : p.getUser().getUsername(),
                p.getUser().getUserId().equals(userId),
                p.getSelectedBallNumber(),
                "REVEALED".equalsIgnoreCase(session.getStatus()) || "IN_PROGRESS".equalsIgnoreCase(session.getStatus()) || "FINISHED".equalsIgnoreCase(session.getStatus())
                    ? p.getMultiplierWon().doubleValue()
                    : null
            ))
            .toList();

        List<BallOptionDto> balls = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            Integer ballId = i;
            String pickedBy = participants.stream()
                .filter(p -> ballId.equals(p.getSelectedBallNumber()))
                .map(p -> String.valueOf(p.getUser().getUserId()))
                .findFirst()
                .orElse(null);

            Double multiplier = ("REVEALED".equalsIgnoreCase(session.getStatus())
                || "IN_PROGRESS".equalsIgnoreCase(session.getStatus())
                || "FINISHED".equalsIgnoreCase(session.getStatus()))
                ? multiplierByBall.getOrDefault(i, 1.0)
                : null;

            balls.add(new BallOptionDto(i, multiplier, pickedBy));
        }

        boolean canReveal = participants.stream().allMatch(p -> p.getSelectedBallNumber() != null);

        String phase;
        String statusMessage;
        if ("PICKING".equalsIgnoreCase(session.getStatus()) || "READY_REVEAL".equalsIgnoreCase(session.getStatus())) {
            phase = "PICKING";
            statusMessage = canReveal
                ? "Todos eligieron. Revela multiplicadores para iniciar Battle Royale."
                : "Elige una bola. Cada jugador solo puede tomar una.";
        } else if ("REVEALED".equalsIgnoreCase(session.getStatus()) || "IN_PROGRESS".equalsIgnoreCase(session.getStatus()) || "FINISHED".equalsIgnoreCase(session.getStatus())) {
            phase = "REVEALED";
            Optional<MatchParticipantEntity> me = participants.stream().filter(p -> p.getUser().getUserId().equals(userId)).findFirst();
            double myMultiplier = me.map(value -> value.getMultiplierWon().doubleValue()).orElse(1.0);
            statusMessage = "Tu multiplicador es x" + formatMultiplier(myMultiplier) + ". Pasa al Battle Royale.";
        } else {
            phase = "WAITING_ENTRY";
            statusMessage = "Paga EUR 10 para entrar en la sala.";
        }

        return new BallRoomDto(phase, statusMessage, canReveal, players, balls);
    }

    private BattleDto buildBattleDto(GameSessionEntity session, Integer userId, String userSelectedAction) {
        List<MatchParticipantEntity> participants = matchParticipantRepository.findByIdMatchId(session.getMatchId());
        int round = (int) matchCardRepository.countByMatchMatchIdAndCardType(session.getMatchId(), "ATTACK");

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
            .map(p -> new BattlePlayerDto(
                String.valueOf(p.getUser().getUserId()),
                p.getUser().getUserId().equals(userId) ? "Tu" : p.getUser().getUsername(),
                p.getUser().getUserId().equals(userId),
                p.getCurrentHp(),
                p.getMultiplierWon().doubleValue(),
                Boolean.TRUE.equals(p.getAlive())
            ))
            .toList();

        String winnerId = session.getWinner() == null ? null : String.valueOf(session.getWinner().getUserId());
        String winnerName = session.getWinner() == null ? null : session.getWinner().getUsername();
        Double winningMultiplier = null;
        if (session.getWinner() != null) {
            winningMultiplier = participants.stream()
                .filter(p -> p.getUser().getUserId().equals(session.getWinner().getUserId()))
                .map(p -> p.getMultiplierWon().doubleValue())
                .findFirst()
                .orElse(1.0);
        }

        List<String> log = battleLogsByMatch.getOrDefault(session.getMatchId(), List.of("Completa primero el sorteo de bolas."));

        return new BattleDto(
            phase,
            round,
            winnerId,
            winnerName,
            winningMultiplier,
            userSelectedAction == null ? "ATTACK" : userSelectedAction,
            true,
            log,
            players
        );
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

    private void appendBattleLogs(Integer matchId, List<String> logs) {
        List<String> current = new ArrayList<>(battleLogsByMatch.getOrDefault(matchId, List.of()));
        current.addAll(logs);
        if (current.size() > 24) {
            current = current.subList(current.size() - 24, current.size());
        }
        battleLogsByMatch.put(matchId, current);
    }

    private void applyWinnerImpact(String tokenAlias, BigDecimal multiplier) {
        if (tokenAlias == null || tokenAlias.isBlank() || multiplier == null) {
            return;
        }

        TokenEntity token = resolveToken(tokenAlias);
        BigDecimal next = token.getCurrentPrice().multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
        if (next.compareTo(new BigDecimal("0.50")) < 0) {
            next = new BigDecimal("0.50");
        }
        token.setCurrentPrice(next);
        token.setLastUpdate(Instant.now());
        tokenRepository.save(token);

        TokenPriceHistoryEntity history = new TokenPriceHistoryEntity();
        history.setToken(token);
        history.setPrice(next);
        tokenPriceHistoryRepository.save(history);
    }

    private void closeActiveSessionIfAny(Integer userId) {
        Optional<GameSessionEntity> current = findCurrentSession(userId);
        current.ifPresent(session -> {
            session.setStatus("CLOSED");
            session.setEndTime(Instant.now());
            gameSessionRepository.save(session);
        });
    }

    private void logTransaction(UserEntity user, String type, BigDecimal amount, String description) {
        TransactionLogEntity log = new TransactionLogEntity();
        log.setUser(user);
        log.setType(type);
        log.setAmountFiat(amount.setScale(2, RoundingMode.HALF_UP));
        log.setDescription(description);
        transactionLogRepository.save(log);
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
        double value = Math.random();
        if (value < 0.55) {
            return "ATTACK";
        }
        if (value < 0.80) {
            return "SHIELD";
        }
        return "REBOUND";
    }

    private BigDecimal randomMultiplier() {
        double random = Math.random();
        double skewed = Math.pow(random, 2.8);
        double value = 0.5 + (skewed * (100.0 - 0.5));
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private String formatMultiplier(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private TokenMeta tokenMeta(Integer tokenId, String tokenName, String colorCode) {
        return switch (tokenId) {
            case 1 -> new TokenMeta("FRO", colorCode == null ? "#FF0000" : colorCode);
            case 2 -> new TokenMeta("FAZ", colorCode == null ? "#0000FF" : colorCode);
            case 3 -> new TokenMeta("FVD", colorCode == null ? "#00FF00" : colorCode);
            case 4 -> new TokenMeta("FGD", colorCode == null ? "#FFD700" : colorCode);
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
