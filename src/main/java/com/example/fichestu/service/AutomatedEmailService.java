package com.example.fichestu.service;

import com.example.fichestu.persistence.entity.UserEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
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
    private final GoogleAppsScriptEmailClient googleAppsScriptEmailClient;
    private final ResendEmailClient resendEmailClient;
    private final boolean enabled;
    private final String fromAddress;

    public AutomatedEmailService(
        ObjectProvider<JavaMailSender> mailSenderProvider,
        ObjectProvider<GoogleAppsScriptEmailClient> googleAppsScriptEmailClientProvider,
        ObjectProvider<ResendEmailClient> resendEmailClientProvider,
        @Value("${app.email.enabled:false}") boolean enabled,
        @Value("${app.email.from:noreply@fichestu.local}") String fromAddress
    ) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.googleAppsScriptEmailClient = googleAppsScriptEmailClientProvider.getIfAvailable();
        this.resendEmailClient = resendEmailClientProvider.getIfAvailable();
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
                Cartera inicial: 10 fichas de cada tipo.

                Ya puedes iniciar sesion y empezar a jugar.
                """.formatted(user.getUsername()),
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
                El valor total depende del precio actual de tus fichas.
                """.formatted(
                    user.getUsername(),
                    type,
                    formatMoney(amount),
                    description
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
        if (!enabled) {
            log.debug("Automated email skipped for {} to {} because email is disabled", eventType, to);
            return;
        }

        if (googleAppsScriptEmailClient != null && googleAppsScriptEmailClient.isConfigured()) {
            try {
                googleAppsScriptEmailClient.sendTextEmail(to, subject, body, idempotencyKey(eventType, to, subject));
                return;
            } catch (EmailDeliveryException ex) {
                log.warn("Automated email {} could not be sent to {} through Google Apps Script: {}", eventType, to, ex.getMessage());
            }
        }

        if (mailSender == null) {
            log.debug("SMTP mail sender is not configured for {} to {}", eventType, to);
        } else {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);

            try {
                mailSender.send(message);
                return;
            } catch (MailException ex) {
                log.warn("Automated email {} could not be sent to {} through SMTP: {}", eventType, to, ex.getMessage());
            }
        }

        if (resendEmailClient != null && resendEmailClient.isConfigured()) {
            try {
                resendEmailClient.sendTextEmail(to, subject, body, idempotencyKey(eventType, to, subject));
            } catch (EmailDeliveryException ex) {
                log.warn("Automated email {} could not be sent to {} through Resend: {}", eventType, to, ex.getMessage());
            }
        }
    }

    private String formatMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String idempotencyKey(String eventType, String to, String subject) {
        return eventType + "-" + UUID.randomUUID();
    }
}
