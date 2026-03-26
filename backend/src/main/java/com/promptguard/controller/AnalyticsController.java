package com.promptguard.controller;

import com.promptguard.repository.AuditLogRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/analytics")
@CrossOrigin(origins = "*")
public class AnalyticsController {

    private final AuditLogRepository repository;
    private final JdbcTemplate db;

    public AnalyticsController(AuditLogRepository repository, JdbcTemplate db) {
        this.repository = repository;
        this.db = db;
    }

    @GetMapping("/risk-summary")
    public ResponseEntity<Map<String, Object>> getRiskSummary() {
        Map<String, Object> summary = repository.getGlobalSummary();
        long total = ((Number) summary.getOrDefault("totalPrompts", 0L)).longValue();
        long blocked = ((Number) summary.getOrDefault("blockedPrompts", 0L)).longValue();
        summary.put("blockRate", total > 0 ? Math.round((double) blocked / total * 100) + "%" : "0%");
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/tokens")
    public ResponseEntity<Map<String, Object>> getTokenStats(@RequestParam(required = false) String userId) {
        boolean filterByUser = (userId != null && !userId.isBlank() && !"ALL".equals(userId));
        Map<String, Object> summary = filterByUser ? repository.getUserSummary(userId) : repository.getGlobalSummary();
        Map<String, Object> resp = new LinkedHashMap<>();
        
        resp.put("totalTokensUsed", summary.get("tokensUsed"));
        resp.put("totalCostUsed", summary.get("costUsed"));
        resp.put("totalTokensSaved", summary.get("tokensSaved"));
        resp.put("totalCostSaved", summary.get("costSaved"));
        
        resp.put("usedLogs", repository.findUsedTokensLogs(userId, 50));
        resp.put("savedLogs", repository.findSavedTokensLogs(userId, 50));
        
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/tool-usage")
    public ResponseEntity<?> getToolUsage() {
        return ResponseEntity.ok(repository.countByTool());
    }

    @GetMapping("/risk-breakdown")
    public ResponseEntity<?> getRiskBreakdown() {
        return ResponseEntity.ok(repository.countByRiskType());
    }

    @GetMapping("/top-users")
    public ResponseEntity<?> getTopUsers() {
        return ResponseEntity.ok(repository.topUsers());
    }

    @GetMapping("/recent-logs")
    public ResponseEntity<?> getRecentLogs(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String userId) {
        return ResponseEntity.ok(repository.findRecent(limit, userId));
    }

    @GetMapping("/my-prompts")
    public ResponseEntity<?> myPrompts(
            @RequestParam String userId,
            @RequestParam(defaultValue = "50") int limit) {
        if (userId == null || userId.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));

        List<Map<String, Object>> rows = db.queryForList(
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
                "FROM audit_logs WHERE user_id = ? ORDER BY created_at DESC LIMIT ?",
                userId, limit);
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/users")
    public ResponseEntity<?> getUserList() {
        List<Map<String, Object>> rows = db.queryForList(
                "SELECT user_id, display_name, role FROM users ORDER BY role DESC, user_id ASC");
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/my-stats")
    public ResponseEntity<?> getMyStats(@RequestParam String userId) {
        if (userId == null || userId.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));
        try {
            return ResponseEntity.ok(repository.findStatsByUser(userId));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("total", 0, "blocked", 0, "redacted", 0, "allowed", 0));
        }
    }
}
