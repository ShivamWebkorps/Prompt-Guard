package com.promptguard.controller;

import com.promptguard.model.User;
import com.promptguard.model.UserKeywordPolicy;
import com.promptguard.repository.UserPolicyRepository;
import com.promptguard.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/policies")
@CrossOrigin(origins = "*")
public class PolicyController {

    private final UserPolicyRepository repository;
    private final UserRepository       userRepository;

    public PolicyController(UserPolicyRepository repository, UserRepository userRepository) {
        this.repository     = repository;
        this.userRepository = userRepository;
    }

    /**
     * GET /api/v1/policies
     * Returns ALL rows from user_keyword_policies — admin use only.
     * Optional filter: ?userId=rohan-user  (resolves to org_id automatically)
     */
    @GetMapping
    public ResponseEntity<List<UserKeywordPolicy>> getAllPolicies(
            @RequestParam(required = false) String userId) {
        if (userId != null && !userId.isBlank()) {
            // Try to resolve userId → org_id
            String orgKey = resolveOrgKey(userId);
            return ResponseEntity.ok(repository.findByUserId(orgKey));
        }
        return ResponseEntity.ok(repository.findAll());
    }

    /**
     * POST /api/v1/policies
     * Body: { "userId": "102", "subUser": "user1", "keywordList": "confidential,secret", ... }
     * userId can be org_id ("101"/"102") OR a user slug ("rohan-user") — both are handled.
     */
    @PostMapping
    public ResponseEntity<?> createPolicy(@RequestBody Map<String, Object> body) {
        String userId  = (String) body.get("userId");
        String subUser = (String) body.get("subUser");
        String kwList  = (String) body.get("keywordList");

        if (userId == null || userId.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));
        if (subUser == null || subUser.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "subUser is required"));
        if (kwList == null || kwList.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "keywordList is required"));

        // Resolve userId → org_id if it looks like a user slug
        String orgKey = resolveOrgKey(userId);

        boolean blockCol    = Boolean.TRUE.equals(body.get("blockCol"));
        boolean criticalCol  = Boolean.TRUE.equals(body.get("criticalCol")) || Boolean.TRUE.equals(body.get("critialCol"));
        boolean redactedCol = Boolean.TRUE.equals(body.get("redactedCol"));
        boolean allowCol    = Boolean.TRUE.equals(body.get("allowCol"));
        String  promptCol   = body.getOrDefault("promptCol", "").toString();

        repository.insert(orgKey, subUser, kwList, blockCol, criticalCol, redactedCol, allowCol, promptCol);
        return ResponseEntity.ok(Map.of("status", "created", "userId", orgKey, "subUser", subUser));
    }

    /** DELETE /api/v1/policies/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePolicy(@PathVariable int id) {
        repository.deleteById(id);
        return ResponseEntity.ok(Map.of("status", "deleted", "id", id));
    }

    /**
     * Resolves a userId (e.g. "rohan-user") to its org_id string (e.g. "102").
     * If userId is already numeric (an org_id) or cannot be resolved, returns as-is.
     */
    private String resolveOrgKey(String userId) {
        // Already a numeric org_id?
        if (userId.matches("\\d+")) return userId;
        try {
            Optional<User> userOpt = userRepository.findByUserId(userId);
            if (userOpt.isPresent() && userOpt.get().getOrgId() != null) {
                return String.valueOf(userOpt.get().getOrgId());
            }
        } catch (Exception ignored) {}
        return userId; // fallback
    }
}
