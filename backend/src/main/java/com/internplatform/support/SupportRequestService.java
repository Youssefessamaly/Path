package com.internplatform.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;

/**
 * Delivers a public support-form submission (legal-web/support.html) to PATH's support inbox.
 * Reuses the exact same SMTP infrastructure (JavaMailSender bean, spring.mail.* config) already
 * running in production for password-reset emails — see SmtpPasswordResetEmailSender — rather
 * than introducing a second mail pathway. Never logs the visitor's name, email, or message
 * content; only exception categories, matching that same class's logging discipline.
 */
@Service
public class SupportRequestService {

    private static final Logger log = LoggerFactory.getLogger(SupportRequestService.class);
    private static final String DELIVERY_UNAVAILABLE =
            "Could not send your message right now. Please try again later, or email us directly.";
    private static final int MAX_SUBMISSIONS_PER_IP_PER_HOUR = 5;

    private final JavaMailSender mailSender;
    private final SupportRequestRateLimiter rateLimiter;
    private final String fromAddress;
    private final String notificationEmail;

    public SupportRequestService(
            JavaMailSender mailSender,
            SupportRequestRateLimiter rateLimiter,
            @Value("${app.password-reset.from-address:}") String configuredFrom,
            @Value("${spring.mail.username:}") String smtpUsername,
            @Value("${app.support.notification-email:path.app.support@gmail.com}") String notificationEmail
    ) {
        this.mailSender = mailSender;
        this.rateLimiter = rateLimiter;
        // Same verified-sender resolution as SmtpPasswordResetEmailSender: a configured sender
        // or the authenticated SMTP account, never a made-up address.
        this.fromAddress = configuredFrom == null || configuredFrom.isBlank()
                ? smtpUsername == null ? "" : smtpUsername.trim()
                : configuredFrom.trim();
        this.notificationEmail = notificationEmail;
    }

    /**
     * @return true once genuinely sent. A honeypot-tripped submission returns normally without
     *         throwing and without sending anything — see SupportRequestController, which maps
     *         both cases to the identical success response so a bot gets no signal either way.
     */
    public boolean submit(SupportRequestCreateRequest request, String clientIp) {
        if (request.website() != null && !request.website().isBlank()) {
            // Honeypot tripped — silently accept, never send.
            return true;
        }

        if (!rateLimiter.tryAcquire(clientIp, MAX_SUBMISSIONS_PER_IP_PER_HOUR)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please try again later.");
        }

        if (fromAddress.isBlank()) {
            log.error("Support request SMTP sender is unconfigured. Set SMTP_FROM or SMTP_USERNAME.");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, DELIVERY_UNAVAILABLE);
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress, "PATH Support Form");
            helper.setTo(notificationEmail);
            // So a support agent can just hit "reply" and it reaches the visitor directly,
            // while From stays the authenticated/verified sender most SMTP providers require.
            helper.setReplyTo(request.email(), request.fullName());
            helper.setSubject("[PATH Support] (" + request.accountType() + ") " + request.subject());
            helper.setText(plainTextBody(request));
            message.setHeader("Auto-Submitted", "auto-generated");
            mailSender.send(message);
            return true;
        } catch (MessagingException | UnsupportedEncodingException | MailException exception) {
            String category = exception instanceof MailAuthenticationException
                    ? "SMTP_AUTHENTICATION"
                    : "SMTP_CONNECTION_OR_DELIVERY";
            log.error("PATH support request delivery failed: category={}, causeType={}.",
                    category, rootCause(exception).getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, DELIVERY_UNAVAILABLE);
        }
    }

    private static Throwable rootCause(Throwable exception) {
        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    private static String plainTextBody(SupportRequestCreateRequest request) {
        return "New PATH support request\n"
                + "\n"
                + "From: " + request.fullName() + " <" + request.email() + ">\n"
                + "Account type: " + request.accountType() + "\n"
                + "Subject: " + request.subject() + "\n"
                + "\n"
                + "Message:\n"
                + request.message() + "\n"
                + "\n"
                + "---\n"
                + "Submitted via path-legal.pages.dev/support.html. Reply to this email to "
                + "respond directly to the visitor.\n";
    }
}
