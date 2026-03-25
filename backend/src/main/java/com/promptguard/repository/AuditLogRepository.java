package com.promptguard.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class AuditLogRepository {

    private final JdbcTemplate db;

    public AuditLogRepository(JdbcTemplate db) {
        this.db = db;
    }

    public long countTotal() {
        Long n = db.queryForObject("SELECT COUNT(*) FROM audit_logs", Long.class);
        return n != null ? n : 0L;
    }

    public long countByAction(String action) {
        Long n = db.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = ?", Long.class, action);
        return n != null ? n : 0L;
    }

    public List<Map<String, Object>> countByTool() {
        return db.queryForList(
                "SELECT tool, COUNT(*) AS count FROM audit_logs GROUP BY tool ORDER BY count DESC");
    }

    public List<Map<String, Object>> countByRiskType() {
        return db.queryForList(
                "SELECT highest_risk_type AS \"riskType\", COUNT(*) AS count " +
                        "FROM audit_logs GROUP BY highest_risk_type ORDER BY count DESC");
    }

    public List<Map<String, Object>> topUsers() {
        return db.queryForList(
                "SELECT user_id, COUNT(*) AS total, " +
                        "SUM(CASE WHEN action='BLOCK' THEN 1 ELSE 0 END) AS blocked " +
                        "FROM audit_logs GROUP BY user_id ORDER BY total DESC LIMIT 10");
    }

    public Map<String, Object> getGlobalSummary() {
        return db.queryForMap(
            "SELECT " +
            "  COUNT(*) AS \"totalPrompts\", " +
            "  COALESCE(SUM(CASE WHEN action='BLOCK' THEN 1 ELSE 0 END), 0) AS \"blockedPrompts\", " +
            "  COALESCE(SUM(CASE WHEN action='REDACT' THEN 1 ELSE 0 END), 0) AS \"redactedPrompts\", " +
            "  COALESCE(SUM(CASE WHEN action='ALERT' THEN 1 ELSE 0 END), 0) AS \"alertedPrompts\", " +
            "  COALESCE(SUM(CASE WHEN action='ALLOW' THEN 1 ELSE 0 END), 0) AS \"allowedPrompts\", " +
            "  COALESCE(SUM(tokens_used), 0) AS \"tokensUsed\", " +
            "  COALESCE(SUM(tokens_saved), 0) AS \"tokensSaved\", " +
            "  COALESCE(SUM(cost_used), 0.0) AS \"costUsed\", " +
            "  COALESCE(SUM(cost_saved), 0.0) AS \"costSaved\" " +
            "FROM audit_logs");
    }

    public List<Map<String, Object>> findRecent(int limit) {
        return db.queryForList(
                "SELECT id, " +
                "  user_id           AS \"userId\", " +
                "  tool, " +
                "  browser_name      AS \"browserName\", " +
                "  original_prompt   AS \"originalPrompt\", " +
                "  redacted_prompt   AS \"redactedPrompt\", " +
                "  highest_risk_type AS \"highestRiskType\", " +
                "  risk_score        AS \"riskScore\", " +
                "  risk_level        AS \"riskLevel\", " +
                "  action, " +
                "  action_reason     AS \"actionReason\", " +
                "  tokens_used       AS \"tokensUsed\", " +
                "  tokens_saved      AS \"tokensSaved\", " +
                "  cost_used         AS \"costUsed\", " +
                "  cost_saved        AS \"costSaved\", " +
                "  created_at        AS \"timestamp\" " +
                "FROM audit_logs ORDER BY created_at DESC LIMIT ?",
                limit);
    }

    public List<Map<String, Object>> findUsedTokensLogs(int limit) {
        return db.queryForList(
            "SELECT user_id AS \"userId\", tool, tokens_used AS \"tokens\", cost_used AS \"cost\", created_at AS \"timestamp\" " +
            "FROM audit_logs WHERE tokens_used > 0 ORDER BY created_at DESC LIMIT ?", limit);
    }

    public List<Map<String, Object>> findSavedTokensLogs(int limit) {
        return db.queryForList(
            "SELECT user_id AS \"userId\", tool, tokens_saved AS \"saved\", cost_saved AS \"value\", created_at AS \"timestamp\" " +
            "FROM audit_logs WHERE tokens_saved > 0 ORDER BY created_at DESC LIMIT ?", limit);
    }

    public Map<String, Object> findStatsByUser(String userId) {
        return db.queryForMap(
                "SELECT " +
                        "  COUNT(*) AS \"total\", " +
                        "  COALESCE(SUM(CASE WHEN action='BLOCK' THEN 1 ELSE 0 END), 0) AS \"blocked\", " +
                        "  COALESCE(SUM(CASE WHEN action='REDACT' THEN 1 ELSE 0 END), 0) AS \"redacted\", " +
                        "  COALESCE(SUM(CASE WHEN action='ALERT' THEN 1 ELSE 0 END), 0) AS \"alerted\", " +
                        "  COALESCE(SUM(CASE WHEN action='ALLOW' THEN 1 ELSE 0 END), 0) AS \"allowed\", " +
                        "  COALESCE(SUM(tokens_used), 0) AS \"tokensUsed\", " +
                        "  COALESCE(SUM(tokens_saved), 0) AS \"tokensSaved\", " +
                        "  COALESCE(SUM(cost_used), 0.0) AS \"costUsed\", " +
                        "  COALESCE(SUM(cost_saved), 0.0) AS \"costSaved\" " +
                        "FROM audit_logs WHERE user_id = ?",
                userId);
    }
}
