package com.tubereturns.service;

import com.tubereturns.model.Role;
import com.tubereturns.model.User;
import com.tubereturns.repository.RoleRepository;
import com.tubereturns.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class AdminAccountService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${tubereturns.admin.email}")
    private String adminEmail;

    @Value("${tubereturns.admin.password}")
    private String adminPassword;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void syncAdminAccount() {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.info("Admin credentials not configured in the environment; skipping admin account sync");
            return;
        }
        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role not found"));
        User admin = userRepository.findByEmail(adminEmail).orElseGet(User::new);
        admin.setEmail(adminEmail);
        if (admin.getFirstName() == null || admin.getFirstName().isBlank()) {
            admin.setFirstName("Admin");
        }
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setProvider("local");
        admin.setEmailVerified(true);
        admin.setRole(adminRole);
        userRepository.save(admin);
        log.info("Admin account credentials synced from the environment for {}", adminEmail);
    }
}
