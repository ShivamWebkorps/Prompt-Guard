package com.promptguard.model;

/**
 * Plain POJO — NO JPA annotations.
 * Project uses JdbcTemplate only, not Spring Data JPA.
 */
public class User {

    private String  userId;
    private String  displayName;
    private String  role;    // "ADMIN" or "USER"
    private Integer orgId;   // FK → organizations.org_id (nullable for admin)
    private String  orgName; // Joined from organizations — read-only convenience field

    public User() {}

    public User(String userId, String displayName, String role) {
        this.userId      = userId;
        this.displayName = displayName;
        this.role        = role;
    }

    public User(String userId, String displayName, String role, Integer orgId) {
        this.userId      = userId;
        this.displayName = displayName;
        this.role        = role;
        this.orgId       = orgId;
    }

    public String getUserId()          { return userId; }
    public void setUserId(String v)    { this.userId = v; }

    public String getDisplayName()          { return displayName; }
    public void setDisplayName(String v)    { this.displayName = v; }

    public String getRole()          { return role; }
    public void setRole(String v)    { this.role = v; }

    public Integer getOrgId()           { return orgId; }
    public void setOrgId(Integer v)     { this.orgId = v; }

    public String getOrgName()          { return orgName; }
    public void setOrgName(String v)    { this.orgName = v; }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(this.role);
    }
}
