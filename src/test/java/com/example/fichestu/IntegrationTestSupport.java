package com.example.fichestu;

import com.example.fichestu.persistence.entity.TokenEntity;
import com.example.fichestu.persistence.entity.UserEntity;
import com.example.fichestu.persistence.entity.MarketResetAuditEntity;
import com.example.fichestu.persistence.repository.BadgeRepository;
import com.example.fichestu.persistence.repository.GameSessionEventRepository;
import com.example.fichestu.persistence.repository.GameSessionRepository;
import com.example.fichestu.persistence.repository.MarketResetAuditRepository;
import com.example.fichestu.persistence.repository.MatchCardRepository;
import com.example.fichestu.persistence.repository.MatchParticipantRepository;
import com.example.fichestu.persistence.repository.TokenPriceHistoryRepository;
import com.example.fichestu.persistence.repository.TokenRepository;
import com.example.fichestu.persistence.repository.TransactionLogRepository;
import com.example.fichestu.persistence.repository.UserBadgeRepository;
import com.example.fichestu.persistence.repository.UserRepository;
import com.example.fichestu.persistence.repository.UserWalletRepository;
import com.example.fichestu.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
        userRepository.deleteAll();
    }

    protected UserEntity createUser(String username, String email, String rawPassword, String role, BigDecimal fiatBalance) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(rawPassword == null ? null : passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setFiatBalance(fiatBalance);
        return userRepository.save(user);
    }

    protected String bearerFor(UserEntity user) {
        return "Bearer " + jwtService.generateToken(user);
    }

    protected void createDefaultTokens() {
        createToken("Ficha Roja", "#FF0000", new BigDecimal("50.00"));
        createToken("Ficha Azul", "#0000FF", new BigDecimal("25.00"));
        createToken("Ficha Verde", "#00FF00", new BigDecimal("10.00"));
        createToken("Ficha Dorada", "#FFD700", new BigDecimal("100.00"));
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

    private void createToken(String name, String colorCode, BigDecimal currentPrice) {
        TokenEntity token = new TokenEntity();
        token.setName(name);
        token.setColorCode(colorCode);
        token.setCurrentPrice(currentPrice);
        token.setLastUpdate(Instant.now());
        tokenRepository.save(token);
    }
}
