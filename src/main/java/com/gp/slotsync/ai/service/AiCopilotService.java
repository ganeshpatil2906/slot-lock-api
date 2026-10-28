package com.gp.slotsync.ai.service;

import com.gp.slotsync.ai.dto.*;
import com.gp.slotsync.dto.BookingResponse;
import com.gp.slotsync.dto.ConfirmBookingRequest;
import com.gp.slotsync.dto.HoldSlotRequest;
import com.gp.slotsync.dto.SlotResponse;
import com.gp.slotsync.entity.Slot;
import com.gp.slotsync.entity.User;
import com.gp.slotsync.enums.BookingStatus;
import com.gp.slotsync.enums.SlotStatus;
import com.gp.slotsync.exception.ResourceNotFoundException;
import com.gp.slotsync.repository.SlotRepository;
import com.gp.slotsync.repository.UserRepository;
import com.gp.slotsync.service.BookingService;
import com.gp.slotsync.service.SlotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiCopilotService {

    private static final Logger log = LoggerFactory.getLogger(AiCopilotService.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MMM dd, hh:mm a");

    private final SlotService slotService;
    private final BookingService bookingService;
    private final DynamicTtlService dynamicTtlService;
    private final SlotOptimizerService slotOptimizerService;
    private final NoShowPredictorService noShowPredictorService;
    private final UserRepository userRepository;
    private final SlotRepository slotRepository;

    @Value("${slotsync.ai.provider:internal}")
    private String aiProvider;

    public AiCopilotService(
            SlotService slotService,
            BookingService bookingService,
            DynamicTtlService dynamicTtlService,
            SlotOptimizerService slotOptimizerService,
            NoShowPredictorService noShowPredictorService,
            UserRepository userRepository,
            SlotRepository slotRepository) {
        this.slotService = slotService;
        this.bookingService = bookingService;
        this.dynamicTtlService = dynamicTtlService;
        this.slotOptimizerService = slotOptimizerService;
        this.noShowPredictorService = noShowPredictorService;
        this.userRepository = userRepository;
        this.slotRepository = slotRepository;
    }

    /**
     * Main conversation router: takes natural language input from the user and executes tool-calling.
     */
    public AiChatResponse processMessage(AiChatRequest request, String userEmail) {
        String msg = request.message().trim();
        String lower = msg.toLowerCase();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEmail));

        log.info("AI Copilot processing message from {}: '{}'", userEmail, msg);

        // Intent 1: HOLD SLOT (e.g. "hold slot 3", "reserve slot #5", "book slot 12")
        Pattern holdPattern = Pattern.compile("(?:hold|reserve|book|grab)\\s+(?:slot\\s*(?:#|id)?\\s*)?(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher holdMatcher = holdPattern.matcher(lower);
        if (holdMatcher.find()) {
            Long slotId = Long.parseLong(holdMatcher.group(1));
            return handleHoldSlotIntent(slotId, user);
        }

        // Intent 2: CONFIRM BOOKING (e.g. "confirm booking", "confirm hold", "confirm")
        if (lower.contains("confirm")) {
            return handleConfirmBookingIntent(lower, user);
        }

        // Intent 3: CANCEL BOOKING (e.g. "cancel booking 4", "cancel #4")
        Pattern cancelPattern = Pattern.compile("cancel\\s+(?:booking\\s*(?:#|id)?\\s*)?(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher cancelMatcher = cancelPattern.matcher(lower);
        if (cancelMatcher.find()) {
            Long bookingId = Long.parseLong(cancelMatcher.group(1));
            return handleCancelBookingIntent(bookingId, user);
        }

        // Intent 4: RECOMMEND / OPTIMIZE (e.g. "recommend slot", "best slot", "optimize", "gap saver")
        if (lower.contains("recommend") || lower.contains("optimize") || lower.contains("best slot") || lower.contains("gap")) {
            return handleRecommendationIntent();
        }

        // Intent 5: MY BOOKINGS (e.g. "my bookings", "what did i book", "show reservations")
        if (lower.contains("my booking") || lower.contains("my reservation") || lower.contains("what did i book")) {
            return handleMyBookingsIntent(user);
        }

        // Intent 6: SEARCH / BROWSE SLOTS (e.g. "available slots", "find rooms", "browse", "show slots")
        if (lower.contains("slot") || lower.contains("room") || lower.contains("available") || lower.contains("find") || lower.contains("search")) {
            return handleSearchSlotsIntent();
        }

        // Intent 7: EXPLAIN CONTENTION & DYNAMIC TTL (e.g. "why dynamic ttl", "contention", "optimistic lock")
        if (lower.contains("ttl") || lower.contains("contention") || lower.contains("lock") || lower.contains("race")) {
            return handleExplainContentionIntent();
        }

        // Default: Welcome & Help overview
        return handleGeneralHelpIntent(user);
    }

    private AiChatResponse handleHoldSlotIntent(Long slotId, User user) {
        try {
            Slot slot = slotRepository.findById(slotId)
                    .orElseThrow(() -> new ResourceNotFoundException("Slot #" + slotId + " not found"));

            if (slot.getStatus() != SlotStatus.AVAILABLE) {
                return new AiChatResponse(
                        "⚠️ **Slot #" + slotId + " is currently " + slot.getStatus() + "** and cannot be held. Would you like me to recommend an available alternative?",
                        "HOLD_SLOT_FAILED",
                        List.of(new AiActionSuggestion("✨ Recommend Alternative Slots", "RECOMMEND", "")),
                        Map.of("slotId", slotId, "status", slot.getStatus().name())
                );
            }

            // Calculate dynamic TTL
            DynamicTtlResponse ttl = dynamicTtlService.calculateTtl(slot);
            String idempotencyKey = "ai_hold_" + slotId + "_" + System.currentTimeMillis();

            HoldSlotRequest holdReq = new HoldSlotRequest(slotId, idempotencyKey, ttl.recommendedTtlMinutes());
            BookingResponse booking = bookingService.holdSlot(holdReq, user.getEmail());

            String reply = String.format(
                    "🔒 **Slot #%d (%s) is now HELD for you!**\n\n" +
                    "• **Time:** %s — %s\n" +
                    "• **Dynamic Hold Window:** %d minutes (%s)\n" +
                    "• **Reasoning:** %s\n\n" +
                    "⏳ Complete your confirmation before the hold timer expires!",
                    slot.getId(),
                    slot.getResource().getName(),
                    slot.getStartTime().format(TIME_FMT),
                    slot.getEndTime().format(DateTimeFormatter.ofPattern("hh:mm a")),
                    ttl.recommendedTtlMinutes(),
                    ttl.demandLevel(),
                    ttl.rationale()
            );

            List<AiActionSuggestion> actions = List.of(
                    new AiActionSuggestion("✅ Confirm Booking Now", "CONFIRM_BOOKING", idempotencyKey),
                    new AiActionSuggestion("❌ Cancel Hold", "CANCEL_BOOKING", booking.id().toString())
            );

            return new AiChatResponse(reply, "HOLD_SLOT_SUCCESS", actions, Map.of("bookingId", booking.id(), "idempotencyKey", idempotencyKey));
        } catch (Exception ex) {
            return new AiChatResponse(
                    "❌ Could not place hold on Slot #" + slotId + ": " + ex.getMessage(),
                    "HOLD_SLOT_ERROR",
                    Collections.emptyList(),
                    Map.of("error", ex.getMessage())
            );
        }
    }

    private AiChatResponse handleConfirmBookingIntent(String query, User user) {
        try {
            // Find active HELD booking for user
            List<BookingResponse> myBookings = bookingService.getUserBookings(user.getEmail());
            Optional<BookingResponse> heldOpt = myBookings.stream()
                    .filter(b -> b.status() == BookingStatus.HELD)
                    .findFirst();

            if (heldOpt.isEmpty()) {
                return new AiChatResponse(
                        "ℹ️ You do not currently have any slots on hold waiting for confirmation.",
                        "CONFIRM_NONE",
                        List.of(new AiActionSuggestion("🔍 Browse Available Slots", "BROWSE_SLOTS", "")),
                        Collections.emptyMap()
                );
            }

            BookingResponse heldBooking = heldOpt.get();
            ConfirmBookingRequest confirmReq = new ConfirmBookingRequest(heldBooking.idempotencyKey());
            BookingResponse confirmed = bookingService.confirmBooking(confirmReq, user.getEmail());

            String reply = String.format(
                    "🎉 **Booking Confirmed Successfully!**\n\n" +
                    "• **Booking ID:** #%d\n" +
                    "• **Resource:** %s\n" +
                    "• **Slot:** %s — %s\n" +
                    "• **Status:** CONFIRMED ✅\n\n" +
                    "Your reservation is locked in the system database.",
                    confirmed.id(),
                    confirmed.resourceName(),
                    confirmed.slotStartTime().format(TIME_FMT),
                    confirmed.slotEndTime().format(DateTimeFormatter.ofPattern("hh:mm a"))
            );

            return new AiChatResponse(reply, "CONFIRM_SUCCESS", Collections.emptyList(), Map.of("bookingId", confirmed.id()));
        } catch (Exception ex) {
            return new AiChatResponse(
                    "❌ Failed to confirm booking: " + ex.getMessage(),
                    "CONFIRM_ERROR",
                    Collections.emptyList(),
                    Map.of("error", ex.getMessage())
            );
        }
    }

    private AiChatResponse handleCancelBookingIntent(Long bookingId, User user) {
        try {
            BookingResponse cancelled = bookingService.cancelBooking(bookingId, user.getEmail());
            return new AiChatResponse(
                    "🗑️ **Booking #" + bookingId + " has been cancelled.** The time slot has been released back to the general pool for other users.",
                    "CANCEL_SUCCESS",
                    List.of(new AiActionSuggestion("🔍 Find New Slot", "BROWSE_SLOTS", "")),
                    Map.of("bookingId", cancelled.id())
            );
        } catch (Exception ex) {
            return new AiChatResponse(
                    "❌ Could not cancel Booking #" + bookingId + ": " + ex.getMessage(),
                    "CANCEL_ERROR",
                    Collections.emptyList(),
                    Map.of("error", ex.getMessage())
            );
        }
    }

    private AiChatResponse handleRecommendationIntent() {
        List<SlotRecommendationResponse> recs = slotOptimizerService.getRecommendations(null, 60);
        if (recs.isEmpty()) {
            return new AiChatResponse(
                    "ℹ️ No upcoming available slots found to optimize at this moment.",
                    "RECOMMEND_EMPTY",
                    Collections.emptyList(),
                    Collections.emptyMap()
            );
        }

        StringBuilder sb = new StringBuilder("✨ **AI Schedule Optimization & Gap Minimization**\n\n");
        sb.append("Here are the top slots recommended to minimize calendar fragmentation:\n\n");

        List<AiActionSuggestion> actions = new ArrayList<>();
        int count = Math.min(3, recs.size());
        for (int i = 0; i < count; i++) {
            SlotRecommendationResponse r = recs.get(i);
            sb.append(String.format("%d. **Slot #%d — %s**\n", (i + 1), r.slotId(), r.resourceName()));
            sb.append(String.format("   • %s (%s)\n", r.startTime().format(TIME_FMT), r.badge()));
            sb.append(String.format("   • *Efficiency Score:* %.1f%% — %s\n\n", r.efficiencyScore(), r.reason()));

            actions.add(new AiActionSuggestion("⚡ Hold Slot #" + r.slotId() + " (" + Math.round(r.efficiencyScore()) + "% Match)", "HOLD_SLOT", r.slotId().toString()));
        }

        return new AiChatResponse(sb.toString(), "RECOMMENDATIONS_FOUND", actions, Map.of("count", count));
    }

    private AiChatResponse handleMyBookingsIntent(User user) {
        List<BookingResponse> bookings = bookingService.getUserBookings(user.getEmail());
        if (bookings.isEmpty()) {
            return new AiChatResponse(
                    "📅 You don't have any bookings yet! Would you like me to find open slots for you?",
                    "MY_BOOKINGS_EMPTY",
                    List.of(new AiActionSuggestion("🔍 Browse Available Slots", "BROWSE_SLOTS", "")),
                    Collections.emptyMap()
            );
        }

        StringBuilder sb = new StringBuilder("📋 **Your Current Bookings & AI Risk Insights:**\n\n");
        List<AiActionSuggestion> actions = new ArrayList<>();

        for (BookingResponse b : bookings) {
            sb.append(String.format("• **Booking #%d** | %s (%s)\n", b.id(), b.resourceName(), b.status()));
            sb.append(String.format("  Time: %s\n", b.slotStartTime().format(TIME_FMT)));

            if (b.status() == BookingStatus.HELD) {
                sb.append("  ⚠️ *Status: HELD — Needs Confirmation!*\n");
                actions.add(new AiActionSuggestion("✅ Confirm Booking #" + b.id(), "CONFIRM_BOOKING", b.idempotencyKey()));
            } else if (b.status() == BookingStatus.CONFIRMED) {
                try {
                    NoShowPredictionResponse risk = noShowPredictorService.predict(b.id());
                    sb.append(String.format("  🛡️ *AI Attendance Confidence:* %s (%.0f%% no-show risk)\n",
                            risk.riskCategory(), risk.noShowProbability() * 100));
                } catch (Exception ignored) {}
                actions.add(new AiActionSuggestion("❌ Cancel #" + b.id(), "CANCEL_BOOKING", b.id().toString()));
            }
            sb.append("\n");
        }

        return new AiChatResponse(sb.toString(), "MY_BOOKINGS_FOUND", actions, Map.of("total", bookings.size()));
    }

    private AiChatResponse handleSearchSlotsIntent() {
        List<SlotResponse> slots = slotService.getSlots(null, SlotStatus.AVAILABLE, null, null);
        if (slots.isEmpty()) {
            return new AiChatResponse(
                    "ℹ️ No AVAILABLE slots found matching current criteria.",
                    "SEARCH_EMPTY",
                    Collections.emptyList(),
                    Collections.emptyMap()
            );
        }

        StringBuilder sb = new StringBuilder("🔍 **Available Time Slots:**\n\n");
        List<AiActionSuggestion> actions = new ArrayList<>();

        int count = Math.min(4, slots.size());
        for (int i = 0; i < count; i++) {
            SlotResponse s = slots.get(i);
            sb.append(String.format("• **Slot #%d** — %s\n", s.id(), s.resourceName()));
            sb.append(String.format("  Time: %s — %s\n", s.startTime().format(TIME_FMT), s.endTime().format(DateTimeFormatter.ofPattern("hh:mm a"))));
            actions.add(new AiActionSuggestion("🔒 Hold Slot #" + s.id(), "HOLD_SLOT", s.id().toString()));
        }

        sb.append("\nClick a slot action below or tell me which one to reserve for you!");
        return new AiChatResponse(sb.toString(), "SEARCH_SUCCESS", actions, Map.of("totalFound", slots.size()));
    }

    private AiChatResponse handleExplainContentionIntent() {
        String reply = "⚡ **How SlotSync AI Solves Race Conditions & Starvation:**\n\n" +
                "1. **Optimistic Locking (`@Version`):** Multiple users can view the same slot concurrently, but the database checks the version counter upon write. If two users click simultaneously, one succeeds and the other is cleanly notified without table locks.\n\n" +
                "2. **Dynamic Hold TTL:** In high-traffic scenarios, standard 15-minute holds starve inventory. Our AI dynamically scales hold timers (down to **2–5 minutes** during traffic surges) so abandoned holds cycle back into circulation rapidly.\n\n" +
                "3. **AI Bot & Scalper Shield:** Evaluates velocity and hold-to-confirm ratios to prevent bots from hoarding time slots.";

        List<AiActionSuggestion> actions = List.of(
                new AiActionSuggestion("📊 View Contention Analytics", "VIEW_CONTENTION", ""),
                new AiActionSuggestion("✨ Recommend Best Slots", "RECOMMEND", "")
        );

        return new AiChatResponse(reply, "EXPLAIN_CONTENTION", actions, Collections.emptyMap());
    }

    private AiChatResponse handleGeneralHelpIntent(User user) {
        String reply = String.format(
                "👋 **Hello %s! I am your SlotSync AI Copilot.**\n\n" +
                "I can assist you with natural language scheduling and reservation management:\n\n" +
                "• *\"Find available meeting rooms tomorrow\"*\n" +
                "• *\"Recommend the best slot to minimize gaps\"*\n" +
                "• *\"Hold slot 2\"* (with dynamic AI TTL)\n" +
                "• *\"Confirm my booking\"*\n" +
                "• *\"Show my bookings and no-show risk\"*\n" +
                "• *\"Explain dynamic TTL & contention\"*\n\n" +
                "What would you like to do?",
                user.getName()
        );

        List<AiActionSuggestion> actions = List.of(
                new AiActionSuggestion("✨ Recommend Best Slots", "RECOMMEND", ""),
                new AiActionSuggestion("🔍 Browse Available Slots", "BROWSE_SLOTS", ""),
                new AiActionSuggestion("📋 View My Bookings", "MY_BOOKINGS", ""),
                new AiActionSuggestion("⚡ Why Dynamic TTL?", "EXPLAIN_TTL", "")
        );

        return new AiChatResponse(reply, "GENERAL_HELP", actions, Map.of("provider", aiProvider != null ? aiProvider : "internal"));
    }
}
