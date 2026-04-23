package com.tubereturns.service;

import com.tubereturns.dto.RegisterRequestDto;
import com.tubereturns.model.Role;
import com.tubereturns.model.User;
import com.tubereturns.repository.RoleRepository;
import com.tubereturns.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    @Transactional
    public void registerUser(RegisterRequestDto request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already registered");
        }
        Role freeRole = roleRepository.findByName("FREE")
                .orElseThrow(() -> new IllegalStateException("FREE role not found"));
        String token = UUID.randomUUID().toString();
        User user = new User();
        user.setFirstName(request.firstName());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setProvider("local");
        user.setEmailVerified(false);
        user.setRole(freeRole);
        user.setVerificationToken(token);
        user.setVerificationTokenExpiresAt(Instant.now().plusSeconds(3600));
        userRepository.save(user);
        emailService.sendVerificationEmail(user.getEmail(), user.getFirstName(), token);
    }

    @Transactional
    public void verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification link"));
        if (user.getVerificationTokenExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Verification link has expired");
        }
        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiresAt(null);
        userRepository.save(user);
    }

    @Transactional
    public void initiatePasswordReset(String email) {
        userRepository.findByEmail(email).ifPresentOrElse(user -> {
            String token = UUID.randomUUID().toString();
            user.setPasswordResetToken(token);
            user.setPasswordResetTokenExpiresAt(Instant.now().plusSeconds(3600));
            userRepository.save(user);
            log.info("Password reset initiated for {}", email);
            emailService.sendPasswordResetEmail(user.getEmail(), user.getFirstName(), token);
        }, () -> log.warn("Password reset requested for unknown email: {}", email));
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByPasswordResetToken(token)
                .orElseThrow(() -> {
                    log.warn("Password reset attempted with invalid token");
                    return new IllegalArgumentException("Invalid or expired password reset link");
                });
        if (user.getPasswordResetTokenExpiresAt().isBefore(Instant.now())) {
            log.warn("Password reset attempted with expired token for {}", user.getEmail());
            throw new IllegalArgumentException("Password reset link has expired");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiresAt(null);
        userRepository.save(user);
        log.info("Password successfully reset for {}", user.getEmail());
    }

    @Transactional
    public User findOrCreateOAuth2User(String provider, String providerId, String email, String firstName) {
        return userRepository.findByEmail(email)
                .map(existing -> {
                    existing.setProvider(provider);
                    existing.setProviderId(providerId);
                    return userRepository.save(existing);
                })
                .orElseGet(() -> {
                    User user = new User();
                    user.setFirstName(firstName);
                    user.setEmail(email);
                    user.setProvider(provider);
                    user.setProviderId(providerId);
                    user.setEmailVerified(true);
                    return userRepository.save(user);
                });
    }
}
