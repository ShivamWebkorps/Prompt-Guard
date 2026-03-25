// ============================================
// PromptGuard — popup.js  v2.0
// Tabs UI, browser detection, full DB-backed auth
// ============================================

// ── Browser detection ─────────────────────────
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

const ORG_MAP = {
  "101": "Telecomm",
  "kushal-user": "Telecomm",
  "102": "Software",
  "rohan-user": "Software"
};
const orgLabel = (id) => ORG_MAP[id] || id;

// ── Helpers ───────────────────────────────────
function setText(id, val) {
  const el = document.getElementById(id);
  if (el) el.textContent = val;
}

function showAlert(msg) {
  let el = document.getElementById("pg-alert");
  if (!el) {
    el = document.createElement("div");
    el.id = "pg-alert";
    el.style.cssText = `position:fixed;bottom:8px;left:8px;right:8px;
      background:#7f1d1d;border:1px solid #ef4444;color:#fef2f2;
      padding:8px 12px;border-radius:7px;font-size:11px;
      font-weight:700;text-align:center;z-index:9999`;
    document.body.appendChild(el);
  }
  el.textContent   = msg;
  el.style.display = "block";
  setTimeout(() => { el.style.display = "none"; }, 3000);
}

function updateOrgDisplay() {
  const val  = document.getElementById("userIdInput")?.value?.trim();
  const warn = document.getElementById("userIdWarn");
  const inp  = document.getElementById("userIdInput");
  const res  = document.getElementById("orgResolved");

  if (!val) {
    warn?.classList.add("show");
    inp?.classList.add("warn");
    if (res) res.textContent = "";
  } else {
    warn?.classList.remove("show");
    inp?.classList.remove("warn");
    if (res) {
      const label = ORG_MAP[val];
      res.textContent = label ? `(${label})` : "";
    }
  }
}

// ── DOM Ready ─────────────────────────────────
document.addEventListener("DOMContentLoaded", () => {

  // ── Tab switching ────────────────────────────
  document.querySelectorAll(".tab").forEach(tab => {
    tab.addEventListener("click", () => {
      document.querySelectorAll(".tab").forEach(t => t.classList.remove("active"));
      document.querySelectorAll(".panel").forEach(p => p.classList.remove("active"));
      tab.classList.add("active");
      document.getElementById("panel-" + tab.dataset.tab)?.classList.add("active");
    });
  });

  // ── Load saved settings ──────────────────────
  chrome.storage.sync.get(["userId", "subUser", "apiUrl", "enabled"], (data) => {
    if (data.userId)  document.getElementById("userIdInput").value = data.userId;
    if (data.subUser) document.getElementById("subUserInput").value = data.subUser;
    if (data.apiUrl)  document.getElementById("apiUrlInput").value = data.apiUrl;
    if (data.enabled === false) document.getElementById("enabledToggle").checked = false;
    updateOrgDisplay();
  });

  // ── Load stats ───────────────────────────────
  chrome.storage.sync.get(["userId", "apiUrl"], (syncData) => {
    const apiBase = syncData.apiUrl || "http://localhost:8080";
    const userId  = syncData.userId;

    // 1. Show local cache first (instant)
    chrome.storage.local.get("stats", (localData) => {
      if (localData.stats) {
        const s = localData.stats;
        setText("statTotal",    s.total    || 0);
        setText("statBlocked",  s.blocked  || 0);
        setText("statRedacted", s.redacted || 0);
        setText("statAllowed",  s.allowed  || 0);
      }
    });

    // 2. Fetch fresh from DB (consistent with UI)
    if (userId) {
      loadRemoteStats(userId, apiBase);
    }
  });

  // ── Role check ───────────────────────────────
  chrome.runtime.sendMessage({ type: "GET_ROLE" }, (res) => {
    const isAdmin = res?.isAdmin || false;
    const userId  = res?.userId  || "";

    // Role badge
    const badge = document.getElementById("roleBadge");
    if (badge) {
      const displayId = isAdmin ? "ADMIN" : orgLabel(userId || "anonymous");
      badge.textContent   = (isAdmin ? "👑 " : "👤 ") + displayId;
      badge.style.display = "inline-block";
      if (isAdmin) badge.classList.add("admin");
    }

    // Admin panel
    const adminContent = document.getElementById("adminContent");
    if (isAdmin && adminContent) {
      adminContent.innerHTML = `
        <div style="background:#0d0f25;border:1px solid #4f46e544;border-radius:10px;padding:14px;margin-bottom:12px">
          <div style="font-size:11px;color:#818cf8;font-weight:700;letter-spacing:1px;text-transform:uppercase;margin-bottom:8px">Admin Controls</div>
          <div style="font-size:12px;color:#64748b;line-height:1.6">
            Logged in as <span style="color:#a78bfa;font-weight:700">${userId}</span><br>
            You have full administrative access.
          </div>
        </div>
        <button class="danger-btn" id="removeExtBtn">🗑️ Remove Extension from Device</button>`;

      document.getElementById("removeExtBtn")?.addEventListener("click", () => {
        if (confirm("Are you sure you want to remove PromptGuard from this device?")) {
          chrome.runtime.sendMessage({ type: "REQUEST_UNINSTALL" }, (r) => {
            if (!r?.allowed) showAlert("🔒 Remove not allowed");
          });
        }
      });
    } else if (adminContent) {
      adminContent.innerHTML = `<div class="lock-row">🔒 Admin access required to view this panel.</div>`;
    }

    // Non-admin: lock toggle
    if (!isAdmin) {
      const wrapper = document.getElementById("toggleWrapper");
      if (wrapper) {
        wrapper.innerHTML = `<div class="lock-row">🔒 Managed by your organization</div>`;
      }
    }

    // Toggle intercept for non-admin
    const toggle = document.getElementById("enabledToggle");
    if (toggle && !isAdmin) {
      toggle.addEventListener("change", (e) => {
        if (!e.target.checked) {
          chrome.runtime.sendMessage({ type: "REQUEST_DISABLE" }, (r) => {
            if (!r?.allowed) { e.target.checked = true; showAlert("🔒 Only admin can disable protection"); }
          });
        }
      });
    }
  });

  // ── Save settings ────────────────────────────
  document.getElementById("saveBtn")?.addEventListener("click", () => {
    const userId  = document.getElementById("userIdInput").value.trim();
    const subUser = document.getElementById("subUserInput").value.trim();
    const apiUrl  = document.getElementById("apiUrlInput").value.trim() || "http://localhost:8080";
    const enabled = document.getElementById("enabledToggle")?.checked ?? true;
    const btn     = document.getElementById("saveBtn");

    chrome.storage.sync.set({ userId, subUser, apiUrl, enabled }, () => {
      btn.textContent = "Saved ✓";
      btn.style.background = "#16a34a";
      setTimeout(() => { btn.textContent = "Save Settings"; btn.style.background = "#4f46e5"; }, 2000);
      loadRemoteStats(userId, apiUrl);
      updateOrgDisplay();
    });
  });

  // ── Refresh & Action buttons ────────────────
  document.getElementById("refreshStatsBtn")?.addEventListener("click", () => {
    chrome.storage.sync.get(["userId", "apiUrl"], (data) => {
      loadRemoteStats(data.userId, data.apiUrl || "http://localhost:8080");
    });
  });

  document.getElementById("testBtn")?.addEventListener("click", runTestPrompt);
  document.getElementById("userIdInput")?.addEventListener("input", updateOrgDisplay);

}); // end DOMContentLoaded

