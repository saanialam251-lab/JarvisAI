package com.jarvis.assistant.core.voice

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Downloads + unzips the small Vosk English model (~40 MB) so speech
 * recognition and the wake word run 100% on-device (privacy requirement:
 * microphone audio never uploaded for wake-word detection).
 *
 * No special Android permission is needed beyond INTERNET (already declared
 * in the manifest) — the model is stored in the app's own private storage
 * (filesDir/cacheDir), which every app can write to without asking.
 */
class ModelManager(private val context: Context) {

    companion object {
        const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        val modelDir: (Context) -> File = { File(it.filesDir, "model") }
    }

    // Vosk models are packaged with the actual model file one level deeper,
    // inside an "am" subfolder — NOT directly in the model root.
    fun isModelReady(): Boolean = File(modelDir(context), "am/final.mdl").exists()

    suspend fun downloadModel(onProgress: (Int) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        val zipFile = File(context.cacheDir, "vosk_model.zip")
        var conn: HttpURLConnection? = null
        try {
            // Clear any partial/stale extraction from a previous failed attempt
            // (e.g. an older bug that unzipped into the wrong subfolder) so we
            // never end up with leftover junk taking up space or confusing
            // isModelReady().
            clearModelDir()

            conn = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                instanceFollowRedirects = true
            }

            if (conn.responseCode !in 200..299) {
                return@withContext Result.failure(
                    Exception("Download failed: server returned HTTP ${conn.responseCode}."))
            }

            val contentLength = conn.contentLength
            conn.inputStream.use { input ->
                BufferedInputStream(input).use { bis ->
                    FileOutputStream(zipFile).use { fos ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        var total = 0L
                        while (bis.read(buf).also { read = it } != -1) {
                            fos.write(buf, 0, read)
                            total += read
                            if (contentLength > 0) {
                                onProgress(((total * 100) / contentLength).toInt().coerceIn(0, 99))
                            }
                        }
                    }
                }
            }

            if (zipFile.length() < 1_000_000L) {
                return@withContext Result.failure(
                    Exception("Downloaded file was incomplete (${zipFile.length()} bytes). Check your connection and try again."))
            }

            unzipStrippingTopFolder(zipFile, modelDir(context))

            if (!isModelReady()) {
                return@withContext Result.failure(
                    Exception("Model files extracted but am/final.mdl is missing. The zip format may have changed — try again, or check your connection."))
            }
            onProgress(100)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
            zipFile.delete() // clean up the ~40MB zip whether we succeeded or failed
        }
    }

    private fun clearModelDir() {
        val dir = modelDir(context)
        if (dir.exists()) dir.deleteRecursively()
    }

    /**
     * The zip contains a single top-level folder (e.g. "vosk-model-small-en-us-0.15/").
     * We strip that first path segment so contents land directly in modelDir(context)/,
     * which is what isModelReady() and Vosk's Model(path) expect.
     */
    private fun unzipStrippingTopFolder(zip: File, targetDir: File) {
        targetDir.mkdirs()
        ZipInputStream(FileInputStream(zip)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val strippedName = entry.name.substringAfter('/', missingDelimiterValue = "")
                if (strippedName.isNotBlank()) {
                    val outFile = File(targetDir, strippedName)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
