package com.promptguard.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * DatabaseInitializer — runs on every startup.
 *
 * Execution order (all idempotent):
 *   1. Ensure organizations table exists (CREATE IF NOT EXISTS)
 *   2. Ensure users.org_id column exists (ALTER IF NOT EXISTS)
 *   3. Seed organizations  101=Telecomm  102=Software
 *   4. Seed users with org assignments
 *   5. Migrate old slug-based policy rows → org_id keys
 *   6. Seed keyword policies (user_id = org_id string "101"/"102")
 */
@Component
public class DatabaseInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);
    private final JdbcTemplate db;

    public DatabaseInitializer(JdbcTemplate db) {
        this.db = db;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== PromptGuard DB Init ===");

        // ── Step 1: Ensure organizations table exists ─────────────────────────
        // We do this here (not just in schema.sql) because schema.sql's
        // CREATE IF NOT EXISTS won't modify an existing stale table.
        try {
            db.execute(
                "CREATE TABLE IF NOT EXISTS organizations (" +
                "  org_id   INTEGER      PRIMARY KEY," +
                "  org_name VARCHAR(255) NOT NULL UNIQUE" +
                ")"
            );
            log.info("organizations table ready");
        } catch (Exception e) {
            log.warn("organizations table create: {}", e.getMessage());
        }

        // ── Step 2: Migration — browser_name in audit_logs ────────────────────
        try {
            Integer ex = db.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_name='audit_logs' AND column_name='browser_name'",
                Integer.class);
            if (ex == null || ex == 0) {
                db.execute("ALTER TABLE audit_logs ADD COLUMN browser_name VARCHAR(50) DEFAULT 'Unknown'");
                log.info("Migration: added browser_name column");
            }
        } catch (Exception e) {
            log.warn("browser_name migration: {}", e.getMessage());
        }

        // ── Step 3: Migration — org_id in users ───────────────────────────────
        try {
            Integer ex = db.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_name='users' AND column_name='org_id'",
                Integer.class);
            if (ex == null || ex == 0) {
                db.execute(
                    "ALTER TABLE users ADD COLUMN org_id INTEGER " +
                    "REFERENCES organizations(org_id) ON DELETE SET NULL"
                );
                log.info("Migration: added org_id column to users");
            }
        } catch (Exception e) {
            log.warn("org_id migration: {}", e.getMessage());
        }

        // ── Migration: token/cost columns ────────────────────────────────────
        try {
            String[] cols = {"tokens_used", "tokens_saved", "cost_used", "cost_saved"};
            String[] types = {"INTEGER DEFAULT 0", "INTEGER DEFAULT 0", "DOUBLE PRECISION DEFAULT 0.0", "DOUBLE PRECISION DEFAULT 0.0"};
            for (int i=0; i<cols.length; i++) {
                String col = cols[i];
                Integer ex = db.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                    "WHERE table_name='audit_logs' AND column_name=?",
                    Integer.class, col);
                if (ex == null || ex == 0) {
                    db.execute("ALTER TABLE audit_logs ADD COLUMN " + col + " " + types[i]);
                }
            }
        } catch (Exception e) {}

        // ── Step 4: Seed organizations (FK parent — must come before users) ───
        insertOrg(101, "Telecomm");
        insertOrg(102, "Software");

        // ── Step 5: Seed users ────────────────────────────────────────────────
        // Standardizing IDs: rohan, kushal, shivam, raj, admin
        // Proactive cleanup of legacy -user IDs
        try {
            db.update("DELETE FROM users WHERE user_id LIKE '%-user'");
            log.info("Cleanup: removed legacy -user accounts");
        } catch (Exception e) {}

        insertUser("admin",  "Admin",  "ADMIN", null);
        insertUser("rohan",  "Rohan",  "USER",  102);
        insertUser("kushal", "Kushal", "USER",  101);
        insertUser("shivam", "Shivam", "USER",  102);
        insertUser("raj",    "Raj",    "USER",  101);

        // Update display names if needed (though insertUser handles it)
        try {
            db.update("UPDATE users SET display_name = 'Software' WHERE user_id = 'rohan'");
            db.update("UPDATE users SET display_name = 'Telecomm' WHERE user_id = 'kushal'");
        } catch (Exception e) {
            log.warn("User display name update: {}", e.getMessage());
        }

        // Patch org_id for users already in DB without it
        try {
            db.update("UPDATE users SET org_id = 102 WHERE user_id = 'rohan'  AND org_id IS NULL");
            db.update("UPDATE users SET org_id = 101 WHERE user_id = 'kushal' AND org_id IS NULL");
        } catch (Exception e) {
            log.warn("User org_id patch: {}", e.getMessage());
        }

        // ── Step 6: Migrate old slug-based policy rows → numeric org_id ───────
        try {
            int r = db.update("UPDATE user_keyword_policies SET user_id='102' WHERE user_id='rohan'");
            int k = db.update("UPDATE user_keyword_policies SET user_id='101' WHERE user_id='kushal'");
            if (r > 0) log.info("Migration: {} rohan policy rows → org 102", r);
            if (k > 0) log.info("Migration: {} kushal policy rows → org 101", k);
        } catch (Exception e) {
            log.warn("Policy slug→orgId migration: {}", e.getMessage());
        }

        // ── Step 7a: Remove wildcard policies (they block ALL prompts) ────────
        try {
            int removed = db.update(
                "DELETE FROM user_keyword_policies WHERE keyword_list = '*'"
            );
            if (removed > 0) log.info("Cleanup: removed {} wildcard (*) policies", removed);
        } catch (Exception e) {
            log.warn("Wildcard policy cleanup failed: {}", e.getMessage());
        }

        // ── Step 7b: Seed keyword policies ────────────────────────────────────
        seedPolicies();

        // ── Summary ───────────────────────────────────────────────────────────
        try {
            Long u = db.queryForObject("SELECT COUNT(*) FROM users", Long.class);
            Long o = db.queryForObject("SELECT COUNT(*) FROM organizations", Long.class);
            Long p = db.queryForObject("SELECT COUNT(*) FROM user_keyword_policies", Long.class);
            log.info("DB ready — orgs:{}, users:{}, policies:{}", o, u, p);
        } catch (Exception e) {
            log.warn("Summary count failed: {}", e.getMessage());
        }
        // ── Migration: rename critial_col to critical_col ──────────────────
        try {
            Integer ex = db.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_name='user_keyword_policies' AND column_name='critial_col'",
                Integer.class);
            if (ex != null && ex > 0) {
                db.execute("ALTER TABLE user_keyword_policies RENAME COLUMN critial_col TO critical_col");
                log.info("Migration: renamed critial_col to critical_col");
            }
        } catch (Exception e) {}

        // ── Migration: remove exact duplicate policy rows ──────────────────
        try {
            int deleted = db.update(
                "DELETE FROM user_keyword_policies a USING user_keyword_policies b " +
                "WHERE a.id > b.id " +
                "AND a.user_id = b.user_id AND a.sub_user = b.sub_user " +
                "AND a.keyword_list = b.keyword_list AND a.block_col = b.block_col " +
                "AND a.critical_col = b.critical_col AND a.redacted_col = b.redacted_col " +
                "AND a.allow_col = b.allow_col"
            );
            if (deleted > 0) log.info("Migration: removed {} duplicate policy rows", deleted);
        } catch (Exception e) {
            log.warn("Duplicate policy cleanup failed: {}", e.getMessage());
        }

        log.info("=== DB Init Done ===");
    }

    // ── Seed helpers ──────────────────────────────────────────────────────────

    private void seedPolicies() {
        // ── Org 102 = Software (rohan-user) ───────────────────────────────────
        upsert("102", "user1", "confidential,secret,internal,restricted,private",
                false, false, false, true,  "Software-user1 Confidential BLOCK");
        upsert("102", "user2", "password,token,api_key,access_key,private_key",
                false, true,  false, false, "Software-user2 Credentials REDACT");
        upsert("102", "user1", "breach,attack,exploit,vulnerability",
                false, false, true,  false, "Software-user1 Security CRITICAL");
        upsert("102", "user1", "acess",
                false, true,  false, false, "Software-user1 Access REDACT");

        // ── Org 101 = Telecomm (kushal-user) ──────────────────────────────────
        upsert("101", "user1", "imei,sim_card,network_key,msisdn,iccid",
                false, false, false, true,  "Telecomm-user1 Telecom identifiers BLOCK");
        upsert("101", "user1", "subscriber_id,call_record,location_data,billing_info",
                false, true,  false, false, "Telecomm-user1 Subscriber PII REDACT");
        upsert("101", "user1", "cricket,match_fix,betting_tip,gambling,odds",
                false, false, false, true,  "Telecomm-user1 Sport terms BLOCK");
        upsert("101", "user1", "trai_violation,number_portability,roaming_fraud",
                false, false, true,  false, "Telecomm-user1 Compliance CRITICAL");
        upsert("101", "user1", "sim_card,msisdn,iccid",
                false, false, false, true,  "Telecomm-user1 SIM identifiers BLOCK");
        upsert("101", "user2", "number_portability,roaming_fraud",
                false, false, true,  false, "Telecomm-user2 Roaming CRITICAL");

    }

    private void upsert(String userId, String subUser, String words,
            boolean allow, boolean redact, boolean critical, boolean block, String prompt) {
        try {
            Integer ex = db.queryForObject(
                "SELECT COUNT(*) FROM user_keyword_policies " +
                "WHERE user_id=? AND sub_user=? AND keyword_list=?",
                Integer.class, userId, subUser, words);
            if (ex == null || ex == 0) {
                db.update(
                    "INSERT INTO user_keyword_policies" +
                    "(user_id,sub_user,keyword_list,allow_col,redacted_col,critical_col,block_col,prompt_col) " +
                    "VALUES(?,?,?,?,?,?,?,?)",
                    userId, subUser, words, allow, redact, critical, block, prompt);
                log.info("Seeded policy {}:{} [{}]", userId, subUser,
                    words.substring(0, Math.min(30, words.length())));
            }
        } catch (Exception e) {
            log.warn("upsert policy {}:{} failed: {}", userId, subUser, e.getMessage());
        }
    }

    private void insertUser(String userId, String name, String role, Integer orgId) {
        try {
            Integer ex = db.queryForObject(
                "SELECT COUNT(*) FROM users WHERE user_id=?", Integer.class, userId);
            if (ex == null || ex == 0) {
                db.update(
                    "INSERT INTO users(user_id, display_name, role, org_id) VALUES(?,?,?,?)",
                    userId, name, role, orgId);
                log.info("Seeded user: {} (org={})", userId, orgId);
            }
        } catch (Exception e) {
            log.warn("insertUser {} failed: {}", userId, e.getMessage());
        }
    }

    private void insertOrg(int orgId, String orgName) {
        try {
            Integer ex = db.queryForObject(
                "SELECT COUNT(*) FROM organizations WHERE org_id=?", Integer.class, orgId);
            if (ex == null || ex == 0) {
                db.update("INSERT INTO organizations(org_id, org_name) VALUES(?,?)", orgId, orgName);
                log.info("Seeded org: {} - {}", orgId, orgName);
            }
        } catch (Exception e) {
            log.warn("insertOrg {} failed: {}", orgId, e.getMessage());
        }
    }
}
