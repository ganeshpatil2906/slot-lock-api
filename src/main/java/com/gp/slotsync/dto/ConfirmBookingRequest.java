package com.gp.slotsync.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmBookingRequest(
    @NotBlank(message = "Idempotency key is required")
    String idempotencyKey
) {}
