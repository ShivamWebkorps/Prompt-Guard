package com.promptguard.repository;

import com.promptguard.model.Organization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class OrganizationRepository {

    private final JdbcTemplate db;

    public OrganizationRepository(JdbcTemplate db) {
        this.db = db;
    }

    private static final RowMapper<Organization> ORG_MAPPER = (rs, rowNum) -> {
        Organization o = new Organization();
        o.setOrgId(rs.getInt("org_id"));
        o.setOrgName(rs.getString("org_name"));
        return o;
    };

    public List<Organization> findAll() {
        return db.query("SELECT org_id, org_name FROM organizations ORDER BY org_id", ORG_MAPPER);
    }

    public Optional<Organization> findById(int orgId) {
        List<Organization> list = db.query(
                "SELECT org_id, org_name FROM organizations WHERE org_id = ?", ORG_MAPPER, orgId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /** Upsert — idempotent seed */
    public void save(Organization org) {
        int updated = db.update(
                "UPDATE organizations SET org_name = ? WHERE org_id = ?",
                org.getOrgName(), org.getOrgId());
        if (updated == 0) {
            db.update("INSERT INTO organizations (org_id, org_name) VALUES (?, ?)",
                    org.getOrgId(), org.getOrgName());
        }
    }
}
