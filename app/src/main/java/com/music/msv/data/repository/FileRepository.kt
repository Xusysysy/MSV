package com.music.msv.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File as JFile

class FileRepository(private val context: Context) {

    private val docsDir: JFile
        get() = JFile(context.filesDir, "docs").also { it.mkdirs() }

    fun getFileName(uri: Uri): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else {
                    uri.lastPathSegment ?: "unknown"
                }
            } ?: uri.lastPathSegment ?: "unknown"
        } catch (e: Exception) {
            uri.lastPathSegment ?: "unknown"
        }
    }

    fun isPdf(fileName: String): Boolean =
        fileName.lowercase().endsWith(".pdf")

    fun isImage(fileName: String): Boolean =
        fileName.lowercase().let {
            it.endsWith(".jpg") || it.endsWith(".jpeg") ||
                    it.endsWith(".png") || it.endsWith(".webp") ||
                    it.endsWith(".bmp") || it.endsWith(".gif")
        }

    fun takePersistablePermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}
    }

    fun takePersistablePermissions(uris: List<Uri>) {
        uris.forEach { takePersistablePermission(it) }
    }

    fun copyToLocal(uri: Uri, fileName: String): Uri? {
        return try {
            val dest = JFile(docsDir, fileName)
            if (dest.exists()) return Uri.fromFile(dest)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            Uri.fromFile(dest)
        } catch (_: Exception) {
            null
        }
    }

    fun getLocalFile(fileName: String): Uri? {
        val file = JFile(docsDir, fileName)
        return if (file.exists()) Uri.fromFile(file) else null
    }

    fun listLocalFiles(): List<JFile> {
        return docsDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun openInputStream(uri: Uri) = context.contentResolver.openInputStream(uri)
}
