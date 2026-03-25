package com.promptguard.repository;

import com.promptguard.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Pure JdbcTemplate — NO Spring Data JPA dependency needed.
 */
@Repository
public class UserRepository {

    private final JdbcTemplate db;

    public UserRepository(JdbcTemplate db) {
        this.db = db;
    }

    private static final RowMapper<User> USER_MAPPER = (rs, rowNum) -> {
        User u = new User();
        u.setUserId(rs.getString("user_id"));
        u.setDisplayName(rs.getString("display_name"));
        u.setRole(rs.getString("role"));
        int orgId = rs.getInt("org_id");
        if (!rs.wasNull()) u.setOrgId(orgId);
        u.setOrgName(rs.getString("org_name")); // may be null for admin
        return u;
    };

    private static final String BASE_QUERY =
            "SELECT u.user_id, u.display_name, u.role, u.org_id, o.org_name " +
            "FROM users u LEFT JOIN organizations o ON u.org_id = o.org_id ";

    /** Find a single user by their ID */
    public Optional<User> findByUserId(String userId) {
        List<User> list = db.query(BASE_QUERY + "WHERE u.user_id = ?", USER_MAPPER, userId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /** List all users — used by dashboard dropdown */
    public List<User> findAll() {
        return db.query(BASE_QUERY + "ORDER BY u.role DESC, u.user_id ASC", USER_MAPPER);
    }

    /** Upsert: update if exists, insert if not */
    public void save(User user) {
        int updated = db.update(
                "UPDATE users SET display_name = ?, role = ?, org_id = ? WHERE user_id = ?",
                user.getDisplayName(), user.getRole(), user.getOrgId(), user.getUserId());
        if (updated == 0) {
            db.update(
                    "INSERT INTO users (user_id, display_name, role, org_id) VALUES (?, ?, ?, ?)",
                    user.getUserId(), user.getDisplayName(), user.getRole(), user.getOrgId());
        }
    }
}
