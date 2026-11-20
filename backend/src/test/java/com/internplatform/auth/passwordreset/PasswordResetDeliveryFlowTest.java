package com.internplatform.auth.passwordreset;

import com.internplatform.user.User;
import com.internplatform.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Isolated tests: no database and no real email is sent. */
class PasswordResetDeliveryFlowTest {

    private final PasswordResetOtpRepository otpRepository = mock(PasswordResetOtpRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final PasswordResetEmailSender emailSender = mock(PasswordResetEmailSender.class);
    private final PasswordResetService service = new PasswordResetService(
            otpRepository, userRepository, passwordEncoder, emailSender);

    @Test
    void unknownEmailDoesNotSendButDoesNotRevealAccountExistence() {
        when(userRepository.findByEmailIgnoreCase("missing@example.test"))
                .thenReturn(Optional.empty());

        assertThatCode(() -> service.requestPasswordReset("  MISSING@example.test  "))
                .doesNotThrowAnyException();
        verifyNoInteractions(emailSender, otpRepository);
    }

    @Test
    void disabledAccountDoesNotSend() {
        User user = mock(User.class);
        when(user.isEnabled()).thenReturn(false);
        when(userRepository.findByEmailIgnoreCase("inactive@example.test"))
                .thenReturn(Optional.of(user));

        assertThatCode(() -> service.requestPasswordReset("inactive@example.test"))
                .doesNotThrowAnyException();
        verifyNoInteractions(emailSender, otpRepository);
    }

    @Test
    void eligibleAccountAttemptsEmailWithSixDigitOtp() {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        when(user.isEnabled()).thenReturn(true);
        when(user.getId()).thenReturn(userId);
        when(user.getEmail()).thenReturn("active@example.test");
        when(userRepository.findByEmailIgnoreCase("active@example.test"))
                .thenReturn(Optional.of(user));
        when(otpRepository.findAllByUserIdAndUsedAtIsNullAndInvalidatedAtIsNull(userId))
                .thenReturn(List.of());
        when(passwordEncoder.encode(anyString())).thenReturn("fake-hash");

        service.requestPasswordReset("active@example.test");

        var code = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendOtp(eq("active@example.test"), code.capture());
        assertThat(code.getValue()).matches("[0-9]{6}");
        verify(otpRepository).save(any(PasswordResetOtp.class));
    }
}
