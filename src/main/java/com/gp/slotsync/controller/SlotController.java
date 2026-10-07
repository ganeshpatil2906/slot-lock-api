package com.gp.slotsync.controller;

import com.gp.slotsync.dto.CreateSlotRequest;
import com.gp.slotsync.dto.SlotResponse;
import com.gp.slotsync.enums.SlotStatus;
import com.gp.slotsync.service.SlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/slots")
@Tag(name = "Slot Management", description = "Endpoints for creating and searching time slots")
@SecurityRequirement(name = "bearerAuth")
public class SlotController {

    private final SlotService slotService;

    public SlotController(SlotService slotService) {
        this.slotService = slotService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a time slot", description = "Admin endpoint to create a time slot for a resource")
    public ResponseEntity<SlotResponse> createSlot(@Valid @RequestBody CreateSlotRequest request) {
        SlotResponse response = slotService.createSlot(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Search time slots", description = "Searches for slots by resource ID, status, or date range window")
    public ResponseEntity<List<SlotResponse>> getSlots(
            @RequestParam(required = false) Long resourceId,
            @RequestParam(required = false) SlotStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        List<SlotResponse> response = slotService.getSlots(resourceId, status, start, end);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get slot by ID", description = "Retrieves details of a specific slot")
    public ResponseEntity<SlotResponse> getSlotById(@PathVariable Long id) {
        SlotResponse response = slotService.getSlotById(id);
        return ResponseEntity.ok(response);
    }
}
