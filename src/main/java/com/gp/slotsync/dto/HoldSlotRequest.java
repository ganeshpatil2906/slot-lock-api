package com.gp.slotsync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HoldSlotRequest(
    @NotNull(message = "Slot ID is required")
    Long slotId,

    @NotBlank(message = "Idempotency key is required")
    @Size(min = 8, max = 64, message = "Idempotency key must be between 8 and 64 characters")
    String idempotencyKey,

    Integer holdDurationMinutes
) {}
