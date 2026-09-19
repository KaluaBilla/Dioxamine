package io.github.rhythmcache.dioxamine.fastboot

import android.content.Context
import android.net.Uri
import io.github.rhythmcache.dioxamine.core.FileUtils
import io.github.rhythmcache.dioxamine.core.FormatUtils

object FastbootFileUtils {

    fun resolveNameAndSize(context: Context, uri: Uri): Pair<String, Long> =
        FileUtils.resolveNameAndSize(context, uri, defaultName = "image")

    /** Strips a trailing extension, e.g. "boot.img" -> "boot", "vbmeta.tar.gz" -> "vbmeta.tar" (single strip only). */
    fun stripExtension(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot > 0) fileName.substring(0, dot) else fileName
    }

    fun formatBytes(bytes: Long): String = FormatUtils.formatFileSize(bytes)
}