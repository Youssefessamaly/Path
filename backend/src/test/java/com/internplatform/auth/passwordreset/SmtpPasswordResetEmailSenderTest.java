package com.internplatform.auth.passwordreset;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import org.mockito.ArgumentCaptor;

class SmtpPasswordResetEmailSenderTest {

    @Test
    void usesVerifiedSenderAndPathBrandingWithoutChangingOtp() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpPasswordResetEmailSender sender =
                new SmtpPasswordResetEmailSender(mailSender, "path.app.support@gmail.com", "other@gmail.com");

        sender.sendOtp("student@example.test", "003421");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom()).isEqualTo("path.app.support@gmail.com");
        assertThat(message.getTo()).containsExactly("student@example.test");
        assertThat(message.getSubject()).isEqualTo("PATH password reset code");
        assertThat(message.getText()).contains("003421", "5 minutes");
    }

    @Test
    void fallsBackToAuthenticatedSmtpAddressWhenFromUnset() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpPasswordResetEmailSender sender =
                new SmtpPasswordResetEmailSender(mailSender, "", "path.app.support@gmail.com");

        sender.sendOtp("student@example.test", "000001");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getFrom()).isEqualTo("path.app.support@gmail.com");
    }

    @Test
    void rejectsMissingSenderWithoutAttemptingDelivery() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpPasswordResetEmailSender sender = new SmtpPasswordResetEmailSender(mailSender, "", "");

        assertThatThrownBy(() -> sender.sendOtp("student@example.test", "123456"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void handlesSmtpAuthenticationFailureAsTemporaryServiceOutage() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new MailAuthenticationException("Invalid app password"))
                .when(mailSender).send(any(SimpleMailMessage.class));
        SmtpPasswordResetEmailSender sender =
                new SmtpPasswordResetEmailSender(mailSender, "path.app.support@gmail.com", "");

        assertThatThrownBy(() -> sender.sendOtp("student@example.test", "123456"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getReason()).doesNotContain("123456", "student@example.test");
                });
    }

    @Test
    void handlesSmtpTransportFailureAsTemporaryServiceOutage() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new MailSendException("Connection refused"))
                .when(mailSender).send(any(SimpleMailMessage.class));
        SmtpPasswordResetEmailSender sender =
                new SmtpPasswordResetEmailSender(mailSender, "path.app.support@gmail.com", "");

        assertThatThrownBy(() -> sender.sendOtp("student@example.test", "123456"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
