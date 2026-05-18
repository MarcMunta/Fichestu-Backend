package com.example.fichestu.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordResetMailService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailService.class);

    private final JavaMailSender mailSender;
    private final GoogleAppsScriptEmailClient googleAppsScriptEmailClient;
    private final ResendEmailClient resendEmailClient;
    private final boolean mailEnabled;
    private final String fromAddress;

    public PasswordResetMailService(
        ObjectProvider<JavaMailSender> mailSenderProvider,
        ObjectProvider<GoogleAppsScriptEmailClient> googleAppsScriptEmailClientProvider,
        ObjectProvider<ResendEmailClient> resendEmailClientProvider,
        @Value("${app.password-reset.mail-enabled:false}") boolean mailEnabled,
        @Value("${app.password-reset.from:noreply@fichestu.local}") String fromAddress
    ) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.googleAppsScriptEmailClient = googleAppsScriptEmailClientProvider.getIfAvailable();
        this.resendEmailClient = resendEmailClientProvider.getIfAvailable();
        this.mailEnabled = mailEnabled;
        this.fromAddress = fromAddress;
    }

    public void sendResetToken(String email, String token, long expirationMinutes) {
        if (!mailEnabled) {
            log.info("Password reset token for {}: {} (expires in {} minutes)", email, token, expirationMinutes);
            return;
        }

        String subject = "Restablecer contrasena en Fichestu";
        String body = """
            Hemos recibido una solicitud para restablecer tu contrasena en Fichestu.

            Codigo de verificacion: %s

            Este codigo caduca en %d minutos. Si no has sido tu, ignora este correo.
            """.formatted(token, expirationMinutes);

        if (googleAppsScriptEmailClient != null && googleAppsScriptEmailClient.isConfigured()) {
            try {
                googleAppsScriptEmailClient.sendTextEmail(email, subject, body, "password-reset-" + email + "-" + token);
                return;
            } catch (EmailDeliveryException ex) {
                log.warn("Password reset email could not be sent to {} through Google Apps Script: {}", email, ex.getMessage());
            }
        }

        if (mailSender == null) {
            log.warn("SMTP mail sender is not configured for password reset email to {}", email);
        } else {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(email);
            message.setSubject(subject);
            message.setText(body);

            try {
                mailSender.send(message);
                return;
            } catch (MailException ex) {
                log.warn("Password reset email could not be sent to {} through SMTP: {}", email, ex.getMessage());
            }
        }

        if (resendEmailClient != null && resendEmailClient.isConfigured()) {
            try {
                resendEmailClient.sendTextEmail(email, subject, body, "password-reset-" + email + "-" + token);
                return;
            } catch (EmailDeliveryException ex) {
                log.warn("Password reset email could not be sent to {} through Resend: {}", email, ex.getMessage());
            }
        }

        throw recoveryEmailFailure();
    }

    private ResponseStatusException recoveryEmailFailure() {
        return new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "No se pudo enviar el correo de recuperacion"
        );
    }
}
