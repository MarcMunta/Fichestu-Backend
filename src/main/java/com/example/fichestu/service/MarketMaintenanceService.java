package com.example.fichestu.service;

import com.example.fichestu.persistence.entity.MarketResetAuditEntity;
import com.example.fichestu.persistence.entity.TokenEntity;
import com.example.fichestu.persistence.entity.TokenPriceHistoryEntity;
import com.example.fichestu.persistence.repository.MarketResetAuditRepository;
import com.example.fichestu.persistence.repository.TokenPriceHistoryRepository;
import com.example.fichestu.persistence.repository.TokenRepository;
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

    private static final BigDecimal MIN_PRICE = new BigDecimal("0.50");
    private static final BigDecimal BASE_MIN = new BigDecimal("5.00");
    private static final BigDecimal BASE_MAX = new BigDecimal("500.00");

    private final TokenRepository tokenRepository;
    private final TokenPriceHistoryRepository tokenPriceHistoryRepository;
    private final MarketResetAuditRepository marketResetAuditRepository;
    private final RandomProvider randomProvider;

    @Value("${app.market.reset-zone:Europe/Madrid}")
    private String resetZone;

    @Value("${app.market.tick-interval-ms:300000}")
    private long tickIntervalMs;

    public MarketMaintenanceService(
        TokenRepository tokenRepository,
        TokenPriceHistoryRepository tokenPriceHistoryRepository,
        MarketResetAuditRepository marketResetAuditRepository,
        RandomProvider randomProvider
    ) {
        this.tokenRepository = tokenRepository;
        this.tokenPriceHistoryRepository = tokenPriceHistoryRepository;
        this.marketResetAuditRepository = marketResetAuditRepository;
        this.randomProvider = randomProvider;
    }

    @Scheduled(cron = "${app.market.daily-reset-cron:0 0 0 * * *}", zone = "${app.market.reset-zone:Europe/Madrid}")
    public void scheduledDailyReset() {
        runDailyResetIfDue();
    }

    @Scheduled(fixedDelayString = "${app.market.tick-interval-ms:300000}")
    public void scheduledMarketTick() {
        ensurePriceHistorySeeded();
        advanceMarketIfStale();
    }

    @Transactional
    public void syncMarketState() {
        ensurePriceHistorySeeded();
        runDailyResetIfDue();
        advanceMarketIfStale();
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
    public boolean advanceMarketIfStale() {
        List<TokenEntity> tokens = tokenRepository.findAllByOrderByTokenIdAsc();
        if (tokens.isEmpty()) {
            return false;
        }

        Instant lastUpdate = tokens.stream()
            .map(TokenEntity::getLastUpdate)
            .filter(value -> value != null)
            .min(Instant::compareTo)
            .orElse(Instant.EPOCH);

        if (lastUpdate.plusMillis(tickIntervalMs).isAfter(Instant.now())) {
            return false;
        }

        for (TokenEntity token : tokens) {
            double factor = 0.95 + (randomProvider.nextDouble() * 0.13);
            BigDecimal nextPrice = token.getCurrentPrice()
                .multiply(BigDecimal.valueOf(factor))
                .setScale(2, RoundingMode.HALF_UP);
            if (nextPrice.compareTo(MIN_PRICE) < 0) {
                nextPrice = MIN_PRICE;
            }
            token.setCurrentPrice(nextPrice);
            token.setLastUpdate(Instant.now());
            tokenRepository.save(token);
            appendPriceHistory(token, nextPrice);
        }

        return true;
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
