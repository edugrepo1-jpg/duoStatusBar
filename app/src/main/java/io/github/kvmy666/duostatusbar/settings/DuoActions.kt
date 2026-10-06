package io.github.kvmy666.duostatusbar.settings

import io.github.kvmy666.duostatusbar.i18n.UiText
import android.content.Context

/**
 * Auto Expand's action vocabulary, copied from its `ZoneAction.ALL`.
 *
 * Copied, not imported: the two modules are separate APKs and neither depends on the other at build time.
 * The keys *are* the contract — they travel as `zone_action_key` in Auto Expand's privileged broadcast — so
 * if its list ever changes this one must follow. Keeping it in one file (rather than as literals scattered
 * through the UI) is what makes that a one-line change.
 *
 * The offered list is status-bar-appropriate toggles (Wi-Fi, Bluetooth, mobile data, DND, airplane, power
 * saver, auto-rotate) plus "No Action". Every key here is handled by Auto Expand's privileged receiver for
 * both apps installed together: the four it handles directly (Wi-Fi/Bluetooth/mobile data/power saver) and
 * the rest through its own dispatcher. A key it cannot express stays out of this list, so the element can
 * never offer an action that silently does nothing.
 */
object DuoActions {

    const val AUTO_EXPAND_PACKAGE = "io.github.kvmy666.autoexpand"

    val ALL: List<Pair<String, String>> get() = listOf(
        "no_action" to UiText.t("No Action"),
        "toggle_wifi" to UiText.t("Toggle Wi-Fi"),
        "toggle_bluetooth" to UiText.t("Toggle Bluetooth"),
        "toggle_mobile_data" to UiText.t("Toggle Mobile Data"),
        "toggle_dnd" to UiText.t("Toggle Do Not Disturb"),
        "toggle_airplane_mode" to UiText.t("Toggle Airplane Mode"),
        "toggle_auto_rotate" to UiText.t("Toggle Auto Rotate"),
        "toggle_power_saver" to UiText.t("Toggle Power Saver")
    )

    fun label(key: String): String = ALL.firstOrNull { it.first == key }?.second ?: key

    fun isAutoExpandInstalled(context: Context): Boolean = try {
        context.packageManager.getApplicationInfo(AUTO_EXPAND_PACKAGE, 0)
        true
    } catch (_: Throwable) {
        false
    }
}
