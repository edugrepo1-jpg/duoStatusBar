# Code review (NFR-01)

Scope: the current `main` after PR #11 (staging-merged) plus the FR-01 changes. Findings are rated by
real impact, not style preference. Anything that can break System UI or lose a user's icons is critical;
anything that misleads support or wastes battery is moderate; the rest is minor.

## Security verification of the external PR

- **Malware scan: clean.** The PR adds no `Runtime.exec`/`ProcessBuilder`, no sockets or HTTP, no new
  `System.load`/`Class.forName`/DEX loading, no base64 blobs and no manifest/permission changes. The
  only I/O it touches is the already-present `ContentResolver.query` and one settings `sendBroadcast`.
- **Binary authenticity: proven.** The committed `app/src/main/res/raw/duo.riv` is byte-identical
  (SHA256 `62D08020…`) to a fresh `rive rive/duo --once` build of the reviewed text `scene.rml`. The
  shipped drawing cannot differ from the source that was reviewed.
- **No secrets added.** The diff touches no `.env`, keystore, or relay configuration.

## Critical

None found.

## Moderate

1. **`RootLogs.restartSystemUi` reports success unconditionally.** It ignores the `waitFor` result and
   the timeout, then returns `true`. A root prompt that hangs or is denied is still reported as a
   restart. *Fix:* return the wait outcome and log a timeout. (Applied in this pass.)
2. **`L` does not carry a level.** `i`/`w`/`d`/`v`/`e` all go out as `Log.i` plus `XposedBridge.log`, so
   warnings cannot be told from info in the dump and the log cannot be filtered. This is the main
   NFR-06 gap; log strings stay frozen, but a prefix level (`L.w` → `W|`) would help support.
3. **Idle animation pause is invisible to users.** `DuoRiveView.idleStop` stops the renderer ~3 s after
   the last change (battery fix). Users read this as "static / broken animations" (see
   `docs/rive-selinux-root.md`). *Product decision:* keep (battery) or expose an idle-motion option.
4. **Settings provider column contract expanded.** `DuoPrefs.COLUMNS` is now
   `PORTRAIT_COLUMNS + LANDSCAPE_COLUMNS`. App and module ship in one APK, so this is safe today, but a
   mismatched app/module pair would read shifted columns. Documented in `rom-contract.md`; no action
   while they ship together.
5. **`RomProfiles` cache has no invalidation path.** Fine for a bundled asset; a future remote-rule
   override must add an explicit refresh or the process keeps the first parse for its lifetime.

## Minor

1. **Hard-coded `"com.android.systemui"` in several places** (`Diag` candidate list, `RootLogs` restart
   script) instead of routing through `RomAdapter.systemUiPackage`. All current adapters use the same
   package, so no behaviour change, but it is one more place to edit for an OEM that renames System UI.
2. **`ContainerFinder.roleScan` matches class-name substrings** (`StatusIconContainer`,
   `BatteryMeter`/`BatteryIcon`/`BatteryView`). A vendor class that merely contains those words could
   match; the lowest-common-ancestor rule makes a false strip unlikely, and a wrong match is a no-draw,
   never a crash.
3. **`ElementGestures.install` re-creates the detector and re-logs on every layout apply.** Harmless but
   noisy; the log line is duplicated in the dump.
4. **`Diag.CANDIDATE_IDS` lists `status_icons` twice** (pre-existing). Cosmetic.
5. **`RomDetection` haystack matching is substring-based** (e.g. a product string containing `oxygen`).
   Pre-existing; the measured-profile override is additive and does not widen it.

## Positive confirmations

- Every new attach/hide/layout path in PR #11 is wrapped in `L.guard`/`try`, and the element still has
  the Rive → Canvas fallback per surface.
- The split layout is **off by default**, and the taller element box preserves the original percentage
  seat (`77.5 - 76 = 61.5 - 60`), so defaults are unchanged.
- The new tests (DuoMapping 258 lines, SettingsChannel 212) cover the new state, the split layout, the
  migration from `network_only`, and the orientation column split.

## Follow-ups (tracked)

- NFR-06: add a level to `L` and expand the dump (already partly done in `RootLogs` and the role block).
- M3: decide the idle-motion default.
- M5: add the role/profiles cases to the on-device ROM matrix (phone + GSI emulator).
