// ============================================
// PromptGuard — content.js
// Intercepts prompts on ChatGPT / Gemini / Copilot / Claude
// Always sends userId from storage — never anonymous
// ============================================

let interceptEnabled = true;

function detectTool() {
  const h = window.location.hostname;
  if (h.includes("openai") || h.includes("chatgpt")) return "ChatGPT";
  if (h.includes("gemini")) return "Gemini";
  if (h.includes("copilot")) return "Copilot";
  if (h.includes("claude")) return "Claude";
  if (h.includes("perplexity")) return "Perplexity";
  if (h.includes("deepseek")) return "DeepSeek";
  if (h.includes("grok") || h.includes("x.com")) return "Grok";
  return "Unknown";
}

async function detectBrowser() {
  const ua = navigator.userAgent;
  // Brave: must check first — uses Chrome UA string
  try {
    if (navigator.brave && typeof navigator.brave.isBrave === "function") {
      const isBrave = await navigator.brave.isBrave();
      if (isBrave) return "Brave";
    }
  } catch (_) { }
  if (ua.includes("Edg/")) return "Edge";
  if (ua.includes("OPR/") || ua.includes("Opera")) return "Opera";
  if (ua.includes("Firefox/")) return "Firefox";
  if (ua.includes("Safari/") && !ua.includes("Chrome")) return "Safari";
  if (ua.includes("Chrome/")) return "Chrome";
  return "Unknown";
}

async function getSettings() {
  return new Promise((resolve) => {
    chrome.storage.sync.get(["userId", "subUser", "apiUrl", "enabled"], (data) => {
      resolve({
        userId:  (data.userId && data.userId.trim()) ? data.userId.trim().toLowerCase() : "anonymous-user",
        subUser: (data.subUser && data.subUser.trim()) ? data.subUser.trim().toLowerCase() : "anonymous-sub",
        apiUrl:  (data.apiUrl  && data.apiUrl.trim())  ? data.apiUrl.trim()  : "http://localhost:8082",
        enabled: data.enabled !== false,
      });
    });
  });
}

function showToast(message, type) {
  const old = document.getElementById("pg-toast");
  if (old) old.remove();

  const styles = {
    block: { bg: "#dc2626", border: "#ef4444", icon: "🚫" },
    redact: { bg: "#7c3aed", border: "#a78bfa", icon: "✏️" },
    alert: { bg: "#d97706", border: "#fbbf24", icon: "⚠️" },
    allow: { bg: "#16a34a", border: "#4ade80", icon: "✅" },
  };
  const s = styles[type] || styles.allow;

  if (!document.getElementById("pg-style")) {
    const el = document.createElement("style");
    el.id = "pg-style";
    el.textContent = `@keyframes pgIn { from{transform:translateX(120%);opacity:0} to{transform:translateX(0);opacity:1} }`;
    document.head.appendChild(el);
  }

  const toast = document.createElement("div");
  toast.id = "pg-toast";
  toast.style.cssText = `
    position:fixed;top:20px;right:20px;z-index:2147483647;
    background:${s.bg};border:2px solid ${s.border};color:#fff;
    padding:14px 20px;border-radius:12px;
    font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;
    font-size:14px;font-weight:600;max-width:380px;
    box-shadow:0 8px 30px rgba(0,0,0,.5);animation:pgIn .3s ease;
    line-height:1.5;`;
  toast.innerHTML = `
    <div style="display:flex;align-items:flex-start;gap:10px">
      <span style="font-size:22px;line-height:1">${s.icon}</span>
      <div style="flex:1">
        <div style="font-size:11px;font-weight:800;text-transform:uppercase;
                    letter-spacing:1.5px;margin-bottom:5px;opacity:.85">PromptGuard</div>
        <div style="font-size:13px">${message}</div>
      </div>
      <span id="pg-close" style="cursor:pointer;font-size:20px;opacity:.7;margin-left:6px">×</span>
    </div>`;
  document.body.appendChild(toast);
  document.getElementById("pg-close").addEventListener("click", () => toast.remove());
  setTimeout(() => { if (toast.parentNode) toast.remove(); }, 6000);
}

