package com.gp.slotsync.ai.dto;

import java.util.List;

public record NoShowPredictionResponse(
    Long bookingId,
    double noShowProbability,
    String riskCategory,
    List<String> riskFactors,
    String recommendation
) {}
