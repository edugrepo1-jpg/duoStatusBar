package io.github.kvmy666.duostatusbar.hook

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.hook.rom.RomDetection
import io.github.kvmy666.duostatusbar.hook.rom.RomResources
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Phase 3 entry point for the SystemUI process.
 *
 * Finds the status bar the same OEM-agnostic way the Phase 0 probe did — by watching
 * `WindowManagerImpl.addView` for the window whose layout params say `TYPE_STATUS_BAR` — then hands
 * that view to [DuoIconHost] and starts [DuoStateMonitor].
 *
 * The status bar is inflated a few seconds into boot, so the attach is retried on a short schedule;
 * if it never succeeds nothing is hidden and the stock status bar is untouched (FR-21).
 */
class DuoHook(private val lp: XC_LoadPackage.LoadPackageParam) {

    private val handler = Handler(Looper.getMainLooper())
    private val attaching = AtomicBoolean(false)

    /** The status-bar touch hook is installed once per process. */
    private val touchHooked = AtomicBoolean(false)

    /** The debug diagnostic dump is written to the log once per process, not on every settings change. */
    private val diagnosticsLogged = AtomicBoolean(false)

    /**
     * A context to read the settings and register the settings receiver with. It is the [Application]
     * once one exists, and the status-bar window's own context on a ROM where the module is injected
     * after the Application was created (see [start]).
     */
    private var app: Context? = null
    private var host: DuoIconHost? = null
    private var monitor: DuoStateMonitor? = null
    private var statusBarRoot: View? = null

    /**
     * The keyguard/shade window (FR-03b). On the lock screen the `StatusBar` window still draws our
     * element, but the keyguard's own status bar is a *second* bar in this window and sits on top of
     * it, so its stock icons have to be hidden too or the lock screen shows both.
     */
    private var shadeRoot: View? = null

    /** The pulled-down shade's header, handed over by [hookShadeHeader]. */
    private var shadeHeader: View? = null

    /** The ROM adapter, used only to resolve resource ids against SystemUI's package. */
    private val rom = RomDetection.forThisRom(
        Build.MANUFACTURER.orEmpty(),
        Build.BRAND.orEmpty(),
        Build.PRODUCT.orEmpty(),
        Build.DISPLAY.orEmpty()
    )

    /**
     * The debounced "settings changed" apply. A drag on the app's size/position slider broadcasts on
     * every tick; running the whole re-read for each one puts a burst of binder calls on System UI's
     * main thread. Waiting for the burst to settle keeps the bar responsive and, more importantly,
     * keeps the watchdog from restarting System UI.
     */
    private val settingsApply = Runnable {
        val ctx = app ?: return@Runnable
        L.guard("DuoHook settings changed") {
            val stage = DuoGuard(ctx).stage()
            val settings = host?.refreshSettings()
            when {
                stage == DuoGuard.OFF -> host?.teardown()
                host?.duo == null -> scheduleAttach(attempt = 0)
                else -> monitor?.refresh()
            }
            report(ctx, stage, settings)
        }
    }

    fun install() {
        L.guard("DuoHook install") {
            // LSPosed calls handleLoadPackage once per package in scope; on HyperOS System UI is seen
            // twice (`com.android.systemui` and `system`). Hooking everything twice would double every
            // callback, so only the first load in the process does anything.
            if (!installed.compareAndSet(false, true)) {
                L.i("already installed in this process - skipping duplicate load of ${lp.packageName}")
                return@guard
            }
            L.i("=== Duo Status Bar ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE}) ===")
            // The window hook goes in *now*, not from Application.onCreate. On HyperOS 16 and One UI the
            // Application is created before LSPosed injects the module, so an Application.onCreate hook
            // fires too late (or never) and the status-bar window - already added - was never seen. The
            // users' logs show exactly that: "MainHook loaded" then silence, and the element never drew.
            // Watching addView from the first instant of the process catches the window on every ROM.
            hookWindowManagerAddView()
            hookApplication()
            // If the Application already exists (injected late), start now; otherwise Application.onCreate
            // calls [start] the moment it fires, and [bootstrap] retries in case neither is ready yet.
            adoptExistingStatusBarWindow()
            startWithCurrentApplication()
            bootstrap()
        }
    }

