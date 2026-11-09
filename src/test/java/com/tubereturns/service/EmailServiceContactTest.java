package com.tubereturns.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceContactTest {

    private static final String ATTACKER_NAME = "Account Security Team";
    private static final String ATTACKER_TEXT = "Your account is locked, visit evil.example to restore it";
    private static final String VICTIM = "victim@example.test";

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final EmailService emailService = new EmailService();

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
        ReflectionTestUtils.setField(emailService, "mailSender", mailSender);
        ReflectionTestUtils.setField(emailService, "meterRegistry", new SimpleMeterRegistry());
        ReflectionTestUtils.setField(emailService, "baseUrl", "https://tubereturns.example");
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@tubereturns.example");
    }

    @Test
    void acknowledgementToTheSuppliedAddressCarriesNoCallerContent() throws Exception {
        emailService.sendContactEmail(ATTACKER_NAME, VICTIM, ATTACKER_TEXT);

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(2)).send(sent.capture());
        List<MimeMessage> messages = sent.getAllValues();

        MimeMessage toVictim = messages.stream().filter(m -> recipients(m).contains(VICTIM)).findFirst().orElseThrow();
        String victimBody = textOf(toVictim);
        assertThat(victimBody).contains("We received your message");
        assertThat(victimBody).doesNotContain(ATTACKER_TEXT).doesNotContain("evil.example").doesNotContain(ATTACKER_NAME);
        assertThat(toVictim.getSubject()).doesNotContain(ATTACKER_NAME);

        MimeMessage toOperator = messages.stream().filter(m -> !recipients(m).contains(VICTIM)).findFirst().orElseThrow();
        assertThat(textOf(toOperator)).contains(ATTACKER_TEXT).contains(ATTACKER_NAME);
    }

    private List<String> recipients(MimeMessage message) {
        try {
            return Arrays.stream(message.getRecipients(Message.RecipientType.TO)).map(Address::toString).toList();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String textOf(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof String text) {
            return text;
        }
        StringBuilder all = new StringBuilder();
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                if (bodyPart.isMimeType("text/*") || bodyPart.isMimeType("multipart/*")) {
                    all.append(textOf(bodyPart));
                }
            }
        }
        return all.toString();
    }
}
