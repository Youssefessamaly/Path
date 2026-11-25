package com.internplatform.support;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.server.ResponseStatusException;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Isolated tests: a mocked JavaMailSender, no real database or SMTP connection. */
class SupportRequestServiceTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final SupportRequestRateLimiter rateLimiter = mock(SupportRequestRateLimiter.class);

    private SupportRequestService service(String fromAddress) {
        return new SupportRequestService(
                mailSender, rateLimiter, fromAddress, "fallback@example.test", "path.app.support@gmail.com");
    }

    private static SupportRequestCreateRequest realRequest(String honeypot) {
        return new SupportRequestCreateRequest(
                "Jordan Visitor", "jordan@example.test", SupportAccountType.INTERN,
                "Can't reset my password", "I never receive the reset code email.", honeypot);
    }

    private MimeMessage realMimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    @Test
    void honeypotTrippedSubmissionReportsSuccessButNeverSendsOrCountsAgainstRateLimit() {
        SupportRequestService service = service("support@path.test");

        boolean submitted = service.submit(realRequest("https://spam.example"), "203.0.113.5");

        assertThat(submitted).isTrue();
        verifyNoInteractions(mailSender);
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void legitimateSubmissionIsRateLimitedPerIp() {
        SupportRequestService service = service("support@path.test");
        when(rateLimiter.tryAcquire("203.0.113.5", 5)).thenReturn(false);

        assertThatThrownBy(() -> service.submit(realRequest(null), "203.0.113.5"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Too many requests");
        verifyNoInteractions(mailSender);
    }

    @Test
    void legitimateSubmissionSendsToTheConfiguredSupportInboxWithReplyToTheVisitor() throws Exception {
        SupportRequestService service = service("support@path.test");
        when(rateLimiter.tryAcquire(any(), anyInt())).thenReturn(true);
        MimeMessage message = realMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(message);

        boolean submitted = service.submit(realRequest(""), "203.0.113.5");

        assertThat(submitted).isTrue();
        verify(mailSender).send(message);
        assertThat(message.getAllRecipients()[0].toString()).contains("path.app.support@gmail.com");
        assertThat(message.getReplyTo()[0].toString()).contains("jordan@example.test");
        assertThat(message.getSubject()).contains("INTERN").contains("Can't reset my password");
    }

    @Test
    void unconfiguredSenderFailsCleanlyWithoutAttemptingToSend() {
        // Both fallback sources blank — service("") alone still resolves via smtpUsername.
        SupportRequestService service = new SupportRequestService(
                mailSender, rateLimiter, "", "", "path.app.support@gmail.com");
        when(rateLimiter.tryAcquire(any(), anyInt())).thenReturn(true);

        assertThatThrownBy(() -> service.submit(realRequest(null), "203.0.113.5"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Could not send");
        verifyNoInteractions(mailSender);
    }
}
