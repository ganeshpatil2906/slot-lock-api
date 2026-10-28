package com.gp.slotsync.ai;

import com.gp.slotsync.ai.dto.AbuseAuditResponse;
import com.gp.slotsync.ai.service.AbuseDetectionService;
import com.gp.slotsync.entity.User;
import com.gp.slotsync.enums.BookingStatus;
import com.gp.slotsync.enums.Role;
import com.gp.slotsync.exception.AbuseDetectedException;
import com.gp.slotsync.repository.BookingRepository;
import com.gp.slotsync.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AbuseDetectionServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private UserRepository userRepository;

    private AbuseDetectionService abuseDetectionService;

    @BeforeEach
    void setUp() {
        abuseDetectionService = new AbuseDetectionService(bookingRepository, userRepository, true, 3, 2);
    }

    private User createUser(Long id, Role role) {
        User user = new User();
        user.setName("Test User");
        user.setEmail("user" + id + "@example.com");
        user.setRole(role);
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return user;
    }

    @Test
    void validateHoldAllowed_NormalUser_Success() {
        User user = createUser(1L, Role.USER);
        when(bookingRepository.countByUserIdAndStatus(1L, BookingStatus.HELD)).thenReturn(0L);

        assertDoesNotThrow(() -> abuseDetectionService.validateHoldAllowed(user));
    }

    @Test
    void validateHoldAllowed_ExcessiveConcurrentHolds_ThrowsException() {
        User user = createUser(2L, Role.USER);
        when(bookingRepository.countByUserIdAndStatus(2L, BookingStatus.HELD)).thenReturn(2L); // Limit is 2

        assertThrows(AbuseDetectedException.class, () -> abuseDetectionService.validateHoldAllowed(user));
    }

    @Test
    void validateHoldAllowed_RapidVelocity_ThrowsException() {
        User user = createUser(3L, Role.USER);
        when(bookingRepository.countByUserIdAndStatus(3L, BookingStatus.HELD)).thenReturn(0L);

        // Max is 3 per minute
        abuseDetectionService.validateHoldAllowed(user);
        abuseDetectionService.validateHoldAllowed(user);
        abuseDetectionService.validateHoldAllowed(user);

        // 4th should exceed velocity rate limit
        assertThrows(AbuseDetectedException.class, () -> abuseDetectionService.validateHoldAllowed(user));
    }

    @Test
    void validateHoldAllowed_AdminBypassesRestrictions() {
        User admin = createUser(99L, Role.ADMIN);
        // Admin never checks bookingRepository counts
        assertDoesNotThrow(() -> abuseDetectionService.validateHoldAllowed(admin));
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void assessUserRisk_ComputesAuditProfile() {
        User user = createUser(4L, Role.USER);
        when(bookingRepository.countByUserIdAndStatus(4L, BookingStatus.HELD)).thenReturn(1L);
        when(bookingRepository.countByUserId(4L)).thenReturn(5L);
        when(bookingRepository.countByUserIdAndStatus(4L, BookingStatus.EXPIRED)).thenReturn(4L);
        when(bookingRepository.countByUserIdAndStatus(4L, BookingStatus.CANCELLED)).thenReturn(0L);

        AbuseAuditResponse profile = abuseDetectionService.assessUserRisk(user);

        assertNotNull(profile);
        assertEquals(4L, profile.userId());
        assertTrue(profile.abandonmentRate() >= 0.80);
    }
}
