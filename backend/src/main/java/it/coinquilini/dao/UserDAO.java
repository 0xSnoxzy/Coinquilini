package it.coinquilini.dao;

import it.coinquilini.model.User;
import java.util.List;

/**
 * Interfaccia DAO per l'accesso ai dati della tabella app_user.
 * Riferimento slide: "Persistence - JDBC - Pattern DAO", slide 10 e 11 (interfaccia ProductDAO).
 * Definisce i metodi CRUD per separare la logica applicativa dall'accesso al database.
 */
public interface UserDAO {

    User findById(Long id);

    User findByEmail(String email);

    void save(User user);

    void update(User user);

    List<User> findAll();
}
