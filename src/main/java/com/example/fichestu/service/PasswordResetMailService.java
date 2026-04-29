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
    private final boolean mailEnabled;
    private final String fromAddress;

    public PasswordResetMailService(
        ObjectProvider<JavaMailSender> mailSenderProvider,
        @Value("${app.password-reset.mail-enabled:false}") boolean mailEnabled,
        @Value("${app.password-reset.from:noreply@fichestu.local}") String fromAddress
    ) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.mailEnabled = mailEnabled;
        this.fromAddress = fromAddress;
    }

    public void sendResetToken(String email, String token, long expirationMinutes) {
        if (!mailEnabled || mailSender == null) {
            log.info("Password reset token for {}: {} (expires in {} minutes)", email, token, expirationMinutes);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("Restablecer contrasena en Fichestu");
        message.setText("""
            Hemos recibido una solicitud para restablecer tu contrasena en Fichestu.

            Codigo de verificacion: %s

            Este codigo caduca en %d minutos. Si no has sido tu, ignora este correo.
            """.formatted(token, expirationMinutes));

        try {
            mailSender.send(message);
        } catch (MailException ex) {
            log.warn("Password reset email could not be sent to {}: {}", email, ex.getMessage());
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "No se pudo enviar el correo de recuperacion"
            );
        }
    }
}
