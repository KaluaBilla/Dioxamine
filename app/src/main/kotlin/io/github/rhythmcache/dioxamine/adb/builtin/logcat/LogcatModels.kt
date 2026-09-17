package io.github.rhythmcache.dioxamine.adb.builtin.logcat

import androidx.compose.ui.graphics.Color

enum class LogLevel(
    val letter: Char,
    val priority: Int,
    val label: String,
    val color: Color
) {
    VERBOSE('V', 2, "V", Color(0xFF8E8E93)),
    DEBUG('D', 3, "D", Color(0xFF2196F3)),
    INFO('I', 4, "I", Color(0xFF4CAF50)),
    WARN('W', 5, "W", Color(0xFFFF9800)),
    ERROR('E', 6, "E", Color(0xFFF44336)),
    FATAL('F', 7, "F", Color(0xFFE91E63));

    companion object {
        fun fromChar(c: Char): LogLevel = when (c) {
            'V' -> VERBOSE
            'D' -> DEBUG
            'I' -> INFO
            'W' -> WARN
            'E' -> ERROR
            'F', 'A' -> FATAL
            else -> VERBOSE
        }
    }
}

data class LogcatEntry(
    val id: Long,
    val timestamp: String,
    val pid: String,
    val tid: String,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val raw: String
)

object LogcatParser {
    // Matches standard logcat -v threadtime output:
    // e.g.: "09-17 21:55:12.345  1234  5678 D TagName : Message"
    private val THREADTIME_REGEX = Regex(
        """^([0-9]{2}-[0-9]{2}\s+[0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3})\s+(\d+)\s+(\d+)\s+([VDIWEFA])\s+(.*?)\s*:\s*(.*)$"""
    )

    fun parse(line: String, id: Long, defaultLevel: LogLevel = LogLevel.VERBOSE, defaultTag: String = ""): LogcatEntry {
        val match = THREADTIME_REGEX.matchEntire(line)
        if (match != null) {
            val timestamp = match.groupValues[1]
            val pid = match.groupValues[2]
            val tid = match.groupValues[3]
            val levelChar = match.groupValues[4].firstOrNull() ?: 'V'
            val level = LogLevel.fromChar(levelChar)
            val tag = match.groupValues[5].trim()
            val message = match.groupValues[6]
            return LogcatEntry(
                id = id,
                timestamp = timestamp,
                pid = pid,
                tid = tid,
                level = level,
                tag = tag,
                message = message,
                raw = line
            )
        }

        if (line.startsWith("--------- beginning of")) {
            return LogcatEntry(
                id = id,
                timestamp = "",
                pid = "",
                tid = "",
                level = LogLevel.INFO,
                tag = "system",
                message = line,
                raw = line
            )
        }

        return LogcatEntry(
            id = id,
            timestamp = "",
            pid = "",
            tid = "",
            level = defaultLevel,
            tag = defaultTag,
            message = line,
            raw = line
        )
    }
}