    /** The single host for this process, created on first use. */
    private fun ensureHost(ctx: Context): DuoIconHost {
        host?.let { return it }
        return DuoIconHost(ctx).also { host = it }
    }

    /**
     * Bootstraps from the Application that is already alive, if there is one.
     *
     * This is the other half of the early-attach fix: when the module is injected after the Application
     * was created, `Application.onCreate` never runs again, so the heartbeat, the settings receiver and
     * the extra hooks would never be installed. `AndroidAppHelper.currentApplication()` is the Xposed
     * API for exactly this case; it is null when a normal onCreate is still coming.
     */
    private fun startWithCurrentApplication() {
        L.guard("DuoHook bootstrap") {
            val ctx = resolveAppContext()
            if (ctx != null) {
                L.i("Application already created at injection - starting now")
                start(ctx)
            }
        }
    }

    /**
     * Resolves a usable Application/context for the SystemUI process.
     *
     * `AndroidAppHelper.currentApplication()` is the documented route, but on a framework that injects the
     * module after `Application.onCreate` it can be null (issue #9, Vector). Two reflection fallbacks read
     * the context straight off `ActivityThread`, and the status-bar window's own context is the last resort.
     */
    private fun resolveAppContext(): Context? {
        try {
            android.app.AndroidAppHelper.currentApplication()?.let { return it }
        } catch (t: Throwable) {
            L.w("currentApplication unreadable: ${t.javaClass.simpleName}: ${t.message}")
        }
        try {
            val thread = Class.forName("android.app.ActivityThread")
            val current = thread.getMethod("currentActivityThread").invoke(null)
            if (current != null) {
                // Note: `mSystemContext` is deliberately NOT used. Its package is "android" while the
                // process uid is SystemUI, so a ContentResolver call from it is rejected with
                // "Given calling package android does not match caller's uid" - which broke both the
                // Settings.Global override and the settings provider read. Only contexts whose package is
                // the app are usable here.
                val field = thread.getDeclaredField("mInitialApplication").also { it.isAccessible = true }
                (field.get(current) as? Context)?.let { if (it.packageName != "android") return it }
                val bound = thread.getDeclaredField("mBoundApplication").also { it.isAccessible = true }
                    .get(current)
                if (bound != null) {
                    val app = bound.javaClass.getDeclaredField("appContext").also { it.isAccessible = true }
                        .get(bound) as? Context
                    if (app != null && app.packageName != "android") return app
                }
            }
        } catch (t: Throwable) {
            L.w("ActivityThread context unreadable: ${t.javaClass.simpleName}: ${t.message}")
        }
        return null
    }

    /**
     * The Application may only be created a moment after the module loads, so a failed resolve is retried
     * briefly instead of giving up - the alternative is the "MainHook loaded" then silence that made the
     * app report the module as never injected.
     */
    private fun bootstrap(attempt: Int = 0) {
        if (started.get()) return
        val ctx = resolveAppContext() ?: statusBarRoot?.context
        if (ctx != null) {
            start(ctx)
            return
        }
        if (attempt >= MAX_BOOTSTRAP_ATTEMPTS) {
            L.w("no Application context after $MAX_BOOTSTRAP_ATTEMPTS attempts - waiting for the status-bar window")
            return
        }
        handler.postDelayed({
            L.guard("DuoHook bootstrap retry") { bootstrap(attempt + 1) }
        }, BOOTSTRAP_RETRY_MS)
    }

    /**
     * Adopts a status-bar window that was added before the module was injected.
     *
     * When injection is late, `WindowManagerImpl.addView` already fired and the addView hook will never see
     * the window, so it is read back from `WindowManagerGlobal.mViews` - the process's own list of added
     * windows - by its layout-params type.
     */
    private fun adoptExistingStatusBarWindow() {
        if (statusBarRoot != null) return
        L.guard("DuoHook existing window") {
            val found = findExistingStatusBarWindow() ?: return@guard
            statusBarRoot = found
            L.i("existing status bar window found: ${found.javaClass.name}")
            scheduleAttach(attempt = 0)
            // Now that a SystemUI-owned context exists, the bootstrap can succeed even when the
            // ActivityThread lookups did not.
            bootstrap()
        }
    }

