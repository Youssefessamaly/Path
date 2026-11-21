package com.internplatform.auth.passwordreset;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.server.ResponseStatusException;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpPasswordResetEmailSenderTest {

    @Test
    void sendsBrandedMultipartEmailWithPlainTextFallbackAndLeadingZeros() throws Exception {
        JavaMailSender mailSender = configuredMailSender();
        SmtpPasswordResetEmailSender sender =
                new SmtpPasswordResetEmailSender(mailSender, "path.app.support@gmail.com", "other@gmail.com");

        sender.sendOtp("student@example.test", "003421");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        InternetAddress from = (InternetAddress) message.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("path.app.support@gmail.com");
        assertThat(from.getPersonal()).isEqualTo("PATH Support");
        assertThat(((InternetAddress) message.getRecipients(MimeMessage.RecipientType.TO)[0]).getAddress())
                .isEqualTo("student@example.test");
        assertThat(message.getSubject()).isEqualTo("Your PATH password reset code");
        assertThat(message.getHeader("Auto-Submitted", null)).isEqualTo("auto-generated");
        assertThat(message.isMimeType("multipart/*")).isTrue();

        String text = findPart(message, "text/plain");
        String html = findPart(message, "text/html");
        // Keep all six digits contiguous in both MIME alternatives for iOS Mail.
        assertThat(text).contains("Your PATH password reset verification code is:\n003421\n", "5 minutes", "Never share this code");
        assertThat(html).contains("PATH", "Your verification code", "#0D4A43", "5 minutes");
        assertThat(html).contains("003421");
        assertThat(html.split("003421", -1)).hasSize(2);
        assertThat(html).doesNotContain("width:16%", "Copy-friendly code");
        assertThat(html).doesNotContain("<img", "<script", "http://", "https://");
    }

    @Test
    void fallsBackToAuthenticatedSmtpAddressWhenFromUnset() throws Exception {
        JavaMailSender mailSender = configuredMailSender();
        SmtpPasswordResetEmailSender sender =
                new SmtpPasswordResetEmailSender(mailSender, "", "path.app.support@gmail.com");

        sender.sendOtp("student@example.test", "000001");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(((InternetAddress) captor.getValue().getFrom()[0]).getAddress())
                .isEqualTo("path.app.support@gmail.com");
    }

    @Test
    void rejectsMissingSenderWithoutAttemptingDelivery() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpPasswordResetEmailSender sender = new SmtpPasswordResetEmailSender(mailSender, "", "");

        assertThatThrownBy(() -> sender.sendOtp("student@example.test", "123456"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void handlesSmtpAuthenticationFailureAsTemporaryServiceOutage() {
        JavaMailSender mailSender = configuredMailSender();
        doThrow(new MailAuthenticationException("Invalid app password"))
                .when(mailSender).send(any(MimeMessage.class));
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
        JavaMailSender mailSender = configuredMailSender();
        doThrow(new MailSendException("Connection refused"))
                .when(mailSender).send(any(MimeMessage.class));
        SmtpPasswordResetEmailSender sender =
                new SmtpPasswordResetEmailSender(mailSender, "path.app.support@gmail.com", "");

        assertThatThrownBy(() -> sender.sendOtp("student@example.test", "123456"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void rejectsBadOtpBeforeAttemptingEmail() {
        JavaMailSender mailSender = configuredMailSender();
        SmtpPasswordResetEmailSender sender =
                new SmtpPasswordResetEmailSender(mailSender, "path.app.support@gmail.com", "");

        assertThatThrownBy(() -> sender.sendOtp("student@example.test", "<script>"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    private static JavaMailSender configuredMailSender() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(invocation ->
                new MimeMessage(Session.getInstance(new Properties())));
        return sender;
    }

    private static String findPart(Part part, String mimeType) throws Exception {
        if (part.isMimeType(mimeType)) {
            return (String) part.getContent();
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int index = 0; index < multipart.getCount(); index++) {
                String content = findPart(multipart.getBodyPart(index), mimeType);
                if (content != null) return content;
            }
        }
        return null;
    }
}
