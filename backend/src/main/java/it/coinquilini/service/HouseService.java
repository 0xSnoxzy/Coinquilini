package it.coinquilini.service;

import it.coinquilini.dao.HouseDAO;
import it.coinquilini.dao.JdbcTransactions;
import it.coinquilini.dao.MembershipDAO;
import it.coinquilini.model.House;
import it.coinquilini.model.Membership;
import it.coinquilini.model.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Locale;

@Service
public class HouseService {
    private static final String INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();
    private final AuthService auth;
    private final HouseDAO houses;
    private final MembershipDAO memberships;
    private final JdbcTransactions transactions;

    public HouseService(AuthService auth, HouseDAO houses, MembershipDAO memberships,
                        JdbcTransactions transactions) {
        this.auth = auth;
        this.houses = houses;
        this.memberships = memberships;
        this.transactions = transactions;
    }

    public House create(HttpSession session, String name, String emoji) {
        if (name == null || name.isBlank() || name.trim().length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Il nome della casa è obbligatorio e può contenere al massimo 100 caratteri.");
        }
        String houseEmoji = emoji == null || emoji.isBlank() ? "🏠" : emoji.trim();
        if (houseEmoji.length() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Emoji della casa non valida.");
        }
        // Una collisione del codice richiede una nuova transazione: PostgreSQL
        // non permette altre query nella transazione che ha violato UNIQUE.
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                return transactions.execute(() -> {
                    User user = auth.currentUserForUpdate(session);
                    assertNoActiveHouse(user.getId());
                    House house = new House();
                    house.setName(name.trim());
                    house.setEmoji(houseEmoji);
                    house.setInviteCode(newInviteCode());
                    houses.save(house);
                    Membership owner = new Membership();
                    owner.setHouseId(house.getId());
                    owner.setUserId(user.getId());
                    owner.setRole("OWNER");
                    memberships.save(owner);
                    return house;
                });
            } catch (DuplicateKeyException ex) {
                // Il rollback rimuove anche un'eventuale casa appena inserita.
                if (attempt == 4) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Impossibile completare la creazione della casa. Riprova.");
                }
            }
        }
        throw new IllegalStateException("Tentativi di creazione esauriti");
    }

    public House join(HttpSession session, String code) {
        String normalized = code == null ? "" : code.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9]{6}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Inserisci un codice invito di 6 caratteri alfanumerici.");
        }
        try {
            return transactions.execute(() -> {
                User user = auth.currentUserForUpdate(session);
                assertNoActiveHouse(user.getId());
                House house = houses.findByInviteCodeForUpdate(normalized);
                if (house == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Codice invito non valido.");
                }
                if (house.isArchived()) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Non puoi entrare in una casa archiviata.");
                }
                Membership previous = memberships.findByHouseAndUser(house.getId(), user.getId());
                if (previous != null) {
                    // Un rientro preserva la riga storica, senza recuperare ruoli OWNER precedenti.
                    memberships.reactivateAsMember(previous.getId());
                } else {
                    Membership member = new Membership();
                    member.setHouseId(house.getId());
                    member.setUserId(user.getId());
                    memberships.save(member);
                }
                return house;
            });
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Appartieni già a una casa attiva.");
        }
    }

    private void assertNoActiveHouse(Long userId) {
        if (memberships.findActiveByUserId(userId) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Appartieni già a una casa attiva.");
        }
    }

    private String newInviteCode() {
        StringBuilder code = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            code.append(INVITE_ALPHABET.charAt(random.nextInt(INVITE_ALPHABET.length())));
        }
        return code.toString();
    }
}