    private fun findExistingStatusBarWindow(): View? = try {
        val cls = Class.forName("android.view.WindowManagerGlobal")
        val instance = cls.getMethod("getInstance").invoke(null)
        val roots = cls.getDeclaredField("mViews").also { it.isAccessible = true }.get(instance) as? List<*>
        roots?.firstNotNullOfOrNull { root ->
            val view = when (root) {
                is View -> root
                null -> null
                else -> try {
                    root.javaClass.getDeclaredField("mView").also { it.isAccessible = true }
                        .get(root) as? View
                } catch (_: Throwable) {
                    null
                }
            }
            val type = (view?.layoutParams as? android.view.WindowManager.LayoutParams)?.type
            if (view != null && (type == TYPE_STATUS_BAR || type == TYPE_NOTIFICATION_SHADE)) view else null
        }
    } catch (t: Throwable) {
        L.w("existing window scan: ${t.javaClass.simpleName}: ${t.message}")
        null
    }

    /**
     * Everything that needs a live context. Runs once per process, from whichever of
     * [startWithCurrentApplication] or `Application.onCreate` happens first.
     */
    private fun start(ctx: Context) {
        if (!started.compareAndSet(false, true)) return
        app = ctx
        L.guard("DuoHook start") {
            // The gate is read before anything is hooked: while the module is off it leaves no trace in
            // this process at all, so a fresh install cannot affect the status bar until asked to.
            val guard = DuoGuard(ctx)
            val stage = guard.stage()
            // Heartbeat before the gate: it is how the app tells "LSPosed never injected the module"
            // apart from "the module ran but is switched off". Two signals: a Settings.Global stamp, and
            // a provider report. The provider needs no permission, so the About screen cannot show a
            // false "never" on a ROM that denies SystemUI WRITE_SECURE_SETTINGS.
            L.guard("DuoHook heartbeat") {
                guard.noteLoaded()
                DuoSettingsClient.report(ctx, "loaded · stage=$stage")
                DuoSettingsClient.reportFallback(ctx, "")
            }
            if (stage == DuoGuard.OFF) {
                L.i("gated off - nothing hooked. Enable with: ${guard.enableHint}, then restart SystemUI")
                return@guard
            }
            L.i("application ready: ${ctx.packageName} (stage $stage)")
            ensureHost(ctx)
            hookShadeHeader()
            hookStatusIconContainer()
            hookBarAppearance()
            hookSettingsChanges(ctx)
            // The window may already have been captured by the early addView hook; attach now.
            statusBarRoot?.let { scheduleAttach(attempt = 0) }
        }
    }

