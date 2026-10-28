package com.gp.slotsync.ai.service;

import com.gp.slotsync.ai.dto.AbuseAuditResponse;
import com.gp.slotsync.entity.User;
import com.gp.slotsync.enums.BookingStatus;
import com.gp.slotsync.enums.Role;
import com.gp.slotsync.exception.AbuseDetectedException;
import com.gp.slotsync.repository.BookingRepository;
import com.gp.slotsync.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class AbuseDetectionService {

    private static final Logger log = LoggerFactory.getLogger(AbuseDetectionService.class);

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final boolean enabled;
    private final int maxHoldsPerMinute;
    private final int maxConcurrentHolds;

    // Per-user sliding window timestamps of hold operations
    private final Map<Long, ConcurrentLinkedDeque<Instant>> userHoldVelocity = new ConcurrentHashMap<>();

    public AbuseDetectionService(
            BookingRepository bookingRepository,
            UserRepository userRepository,
            @Value("${slotsync.ai.abuse-shield.enabled:true}") boolean enabled,
            @Value("${slotsync.ai.abuse-shield.max-holds-per-minute:5}") int maxHoldsPerMinute,
            @Value("${slotsync.ai.abuse-shield.max-concurrent-holds:3}") int maxConcurrentHolds) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.enabled = enabled;
        this.maxHoldsPerMinute = maxHoldsPerMinute;
        this.maxConcurrentHolds = maxConcurrentHolds;
    }

    /**
     * Validates whether a user is permitted to hold a slot or blocked by the bot/scalper shield.
     */
    public void validateHoldAllowed(User user) {
        if (!enabled || user.getRole() == Role.ADMIN) {
            return; // Admins bypass restrictions
        }

        Instant cutoff = Instant.now().minus(Duration.ofMinutes(1));
        ConcurrentLinkedDeque<Instant> timestamps = userHoldVelocity.computeIfAbsent(user.getId(), k -> new ConcurrentLinkedDeque<>());
        timestamps.removeIf(instant -> instant.isBefore(cutoff));

        // 1. Velocity rate limit check
        if (timestamps.size() >= maxHoldsPerMinute) {
            log.warn("AI Abuse Shield: User {} ({}) triggered velocity rate limit with {} holds in last 60s",
                    user.getId(), user.getEmail(), timestamps.size());
            throw new AbuseDetectedException("AI Bot Shield: Rate limit exceeded. Too many hold requests in a short window. Please wait a minute before requesting another hold.");
        }

        // 2. Concurrent active unconfirmed holds check
        long activeHolds = bookingRepository.countByUserIdAndStatus(user.getId(), BookingStatus.HELD);
        if (activeHolds >= maxConcurrentHolds) {
            log.warn("AI Abuse Shield: User {} ({}) reached maximum concurrent active holds ({})",
                    user.getId(), user.getEmail(), activeHolds);
            throw new AbuseDetectedException("AI Bot Shield: You already have " + activeHolds + " unconfirmed active holds. Please confirm or cancel an existing hold before acquiring more slots.");
        }

        // Record this hold attempt
        timestamps.add(Instant.now());
    }

    /**
     * Assesses a user's risk profile based on behavioral heuristics.
     */
    public AbuseAuditResponse assessUserRisk(User user) {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(1));
        ConcurrentLinkedDeque<Instant> timestamps = userHoldVelocity.get(user.getId());
        int recentHolds = 0;
        if (timestamps != null) {
            timestamps.removeIf(instant -> instant.isBefore(cutoff));
            recentHolds = timestamps.size();
        }

        long activeHolds = bookingRepository.countByUserIdAndStatus(user.getId(), BookingStatus.HELD);
        long totalBookings = bookingRepository.countByUserId(user.getId());
        long expiredBookings = bookingRepository.countByUserIdAndStatus(user.getId(), BookingStatus.EXPIRED);
        long cancelledBookings = bookingRepository.countByUserIdAndStatus(user.getId(), BookingStatus.CANCELLED);

        double abandonmentRate = 0.0;
        if (totalBookings > 0) {
            abandonmentRate = (double) (expiredBookings + cancelledBookings) / totalBookings;
        }

        // Compute AI Risk Score (0.0 to 1.0)
        double score = 0.0;
        if (recentHolds >= maxHoldsPerMinute - 1) score += 0.35;
        if (activeHolds >= maxConcurrentHolds) score += 0.35;
        if (totalBookings >= 4 && abandonmentRate >= 0.70) score += 0.30;

        score = Math.min(1.0, score);

        String riskLevel;
        boolean flagged = false;
        String recommendation;

        if (score >= 0.70) {
            riskLevel = "HIGH_RISK";
            flagged = true;
            recommendation = "Suspicious hoarding/bot activity pattern. Automatic hold throttling engaged.";
        } else if (score >= 0.35) {
            riskLevel = "MEDIUM_RISK";
            recommendation = "Elevated hold volume observed. User nearing concurrent hold threshold.";
        } else {
            riskLevel = "LOW_RISK";
            recommendation = "Standard user booking pattern. No abusive signals detected.";
        }

        return new AbuseAuditResponse(
                user.getId(),
                user.getEmail(),
                Math.round(score * 100.0) / 100.0,
                riskLevel,
                (int) activeHolds,
                recentHolds,
                Math.round(abandonmentRate * 100.0) / 100.0,
                flagged,
                recommendation
        );
    }

    /**
     * Admin audit report of all users with risk profiles.
     */
    public List<AbuseAuditResponse> auditAllUsers() {
        return userRepository.findAll().stream()
                .map(this::assessUserRisk)
                .sorted(Comparator.comparingDouble(AbuseAuditResponse::riskScore).reversed())
                .toList();
    }
}
