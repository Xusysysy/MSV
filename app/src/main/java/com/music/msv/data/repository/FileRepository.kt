package com.music.msv.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

class FileRepository(private val context: Context) {

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

    fun openInputStream(uri: Uri) = context.contentResolver.openInputStream(uri)
}
