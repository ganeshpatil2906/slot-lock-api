package com.gp.slotsync.service;

import com.gp.slotsync.dto.AuthResponse;
import com.gp.slotsync.dto.LoginRequest;
import com.gp.slotsync.dto.RegisterRequest;
import com.gp.slotsync.entity.User;
import com.gp.slotsync.enums.Role;
import com.gp.slotsync.exception.BookingConflictException;
import com.gp.slotsync.repository.UserRepository;
import com.gp.slotsync.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtTokenProvider jwtTokenProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, authenticationManager, jwtTokenProvider);
    }

    @Test
    void register_Success() {
        RegisterRequest request = new RegisterRequest("Test User", "test@example.com", "password123", Role.USER);

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        
        User savedUser = new User();
        savedUser.setName("Test User");
        savedUser.setEmail("test@example.com");
        savedUser.setRole(Role.USER);
        
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtTokenProvider.generateToken("test@example.com", "USER")).thenReturn("mock-jwt-token");

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("mock-jwt-token", response.token());
        assertEquals("test@example.com", response.email());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void register_ExistingUser_ThrowsException() {
        RegisterRequest request = new RegisterRequest("Test User", "test@example.com", "password123", Role.USER);

        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThrows(BookingConflictException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_Success() {
        LoginRequest request = new LoginRequest("test@example.com", "password123");

        User user = new User();
        user.setName("Test User");
        user.setEmail("test@example.com");
        user.setRole(Role.USER);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateToken("test@example.com", "USER")).thenReturn("mock-jwt-token");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mock-jwt-token", response.token());
        verify(authenticationManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }
}
