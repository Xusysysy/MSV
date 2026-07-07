package com.music.msv.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import java.io.File as JFile
import kotlin.math.min

class PdfPageRenderer(private val context: Context) {

    private var renderer: PdfRenderer? = null
    private var currentUri: Uri? = null
    private var pageCount: Int = 0

    private val cache = object : LruCache<String, Bitmap>(16) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun open(uri: Uri): Int {
        close()
        val fd: ParcelFileDescriptor? = if (uri.scheme == "file") {
            try {
                ParcelFileDescriptor.open(JFile(uri.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (_: Exception) { null }
        } else {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")
            } catch (_: Exception) { null }
        }
        if (fd == null) return 0
        try {
            renderer = PdfRenderer(fd)
            currentUri = uri
            pageCount = renderer!!.pageCount
            return pageCount
        } catch (_: Exception) {
            try { fd.close() } catch (_: Exception) {}
            return 0
        }
    }

    fun renderPage(pageIndex: Int, viewportW: Int, viewportH: Int, zoom: Float = 1f): Bitmap? {
        val r = renderer ?: return null
        if (pageIndex !in 0 until pageCount) return null

        val key = "$pageIndex-${viewportW}-${viewportH}-$zoom"
        cache.get(key)?.let { return it }

        val page = r.openPage(pageIndex)
        val pw = page.width.toFloat()
        val ph = page.height.toFloat()
        val scale = min(viewportW / pw, viewportH / ph) * zoom
        val renderWidth = (pw * scale).toInt().coerceAtLeast(1)
        val renderHeight = (ph * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(AndroidColor.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()

        cache.put(key, bitmap)
        return bitmap
    }

    fun renderThumbnail(pageIndex: Int, maxDim: Int = 200): Bitmap? {
        val r = renderer ?: return null
        if (pageIndex !in 0 until pageCount) return null

        val key = "thumb-$pageIndex-$maxDim"
        cache.get(key)?.let { return it }

        val page = r.openPage(pageIndex)
        val scale = maxDim.toFloat() / page.width.coerceAtLeast(page.height).toFloat()
        val w = (page.width.toFloat() * scale).toInt().coerceAtLeast(1)
        val h = (page.height.toFloat() * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(AndroidColor.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()

        cache.put(key, bitmap)
        return bitmap
    }

    val pageWidth: Int get() {
        val r = renderer ?: return 0
        val page = r.openPage(0)
        val w = page.width
        page.close()
        return w
    }

    val pageHeight: Int get() {
        val r = renderer ?: return 0
        val page = r.openPage(0)
        val h = page.height
        page.close()
        return h
    }

    fun close() {
        cache.evictAll()
        renderer?.close()
        renderer = null
        currentUri = null
        pageCount = 0
    }

    fun getPageCount(): Int = pageCount
}
