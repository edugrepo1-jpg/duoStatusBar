package io.github.kvmy666.duostatusbar.hook
import io.github.kvmy666.duostatusbar.DiagnosticPrivacy
/** Keep head facts and most recent events below Binder's shared transaction budget. */
internal object DiagnosticTransport {
    fun prepare(raw:String):String {
        if(raw.length<=128_000)return DiagnosticPrivacy.clean(raw)
        val head=raw.take(48_000).substringBeforeLast('\n', "")
        val tail=raw.takeLast(79_000).substringAfter('\n', "")
        return DiagnosticPrivacy.clean(head+"\n[older diagnostic lines omitted]\n"+tail)
    }
}
