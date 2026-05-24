package com.example.fichestu.service;

import com.example.fichestu.api.GameDtos.BadgeDto;
import com.example.fichestu.api.GameDtos.ProfileStatsDto;
import com.example.fichestu.persistence.entity.BadgeEntity;
import com.example.fichestu.persistence.entity.UserBadgeEntity;
import com.example.fichestu.persistence.entity.UserBadgeId;
import com.example.fichestu.persistence.repository.BadgeRepository;
import com.example.fichestu.persistence.repository.MatchParticipantRepository;
import com.example.fichestu.persistence.repository.TransactionLogRepository;
import com.example.fichestu.persistence.repository.UserBadgeRepository;
import com.example.fichestu.persistence.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerProfileReadService {

    private static final String TYPE_REWARDED = "REWARDED";
    private static final String TYPE_PROFILE_BALL_ROOM = "PROFILE_BALL_ROOM_PLAYED";
    private static final String TYPE_PROFILE_BATTLE = "PROFILE_BATTLE_PLAYED";
    private static final String TYPE_PROFILE_BATTLE_WIN = "PROFILE_BATTLE_WON";
    private static final String TYPE_PROFILE_MULTIPLIER = "PROFILE_MULTIPLIER";
    private static final Duration REWARDED_COOLDOWN = Duration.ofSeconds(30);

    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final UserRepository userRepository;

    public PlayerProfileReadService(
        BadgeRepository badgeRepository,
        UserBadgeRepository userBadgeRepository,
        MatchParticipantRepository matchParticipantRepository,
        TransactionLogRepository transactionLogRepository,
        UserRepository userRepository
    ) {
        this.badgeRepository = badgeRepository;
        this.userBadgeRepository = userBadgeRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.transactionLogRepository = transactionLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public ProfileStatsDto loadStatsForUser(Integer userId) {
        int archivedBallRooms = Math.toIntExact(transactionLogRepository.countByUserUserIdAndType(userId, TYPE_PROFILE_BALL_ROOM));
        int archivedBattles = Math.toIntExact(transactionLogRepository.countByUserUserIdAndType(userId, TYPE_PROFILE_BATTLE));
        int archivedWins = Math.toIntExact(transactionLogRepository.countByUserUserIdAndType(userId, TYPE_PROFILE_BATTLE_WIN));
        int activeBallRooms = Math.toIntExact(matchParticipantRepository.countRealBallRoomsPlayed(userId));
        int activeBattles = Math.toIntExact(matchParticipantRepository.countRealBattlesPlayed(userId));
        int activeWins = Math.toIntExact(matchParticipantRepository.countRealBattlesWon(userId));
        int ballRoomsPlayed = activeBallRooms + archivedBallRooms;
        int battlesPlayed = activeBattles + archivedBattles;
        int battlesWon = activeWins + archivedWins;
        BigDecimal bestMultiplierValue = matchParticipantRepository.findBestRealMultiplier(userId);
        BigDecimal archivedBestMultiplier = transactionLogRepository.findMaxAmountByUserAndType(userId, TYPE_PROFILE_MULTIPLIER);
        double bestMultiplier = Math.max(
            bestMultiplierValue == null ? 1.0 : bestMultiplierValue.doubleValue(),
            archivedBestMultiplier == null ? 1.0 : archivedBestMultiplier.doubleValue()
        );
        Double averageMultiplierValue = matchParticipantRepository.findAverageRealMultiplier(userId);
        Double archivedAverageMultiplier = transactionLogRepository.findAverageAmountByUserAndType(userId, TYPE_PROFILE_MULTIPLIER);
        double activeAverage = averageMultiplierValue == null ? 1.0 : averageMultiplierValue;
        double archivedAverage = archivedAverageMultiplier == null ? 1.0 : archivedAverageMultiplier;
        int multiplierSamples = activeBallRooms + archivedBallRooms;
        double average = multiplierSamples == 0
            ? 1.0
            : ((activeAverage * activeBallRooms) + (archivedAverage * archivedBallRooms)) / multiplierSamples;
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

    @Transactional
    public List<BadgeDto> loadBadgesForUser(Integer userId) {
        ensureBadgeCatalog();
        ProfileStatsDto stats = loadStatsForUser(userId);
        syncUnlockedBadges(userId, stats);

        Set<Integer> unlockedIds = userBadgeRepository.findByIdUserId(userId).stream()
            .map(userBadge -> userBadge.getBadge().getBadgeId())
            .collect(Collectors.toSet());

        return badgeRepository.findAllByOrderByBadgeIdAsc().stream()
            .map(badge -> new BadgeDto(
                badge.getBadgeId(),
                badge.getName(),
                badge.getDescription(),
                unlockedIds.contains(badge.getBadgeId())
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public int currentRewardedCooldownSeconds(Integer userId) {
        return transactionLogRepository.findTopByUserUserIdAndTypeOrderByCreatedAtDesc(userId, TYPE_REWARDED)
            .map(log -> {
                long secondsSinceClaim = Duration.between(log.getCreatedAt(), Instant.now()).getSeconds();
                return (int) Math.max(0, REWARDED_COOLDOWN.getSeconds() - secondsSinceClaim);
            })
            .orElse(0);
    }

    @Transactional
    protected void ensureBadgeCatalog() {
        Map<String, BadgeEntity> badgesByName = badgeRepository.findAllByOrderByBadgeIdAsc().stream()
            .collect(Collectors.toMap(BadgeEntity::getName, badge -> badge, (left, right) -> left));

        for (BadgeSeed seed : defaultBadgeSeeds()) {
            if (badgesByName.containsKey(seed.name())) {
                continue;
            }

            BadgeEntity badge = new BadgeEntity();
            badge.setName(seed.name());
            badge.setDescription(seed.description());
            badge.setIconUrl(seed.iconUrl());
            badgeRepository.save(badge);
        }
    }

    private void syncUnlockedBadges(Integer userId, ProfileStatsDto stats) {
        Map<String, BadgeEntity> badgesByName = badgeRepository.findAllByOrderByBadgeIdAsc().stream()
            .collect(Collectors.toMap(BadgeEntity::getName, badge -> badge, (left, right) -> left));

        for (String badgeName : eligibleBadgeNames(stats)) {
            BadgeEntity badge = badgesByName.get(badgeName);
            if (badge == null) {
                continue;
            }

            UserBadgeId id = new UserBadgeId(userId, badge.getBadgeId());
            if (userBadgeRepository.existsById(id)) {
                continue;
            }

            UserBadgeEntity userBadge = new UserBadgeEntity();
            userBadge.setId(id);
            userBadge.setUser(userRepository.getReferenceById(userId));
            userBadge.setBadge(badge);
            userBadgeRepository.save(userBadge);
        }
    }

    private Set<String> eligibleBadgeNames(ProfileStatsDto stats) {
        Set<String> names = new LinkedHashSet<>();
        if (stats.battlesWon() >= 1) {
            names.add("Primer Knockout");
        }
        if (stats.bestMultiplier() >= 3.0) {
            names.add("Sangre Fria");
        }
        if (stats.ballRoomsPlayed() >= 5) {
            names.add("Trader Diario");
        }
        double winRate = stats.battlesPlayed() == 0
            ? 0.0
            : (double) stats.battlesWon() / (double) stats.battlesPlayed();
        if (stats.battlesPlayed() >= 6 && winRate >= 0.5) {
            names.add("Maestro Royale");
        }
        if (stats.rewardedAdsClaimed() >= 3) {
            names.add("Bonus Hunter");
        }
        return names;
    }

    private List<BadgeSeed> defaultBadgeSeeds() {
        List<BadgeSeed> seeds = new ArrayList<>();
        seeds.add(new BadgeSeed("Primer Knockout", "Gana tu primera batalla.", null));
        seeds.add(new BadgeSeed("Sangre Fria", "Consigue multiplicador x3 o superior.", null));
        seeds.add(new BadgeSeed("Trader Diario", "Juega 5 salas de bolas.", null));
        seeds.add(new BadgeSeed("Maestro Royale", "Mantiene winrate del 50% con 6 batallas.", null));
        seeds.add(new BadgeSeed("Bonus Hunter", "Reclama 3 rewarded ads.", null));
        return seeds;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record BadgeSeed(String name, String description, String iconUrl) {
    }
}
