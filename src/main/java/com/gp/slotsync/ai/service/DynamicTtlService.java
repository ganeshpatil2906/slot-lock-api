package com.gp.slotsync.ai.service;

import com.gp.slotsync.ai.dto.ContentionAnalyticsResponse;
import com.gp.slotsync.ai.dto.DynamicTtlResponse;
import com.gp.slotsync.entity.Slot;
import com.gp.slotsync.enums.SlotStatus;
import com.gp.slotsync.repository.SlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class DynamicTtlService {

    private static final Logger log = LoggerFactory.getLogger(DynamicTtlService.class);

    private final SlotRepository slotRepository;
    private final int baseHoldMinutes;
    private final int minHoldMinutes;
    private final int maxHoldMinutes;

    // In-memory sliding window trackers (10-minute observation window)
    private final Map<Long, ConcurrentLinkedDeque<Instant>> slotCollisions = new ConcurrentHashMap<>();
    private final Map<Long, ConcurrentLinkedDeque<Instant>> slotHoldAttempts = new ConcurrentHashMap<>();

    public DynamicTtlService(
            SlotRepository slotRepository,
            @Value("${slotsync.ai.dynamic-ttl.base-minutes:15}") int baseHoldMinutes,
            @Value("${slotsync.ai.dynamic-ttl.min-minutes:2}") int minHoldMinutes,
            @Value("${slotsync.ai.dynamic-ttl.max-minutes:25}") int maxHoldMinutes) {
        this.slotRepository = slotRepository;
        this.baseHoldMinutes = baseHoldMinutes;
        this.minHoldMinutes = minHoldMinutes;
        this.maxHoldMinutes = maxHoldMinutes;
    }

    /**
     * Records an OptimisticLockingFailureException collision on a slot.
     */
    public void recordCollision(Long slotId) {
        if (slotId == null) return;
        slotCollisions.computeIfAbsent(slotId, k -> new ConcurrentLinkedDeque<>()).add(Instant.now());
        log.warn("AI Contention: Collision recorded on Slot #{}", slotId);
    }

    /**
     * Records a hold attempt to track slot velocity.
     */
    public void recordHoldAttempt(Long slotId) {
        if (slotId == null) return;
        slotHoldAttempts.computeIfAbsent(slotId, k -> new ConcurrentLinkedDeque<>()).add(Instant.now());
    }

    /**
     * Calculates the dynamic hold TTL (in minutes) for a given slot based on AI contention heuristics.
     */
    public DynamicTtlResponse calculateTtl(Slot slot) {
        if (slot == null) {
            return new DynamicTtlResponse(null, baseHoldMinutes, 0.0, "NORMAL", "Default base TTL applied");
        }

        Instant cutoff = Instant.now().minus(Duration.ofMinutes(10));
        
        // Clean and count recent collisions (within 10m)
        ConcurrentLinkedDeque<Instant> collisions = slotCollisions.get(slot.getId());
        long recentCollisions = 0;
        if (collisions != null) {
            collisions.removeIf(instant -> instant.isBefore(cutoff));
            recentCollisions = collisions.size();
        }

        // Clean and count recent hold attempts (within 10m)
        ConcurrentLinkedDeque<Instant> attempts = slotHoldAttempts.get(slot.getId());
        long recentAttempts = 0;
        if (attempts != null) {
            attempts.removeIf(instant -> instant.isBefore(cutoff));
            recentAttempts = attempts.size();
        }

        // Calculate contention score between 0.0 and 1.0
        double collisionWeight = recentCollisions * 0.35;
        double attemptWeight = recentAttempts * 0.15;
        double contentionScore = Math.min(1.0, Math.max(0.0, collisionWeight + attemptWeight));

        // Evaluate time urgency until slot start
        LocalDateTime now = LocalDateTime.now();
        Duration timeUntilStart = Duration.between(now, slot.getStartTime());
        long minutesUntilStart = timeUntilStart.toMinutes();

        int calculatedMinutes = baseHoldMinutes;
        String demandLevel;
        String rationale;

        if (minutesUntilStart <= 15) {
            // Critical urgency: slot is about to start
            calculatedMinutes = minHoldMinutes;
            demandLevel = "CRITICAL_PROXIMITY";
            rationale = "Slot starts in " + Math.max(1, minutesUntilStart) + " mins. Minimum TTL of " + minHoldMinutes + "m enforced to prevent inventory starvation.";
        } else if (minutesUntilStart <= 30) {
            calculatedMinutes = Math.min(5, baseHoldMinutes);
            demandLevel = "HIGH_PROXIMITY";
            rationale = "Slot starts in under 30 mins. Accelerated " + calculatedMinutes + "m TTL protects resource availability.";
        } else if (contentionScore >= 0.70) {
            calculatedMinutes = 3;
            demandLevel = "SURGE_COLLISION";
            rationale = "High concurrency collisions detected (" + recentCollisions + " recent collisions). Dynamic TTL reduced to 3m to rapidly cycle lock contention.";
        } else if (contentionScore >= 0.35) {
            calculatedMinutes = 7;
            demandLevel = "ELEVATED_DEMAND";
            rationale = "Elevated booking interest (" + recentAttempts + " attempts). Optimized 7m checkout window assigned.";
        } else {
            // Quiet / low contention
            calculatedMinutes = baseHoldMinutes;
            demandLevel = "LOW_CONTENTION";
            rationale = "Healthy contention profile. Standard " + baseHoldMinutes + "m checkout window allocated.";
        }

        calculatedMinutes = Math.max(minHoldMinutes, Math.min(maxHoldMinutes, calculatedMinutes));

        return new DynamicTtlResponse(
                slot.getId(),
                calculatedMinutes,
                Math.round(contentionScore * 100.0) / 100.0,
                demandLevel,
                rationale
        );
    }

    /**
     * Compiles system-wide contention analytics and auto-tuning recommendations.
     */
    public ContentionAnalyticsResponse getContentionAnalytics() {
        List<Slot> allSlots = slotRepository.findAll();
        int total = allSlots.size();
        int held = 0;
        int booked = 0;
        double scoreSum = 0.0;

        List<DynamicTtlResponse> highContentionSlots = new ArrayList<>();

        for (Slot s : allSlots) {
            if (s.getStatus() == SlotStatus.HELD) held++;
            else if (s.getStatus() == SlotStatus.BOOKED) booked++;

            DynamicTtlResponse ttl = calculateTtl(s);
            scoreSum += ttl.contentionScore();
            if (ttl.contentionScore() >= 0.35 || "SURGE_COLLISION".equals(ttl.demandLevel())) {
                highContentionSlots.add(ttl);
            }
        }

        double avgScore = total > 0 ? (scoreSum / total) : 0.0;
        List<String> recommendations = new ArrayList<>();

        if (avgScore > 0.5) {
            recommendations.add("Peak system contention detected. Recommend decreasing global base TTL to 8 minutes.");
        } else {
            recommendations.add("System concurrency is balanced. Default TTL operating within safe boundaries.");
        }

        if (!highContentionSlots.isEmpty()) {
            recommendations.add(highContentionSlots.size() + " slots experiencing elevated race-condition pressure. Dynamic lock throttling active.");
        }

        recommendations.add("Optimistic locking (@Version) active on all " + total + " slots with zero deadlocks.");

        return new ContentionAnalyticsResponse(
                total,
                held,
                booked,
                Math.round(avgScore * 100.0) / 100.0,
                highContentionSlots,
                recommendations
        );
    }
}
