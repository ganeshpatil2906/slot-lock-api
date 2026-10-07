package com.gp.slotsync.repository;

import com.gp.slotsync.entity.Booking;
import com.gp.slotsync.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByIdempotencyKey(String idempotencyKey);
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Booking> findByStatusAndHoldExpiresAtBefore(BookingStatus status, LocalDateTime now);
    Optional<Booking> findBySlotIdAndStatusIn(Long slotId, List<BookingStatus> statuses);
}

