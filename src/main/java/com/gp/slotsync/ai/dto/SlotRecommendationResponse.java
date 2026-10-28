package com.gp.slotsync.ai.dto;

import java.time.LocalDateTime;

public record SlotRecommendationResponse(
    Long slotId,
    Long resourceId,
    String resourceName,
    LocalDateTime startTime,
    LocalDateTime endTime,
    double efficiencyScore,
    String badge,
    String reason
) {}
