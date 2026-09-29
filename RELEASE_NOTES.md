# Duo Status Bar 1.3.0 — stable

The 1.3 line graduates from beta. Everything below shipped through the betas and is now the stable
release: cross-ROM injection fixes (HyperOS, ColorOS, Samsung, Android 17 Compose), the **Vector /
late-injection** load fix (no more false "module not loaded"), accurate **Wi-Fi bars** (stock 4-bar scale
mapped to Duo's three), **choose your SIM** on dual-SIM phones, a tidied **settings screen with a
Customize section**, one-tap **bug reporting** (with a description field, plus **Export log to a file**),
a fixed **launcher icon**, and slower README GIFs.

## What's new in v1.3.0-beta.4 — pre-release

### Dual SIM and signal accuracy (from issue #10 and user reports)

- **Dual SIM: choose the line.** On a dual-SIM phone a new **Cellular line** setting picks which line the
  four spheres follow — Automatic (the data line), SIM 1 or SIM 2. Levels are read per subscription, and a
  single-SIM phone is unaffected.
- **A nicer screen.** The settings got a header with the app icon, rounded cards, and a cleaner layout.
- **Bug reporting, simplified.** "Refresh status", "Share status" and "Save status to a file" are gone.
  The user types **what went wrong**, then **Send the bug to the developer**: it packs the description, the
  module status and the root logs into one file and uploads it. There is also **Export log to a file**,
  which saves the same report anywhere with the system picker — no Telegram, no network required. The user
  does not need Telegram installed; the app uploads over HTTPS.
- **No bot token in the APK.** Uploads go through a tiny Cloudflare Worker relay (`relay/`) that holds the
  token as a server secret and enforces a real per-IP cooldown with a Durable Object. The app only knows
  the relay URL, so a published APK contains no secret. A hidden 60-second cooldown in the app plus the
  relay's own limit stop spamming. If the relay is unreachable, it falls back to the share sheet.
- **PayPal support button** with the PayPal mark.
- **Fixed launcher icon.** The adaptive-icon foreground was an old wireframe render; it is now rebuilt from
  the real Rive scene, solid and inside the launcher's safe zone (see `tools/make-launcher-icon.py`).
- **Restart System UI** now tries the module first, then root (several `pkill`/`killall` spellings), then
  Shizuku, so it works on ROMs without `su`.
- **Slower README GIFs** and regenerated from the scene.
- **Accurate Wi-Fi bars.** The Wi-Fi level now comes from the active network's own `WifiInfo` and the
  platform's RSSI-to-bars mapping instead of fixed thresholds, which showed a strong link as 2 of 3 bars.
- **Wi-Fi only when it has internet.** A connected-but-unvalidated Wi-Fi network (captive portal,
  internet-less AP) no longer shows the Wi-Fi glyph; the middle slot follows the connection that is really
  carrying data.
- **Your other icons stay.** The default now hides only the icons Duo replaces (battery, Wi-Fi,
  cellular). Alarm, Bluetooth, network speed and the rest are kept. Hiding everything is still available
  as an opt-in switch, and the detection now also matches OEM view ids/classes (e.g. ColorOS).

### Module loading (issue #9 and stale reports)

- **Late injection no longer reads as "never".** On frameworks that inject the module after System UI has
  started (e.g. Vector), `Application.onCreate` never fires for the module and the already-added status-bar
  window was never found. Duo now resolves the process context directly and adopts an existing status-bar
  window, and it records its heartbeat before the enable gate — so the About screen reflects reality.
- **Bootstrap no longer uses the system context.** The extra context resolution must not fall back to
  `ActivityThread.mSystemContext`: its package is `android` while the process uid is System UI, so every
  `ContentResolver` call (the `Settings.Global` override and the settings provider) was rejected with
  "*calling package android does not match caller's uid*" and the module resolved to `stage 0` — the icon
  did not draw. It now only accepts a context whose package is the app, or System UI's own window context.

### ROM fixes

- **HyperOS 3:** the icon-arrival hook now also covers `MiuiStatusIconContainer`, and the clock font is
  applied to the visible clock rather than a 0×0 duplicate in HyperOS's nested clock tree.

## What's new in v1.3.0-beta.3 — pre-release

These builds are **pre-release / beta**: the fixes below come straight from users' diagnostic logs on
several ROMs and are being tested in the open.

### beta.3 — Android 17 AOSP (Pixel) and safety

- **Android 17 AOSP support (Pixel).** Android 17 draws the status-bar Wi-Fi/cellular/battery cluster with
  Compose, and the old View strip (`system_icons`) is left `GONE`. The module used to inject into that dead
  container and draw nothing. It now detects the Compose icon view and draws the element in the bar,
  anchored over it. (Experimental — this is the one path with no on-device confirmation yet.)
- **Never inject into a `GONE` strip.** A GONE legacy container is skipped, so a ROM that has both the old
  and the new layouts can never end up with an invisible element again.
- **Clearer colour diagnostics.** The log now states which colour the element is drawn in and where it came
  from, so "the colour does not match the bar" can be pinned down from one report.

### beta.2 — HyperOS fix (icons hid but the ring never appeared)

- **The element no longer disappears on HyperOS 3 (Xiaomi).** The user's log showed the module attaching
  correctly and hiding the stock icons — but `DuoRiveView` measured **0×0**. HyperOS re-hosts
  `system_icons` as a `MiuiStatusBatteryContainer` that lays out only its own children, so the injected
  view got no size and drew nothing. The element now goes into the bar's plain `FrameLayout` layer and is
  anchored over the battery (which is kept laid-out, `INVISIBLE`, so the position survives a rotation).

### ROM fixes (from user logs)

- **HyperOS (Xiaomi) now actually attaches.** On HyperOS 16/17 the System UI process existed before
  LSPosed injected the module, so `Application.onCreate` never ran afterwards — the log said
  "MainHook loaded" and then nothing, and the app reported "module load: never". The module now installs
  its window hook the instant it loads and bootstraps from the already-created Application, so the
  element appears without a special build. The HyperOS adapter is now **measured** (HyperOS 3, Android
  17): `system_icons → MiuiStatusBatteryContainer`, with `system_icon_area`/`status_bar_icons` fallbacks.
- **Samsung One UI now attaches.** Same root cause: the window hook used to be installed too late, after
  One UI had already added the status-bar window, so nothing was ever found. Installing it first fixes
  it, and a failed attach now sends a full diagnostic dump instead of staying silent, so any remaining
  One UI id can be corrected from one report.
- **The Rive breaker no longer latches forever.** Two unrelated System UI restarts (the app's Restart
  button, the watchdog) could push the crash counter to 2 and the module then used the simple Canvas
  drawing *forever* — the OxygenOS 16 report (`renderer=Canvas, riveAttempts=2`). The counter now only
  stays armed while the last attempt is recent, so a genuine crash loop still stops, but a healthy device
  re-arms the animated renderer on its next start.
- **Failed ROMs leave evidence.** If the module cannot find the icon strip, it now pushes the same
  diagnostic dump (id probes + view tree) the About screen exports, so support does not need a round-trip.

### New

- **Show only Wi-Fi and 5G/4G.** A new Appearance switch keeps the middle of the ring to the Wi-Fi icon
  and the cellular 5G/4G label. When it is on, **Do Not Disturb and Airplane never replace them** — the
  slot follows the connection only.

**Tip:** changing the size still needs a restart — the **Restart System UI** button is in the app.
