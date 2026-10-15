package io.github.kvmy666.duostatusbar.fx

/** Known disjoint owners must never be presented as a video call. Unknown origins remain unknown. */
internal object RuntimePrivacyPair {
    fun active(enabled:Boolean,communication:Boolean,camera:Boolean,microphone:Boolean,shared:Boolean,
        cameraSources:Set<String>,microphoneSources:Set<String>):Boolean =
        enabled&&communication&&camera&&microphone&&
            (shared||cameraSources.isEmpty()||microphoneSources.isEmpty())
}
