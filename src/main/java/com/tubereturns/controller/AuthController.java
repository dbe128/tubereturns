package com.tubereturns.controller;

import com.tubereturns.dto.AuthResponseDto;
import com.tubereturns.dto.ForgotPasswordRequestDto;
import com.tubereturns.dto.LoginRequestDto;
import com.tubereturns.dto.RegisterRequestDto;
import com.tubereturns.dto.ResetPasswordRequestDto;
import com.tubereturns.model.User;
import com.tubereturns.service.JwtService;
import com.tubereturns.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Registration, login and token management")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @PostMapping("/signup")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequestDto request) {
        try {
            userService.registerUser(request);
            return ResponseEntity.ok(Map.of("message", "Check your email to complete registration"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequestDto request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
            User user = (User) userService.loadUserByUsername(request.email());
            return ResponseEntity.ok(new AuthResponseDto(jwtService.generateToken(user), user.getEmail(), user.getFirstName()));
        } catch (DisabledException e) {
            return ResponseEntity.status(403).body(Map.of("message", "Please verify your email address before signing in"));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid email or password"));
        }
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verify(@RequestParam String token) {
        try {
            userService.verifyEmail(token);
            return ResponseEntity.ok(Map.of("message", "Email verified"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDto request) {
        userService.initiatePasswordReset(request.email());
        return ResponseEntity.ok(Map.of("message", "If that email is registered you will receive a reset link shortly"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequestDto request) {
        try {
            userService.resetPassword(request.token(), request.newPassword());
            return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponseDto> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(new AuthResponseDto(jwtService.generateToken(user), user.getEmail(), user.getFirstName()));
    }
}
