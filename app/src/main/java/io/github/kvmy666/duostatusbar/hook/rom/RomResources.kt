package io.github.kvmy666.duostatusbar.hook.rom

import android.content.Context

/**
 * Resolves a SystemUI resource id against the ROM adapter's package, so every call site goes through
 * one place and an unreadable resources object yields 0 instead of throwing.
 */
internal object RomResources {

    /** The id for a resource [name] in [rom]'s SystemUI package, or 0 when it cannot be resolved. */
    fun id(context: Context, rom: RomAdapter, name: String): Int = try {
        context.resources.getIdentifier(name, "id", rom.systemUiPackage)
    } catch (_: Throwable) {
        0
    }
}
