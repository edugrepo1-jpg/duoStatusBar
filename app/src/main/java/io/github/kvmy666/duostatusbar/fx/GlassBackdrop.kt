package io.github.kvmy666.duostatusbar.fx

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View
import android.view.WindowManager
import io.github.kvmy666.duostatusbar.L

/** AOSP BackgroundBlurDrawable API, probed rather than assumed on OEM ROMs.
 * https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/com/android/internal/graphics/drawable/BackgroundBlurDrawable.java
 * No screenshots, bitmap capture, window flag edits, or pretend foreground blur. */
internal class GlassBackdrop(private val view: View) {
    private var drawable: Drawable? = null
    private var tried = false
    fun draw(canvas: Canvas, enabled: Boolean, scale: Float, opacity: Float) {
        try {
            if (!enabled || !canvas.isHardwareAccelerated || scale <= 0 || opacity <= .001f) { drawable?.setVisible(false, false); return }
            val wm = view.context.getSystemService(WindowManager::class.java)
            if (wm?.isCrossWindowBlurEnabled != true) { drawable?.setVisible(false, false); return }
            if (!tried && view.isAttachedToWindow) {
                tried = true
                val getRoot = View::class.java.getDeclaredMethod("getViewRootImpl").apply { isAccessible = true }
                val root = getRoot.invoke(view) ?: return
                drawable = root.javaClass.getMethod("createBackgroundBlurDrawable").invoke(root) as? Drawable
                drawable?.let {
                    it.javaClass.getMethod("setBlurRadius", Int::class.javaPrimitiveType).invoke(it, (12 * view.resources.displayMetrics.density).toInt())
                    it.javaClass.getMethod("setCornerRadius", Float::class.javaPrimitiveType).invoke(it, 1000f)
                    L.i("Vidro: compositor aceitou a região de blur; resultado visual ainda não verificado no aparelho")
                }
            }
            drawable?.let {
                it.setVisible(true, false); it.alpha = (opacity * 220).toInt().coerceIn(0, 255)
                it.setBounds(-54, -54, 54, 54); it.draw(canvas)
            }
        } catch (t: Throwable) { tried = true; drawable?.setVisible(false, false); drawable = null; L.w("Vidro: blur indisponível; usando fundo translúcido (${t.javaClass.simpleName})") }
    }
    fun release() { try { drawable?.setVisible(false, false); drawable = null; tried = false } catch (_: Throwable) { } }
}
