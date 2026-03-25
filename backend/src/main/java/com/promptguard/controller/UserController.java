package com.promptguard.controller;

import com.promptguard.model.User;
import com.promptguard.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/users")
@CrossOrigin(origins = "*")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * GET /api/v1/users/{userId}/role
     * Called by extension background.js on every popup open.
     * Always returns a valid response — never throws an error.
     * Unknown users get role=USER (safe default).
     */
    @GetMapping("/{userId}/role")
    public ResponseEntity<?> getRole(@PathVariable String userId) {
        if (userId == null || userId.isBlank() || userId.equals("anonymous-user")) {
            return ResponseEntity.ok(Map.of("role", "USER", "exists", false, "userId", userId));
        }

        try {
            Optional<User> user = userRepository.findByUserId(userId);
            if (user.isEmpty()) {
                return ResponseEntity.ok(Map.of("role", "USER", "exists", false, "userId", userId));
            }
            return ResponseEntity.ok(Map.of(
                    "role", user.get().getRole(),
                    "exists", true,
                    "userId", userId));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("role", "USER", "exists", false, "userId", userId));
        }
    }

    /** GET /api/v1/users — list all users */
    @GetMapping
    public ResponseEntity<List<User>> listUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    /**
     * POST /api/v1/users
     * Body: { "userId": "john-user", "displayName": "John", "role": "USER" }
     */
    @PostMapping
    public ResponseEntity<?> createOrUpdateUser(@RequestBody Map<String, String> body) {
        String userId      = body.get("userId");
        String displayName = body.getOrDefault("displayName", userId);
        String role        = body.getOrDefault("role", "USER");
        String orgIdStr    = body.get("orgId");
        
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));
        }

        // Validate role
        if (!"ADMIN".equalsIgnoreCase(role) && !"USER".equalsIgnoreCase(role)) {
            role = "USER";
        }

        Integer orgId = null;
        if (orgIdStr != null && !orgIdStr.isBlank()) {
            try { orgId = Integer.parseInt(orgIdStr); } catch (NumberFormatException ignored) {}
        }

        User user = new User(userId, displayName, role, orgId);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("status", "saved", "userId", userId, "role", role));
    }
}
