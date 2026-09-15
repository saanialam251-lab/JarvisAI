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
 */
class ModelManager(private val context: Context) {

    companion object {
        const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        val modelDir: (Context) -> File = { File(it.filesDir, "model") }
    }

    fun isModelReady(): Boolean = modelDir(context).exists() &&
        File(modelDir(context), "final.mdl").exists()

    suspend fun downloadModel(onProgress: (Int) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val zipFile = File(context.cacheDir, "vosk_model.zip")
            val conn = URL(MODEL_URL).openConnection() as HttpURLConnection
            conn.inputStream.use { input ->
                BufferedInputStream(input).use { bis ->
                    FileOutputStream(zipFile).use { fos ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int; var total = 0L; val size = conn.contentLength.coerceAtLeast(1)
                        while (bis.read(buf).also { read = it } != -1) {
                            fos.write(buf, 0, read); total += read
                            onProgress(((total * 100) / size).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
            unzip(zipFile, context.filesDir)
            zipFile.delete()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun unzip(zip: File, targetDir: File) {
        ZipInputStream(FileInputStream(zip)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                if (entry.isDirectory) outFile.mkdirs()
                else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
