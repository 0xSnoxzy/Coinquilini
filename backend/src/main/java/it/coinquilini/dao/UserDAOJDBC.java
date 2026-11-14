package it.coinquilini.dao;

import it.coinquilini.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Implementazione JDBC del pattern DAO per la tabella app_user.
 * Riferimento slide: "Persistence - JDBC - Pattern DAO", slide 12 (ProductDAOImpl), 16 (findAll) e 18 (executeUpdate).
 * Usa Connection dal DataSource e PreparedStatement parametrizzati contro SQL Injection.
 */
@Repository
public class UserDAOJDBC implements UserDAO {

    private final JdbcTemplate jdbc;

    public UserDAOJDBC(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public User findById(Long id) {
        String query = "SELECT * FROM app_user WHERE id = ?";
        return first(jdbc.query(query, this::mapUser, id));
    }

    @Override
    public User findByEmail(String email) {
        String query = "SELECT * FROM app_user WHERE LOWER(TRIM(email)) = ?";
        return first(jdbc.query(query, this::mapUser, email));
    }

    @Override
    public void save(User user) {
        String query = "INSERT INTO app_user (name, email, password, role, active, color, photo) VALUES (?, ?, ?, ?, ?, ?, ?)";
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(query, new String[]{"id"});
            statement.setString(1, user.getName());
            statement.setString(2, user.getEmail());
            statement.setString(3, user.getPassword());
            statement.setString(4, user.getRole() != null ? user.getRole() : "USER");
            statement.setBoolean(5, user.isActive());
            statement.setString(6, user.getColor() != null ? user.getColor() : "#2563eb");
            statement.setString(7, user.getPhoto() != null ? user.getPhoto() : "");
            return statement;
        }, keys);
        user.setId(keys.getKey().longValue());
    }

    @Override
    public void update(User user) {
        String query = "UPDATE app_user SET name = ?, color = ?, photo = ? WHERE id = ?";
        jdbc.update(query, user.getName(), user.getColor(), user.getPhoto(), user.getId());
    }

    @Override
    public List<User> findAll() {
        String query = "SELECT * FROM app_user";
        return jdbc.query(query, this::mapUser);
    }

    private User first(List<User> users) {
        return users.isEmpty() ? null : users.getFirst();
    }

    /**
     * Metodo helper privato per estrarre l'oggetto User dal ResultSet.
     * Riferimento slide: slide 16 (istanziazione di Student a partire dai campi del ResultSet).
     */
    private User mapUser(ResultSet rs, int rowNum) throws SQLException {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setName(rs.getString("name"));
        user.setEmail(rs.getString("email"));
        user.setPassword(rs.getString("password"));
        user.setRole(rs.getString("role"));
        user.setActive(rs.getBoolean("active"));
        user.setColor(rs.getString("color"));
        user.setPhoto(rs.getString("photo"));
        return user;
    }
}
