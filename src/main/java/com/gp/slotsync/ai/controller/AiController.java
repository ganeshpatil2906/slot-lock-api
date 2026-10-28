package com.gp.slotsync.ai.controller;

import com.gp.slotsync.ai.dto.*;
import com.gp.slotsync.ai.service.*;
import com.gp.slotsync.entity.Slot;
import com.gp.slotsync.entity.User;
import com.gp.slotsync.exception.ResourceNotFoundException;
import com.gp.slotsync.repository.SlotRepository;
import com.gp.slotsync.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI Engine & Copilot", description = "AI conversational copilot, dynamic TTL contention optimizer, no-show predictor, schedule packing optimizer, and anti-scalper bot shield")
@SecurityRequirement(name = "bearerAuth")
public class AiController {

    private final AiCopilotService aiCopilotService;
    private final DynamicTtlService dynamicTtlService;
    private final AbuseDetectionService abuseDetectionService;
    private final NoShowPredictorService noShowPredictorService;
    private final SlotOptimizerService slotOptimizerService;
    private final SlotRepository slotRepository;
    private final UserRepository userRepository;

    public AiController(
            AiCopilotService aiCopilotService,
            DynamicTtlService dynamicTtlService,
            AbuseDetectionService abuseDetectionService,
            NoShowPredictorService noShowPredictorService,
            SlotOptimizerService slotOptimizerService,
            SlotRepository slotRepository,
            UserRepository userRepository) {
        this.aiCopilotService = aiCopilotService;
        this.dynamicTtlService = dynamicTtlService;
        this.abuseDetectionService = abuseDetectionService;
        this.noShowPredictorService = noShowPredictorService;
        this.slotOptimizerService = slotOptimizerService;
        this.slotRepository = slotRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/copilot/chat")
    @Operation(summary = "AI Copilot Chat", description = "Conversational AI assistant that understands natural language booking requests and triggers backend tools")
    public ResponseEntity<AiChatResponse> chat(
            @Valid @RequestBody AiChatRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        AiChatResponse response = aiCopilotService.processMessage(request, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/dynamic-ttl/{slotId}")
    @Operation(summary = "Dynamic Hold TTL Evaluation", description = "Calculates adaptive hold duration based on concurrency contention and slot proximity")
    public ResponseEntity<DynamicTtlResponse> getDynamicTtl(@PathVariable Long slotId) {
        Slot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found with id: " + slotId));
        DynamicTtlResponse response = dynamicTtlService.calculateTtl(slot);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/contention/analytics")
    @Operation(summary = "System Contention Analytics", description = "Provides real-time concurrency collision analytics, active contention heatmap, and auto-tuning recommendations")
    public ResponseEntity<ContentionAnalyticsResponse> getContentionAnalytics() {
        ContentionAnalyticsResponse response = dynamicTtlService.getContentionAnalytics();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/no-show-risk/{bookingId}")
    @Operation(summary = "No-Show & Cancellation Risk Prediction", description = "Predicts cancellation probability for a booking based on lead time, timing, and historical patterns")
    public ResponseEntity<NoShowPredictionResponse> getNoShowRisk(@PathVariable Long bookingId) {
        NoShowPredictionResponse response = noShowPredictorService.predict(bookingId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/recommendations")
    @Operation(summary = "AI Schedule Packing Recommendations", description = "Recommends time slots that minimize calendar fragmentation and orphaned idle gaps")
    public ResponseEntity<List<SlotRecommendationResponse>> getRecommendations(
            @RequestParam(required = false) Long resourceId,
            @RequestParam(required = false, defaultValue = "60") Integer durationMinutes) {
        List<SlotRecommendationResponse> response = slotOptimizerService.getRecommendations(resourceId, durationMinutes);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/abuse/audit")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Bot & Scalper Shield Audit (Admin Only)", description = "Audits user booking velocity and hoarding risk scores across the system")
    public ResponseEntity<List<AbuseAuditResponse>> auditAbuse() {
        List<AbuseAuditResponse> report = abuseDetectionService.auditAllUsers();
        return ResponseEntity.ok(report);
    }

    @GetMapping("/abuse/my-status")
    @Operation(summary = "Check Current User Risk Status", description = "Returns the risk score and abuse audit status for the authenticated user")
    public ResponseEntity<AbuseAuditResponse> getMyRiskStatus(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userDetails.getUsername()));
        AbuseAuditResponse status = abuseDetectionService.assessUserRisk(user);
        return ResponseEntity.ok(status);
    }
}
