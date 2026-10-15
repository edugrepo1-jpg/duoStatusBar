package io.github.kvmy666.duostatusbar.standalone

import android.content.Context
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.IconBlacklist

/** Transactional, narrow hiding. A successful write is not proof of OEM visual support. */
internal class RootStockIcons(private val context: Context, private val generation: String) {
    private var owned: List<String>? = null
    private fun journal(data: Bundle? = null): Bundle? = context.contentResolver.call(android.net.Uri.parse("content://${DuoPrefs.AUTHORITY}"), "root_stock_journal", generation, data)?.takeIf {it.containsKey("generation")&&it.containsKey("added")}
    private fun read(): String = Settings.Secure.getString(context.contentResolver, "icon_blacklist").orEmpty().also {
        require(it.length <= 4096 && !it.contains('\n') && !it.contains('\r'))
    }
    fun hideAfterDraw(): Boolean = runCatching {
        check(Process.myUid() == 0)
        val pending = journal() ?: return false
        val previous = pending.getString("generation").orEmpty()
        // Never overwrite an unrestored generation's ownership.
        if (previous.isNotEmpty() && previous != generation) return false
        val before = read()
        val added = if (previous == generation) IconBlacklist.parse(pending.getString("added")) else IconBlacklist.KEYS.filterNot { it in IconBlacklist.parse(before) }
        val saved = journal(Bundle().apply { putString("added", added.joinToString(",")) }) ?: return false
        if (saved.getString("generation") != generation) return false
        owned = added
        val desired = IconBlacklist.withKeys(before)
        if (!Settings.Secure.putString(context.contentResolver, "icon_blacklist", desired) || !IconBlacklist.hasKeys(read())) {
            restore(); return false
        }
        true
    }.getOrDefault(false)
    fun restore(): Boolean = runCatching {
        check(Process.myUid() == 0)
        // Restore SecureSettings before contacting a possibly stalled app provider during teardown.
        val added = owned ?: run {
            val saved = journal() ?: return false
            if (saved.getString("generation") != generation) return true
            IconBlacklist.parse(saved.getString("added")).filter { it in IconBlacklist.KEYS }
        }
        val desired = IconBlacklist.withoutKeys(read(), added)
        if (!Settings.Secure.putString(context.contentResolver, "icon_blacklist", desired)) return false
        if (IconBlacklist.parse(read()).any { it in added }) return false
        val response = journal(Bundle().apply { putBoolean("clear", true) }) ?: return false
        val cleared = response.getString("generation").isNullOrEmpty()
        if (cleared) owned = null
        cleared
    }.getOrDefault(false)
    companion object {
        @JvmStatic fun recover(context: Context, generation: String): Boolean = RootStockIcons(context, generation).restore()
    }
}
