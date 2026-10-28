package com.gp.slotsync.ai;

import com.gp.slotsync.ai.dto.ContentionAnalyticsResponse;
import com.gp.slotsync.ai.dto.DynamicTtlResponse;
import com.gp.slotsync.ai.service.DynamicTtlService;
import com.gp.slotsync.entity.Slot;
import com.gp.slotsync.enums.SlotStatus;
import com.gp.slotsync.repository.SlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DynamicTtlServiceTest {

    @Mock
    private SlotRepository slotRepository;

    private DynamicTtlService dynamicTtlService;

    @BeforeEach
    void setUp() {
        dynamicTtlService = new DynamicTtlService(slotRepository, 15, 2, 25);
    }

    private void setSlotId(Slot slot, Long id) {
        try {
            Field field = Slot.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(slot, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void calculateTtl_NormalConditions_ReturnsBaseTtl() {
        Slot slot = new Slot();
        setSlotId(slot, 1L);
        slot.setStartTime(LocalDateTime.now().plusDays(2));
        slot.setEndTime(LocalDateTime.now().plusDays(2).plusHours(1));

        DynamicTtlResponse response = dynamicTtlService.calculateTtl(slot);

        assertNotNull(response);
        assertEquals(15, response.recommendedTtlMinutes());
        assertEquals("LOW_CONTENTION", response.demandLevel());
        assertEquals(0.0, response.contentionScore());
    }

    @Test
    void calculateTtl_ImminentSlot_ReturnsMinTtl() {
        Slot slot = new Slot();
        setSlotId(slot, 2L);
        slot.setStartTime(LocalDateTime.now().plusMinutes(10));
        slot.setEndTime(LocalDateTime.now().plusMinutes(40));

        DynamicTtlResponse response = dynamicTtlService.calculateTtl(slot);

        assertNotNull(response);
        assertEquals(2, response.recommendedTtlMinutes());
        assertEquals("CRITICAL_PROXIMITY", response.demandLevel());
    }

    @Test
    void calculateTtl_HighCollisions_ThrottlesTtl() {
        Slot slot = new Slot();
        setSlotId(slot, 3L);
        slot.setStartTime(LocalDateTime.now().plusHours(3));
        slot.setEndTime(LocalDateTime.now().plusHours(4));

        // Simulate 3 collisions
        dynamicTtlService.recordCollision(3L);
        dynamicTtlService.recordCollision(3L);
        dynamicTtlService.recordCollision(3L);

        DynamicTtlResponse response = dynamicTtlService.calculateTtl(slot);

        assertNotNull(response);
        assertTrue(response.contentionScore() >= 0.70);
        assertEquals(3, response.recommendedTtlMinutes());
        assertEquals("SURGE_COLLISION", response.demandLevel());
    }

    @Test
    void getContentionAnalytics_ReturnsSystemSummary() {
        Slot s1 = new Slot();
        setSlotId(s1, 10L);
        s1.setStatus(SlotStatus.AVAILABLE);
        s1.setStartTime(LocalDateTime.now().plusHours(2));

        Slot s2 = new Slot();
        setSlotId(s2, 11L);
        s2.setStatus(SlotStatus.BOOKED);
        s2.setStartTime(LocalDateTime.now().plusHours(3));

        when(slotRepository.findAll()).thenReturn(List.of(s1, s2));

        ContentionAnalyticsResponse analytics = dynamicTtlService.getContentionAnalytics();

        assertNotNull(analytics);
        assertEquals(2, analytics.totalSlots());
        assertEquals(1, analytics.bookedSlots());
        assertFalse(analytics.autoTuningRecommendations().isEmpty());
    }
}
