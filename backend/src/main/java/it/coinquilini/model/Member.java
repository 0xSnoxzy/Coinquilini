package it.coinquilini.model;

/** Vista di un membro compatibile con models.ts; non contiene credenziali. */
public record Member(
        Long id, String name, String email, String role, boolean active,
        String color, String photo, Long membershipId,
        String membershipRole, boolean memberActive) {
}
