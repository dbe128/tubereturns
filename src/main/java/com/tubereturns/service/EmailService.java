package com.tubereturns.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${tubereturns.app.base-url:http://localhost:4200}")
    private String baseUrl;

    @Value("${tubereturns.app.from-email:noreply@tubereturns.com}")
    private String fromEmail;

    public void sendVerificationEmail(String toEmail, String firstName, String token) {
        String link = baseUrl + "/verify-email?token=" + token;
        if (mailSender == null) {
            log.warn("Mail not configured — verification link for {}: {}", toEmail, link);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Verify your TubeReturns account");
        message.setText("""
                Hi %s,

                Click the link below to verify your email address. The link expires in 1 hour.

                %s

                If you didn't create an account, you can ignore this email.

                — TubeReturns
                """.formatted(firstName, link));
        trySend(message, toEmail, link);
    }

    public void sendPasswordResetEmail(String toEmail, String firstName, String token) {
        String link = baseUrl + "/reset-password?token=" + token;
        if (mailSender == null) {
            log.warn("Mail not configured — password reset link for {}: {}", toEmail, link);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Reset your TubeReturns password");
        message.setText("""
                Hi %s,

                Click the link below to reset your password. The link expires in 1 hour.

                %s

                If you didn't request a password reset, you can ignore this email.

                — TubeReturns
                """.formatted(firstName, link));
        trySend(message, toEmail, link);
    }

    private void trySend(SimpleMailMessage message, String toEmail, String fallbackLink) {
        try {
            mailSender.send(message);
            log.info("Email sent to {}", toEmail);
        } catch (MailException e) {
            log.warn("Failed to send email to {} (link: {}): {}", toEmail, fallbackLink, e.getMessage());
        }
    }
}
