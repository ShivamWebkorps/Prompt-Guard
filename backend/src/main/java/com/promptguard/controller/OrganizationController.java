package com.promptguard.controller;

import com.promptguard.model.Organization;
import com.promptguard.repository.OrganizationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/organizations")
@CrossOrigin(origins = "*")
public class OrganizationController {

    private final OrganizationRepository orgRepository;

    public OrganizationController(OrganizationRepository orgRepository) {
        this.orgRepository = orgRepository;
    }

    /** GET /api/v1/organizations — list all orgs */
    @GetMapping
    public ResponseEntity<List<Organization>> listOrgs() {
        return ResponseEntity.ok(orgRepository.findAll());
    }

    /**
     * POST /api/v1/organizations
     * POST /api/v1/organizations
     * Body: { "orgId": 103, "orgName": "Finance" }
     */
    @PostMapping
    public ResponseEntity<?> createOrg(@RequestBody Map<String, Object> body) {
        Object orgIdObj = body.get("orgId");
        String orgName  = (String) body.get("orgName");

        if (orgIdObj == null || orgName == null || orgName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "orgId and orgName are required"));
        }
        int orgId = Integer.parseInt(orgIdObj.toString());
        Organization org = new Organization(orgId, orgName);
        orgRepository.save(org);
        return ResponseEntity.ok(Map.of("status", "saved", "orgId", orgId, "orgName", orgName));
    }
}
