package com.tubereturns.service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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

    public void sendChannelProcessedEmail(String toEmail, String channelName, String channelHandle) {
        if (mailSender == null) {
            log.warn("Mail not configured — channel processed notification for {} (channel: {})", toEmail, channelName);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("TubeReturns — " + channelName + " is ready");
            helper.setText(buildChannelProcessedHtml(channelName), true);
            mailSender.send(message);
            log.info("Channel processed email sent to {} for channel {}", toEmail, channelHandle);
        } catch (Exception e) {
            log.warn("Failed to send channel processed email to {} for channel {}: {}", toEmail, channelHandle, e.getMessage());
        }
    }

    private String buildChannelProcessedHtml(String channelName) {
        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"></head>
                <body style="font-family:sans-serif;max-width:480px;margin:0 auto;padding:32px 24px;color:#111;">
                  <div style="margin-bottom:24px;">
                    <a href="%1$s" style="text-decoration:none;font-size:22px;font-weight:800;color:#2563eb;letter-spacing:-0.5px;">TubeReturns</a>
                  </div>
                  <h2 style="margin:0 0 12px;font-size:18px;font-weight:700;">Channel analysis complete</h2>
                  <p style="margin:0 0 8px;color:#374151;font-size:15px;"><strong>%2$s</strong> has been fully processed.</p>
                  <p style="margin:0 0 24px;color:#6b7280;font-size:14px;">All videos have had their stock picks extracted and performance is now included in the leaderboard.</p>
                  <a href="%1$s" style="display:inline-block;background:#2563eb;color:#fff;text-decoration:none;padding:12px 24px;border-radius:8px;font-weight:600;font-size:14px;">View on TubeReturns →</a>
                  <p style="margin:32px 0 0;color:#9ca3af;font-size:12px;">— TubeReturns</p>
                </body>
                </html>
                """.formatted(baseUrl, channelName);
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
