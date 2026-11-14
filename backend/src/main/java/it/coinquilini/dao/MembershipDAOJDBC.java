package it.coinquilini.dao;

import it.coinquilini.model.Member;
import it.coinquilini.model.Membership;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class MembershipDAOJDBC implements MembershipDAO {
    private final JdbcTemplate jdbc;

    public MembershipDAOJDBC(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void save(Membership membership) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO membership (house_id, user_id, role, active)
                    VALUES (?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, membership.getHouseId());
            statement.setLong(2, membership.getUserId());
            statement.setString(3, membership.getRole());
            statement.setBoolean(4, membership.isActive());
            return statement;
        }, keys);
        membership.setId(keys.getKey().longValue());
    }

    @Override
    public Membership findActiveByUserId(Long userId) {
        List<Membership> memberships = jdbc.query("""
                SELECT * FROM membership WHERE user_id = ? AND active = TRUE
                """, this::mapMembership, userId);
        return memberships.isEmpty() ? null : memberships.getFirst();
    }

    @Override
    public Membership findByHouseAndUser(Long houseId, Long userId) {
        List<Membership> memberships = jdbc.query(
                "SELECT * FROM membership WHERE house_id = ? AND user_id = ?",
                this::mapMembership, houseId, userId);
        return memberships.isEmpty() ? null : memberships.getFirst();
    }

    @Override
    public void reactivateAsMember(Long membershipId) {
        jdbc.update("UPDATE membership SET active = TRUE, role = 'MEMBER' WHERE id = ?", membershipId);
    }

    @Override
    public List<Member> findMembersByHouseId(Long houseId) {
        return jdbc.query("""
                SELECT u.id, u.name, u.email, u.role, u.active, u.color, u.photo,
                       m.id AS membership_id, m.role AS membership_role, m.active AS member_active
                FROM membership m JOIN app_user u ON u.id = m.user_id
                WHERE m.house_id = ? ORDER BY u.id
                """, (rs, rowNum) -> new Member(
                rs.getLong("id"), rs.getString("name"), rs.getString("email"),
                rs.getString("role"), rs.getBoolean("active"), rs.getString("color"),
                rs.getString("photo"), rs.getLong("membership_id"),
                rs.getString("membership_role"), rs.getBoolean("member_active")), houseId);
    }

    private Membership mapMembership(ResultSet rs, int rowNum) throws SQLException {
        Membership membership = new Membership();
        membership.setId(rs.getLong("id"));
        membership.setHouseId(rs.getLong("house_id"));
        membership.setUserId(rs.getLong("user_id"));
        membership.setRole(rs.getString("role"));
        membership.setActive(rs.getBoolean("active"));
        return membership;
    }
}
