package io.github.kvmy666.duostatusbar.fx

import android.graphics.*
import android.media.MediaMetadata

/** A single small in-memory thumbnail. Never opens URIs, writes files or exports artwork to logs. */
internal class MediaArtworkCache {
    private var source:Bitmap?=null
    private var generation=0
    private var thumbnail:Bitmap?=null
    fun clear() {source=null;thumbnail=null;generation=0}
    fun read(metadata:MediaMetadata?):Bitmap? {
        val image=runCatching {metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)}.getOrNull()
        if(image==null||image.isRecycled){clear();return null}
        if(source===image&&generation==image.generationId)return thumbnail
        source=image;generation=image.generationId
        thumbnail=null
        thumbnail=runCatching {
            // Never retain or recycle a bitmap owned by the media player as our display thumbnail.
            val ratio=128f/maxOf(image.width,image.height)
            val w=(image.width*minOf(1f,ratio)).toInt().coerceIn(1,128)
            val h=(image.height*minOf(1f,ratio)).toInt().coerceIn(1,128)
            // A large hardware cover would require an expensive full-size CPU copy. Use the
            // waveform fallback instead; ordinary software covers are directly downsampled.
            if(image.config==Bitmap.Config.HARDWARE&&image.width.toLong()*image.height>262144L)return null
            val readable=if(image.config==Bitmap.Config.HARDWARE)image.copy(Bitmap.Config.ARGB_8888,false) else image
            try {Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).also {
                Canvas(it).drawBitmap(requireNotNull(readable),null,Rect(0,0,w,h),Paint(Paint.FILTER_BITMAP_FLAG))
            }} finally {if(readable!==image)readable?.recycle()}
        }.getOrNull()
        return thumbnail
    }
}

internal object MediaArtwork {
    /** The private relay accepts only the already bounded thumbnail, never a full album bitmap. */
    fun valid(bitmap:Bitmap?)=bitmap!=null&&!bitmap.isRecycled&&bitmap.width in 1..128&&bitmap.height in 1..128&&bitmap.allocationByteCount<=65536
}

/** Static cover glow: no extra animation loop, blur bitmap or allocation on each frame. */
internal class AlbumCoverPainter {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matrix=Matrix()
    private val bounds=RectF()
    private var image:Bitmap?=null
    private var bitmapShader:BitmapShader?=null
    fun draw(canvas:Canvas,bitmap:Bitmap?,rect:RectF,color:Int):Boolean {
        if(!MediaArtwork.valid(bitmap))return false
        val art=requireNotNull(bitmap)
        if(image!==art){image=art;bitmapShader=BitmapShader(art,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP)}
        paint.style=Paint.Style.FILL;paint.alpha=255
        val scale=maxOf(rect.width()/art.width,rect.height()/art.height)
        matrix.setScale(scale,scale);matrix.postTranslate(rect.centerX()-art.width*scale/2,rect.centerY()-art.height*scale/2);bitmapShader?.setLocalMatrix(matrix)
        paint.alpha=255;paint.shader=bitmapShader;canvas.drawCircle(rect.centerX(),rect.centerY(),minOf(rect.width(),rect.height())/2,paint)
        paint.shader=null;paint.color=0x55FFFFFF;paint.style=Paint.Style.STROKE;paint.strokeWidth=maxOf(1f,rect.width()*.016f)
        bounds.set(rect);canvas.drawCircle(rect.centerX(),rect.centerY(),minOf(rect.width(),rect.height())/2-paint.strokeWidth/2,paint);paint.style=Paint.Style.FILL
        return true
    }
}
