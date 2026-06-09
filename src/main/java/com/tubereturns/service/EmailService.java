package com.tubereturns.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Autowired
    private MeterRegistry meterRegistry;

    @Value("${tubereturns.app.base-url}")
    private String baseUrl;

    @Value("${tubereturns.app.from-email}")
    private String fromEmail;

    private static final ClassPathResource LOGO = new ClassPathResource("email/logo.png");

    @Async
    public void sendVerificationEmail(String toEmail, String firstName, String token) {
        String link = baseUrl + "/verify-email?token=" + token;
        if (mailSender == null) {
            log.warn("Mail not configured — verification link for {}: {}", toEmail, link);
            return;
        }
        String html = """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"></head>
                <body style="font-family:sans-serif;max-width:480px;margin:0 auto;padding:32px 24px;color:#111;">
                  <div style="margin-bottom:24px;">
                    <img src="cid:logo" alt="TubeReturns" style="height:150px;" />
                  </div>
                  <h2 style="margin:0 0 12px;font-size:18px;font-weight:700;">Verify your email</h2>
                  <p style="margin:0 0 8px;color:#374151;font-size:15px;">Hi %s,</p>
                  <p style="margin:0 0 24px;color:#6b7280;font-size:14px;">Click the button below to verify your email address. The link expires in 1 hour.</p>
                  <a href="%s" style="display:inline-block;background:#111827;color:#fff;text-decoration:none;padding:12px 24px;border-radius:8px;font-weight:600;font-size:14px;">Verify email →</a>
                  <p style="margin:32px 0 0;color:#9ca3af;font-size:12px;">If you didn't create an account, you can ignore this email.</p>
                </body>
                </html>
                """.formatted(escapeHtml(firstName), link);
        trySend(toEmail, "Verify your TubeReturns account", html);
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String firstName, String token) {
        String link = baseUrl + "/reset-password?token=" + token;
        if (mailSender == null) {
            log.warn("Mail not configured — password reset link for {}: {}", toEmail, link);
            return;
        }
        String html = """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"></head>
                <body style="font-family:sans-serif;max-width:480px;margin:0 auto;padding:32px 24px;color:#111;">
                  <div style="margin-bottom:24px;">
                    <img src="cid:logo" alt="TubeReturns" style="height:150px;" />
                  </div>
                  <h2 style="margin:0 0 12px;font-size:18px;font-weight:700;">Reset your password</h2>
                  <p style="margin:0 0 8px;color:#374151;font-size:15px;">Hi %s,</p>
                  <p style="margin:0 0 24px;color:#6b7280;font-size:14px;">Click the button below to reset your password. The link expires in 1 hour.</p>
                  <a href="%s" style="display:inline-block;background:#111827;color:#fff;text-decoration:none;padding:12px 24px;border-radius:8px;font-weight:600;font-size:14px;">Reset password →</a>
                  <p style="margin:32px 0 0;color:#9ca3af;font-size:12px;">If you didn't request a password reset, you can ignore this email.</p>
                </body>
                </html>
                """.formatted(escapeHtml(firstName), link);
        trySend(toEmail, "Reset your TubeReturns password", html);
    }

    public boolean sendChannelProcessedEmail(String toEmail, String channelName, String channelHandle) {
        if (mailSender == null) {
            log.warn("Mail not configured — channel processed notification for {} (channel: {})", toEmail, channelName);
            return false;
        }
        String html = """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"></head>
                <body style="font-family:sans-serif;max-width:480px;margin:0 auto;padding:32px 24px;color:#111;">
                  <div style="margin-bottom:24px;">
                    <img src="cid:logo" alt="TubeReturns" style="height:150px;" />
                  </div>
                  <h2 style="margin:0 0 12px;font-size:18px;font-weight:700;">Channel analysis complete</h2>
                  <p style="margin:0 0 8px;color:#374151;font-size:15px;"><strong>%s</strong> has been fully processed.</p>
                  <p style="margin:0 0 24px;color:#6b7280;font-size:14px;">All videos have had their stock picks extracted and performance is now included in the leaderboard.</p>
                  <a href="%s" style="display:inline-block;background:#111827;color:#fff;text-decoration:none;padding:12px 24px;border-radius:8px;font-weight:600;font-size:14px;">View on TubeReturns →</a>
                  <p style="margin:32px 0 0;color:#9ca3af;font-size:12px;">— TubeReturns</p>
                </body>
                </html>
                """.formatted(escapeHtml(channelName), baseUrl);
        try {
            trySend(toEmail, "TubeReturns — " + channelName + " is ready", html);
            log.info("Channel processed email sent to {} for channel {}", toEmail, channelHandle);
            return true;
        } catch (Exception e) {
            log.error("Failed to send channel processed email to {} for channel {}: {}", toEmail, channelHandle, e.getMessage());
            return false;
        }
    }

    @Async
    public void sendContactEmail(String senderName, String senderEmail, String message) {
        if (mailSender == null) {
            log.warn("Mail not configured — contact message from {}: {}", senderEmail, message);
            return;
        }
        String escapedMessage = escapeHtml(message).replace("\n", "<br>");
        String escapedName = escapeHtml(senderName);
        String escapedEmail = escapeHtml(senderEmail);
        String html = """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"></head>
                <body style="font-family:sans-serif;max-width:480px;margin:0 auto;padding:32px 24px;color:#111;">
                  <div style="margin-bottom:24px;">
                    <img src="cid:logo" alt="TubeReturns" style="height:150px;" />
                  </div>
                  <h2 style="margin:0 0 12px;font-size:18px;font-weight:700;">New feedback message</h2>
                  <p style="margin:0 0 4px;color:#6b7280;font-size:13px;"><strong>From:</strong> %s &lt;%s&gt;</p>
                  <div style="margin:16px 0;padding:16px;background:#f9fafb;border-left:3px solid #16a34a;border-radius:4px;font-size:14px;color:#374151;line-height:1.6;">%s</div>
                  <p style="margin:24px 0 0;color:#9ca3af;font-size:12px;">— TubeReturns</p>
                </body>
                </html>
                """.formatted(escapedName, escapedEmail, escapedMessage);
        trySend("feedback@tubereturns.com", "TubeReturns feedback", html);

        String copyHtml = """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"></head>
                <body style="font-family:sans-serif;max-width:480px;margin:0 auto;padding:32px 24px;color:#111;">
                  <div style="margin-bottom:24px;">
                    <img src="cid:logo" alt="TubeReturns" style="height:150px;" />
                  </div>
                  <h2 style="margin:0 0 12px;font-size:18px;font-weight:700;">We received your message</h2>
                  <p style="margin:0 0 8px;color:#374151;font-size:15px;">Hi %s,</p>
                  <p style="margin:0 0 16px;color:#6b7280;font-size:14px;">Thanks for reaching out! Here's a copy of what you sent us:</p>
                  <div style="margin:0 0 24px;padding:16px;background:#f9fafb;border-left:3px solid #16a34a;border-radius:4px;font-size:14px;color:#374151;line-height:1.6;">%s</div>
                  <p style="margin:0 0 0;color:#9ca3af;font-size:12px;">We'll get back to you as soon as we can. — TubeReturns</p>
                </body>
                </html>
                """.formatted(escapedName, escapedMessage);
        trySend(senderEmail, "TubeReturns feedback — your message", copyHtml);
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private void trySend(String toEmail, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, "TubeReturns");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            helper.addInline("logo", LOGO);
            mailSender.send(message);
            counter("success").increment();
            log.info("Email sent to {}", toEmail);
        } catch (Exception e) {
            counter("failure").increment();
            log.warn("Failed to send email to {}: {}", toEmail, e.getMessage());
        }
    }

    private Counter counter(String outcome) {
        return Counter.builder("tubereturns.emails.sent")
                .tag("outcome", outcome)
                .register(meterRegistry);
    }
}
