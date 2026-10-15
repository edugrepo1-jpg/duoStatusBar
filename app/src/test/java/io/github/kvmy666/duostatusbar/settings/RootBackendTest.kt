package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.standalone.RootRuntimeManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class RootBackendTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    @Before fun clean() {context.getSharedPreferences("duo_runtime",Context.MODE_PRIVATE).edit().clear().commit()}
    private fun health(version:Int=BuildConfig.VERSION_CODE)=Bundle().apply {putString("state","ready");putInt("versionCode",version)}
    @Test fun `launch alone and stale helper cannot certify a running renderer`() {
        assertEquals("module",RuntimeBackend.mode(context))
        val old=RuntimeBackend.begin(context)
        assertFalse(RuntimeBackend.ready(context))
        val current=RuntimeBackend.begin(context)
        assertFalse(RuntimeBackend.report(context,old,health()))
        assertFalse(RuntimeBackend.ready(context))
        assertTrue(RuntimeBackend.report(context,current,health(BuildConfig.VERSION_CODE-1)))
        assertFalse(RuntimeBackend.ready(context))
        assertTrue(RuntimeBackend.report(context,current,health()))
        assertTrue(RuntimeBackend.ready(context))
        RuntimeBackend.stop(context)
        assertFalse(RuntimeBackend.ready(context))
    }
    @Test fun `old cleanup cannot erase a newer stock restoration journal`() {
        val old=RuntimeBackend.begin(context)
        RuntimeBackend.stockJournal(context,old,Bundle().apply {putString("added","wifi,mobile,battery,clock,privacy")})
        val next=RuntimeBackend.begin(context)
        // An unrestored owner is retained; the new process must recover it first.
        val blocked=RuntimeBackend.stockJournal(context,next,Bundle().apply {putString("added","battery")})
        assertEquals(old,blocked.getString("generation"))
        assertEquals("wifi,mobile,battery",blocked.getString("added"))
        RuntimeBackend.stockJournal(context,old,Bundle().apply {putBoolean("clear",true)})
        RuntimeBackend.stockJournal(context,next,Bundle().apply {putString("added","battery")})
        val protected=RuntimeBackend.stockJournal(context,old,Bundle().apply {putBoolean("clear",true)})
        assertEquals(next,protected.getString("generation"))
        assertEquals("battery",protected.getString("added"))
    }
    @Test fun `stopped helper cannot confirm restoration while its journal is pending`() {
        val generation=RuntimeBackend.begin(context)
        RuntimeBackend.stockJournal(context,generation,Bundle().apply {putString("added","battery")})
        RuntimeBackend.report(context,generation,health().apply {putString("state","stopped");putBoolean("stockRestored",true)})
        assertFalse(RuntimeBackend.health(context).getBoolean("stockRestored"))
        RuntimeBackend.stockJournal(context,generation,Bundle().apply {putBoolean("clear",true)})
        assertTrue(RuntimeBackend.health(context).getBoolean("stockRestored"))
    }
    @Test fun `launch rejects arbitrary command arguments and quotes installed apk path`() {
        assertThrows(IllegalArgumentException::class.java) {RootRuntimeManager.launchScript("/data/app/base.apk","x; id")}
        val script=RootRuntimeManager.launchScript("/data/app/O'Name/base.apk","a".repeat(32))
        assertTrue(script.contains("--restore-only"))
        assertFalse(script.contains("pkill"));assertFalse(script.contains("setenforce"))
        assertTrue(script.contains("</dev/null >/dev/null 2>&1"))
    }
    @Test fun `root reports never certify OEM visual hiding or independent panel detection`() {
        val generation=RuntimeBackend.begin(context)
        val input=health().apply {putBoolean("panelTracking",true);putBoolean("stockVisualVerified",true)}
        RuntimeBackend.report(context,generation,input)
        assertFalse(RuntimeBackend.health(context).getBoolean("panelTracking"))
        assertFalse(RuntimeBackend.health(context).getBoolean("stockVisualVerified"))
    }
}
