package com.example.fichestu.service;

import com.example.fichestu.persistence.entity.MarketResetAuditEntity;
import com.example.fichestu.persistence.entity.TokenEntity;
import com.example.fichestu.persistence.entity.TokenPriceHistoryEntity;
import com.example.fichestu.persistence.entity.UserEntity;
import com.example.fichestu.persistence.entity.UserWalletEntity;
import com.example.fichestu.persistence.entity.UserWalletId;
import com.example.fichestu.persistence.repository.MarketResetAuditRepository;
import com.example.fichestu.persistence.repository.TokenPriceHistoryRepository;
import com.example.fichestu.persistence.repository.TokenRepository;
import com.example.fichestu.persistence.repository.UserRepository;
import com.example.fichestu.persistence.repository.UserWalletRepository;
import com.example.fichestu.support.RandomProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketMaintenanceService {

    private static final BigDecimal BASE_MIN = new BigDecimal("1.00");
    private static final BigDecimal BASE_MAX = new BigDecimal("500.00");
    private static final BigDecimal RESET_PORTFOLIO_VALUE = new BigDecimal("200.00");
    private static final LocalDate CASHLESS_PORTFOLIO_RESET_MARKER = LocalDate.of(2000, 1, 1);

    private final TokenRepository tokenRepository;
    private final TokenPriceHistoryRepository tokenPriceHistoryRepository;
    private final MarketResetAuditRepository marketResetAuditRepository;
    private final UserRepository userRepository;
    private final UserWalletRepository userWalletRepository;
    private final RandomProvider randomProvider;

    @Value("${app.market.reset-zone:Europe/Madrid}")
    private String resetZone;

    public MarketMaintenanceService(
        TokenRepository tokenRepository,
        TokenPriceHistoryRepository tokenPriceHistoryRepository,
        MarketResetAuditRepository marketResetAuditRepository,
        UserRepository userRepository,
        UserWalletRepository userWalletRepository,
        RandomProvider randomProvider
    ) {
        this.tokenRepository = tokenRepository;
        this.tokenPriceHistoryRepository = tokenPriceHistoryRepository;
        this.marketResetAuditRepository = marketResetAuditRepository;
        this.userRepository = userRepository;
        this.userWalletRepository = userWalletRepository;
        this.randomProvider = randomProvider;
    }

    @Scheduled(cron = "${app.market.daily-reset-cron:0 0 0 * * *}", zone = "${app.market.reset-zone:Europe/Madrid}")
    public void scheduledDailyReset() {
        runDailyResetIfDue();
    }

    @Scheduled(fixedDelayString = "${app.market.tick-interval-ms:300000}")
    public void scheduledMarketTick() {
        ensurePriceHistorySeeded();
    }

    @Transactional
    public void syncMarketState() {
        ensurePriceHistorySeeded();
        resetAllUsersToInitialPortfolioIfNeeded();
    }

    @Transactional
    public boolean runDailyResetIfDue() {
        return runDailyResetForBusinessDate(LocalDate.now(ZoneId.of(resetZone)));
    }

    @Transactional
    public boolean runDailyResetForBusinessDate(LocalDate businessDate) {
        if (marketResetAuditRepository.existsByBusinessDate(businessDate)) {
            return false;
        }

        ensurePriceHistorySeeded();

        List<TokenEntity> tokens = tokenRepository.findAllByOrderByTokenIdAsc();
        for (TokenEntity token : tokens) {
            BigDecimal newPrice = randomBasePrice();
            token.setCurrentPrice(newPrice);
            token.setLastUpdate(Instant.now());
            tokenRepository.save(token);
            appendPriceHistory(token, newPrice);
        }

        MarketResetAuditEntity audit = new MarketResetAuditEntity();
        audit.setBusinessDate(businessDate);
        audit.setZoneId(resetZone);
        audit.setSummary("Reset diario ejecutado para " + tokens.size() + " tokens. Las carteras conservan sus fichas.");
        marketResetAuditRepository.save(audit);

        return true;
    }

    @Transactional
    public boolean resetAllUsersToInitialPortfolioIfNeeded() {
        if (marketResetAuditRepository.existsByBusinessDate(CASHLESS_PORTFOLIO_RESET_MARKER)) {
            return false;
        }

        List<TokenEntity> tokens = tokenRepository.findAllByOrderByTokenIdAsc();
        if (tokens.isEmpty()) {
            return false;
        }

        for (TokenEntity token : tokens) {
            BigDecimal newPrice = randomBasePrice();
            token.setCurrentPrice(newPrice);
            token.setLastUpdate(Instant.now());
            tokenRepository.save(token);
            appendPriceHistory(token, newPrice);
        }

        userWalletRepository.deleteAll();
        BigDecimal perTokenValue = RESET_PORTFOLIO_VALUE.divide(BigDecimal.valueOf(tokens.size()), 8, RoundingMode.HALF_UP);
        for (UserEntity user : userRepository.findAll()) {
            user.setFiatBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            userRepository.save(user);
            for (TokenEntity token : tokens) {
                BigDecimal quantity = perTokenValue.divide(token.getCurrentPrice(), 4, RoundingMode.HALF_UP);
                UserWalletEntity wallet = new UserWalletEntity();
                wallet.setId(new UserWalletId(user.getUserId(), token.getTokenId()));
                wallet.setUser(user);
                wallet.setToken(token);
                wallet.setQuantity(quantity);
                userWalletRepository.save(wallet);
            }
        }

        MarketResetAuditEntity audit = new MarketResetAuditEntity();
        audit.setBusinessDate(CASHLESS_PORTFOLIO_RESET_MARKER);
        audit.setZoneId(resetZone);
        audit.setSummary("Reset inicial cashless: todos los usuarios pasan a 200 FTC en fichas.");
        marketResetAuditRepository.save(audit);

        return true;
    }

    @Transactional
    public boolean advanceMarketIfStale() {
        ensurePriceHistorySeeded();
        return false;
    }

    @Transactional
    public void ensurePriceHistorySeeded() {
        for (TokenEntity token : tokenRepository.findAllByOrderByTokenIdAsc()) {
            if (tokenPriceHistoryRepository.countByTokenTokenId(token.getTokenId()) > 0) {
                continue;
            }
            appendPriceHistory(token, token.getCurrentPrice());
        }
    }

    private BigDecimal randomBasePrice() {
        double ratio = randomProvider.nextDouble();
        BigDecimal spread = BASE_MAX.subtract(BASE_MIN);
        return BASE_MIN.add(spread.multiply(BigDecimal.valueOf(ratio))).setScale(2, RoundingMode.HALF_UP);
    }

    private void appendPriceHistory(TokenEntity token, BigDecimal price) {
        TokenPriceHistoryEntity history = new TokenPriceHistoryEntity();
        history.setToken(token);
        history.setPrice(price);
        tokenPriceHistoryRepository.save(history);
    }

}
