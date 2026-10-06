package io.github.kvmy666.duostatusbar.fx
import android.content.Context
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
class RuntimeIndicatorsTest {
    @Test fun `location toggle appears without requiring an app to access coordinates`() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val manager=context.getSystemService(LocationManager::class.java)
        val indicators=RuntimeIndicators(context,Handler(Looper.getMainLooper())) { }
        Shadows.shadowOf(manager).setLocationEnabled(true)
        indicators.refresh();assertTrue(indicators.location)
        Shadows.shadowOf(manager).setLocationEnabled(false)
        indicators.refresh();assertFalse(indicators.location)
    }
}
