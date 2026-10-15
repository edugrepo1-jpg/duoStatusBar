package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import io.github.kvmy666.duostatusbar.L

/** Only DuoIconHost in the LSPosed process loads this adapter. */
internal object XposedBiometricObserver {
    fun install(context:Context,callback:()->Unit):()->Unit {
        val hooks=mutableListOf<XC_MethodHook.Unhook>()
        val monitor=runCatching {Class.forName("com.android.keyguard.KeyguardUpdateMonitor",false,context.classLoader)}.getOrNull()
        if(monitor!=null)for(cls in listOf(monitor)+monitor.declaredClasses)for(method in cls.declaredMethods) {
            if(method.name.matches(Regex("^(handle|on)(Fingerprint|Face|Biometric)Authenticated$"))||method.name=="onAuthenticationSucceeded") {
                L.guard("gancho biométrico") {
                    hooks+=XposedBridge.hookMethod(method,object:XC_MethodHook() {
                        override fun afterHookedMethod(param:MethodHookParam) {callback()}
                    })
                }
            }
        }
        return {hooks.forEach {hook->L.guard("remover gancho") {hook.unhook()}};hooks.clear()}
    }
}
