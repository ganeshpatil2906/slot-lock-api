package com.gp.slotsync.ai.dto;

public record AbuseAuditResponse(
    Long userId,
    String email,
    double riskScore,
    String riskLevel,
    int activeHoldsCount,
    int recentHoldsCount,
    double abandonmentRate,
    boolean flagged,
    String recommendation
) {}
