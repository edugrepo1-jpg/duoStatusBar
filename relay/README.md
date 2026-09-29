# Duo log relay

A tiny Cloudflare Worker that receives a log upload from the app and forwards it to Telegram. It exists
so the **bot token never ships inside the APK**: the token is a Worker secret, and the app only knows this
Worker's URL. There is no server to maintain — Cloudflare runs it on the free tier.

## What it does

- `POST /` with `multipart/form-data`: `document` (the log file) and optional `caption` (the user's text).
- Forwards it to `sendDocument` for the chat in `TELEGRAM_CHAT_ID`.
- Per-IP cooldown of 60 s (KV marker), plus a 5 MB file cap. Nothing else is stored.

## Deploy (one time, ~2 minutes)

```bash
cd relay
npx wrangler login
npx wrangler kv namespace create RATE_LIMIT      # paste the printed id into wrangler.toml
npx wrangler secret put TELEGRAM_BOT_TOKEN       # the @BotFather token
npx wrangler secret put TELEGRAM_CHAT_ID         # your chat id, e.g. 695855306
npx wrangler deploy
```

`wrangler deploy` prints the URL, e.g. `https://duo-log-relay.<your-subdomain>.workers.dev`.

## Point the app at it

Add the URL to the git-ignored `.env` at the repo root, then rebuild:

```
TELEGRAM_RELAY_URL=https://duo-log-relay.<your-subdomain>.workers.dev
```

When `TELEGRAM_RELAY_URL` is set the app uses the relay and the bot token can be removed from `.env`
entirely, so no secret is compiled into the APK. Leave `TELEGRAM_BOT_TOKEN` empty for public builds.

## Test without the app

```bash
curl -F "caption=hello" -F "document=@README.md;type=text/plain" https://duo-log-relay.<sub>.workers.dev
```

A second call within a minute returns `{"ok":false,"error":"rate_limited"}` (HTTP 429).
