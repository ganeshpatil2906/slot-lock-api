package com.gp.slotsync.controller;

import com.gp.slotsync.dto.BookingResponse;
import com.gp.slotsync.dto.ConfirmBookingRequest;
import com.gp.slotsync.dto.HoldSlotRequest;
import com.gp.slotsync.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
@Tag(name = "Booking Operations", description = "Endpoints for holding, confirming, cancelling, and viewing bookings")
@SecurityRequirement(name = "bearerAuth")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/hold")
    @Operation(summary = "Hold a slot", description = "Temporarily places a slot on HELD status for a user with idempotency protection")
    public ResponseEntity<BookingResponse> holdSlot(@Valid @RequestBody HoldSlotRequest request,
                                                     @AuthenticationPrincipal UserDetails userDetails) {
        BookingResponse response = bookingService.holdSlot(request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/confirm")
    @Operation(summary = "Confirm a booking hold", description = "Confirms a previously held slot using its idempotency key")
    public ResponseEntity<BookingResponse> confirmBooking(@Valid @RequestBody ConfirmBookingRequest request,
                                                         @AuthenticationPrincipal UserDetails userDetails) {
        BookingResponse response = bookingService.confirmBooking(request, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a booking", description = "Cancels an active or held booking and releases the slot back to AVAILABLE")
    public ResponseEntity<BookingResponse> cancelBooking(@PathVariable Long id,
                                                         @AuthenticationPrincipal UserDetails userDetails) {
        BookingResponse response = bookingService.cancelBooking(id, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-bookings")
    @Operation(summary = "Get user bookings", description = "Retrieves all bookings made by the authenticated user")
    public ResponseEntity<List<BookingResponse>> getMyBookings(@AuthenticationPrincipal UserDetails userDetails) {
        List<BookingResponse> response = bookingService.getUserBookings(userDetails.getUsername());
        return ResponseEntity.ok(response);
    }
}
