package it.coinquilini.controller;

import it.coinquilini.dao.UserDAO;
import it.coinquilini.model.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller REST per Autenticazione e Profilo Utente (Modulo 1).
 * Riferimento slide:
 * - "REST", slide 8 e 10 (Verbi HTTP POST, PUT, GET e formato JSON).
 * - "Architecture of a Web Application", slide 25-31 (Session management con Cookie e Sessione HTTP).
 * - "External API Concepts", slide 31 (CORS con Access-Control-Allow-Credentials: true).
 */
@RestController
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:3000"}, allowCredentials = "true")
public class AuthController {

    private final UserDAO userDAO;

    public AuthController(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    /**
     * Endpoint per la registrazione di un nuovo utente.
     * POST /api/auth/register
     */
    @PostMapping("/api/auth/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body, HttpSession session) {
        String name = body.get("name");
        String email = body.get("email");
        String password = body.get("password");

        // Controllo validità campi base
        if (name == null || name.isBlank() || email == null || email.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tutti i campi sono obbligatori."));
        }

        // Verifica se l'email esiste già
        if (userDAO.findByEmail(email) != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email già registrata con un altro account."));
        }

        // Creazione dell'utente (slide: DTO con campi inizializzati)
        User newUser = new User();
        newUser.setName(name.trim());
        newUser.setEmail(email.trim().toLowerCase());
        newUser.setPassword(password); // Salvataggio diretto della password
        newUser.setRole("USER");
        newUser.setActive(true);
        newUser.setColor("#2563eb");
        newUser.setPhoto("");

        userDAO.save(newUser);

        // Memorizza l'utente nella sessione HTTP (slide: Session management)
        session.setAttribute("user", newUser);

        return ResponseEntity.status(HttpStatus.CREATED).body(sanitizeUser(newUser));
    }

    /**
     * Endpoint per il login con credenziali.
     * POST /api/auth/login
     */
    @PostMapping("/api/auth/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpSession session) {
        String email = body.get("email");
        String password = body.get("password");

        if (email == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Inserisci email e password."));
        }

        User user = userDAO.findByEmailAndPassword(email.trim(), password);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Credenziali non valide."));
        }

        if (!user.isActive()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Questo account è stato disattivato dall'amministratore."));
        }

        // Salvataggio dell'utente in sessione
        session.setAttribute("user", user);

        return ResponseEntity.ok(sanitizeUser(user));
    }

    /**
     * Endpoint per verificare se l'utente è attualmente autenticato.
     * GET /api/auth/me
     */
    @GetMapping("/api/auth/me")
    public ResponseEntity<?> me(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Nessuna sessione attiva."));
        }
        // Ricarica dal DB per avere sempre i dati aggiornati
        User freshUser = userDAO.findById(user.getId());
        return ResponseEntity.ok(sanitizeUser(freshUser != null ? freshUser : user));
    }

    /**
     * Endpoint per il logout (invalida la sessione server).
     * POST /api/auth/logout
     */
    @PostMapping("/api/auth/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "Disconnessione effettuata con successo."));
    }

    /**
     * Endpoint per aggiornare il profilo personale (nome, colore tema, foto).
     * PUT /api/profile/save
     */
    @PutMapping("/api/profile/save")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, Object> body, HttpSession session) {
        User loggedUser = (User) session.getAttribute("user");
        if (loggedUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Devi effettuare il login."));
        }

        User user = userDAO.findById(loggedUser.getId());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Utente non trovato."));
        }

        if (body.containsKey("name") && body.get("name") != null) {
            user.setName(body.get("name").toString().trim());
        }
        if (body.containsKey("color") && body.get("color") != null) {
            user.setColor(body.get("color").toString().trim());
        }
        if (body.containsKey("photo") && body.get("photo") != null) {
            user.setPhoto(body.get("photo").toString());
        }

        userDAO.update(user);
        session.setAttribute("user", user);

        return ResponseEntity.ok(sanitizeUser(user));
    }

    /**
     * Rimuove la password prima di inviare i dati al frontend Angular (per riservatezza).
     */
    private Map<String, Object> sanitizeUser(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("name", user.getName());
        map.put("email", user.getEmail());
        map.put("role", user.getRole());
        map.put("active", user.isActive());
        map.put("color", user.getColor());
        map.put("photo", user.getPhoto());
        return map;
    }
}
