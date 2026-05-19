package com.example.fichestu.service;

import com.example.fichestu.persistence.entity.TokenEntity;
import com.example.fichestu.persistence.entity.UserEntity;
import com.example.fichestu.persistence.entity.UserWalletEntity;
import com.example.fichestu.persistence.entity.UserWalletId;
import com.example.fichestu.persistence.repository.TokenRepository;
import com.example.fichestu.persistence.repository.UserWalletRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PortfolioWalletService {

    public static final BigDecimal INITIAL_PORTFOLIO_VALUE = new BigDecimal("200.00");
    public static final String GREY_TOKEN_NAME = "Ficha Gris";
    public static final String GREY_TOKEN_COLOR = "#9CA3AF";
    public static final BigDecimal GREY_TOKEN_PRICE = new BigDecimal("1.00");

    private static final int MONEY_SCALE = 2;
    private static final int QUANTITY_SCALE = 4;
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    private static final BigDecimal ZERO_QUANTITY = BigDecimal.ZERO.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);

    private final TokenRepository tokenRepository;
    private final UserWalletRepository userWalletRepository;

    public PortfolioWalletService(TokenRepository tokenRepository, UserWalletRepository userWalletRepository) {
        this.tokenRepository = tokenRepository;
        this.userWalletRepository = userWalletRepository;
    }

    @Transactional
    public void grantInitialTokenWallets(UserEntity user) {
        creditGreyValue(user, INITIAL_PORTFOLIO_VALUE);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculatePortfolioValue(UserEntity user) {
        return calculatePortfolioValue(user, Set.of());
    }

    @Transactional(readOnly = true)
    public BigDecimal calculatePortfolioValue(UserEntity user, Set<Integer> excludedTokenIds) {
        return userWalletRepository.findByIdUserId(user.getUserId()).stream()
            .filter(wallet -> wallet.getToken() != null)
            .filter(wallet -> !excludedTokenIds.contains(wallet.getToken().getTokenId()))
            .map(this::holdingValue)
            .reduce(ZERO_MONEY, BigDecimal::add)
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    @Transactional
    public void debitValue(UserEntity user, BigDecimal amount, Set<Integer> excludedTokenIds, String actionDescription) {
        debitGreyValue(user, amount, actionDescription);
    }

    @Transactional
    public void debitGreyValue(UserEntity user, BigDecimal amount, String actionDescription) {
        BigDecimal target = normalizeMoney(amount);
        if (target.compareTo(ZERO_MONEY) <= 0) {
            return;
        }

        TokenEntity greyToken = ensureGreyToken();
        UserWalletEntity wallet = findOrCreateWallet(user, greyToken);
        BigDecimal quantity = wallet.getQuantity() == null ? ZERO_QUANTITY : wallet.getQuantity();
        BigDecimal available = greyToken.getCurrentPrice()
            .multiply(quantity)
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (available.compareTo(target) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saldo insuficiente para " + actionDescription);
        }

        BigDecimal quantityToDebit = target.divide(greyToken.getCurrentPrice(), QUANTITY_SCALE, RoundingMode.UP);
        wallet.setQuantity(quantity.subtract(quantityToDebit).max(ZERO_QUANTITY).setScale(QUANTITY_SCALE, RoundingMode.HALF_UP));
        userWalletRepository.save(wallet);
    }

    @Transactional
    public void creditValue(UserEntity user, BigDecimal amount, Set<Integer> excludedTokenIds) {
        creditGreyValue(user, amount);
    }

    @Transactional
    public void creditGreyValue(UserEntity user, BigDecimal amount) {
        BigDecimal target = normalizeMoney(amount);
        if (target.compareTo(ZERO_MONEY) <= 0) {
            return;
        }

        TokenEntity greyToken = ensureGreyToken();
        BigDecimal quantity = target.divide(greyToken.getCurrentPrice(), QUANTITY_SCALE, RoundingMode.HALF_UP);
        UserWalletEntity wallet = findOrCreateWallet(user, greyToken);
        BigDecimal current = wallet.getQuantity() == null ? ZERO_QUANTITY : wallet.getQuantity();
        wallet.setQuantity(current.add(quantity).setScale(QUANTITY_SCALE, RoundingMode.HALF_UP));
        userWalletRepository.save(wallet);
    }

    @Transactional
    public UserWalletEntity findOrCreateWallet(UserEntity user, TokenEntity token) {
        UserWalletId id = new UserWalletId(user.getUserId(), token.getTokenId());
        return userWalletRepository.findById(id).orElseGet(() -> {
            UserWalletEntity wallet = new UserWalletEntity();
            wallet.setId(id);
            wallet.setUser(user);
            wallet.setToken(token);
            wallet.setQuantity(ZERO_QUANTITY);
            return wallet;
        });
    }

    @Transactional
    public TokenEntity ensureGreyToken() {
        TokenEntity token = tokenRepository.findByNameIgnoreCase(GREY_TOKEN_NAME).orElseGet(() -> {
            TokenEntity created = new TokenEntity();
            created.setName(GREY_TOKEN_NAME);
            return created;
        });

        boolean changed = token.getTokenId() == null;
        if (!GREY_TOKEN_COLOR.equalsIgnoreCase(token.getColorCode())) {
            token.setColorCode(GREY_TOKEN_COLOR);
            changed = true;
        }
        if (token.getCurrentPrice() == null || token.getCurrentPrice().compareTo(GREY_TOKEN_PRICE) != 0) {
            token.setCurrentPrice(GREY_TOKEN_PRICE);
            changed = true;
        }
        if (token.getLastUpdate() == null) {
            token.setLastUpdate(Instant.now());
            changed = true;
        }

        return changed ? tokenRepository.save(token) : token;
    }

    public boolean isGreyToken(TokenEntity token) {
        return token != null && GREY_TOKEN_NAME.equalsIgnoreCase(token.getName());
    }

    private BigDecimal holdingValue(UserWalletEntity wallet) {
        BigDecimal quantity = wallet.getQuantity() == null ? ZERO_QUANTITY : wallet.getQuantity();
        return wallet.getToken().getCurrentPrice()
            .multiply(quantity)
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeMoney(BigDecimal amount) {
        return amount == null ? ZERO_MONEY : amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
