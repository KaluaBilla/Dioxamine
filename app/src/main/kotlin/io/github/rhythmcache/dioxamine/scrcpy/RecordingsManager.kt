package io.github.rhythmcache.dioxamine.scrcpy

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import io.github.rhythmcache.dioxamine.core.AppLogger
import io.github.rhythmcache.dioxamine.core.FormatUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecordingInfo(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val createdAt: Long
)

object RecordingsManager {
    private const val TAG = "SCRCPY_CLIENT"
    private const val RECORDINGS_DIR = "scrcpy_recordings"
    const val FILE_PROVIDER_AUTHORITY = "io.github.rhythmcache.dioxamine.fileprovider"

    val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val activeRecordingFiles = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun markRecordingActive(file: File) {
        activeRecordingFiles.add(file.absolutePath)
    }

    fun markRecordingFinished(file: File) {
        activeRecordingFiles.remove(file.absolutePath)
    }

    fun isRecordingActive(file: File): Boolean {
        return activeRecordingFiles.contains(file.absolutePath)
    }

    fun getRecordingsDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), RECORDINGS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun generateRecordingFile(context: Context): File {
        val dir = getRecordingsDir(context)
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.US).format(Date())
        return File(dir, "scrcpy_$timestamp.mp4")
    }

    fun generateScreenshotFile(context: Context): File {
        val dir = getRecordingsDir(context)
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.US).format(Date())
        var file = File(dir, "scrcpy_$timestamp.png")
        var counter = 1
        while (!file.createNewFile()) {
            file = File(dir, "scrcpy_${timestamp}_$counter.png")
            counter++
        }
        return file
    }

    fun saveScreenshot(context: Context, bitmap: Bitmap): File? {
        var outputFile: File? = null
        return try {
            val file = generateScreenshotFile(context)
            outputFile = file
            markRecordingActive(file)
            val success = file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (!success) {
                val deleted = file.delete()
                AppLogger.e(TAG, "Bitmap.compress failed for screenshot: ${file.name} (cleanup=$deleted)")
                null
            } else {
                AppLogger.i(TAG, "Screenshot saved: ${file.name}")
                file
            }
        } catch (e: CancellationException) {
            val deleted = outputFile?.delete()
            AppLogger.w(TAG, "Screenshot saving cancelled; cleaned up partial file ${outputFile?.name} (cleanup=$deleted)")
            throw e
        } catch (e: Exception) {
            val deleted = outputFile?.delete()
            AppLogger.e(TAG, "Failed to save screenshot: ${e.message} (cleanup=$deleted)", e)
            null
        } finally {
            outputFile?.let { markRecordingFinished(it) }
        }
    }

    fun hasEnoughFreeSpace(context: Context, minBytes: Long = 50 * 1024 * 1024): Boolean {
        val dir = getRecordingsDir(context)
        val freeBytes = dir.usableSpace
        return freeBytes >= minBytes
    }

    fun listRecordings(context: Context): List<RecordingInfo> {
        val dir = getRecordingsDir(context)
        val files = dir.listFiles { f ->
            f.isFile && (f.extension.equals("mp4", ignoreCase = true) || f.extension.equals("png", ignoreCase = true))
        } ?: return emptyList()

        return files.filter { file ->
            !activeRecordingFiles.contains(file.absolutePath)
        }.mapNotNull { file ->
            runCatching {
                val isVideo = file.extension.equals("mp4", ignoreCase = true)
                val durationMs = if (isVideo) getDuration(file) else 0L
                RecordingInfo(
                    file = file,
                    name = file.name,
                    sizeBytes = file.length(),
                    durationMs = durationMs,
                    createdAt = file.lastModified()
                )
            }.getOrNull()
        }.sortedByDescending { it.createdAt }
    }

    private fun getDuration(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to get duration for ${file.name}: ${e.message}")
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun deleteRecording(file: File): Boolean {
        return runCatching {
            val deleted = file.delete()
            AppLogger.i(TAG, "Recording deleted: ${file.name} (success=$deleted)")
            deleted
        }.getOrElse { e ->
            AppLogger.e(TAG, "Failed to delete recording: ${e.message}", e)
            false
        }
    }

    fun exportRecording(context: Context, sourceFile: File, destUri: Uri): Boolean {
        return runCatching {
            context.contentResolver.openOutputStream(destUri)?.use { outputStream ->
                sourceFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            AppLogger.i(TAG, "Recording exported: ${sourceFile.name}")
            true
        }.getOrElse { e ->
            AppLogger.e(TAG, "Failed to export recording: ${e.message}", e)
            false
        }
    }

    fun openRecording(context: Context, file: File) {
        runCatching {
            val contentUri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
            val isImage = file.extension.equals("png", ignoreCase = true)
            val mimeType = if (isImage) "image/png" else "video/mp4"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure { e ->
            if (e is ActivityNotFoundException) {
                AppLogger.w(TAG, "No app found to open file")
            } else {
                AppLogger.e(TAG, "Failed to open file: ${e.message}", e)
            }
        }
    }

    fun getRecordingThumbnail(file: File): android.graphics.Bitmap? {
        if (file.extension.equals("png", ignoreCase = true)) {
            return try {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(file.absolutePath, options)
                val width = options.outWidth
                val height = options.outHeight
                if (width <= 0 || height <= 0) return null

                val reqSize = 300
                var sampleSize = 1
                while (width / sampleSize > reqSize * 2 || height / sampleSize > reqSize * 2) {
                    sampleSize *= 2
                }
                options.inJustDecodeBounds = false
                options.inSampleSize = sampleSize
                val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null

                var success = false
                try {
                    val maxDim = maxOf(decoded.width, decoded.height)
                    val result = if (maxDim > reqSize) {
                        val scale = reqSize.toFloat() / maxDim
                        val scaledW = (decoded.width * scale).toInt().coerceAtLeast(1)
                        val scaledH = (decoded.height * scale).toInt().coerceAtLeast(1)
                        val scaled = Bitmap.createScaledBitmap(decoded, scaledW, scaledH, true)
                        if (scaled != decoded) {
                            decoded.recycle()
                        }
                        scaled
                    } else {
                        decoded
                    }
                    success = true
                    result
                } finally {
                    if (!success) {
                        decoded.recycle()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to decode PNG thumbnail for ${file.name}: ${e.message}")
                null
            }
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to get thumbnail for ${file.name}: ${e.message}")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun formatDuration(ms: Long): String = FormatUtils.formatDuration(ms)

    fun formatFileSize(bytes: Long): String = FormatUtils.formatFileSize(bytes)
}
