package com.example.fichestu.service;

import com.example.fichestu.persistence.entity.UserEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AutomatedEmailService {

    private static final Logger log = LoggerFactory.getLogger(AutomatedEmailService.class);

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String fromAddress;

    public AutomatedEmailService(
        ObjectProvider<JavaMailSender> mailSenderProvider,
        @Value("${app.email.enabled:false}") boolean enabled,
        @Value("${app.email.from:noreply@fichestu.local}") String fromAddress
    ) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.enabled = enabled;
        this.fromAddress = fromAddress;
    }

    public void sendRegistrationEmail(UserEntity user) {
        sendAfterCommit(
            user.getEmail(),
            "Bienvenido a Fichestu",
            """
                Hola %s,

                Tu cuenta de Fichestu se ha creado correctamente.
                Saldo inicial: %s FTC.

                Ya puedes iniciar sesion y empezar a jugar.
                """.formatted(user.getUsername(), formatMoney(user.getFiatBalance())),
            "REGISTRATION"
        );
    }

    public void sendTransactionEmail(UserEntity user, String type, BigDecimal amount, String description) {
        sendAfterCommit(
            user.getEmail(),
            "Movimiento en tu cuenta Fichestu",
            """
                Hola %s,

                Se ha registrado un movimiento en tu cuenta.

                Tipo: %s
                Importe: %s FTC
                Detalle: %s
                Saldo actual: %s FTC
                """.formatted(
                    user.getUsername(),
                    type,
                    formatMoney(amount),
                    description,
                    formatMoney(user.getFiatBalance())
                ),
            "TRANSACTION_" + type
        );
    }

    public void sendImportantUpdateEmail(UserEntity user, String title, String message, String type) {
        sendAfterCommit(
            user.getEmail(),
            "Fichestu: " + title,
            """
                Hola %s,

                %s

                Tipo de aviso: %s
                """.formatted(user.getUsername(), message, type),
            "UPDATE_" + type
        );
    }

    private void sendAfterCommit(String to, String subject, String body, String eventType) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendBestEffort(to, subject, body, eventType);
                }
            });
            return;
        }

        sendBestEffort(to, subject, body, eventType);
    }

    private void sendBestEffort(String to, String subject, String body, String eventType) {
        if (!enabled || mailSender == null) {
            log.debug("Automated email skipped for {} to {} because email is disabled", eventType, to);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        try {
            mailSender.send(message);
        } catch (MailException ex) {
            log.warn("Automated email {} could not be sent to {}: {}", eventType, to, ex.getMessage());
        }
    }

    private String formatMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
