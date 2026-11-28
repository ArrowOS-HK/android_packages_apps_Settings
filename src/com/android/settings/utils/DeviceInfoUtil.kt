/*
 * Copyright (C) 2026 HK Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.utils

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemProperties
import android.os.storage.StorageManager
import android.provider.Settings
import android.view.Display
import com.android.internal.util.MemInfoReader
import com.android.settings.R
import com.android.settings.deviceinfo.HardwareInfoPreferenceController
import com.android.settingslib.deviceinfo.PrivateStorageInfo
import com.android.settingslib.deviceinfo.StorageManagerVolumeProvider
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Hardware, storage, memory, and maintainer property helpers for About Phone.
 * Maintainer-configured props default to "Unknown" when omitted from the device tree,
 * while device name, RAM, ZRAM, storage, and display are auto-detected.
 */
object DeviceInfoUtil {

    private const val ONE_GB_BYTES = 1024.0 * 1024.0 * 1024.0
    private const val ONE_DECIMAL_GB = 1_000_000_000.0
    private const val UNKNOWN = "Unknown"

    private fun firstNonBlankProp(vararg keys: String): String? {
        for (key in keys) {
            val value = SystemProperties.get(key, "").trim()
            if (value.isNotEmpty() && !value.equals(UNKNOWN, ignoreCase = true)) {
                return value
            }
        }
        return null
    }

    fun getDeviceName(context: Context): String {
        val globalName = Settings.Global.getString(
            context.contentResolver,
            Settings.Global.DEVICE_NAME
        )?.trim()
        return if (!globalName.isNullOrEmpty()) globalName else Build.MODEL
    }

    fun getHardwareModel(): String {
        return firstNonBlankProp("ro.ext.model") ?: UNKNOWN
    }

    fun getRomName(context: Context): String {
        return firstNonBlankProp("ro.ext.rom.name")
            ?: context.getString(R.string.config_about_rom_name).trim().ifEmpty { "ArrowOS" }
    }

    fun getVersionBadgeText(context: Context): String {
        val rawVersion = firstNonBlankProp("ro.ext.version", "ro.modversion") ?: "13.1"
        val cleanVersion = rawVersion.removePrefix("v").removePrefix("V").trim().ifEmpty { "13.1" }
        return "${getRomName(context)} | $cleanVersion"
    }

    fun getMaintainer(): String {
        return firstNonBlankProp("ro.ext.maintainer", "ro.device.maintainer") ?: UNKNOWN
    }

    fun getMaintainerSubtitle(): String {
        return "Tiramisu || ${getMaintainer()}"
    }

    fun getBrandDisplayName(context: Context): String {
        val overlayName = context.getString(R.string.config_about_brand_name).trim()
        if (overlayName.isNotEmpty()) return overlayName
        val propBrand = firstNonBlankProp("ro.ext.brand")
        if (!propBrand.isNullOrEmpty()) return propBrand
        val raw = Build.MANUFACTURER.ifBlank { Build.BRAND }.trim()
        if (raw.isEmpty()) return "Android"
        return raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
    }