// ── Run test prompt ───────────────────────────
async function runTestPrompt() {
  const promptEl = document.getElementById("testPrompt");
  const prompt   = promptEl?.value?.trim();
  if (!prompt) { showAlert("Please type a prompt first"); return; }

  const testBtn = document.getElementById("testBtn");
  testBtn.disabled    = true;
  testBtn.textContent = "⏳ Checking...";

  const apiBase  = document.getElementById("apiUrlInput")?.value?.trim() || "http://localhost:8080";
  const userId   = document.getElementById("userIdInput")?.value?.trim() || "popup-test";
  const subUser  = document.getElementById("subUserInput")?.value?.trim() || "test-user";

  const resultBox      = document.getElementById("resultBox");
  const resultAction   = document.getElementById("resultAction");
  const resultDetail   = document.getElementById("resultDetail");
  const resultRedacted = document.getElementById("resultRedacted");

  try {
    const res = await fetch(`${apiBase}/api/v1/prompts`, {
      method:  "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        userId,
        subUser,
        tool:        "PopupTest",
        browserName: await detectBrowser(),
        prompt,
        testing:     true,
        timestamp:   new Date().toISOString().slice(0, 19)
      })
    });

    if (!res.ok) throw new Error("HTTP " + res.status);
    const data  = await res.json();
    const icons = { BLOCK:"🚫", REDACT:"✏️", ALERT:"⚠️", ALLOW:"✅" };

    resultAction.textContent = `${icons[data.action] || ""} ${data.action}`;
    resultAction.className   = `result-action ${data.action}`;
    resultDetail.textContent =
      `${data.reason} · Risk: ${data.riskScore}/100 (${data.riskLevel}) · ${data.processingTimeMs}ms`;

    if (data.action === "REDACT" && data.redactedPrompt && data.redactedPrompt !== prompt) {
      resultRedacted.textContent   = "Redacted: " + data.redactedPrompt;
      resultRedacted.style.display = "block";
    } else {
      resultRedacted.style.display = "none";
    }
    resultBox.classList.add("show");

    // 🔄 REFRESH stats after prompt!
    loadRemoteStats(userId, apiBase);
    resultBox.classList.add("show");

  } catch (err) {
    resultAction.textContent = "❌ Connection Error";
    resultAction.className   = "result-action BLOCK";
    resultDetail.textContent = `Cannot reach ${apiBase} — Is Spring Boot running?`;
    resultRedacted.style.display = "none";
    resultBox.classList.add("show");
  }

  testBtn.disabled    = false;
  testBtn.textContent = "⚡ Check Prompt";
}

// ── Load Remote Stats ────────────────────────
async function loadRemoteStats(userId, apiBase) {
  if (!userId) return;
  const btn = document.getElementById("refreshStatsBtn");
  if (btn) {
    btn.style.transition = "transform 0.6s cubic-bezier(0.4, 0, 0.2, 1)";
    btn.style.transform  = "rotate(360deg)";
    btn.style.color      = "#818cf8";
  }

  try {
    const res = await fetch(`${apiBase}/api/analytics/my-stats?userId=${userId}`);
    if (!res.ok) return;
    const s = await res.json();
    
    setText("statTotal",    s.total    || 0);
    setText("statBlocked",  s.blocked  || 0);
    setText("statRedacted", s.redacted || 0);
    setText("statAllowed",  s.allowed  || 0);
    
    chrome.storage.local.set({ stats: s });
  } catch (err) {
    console.error("Remote stats fetch failed", err);
  } finally {
    if (btn) {
      setTimeout(() => {
        btn.style.transform = "rotate(0deg)";
        btn.style.transition = "none";
        btn.style.color = "#64748b";
      }, 600);
    }
  }
}
