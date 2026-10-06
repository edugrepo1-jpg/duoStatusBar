package io.github.kvmy666.duostatusbar

/** Defense in depth for module-owned technical logs. No global log or user-content capture. */
internal object DiagnosticPrivacy {
    // A token boundary and possessive runs prevent quadratic retries on a huge non-email line.
    // Also redact local addresses rather than requiring a public TLD.
    private val email=Regex("(?<![A-Za-z0-9._%+-])[A-Za-z0-9._%+-]++@[A-Za-z0-9.-]++")
    private val mac=Regex("(?i)\\b(?:[0-9a-f]{2}:){5}[0-9a-f]{2}\\b")
    private val ip=Regex("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b")
    private val secret=Regex("(?i)(?:ssid|bssid|android[_ ]?id|imei|serial(?:number)?|account|token|access_hash|file_reference|password|latitude|longitude)\\s*[=:]\\s*[^\\n,;]+")
    private val phone=Regex("(?<![0-9])(?:\\+[0-9]{1,3}[- ]?)?(?:\\(?[0-9]{2,3}\\)?[- ])?[0-9]{4,5}[- ][0-9]{4}(?![0-9])")
    private val userPath=Regex("(?:/storage/emulated/\\d+|/sdcard|/data/(?:user|user_de)/\\d+)[^\\s\\n]*")
    private val url=Regex("https?://[^\\s\\n]+")
    fun clean(text:String):String {
        // Do not split a sensitive token at the capture boundary before matching it.
        // An incomplete final line is discarded only when the source exceeded the cap.
        var safe=if(text.length>512_000) text.take(512_000).substringBeforeLast('\n', "") else text
        for(pattern in listOf(email,mac,ip,secret,phone,userPath,url))safe=pattern.replace(safe,"[redacted]")
        return safe
    }
}