    fun getProcessor(): String {
        val raw = firstNonBlankProp("ro.ext.processor", "ro.device.chipset") ?: return UNKNOWN
        return raw.replace("_", " ")
            .replace(Regex("(?i)\\bqualcomm\\s*®?\\s*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifEmpty { UNKNOWN }
    }

    fun getProcessorDetails(): String {
        firstNonBlankProp("ro.ext.processor.detail")?.let { return it }
        val lines = mutableListOf<String>()
        lines.add(getProcessor())
        val cores = Runtime.getRuntime().availableProcessors()
        val maxGhz = readMaxCpuFreqGhz()
        val freqPart = if (maxGhz != null) String.format(Locale.US, " · Up to %.2f GHz", maxGhz) else ""
        lines.add("$cores cores$freqPart")
        val abi = Build.SUPPORTED_ABIS.firstOrNull()
        if (!abi.isNullOrBlank()) {
            lines.add("Architecture: $abi")
        }
        val platform = firstNonBlankProp("ro.board.platform", "ro.hardware")
        if (!platform.isNullOrBlank()) {
            lines.add("Platform: $platform")
        }
        return lines.joinToString("\n")
    }

    private fun readMaxCpuFreqGhz(): Double? {
        val cpuRoot = File("/sys/devices/system/cpu")
        val cpuDirs = cpuRoot.listFiles { file ->
            file.isDirectory && file.name.startsWith("cpu") && file.name.substring(3).all { it.isDigit() }
        } ?: return null
        var maxKHz = 0L
        for (dir in cpuDirs) {
            val freqFile = File(dir, "cpufreq/cpuinfo_max_freq")
            if (freqFile.canRead()) {
                val khz = try {
                    freqFile.readText().trim().toLongOrNull() ?: 0L
                } catch (_: Throwable) {
                    0L
                }
                if (khz > maxKHz) maxKHz = khz
            }
        }
        return if (maxKHz > 0L) maxKHz / 1_000_000.0 else null
    }

    fun getPhysicalRamBytes(): Long {
        val memInfoReader = MemInfoReader()
        memInfoReader.readMemInfo()
        return memInfoReader.totalSize
    }

    fun getPhysicalRamFormatted(): String {
        val totalRamGB = getPhysicalRamBytes() / ONE_GB_BYTES
        val roundedRamGB = roundToNearestKnownRamSize(totalRamGB)
        return String.format(Locale.US, "%d.00 GB", roundedRamGB)
    }

    fun getZramFormatted(): String? {
        val swapGB = getZramSize()
        return if (swapGB >= 0.05) {
            String.format(Locale.US, "+ %.2f GB", swapGB)
        } else {
            null
        }
    }

    fun getTotalRam(): String {
        val totalRamGB = getPhysicalRamBytes() / ONE_GB_BYTES
        val roundedRamGB = roundToNearestKnownRamSize(totalRamGB).toDouble()
        val swapGB = getZramSize()
        return if (swapGB > 0.0) {
            String.format(Locale.US, "%.1f GB + %.2f GB ZRAM", roundedRamGB, swapGB)
        } else {
            String.format(Locale.US, "%.1f GB", roundedRamGB)
        }
    }

    fun getZramSize(): Double {
        val swapLine: String = try {
            BufferedReader(FileReader("/proc/meminfo")).use { reader ->
                reader.lineSequence().firstOrNull { it.startsWith("SwapTotal:") } ?: return 0.0
            }
        } catch (_: IOException) {
            return 0.0
        }

        val swapValue = swapLine.split(":").getOrNull(1)?.trim()?.split(" ")?.firstOrNull()
        val swapValueKB = swapValue?.toLongOrNull() ?: return 0.0
        return if (swapValueKB > 0) swapValueKB / (1024.0 * 1024.0) else 0.0
    }

    private fun roundToNearestKnownRamSize(memoryGB: Double): Int {
        val knownSizes = intArrayOf(1, 2, 3, 4, 6, 8, 10, 12, 16, 24, 32, 48, 64)
        if (memoryGB <= 0) return 1
        for (size in knownSizes) {
            if (memoryGB <= size) return size
        }
        return knownSizes.last()
    }

    /** Returns Pair(usedBytes, totalBytes) from PrivateStorageInfo/StatFs. */
    fun getRawStorageBytes(context: Context): Pair<Long, Long> {
        return try {
            val storageManager = context.getSystemService(StorageManager::class.java)
            val volumeProvider = StorageManagerVolumeProvider(storageManager)
            val info = PrivateStorageInfo.getPrivateStorageInfo(volumeProvider)
            val total = info.totalBytes
            val used = (info.totalBytes - info.freeBytes).coerceAtLeast(0L)
            used to total
        } catch (_: Throwable) {
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = stat.totalBytes
            val free = stat.availableBytes
            (total - free).coerceAtLeast(0L) to total
        }
    }

    fun getStorageTotal(context: Context): String {
        val (_, totalBytes) = getRawStorageBytes(context)
        val totalStorageGB = totalBytes / ONE_GB_BYTES
        val roundedStorageGB = roundToNearestKnownStorageSize(totalStorageGB)
        return if (roundedStorageGB >= 1024) {
            "${roundedStorageGB / 1024} TB"
        } else {
            "$roundedStorageGB GB"
        }
    }

    /** Formats storage bar header like "34 GB / 128 GB". */
    fun getStorageUsedAndTotalFormatted(context: Context): String {
        val (usedBytes, totalBytes) = getRawStorageBytes(context)
        val roundedTotalBytes = roundMarketingStorageBytes(totalBytes)
        val adjustedUsedBytes = if (roundedTotalBytes > totalBytes) {
            (roundedTotalBytes - (totalBytes - usedBytes)).coerceAtLeast(0L)
        } else {
            usedBytes
        }
        val usedGb = (adjustedUsedBytes / ONE_DECIMAL_GB).roundToInt().coerceAtLeast(0)
        val totalGb = (roundedTotalBytes / ONE_DECIMAL_GB).roundToInt().coerceAtLeast(1)
        return "$usedGb GB / $totalGb GB"
    }

    private fun roundMarketingStorageBytes(rawBytes: Long): Long {
        val rawGb = rawBytes / ONE_DECIMAL_GB
        val tiers = intArrayOf(16, 32, 64, 128, 256, 512, 1024, 2048)
        for (tier in tiers) {
            if (rawGb < tier * 1.1) {
                return tier.toLong() * 1_000_000_000L
            }
        }
        return rawBytes
    }

    private fun roundToNearestKnownStorageSize(storageGB: Double): Int {
        val knownSizes = intArrayOf(16, 32, 64, 128, 256, 512, 1024)
        if (storageGB <= 8) return ceil(storageGB).toInt()
        for (size in knownSizes) {
            if (storageGB <= size) return size
        }
        return ceil(storageGB).toInt()
    }

    fun getBatteryCapacity(): String {
        val propBattery = firstNonBlankProp(
            "ro.ext.battery",
            "ro.device.battery",
            "persist.sys.device_battery"
        ) ?: return UNKNOWN
        return if (propBattery.any { it.isLetter() }) propBattery else "$propBattery mAh (TYP)"
    }

    fun getScreenResolution(context: Context): String {
        firstNonBlankProp("ro.ext.display")?.let { return it }
        return try {
            val dm = context.getSystemService(DisplayManager::class.java)
            val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
            val mode = display?.mode
            val width = mode?.physicalWidth ?: context.resources.displayMetrics.widthPixels
            val height = mode?.physicalHeight ?: context.resources.displayMetrics.heightPixels
            val hz = (mode?.refreshRate ?: display?.refreshRate ?: 60f).roundToInt()
            "${width}x${height}, ${hz}Hz"
        } catch (_: Throwable) {
            val metrics = context.resources.displayMetrics
            "${metrics.widthPixels}x${metrics.heightPixels}"
        }
    }

    fun getCameraSummary(): String {
        val front = firstNonBlankProp("ro.ext.camera.front", "persist.sys.device_camera_info_front")
        val rear = firstNonBlankProp("ro.ext.camera.rear", "persist.sys.device_camera_info_rear")
        if (front == null && rear == null) {
            return UNKNOWN
        }
        val parts = mutableListOf<String>()
        if (front != null) parts.add(formatPropCamera("Front", front))
        if (rear != null) parts.add(formatPropCamera("Rear", rear))
        return parts.joinToString(" ")
    }

    private fun formatPropCamera(prefix: String, raw: String): String {
        if (raw.contains("front", ignoreCase = true) || raw.contains("rear", ignoreCase = true)) {
            return raw
        }
        val parts = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val formatted = parts.joinToString(" + ") { part ->
            if (part.contains("mp", ignoreCase = true)) part else "${part}MP"
        }
        return "$prefix $formatted"
    }

    fun getSystemCoreSummary(context: Context): String {
        return firstNonBlankProp("ro.ext.system_core")
            ?: context.getString(R.string.hk_about_system_core_value)
    }
}
