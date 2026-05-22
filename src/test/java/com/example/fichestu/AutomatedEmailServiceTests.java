package com.example.fichestu;

import com.example.fichestu.persistence.entity.UserEntity;
import com.example.fichestu.service.AutomatedEmailService;
import com.example.fichestu.service.PasswordResetMailService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutomatedEmailServiceTests {

    @Test
    void registrationEmailIsSentWhenEnabled() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        AutomatedEmailService service = new AutomatedEmailService(
            providerFor(mailSender),
            providerFor(null),
            providerFor(null),
            true,
            "noreply@test.local"
        );

        service.sendRegistrationEmail(user());

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void transactionEmailContainsMovementDetails() {
        CapturingMailSender mailSender = new CapturingMailSender();
        AutomatedEmailService service = new AutomatedEmailService(
            providerFor(mailSender),
            providerFor(null),
            providerFor(null),
            true,
            "noreply@test.local"
        );

        service.sendTransactionEmail(user(), "BUY", new BigDecimal("-50.00"), "Compra de 1 FRO");

        assertThat(mailSender.message.getTo()).containsExactly("alice@test.com");
        assertThat(mailSender.message.getSubject()).isEqualTo("Movimiento en tu cuenta Fichestu");
        assertThat(mailSender.message.getText()).contains("Tipo: BUY");
        assertThat(mailSender.message.getText()).contains("Importe: -50.00 Stum");
        assertThat(mailSender.message.getText()).contains("Compra de 1 FRO");
    }

    @Test
    void emailIsSkippedWhenDisabled() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        AutomatedEmailService service = new AutomatedEmailService(
            providerFor(mailSender),
            providerFor(null),
            providerFor(null),
            false,
            "noreply@test.local"
        );

        service.sendImportantUpdateEmail(user(), "Aviso", "Mensaje", "SYSTEM");

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void passwordResetDoesNotFailRequestWhenProvidersAreDown() {
        PasswordResetMailService service = new PasswordResetMailService(
            providerFor(null),
            providerFor(null),
            providerFor(null),
            true,
            false,
            "noreply@test.local"
        );

        assertThatCode(() -> service.sendResetToken("alice@test.com", "123456", 15))
            .doesNotThrowAnyException();
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> providerFor(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setUsername("alice");
        user.setEmail("alice@test.com");
        user.setFiatBalance(new BigDecimal("100.00"));
        return user;
    }

    private static final class CapturingMailSender implements JavaMailSender {
        private SimpleMailMessage message;

        @Override
        public void send(SimpleMailMessage simpleMessage) {
            this.message = simpleMessage;
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) {
            this.message = simpleMessages[0];
        }

        @Override
        public jakarta.mail.internet.MimeMessage createMimeMessage() {
            throw new UnsupportedOperationException();
        }

        @Override
        public jakarta.mail.internet.MimeMessage createMimeMessage(java.io.InputStream contentStream) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(jakarta.mail.internet.MimeMessage mimeMessage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(jakarta.mail.internet.MimeMessage... mimeMessages) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(org.springframework.mail.javamail.MimeMessagePreparator mimeMessagePreparator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(org.springframework.mail.javamail.MimeMessagePreparator... mimeMessagePreparators) {
            throw new UnsupportedOperationException();
        }
    }
}
