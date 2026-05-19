package com.example.fichestu;

import com.example.fichestu.persistence.entity.TokenEntity;
import com.example.fichestu.persistence.entity.UserEntity;
import com.example.fichestu.persistence.entity.MarketResetAuditEntity;
import com.example.fichestu.persistence.entity.UserWalletEntity;
import com.example.fichestu.persistence.entity.UserWalletId;
import com.example.fichestu.persistence.repository.BadgeRepository;
import com.example.fichestu.persistence.repository.GameSessionEventRepository;
import com.example.fichestu.persistence.repository.GameSessionRepository;
import com.example.fichestu.persistence.repository.MarketResetAuditRepository;
import com.example.fichestu.persistence.repository.MatchCardRepository;
import com.example.fichestu.persistence.repository.MatchParticipantRepository;
import com.example.fichestu.persistence.repository.NotificationRepository;
import com.example.fichestu.persistence.repository.PasswordResetTokenRepository;
import com.example.fichestu.persistence.repository.RevokedJwtTokenRepository;
import com.example.fichestu.persistence.repository.TokenPriceHistoryRepository;
import com.example.fichestu.persistence.repository.TokenRepository;
import com.example.fichestu.persistence.repository.TransactionLogRepository;
import com.example.fichestu.persistence.repository.UserBadgeRepository;
import com.example.fichestu.persistence.repository.UserRepository;
import com.example.fichestu.persistence.repository.UserWalletRepository;
import com.example.fichestu.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
abstract class IntegrationTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected BCryptPasswordEncoder passwordEncoder;

    @Autowired
    protected JwtService jwtService;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected TokenRepository tokenRepository;

    @Autowired
    protected UserWalletRepository userWalletRepository;

    @Autowired
    protected TokenPriceHistoryRepository tokenPriceHistoryRepository;

    @Autowired
    protected TransactionLogRepository transactionLogRepository;

    @Autowired
    protected GameSessionRepository gameSessionRepository;

    @Autowired
    protected MatchParticipantRepository matchParticipantRepository;

    @Autowired
    protected MatchCardRepository matchCardRepository;

    @Autowired
    protected BadgeRepository badgeRepository;

    @Autowired
    protected UserBadgeRepository userBadgeRepository;

    @Autowired
    protected GameSessionEventRepository gameSessionEventRepository;

    @Autowired
    protected MarketResetAuditRepository marketResetAuditRepository;

    @Autowired
    protected PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    protected RevokedJwtTokenRepository revokedJwtTokenRepository;

    @Autowired
    protected NotificationRepository notificationRepository;

    @BeforeEach
    void cleanDatabase() {
        gameSessionEventRepository.deleteAll();
        matchCardRepository.deleteAll();
        matchParticipantRepository.deleteAll();
        gameSessionRepository.deleteAll();
        userBadgeRepository.deleteAll();
        badgeRepository.deleteAll();
        userWalletRepository.deleteAll();
        tokenPriceHistoryRepository.deleteAll();
        transactionLogRepository.deleteAll();
        tokenRepository.deleteAll();
        marketResetAuditRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        revokedJwtTokenRepository.deleteAll();
        notificationRepository.deleteAll();
        userRepository.deleteAll();
    }

    protected UserEntity createUser(String username, String email, String rawPassword, String role, BigDecimal fiatBalance) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(rawPassword == null ? null : passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setFiatBalance(BigDecimal.ZERO);
        UserEntity saved = userRepository.save(user);
        seedPortfolioValue(saved, fiatBalance);
        return saved;
    }

    protected void seedPortfolioValue(UserEntity user, BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        var tokens = tokenRepository.findAllByOrderByTokenIdAsc();
        if (tokens.isEmpty()) {
            return;
        }

        BigDecimal perTokenValue = value.divide(BigDecimal.valueOf(tokens.size()), 8, RoundingMode.HALF_UP);
        for (TokenEntity token : tokens) {
            BigDecimal quantity = perTokenValue.divide(token.getCurrentPrice(), 4, RoundingMode.HALF_UP);
            seedWalletQuantity(user, token, quantity);
        }
    }

    protected void seedWalletQuantity(UserEntity user, TokenEntity token, BigDecimal quantity) {
        UserWalletEntity wallet = new UserWalletEntity();
        wallet.setId(new UserWalletId(user.getUserId(), token.getTokenId()));
        wallet.setUser(user);
        wallet.setToken(token);
        wallet.setQuantity(quantity.setScale(4, RoundingMode.HALF_UP));
        userWalletRepository.save(wallet);
    }

    protected String bearerFor(UserEntity user) {
        return "Bearer " + jwtService.generateToken(user);
    }

    protected void createDefaultTokens() {
        createToken("Ficha Roja", "#FF0000", new BigDecimal("50.00"));
        createToken("Ficha Azul", "#0000FF", new BigDecimal("25.00"));
        createToken("Ficha Verde", "#00FF00", new BigDecimal("10.00"));
        createToken("Ficha Dorada", "#FFD700", new BigDecimal("100.00"));
        markPortfolioResetExecuted();
    }

    protected TokenEntity findTokenByName(String name) {
        return tokenRepository.findByNameIgnoreCase(name).orElseThrow();
    }

    protected LocalDate currentBusinessDate() {
        return LocalDate.now(ZoneId.of("Europe/Madrid"));
    }

    protected void markDailyResetExecuted(LocalDate businessDate) {
        if (marketResetAuditRepository.existsByBusinessDate(businessDate)) {
            return;
        }

        MarketResetAuditEntity audit = new MarketResetAuditEntity();
        audit.setBusinessDate(businessDate);
        audit.setZoneId("Europe/Madrid");
        audit.setSummary("Reset sembrado en test para estabilizar el mercado");
        marketResetAuditRepository.save(audit);
    }

    protected void markPortfolioResetExecuted() {
        LocalDate markerDate = LocalDate.of(2000, 1, 1);
        if (marketResetAuditRepository.existsByBusinessDate(markerDate)) {
            return;
        }

        MarketResetAuditEntity audit = new MarketResetAuditEntity();
        audit.setBusinessDate(markerDate);
        audit.setZoneId("Europe/Madrid");
        audit.setSummary("Reset cashless sembrado en test");
        marketResetAuditRepository.save(audit);
    }

    private void createToken(String name, String colorCode, BigDecimal currentPrice) {
        TokenEntity token = new TokenEntity();
        token.setName(name);
        token.setColorCode(colorCode);
        token.setCurrentPrice(currentPrice);
        token.setLastUpdate(Instant.now());
        tokenRepository.save(token);
    }
}
