package it.coinquilini.dao;

import it.coinquilini.model.House;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

@Repository
public class HouseDAOJDBC implements HouseDAO {
    private final JdbcTemplate jdbc;

    public HouseDAOJDBC(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void save(House house) {
        String code = normalizeInviteCode(house.getInviteCode());
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO house (name, emoji, photo, invite_code, archived)
                    VALUES (?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setString(1, house.getName());
            statement.setString(2, house.getEmoji());
            statement.setString(3, house.getPhoto());
            statement.setString(4, code);
            statement.setBoolean(5, house.isArchived());
            return statement;
        }, keys);
        house.setId(keys.getKey().longValue());
        house.setInviteCode(code);
    }

    @Override
    public House findById(Long id) {
        return first(jdbc.query("SELECT * FROM house WHERE id = ?", this::mapHouse, id));
    }

    @Override
    public House findByInviteCode(String inviteCode) {
        return first(jdbc.query("SELECT * FROM house WHERE invite_code = ?",
                this::mapHouse, normalizeInviteCode(inviteCode)));
    }

    @Override
    public House findActiveByUserId(Long userId) {
        return first(jdbc.query("""
                SELECT h.* FROM house h
                JOIN membership m ON m.house_id = h.id
                WHERE m.user_id = ? AND m.active = TRUE
                """, this::mapHouse, userId));
    }

    private String normalizeInviteCode(String code) {
        return code == null ? null : code.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private House first(List<House> houses) {
        return houses.isEmpty() ? null : houses.getFirst();
    }

    private House mapHouse(ResultSet rs, int rowNum) throws SQLException {
        House house = new House();
        house.setId(rs.getLong("id"));
        house.setName(rs.getString("name"));
        house.setEmoji(rs.getString("emoji"));
        house.setPhoto(rs.getString("photo"));
        house.setInviteCode(rs.getString("invite_code"));
        house.setArchived(rs.getBoolean("archived"));
        return house;
    }
}
