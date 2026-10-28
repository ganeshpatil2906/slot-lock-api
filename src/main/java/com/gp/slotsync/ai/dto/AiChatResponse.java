package com.gp.slotsync.ai.dto;

import java.util.List;
import java.util.Map;

public record AiChatResponse(
    String reply,
    String detectedIntent,
    List<AiActionSuggestion> actions,
    Map<String, Object> metadata
) {}
