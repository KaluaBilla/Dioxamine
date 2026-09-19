package io.github.rhythmcache.dioxamine.fastboot

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

import io.github.rhythmcache.dioxamine.core.FormatUtils

object FastbootFileUtils {

    fun resolveNameAndSize(context: Context, uri: Uri): Pair<String, Long> {
        var name = "image"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
            }
        }
        return name to size
    }

    /** Strips a trailing extension, e.g. "boot.img" -> "boot", "vbmeta.tar.gz" -> "vbmeta.tar" (single strip only). */
    fun stripExtension(fileName: String): String = fileName.substringBeforeLast('.', fileName)

    fun formatBytes(bytes: Long): String = FormatUtils.formatFileSize(bytes)
}