async function checkPrompt(promptText, submitFn) {
  const { userId, subUser, apiUrl, enabled } = await getSettings();
  if (!enabled) { submitFn(promptText); return; }

  try {
    const res = await fetch(`${apiUrl}/api/v1/prompts`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        userId,
        subUser,
        tool: detectTool(),
        browserName: await detectBrowser(),
        prompt: promptText,
        timestamp: new Date().toISOString().slice(0, 19),
      }),
    });

    if (!res.ok) { submitFn(promptText); return; }

    const result = await res.json();

    if (result.action === "BLOCK") {
      showToast("Prompt BLOCKED! " + result.reason, "block");
      // Do NOT call submitFn — prompt is stopped here
    } else if (result.action === "REDACT") {
      showToast("Sensitive data removed. " + result.reason, "redact");
      submitFn(result.redactedPrompt || promptText);
    } else if (result.action === "ALERT") {
      showToast("Warning: " + result.reason, "alert");
      submitFn(promptText);
    } else {
      submitFn(promptText);
    }

    chrome.runtime.sendMessage({ type: "UPDATE_STATS", action: result.action });

  } catch (err) {
    console.error("[PromptGuard]", err);
    submitFn(promptText); // fail open
  }
}

function getPromptText(el) {
  return el ? (el.value || el.innerText || el.textContent || "") : "";
}

function isPromptBox(el) {
  if (!el) return false;
  if (el.tagName === "TEXTAREA" || el.tagName === "INPUT") return true;
  const ce = el.getAttribute("contenteditable");
  if (ce === "true" || ce === "plaintext-only" || ce === "") return true;
  if (el.getAttribute("role") === "textbox") return true;
  return false;
}

let lastActivePromptBox = null;

// Track the last element the user typed into
document.addEventListener("focus", (e) => {
  if (isPromptBox(e.target)) lastActivePromptBox = e.target;
}, true);
document.addEventListener("input", (e) => {
  if (isPromptBox(e.target)) lastActivePromptBox = e.target;
}, true);

// ── ENTER key intercept ──────────────────────────────────────
document.addEventListener("keydown", async (e) => {
  if (!interceptEnabled || e.key !== "Enter" || e.shiftKey || e.ctrlKey || e.isComposing) return;
  const active = document.activeElement;
  if (!isPromptBox(active)) return;
  const text = getPromptText(active).trim();
  if (text.length < 3) return;

  e.preventDefault();
  e.stopImmediatePropagation();

  await checkPrompt(text, (final) => {
    if (active.tagName === "TEXTAREA" || active.tagName === "INPUT") {
      active.value = final;
    } else {
      active.innerText = final;
      const r = document.createRange(), s = window.getSelection();
      r.selectNodeContents(active); r.collapse(false);
      s.removeAllRanges(); s.addRange(r);
    }

    active.dispatchEvent(new Event("input", { bubbles: true })); // alert React/Vue
    interceptEnabled = false;
    active.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", code: "Enter", keyCode: 13, bubbles: true, cancelable: true }));
    setTimeout(() => { interceptEnabled = true; }, 300);
  });
}, true);

// ── Send button intercept ─────────────────────────────────────
document.addEventListener("click", async (e) => {
  if (!interceptEnabled) return;
  const btn = e.target.closest(
    "button[data-testid*='send' i]," +
    "button[aria-label*='send' i]," +
    "button[aria-label*='ubmit' i]," +
    "button[aria-label*='Grok' i]," +
    "button[class*='send' i]," +
    "button svg," +
    "div[role='button'][aria-label*='send' i]," +
    "div[role='button'][aria-label*='Grok' i]," +
    "div[role='button'][aria-label*='deepseek' i]"
  );
  if (!btn) return;

  const actualBtn = (btn.tagName === 'SVG') ? btn.closest('button') : btn;
  if (!actualBtn) return;

  const area =
    lastActivePromptBox ||
    document.querySelector("textarea#prompt-textarea, textarea#chat-input, textarea[placeholder]") ||
    document.querySelector("[contenteditable='plaintext-only'], [contenteditable='true']") ||
    document.querySelector("textarea");

  if (!area || !isPromptBox(area)) return;

  const text = getPromptText(area).trim();
  if (text.length < 3) return;

  e.preventDefault();
  e.stopImmediatePropagation();

  await checkPrompt(text, (final) => {
    if (area.tagName === "TEXTAREA" || area.tagName === "INPUT") area.value = final;
    else area.innerText = final;

    area.dispatchEvent(new Event("input", { bubbles: true })); // alert React/Vue
    interceptEnabled = false;
    actualBtn.click();
    setTimeout(() => { interceptEnabled = true; }, 300);
  });
}, true);

console.log("🛡️ PromptGuard active on", detectTool());
