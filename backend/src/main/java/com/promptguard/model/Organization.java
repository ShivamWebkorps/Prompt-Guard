package com.promptguard.model;

/**
 * Plain POJO for the organizations table.
 * org_id : 101 = Telecomm, 102 = Software
 */
public class Organization {

    private int    orgId;
    private String orgName;

    public Organization() {}

    public Organization(int orgId, String orgName) {
        this.orgId   = orgId;
        this.orgName = orgName;
    }

    public int getOrgId()              { return orgId; }
    public void setOrgId(int orgId)    { this.orgId = orgId; }

    public String getOrgName()                 { return orgName; }
    public void setOrgName(String orgName)     { this.orgName = orgName; }
}
