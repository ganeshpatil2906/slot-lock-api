package com.gp.slotsync.ai.dto;

public record DynamicTtlResponse(
    Long slotId,
    int recommendedTtlMinutes,
    double contentionScore,
    String demandLevel,
    String rationale
) {}
