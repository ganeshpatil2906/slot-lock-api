package com.gp.slotsync.ai.dto;

public record AiActionSuggestion(
    String label,
    String actionType,
    String payload
) {}
