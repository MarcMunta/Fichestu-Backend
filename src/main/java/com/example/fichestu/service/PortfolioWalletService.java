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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PortfolioWalletService {

    public record EntryDebit(Integer tokenId, BigDecimal quantity, BigDecimal value) {
    }

    public static final BigDecimal INITIAL_PORTFOLIO_VALUE = new BigDecimal("200.00");
    public static final BigDecimal INITIAL_MARKET_TOKEN_QUANTITY = new BigDecimal("10.0000");
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
        for (TokenEntity token : tokenRepository.findAllByOrderByTokenIdAsc()) {
            if (isGreyToken(token)) {
                continue;
            }
            UserWalletEntity wallet = findOrCreateWallet(user, token);
            wallet.setQuantity(INITIAL_MARKET_TOKEN_QUANTITY);
            userWalletRepository.save(wallet);
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal calculatePortfolioValue(UserEntity user) {
        return calculatePortfolioValue(user, Set.of());
    }

    @Transactional(readOnly = true)
    public BigDecimal calculatePortfolioValue(UserEntity user, Set<Integer> excludedTokenIds) {
        Set<Integer> excluded = excludedTokenIds == null ? Set.of() : excludedTokenIds;
        return userWalletRepository.findByIdUserId(user.getUserId()).stream()
            .filter(wallet -> wallet.getToken() != null)
            .filter(wallet -> !excluded.contains(wallet.getToken().getTokenId()))
            .map(this::holdingValue)
            .reduce(ZERO_MONEY, BigDecimal::add)
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    @Transactional
    public void debitValue(UserEntity user, BigDecimal amount, Set<Integer> excludedTokenIds, String actionDescription) {
        debitGreyValue(user, amount, actionDescription);
    }

    @Transactional
    public EntryDebit debitGreyValue(UserEntity user, BigDecimal amount, String actionDescription) {
        BigDecimal target = normalizeMoney(amount);
        if (target.compareTo(ZERO_MONEY) <= 0) {
            return null;
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
        BigDecimal actualDebited = greyToken.getCurrentPrice().multiply(quantityToDebit).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return new EntryDebit(greyToken.getTokenId(), quantityToDebit, actualDebited);
    }

    @Transactional
    public List<EntryDebit> debitValueFromPreferredTokens(
        UserEntity user,
        BigDecimal amount,
        List<Integer> preferredTokenIds,
        String actionDescription
    ) {
        List<Integer> preferred = preferredTokenIds == null ? List.of() : preferredTokenIds.stream()
            .filter(id -> id != null && id > 0)
            .distinct()
            .toList();
        if (preferred.isEmpty()) {
            List<UserWalletEntity> wallets = spendableWallets(user).stream()
                .sorted(Comparator.comparing((UserWalletEntity wallet) -> wallet.getToken().getCurrentPrice())
                    .thenComparing(wallet -> wallet.getToken().getTokenId()))
                .toList();
            return debitValueFromWallets(amount, actionDescription, wallets);
        }

        Comparator<UserWalletEntity> sort = Comparator.comparingInt((UserWalletEntity wallet) -> {
            int index = preferred.indexOf(wallet.getToken().getTokenId());
            return index < 0 ? Integer.MAX_VALUE : index;
        }).thenComparing(wallet -> wallet.getToken().getTokenId());

        List<UserWalletEntity> wallets = spendableWallets(user).stream()
            .filter(wallet -> preferred.contains(wallet.getToken().getTokenId()))
            .sorted(sort)
            .toList();
        return debitValueFromWallets(amount, actionDescription, wallets);
    }

    private List<EntryDebit> debitValueFromWallets(
        BigDecimal amount,
        String actionDescription,
        List<UserWalletEntity> wallets
    ) {
        BigDecimal target = normalizeMoney(amount);
        if (target.compareTo(ZERO_MONEY) <= 0) {
            return List.of();
        }
        BigDecimal spendable = wallets.stream()
            .map(this::holdingValue)
            .reduce(ZERO_MONEY, BigDecimal::add)
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (spendable.compareTo(target) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saldo insuficiente para " + actionDescription);
        }

        BigDecimal remaining = target;
        List<EntryDebit> debits = new java.util.ArrayList<>();
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
            debits.add(new EntryDebit(wallet.getToken().getTokenId(), quantityToDebit, actualDebited));
            remaining = remaining.subtract(actualDebited).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return debits;
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
    public void creditQuantities(UserEntity user, Map<Integer, BigDecimal> quantitiesByTokenId) {
        if (quantitiesByTokenId == null || quantitiesByTokenId.isEmpty()) {
            return;
        }

        Map<Integer, BigDecimal> normalized = new LinkedHashMap<>();
        quantitiesByTokenId.forEach((tokenId, quantity) -> {
            if (tokenId != null && quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0) {
                normalized.merge(tokenId, quantity.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP), BigDecimal::add);
            }
        });

        for (TokenEntity token : tokenRepository.findAllByOrderByTokenIdAsc()) {
            BigDecimal quantity = normalized.get(token.getTokenId());
            if (quantity == null || quantity.compareTo(ZERO_QUANTITY) <= 0) {
                continue;
            }
            UserWalletEntity wallet = findOrCreateWallet(user, token);
            BigDecimal current = wallet.getQuantity() == null ? ZERO_QUANTITY : wallet.getQuantity();
            wallet.setQuantity(current.add(quantity).setScale(QUANTITY_SCALE, RoundingMode.HALF_UP));
            userWalletRepository.save(wallet);
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateSpendableValue(UserEntity user, Set<Integer> includedTokenIds) {
        Set<Integer> included = includedTokenIds == null ? Set.of() : includedTokenIds;
        return userWalletRepository.findByIdUserId(user.getUserId()).stream()
            .filter(wallet -> wallet.getToken() != null)
            .filter(wallet -> included.isEmpty() || included.contains(wallet.getToken().getTokenId()))
            .map(this::holdingValue)
            .reduce(ZERO_MONEY, BigDecimal::add)
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private List<UserWalletEntity> spendableWallets(UserEntity user) {
        return userWalletRepository.findByIdUserId(user.getUserId()).stream()
            .filter(wallet -> wallet.getToken() != null)
            .filter(wallet -> wallet.getQuantity() != null && wallet.getQuantity().compareTo(ZERO_QUANTITY) > 0)
            .filter(wallet -> wallet.getToken().getCurrentPrice() != null && wallet.getToken().getCurrentPrice().compareTo(BigDecimal.ZERO) > 0)
            .toList();
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
