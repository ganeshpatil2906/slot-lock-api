package com.gp.slotsync.service;

import com.gp.slotsync.dto.BookingResponse;
import com.gp.slotsync.dto.HoldSlotRequest;
import com.gp.slotsync.entity.Booking;
import com.gp.slotsync.entity.Resource;
import com.gp.slotsync.entity.Slot;
import com.gp.slotsync.entity.User;
import com.gp.slotsync.enums.BookingStatus;
import com.gp.slotsync.enums.Role;
import com.gp.slotsync.enums.SlotStatus;
import com.gp.slotsync.exception.SlotUnavailableException;
import com.gp.slotsync.repository.BookingRepository;
import com.gp.slotsync.repository.SlotRepository;
import com.gp.slotsync.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private SlotRepository slotRepository;
    @Mock private UserRepository userRepository;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(bookingRepository, slotRepository, userRepository, 15);
    }

    @Test
    void holdSlot_Success() {
        HoldSlotRequest request = new HoldSlotRequest(10L, "idempotency-key-123", 15);
        String userEmail = "user@example.com";

        User user = new User();
        user.setName("John Doe");
        user.setEmail(userEmail);
        user.setRole(Role.USER);

        Resource resource = new Resource();
        resource.setName("Room 101");
        resource.setType("CONFERENCE");

        Slot slot = new Slot();
        slot.setResource(resource);
        slot.setStartTime(LocalDateTime.now().plusHours(1));
        slot.setEndTime(LocalDateTime.now().plusHours(2));
        slot.setStatus(SlotStatus.AVAILABLE);

        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(bookingRepository.findByIdempotencyKey("idempotency-key-123")).thenReturn(Optional.empty());
        when(slotRepository.findById(10L)).thenReturn(Optional.of(slot));

        Booking savedBooking = new Booking();
        savedBooking.setUser(user);
        savedBooking.setSlot(slot);
        savedBooking.setStatus(BookingStatus.HELD);
        savedBooking.setIdempotencyKey("idempotency-key-123");
        savedBooking.setHoldExpiresAt(LocalDateTime.now().plusMinutes(15));

        when(bookingRepository.save(any(Booking.class))).thenReturn(savedBooking);

        BookingResponse response = bookingService.holdSlot(request, userEmail);

        assertNotNull(response);
        assertEquals(BookingStatus.HELD, response.status());
        assertEquals(SlotStatus.HELD, slot.getStatus());
        verify(slotRepository, times(1)).save(slot);
        verify(bookingRepository, times(1)).save(any(Booking.class));
    }

    @Test
    void holdSlot_UnavailableSlot_ThrowsException() {
        HoldSlotRequest request = new HoldSlotRequest(10L, "idempotency-key-123", 15);
        String userEmail = "user@example.com";

        User user = new User();
        user.setEmail(userEmail);

        Slot slot = new Slot();
        slot.setStatus(SlotStatus.BOOKED);

        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(bookingRepository.findByIdempotencyKey("idempotency-key-123")).thenReturn(Optional.empty());
        when(slotRepository.findById(10L)).thenReturn(Optional.of(slot));

        assertThrows(SlotUnavailableException.class, () -> bookingService.holdSlot(request, userEmail));
        verify(bookingRepository, never()).save(any(Booking.class));
    }
}
