package com.gp.slotsync.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record AiChatRequest(
    @NotBlank(message = "Message cannot be empty")
    String message,
    String conversationId
) {}
