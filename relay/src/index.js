/**
 * Duo Status Bar log relay.
 *
 * Holds the Telegram bot token as a Worker secret, so the Android APK carries no secret at all. The app
 * POSTs a multipart form (`document`, optional `caption`); this forwards it to Telegram's `sendDocument`
 * for the configured chat.
 *
 * Rate limiting is enforced by a Durable Object (one instance per caller IP), which is strongly
 * consistent — unlike KV, whose eventual consistency let a burst through. One send per IP per
 * `COOLDOWN_SECONDS`, plus a hard file-size cap.
 *
 * Nothing is stored except a "last sent" timestamp in each Durable Object.
 *
 * Required secrets (set with `wrangler secret put`, never in this file):
 *   TELEGRAM_BOT_TOKEN, TELEGRAM_CHAT_ID
 * Optional secret:
 *   RELAY_KEY  - when set, callers must send `X-Duo-Key: <value>`
 */

const MAX_BYTES = 5 * 1024 * 1024;
const COOLDOWN_SECONDS = 60;

export default {
  async fetch(request, env) {
    if (request.method !== "POST") return json({ ok: false, error: "method" }, 405);

    if (env.RELAY_KEY && request.headers.get("x-duo-key") !== env.RELAY_KEY) {
      return json({ ok: false, error: "forbidden" }, 403);
    }

    const ip = request.headers.get("cf-connecting-ip") || "unknown";

    // Reserve the slot first: the Durable Object is single-threaded per instance, so two concurrent
    // requests from the same IP cannot both pass.
    const gate = await env.LIMITER.get(env.LIMITER.idFromName(ip))
      .fetch("https://do/", {
        method: "POST",
        body: JSON.stringify({ cooldownMs: COOLDOWN_SECONDS * 1000 }),
      });
    if (gate.status === 429) return json({ ok: false, error: "rate_limited" }, 429);

    let form;
    try {
      form = await request.formData();
    } catch {
      return json({ ok: false, error: "bad_form" }, 400);
    }

    const document = form.get("document");
    if (!(document instanceof File)) return json({ ok: false, error: "no_document" }, 400);
    if (document.size > MAX_BYTES) return json({ ok: false, error: "too_large" }, 413);

    const caption = String(form.get("caption") || "").slice(0, 200);

    const telegram = new FormData();
    telegram.append("chat_id", env.TELEGRAM_CHAT_ID);
    if (caption) telegram.append("caption", caption);
    telegram.append("document", document, document.name || "duo-log.txt");

    const res = await fetch(
      `https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/sendDocument`,
      { method: "POST", body: telegram }
    );
    if (!res.ok) {
      const detail = (await res.text()).slice(0, 200);
      return json({ ok: false, error: "telegram", status: res.status, detail }, 502);
    }

    return json({ ok: true });
  },
};

/**
 * One instance per caller IP, holding the last accepted send time. Returns 429 while the caller is inside
 * the cooldown, 200 otherwise.
 */
export class RateLimiter {
  constructor(state) {
    this.state = state;
  }

  async fetch(request) {
    const { cooldownMs } = await request.json();
    const now = Date.now();
    const last = (await this.state.storage.get("last")) || 0;
    if (now - last < cooldownMs) return new Response("", { status: 429 });
    await this.state.storage.put("last", now);
    return new Response("", { status: 200 });
  }
}

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}
