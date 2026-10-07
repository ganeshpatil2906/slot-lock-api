package com.gp.slotsync.dto;

import com.gp.slotsync.entity.Slot;
import com.gp.slotsync.enums.SlotStatus;
import java.time.LocalDateTime;

public record SlotResponse(
    Long id,
    Long resourceId,
    String resourceName,
    LocalDateTime startTime,
    LocalDateTime endTime,
    SlotStatus status,
    Long version
) {
    public static SlotResponse fromEntity(Slot slot) {
        return new SlotResponse(
            slot.getId(),
            slot.getResource().getId(),
            slot.getResource().getName(),
            slot.getStartTime(),
            slot.getEndTime(),
            slot.getStatus(),
            slot.getVersion()
        );
    }
}
