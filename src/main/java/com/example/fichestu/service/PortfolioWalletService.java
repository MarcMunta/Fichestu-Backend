package com.example.fichestu.service;

import com.example.fichestu.persistence.entity.TokenEntity;
import com.example.fichestu.persistence.entity.UserEntity;
import com.example.fichestu.persistence.entity.UserWalletEntity;
import com.example.fichestu.persistence.entity.UserWalletId;
import com.example.fichestu.persistence.repository.TokenRepository;
import com.example.fichestu.persistence.repository.UserWalletRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PortfolioWalletService {

    public static final BigDecimal INITIAL_PORTFOLIO_VALUE = new BigDecimal("200.00");

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
        creditValue(user, INITIAL_PORTFOLIO_VALUE, Set.of());
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
        BigDecimal target = normalizeMoney(amount);
        if (target.compareTo(ZERO_MONEY) <= 0) {
            return;
        }
        if (calculatePortfolioValue(user, excludedTokenIds).compareTo(target) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saldo insuficiente para " + actionDescription);
        }

        BigDecimal remaining = target;
        List<UserWalletEntity> wallets = userWalletRepository.findByIdUserId(user.getUserId()).stream()
            .filter(wallet -> wallet.getToken() != null)
            .filter(wallet -> !excludedTokenIds.contains(wallet.getToken().getTokenId()))
            .filter(wallet -> wallet.getQuantity() != null && wallet.getQuantity().compareTo(ZERO_QUANTITY) > 0)
            .filter(wallet -> wallet.getToken().getCurrentPrice() != null && wallet.getToken().getCurrentPrice().compareTo(BigDecimal.ZERO) > 0)
            .sorted(Comparator.comparing(wallet -> wallet.getToken().getTokenId()))
            .toList();

        for (UserWalletEntity wallet : wallets) {
            if (remaining.compareTo(ZERO_MONEY) <= 0) {
                break;
            }

            BigDecimal price = wallet.getToken().getCurrentPrice();
            BigDecimal walletValue = holdingValue(wallet);
            BigDecimal valueToDebit = remaining.min(walletValue);
            BigDecimal quantityToDebit = valueToDebit.divide(price, QUANTITY_SCALE, RoundingMode.UP);
            if (quantityToDebit.compareTo(wallet.getQuantity()) > 0) {
                quantityToDebit = wallet.getQuantity();
            }

            wallet.setQuantity(wallet.getQuantity().subtract(quantityToDebit).max(ZERO_QUANTITY).setScale(QUANTITY_SCALE, RoundingMode.HALF_UP));
            userWalletRepository.save(wallet);

            BigDecimal actualDebited = price.multiply(quantityToDebit).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            remaining = remaining.subtract(actualDebited).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
    }

    @Transactional
    public void creditValue(UserEntity user, BigDecimal amount, Set<Integer> excludedTokenIds) {
        BigDecimal target = normalizeMoney(amount);
        if (target.compareTo(ZERO_MONEY) <= 0) {
            return;
        }

        List<TokenEntity> tokens = tokenRepository.findAllByOrderByTokenIdAsc().stream()
            .filter(token -> !excludedTokenIds.contains(token.getTokenId()))
            .filter(token -> token.getCurrentPrice() != null && token.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0)
            .toList();
        if (tokens.isEmpty()) {
            tokens = tokenRepository.findAllByOrderByTokenIdAsc().stream()
                .filter(token -> token.getCurrentPrice() != null && token.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0)
                .toList();
        }
        if (tokens.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay fichas disponibles para abonar FTC");
        }

        BigDecimal perTokenValue = target.divide(BigDecimal.valueOf(tokens.size()), 8, RoundingMode.HALF_UP);
        for (TokenEntity token : tokens) {
            BigDecimal quantity = perTokenValue.divide(token.getCurrentPrice(), QUANTITY_SCALE, RoundingMode.HALF_UP);
            UserWalletEntity wallet = findOrCreateWallet(user, token);
            wallet.setQuantity(wallet.getQuantity().add(quantity).setScale(QUANTITY_SCALE, RoundingMode.HALF_UP));
            userWalletRepository.save(wallet);
        }
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
