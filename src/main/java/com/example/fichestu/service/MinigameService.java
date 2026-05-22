package com.example.fichestu.service;

import com.example.fichestu.api.MinigameDtos.FinishMinigameRequest;
import com.example.fichestu.api.MinigameDtos.MinigameAccessListResponse;
import com.example.fichestu.api.MinigameDtos.MinigameAccessResponse;
import com.example.fichestu.api.MinigameDtos.MinigameFinishResponse;
import com.example.fichestu.api.MinigameDtos.MinigameStartResponse;
import com.example.fichestu.api.MinigameDtos.StartMinigameRequest;
import com.example.fichestu.persistence.entity.MinigameAttemptEntity;
import com.example.fichestu.persistence.entity.TokenEntity;
import com.example.fichestu.persistence.entity.TransactionLogEntity;
import com.example.fichestu.persistence.entity.UserEntity;
import com.example.fichestu.persistence.repository.MinigameAttemptRepository;
import com.example.fichestu.persistence.repository.TransactionLogRepository;
import com.example.fichestu.security.CurrentUserService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MinigameService {

    private enum MinigameRule {
        TOKEN_RUSH("TOKEN_RUSH", new BigDecimal("25.00"), new BigDecimal("1000.00")),
        MARKET_MEMORY("MARKET_MEMORY", new BigDecimal("25.00"), new BigDecimal("220.00")),
        COIN_FLIP("COIN_FLIP", new BigDecimal("100.00"), new BigDecimal("200.00")),
        TOKEN_PATTERN("TOKEN_PATTERN", new BigDecimal("25.00"), new BigDecimal("200.00"));

        private final String type;
        private final BigDecimal paidEntryCost;
        private final BigDecimal maxReward;

        MinigameRule(String type, BigDecimal paidEntryCost, BigDecimal maxReward) {
            this.type = type;
            this.paidEntryCost = paidEntryCost;
            this.maxReward = maxReward;
        }
    }

    private static final String PAYMENT_FREE = "FREE";
    private static final String PAYMENT_PAID = "PAID";
    private static final String PAYMENT_AUTO = "AUTO";
    private static final String STATUS_STARTED = "STARTED";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final Duration FREE_COOLDOWN = Duration.ofHours(3);
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final CurrentUserService currentUserService;
    private final MinigameAttemptRepository minigameAttemptRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final PortfolioWalletService portfolioWalletService;
    private final NotificationService notificationService;

    public MinigameService(
        CurrentUserService currentUserService,
        MinigameAttemptRepository minigameAttemptRepository,
        TransactionLogRepository transactionLogRepository,
        PortfolioWalletService portfolioWalletService,
        NotificationService notificationService
    ) {
        this.currentUserService = currentUserService;
        this.minigameAttemptRepository = minigameAttemptRepository;
        this.transactionLogRepository = transactionLogRepository;
        this.portfolioWalletService = portfolioWalletService;
        this.notificationService = notificationService;
    }

    @Transactional
    public MinigameAccessListResponse accessList() {
        UserEntity user = currentUserService.requireUserEntity();
        BigDecimal cashBalance = calculateStumBalance(user);
        return new MinigameAccessListResponse(
            "Acceso a minijuegos cargado",
            true,
            cashBalance,
            Arrays.stream(MinigameRule.values())
                .map(rule -> accessFor(user, rule, cashBalance))
                .toList()
        );
    }

    @Transactional
    public MinigameAccessResponse access(String gameType) {
        UserEntity user = currentUserService.requireUserEntity();
        return accessFor(user, resolveRule(gameType), calculateStumBalance(user));
    }

    @Transactional
    public MinigameStartResponse start(StartMinigameRequest request) {
        UserEntity user = currentUserService.requireUserEntity();
        MinigameRule rule = resolveRule(request.getGameType());
        String paymentType = normalizePaymentType(request.getPaymentType());
        int cooldown = freeCooldownSeconds(user, rule);

        if (PAYMENT_AUTO.equals(paymentType)) {
            paymentType = cooldown == 0 ? PAYMENT_FREE : PAYMENT_PAID;
        }
        if (PAYMENT_FREE.equals(paymentType) && cooldown > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Intento gratis no disponible todavia");
        }

        BigDecimal entryCost = ZERO_MONEY;
        if (PAYMENT_PAID.equals(paymentType)) {
            entryCost = normalizeMoney(rule.paidEntryCost);
            portfolioWalletService.debitGreyValue(user, entryCost, "repetir minijuego");
            logTransaction(user, "MINIGAME_ENTRY", entryCost.negate(), "Entrada pagada en " + rule.type);
        }

        MinigameAttemptEntity attempt = new MinigameAttemptEntity();
        attempt.setUser(user);
        attempt.setGameType(rule.type);
        attempt.setPaymentType(paymentType);
        attempt.setStatus(STATUS_STARTED);
        attempt.setEntryCost(entryCost);
        attempt = minigameAttemptRepository.save(attempt);

        BigDecimal cashBalance = calculateStumBalance(user);
        MinigameAccessResponse access = accessFor(user, rule, cashBalance);
        return new MinigameStartResponse(
            PAYMENT_FREE.equals(paymentType) ? "Intento gratis iniciado" : "Entrada pagada",
            true,
            attempt.getAttemptId(),
            rule.type,
            paymentType,
            entryCost,
            cashBalance,
            access.freeAvailable(),
            access.freeCooldownSec(),
            access.nextFreeAt()
        );
    }

    @Transactional
    public MinigameFinishResponse finish(Integer attemptId, FinishMinigameRequest request) {
        UserEntity user = currentUserService.requireUserEntity();
        MinigameAttemptEntity attempt = minigameAttemptRepository.findById(attemptId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intento no encontrado"));
        if (!attempt.getUser().getUserId().equals(user.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Este intento no es tuyo");
        }
        if (!STATUS_STARTED.equalsIgnoreCase(attempt.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Intento ya terminado");
        }

        MinigameRule rule = resolveRule(attempt.getGameType());
        BigDecimal reward = normalizeMoney(request.getRewardStum()).min(rule.maxReward).max(ZERO_MONEY);
        int score = request.getScore() == null ? 0 : Math.max(0, request.getScore());

        attempt.setScore(score);
        attempt.setRewardAmount(reward);
        attempt.setCompletedAt(Instant.now());
        attempt.setStatus(STATUS_COMPLETED);
        minigameAttemptRepository.save(attempt);

        if (reward.compareTo(ZERO_MONEY) > 0) {
            portfolioWalletService.creditGreyValue(user, reward);
            logTransaction(user, "MINIGAME_REWARD", reward, "Recompensa de " + rule.type);
            notificationService.create(
                user,
                "Recompensa de minijuego",
                "Has ganado " + reward + " Stum en " + displayName(rule) + ".",
                "MINIGAME_REWARD"
            );
        }

        BigDecimal cashBalance = calculateStumBalance(user);
        MinigameAccessResponse access = accessFor(user, rule, cashBalance);
        return new MinigameFinishResponse(
            reward.compareTo(ZERO_MONEY) > 0 ? "Recompensa aplicada" : "Intento terminado",
            true,
            attempt.getAttemptId(),
            rule.type,
            score,
            reward,
            cashBalance,
            access.freeAvailable(),
            access.freeCooldownSec(),
            access.nextFreeAt()
        );
    }

    private MinigameAccessResponse accessFor(UserEntity user, MinigameRule rule, BigDecimal cashBalance) {
        int cooldown = freeCooldownSeconds(user, rule);
        Instant nextFreeAt = nextFreeAt(user, rule);
        return new MinigameAccessResponse(
            rule.type,
            cooldown == 0,
            cooldown,
            nextFreeAt,
            rule.paidEntryCost,
            rule.maxReward,
            cashBalance
        );
    }

    private int freeCooldownSeconds(UserEntity user, MinigameRule rule) {
        return minigameAttemptRepository.findTopByUserUserIdAndGameTypeAndPaymentTypeOrderByStartedAtDesc(
                user.getUserId(),
                rule.type,
                PAYMENT_FREE
            )
            .map(MinigameAttemptEntity::getStartedAt)
            .map(startedAt -> {
                long elapsed = Duration.between(startedAt, Instant.now()).toSeconds();
                return (int) Math.max(0, FREE_COOLDOWN.toSeconds() - elapsed);
            })
            .orElse(0);
    }

    private Instant nextFreeAt(UserEntity user, MinigameRule rule) {
        return minigameAttemptRepository.findTopByUserUserIdAndGameTypeAndPaymentTypeOrderByStartedAtDesc(
                user.getUserId(),
                rule.type,
                PAYMENT_FREE
            )
            .map(MinigameAttemptEntity::getStartedAt)
            .map(startedAt -> startedAt.plus(FREE_COOLDOWN))
            .filter(next -> next.isAfter(Instant.now()))
            .orElse(null);
    }

    private BigDecimal calculateStumBalance(UserEntity user) {
        TokenEntity greyToken = portfolioWalletService.ensureGreyToken();
        return portfolioWalletService.calculateSpendableValue(user, Set.of(greyToken.getTokenId()));
    }

    private MinigameRule resolveRule(String rawType) {
        String normalized = rawType == null ? "" : rawType.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        for (MinigameRule rule : MinigameRule.values()) {
            if (rule.type.equals(normalized)) {
                return rule;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Minijuego no soportado");
    }

    private String normalizePaymentType(String rawType) {
        String normalized = rawType == null ? PAYMENT_AUTO : rawType.trim().toUpperCase(Locale.ROOT);
        if (PAYMENT_FREE.equals(normalized) || PAYMENT_PAID.equals(normalized) || PAYMENT_AUTO.equals(normalized)) {
            return normalized;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de pago no soportado");
    }

    private BigDecimal normalizeMoney(BigDecimal amount) {
        return amount == null ? ZERO_MONEY : amount.setScale(2, RoundingMode.HALF_UP);
    }

    private void logTransaction(UserEntity user, String type, BigDecimal amount, String description) {
        TransactionLogEntity log = new TransactionLogEntity();
        log.setUser(user);
        log.setType(type);
        log.setAmountFiat(amount.setScale(2, RoundingMode.HALF_UP));
        log.setDescription(description);
        transactionLogRepository.save(log);
    }

    private String displayName(MinigameRule rule) {
        return switch (rule) {
            case TOKEN_RUSH -> "Token Rush";
            case MARKET_MEMORY -> "Market Memory";
            case COIN_FLIP -> "Coin Flip";
            case TOKEN_PATTERN -> "Token Pattern";
        };
    }
}
