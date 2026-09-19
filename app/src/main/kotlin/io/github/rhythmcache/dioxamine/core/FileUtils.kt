package io.github.rhythmcache.dioxamine.core

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

object FileUtils {
    /**
     * Resolves the display name of a given [Uri], falling back to [Uri.getLastPathSegment].
     */
    fun resolveDisplayName(context: Context, uri: Uri): String? =
        resolveDisplayName(context.contentResolver, uri)

    /**
     * Resolves the display name of a given [Uri] via [ContentResolver], falling back to [Uri.getLastPathSegment].
     */
    fun resolveDisplayName(resolver: ContentResolver, uri: Uri): String? {
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        } ?: uri.lastPathSegment
    }

    /**
     * Resolves both display name and file size in bytes for a given [Uri].
     */
    fun resolveNameAndSize(context: Context, uri: Uri, defaultName: String = "file"): Pair<String, Long> {
        var name = defaultName
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }
        } catch (_: Exception) {}
        if (name == defaultName && uri.lastPathSegment != null) {
            name = uri.lastPathSegment ?: defaultName
        }
        return name to size
    }
}
