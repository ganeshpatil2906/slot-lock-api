package com.gp.slotsync.ai;

import com.gp.slotsync.ai.dto.SlotRecommendationResponse;
import com.gp.slotsync.ai.service.SlotOptimizerService;
import com.gp.slotsync.entity.Resource;
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
class SlotOptimizerServiceTest {

    @Mock
    private SlotRepository slotRepository;

    private SlotOptimizerService slotOptimizerService;

    @BeforeEach
    void setUp() {
        slotOptimizerService = new SlotOptimizerService(slotRepository);
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
    void getRecommendations_Empty_ReturnsEmptyList() {
        when(slotRepository.findByStatus(SlotStatus.AVAILABLE)).thenReturn(List.of());

        List<SlotRecommendationResponse> results = slotOptimizerService.getRecommendations(null, 60);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void getRecommendations_PacksBackToBackSlots() {
        Resource res = new Resource();
        setId(res, 1L);
        res.setName("Auditorium A");

        LocalDateTime tomorrow10am = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0);
        LocalDateTime tomorrow11am = LocalDateTime.now().plusDays(1).withHour(11).withMinute(0);
        LocalDateTime tomorrow12pm = LocalDateTime.now().plusDays(1).withHour(12).withMinute(0);

        // Preceding booking: 10am-11am (BOOKED)
        Slot preceding = new Slot();
        setId(preceding, 101L);
        preceding.setResource(res);
        preceding.setStartTime(tomorrow10am);
        preceding.setEndTime(tomorrow11am);
        preceding.setStatus(SlotStatus.BOOKED);

        // Candidate slot: 11am-12pm (AVAILABLE) - back to back!
        Slot candidate = new Slot();
        setId(candidate, 102L);
        candidate.setResource(res);
        candidate.setStartTime(tomorrow11am);
        candidate.setEndTime(tomorrow12pm);
        candidate.setStatus(SlotStatus.AVAILABLE);

        when(slotRepository.findByStatus(SlotStatus.AVAILABLE)).thenReturn(List.of(candidate));
        when(slotRepository.findAll()).thenReturn(List.of(preceding, candidate));

        List<SlotRecommendationResponse> results = slotOptimizerService.getRecommendations(null, 60);

        assertNotNull(results);
        assertEquals(1, results.size());
        SlotRecommendationResponse best = results.get(0);
        assertEquals(102L, best.slotId());
        assertTrue(best.efficiencyScore() >= 80.0);
        assertTrue(best.badge().contains("Best Match"));
    }
}
