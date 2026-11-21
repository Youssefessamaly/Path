package com.internplatform.auth.passwordreset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import javax.net.ssl.SSLException;
import java.io.UnsupportedEncodingException;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Sends password reset codes through the configured SMTP provider.
 * Never log the recipient email address, the OTP, or SMTP credentials.
 */
@Component
public class SmtpPasswordResetEmailSender implements PasswordResetEmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpPasswordResetEmailSender.class);
    private static final String DELIVERY_UNAVAILABLE =
            "Password reset email is temporarily unavailable. Please try again later.";

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpPasswordResetEmailSender(
            JavaMailSender mailSender,
            @Value("${app.password-reset.from-address:}") String configuredFrom,
            @Value("${spring.mail.username:}") String smtpUsername
    ) {
        this.mailSender = mailSender;
        // A verified sender or the authenticated SMTP account must be used in production.
        this.fromAddress = configuredFrom == null || configuredFrom.isBlank()
                ? smtpUsername == null ? "" : smtpUsername.trim()
                : configuredFrom.trim();
    }

    @Override
    public void sendOtp(String recipientEmail, String otp) {
        if (fromAddress.isBlank()) {
            log.error("Password reset SMTP sender is unconfigured. Set SMTP_FROM or SMTP_USERNAME.");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, DELIVERY_UNAVAILABLE);
        }

        try {
            // MIME multipart/alternative includes both polished HTML and a readable
            // plain-text alternative for accessibility and email clients.
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, "PATH Support");
            helper.setTo(recipientEmail);
            helper.setSubject("Your PATH password reset code");
            helper.setText(
                    PasswordResetEmailTemplate.plainText(otp),
                    PasswordResetEmailTemplate.html(otp)
            );
            message.setHeader("Auto-Submitted", "auto-generated");
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException | MailException exception) {
            // Log only exception TYPES: provider exceptions may contain sensitive mail details.
            String category = exception instanceof MailAuthenticationException
                    ? "SMTP_AUTHENTICATION"
                    : "SMTP_CONNECTION_OR_DELIVERY";
            Throwable root = exception;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            String tlsDetail = tlsDiagnostic(root);
            if (mailSender instanceof JavaMailSenderImpl sender) {
                // These are non-secret connection settings. Never log usernames,
                // passwords, recipients, OTPs or the raw provider exception message.
                var props = sender.getJavaMailProperties();
                log.error("PATH password reset email delivery failed: category={}, causeType={}, "
                                + "tlsDetail={}, smtpPort={}, sslEnabled={}, starttlsEnabled={}, "
                                + "starttlsRequired={}.",
                        category, root.getClass().getSimpleName(), tlsDetail, sender.getPort(),
                        props.getProperty("mail.smtp.ssl.enable", "false"),
                        props.getProperty("mail.smtp.starttls.enable", "false"),
                        props.getProperty("mail.smtp.starttls.required", "false"));
            } else {
                log.error("PATH password reset email delivery failed: category={}, causeType={}, tlsDetail={}.",
                        category, root.getClass().getSimpleName(), tlsDetail);
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, DELIVERY_UNAVAILABLE);
        }
    }
    /**
     * Diagnose common TLS failures without logging arbitrary provider exception
     * messages, which could contain recipient information or server details.
     */
    private static String tlsDiagnostic(Throwable root) {
        if (!(root instanceof SSLException)) {
            return "NOT_SSL";
        }
        String detail = root.getMessage() == null
                ? "" : root.getMessage().toLowerCase(Locale.ROOT);
        if (detail.contains("unrecognized ssl message")
                || detail.contains("plaintext connection")
                || detail.contains("wrong version number")) {
            return "SSL_MODE_OR_PORT_MISMATCH";
        }
        if (detail.contains("pkix") || detail.contains("certification path")
                || detail.contains("certificate_unknown") || detail.contains("certificate")) {
            return "CERTIFICATE_VALIDATION";
        }
        if (detail.contains("handshake") || detail.contains("protocol_version")
                || detail.contains("no appropriate protocol")) {
            return "TLS_HANDSHAKE";
        }
        if (detail.contains("connection reset") || detail.contains("connection closed")
                || detail.contains("remote host terminated") || detail.contains("eof")) {
            return "CONNECTION_TERMINATED";
        }
        return "SSL_OTHER";
    }
}
