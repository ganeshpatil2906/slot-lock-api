package com.gp.slotsync.scheduler;

import com.gp.slotsync.entity.Booking;
import com.gp.slotsync.entity.Slot;
import com.gp.slotsync.enums.BookingStatus;
import com.gp.slotsync.enums.SlotStatus;
import com.gp.slotsync.repository.BookingRepository;
import com.gp.slotsync.repository.SlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class HoldCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(HoldCleanupScheduler.class);

    private final BookingRepository bookingRepository;
    private final SlotRepository slotRepository;

    public HoldCleanupScheduler(BookingRepository bookingRepository, SlotRepository slotRepository) {
        this.bookingRepository = bookingRepository;
        this.slotRepository = slotRepository;
    }

    @Scheduled(fixedRateString = "${jwt.hold-cleanup-rate-ms:30000}")
    @Transactional
    public void cleanupExpiredHolds() {
        LocalDateTime now = LocalDateTime.now();
        List<Booking> expiredBookings = bookingRepository.findByStatusAndHoldExpiresAtBefore(BookingStatus.HELD, now);

        if (!expiredBookings.isEmpty()) {
            log.info("Found {} expired booking holds to clean up at {}", expiredBookings.size(), now);
            for (Booking booking : expiredBookings) {
                booking.setStatus(BookingStatus.EXPIRED);
                Slot slot = booking.getSlot();
                if (slot != null && slot.getStatus() == SlotStatus.HELD) {
                    slot.setStatus(SlotStatus.AVAILABLE);
                    slotRepository.save(slot);
                }
                bookingRepository.save(booking);
                log.info("Expired booking id: {} and released slot id: {}", booking.getId(), slot != null ? slot.getId() : null);
            }
        }
    }
}
