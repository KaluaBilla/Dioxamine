package io.github.rhythmcache.dioxamine.core

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

object FileUtils {
    /**
     * Resolves the display name of a given [Uri], optionally falling back to [Uri.getLastPathSegment].
     */
    fun resolveDisplayName(
        context: Context,
        uri: Uri,
        fallbackToLastPathSegment: Boolean = false
    ): String? = resolveDisplayName(context.contentResolver, uri, fallbackToLastPathSegment)

    /**
     * Resolves the display name of a given [Uri] via [ContentResolver], optionally falling back to [Uri.getLastPathSegment].
     */
    fun resolveDisplayName(
        resolver: ContentResolver,
        uri: Uri,
        fallbackToLastPathSegment: Boolean = false
    ): String? {
        val name = try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx)?.takeIf { it.isNotBlank() } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
        return name ?: if (fallbackToLastPathSegment) uri.lastPathSegment?.takeIf { it.isNotBlank() } else null
    }

    /**
     * Resolves both display name and file size in bytes for a given [Uri].
     */
    fun resolveNameAndSize(
        context: Context,
        uri: Uri,
        defaultName: String = "file"
    ): Pair<String, Long> {
        var name: String? = null
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx >= 0) {
                        name = cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }
                    }
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }
        } catch (_: Exception) {}
        val finalName = name
            ?: uri.lastPathSegment?.takeIf { it.isNotBlank() }
            ?: defaultName
        return finalName to size
    }
}
