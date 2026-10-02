# Duo relay (Cloudflare Worker)

A tiny Worker with two jobs:

1. **Log upload** (`POST /`) — forwards a bug report to Telegram, so the bot token is a Worker secret and
   never ships in the APK.
2. **Release feed** (`GET /releases`) — serves the newest GitHub release (stable **or** pre-release) as a
   small normalized JSON, cached in KV. Devices read this instead of GitHub's API, so at most one GitHub
   call per cache window serves every user and we stay far under GitHub's unauthenticated rate limit.
   `GET /download` streams the APK only as a fallback for networks where GitHub's CDN is blocked.

There is no server to maintain — Cloudflare runs it on the free tier.

## Endpoints

- `POST /` — `multipart/form-data`: `document` (the log file), optional `caption`. Forwards to Telegram.
  Per-IP cooldown of 60 s (Durable Object), 5 MB file cap. Nothing else is stored.
- `GET /releases` — `{ ok, fetchedAt, latest, releases }`, where each release is
  `{ version, tag, prerelease, pageUrl, apkUrl, sha256, publishedAt }`. `latest` is the highest version,
  including pre-releases. Cached in KV for 6 h, then served with `Cache-Control: max-age=21600`.
- `GET /download` — streams `latest.apkUrl` from GitHub with `application/vnd.android.package-archive`.
  Used by the app only when a direct GitHub download fails.

## Deploy (one time, ~2 minutes)

```bash
cd relay
npx wrangler login
npx wrangler kv namespace create RELEASES     # paste the printed id into wrangler.toml
npx wrangler secret put TELEGRAM_BOT_TOKEN    # the @BotFather token
npx wrangler secret put TELEGRAM_CHAT_ID      # your chat id, e.g. 695855306
npx wrangler deploy
```

`wrangler deploy` prints the URL, e.g. `https://duo-log-relay.<your-subdomain>.workers.dev`.

## Point the app at it

Add the URL to the git-ignored `.env` at the repo root, then rebuild:

```
TELEGRAM_RELAY_URL=https://duo-log-relay.<your-subdomain>.workers.dev
```

When `TELEGRAM_RELAY_URL` is set the app uses the relay for logs (and for update checks), and the bot
token can be removed from `.env` entirely, so no secret is compiled into the APK. Leave
`TELEGRAM_BOT_TOKEN` empty for public builds.

## Test without the app

```bash
curl -F "caption=hello" -F "document=@README.md;type=text/plain" https://duo-log-relay.<sub>.workers.dev
curl https://duo-log-relay.<sub>.workers.dev/releases
```

A second log POST within a minute returns `{"ok":false,"error":"rate_limited"}` (HTTP 429).
