package io.github.kvmy666.duostatusbar

/** Defense in depth for module-owned technical logs. No global log or user-content capture. */
internal object DiagnosticPrivacy {
    private val email=Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val mac=Regex("(?i)\\b(?:[0-9a-f]{2}:){5}[0-9a-f]{2}\\b")
    private val ip=Regex("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b")
    private val secret=Regex("(?i)(?:ssid|bssid|android[_ ]?id|imei|serial(?:number)?|account|token|access_hash|file_reference|password|latitude|longitude)\\s*[=:]\\s*[^\\n,;]+")
    private val phone=Regex("(?<![0-9])(?:\\+[0-9]{1,3}[- ]?)?(?:\\(?[0-9]{2,3}\\)?[- ])?[0-9]{4,5}[- ][0-9]{4}(?![0-9])")
    private val userPath=Regex("(?:/storage/emulated/\\d+|/sdcard|/data/(?:user|user_de)/\\d+)[^\\s\\n]*")
    private val url=Regex("https?://[^\\s\\n]+")
    fun clean(text:String):String {
        var safe=text.take(512_000)
        for(pattern in listOf(email,mac,ip,secret,phone,userPath,url))safe=pattern.replace(safe,"[redacted]")
        return safe
    }
}
