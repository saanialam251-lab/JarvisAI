package com.jarvis.assistant.data.monitors

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceInfo(
    val model: String, val manufacturer: String, val androidVersion: String,
    val sdk: Int, val totalRamMb: Long, val availRamMb: Long,
    val totalStorageGb: Long, val availStorageGb: Long,
    val cpuInfo: String, val cores: Int, val screenResolution: String,
)

@Singleton
class DeviceMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _state = MutableStateFlow<DeviceInfo?>(null)
    val state: StateFlow<DeviceInfo?> = _state

    init { refreshOnceBlocking() }

    fun refreshOnceBlocking() {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)

        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val avail = stat.availableBytes

        _state.value = DeviceInfo(
            model = Build.MODEL, manufacturer = Build.MANUFACTURER,
            androidVersion = Build.VERSION.RELEASE, sdk = Build.VERSION.SDK_INT,
            totalRamMb = mi.totalMem / (1024 * 1024), availRamMb = mi.availMem / (1024 * 1024),
            totalStorageGb = total / (1024 * 1024 * 1024), availStorageGb = avail / (1024 * 1024 * 1024),
            cpuInfo = readCpu(), cores = Runtime.getRuntime().availableProcessors(),
            screenResolution = context.resources.displayMetrics.let { "${it.widthPixels}x${it.heightPixels}" },
        )
    }

    suspend fun refreshOnce() = withContext(Dispatchers.IO) { refreshOnceBlocking() }

    private fun readCpu(): String = runCatching {
        val f = File("/proc/cpuinfo")
        if (!f.exists()) return "Not available on this device"
        f.readLines().firstOrNull { it.startsWith("Hardware") || it.startsWith("Processor") || it.startsWith("model name") }
            ?.substringAfter(":")?.trim() ?: "Not available on this device"
    }.getOrDefault("Not available on this device")
}
