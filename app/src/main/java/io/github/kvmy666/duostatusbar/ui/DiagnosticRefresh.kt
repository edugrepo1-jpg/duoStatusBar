package io.github.kvmy666.duostatusbar.ui

import kotlinx.coroutines.delay

/** Wait only after an explicit export. A cached snapshot is labelled, never presented as fresh. */
internal object DiagnosticRefresh {
    suspend fun await(
        fallback:String,
        read:()->String,
        request:()->Unit,
        wait:suspend ()->Unit={delay(100)},
        attempts:Int=35
    ):String {
        var previous=fallback
        var latest=fallback
        var error:String?=null
        try {
            previous=read().ifEmpty {fallback};latest=previous
            request()
            repeat(attempts.coerceIn(0,50)) {
                wait()
                latest=read().ifEmpty {latest}
                if(latest.isNotBlank()&&latest!=previous)return envelope("fresh_response_observed",latest)
            }
        } catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
        catch(failure:Throwable){error=failure.javaClass.simpleName}
        return envelope(if(latest.isBlank())"missing" else "cached_response_freshness_unconfirmed",latest,error)
    }
    private fun envelope(status:String,dump:String,error:String?=null)=buildString {
        appendLine("=== module capture freshness ===")
        appendLine("refreshStatus=$status rootRequired=false requestedBy=explicit user export")
        if(error!=null)appendLine("refreshError=$error")
        if(dump.isBlank())appendLine("No runtime snapshot received. In module mode check LSPosed scope and injection; in standalone root mode check the explicit start button and root process health. Check enabled state and settings bridge in either mode.")
        else appendLine(dump)
    }
}
