package com.gp.slotsync.dto;

import com.gp.slotsync.entity.Booking;
import com.gp.slotsync.enums.BookingStatus;
import java.time.LocalDateTime;

public record BookingResponse(
    Long id,
    Long userId,
    String userName,
    String userEmail,
    Long slotId,
    Long resourceId,
    String resourceName,
    LocalDateTime slotStartTime,
    LocalDateTime slotEndTime,
    BookingStatus status,
    String idempotencyKey,
    LocalDateTime holdExpiresAt,
    LocalDateTime createdAt
) {
    public static BookingResponse fromEntity(Booking booking) {
        return new BookingResponse(
            booking.getId(),
            booking.getUser().getId(),
            booking.getUser().getName(),
            booking.getUser().getEmail(),
            booking.getSlot().getId(),
            booking.getSlot().getResource().getId(),
            booking.getSlot().getResource().getName(),
            booking.getSlot().getStartTime(),
            booking.getSlot().getEndTime(),
            booking.getStatus(),
            booking.getIdempotencyKey(),
            booking.getHoldExpiresAt(),
            booking.getCreatedAt()
        );
    }
}
