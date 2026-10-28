package com.gp.slotsync.ai.dto;

import java.util.List;

public record ContentionAnalyticsResponse(
    int totalSlots,
    int heldSlots,
    int bookedSlots,
    double averageContentionScore,
    List<DynamicTtlResponse> highContentionSlots,
    List<String> autoTuningRecommendations
) {}
