# ROM contract — what the refactor must not change

Duo is a status-bar module that has to attach, hide icons and draw on ROMs we cannot test. Every
ROM-specific fix so far came from a user's **diagnostic dump**, so the probes, the hide logic and the log
strings are the product's contract. Refactors are **behavior-freezing**: they may move code, name things
better and add tests, but they must not change any of the following.

## Frozen surface

1. **Log strings and the `DuoSB` tag.** The diagnostic dump and the compact status line are what we fix
   ROMs from. No rewording, reordering, merging or removing. New lines may be added only inside the
   existing sections, and never on the attach/hide path.
2. **Container probe order.** `RomAdapter.containerIds` order, then `stripAroundAnchors`
   (`batteryId`, `statusIcons`, `status_icons`, `system_icons`), then `chooseElementParent`,
   `findComposeIconView`, `findBar`, `findShadeIconsArea`.
3. **Reflection targets and their exact names/values.** `ActivityThread.mInitialApplication`,
   `mBoundApplication.appContext` (and **never** `mSystemContext` — its package is `android`, which the
   provider rejects), `WindowManagerGlobal.mViews` / `mView`, window types `2000`/`2040`,
   `TelephonyManager.createForSubscriptionId`, `StatusBarIconView.getSlot`/`getSlotTag`.
4. **Hooked class names.** `android.app.Application`, `com.android.systemui.SystemUIApplication`,
   `com.android.systemui.MiuiSystemUIApplication`, `android.view.WindowManagerImpl.addView`,
   `com.android.systemui.statusbar.phone.StatusIconContainer`,
   `com.android.systemui.statusbar.views.MiuiStatusIconContainer`, `ShadeHeaderController`, `ViewStub`,
   `StatusBarIconView.onDarkChanged` / `setIconColor`.
5. **Hiding semantics.** `GONE` + 0×0 for replaced icons; `INVISIBLE` (keep layout) for the measured
   anchor; never hide a strip the element is not drawing in; `restore()` puts everything back exactly.
6. **Stage precedence.** `duo_statusbar_stage` (adb override) → app settings → off. Keys unchanged.
7. **Settings provider contract.** Authority `io.github.kvmy666.duostatusbar.settings`, and the column set
   and order in `DuoPrefs.COLUMNS` / `DuoSettingsProvider.rowFor`.
8. **Rive.** `DuoBinder` property names and `PROPERTY_COUNT`, `scene.rml` binds, and
   `app/src/main/res/raw/duo.riv` must equal the scene build (CI `cmp`). No Rive changes in the refactor.
9. **Fallback order.** Rive → Canvas; attach retry count/timings; overlay-vs-strip placement.

## Safety procedure for every refactor phase

1. `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` — green.
2. CI guards green: `route-module-logs.py --check`, `check-rive-eases.py`, authority grep, Rive `cmp`.
3. Install on a device, restart System UI, and **diff the diagnostic dump** against the baseline captured
   before the refactor. Expect an empty diff (ignoring timestamps and pids).
4. The dump is the evidence. A refactor that changes the dump is not a refactor.

### Capturing and diffing the dump

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$log = (& $adb shell "su -c 'ls -t /data/adb/lspd/log/modules_*.log | head -1'").Trim()
& $adb shell su 0 cat $log | Set-Content .\modules.log
(Get-Content .\modules.log | Where-Object { $_ -match 'DuoSB \|' } |
    ForEach-Object { ($_ -replace '.*DuoSB \| ', '') }) |
    Set-Content .\diag-after.txt
Compare-Object (Get-Content .\diag-baseline.txt) (Get-Content .\diag-after.txt)
```

Timestamps and pids differ every run; filter them out before comparing (the dump body — build, adapter,
probes, view tree, readers — should be identical).
