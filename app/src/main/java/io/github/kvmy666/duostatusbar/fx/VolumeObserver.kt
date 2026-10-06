package io.github.kvmy666.duostatusbar.fx

import android.content.*
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.provider.Settings
import io.github.kvmy666.duostatusbar.L

internal class VolumeObserver(private val context:Context,private val handler:Handler,private val changed:(Int)->Unit) {
    private val audio by lazy { context.getSystemService(AudioManager::class.java) }
    private var running=false
    private var registered=false
    private var observed=false
    private val levels=mutableMapOf<Int,Int>()
    private val streams=listOf(AudioManager.STREAM_MUSIC,AudioManager.STREAM_RING,AudioManager.STREAM_VOICE_CALL,AudioManager.STREAM_ALARM,AudioManager.STREAM_NOTIFICATION)
    private val receiver=object:BroadcastReceiver() {
        override fun onReceive(c:Context?,intent:Intent?) {
            if(running&&intent?.action==ACTION)read(intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE",AudioManager.STREAM_MUSIC))
        }
    }
    private val observer=object:ContentObserver(handler) {
        override fun onChange(selfChange:Boolean,uri:Uri?) {
            if(running&&(uri==null||uri.lastPathSegment.orEmpty().startsWith("volume")))streams.forEach(::read)
        }
    }
    fun start() {
        if(running)return
        running=true;streams.forEach { stream -> runCatching { levels[stream]=audio?.getStreamVolume(stream) ?: -1 } }
        try { context.registerReceiver(receiver,IntentFilter(ACTION),null,handler,Context.RECEIVER_EXPORTED);registered=true }
        catch(t:Throwable) { L.w("Volume: aviso indisponível: ${t.javaClass.simpleName}") }
        try { context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI,true,observer);observed=true }
        catch(t:Throwable) { L.w("Volume: leitura alternativa indisponível: ${t.javaClass.simpleName}") }
    }
    private fun read(stream:Int) {
        if(stream !in streams)return
        try {
            val value=audio?.getStreamVolume(stream) ?: return
            val previous=levels.put(stream,value)
            if(previous!=null&&previous!=value) {
                val max=audio?.getStreamMaxVolume(stream) ?: 0
                if(max>0)changed((100f*value/max).toInt().coerceIn(0,100))
            }
        } catch(_:Throwable) { }
    }
    fun stop() {
        running=false
        if(registered){runCatching { context.unregisterReceiver(receiver) };registered=false}
        if(observed){runCatching { context.contentResolver.unregisterContentObserver(observer) };observed=false}
        levels.clear()
    }
    companion object { const val ACTION="android.media.VOLUME_CHANGED_ACTION" }
}
