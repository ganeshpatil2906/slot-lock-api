package com.gp.slotsync.ai;

import com.gp.slotsync.ai.dto.AiChatRequest;
import com.gp.slotsync.ai.dto.AiChatResponse;
import com.gp.slotsync.ai.service.AiCopilotService;
import com.gp.slotsync.ai.service.DynamicTtlService;
import com.gp.slotsync.ai.service.NoShowPredictorService;
import com.gp.slotsync.ai.service.SlotOptimizerService;
import com.gp.slotsync.entity.User;
import com.gp.slotsync.enums.Role;
import com.gp.slotsync.repository.SlotRepository;
import com.gp.slotsync.repository.UserRepository;
import com.gp.slotsync.service.BookingService;
import com.gp.slotsync.service.SlotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiCopilotServiceTest {

    @Mock private SlotService slotService;
    @Mock private BookingService bookingService;
    @Mock private DynamicTtlService dynamicTtlService;
    @Mock private SlotOptimizerService slotOptimizerService;
    @Mock private NoShowPredictorService noShowPredictorService;
    @Mock private UserRepository userRepository;
    @Mock private SlotRepository slotRepository;

    private AiCopilotService copilotService;

    @BeforeEach
    void setUp() {
        copilotService = new AiCopilotService(
                slotService,
                bookingService,
                dynamicTtlService,
                slotOptimizerService,
                noShowPredictorService,
                userRepository,
                slotRepository
        );
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
    void processMessage_GeneralHelp_ReturnsHelpInfo() {
        User user = new User();
        setId(user, 1L);
        user.setName("Alice");
        user.setEmail("alice@example.com");
        user.setRole(Role.USER);

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        AiChatResponse response = copilotService.processMessage(
                new AiChatRequest("hello", null),
                "alice@example.com"
        );

        assertNotNull(response);
        assertEquals("GENERAL_HELP", response.detectedIntent());
        assertTrue(response.reply().contains("Alice"));
        assertFalse(response.actions().isEmpty());
    }

    @Test
    void processMessage_ExplainTtl_ExplainsContention() {
        User user = new User();
        setId(user, 1L);
        user.setName("Alice");
        user.setEmail("alice@example.com");

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        AiChatResponse response = copilotService.processMessage(
                new AiChatRequest("why dynamic ttl and contention?", null),
                "alice@example.com"
        );

        assertNotNull(response);
        assertEquals("EXPLAIN_CONTENTION", response.detectedIntent());
        assertTrue(response.reply().contains("Optimistic Locking"));
    }
}
