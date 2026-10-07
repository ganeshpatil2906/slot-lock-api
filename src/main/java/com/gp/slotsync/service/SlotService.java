package com.gp.slotsync.service;

import com.gp.slotsync.dto.CreateSlotRequest;
import com.gp.slotsync.dto.SlotResponse;
import com.gp.slotsync.entity.Resource;
import com.gp.slotsync.entity.Slot;
import com.gp.slotsync.enums.SlotStatus;
import com.gp.slotsync.exception.BookingConflictException;
import com.gp.slotsync.exception.InvalidRequestException;
import com.gp.slotsync.exception.ResourceNotFoundException;
import com.gp.slotsync.repository.ResourceRepository;
import com.gp.slotsync.repository.SlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SlotService {

    private final SlotRepository slotRepository;
    private final ResourceRepository resourceRepository;

    public SlotService(SlotRepository slotRepository, ResourceRepository resourceRepository) {
        this.slotRepository = slotRepository;
        this.resourceRepository = resourceRepository;
    }

    @Transactional
    public SlotResponse createSlot(CreateSlotRequest request) {
        if (request.startTime().isAfter(request.endTime()) || request.startTime().isEqual(request.endTime())) {
            throw new InvalidRequestException("Start time must be before end time");
        }

        Resource resource = resourceRepository.findById(request.resourceId())
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found with id: " + request.resourceId()));

        if (!resource.isActive()) {
            throw new InvalidRequestException("Cannot create slot for inactive resource");
        }

        if (slotRepository.existsByResourceIdAndStartTime(request.resourceId(), request.startTime())) {
            throw new BookingConflictException("Slot already exists for this resource at start time: " + request.startTime());
        }

        Slot slot = new Slot();
        slot.setResource(resource);
        slot.setStartTime(request.startTime());
        slot.setEndTime(request.endTime());
        slot.setStatus(SlotStatus.AVAILABLE);

        Slot saved = slotRepository.save(slot);
        return SlotResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> getSlots(Long resourceId, SlotStatus status, LocalDateTime start, LocalDateTime end) {
        List<Slot> slots;
        if (resourceId != null && start != null && end != null) {
            slots = slotRepository.findByResourceIdAndStartTimeGreaterThanEqualAndEndTimeLessThanEqual(resourceId, start, end);
        } else if (resourceId != null && status != null) {
            slots = slotRepository.findByResourceIdAndStatus(resourceId, status);
        } else if (resourceId != null) {
            slots = slotRepository.findByResourceId(resourceId);
        } else if (status != null) {
            slots = slotRepository.findByStatus(status);
        } else {
            slots = slotRepository.findAll();
        }

        return slots.stream().map(SlotResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public SlotResponse getSlotById(Long id) {
        Slot slot = slotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found with id: " + id));
        return SlotResponse.fromEntity(slot);
    }
}
