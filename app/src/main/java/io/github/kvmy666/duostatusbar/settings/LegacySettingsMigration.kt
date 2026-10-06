package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import java.security.MessageDigest
import io.github.kvmy666.duostatusbar.BuildConfig

/** One-time read of the earlier, locally signed Canvas app. No root, logs or permissions migrate. */
internal object LegacySettingsMigration {
    private const val LEGACY="io.github.kvmy666.duostatusbar"
    private const val CANVAS_SIGNER="e6aaa9c405ab2114e6aa59b1c6aec5e94bf942f98d09788180803d7e5acf2be8"
    private val booleans=setOf("enabled","use_rive","show_percent","live_apply","clock_font","animations_enabled","arrival_enabled","departure_enabled","charging_enabled","hide_other_icons","network_only","split_indicators","wifi_dots","show_airplane","show_dnd")
    private val strings=setOf("tap_action","double_tap_action","long_press_action","icon_color","sim_choice","dnd_mode","experience_v1")
    fun import(context:Context):Boolean {
        val p=context.getSharedPreferences("duo_settings",Context.MODE_PRIVATE)
        if(context.packageName!=BuildConfig.APPLICATION_ID || p.contains("revision") || p.getBoolean("recreate_migration_checked",false))return false
        val trusted=runCatching {
            val own=context.packageManager.getPackageInfo(LEGACY,PackageManager.GET_SIGNING_CERTIFICATES)
            own.signingInfo?.apkContentsSigners?.any {
                MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") {b->"%02x".format(b.toInt() and 255)}==CANVAS_SIGNER
            }==true
        }.getOrDefault(false)
        if(!trusted) {p.edit().putBoolean("recreate_migration_checked",true).apply();return false}
        return runCatching {context.contentResolver.query(Uri.parse("content://$LEGACY.settings"),null,null,null,null)?.use {cursor ->
            val imported=importCursor(context,cursor)
            p.edit().putBoolean("recreate_migration_checked",true).putBoolean("recreate_migrated",imported).apply()
            imported
        } ?: false}.getOrDefault(false)
    }
    internal fun importCursor(context:Context,cursor:Cursor):Boolean {
        if(!cursor.moveToFirst())return false
        if(cursor.getColumnIndex(DuoPrefs.COL_EXPERIENCE)<0)return false // Original Rive settings aren't this Canvas fork.
        val p=context.getSharedPreferences("duo_settings",Context.MODE_PRIVATE)
        if(p.contains("revision"))return false
        val edit=p.edit()
        for(name in DuoPrefs.COLUMNS) {
            if(name==DuoPrefs.COL_REVISION)continue
            val index=cursor.getColumnIndex(name);if(index<0 || cursor.isNull(index))continue
            val key=name.removePrefix(DuoPrefs.LAND_PREFIX)
            when(key) {
                in booleans->edit.putBoolean(name,cursor.getInt(index)!=0)
                in strings->edit.putString(name,cursor.getString(index).take(if(key==DuoPrefs.COL_EXPERIENCE)4096 else 200))
                else->edit.putInt(name,cursor.getInt(index))
            }
        }
        edit.putLong("revision",1).putBoolean("landscape_set",cursor.getColumnIndex("land_enabled")>=0).commit()
        // Re-save through canonical clamping before sending anything to SystemUI.
        for(orientation in DuoOrientation.entries)DuoPrefs.write(context,DuoPrefs.read(context,orientation),orientation)
        return true
    }
}