    private fun hookApplication() {
        L.guard("DuoHook hook Application") {
            val callback = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    L.guard("DuoHook Application.onCreate") {
                        start(param.thisObject as Application)
                    }
                }
            }
            // The base class covers every ROM whose Application calls super.onCreate(). The concrete
            // SystemUI Applications are hooked too, for GSI/Vector ROMs (issue #9, PR #8) where the
            // framework can bypass the base `android.app.Application.onCreate` lifecycle. Each name is
            // optional - a ROM that does not have it is simply skipped - and `start` runs once regardless
            // of how many fire (it is guarded by a process-wide flag).
            for (name in listOf(
                "android.app.Application",
                "com.android.systemui.SystemUIApplication",
                "com.android.systemui.MiuiSystemUIApplication"
            )) {
                try {
                    XposedHelpers.findAndHookMethod(name, lp.classLoader, "onCreate", callback)
                    L.i("Application.onCreate hook installed on $name")
                } catch (_: Throwable) {
                    // Not present on this ROM.
                }
            }
        }
    }

    /**
     * The app's half of the channel. When the settings screen writes something it broadcasts, and this
     * re-reads and applies it live: size and offset change without re-injecting anything, switching off puts
     * the stock icons back exactly as they were, and switching on re-attaches.
     */
    private fun hookSettingsChanges(ctx: Context) {
        L.guard("DuoHook settings receiver") {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context?, intent: Intent?) {
                    // The size only takes effect on a fresh start, so the app asks for one here. This
                    // module is the only side that can do it: it lives inside System UI, and killing its
                    // own process makes Android bring System UI straight back.
                    if (intent?.action == DuoPrefs.ACTION_RESTART_SYSTEMUI) {
                        L.i("restart requested by the app - restarting System UI")
                        handler.postDelayed({
                            try {
                                android.os.Process.killProcess(android.os.Process.myPid())
                            } catch (t: Throwable) {
                                L.w("restart failed: ${t.javaClass.simpleName}: ${t.message}")
                            }
                        }, RESTART_DELAY_MS)
                        return
                    }
                    // Coalesce a burst of changes (a slider drag broadcasts on every tick) into one
                    // apply. Each apply does synchronous provider reads on System UI's main thread, so
                    // without this a drag could block it and get System UI restarted by the watchdog.
                    handler.removeCallbacks(settingsApply)
                    handler.postDelayed(settingsApply, SETTINGS_DEBOUNCE_MS)
                }
            }
            ctx.registerReceiver(
                receiver,
                IntentFilter().apply {
                    addAction(DuoSettingsClient.ACTION_SETTINGS_CHANGED)
                    addAction(DuoPrefs.ACTION_RESTART_SYSTEMUI)
                },
                Context.RECEIVER_EXPORTED
            )
            L.i("listening for app settings changes")
        }
    }

    /** Tells the app what the module is actually doing, so diagnostics shows facts, not intentions. */
    private fun report(ctx: Context, stage: Int, settings: ModuleSettings?) {
        L.guard("DuoHook status report") {
            val element = host?.duo
            val renderer = element?.rendererName ?: "none"
            val status = buildString {
                append("stage=").append(stage)
                append(" · renderer=").append(renderer)
                append(" · attached=").append(element != null)
                settings?.let {
                    append(" · size=").append(it.sizePercent).append('%')
                    append(" · offset=").append(it.offsetX).append("dp")
                    append(" · percent=").append(it.showPercent)
                    append(" · rev=").append(it.revision)
                }
                append(" · riveAttempts=").append(DuoGuard(ctx).attempts())
            }
            L.i("status -> app: $status")
            DuoSettingsClient.report(ctx, status)
            reportDiagnostics(ctx, stage, settings, element)
        }
    }

    /**
     * Sends the full diagnostic dump to the app (so its "Save status to a file" button ships it) and
     * writes it to the log once per process. Always on — not debug-only — so any release user can pull a
     * complete bug report without a special build.
     */
    private fun reportDiagnostics(ctx: Context, stage: Int, settings: ModuleSettings?, element: DuoElement?) {
        val dump = Diag.collect(ctx, statusBarRoot, stage, settings, element)
        DuoSettingsClient.reportDump(ctx, dump)
        if (diagnosticsLogged.compareAndSet(false, true)) {
            L.i("--- diagnostic dump (debug build) ---")
            Diag.log(dump)
            L.i("--- end diagnostic dump ---")
        }
    }

    private fun hookWindowManagerAddView() {
        val callback = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                L.guard("DuoHook addView") {
                    val view = param.args.getOrNull(0) as? View ?: return
                    val layoutParams = view.layoutParams ?: return
                    when (XposedHelpers.getIntField(layoutParams, "type")) {
                        TYPE_STATUS_BAR -> {
                            if (statusBarRoot != null) return
                            statusBarRoot = view
                            L.i("status bar window found: ${view.javaClass.name}")
                            scheduleAttach(attempt = 0)
                        }
                        TYPE_NOTIFICATION_SHADE -> {
                            if (shadeRoot != null) return
                            shadeRoot = view
                            L.i("keyguard/shade window found: ${view.javaClass.name} (FR-03b)")
                        }
                    }
                }
            }
        }
        L.guard("DuoHook hook addView") {
            XposedHelpers.findAndHookMethod(
                "android.view.WindowManagerImpl", lp.classLoader, "addView",
                View::class.java, ViewGroup.LayoutParams::class.java, callback
            )
        }
    }

    /** The bar inflates its children over a few seconds; retry until the icon strip exists. */
    private fun scheduleAttach(attempt: Int) {
        if (attempt > MAX_ATTEMPTS) {
            L.i("giving up after $MAX_ATTEMPTS attempts - status bar left untouched")
            // A ROM the module could not attach on must still leave evidence: send the same diagnostic
            // dump as a successful attach, so a report names the ids/probes that were tried. Without
            // this, the Samsung One UI user's report was just "loaded · stage=2" with nothing to act on.
            val ctx = app ?: statusBarRoot?.context
            if (ctx != null) {
                L.guard("DuoHook attach failed report") {
                    DuoSettingsClient.report(ctx, "attach failed after $MAX_ATTEMPTS attempts - see the diagnostic dump")
                    reportDiagnostics(ctx, DuoGuard(ctx).stage(), null, host?.duo)
                }
            }
            return
        }
        handler.postDelayed({
            L.guard("DuoHook attach #$attempt") {
                val root = statusBarRoot ?: return@guard
                // Prefer the Application, but fall back to the bar window's own context: on a ROM where
                // the module is injected late the Application may never have been captured.
                val ctx = app ?: root.context
                val attached = ensureHost(ctx).attach(root)
                if (attached) {
                    if (monitor == null) {
                        monitor = DuoStateMonitor(ctx, host!!).also { it.start() }
                        // Keep the state fresh without polling: a cheap re-read on every layout pass.
                        attachLayoutListener(root)
                    }
                    if (!attaching.compareAndSet(false, true)) return@guard
                    // The keyguard and the shade header may already be on screen (the element attaches
                    // at boot, they come later, but a re-attach after rotation can land either way).
                    shadeRoot?.let { shade -> attachExtraBars(shade) }
                    hookStatusBarTouch(root)
                    L.i("Duo attached on attempt $attempt")
                    report(ctx, DuoGuard(ctx).stage(), null)
                } else {
                    scheduleAttach(attempt + 1)
                }
            }
        }, if (attempt == 0) FIRST_DELAY_MS else RETRY_MS)
    }

    /**
     * FR-05/18: drives the element's own tap gestures from the status bar's touch stream.
     *
     * The status bar consumes touches before they reach the injected element view, so its OnTouch
     * listener never fired and the element's configured actions did nothing - only Auto Expand's edge
     * zones reacted. This hooks `dispatchTouchEvent` on the bar (the same layer Auto Expand uses) and
     * lets [DuoIconHost.handleElementTouch] claim only the touches that land on the element. Auto
     * Expand's zones yield to those touches, so one tap means one action.
     *
     * The runtime class is checked for a *declared* override: hooking an inherited
     * `ViewGroup.dispatchTouchEvent` would intercept every touch in the process.
     */
    private fun hookStatusBarTouch(root: View) {
        if (!touchHooked.compareAndSet(false, true)) return
        L.guard("DuoHook status bar touch") {
            // `dispatchTouchEvent` is inherited from ViewGroup, so this hooks the base method and the
            // identity guard below keeps it to the status-bar window only (hooking without the guard
            // would run for every touch in the process).
            val method = try {
                XposedHelpers.findMethodExact(
                    root.javaClass, "dispatchTouchEvent", android.view.MotionEvent::class.java
                )
            } catch (t: Throwable) {
                L.w("no dispatchTouchEvent on ${root.javaClass.simpleName}: ${t.message}")
                return@guard
            }
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.thisObject !== root) return
                    try {
                        val event = param.args.getOrNull(0) as? android.view.MotionEvent ?: return
                        host?.handleElementTouch(event)
                    } catch (t: Throwable) {
                        L.w("element touch: ${t.javaClass.simpleName}: ${t.message}")
                    }
                }
            })
            L.i("status bar touch hook installed (${root.javaClass.simpleName}) - FR-05/18")
        }
    }

    private fun attachLayoutListener(root: View) {
        L.guard("DuoHook layout listener") {
            root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                L.guard("DuoHook onLayout") {
                    // Hide pass only. Do NOT re-read system state here: the status bar re-lays out on
                    // every clock tick, and refreshing Wi-Fi/cell/etc plus re-rendering Rive on each one
                    // kept SystemUI awake at ~1 Hz — the battery drain. State is already broadcast-driven.
                    host?.reapplyHiding()
                }
            }
            // The keyguard's bar is inflated into the shade window when the lock screen appears, and the
            // shade window is the one that changes then - so its layout pass is the trigger for the
            // second hiding pass (FR-03b).
            val shade = shadeRoot
            shade?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                L.guard("DuoHook onShadeLayout") {
                    attachExtraBars(shade)
                    // Opening the shade re-shows the main bar's icon views, so the hide pass has to run
                    // again here - the status bar's own layout pass does not fire for a shade drag. The
                    // ROM also re-shows them *after* the drag settles, so this runs again a moment later.
                    host?.reapplyHiding()
                    handler.postDelayed({
                        L.guard("DuoHook shade settle") {
                            host?.reapplyHiding()
                            shadeHeader?.let { host?.attachShadeHeader(it) }
                        }
                    }, SHADE_SETTLE_MS)
                }
            }
        }
    }

    /**
     * FR-03b: the two extra bars that carry their own icon strip, both inside the shade window. The ids
     * are read out of the device's SystemUI (`reverse/SystemUI-device.apk`): the lock screen's
     * `KeyguardStatusBarView` uses `system_icons`, and the pulled-down shade's header
     * (`combined_qs_header`) uses `shade_header_system_icons`. Both draw their own stock icons, which is
     * why the lock screen and the shade looked untouched until they were handled.
     */
    private fun attachExtraBars(shade: View) {
        val host = host ?: return
        host.attachExtra("keyguard bar", shade, "system_icons")
        // The shade header is not in this window; it arrives through hookShadeHeader. It is built early,
        // often before the main bar has attached, so this is also where a failed attempt is retried.
        shadeHeader?.let { host.attachShadeHeader(it) }
    }

    /**
     * FR-03b: the pulled-down shade's header.
     *
     * Decompiled from the device's SystemUI rather than guessed:
     * `com.android.systemui.qs.dagger.OplusQSModuleEx.providesShadeHeaderView` takes the shade window,
     * finds the `qs_header_stub` ViewStub inside it, sets it to `R.layout.combined_qs_header` and
     * inflates it - returning the header view. So the header *is* in the shade window's tree, but only
     * after the stub inflates, which is why searching for it at boot found nothing. This takes the
     * returned view, which is the only moment it is handed over directly.
     */
    /**
     * FR-03b: hides icon views as the ROM adds them.
     *
     * A hiding pass on a layout change is not enough on the shade header - the ROM repopulates its
     * `StatusIconContainer` afterwards, so the icons came back after every pass. `StatusIconContainer`
     * is the one container all three bars use for their icons, so hooking its `addView` catches every
     * icon in every bar at the moment it arrives.
     */
    private fun hookStatusIconContainer() {
        L.guard("DuoHook icon container") {
            val names = listOf(
                "com.android.systemui.statusbar.phone.StatusIconContainer",
                "com.android.systemui.statusbar.views.MiuiStatusIconContainer"
            )
            val callback = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    L.guard("DuoHook icon added") {
                        val child = param.args.firstOrNull() as? View ?: return@guard
                        host?.onStatusIconAdded(child)
                    }
                }
            }
            var hooked = 0
            for (name in names) {
                try {
                    val cls = XposedHelpers.findClass(name, lp.classLoader)
                    XposedBridge.hookAllMethods(cls, "addView", callback)
                    hooked++
                } catch (_: Throwable) {
                }
            }
            L.i("status icon container hooks installed: $hooked")
        }
    }

    /**
     * Captures the colour SystemUI tints its own icons, so the Duo element can match the bar (black on a
     * light bar, white on a dark one) - the "chameleon" behaviour across apps.
     *
     * Both entry points are best-effort: `onDarkChanged(ArrayList, float, int)` is the AOSP one and
     * `setIconColor(int, boolean)` the spellings OEM builds added around it. When neither exists the
     * element falls back to the system day/night setting (see [BarTint]).
     */
    private fun hookBarAppearance() {
        L.guard("DuoHook bar appearance") {
            val cls = XposedHelpers.findClass(
                "com.android.systemui.statusbar.StatusBarIconView", lp.classLoader
            )
            val callback = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        // Both methods carry exactly one int: `onDarkChanged`'s tint and `setIconColor`'s
                        // colour. The first int in the argument list is that value either way.
                        for (arg in param.args) {
                            if (arg is Int) {
                                if (BarTint.update(arg)) monitor?.onBarAppearanceChanged()
                                break
                            }
                        }
                    } catch (t: Throwable) {
                        L.w("bar tint: ${t.javaClass.simpleName}: ${t.message}")
                    }
                }
            }
            XposedBridge.hookAllMethods(cls, "onDarkChanged", callback)
            XposedBridge.hookAllMethods(cls, "setIconColor", callback)
            L.i("bar appearance hook installed (StatusBarIconView) - FR-15b")
        }
    }

    private fun hookShadeHeader() {        // The controller that owns the header is the reliable hand-over: it is handed the header view
        // directly, whatever inflated it.
        L.guard("DuoHook shade header controller") {
            val cls = XposedHelpers.findClass(
                "com.android.systemui.shade.ShadeHeaderController", lp.classLoader
            )
            XposedBridge.hookAllConstructors(cls, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    L.guard("DuoHook shade header ctor") {
                        val view = param.args.firstOrNull() as? View ?: return@guard
                        L.i("shade header view: ${view.javaClass.name}")
                        shadeHeader = view
                        view.post { shadeHeader?.let { host?.attachShadeHeader(it) } }
                    }
                }
            })
        }
        L.guard("DuoHook shade header") {
            XposedHelpers.findAndHookMethod(
                "android.view.ViewStub", lp.classLoader, "inflate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        L.guard("DuoHook stub inflate") {
                            val view = param.result as? View ?: return@guard
                            val ctx = app ?: return@guard
                            val id = RomResources.id(ctx, rom, "shade_header_system_icons")
                            if (id == 0 || view.findViewById<View>(id) == null) return@guard
                            L.i("shade header inflated from a stub: ${view.javaClass.name}")
                            shadeHeader = view
                            view.post { shadeHeader?.let { host?.attachShadeHeader(it) } }
                        }
                    }
                }
            )
        }
    }

    private companion object {
        /**
         * Per-process, not per-instance: LSPosed can call `handleLoadPackage` twice in one System UI
         * process (once for `com.android.systemui`, once for `system`), and every hook must go in once.
         */
        val installed = AtomicBoolean(false)

        /** Set by whichever of the Application bootstrap paths runs first, so [start] runs once. */
        val started = AtomicBoolean(false)

        const val TYPE_STATUS_BAR = 2000

        /** `WindowManager.LayoutParams.TYPE_NOTIFICATION_SHADE`, which is @hide. */
        const val TYPE_NOTIFICATION_SHADE = 2040
        const val FIRST_DELAY_MS = 2_500L

        /** How long a late injection waits for the Application before it relies on the bar's context. */
        const val BOOTSTRAP_RETRY_MS = 500L
        const val MAX_BOOTSTRAP_ATTEMPTS = 20

        /** After a shade drag settles, the ROM re-shows its icon views once more. */
        const val SHADE_SETTLE_MS = 400L

        /** Give the restart broadcast a moment to finish before the process goes. */
        const val RESTART_DELAY_MS = 300L

        /** How long a burst of settings changes is allowed to settle before one apply runs. */
        const val SETTINGS_DEBOUNCE_MS = 250L
        const val RETRY_MS = 2_000L
        const val MAX_ATTEMPTS = 6
    }
}
