package com.promptguard.repository;

import com.promptguard.model.UserKeywordPolicy;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class UserPolicyRepository {

    private final JdbcTemplate db;

    public UserPolicyRepository(JdbcTemplate db) {
        this.db = db;
    }

    private static final String SELECT_COLS =
        "SELECT id, user_id AS userId, sub_user AS subUser, " +
        "keyword_list AS keywordList, " +
        "allow_col AS allowCol, redacted_col AS redactedCol, " +
        "critical_col AS criticalCol, block_col AS blockCol, " +
        "prompt_col AS promptCol " +
        "FROM user_keyword_policies ";

    /** Used by UserKeywordDetector — fetch policies for this sub-user OR org-wide '*' policies */
    public List<UserKeywordPolicy> findPolicies(String userId, String subUser) {
        return db.query(
            SELECT_COLS + "WHERE user_id = ? AND (sub_user = ? OR sub_user = '*') ORDER BY id",
            new BeanPropertyRowMapper<>(UserKeywordPolicy.class),
            userId, subUser);
    }

    /** Used by PolicyController — fetch all policies for one org */
    public List<UserKeywordPolicy> findByUserId(String userId) {
        return db.query(
            SELECT_COLS + "WHERE user_id = ? ORDER BY sub_user, id",
            new BeanPropertyRowMapper<>(UserKeywordPolicy.class),
            userId);
    }

    /** Used by PolicyController — fetch ALL policies (admin view) */
    public List<UserKeywordPolicy> findAll() {
        return db.query(
            SELECT_COLS + "ORDER BY user_id, sub_user, id",
            new BeanPropertyRowMapper<>(UserKeywordPolicy.class));
    }

    /** Used by PolicyController — insert a new policy row */
    public void insert(String userId, String subUser, String keywordList,
                       boolean blockCol, boolean criticalCol,
                       boolean redactedCol, boolean allowCol, String promptCol) {
        db.update(
            "INSERT INTO user_keyword_policies " +
            "(user_id, sub_user, keyword_list, block_col, critical_col, redacted_col, allow_col, prompt_col) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            userId, subUser, keywordList,
            blockCol, criticalCol, redactedCol, allowCol, promptCol);
    }

    /** Used by PolicyController — delete a policy row by id */
    public void deleteById(int id) {
        db.update("DELETE FROM user_keyword_policies WHERE id = ?", id);
    }
}
