package com.jarvis.assistant.data.monitors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class MicReport(
    val permissionGranted: Boolean,
    val micAvailable: Boolean,
    val inputDetected: Boolean,
    val peakLevelDb: Float,       // approx RMS dB
    val supportedRates: List<Int>,
    val audioSourceOk: Boolean,
    val issue: String?,
)

@Singleton
class MicDiagnostics @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    private fun supportedRates(): List<Int> {
        val rates = listOf(8000, 16000, 22050, 44100, 48000)
        return rates.filter { rate ->
            runCatching {
                val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                if (minBuf <= 0) return@filter false
                val rec = AudioRecord(MediaRecorder.AudioSource.MIC, rate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf)
                val ok = rec.state == AudioRecord.STATE_INITIALIZED
                rec.release()
                ok
            }.getOrDefault(false)
        }
    }

    /** Live level meter: call repeatedly while testing. */
    suspend fun testMicrophone(durationMs: Long = 2000): MicReport = withContext(Dispatchers.IO) {
        if (!hasPermission) return@withContext MicReport(false, false, false, -100f, emptyList(), false,
            "Microphone permission not granted")

        val rates = supportedRates()
        val rate = if (44100 in rates) 44100 else rates.firstOrNull() ?: 16000
        val bufSize = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            .coerceAtLeast(2048)

        var rec: AudioRecord? = null
        try {
            rec = AudioRecord(MediaRecorder.AudioSource.MIC, rate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
            if (rec.state != AudioRecord.STATE_INITIALIZED)
                return@withContext MicReport(true, false, false, -100f, rates, false,
                    "Microphone is in use by another app or unavailable")

            rec.startRecording()
            val buf = ShortArray(bufSize)
            var maxRms = 0.0
            val end = System.currentTimeMillis() + durationMs
            while (System.currentTimeMillis() < end) {
                val n = rec.read(buf, 0, bufSize)
                if (n > 0) {
                    var sum = 0.0
                    for (i in 0 until n) sum += buf[i] * buf[i]
                    val rms = kotlin.math.sqrt(sum / n)
                    if (rms > maxRms) maxRms = rms
                }
            }
            rec.stop()
            val db = (20 * kotlin.math.log10((maxRms / 32768.0).coerceAtLeast(1e-6))).toFloat()
            val inputOk = maxRms > 100 // anything above digital silence
            MicReport(true, true, inputOk, db, rates, true,
                if (inputOk) null else
                    "No audio input detected. Software can detect symptoms but cannot " +
                    "always determine physical hardware failure — check for debris in the " +
                    "microphone hole or a case blocking it.")
        } catch (e: SecurityException) {
            MicReport(false, false, false, -100f, rates, false, "Permission denied while recording")
        } catch (e: Exception) {
            MicReport(true, false, false, -100f, rates, false, "Audio error: ${e.message}")
        } finally {
            try { rec?.release() } catch (_: Exception) {}
        }
    }

    suspend fun recordSample(seconds: Int = 5, out: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            val rate = 44100
            val bufSize = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = AudioRecord(MediaRecorder.AudioSource.MIC, rate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
            rec.startRecording()
            val pcm = ShortArray(rate * seconds)
            var read = 0
            val end = System.currentTimeMillis() + seconds * 1000L
            while (read < pcm.size && System.currentTimeMillis() < end) {
                val n = rec.read(pcm, read, pcm.size - read)
                if (n > 0) read += n
            }
            rec.stop(); rec.release()

            // Write minimal WAV
            out.parentFile?.mkdirs()
            val byteRate = 16 * rate / 8
            val totalDataLen = read * 2
            val header = java.nio.ByteBuffer.allocate(44)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".toByteArray()).putInt(36 + totalDataLen)
                .put("WAVE".toByteArray()).put("fmt ".toByteArray())
                .putInt(16).putShort(1).putShort(1)
                .putInt(rate).putInt(byteRate).putShort(2).putShort(16)
                .put("data".toByteArray()).putInt(totalDataLen)
            out.writeBytes(header.array())
            val bytes = java.nio.ByteBuffer.allocate(read * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until read) bytes.putShort(pcm[i])
            java.io.FileOutputStream(out, true).use { it.write(bytes.array()) }
            Result.success(out)
        } catch (e: Exception) { Result.failure(e) }
    }

    fun playSample(file: File, onDone: () -> Unit = {}) {
        runCatching {
            val mp = MediaPlayer()
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener { it.release(); onDone() }
            mp.prepare(); mp.start()
        }
    }
}
