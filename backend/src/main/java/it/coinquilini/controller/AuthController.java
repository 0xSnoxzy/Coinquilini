package it.coinquilini.controller;

import it.coinquilini.model.User;
import it.coinquilini.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.Map;

/** Endpoint di autenticazione e profilo compatibili con lo Store Angular. */
@RestController
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:3000"}, allowCredentials = "true")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/api/auth/register")
    public ResponseEntity<UserResponse> register(@RequestBody AuthRequest body, HttpServletRequest request) {
        User user = authService.register(body.name(), body.email(), body.password());
        startSession(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @PostMapping("/api/auth/login")
    public UserResponse login(@RequestBody AuthRequest body, HttpServletRequest request) {
        User user = authService.login(body.email(), body.password());
        startSession(request, user);
        return UserResponse.from(user);
    }

    @GetMapping("/api/auth/me")
    public UserResponse me(HttpServletRequest request) {
        return UserResponse.from(authService.currentUser(request.getSession(false)));
    }

    @PostMapping("/api/auth/logout")
    public Map<String, String> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return Map.of("message", "Disconnessione effettuata con successo.");
    }

    @PutMapping("/api/profile/save")
    public UserResponse updateProfile(@RequestBody ProfileRequest body, HttpServletRequest request) {
        return UserResponse.from(authService.updateProfile(
                request.getSession(false), body.name(), body.color(), body.photo()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleError(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", ex.getReason()));
    }

    private void startSession(HttpServletRequest request, User user) {
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        // Rimuove anche eventuali oggetti User di vecchie sessioni o accessi precedenti.
        Collections.list(session.getAttributeNames()).forEach(session::removeAttribute);
        session.setAttribute(AuthService.SESSION_USER_ID, user.getId());
    }

    public record AuthRequest(String name, String email, String password) {}

    public record ProfileRequest(String name, String color, String photo) {}

    /** Whitelist esplicita: le risposte non includono mai password o hash. */
    public record UserResponse(Long id, String name, String email, String role,
                               boolean active, String color, String photo) {
        static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getName(), user.getEmail(),
                    user.getRole(), user.isActive(), user.getColor(), user.getPhoto());
        }
    }
}
