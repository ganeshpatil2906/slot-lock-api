package com.gp.slotsync.service;

import com.gp.slotsync.dto.BookingResponse;
import com.gp.slotsync.dto.ConfirmBookingRequest;
import com.gp.slotsync.dto.HoldSlotRequest;
import com.gp.slotsync.entity.Booking;
import com.gp.slotsync.entity.Slot;
import com.gp.slotsync.entity.User;
import com.gp.slotsync.enums.BookingStatus;
import com.gp.slotsync.enums.Role;
import com.gp.slotsync.enums.SlotStatus;
import com.gp.slotsync.exception.BookingConflictException;
import com.gp.slotsync.exception.InvalidRequestException;
import com.gp.slotsync.exception.ResourceNotFoundException;
import com.gp.slotsync.exception.SlotUnavailableException;
import com.gp.slotsync.repository.BookingRepository;
import com.gp.slotsync.repository.SlotRepository;
import com.gp.slotsync.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final SlotRepository slotRepository;
    private final UserRepository userRepository;
    private final int defaultHoldMinutes;

    public BookingService(BookingRepository bookingRepository,
                          SlotRepository slotRepository,
                          UserRepository userRepository,
                          @Value("${jwt.hold-default-minutes:15}") int defaultHoldMinutes) {
        this.bookingRepository = bookingRepository;
        this.slotRepository = slotRepository;
        this.userRepository = userRepository;
        this.defaultHoldMinutes = defaultHoldMinutes;
    }

    @Transactional
    public BookingResponse holdSlot(HoldSlotRequest request, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEmail));

        // Idempotency check
        Optional<Booking> existingBookingOpt = bookingRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existingBookingOpt.isPresent()) {
            Booking existing = existingBookingOpt.get();
            if (!existing.getUser().getId().equals(user.getId())) {
                throw new BookingConflictException("Idempotency key belongs to another user");
            }
            return BookingResponse.fromEntity(existing);
        }

        Slot slot = slotRepository.findById(request.slotId())
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found with id: " + request.slotId()));

        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw new SlotUnavailableException("Slot is not available for booking. Current status: " + slot.getStatus());
        }

        int holdMinutes = (request.holdDurationMinutes() != null && request.holdDurationMinutes() > 0)
                ? request.holdDurationMinutes()
                : defaultHoldMinutes;

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(holdMinutes);

        // Update slot status (triggers Optimistic Lock check via @Version)
        slot.setStatus(SlotStatus.HELD);
        slotRepository.save(slot);

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setSlot(slot);
        booking.setStatus(BookingStatus.HELD);
        booking.setIdempotencyKey(request.idempotencyKey());
        booking.setHoldExpiresAt(expiresAt);

        Booking savedBooking = bookingRepository.save(booking);
        return BookingResponse.fromEntity(savedBooking);
    }

    @Transactional
    public BookingResponse confirmBooking(ConfirmBookingRequest request, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEmail));

        Booking booking = bookingRepository.findByIdempotencyKey(request.idempotencyKey())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found for idempotency key: " + request.idempotencyKey()));

        if (!booking.getUser().getId().equals(user.getId())) {
            throw new BookingConflictException("Booking does not belong to user");
        }

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            return BookingResponse.fromEntity(booking);
        }

        if (booking.getStatus() != BookingStatus.HELD) {
            throw new InvalidRequestException("Booking cannot be confirmed from status: " + booking.getStatus());
        }

        if (booking.getHoldExpiresAt() != null && booking.getHoldExpiresAt().isBefore(LocalDateTime.now())) {
            // Expire hold
            booking.setStatus(BookingStatus.EXPIRED);
            Slot slot = booking.getSlot();
            slot.setStatus(SlotStatus.AVAILABLE);
            slotRepository.save(slot);
            bookingRepository.save(booking);
            throw new SlotUnavailableException("Hold has expired. Please re-hold the slot.");
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setHoldExpiresAt(null);

        Slot slot = booking.getSlot();
        slot.setStatus(SlotStatus.BOOKED);
        slotRepository.save(slot);

        Booking saved = bookingRepository.save(booking);
        return BookingResponse.fromEntity(saved);
    }

    @Transactional
    public BookingResponse cancelBooking(Long bookingId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEmail));

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        if (!booking.getUser().getId().equals(user.getId()) && user.getRole() != Role.ADMIN) {
            throw new BookingConflictException("Not authorized to cancel this booking");
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return BookingResponse.fromEntity(booking);
        }

        if (booking.getStatus() == BookingStatus.EXPIRED) {
            throw new InvalidRequestException("Cannot cancel an expired booking");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        Slot slot = booking.getSlot();
        slot.setStatus(SlotStatus.AVAILABLE);
        slotRepository.save(slot);

        Booking saved = bookingRepository.save(booking);
        return BookingResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getUserBookings(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEmail));

        return bookingRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(BookingResponse::fromEntity)
                .toList();
    }
}
