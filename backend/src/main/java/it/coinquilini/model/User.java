package it.coinquilini.model;

/**
 * Data Transfer Object (DTO) per l'entità Utente.
 * Riferimento slide: "Persistence - JDBC - Pattern DAO", slide 10 e 14 (classe Student).
 * Contiene i campi privati della tabella del DB con relativi metodi getter e setter.
 */
public class User {

    private Long id;
    private String name;
    private String email;
    private String password;
    private String role;
    private boolean active;
    private String color;
    private String photo;

    // Costruttore vuoto (richiesto dallo standard JavaBean / DTO)
    public User() {
    }

    // Costruttore con parametri
    public User(Long id, String name, String email, String password, String role, boolean active, String color, String photo) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = role;
        this.active = active;
        this.color = color;
        this.photo = photo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getPhoto() {
        return photo;
    }

    public void setPhoto(String photo) {
        this.photo = photo;
    }
}
