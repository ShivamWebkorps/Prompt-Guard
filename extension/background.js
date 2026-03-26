// ============================================
// PromptGuard — background.js (Service Worker)
// Role is fetched from backend DB — not hardcoded
// ============================================

// Keep service worker alive
const keepAlive = () => setInterval(() => chrome.runtime.getPlatformInfo(() => {}), 20000);
chrome.runtime.onStartup.addListener(keepAlive);
keepAlive();

// ── Read settings from chrome.storage ─────────────────────────
async function getSettings() {
  return new Promise((resolve) => {
    chrome.storage.sync.get(["userId", "subUser", "apiUrl"], (data) => {
      resolve({
        userId: (data.userId && data.userId.trim()) ? data.userId.trim() : "anonymous-user",
        subUser: (data.subUser && data.subUser.trim()) ? data.subUser.trim() : "anonymous-user",
        apiUrl: (data.apiUrl && data.apiUrl.trim()) ? data.apiUrl.trim() : "http://localhost:8082",
      });
    });
  });
}

// ── Role check — hits backend DB, NO hardcoded userId ─────────
async function isAdmin() {
  const { userId, apiUrl } = await getSettings();
  if (!userId || userId === "anonymous-user") return false;
  try {
    const res  = await fetch(`${apiUrl}/api/v1/users/${userId}/role`);
    if (!res.ok) return false;
    const data = await res.json();
    return data.role === "ADMIN";
  } catch (e) {
    console.warn("[PromptGuard] Role check failed:", e.message);
    return false;
  }
}

// ── Send tamper alert to backend console ──────────────────────
async function sendAdminAlert(type, userId, msg) {
  const { apiUrl } = await getSettings();
  try {
    await fetch(`${apiUrl}/api/v1/admin/alerts`, {
      method:  "POST",
      headers: { "Content-Type": "application/json" },
      body:    JSON.stringify({ type, userId, message: msg, timestamp: new Date().toISOString() })
    });
    console.warn(`[PromptGuard] 🚨 Alert sent [${type}] user="${userId}"`);
  } catch (e) {
    console.warn("[PromptGuard] Alert send failed (backend offline):", e.message);
  }
}

// ── In-memory stats (also persisted to chrome.storage.local) ──
let stats = { total: 0, blocked: 0, redacted: 0, alerted: 0, allowed: 0 };

chrome.storage.local.get("stats", (data) => {
  if (data.stats) stats = data.stats;
});

// ── Message handler ───────────────────────────────────────────
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {

  // Stats update from content.js
  if (message.type === "UPDATE_STATS") {
    stats.total++;
    const a = (message.action || "").toUpperCase();
    if      (a === "BLOCK")  stats.blocked++;
    else if (a === "REDACT") stats.redacted++;
    else if (a === "ALERT")  stats.alerted++;
    else                     stats.allowed++;

    if (stats.blocked > 0) {
      chrome.action.setBadgeText({ text: String(stats.blocked) });
      chrome.action.setBadgeBackgroundColor({ color: "#dc2626" });
    }
    chrome.storage.local.set({ stats });
    return;
  }

  // GET_ROLE — popup asks whether current user is admin
  if (message.type === "GET_ROLE") {
    (async () => {
      const { userId } = await getSettings();
      const admin      = await isAdmin();
      sendResponse({ isAdmin: admin, userId });
    })();
    return true; // keep channel open for async
  }

  // REQUEST_UNINSTALL — admin only
  if (message.type === "REQUEST_UNINSTALL") {
    (async () => {
      const { userId } = await getSettings();
      if (await isAdmin()) {
        chrome.management.uninstallSelf({ showConfirmDialog: true });
        sendResponse({ allowed: true });
      } else {
        await sendAdminAlert("REMOVE_ATTEMPT", userId,
          `User "${userId}" tried to remove the extension!`);
        sendResponse({ allowed: false });
      }
    })();
    return true;
  }

  // REQUEST_DISABLE — admin only
  if (message.type === "REQUEST_DISABLE") {
    (async () => {
      const { userId } = await getSettings();
      if (await isAdmin()) {
        sendResponse({ allowed: true });
      } else {
        await sendAdminAlert("DISABLE_ATTEMPT", userId,
          `User "${userId}" tried to disable the extension!`);
        sendResponse({ allowed: false });
      }
    })();
    return true;
  }
});

// ── Detect browser from user agent ───────────────────────────
async function detectBrowser() {
  const ua = navigator.userAgent;
  // Brave must be checked FIRST — it uses identical Chrome UA string
  try {
    if (navigator.brave && typeof navigator.brave.isBrave === "function") {
      const isBrave = await navigator.brave.isBrave();
      if (isBrave) return "Brave";
    }
  } catch (_) {}
  if (ua.includes("Edg/"))                               return "Edge";
  if (ua.includes("OPR/") || ua.includes("Opera"))      return "Opera";
  if (ua.includes("Firefox/"))                           return "Firefox";
  if (ua.includes("Safari/") && !ua.includes("Chrome")) return "Safari";
  if (ua.includes("Chrome/"))                            return "Chrome";
  return "Unknown";
}

// ── Heartbeat every 30s ───────────────────────────────────────
async function sendHeartbeat() {
  const { userId, subUser, apiUrl } = await getSettings();
  try {
    await fetch(`${apiUrl}/api/v1/heartbeat`, {
      method:  "POST",
      headers: { "Content-Type": "application/json" },
      body:    JSON.stringify({
        userId,
        subUser,
        browserName:      await detectBrowser(),
        extensionVersion: chrome.runtime.getManifest().version,
        status:    "ACTIVE",
        timestamp: new Date().toISOString().slice(0, 19)
      })
    });
  } catch (_) { /* silent — backend may be offline */ }
}
sendHeartbeat();
setInterval(sendHeartbeat, 30000);

// ── Uninstall page ────────────────────────────────────────────
chrome.runtime.setUninstallURL("http://localhost:8082/extension-removed-notice");

// ── Detect disable from chrome://extensions ───────────────────
try {
  chrome.management.onDisabled.addListener(async (info) => {
    if (info.id !== chrome.runtime.id) return;
    const { userId } = await getSettings();
    if (!(await isAdmin())) {
      await sendAdminAlert("DISABLE_ATTEMPT", userId,
        `User "${userId}" disabled extension from chrome://extensions`);
    }
  });
} catch (e) {
  console.warn("[PromptGuard] management API:", e.message);
}
