package io.github.kvmy666.duostatusbar.fx

import android.content.Context
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.hook.ModuleSettings
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
class RuntimeAuditTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    @After fun reset(){Fx.sync(ModuleSettings.DEFAULT,context)}
    private fun field(owner:Any,key:String)=owner.javaClass.getDeclaredField(key).apply {isAccessible=true}
    private fun get(owner:Any,key:String)=field(owner,key).get(owner)
    private fun set(owner:Any,key:String,value:Any)=field(owner,key).set(owner,value)

    @Test fun `late session callback after disabling all media cannot register another controller`() {
        Fx.sync(ModuleSettings.DEFAULT.copy(featFlags=0,experienceJson=ExperienceOptions().encode()),context)
        val runtime=RuntimeExperience(context,Handler(Looper.getMainLooper()),{},{})
        val session=MediaSession(context,"Late callback")
        runtime.start()
        try {
            val listener=get(runtime,"sessionListener") as MediaSessionManager.OnActiveSessionsChangedListener
            listener.onActiveSessionsChanged(listOf(session.controller))
            assertTrue((get(runtime,"controllers") as List<*>).isEmpty())
            assertFalse(runtime.playbackKnown)
        } finally {runtime.stop();session.release()}
    }

    @Test fun `metadata callback updates displayed track and a pause keeps the track without playing`() {
        Fx.sync(ModuleSettings.DEFAULT.copy(featFlags=8192),context)
        val runtime=RuntimeExperience(context,Handler(Looper.getMainLooper()),{},{})
        val session=MediaSession(context,"Player")
        runtime.start()
        try {
            val controller=session.controller
            val shadow=Shadows.shadowOf(controller)
            val now=SystemClock.elapsedRealtime()
            set(runtime,"controllers",listOf(controller));set(runtime,"sessionPollAt",now)
            shadow.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,1000,1f,now).build())
            val first=MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,"Track A")
                .putLong(MediaMetadata.METADATA_KEY_DURATION,60000).build()
            shadow.setMetadata(first)
            val callback=get(runtime,"callback") as MediaController.Callback
            callback.onMetadataChanged(first)
            assertEquals("Track A",runtime.playback.title)
            assertTrue(runtime.playback.playing)
            val next=MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,"Track B")
                .putLong(MediaMetadata.METADATA_KEY_DURATION,120000).build()
            shadow.setMetadata(next);callback.onMetadataChanged(next)
            assertEquals("Track B",runtime.playback.title)
            val paused=PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED,10000,0f,now).build()
            shadow.setPlaybackState(paused);callback.onPlaybackStateChanged(paused)
            assertFalse(runtime.playback.playing)
            assertTrue(runtime.playbackKnown)
            assertEquals("Track B",runtime.playback.title)
            assertEquals(10000L,runtime.playback.positionMs)
        } finally {runtime.stop();session.release()}
    }

    @Test fun `unchanged callbacks cannot extend the relay heartbeat past thirty seconds since a real send`() {
        val lastRealSend=1000L
        assertEquals(30000L,ExperienceRelayService.heartbeatDelay(lastRealSend,1000L))
        assertEquals(20000L,ExperienceRelayService.heartbeatDelay(lastRealSend,11000L))
        assertEquals(1000L,ExperienceRelayService.heartbeatDelay(lastRealSend,30000L))
        assertEquals(0L,ExperienceRelayService.heartbeatDelay(lastRealSend,31000L))
        assertEquals(0L,ExperienceRelayService.heartbeatDelay(lastRealSend,70000L))
    }

    @Test fun `media control source rejects malformed or excessive identities`() {
        assertEquals("org.example.player",RuntimeExperience.sourceIdentity("org.example.player"))
        assertEquals("",RuntimeExperience.sourceIdentity(null))
        assertEquals("",RuntimeExperience.sourceIdentity("player\nprivate text"))
        assertEquals("",RuntimeExperience.sourceIdentity("x".repeat(201)))
    }
    @Test fun `screenshot provider recovery establishes a baseline instead of dropping every later capture`() {
        var detections=0
        val observer=ScreenshotObserver(context,Handler(Looper.getMainLooper())) {detections++}
        val columns=arrayOf("_id","_display_name","relative_path","date_added")
        val provider=object:ContentProvider() {
            var cursor:Cursor?=null
            override fun onCreate()=true
            override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?)=cursor
            override fun getType(uri:Uri)="image/png"
            override fun insert(uri:Uri,values:ContentValues?):Uri?=null
            override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?)=0
            override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?)=0
        }
        ShadowContentResolver.registerProviderInternal("media",provider)
        set(observer,"started",true);set(observer,"generation",1L)
        val query=observer.javaClass.getDeclaredMethod("query",Boolean::class.javaPrimitiveType,Long::class.javaPrimitiveType).apply {isAccessible=true}
        query.invoke(observer,true,1L) // Provider temporarily unavailable during registration.
        provider.cursor=MatrixCursor(columns)
        query.invoke(observer,false,1L) // First successful response is empty and must establish ID zero.
        provider.cursor=MatrixCursor(columns).apply {addRow(arrayOf(1L,"Screenshot_test.png","Pictures/Screenshots",System.currentTimeMillis()/1000))}
        query.invoke(observer,false,1L)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1,detections)
        observer.stop()
    }
}
