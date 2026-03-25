package com.promptguard.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class AdminAlertController {

    private static final Logger log = LoggerFactory.getLogger(AdminAlertController.class);

    /**
     * POST /api/v1/admin/alerts
     * Called by extension background.js when a non-admin tries to:
     *   - Remove the extension   → REMOVE_ATTEMPT
     *   - Disable the extension  → DISABLE_ATTEMPT
     */
    @PostMapping("/api/v1/admin/alerts")
    public ResponseEntity<?> receiveAlert(@RequestBody Map<String, Object> payload) {
        String type      = (String) payload.getOrDefault("type",      "UNKNOWN");
        String userId    = (String) payload.getOrDefault("userId",    "unknown");
        String message   = (String) payload.getOrDefault("message",   "No message");
        String timestamp = (String) payload.getOrDefault("timestamp", "N/A");

        log.warn("╔══════════════════════════════════════════════════════╗");
        log.warn("║  🚨  SECURITY ALERT — PromptGuard                   ║");
        log.warn("║  Type      : {}", type);
        log.warn("║  User      : {}", userId);
        log.warn("║  Message   : {}", message);
        log.warn("║  Timestamp : {}", timestamp);
        log.warn("╚══════════════════════════════════════════════════════╝");

        return ResponseEntity.ok(Map.of("status", "received"));
    }

    /**
     * GET /extension-removed-notice
     * Browser opens this page after extension is uninstalled
     * (set via chrome.runtime.setUninstallURL in background.js)
     */
    @GetMapping("/extension-removed-notice")
    public ResponseEntity<String> removedNotice() {
        log.warn("╔══════════════════════════════════════════════════════╗");
        log.warn("║  🚨  EXTENSION WAS REMOVED FROM A BROWSER           ║");
        log.warn("╚══════════════════════════════════════════════════════╝");
        return ResponseEntity.ok(
            "<html><body style='font-family:sans-serif;text-align:center;" +
            "padding:60px;background:#0a0f1e;color:#e2e8f0'>" +
            "<h1>🛡️ PromptGuard</h1>" +
            "<p style='color:#ef4444;font-size:18px'>Extension removed. Admin has been notified.</p>" +
            "</body></html>"
        );
    }
}
