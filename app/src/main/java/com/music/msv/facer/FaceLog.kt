package com.music.msv.facer

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FaceLog {
    private const val LOG_FILE = "msv_face_log.txt"
    private var writer: PrintWriter? = null
    private val sdf = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())

    @Volatile
    var logFile: File? = null
        private set

    fun init(ctx: Context) {
        if (logFile != null) return
        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        logFile = File(dir, LOG_FILE)
        logFile?.let { f ->
            if (f.length() > 5 * 1024 * 1024) f.delete()
            writer = PrintWriter(FileWriter(f, true), true)
            w("FACE_LOG", "=== 日志开始 ${sdf.format(Date())} ===")
        }
    }

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        w(tag, msg)
    }

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        w(tag, msg)
    }

    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
        write("W/$tag $msg")
    }

    fun e(tag: String, msg: String, th: Throwable? = null) {
        Log.e(tag, msg, th)
        val sb = StringBuilder("E/$tag $msg")
        if (th != null) {
            sb.append("\n")
            val sw = StringWriter()
            th.printStackTrace(PrintWriter(sw))
            sb.append(sw.toString())
        }
        write(sb.toString())
    }

    @Synchronized
    private fun write(line: String) {
        try {
            writer?.println("${sdf.format(Date())} $line")
            writer?.flush()
        } catch (_: Exception) {}
    }

    fun getLogPath(): String = logFile?.absolutePath ?: "未初始化"
}
