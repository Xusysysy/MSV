package com.music.msv.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.LruCache

class PdfPageRenderer(private val context: Context) {

    private var renderer: PdfRenderer? = null
    private var currentUri: Uri? = null
    private var pageCount: Int = 0

    private val cache = object : LruCache<String, Bitmap>(16) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun open(uri: Uri): Int {
        close()
        val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: return 0
        renderer = PdfRenderer(fd)
        currentUri = uri
        pageCount = renderer!!.pageCount
        return pageCount
    }

    fun renderPage(pageIndex: Int, width: Int, height: Int, zoom: Float = 1f): Bitmap? {
        val r = renderer ?: return null
        if (pageIndex !in 0 until pageCount) return null

        val key = "$pageIndex-${width}-${height}-$zoom"
        cache.get(key)?.let { return it }

        val page = r.openPage(pageIndex)
        val renderWidth = (width * zoom).toInt().coerceAtLeast(1)
        val renderHeight = (height * zoom).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
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
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()

        cache.put(key, bitmap)
        return bitmap
    }

    val pageWidth: Int get() {
        val r = renderer ?: return 0
        val page = r.openPage(0)
        return page.width
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
