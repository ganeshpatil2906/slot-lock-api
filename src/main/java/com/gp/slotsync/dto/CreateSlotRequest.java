package com.gp.slotsync.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record CreateSlotRequest(
    @NotNull(message = "Resource ID is required")
    Long resourceId,

    @NotNull(message = "Start time is required")
    LocalDateTime startTime,

    @NotNull(message = "End time is required")
    LocalDateTime endTime
) {}
