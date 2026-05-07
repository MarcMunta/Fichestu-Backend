package com.example.fichestu.service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class I18nService {

    private static final Map<String, Map<String, String>> TRANSLATIONS = Map.ofEntries(
        entry("Sesion invalida", "Sesión inválida", "Sessió invàlida", "Invalid session"),
        entry("No tienes permisos para esta operacion", "No tienes permisos para esta operación", "No tens permisos per a aquesta operació", "You do not have permission for this action"),
        entry("Error interno del servidor", "Error intern del servidor", "Internal server error"),
        entry("Datos de entrada invalidos", "Datos de entrada inválidos", "Dades d'entrada invàlides", "Invalid input data"),
        entry("Credenciales invalidas", "Credenciales inválidas", "Credencials invàlides", "Invalid credentials"),
        entry("Token de Google invalido", "Token de Google inválido", "Token de Google invàlid", "Invalid Google token"),
        entry("El username es obligatorio", "El username es obligatorio", "El nom d'usuari és obligatori", "Username is required"),
        entry("El email ya esta registrado", "El email ya está registrado", "L'email ja està registrat", "Email is already registered"),
        entry("El username ya esta registrado", "El username ya está registrado", "El nom d'usuari ja està registrat", "Username is already registered"),
        entry("El username ya esta en uso", "El username ya está en uso", "El nom d'usuari ja està en ús", "Username is already in use"),
        entry("El email ya esta en uso", "El email ya está en uso", "L'email ja està en ús", "Email is already in use"),
        entry("Introduce la contrasena actual", "Introduce la contraseña actual", "Introdueix la contrasenya actual", "Enter your current password"),
        entry("La contrasena actual no es correcta", "La contraseña actual no es correcta", "La contrasenya actual no és correcta", "Current password is not correct"),
        entry("Las contrasenas no coinciden", "Las contraseñas no coinciden", "Les contrasenyes no coincideixen", "Passwords do not match"),
        entry("La contrasena debe tener al menos 6 caracteres", "La contraseña debe tener al menos 6 caracteres", "La contrasenya ha de tenir almenys 6 caràcters", "Password must be at least 6 characters"),
        entry("Token invalido o caducado", "Token inválido o caducado", "Token invàlid o caducat", "Invalid or expired token"),
        entry("No se pudo enviar el correo de recuperacion", "No se pudo enviar el correo de recuperación", "No s'ha pogut enviar el correu de recuperació", "Could not send password recovery email"),
        entry("Ya tienes una partida activa", "Ja tens una partida activa", "You already have an active match"),
        entry("Match no encontrado", "Partida no trobada", "Match not found"),
        entry("La sala ya esta llena", "La sala ya está llena", "La sala ja està plena", "The room is already full"),
        entry("La sala ya no acepta jugadores", "La sala ja no accepta jugadors", "The room no longer accepts players"),
        entry("El matchmaking ya no se puede cancelar", "El matchmaking ja no es pot cancel-lar", "Matchmaking can no longer be cancelled"),
        entry("No perteneces a esta sala", "No pertanys a aquesta sala", "You do not belong to this room"),
        entry("La sala no esta en fase de seleccion", "La sala no está en fase de selección", "La sala no està en fase de selecció", "The room is not in selection phase"),
        entry("Ya elegiste una bola", "Ja has triat una bola", "You already chose a ball"),
        entry("Esa bola ya fue tomada", "Aquesta bola ja esta agafada", "That ball has already been taken"),
        entry("Aun no se puede revelar", "Aún no se puede revelar", "Encara no es pot revelar", "Cannot reveal yet"),
        entry("Aun faltan bolas por elegir", "Aún faltan bolas por elegir", "Encara falten boles per triar", "There are still balls left to choose"),
        entry("Battle no desbloqueado", "Battle no desbloquejat", "Battle is not unlocked"),
        entry("Rewarded no disponible todavia", "Rewarded no disponible todavía", "Rewarded encara no disponible", "Rewarded is not available yet"),
        entry("Saldo insuficiente para comprar", "Saldo insuficient per comprar", "Not enough balance to buy"),
        entry("No tienes suficientes fichas para vender", "No tens prou fitxes per vendre", "Not enough tokens to sell"),
        entry("La cantidad debe ser mayor que cero", "La cantidad debe ser mayor que cero", "La quantitat ha de ser més gran que zero", "Amount must be greater than zero"),
        entry("Notificacion no encontrada", "Notificación no encontrada", "Notificació no trobada", "Notification not found"),
        entry("No puedes leer esta notificacion", "No puedes leer esta notificación", "No pots llegir aquesta notificació", "You cannot read this notification"),
        entry("Notificacion leida", "Notificación leída", "Notificació llegida", "Notification read"),
        entry("Notificaciones leidas", "Notificaciones leídas", "Notificacions llegides", "Notifications read"),
        entry("Notificaciones eliminadas", "Notificaciones eliminadas", "Notificacions eliminades", "Notifications deleted"),
        entry("Bola confirmada", "Bola confirmada", "Bola confirmada", "Ball confirmed"),
        entry("Multiplicadores revelados", "Multiplicadores revelados", "Multiplicadors revelats", "Multipliers revealed"),
        entry("Ya puedes pasar al Battle Royale.", "Ya puedes pasar al Battle Royale.", "Ja pots passar al Battle Royale.", "You can move to Battle Royale."),
        entry("Idioma actualizado", "Idioma actualizado", "Idioma actualitzat", "Language updated"),
        entry("Idioma no soportado", "Idioma no soportado", "Idioma no admès", "Unsupported language"),
        entry("La imagen es obligatoria", "La imagen es obligatoria", "La imatge és obligatòria", "Image is required"),
        entry("Formato de imagen no permitido", "Formato de imagen no permitido", "Format d'imatge no permès", "Image format is not allowed"),
        entry("No se pudo guardar la imagen", "No s'ha pogut guardar la imatge", "Could not save the image"),
        entry("Imagen no encontrada", "Imatge no trobada", "Image not found")
    );

    public String translate(String message, HttpServletRequest request) {
        if (message == null || message.isBlank()) {
            return message;
        }
        String lang = resolveLanguage(request);
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
        return Map.entry(es, Map.of("es", es, "ca", ca, "en", en));
    }

    private static Map.Entry<String, Map<String, String>> entry(String key, String es, String ca, String en) {
        return Map.entry(key, Map.of("es", es, "ca", ca, "en", en));
    }
}
