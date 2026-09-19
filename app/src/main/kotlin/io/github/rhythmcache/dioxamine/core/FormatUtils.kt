package io.github.rhythmcache.dioxamine.core

import java.util.Locale

object FormatUtils {
    /**
     * Converts a size in bytes to a human-readable format (e.g. "12.5 KB", "4.2 MB", "1.5 GB").
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    /**
     * Converts a memory size in kilobytes (KB) to a human-readable string (KB, MB, GB).
     */
    fun formatKb(kb: Long): String = when {
        kb <= 0 -> "0 KB"
        kb >= 1024 * 1024 -> String.format(Locale.US, "%.1f GB", kb / (1024.0 * 1024.0))
        kb >= 1024 -> String.format(Locale.US, "%.1f MB", kb / 1024.0)
        else -> "$kb KB"
    }

    /**
     * Formats a duration in milliseconds to "H:MM:SS" or "M:SS".
     */
    fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }
}

/** Top-level shortcut for [FormatUtils.formatFileSize] */
fun formatFileSize(bytes: Long): String = FormatUtils.formatFileSize(bytes)

/** Top-level shortcut for [FormatUtils.formatKb] */
fun formatKb(kb: Long): String = FormatUtils.formatKb(kb)

/** Top-level shortcut for [FormatUtils.formatDuration] */
fun formatDuration(ms: Long): String = FormatUtils.formatDuration(ms)
