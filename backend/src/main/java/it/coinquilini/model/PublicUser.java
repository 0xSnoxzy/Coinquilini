package it.coinquilini.model;

/** Vista JSON condivisa da autenticazione e snapshot, priva di credenziali. */
public record PublicUser(Long id, String name, String email, String role,
                         boolean active, String color, String photo) {
    public static PublicUser from(User user) {
        return new PublicUser(user.getId(), user.getName(), user.getEmail(),
                user.getRole(), user.isActive(), user.getColor(), user.getPhoto());
    }
}
