package it.coinquilini.service;

import it.coinquilini.dao.UserDAO;
import it.coinquilini.model.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Autenticazione e profilo: le credenziali non entrano nella sessione HTTP. */
@Service
public class AuthService {
    public static final String SESSION_USER_ID = "userId";

    private final UserDAO userDAO;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public User register(String name, String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedName = validateName(name);
        validatePassword(password);
        if (userDAO.findByEmail(normalizedEmail) != null) {
            throw duplicateEmail();
        }

        User user = new User();
        user.setName(normalizedName);
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("USER");
        user.setActive(true);
        user.setColor("#2563eb");
        user.setPhoto("");
        try {
            userDAO.save(user);
        } catch (DuplicateKeyException ex) {
            // Il vincolo UNIQUE gestisce anche registrazioni simultanee della stessa email.
            throw duplicateEmail();
        }
        return user;
    }

    public User login(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        validatePassword(password);
        User user = userDAO.findByEmail(normalizedEmail);
        // matches accetta solo hash BCrypt: nessun fallback per password in chiaro.
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenziali non valide.");
        }
        if (!user.isActive()) {
            throw inactiveAccount();
        }
        return user;
    }

    /** Da riutilizzare negli endpoint autenticati: rilegge sempre lo stato dal DB. */
    public User currentUser(HttpSession session) {
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Nessuna sessione attiva.");
        }
        Object userId = session.getAttribute(SESSION_USER_ID);
        if (!(userId instanceof Long id)) {
            session.invalidate();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessione non valida. Effettua nuovamente il login.");
        }
        User user = userDAO.findById(id);
        if (user == null) {
            session.invalidate();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utente non più disponibile.");
        }
        if (!user.isActive()) {
            session.invalidate();
            throw inactiveAccount();
        }
        return user;
    }

    public User updateProfile(HttpSession session, String name, String color, String photo) {
        User user = currentUser(session);
        if (name != null) {
            user.setName(validateName(name));
        }
        if (color != null) {
            if (!color.matches("#[0-9a-fA-F]{6}")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Colore non valido: usa il formato #RRGGBB.");
            }
            user.setColor(color);
        }
        if (photo != null) {
            user.setPhoto(photo);
        }
        userDAO.update(user);
        return user;
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inserisci un'email valida.");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 150 || !normalized.matches("[^\\s@]+@[^\\s@]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inserisci un'email valida.");
        }
        return normalized;
    }

    private String validateName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Il nome è obbligatorio e può contenere al massimo 100 caratteri.");
        }
        return name.trim();
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La password è obbligatoria.");
        }
        // BCrypt considera al massimo 72 byte: impedisce password troncate equivalenti.
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La password può contenere al massimo 72 byte UTF-8.");
        }
    }

    private ResponseStatusException duplicateEmail() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Email già registrata con un altro account.");
    }

    private ResponseStatusException inactiveAccount() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Questo account è stato disattivato dall'amministratore.");
    }
}
