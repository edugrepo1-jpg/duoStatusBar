package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import android.content.Intent
import android.os.BadParcelableException
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class SecurityDeepAuditTest {
    private val app:Context=ApplicationProvider.getApplicationContext()
    @Test fun `malformed exported report is rejected without crashing or overwriting status`() {
        DuoPrefs.writeStatus(app,"confirmed")
        val malformed=object:Intent(SettingsBridge.ACTION_STATUS_PUSH) {
            override fun getStringExtra(name:String?):String? { throw BadParcelableException("synthetic malformed IPC") }
        }
        StatusBridgeReceiver().onReceive(app,malformed)
        assertEquals("confirmed",DuoPrefs.status(app))
    }
    private fun write(service:IShellService,hide:Boolean):Boolean {
        val method=StockIconHider::class.java.getDeclaredMethod("writeBlacklist",IShellService::class.java,Boolean::class.javaPrimitiveType)
        method.isAccessible=true
        return method.invoke(StockIconHider,service,hide) as Boolean
    }
    private class FakeShell(var value:String):IShellService.Stub() {
        val commands=mutableListOf<String>()
        override fun destroy() {}
        override fun exec(command:String):String {
            commands.add(command)
            return if(command.startsWith("settings get")) value else ""
        }
    }
    @Test fun `a quote from shared system setting cannot escape its shell argument`() {
        val service=FakeShell("marker'; echo synthetic; #")
        write(service,true)
        val command=service.commands.single {it.startsWith("settings put")}
        assertEquals("settings put secure icon_blacklist 'marker'\"'\"'; echo synthetic; #,wifi,mobile,battery'",command)
    }
    @Test fun `failed settings read never overwrites existing blacklist or reports restoration success`() {
        val service=FakeShell("ERROR: shell timed out")
        assertFalse(write(service,false))
        assertTrue(service.commands.none {it.startsWith("settings put")||it.startsWith("settings delete")})
    }
    @Test fun `multiline error cannot become a blacklist entry`() {
        val service=FakeShell("SecurityException: denied\nnull")
        assertFalse(write(service,true))
        assertEquals(1,service.commands.size)
    }
    @Test fun `readback missing unrelated icons is a failure even if duo slots were added`() {
        val service=object:IShellService.Stub() {
            var reads=0
            override fun destroy() {}
            override fun exec(command:String):String = if(command.startsWith("settings get")) {
                if(reads++==0) "alarm,bluetooth" else "wifi,mobile,battery"
            } else ""
        }
        assertFalse(write(service,true))
    }
}
