package com.gp.slotsync.ai.service;

import com.gp.slotsync.ai.dto.NoShowPredictionResponse;
import com.gp.slotsync.entity.Booking;
import com.gp.slotsync.entity.Slot;
import com.gp.slotsync.enums.BookingStatus;
import com.gp.slotsync.exception.ResourceNotFoundException;
import com.gp.slotsync.repository.BookingRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class NoShowPredictorService {

    private final BookingRepository bookingRepository;

    public NoShowPredictorService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    /**
     * Calculates the predictive no-show / cancellation probability for a booking.
     */
    public NoShowPredictionResponse predict(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        return predictForBooking(booking);
    }

    public NoShowPredictionResponse predictForBooking(Booking booking) {
        Slot slot = booking.getSlot();
        LocalDateTime createdAt = booking.getCreatedAt() != null ? booking.getCreatedAt() : LocalDateTime.now();
        LocalDateTime startTime = slot.getStartTime();

        double probability = 0.15; // Baseline no-show/cancellation probability
        List<String> factors = new ArrayList<>();

        // 1. Advance lead-time factor
        long hoursInAdvance = Duration.between(createdAt, startTime).toHours();
        if (hoursInAdvance > 24 * 14) {
            probability += 0.25;
            factors.add("Long lead time (" + (hoursInAdvance / 24) + " days in advance) increases change-of-plans risk");
        } else if (hoursInAdvance > 24 * 7) {
            probability += 0.12;
            factors.add("Moderate lead time (" + (hoursInAdvance / 24) + " days)");
        } else if (hoursInAdvance <= 24) {
            probability -= 0.08;
            factors.add("Immediate / same-day reservation typically exhibits high attendance");
        }

        // 2. Time-of-day factor
        int hour = startTime.getHour();
        if (hour < 8) {
            probability += 0.18;
            factors.add("Early morning slot (" + hour + ":00) has higher historical absenteeism");
        } else if (hour >= 19) {
            probability += 0.14;
            factors.add("Late evening slot (" + hour + ":00) has elevated cancellation variance");
        } else if (hour >= 10 && hour <= 15) {
            probability -= 0.05;
            factors.add("Prime midday business hours have strong commitment rates");
        }

        // 3. Day of week
        DayOfWeek dow = startTime.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            probability += 0.08;
            factors.add("Weekend slot has higher flexibility cancellations");
        }

        // 4. User's historical behavior
        if (booking.getUser() != null) {
            long total = bookingRepository.countByUserId(booking.getUser().getId());
            if (total >= 3) {
                long cancelled = bookingRepository.countByUserIdAndStatus(booking.getUser().getId(), BookingStatus.CANCELLED);
                long expired = bookingRepository.countByUserIdAndStatus(booking.getUser().getId(), BookingStatus.EXPIRED);
                double userCancelRate = (double) (cancelled + expired) / total;

                if (userCancelRate >= 0.50) {
                    probability += 0.22;
                    factors.add("User historical cancellation/abandonment rate is high (" + Math.round(userCancelRate * 100) + "%)");
                } else if (userCancelRate <= 0.10) {
                    probability -= 0.08;
                    factors.add("User has strong track record of confirmed attendance (" + Math.round((1 - userCancelRate) * 100) + "%)");
                }
            }
        }

        probability = Math.max(0.05, Math.min(0.95, probability));
        double roundedProb = Math.round(probability * 100.0) / 100.0;

        String riskCategory;
        String recommendation;

        if (roundedProb >= 0.55) {
            riskCategory = "HIGH_RISK";
            recommendation = "Send automated re-confirmation prompt 3 hours prior; eligible for intelligent standby overbooking.";
        } else if (roundedProb >= 0.30) {
            riskCategory = "MODERATE_RISK";
            recommendation = "Standard automated calendar reminder recommended 24 hours in advance.";
        } else {
            riskCategory = "LOW_RISK";
            recommendation = "High attendance certainty. No special intervention required.";
        }

        return new NoShowPredictionResponse(
                booking.getId(),
                roundedProb,
                riskCategory,
                factors,
                recommendation
        );
    }
}
