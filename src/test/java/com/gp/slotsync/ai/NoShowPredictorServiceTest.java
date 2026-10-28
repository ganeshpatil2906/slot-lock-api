package com.gp.slotsync.ai;

import com.gp.slotsync.ai.dto.NoShowPredictionResponse;
import com.gp.slotsync.ai.service.NoShowPredictorService;
import com.gp.slotsync.entity.Booking;
import com.gp.slotsync.entity.Slot;
import com.gp.slotsync.entity.User;
import com.gp.slotsync.enums.BookingStatus;
import com.gp.slotsync.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NoShowPredictorServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    private NoShowPredictorService predictorService;

    @BeforeEach
    void setUp() {
        predictorService = new NoShowPredictorService(bookingRepository);
    }

    private void setId(Object target, Long id) {
        try {
            Field field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void predict_FarAdvanceAndEarlyMorning_HasHighRisk() {
        User user = new User();
        setId(user, 10L);

        Slot slot = new Slot();
        setId(slot, 100L);
        // 20 days in advance, 7 AM morning
        slot.setStartTime(LocalDateTime.now().plusDays(20).withHour(7).withMinute(0));
        slot.setEndTime(LocalDateTime.now().plusDays(20).withHour(8).withMinute(0));

        Booking booking = new Booking();
        setId(booking, 500L);
        booking.setUser(user);
        booking.setSlot(slot);
        booking.setStatus(BookingStatus.CONFIRMED);

        NoShowPredictionResponse response = predictorService.predictForBooking(booking);

        assertNotNull(response);
        assertTrue(response.noShowProbability() > 0.45);
        assertFalse(response.riskFactors().isEmpty());
    }

    @Test
    void predict_SameDayPrimeHours_HasLowRisk() {
        User user = new User();
        setId(user, 11L);

        Slot slot = new Slot();
        setId(slot, 101L);
        // Today, 2 hours from now, 2 PM
        slot.setStartTime(LocalDateTime.now().plusHours(2).withHour(14).withMinute(0));
        slot.setEndTime(LocalDateTime.now().plusHours(3).withHour(15).withMinute(0));

        Booking booking = new Booking();
        setId(booking, 501L);
        booking.setUser(user);
        booking.setSlot(slot);
        booking.setStatus(BookingStatus.CONFIRMED);

        NoShowPredictionResponse response = predictorService.predictForBooking(booking);

        assertNotNull(response);
        assertTrue(response.noShowProbability() <= 0.35);
        assertEquals("LOW_RISK", response.riskCategory());
    }
}
