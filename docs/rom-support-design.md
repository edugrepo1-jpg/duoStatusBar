# ROM support — design (FR-01 / FR-02)

Status: design, M1. Owner: module. Evidence: the diagnostic logs in
`diagnose from users/` and `~/Downloads/Telegram Desktop/` from 2026-09-22 to 2026-10-02, plus the
on-device dump procedure in [`rom-contract.md`](rom-contract.md).

## Why this exists

Duo was built ROM by ROM: each fix added one more static `RomAdapter` with a hand-written list of
resource ids. That works for the phones we can measure and guesses for the rest, which is why only two
adapters (`oxygenos`, `hyperos`) are marked **measured** and the others are guesses. Every new custom ROM
is therefore a support ticket, and a wrong guess can leave a ROM half-attached (icons hidden, element
0x0) instead of untouched.

The goal is the opposite: an unseen ROM should attach by **role** in about 80% of cases, and a profile
for the remaining 20% should be data we can update without shipping an APK.

## Evidence from the 2026-09/10 logs

| Device | ROM | Adapter | Attach | Rive | Notes |
|---|---|---|---|---|---|
| CPH2747 | OxygenOS 16 | `oxygenos` measured | OK | Rive | Baseline; `su` present |
| CPH2653 | OxygenOS 16 | `oxygenos` measured | OK | Canvas | `riveAttempts=2` breaker latch (fixed in 1.3.2-beta.2) |
| 24129PN74G | HyperOS 3 / A17 | `hyperos` measured | OK | Rive | Wi-Fi arcs, spheres fine |
| 24129PN74C | HyperOS / A16 | `hyperos` | OK-ish | Canvas | 1.1.0 build |
| 25010PN30G | HyperOS | `hyperos` | OK | Rive | "percentage not centred" report |
| 25053RT47C | HyperOS 3.5 | `hyperos` | OK | Rive | "only shows on home screen" |
| SM-A5360 / SM-S948N / SM-A566B / SM-S938B | One UI | `samsung` **unverified** | mixed | mixed (`ready=false` snapshots) | `su` missing; locked-bootloader root |

What the logs prove:

1. **Id-only selection is brittle.** On Samsung/HyperOS the AOSP `system_icons` can resolve to a dead
   0x0 stub while the real cluster lives elsewhere. The host patches this one layout at a time
   (`isStubStrip`, `CombinedStatusView`, `MiuiStatusBatteryContainer`) instead of choosing by role.
2. **"Nothing changes after enabling"** reports show the module loaded only into its own app
   (`out of scope: not SystemUI`) and `no report yet` from SystemUI. That is a scope/injection problem,
   and the diagnostic cannot currently say so in plain words.
3. **Locked-bootloader root hides the evidence.** Reports from those users reached us with
   `root log collection failed: Cannot run program "su"`. (Addressed in `RootLogs` as part of this
   phase: absolute-path root discovery + non-root device facts.)
4. **Rive runs, but idle motion stops by design.** `Rive renderer paused (idle)` is logged ~3 s after
   the last change; `reveal fired` still appears on attach/state changes. "Static mode" needs to be
   separated from "animation never runs" before any change is made.

## Current structure (what we keep)

`RomDetection.forThisRom(...)` → `RomAdapter(id, label, systemUiPackage, containerIds, batteryId,
clockId, notes)`; `ContainerFinder` resolves the strip: adapter ids (AOSP spelling first) → anchors
(`batteryId`, `statusIcons`, `status_icons`, `system_icons`) → tree walk. `Diag` dumps the tree.

The frozen surface in [`rom-contract.md`](rom-contract.md) stays frozen: probe order, reflection
targets, hide semantics, stage precedence and log strings. This design is **additive**.

## Proposed structure — role-based locator + data profiles

### 1. `StripLocator` (new, additive strategy after the existing two)

Instead of trusting an id, resolve by **what the strip must contain**:

- Find the battery node: `rom.batteryId` **or** a view whose class simple-name contains `BatteryMeter`
  / `Battery` / `BatteryIcon`.
- Find the icon container: id `statusIcons` / `status_icons` **or** a class containing
  `StatusIconContainer` (covers `MiuiStatusIconContainer`).
- Pick the **lowest common ancestor** that is a `ViewGroup`, is not the status-bar window itself, is not
  `GONE`, and whose measured width is at least the icon container's. That ancestor is the strip.
- Validate a candidate selected by id too: if it is `isStubStrip`, fall through to the role scan before
  giving up.

Every decision logs one line:
`stripStrategy=<id|anchor|role|compose|tree> container=<class> id=<name> conf=<0-100>`.

`ContainerFinder.findStatusIconsHost` order becomes:
adapter ids (validated) → `stripAroundAnchors` → **`roleScan`** → null. This is purely additive: a ROM
that already resolved at step 1 or 2 behaves exactly as today.

### 2. `RomProfile` as data, not code

Move the adapter contents into bundled JSON (`app/src/main/assets/rom-profiles.json`) with schema
`{ match: {manufacturer, brand, product, sdk, display}, containerIds, batteryId, clockId, classHints,
notes, measured }`. `RomDetection` reads it (falling back to the built-in AOSP default). A profile for a
new ROM is a data edit, and can be delivered as a remote override file later.

### 3. One-report profiling

When `roleScan` resolves an **unknown** ROM, the diagnostic dump gains a ready-to-paste
`romProfile` JSON block (resolved ids + class names + bounds). The next user report then yields a
verified profile with no back-and-forth — the project's evidence rule, automated.

## 80% claim, stated honestly

Most custom ROMs are AOSP-derived and keep either the AOSP ids or the standard class names
(`StatusIconContainer`, `BatteryMeterView`). Role scanning covers those without any profile. ROMs that
rename both ids and classes (deeply skinned One UI / ColorOS builds) still need a measured profile; the
one-report profiling makes that cheap. We will measure the real hit-rate on the phone + GSI emulator and
record it, rather than asserting it.

## Regression safety (the "update broke my status bar" concern)

- Every new strategy is a **fallback**, never a replacement; feature defaults stay identical.
- Ids selected today keep resolving first; a new step only runs when the old ones fail.
- The golden diagnostic-dump diff (`rom-contract.md`) must be unchanged on the baseline ROM after each
  step; a changed dump means the step is not a pure addition.
- Stage gate, kill switch and the Rive crash breaker are untouched.
- New profiles/strategies ship behind the beta channel first.

## Deliverables for M1

1. This design.
2. `stripStrategy` logging + unknown-ROM profile block in `Diag` (supports NFR-06).
3. `ContainerFinder.roleScan` with Robolectric unit tests (AOSP, MIUI, One UI, dead-stub cases).
4. `rom-profiles.json` + loader, with the two measured profiles ported first.
5. Baseline golden dump captured on the OnePlus before/after and diffed.

## Out of scope for M1

- Rive/SELinux static-mode work (M3).
- PR #11 integration (M2).
- The full review (M4) and test matrix expansion (M5).
