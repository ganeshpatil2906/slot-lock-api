package com.gp.slotsync.ai.service;

import com.gp.slotsync.ai.dto.SlotRecommendationResponse;
import com.gp.slotsync.entity.Slot;
import com.gp.slotsync.enums.SlotStatus;
import com.gp.slotsync.repository.SlotRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class SlotOptimizerService {

    private final SlotRepository slotRepository;

    public SlotOptimizerService(SlotRepository slotRepository) {
        this.slotRepository = slotRepository;
    }

    /**
     * Computes AI schedule optimization and recommends slots that minimize calendar fragmentation.
     */
    public List<SlotRecommendationResponse> getRecommendations(Long resourceId, Integer preferredDurationMinutes) {
        List<Slot> availableSlots;
        if (resourceId != null) {
            availableSlots = slotRepository.findByResourceIdAndStatus(resourceId, SlotStatus.AVAILABLE);
        } else {
            availableSlots = slotRepository.findByStatus(SlotStatus.AVAILABLE);
        }

        if (availableSlots.isEmpty()) {
            return Collections.emptyList();
        }

        // Filter out past slots
        LocalDateTime now = LocalDateTime.now();
        List<Slot> futureSlots = availableSlots.stream()
                .filter(s -> s.getStartTime().isAfter(now))
                .sorted(Comparator.comparing(Slot::getStartTime))
                .toList();

        if (futureSlots.isEmpty()) {
            return Collections.emptyList();
        }

        // Cache all slots for adjacency evaluation
        List<Slot> allResourceSlots = resourceId != null 
                ? slotRepository.findByResourceId(resourceId) 
                : slotRepository.findAll();

        List<SlotRecommendationResponse> recommendations = new ArrayList<>();

        for (Slot target : futureSlots) {
            double score = 50.0; // Base score
            List<String> reasons = new ArrayList<>();

            // Duration match check
            long duration = Duration.between(target.getStartTime(), target.getEndTime()).toMinutes();
            if (preferredDurationMinutes != null && preferredDurationMinutes > 0) {
                if (duration == preferredDurationMinutes) {
                    score += 20.0;
                    reasons.add("Exact duration match (" + duration + "m)");
                } else if (duration > preferredDurationMinutes) {
                    score += 5.0;
                }
            }

            // Adjacency / Gap analysis
            boolean hasPrecedingContiguous = false;
            boolean hasFollowingContiguous = false;
            boolean leavesAwkwardGap = false;

            for (Slot other : allResourceSlots) {
                if (other.getId().equals(target.getId())) continue;
                if (!other.getResource().getId().equals(target.getResource().getId())) continue;

                // Preceding booking check
                if (other.getEndTime().equals(target.getStartTime())) {
                    if (other.getStatus() == SlotStatus.BOOKED || other.getStatus() == SlotStatus.HELD) {
                        hasPrecedingContiguous = true;
                    }
                } else if (other.getEndTime().isBefore(target.getStartTime())) {
                    long gapMinutes = Duration.between(other.getEndTime(), target.getStartTime()).toMinutes();
                    if (gapMinutes > 0 && gapMinutes <= 20) {
                        leavesAwkwardGap = true;
                    }
                }

                // Following booking check
                if (other.getStartTime().equals(target.getEndTime())) {
                    if (other.getStatus() == SlotStatus.BOOKED || other.getStatus() == SlotStatus.HELD) {
                        hasFollowingContiguous = true;
                    }
                } else if (other.getStartTime().isAfter(target.getEndTime())) {
                    long gapMinutes = Duration.between(target.getEndTime(), other.getStartTime()).toMinutes();
                    if (gapMinutes > 0 && gapMinutes <= 20) {
                        leavesAwkwardGap = true;
                    }
                }
            }

            if (hasPrecedingContiguous && hasFollowingContiguous) {
                score += 35.0;
                reasons.add("Perfect bridge: completely connects two existing bookings");
            } else if (hasPrecedingContiguous || hasFollowingContiguous) {
                score += 25.0;
                reasons.add("Back-to-back schedule packing: eliminates orphaned idle time");
            }

            if (leavesAwkwardGap) {
                score -= 15.0;
                reasons.add("Leaves minor idle gap with neighboring slot");
            }

            // Prime working hours boost (9am to 5pm)
            int startHour = target.getStartTime().getHour();
            if (startHour >= 9 && startHour < 17) {
                score += 15.0;
                reasons.add("Prime business window (" + startHour + ":00)");
            }

            score = Math.max(10.0, Math.min(100.0, score));
            double roundedScore = Math.round(score * 10.0) / 10.0;

            String badge;
            if (roundedScore >= 85.0) {
                badge = "⭐ Best Match • Gap Saver";
            } else if (roundedScore >= 70.0) {
                badge = "⚡ Prime Utilization";
            } else {
                badge = "📅 Standard Slot";
            }

            String summaryReason = reasons.isEmpty() ? "Standard open slot" : String.join(" • ", reasons);

            recommendations.add(new SlotRecommendationResponse(
                    target.getId(),
                    target.getResource().getId(),
                    target.getResource().getName(),
                    target.getStartTime(),
                    target.getEndTime(),
                    roundedScore,
                    badge,
                    summaryReason
            ));
        }

        // Return sorted by efficiency score descending
        return recommendations.stream()
                .sorted(Comparator.comparingDouble(SlotRecommendationResponse::efficiencyScore).reversed())
                .limit(10)
                .toList();
    }
}
