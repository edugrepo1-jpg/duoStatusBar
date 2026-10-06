package io.github.kvmy666.duostatusbar.settings

import android.content.Context

/**
 * Auto Expand's action vocabulary, copied from its `ZoneAction.ALL`.
 *
 * Copied, not imported: the two modules are separate APKs and neither depends on the other at build time.
 * The keys *are* the contract — they travel as `zone_action_key` in Auto Expand's privileged broadcast — so
 * if its list ever changes this one must follow. Keeping it in one file (rather than as literals scattered
 * through the UI) is what makes that a one-line change.
 *
 * The list is deliberately short: the element is a status-bar widget, so the actions it offers are the
 * toggles that read as "change what the element is showing" — Wi-Fi, DND, airplane and power saving —
 * plus "No Action". Actions that need extra data (`open_app`, `launch_shortcut`, `open_snapper_history`)
 * cannot be expressed by a key alone and were never offered; the rest (media, volume, brightness,
 * screenshot, recents, flashlight …) are left to Auto Expand's own gesture zones rather than duplicated
 * here, so the two modules cannot disagree about what a tap means.
 */
object DuoActions {

    const val AUTO_EXPAND_PACKAGE = "io.github.kvmy666.autoexpand"

    val ALL: List<Pair<String, String>> = listOf(
        "no_action" to "Sem ação",
        "toggle_wifi" to "Ligar/desligar o Wi-Fi",
        "toggle_dnd" to "Ligar/desligar o Não Perturbe",
        "toggle_airplane_mode" to "Ligar/desligar o Modo Avião",
        "toggle_power_saver" to "Ligar/desligar a Economia de bateria"
    )

    fun label(key: String): String = ALL.firstOrNull { it.first == key }?.second ?: key

    fun isAutoExpandInstalled(context: Context): Boolean = try {
        context.packageManager.getApplicationInfo(AUTO_EXPAND_PACKAGE, 0)
        true
    } catch (_: Throwable) {
        false
    }
}
