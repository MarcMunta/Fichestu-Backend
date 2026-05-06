package com.example.fichestu.service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class I18nService {

    private static final Map<String, Map<String, String>> TRANSLATIONS = Map.ofEntries(
        entry("Sesion invalida", "Sessio invalida", "Invalid session"),
        entry("No tienes permisos para esta operacion", "No tens permisos per a aquesta operacio", "You do not have permission for this action"),
        entry("Error interno del servidor", "Error intern del servidor", "Internal server error"),
        entry("Datos de entrada invalidos", "Dades d'entrada invalides", "Invalid input data"),
        entry("Credenciales invalidas", "Credencials invalides", "Invalid credentials"),
        entry("Token de Google invalido", "Token de Google invalid", "Invalid Google token"),
        entry("El username es obligatorio", "El nom d'usuari es obligatori", "Username is required"),
        entry("El email ya esta registrado", "L'email ja esta registrat", "Email is already registered"),
        entry("El username ya esta registrado", "El nom d'usuari ja esta registrat", "Username is already registered"),
        entry("El username ya esta en uso", "El nom d'usuari ja esta en us", "Username is already in use"),
        entry("El email ya esta en uso", "L'email ja esta en us", "Email is already in use"),
        entry("Introduce la contrasena actual", "Introdueix la contrasenya actual", "Enter your current password"),
        entry("La contrasena actual no es correcta", "La contrasenya actual no es correcta", "Current password is not correct"),
        entry("Las contrasenas no coinciden", "Les contrasenyes no coincideixen", "Passwords do not match"),
        entry("La contrasena debe tener al menos 6 caracteres", "La contrasenya ha de tenir almenys 6 caracters", "Password must be at least 6 characters"),
        entry("Token invalido o caducado", "Token invalid o caducat", "Invalid or expired token"),
        entry("No se pudo enviar el correo de recuperacion", "No s'ha pogut enviar el correu de recuperacio", "Could not send password recovery email"),
        entry("Ya tienes una partida activa", "Ja tens una partida activa", "You already have an active match"),
        entry("Match no encontrado", "Partida no trobada", "Match not found"),
        entry("La sala ya esta llena", "La sala ja esta plena", "The room is already full"),
        entry("La sala ya no acepta jugadores", "La sala ja no accepta jugadors", "The room no longer accepts players"),
        entry("El matchmaking ya no se puede cancelar", "El matchmaking ja no es pot cancel-lar", "Matchmaking can no longer be cancelled"),
        entry("No perteneces a esta sala", "No pertanys a aquesta sala", "You do not belong to this room"),
        entry("La sala no esta en fase de seleccion", "La sala no esta en fase de seleccio", "The room is not in selection phase"),
        entry("Ya elegiste una bola", "Ja has triat una bola", "You already chose a ball"),
        entry("Esa bola ya fue tomada", "Aquesta bola ja esta agafada", "That ball has already been taken"),
        entry("Aun no se puede revelar", "Encara no es pot revelar", "Cannot reveal yet"),
        entry("Aun faltan bolas por elegir", "Encara falten boles per triar", "There are still balls left to choose"),
        entry("Battle no desbloqueado", "Battle no desbloquejat", "Battle is not unlocked"),
        entry("Rewarded no disponible todavia", "Rewarded encara no disponible", "Rewarded is not available yet"),
        entry("Saldo insuficiente para comprar", "Saldo insuficient per comprar", "Not enough balance to buy"),
        entry("No tienes suficientes fichas para vender", "No tens prou fitxes per vendre", "Not enough tokens to sell"),
        entry("La cantidad debe ser mayor que cero", "La quantitat ha de ser mes gran que zero", "Amount must be greater than zero"),
        entry("Notificacion no encontrada", "Notificacio no trobada", "Notification not found"),
        entry("No puedes leer esta notificacion", "No pots llegir aquesta notificacio", "You cannot read this notification"),
        entry("La imagen es obligatoria", "La imatge es obligatoria", "Image is required"),
        entry("Formato de imagen no permitido", "Format d'imatge no permes", "Image format is not allowed"),
        entry("No se pudo guardar la imagen", "No s'ha pogut guardar la imatge", "Could not save the image"),
        entry("Imagen no encontrada", "Imatge no trobada", "Image not found")
    );

    public String translate(String message, HttpServletRequest request) {
        if (message == null || message.isBlank()) {
            return message;
        }
        String lang = resolveLanguage(request);
        if ("es".equals(lang)) {
            return message;
        }
        Map<String, String> translated = TRANSLATIONS.get(message);
        if (translated == null) {
            return message;
        }
        return translated.getOrDefault(lang, message);
    }

    private String resolveLanguage(HttpServletRequest request) {
        String header = request.getHeader("Accept-Language");
        if (header == null || header.isBlank()) {
            return "es";
        }
        String normalized = header.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("ca")) {
            return "ca";
        }
        if (normalized.startsWith("en")) {
            return "en";
        }
        return "es";
    }

    private static Map.Entry<String, Map<String, String>> entry(String es, String ca, String en) {
        return Map.entry(es, Map.of("ca", ca, "en", en));
    }
}
