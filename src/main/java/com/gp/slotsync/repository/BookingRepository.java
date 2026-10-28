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
    long countByUserIdAndStatus(Long userId, BookingStatus status);
    long countByUserId(Long userId);
    List<Booking> findByUserId(Long userId);
    List<Booking> findTop100ByOrderByCreatedAtDesc();
    List<Booking> findBySlotResourceId(Long resourceId);
    List<Booking> findBySlotId(Long slotId);
}

