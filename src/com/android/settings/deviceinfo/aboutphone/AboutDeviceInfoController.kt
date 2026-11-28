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

package com.android.settings.deviceinfo.aboutphone

import android.app.AlertDialog
import android.app.settings.SettingsEnums
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.os.storage.StorageManager
import android.os.storage.VolumeInfo
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.preference.PreferenceScreen
import com.android.settings.R
import com.android.settings.core.SubSettingLauncher
import com.android.settings.deviceinfo.StorageDashboardFragment
import com.android.settings.deviceinfo.firmwareversion.FirmwareVersionSettings
import com.android.settings.deviceinfo.hardwareinfo.HardwareInfoFragment
import com.android.settings.deviceinfo.storage.StorageAsyncLoader
import com.android.settings.deviceinfo.storage.StorageCacheHelper
import com.android.settings.utils.DeviceInfoUtil
import com.android.settingslib.applications.StorageStatsSource
import com.android.settingslib.core.AbstractPreferenceController
import com.android.settingslib.widget.LayoutPreference
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Binds the ported hero header, dual Device/Storage tiles, and hardware spec card on About Phone.
 */
class AboutDeviceInfoController(
    context: Context,
    private val onDeviceTileClicked: (() -> Unit)? = null
) : AbstractPreferenceController(context) {

    private var screenRef: PreferenceScreen? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val loadingStorage = AtomicBoolean(false)

    override fun isAvailable(): Boolean = true

    override fun getPreferenceKey(): String = KEY_CARDS

    override fun displayPreference(screen: PreferenceScreen) {
        super.displayPreference(screen)
        screenRef = screen
        bindAll(screen)
    }

    fun refresh(screen: PreferenceScreen? = screenRef) {
        if (screen != null) {
            bindAll(screen)
        }
    }

    private fun bindAll(screen: PreferenceScreen) {
        bindHeader(screen)
        bindCards(screen)
        bindSpecCard(screen)
    }

    private fun bindHeader(screen: PreferenceScreen) {
        val cardsPref = screen.findPreference<LayoutPreference>(KEY_CARDS)
        val rootParent = cardsPref?.findViewById<View>(R.id.about_device_info_cards)?.parent?.parent as? View
        rootParent?.findViewById<View>(R.id.about_hero_root)?.let {
            bindHeroViewsFromRoot(it)
        }
    }

    /**
     * Populates the hero banner: device name on top, ROM title in middle,
     * "Tiramisu || <Maintainer>" subtitle, and "ArrowOS | 13.1" bottom pill.
     */
    fun bindHeroViewsFromRoot(rootView: View?) {
        if (rootView == null) return

        rootView.findViewById<TextView>(R.id.about_header_top)?.text =
            DeviceInfoUtil.getDeviceName(mContext)

        rootView.findViewById<TextView>(R.id.about_header_title)?.text =
            DeviceInfoUtil.getRomName(mContext)

        rootView.findViewById<TextView>(R.id.about_header_subtitle)?.apply {
            visibility = View.VISIBLE
            text = DeviceInfoUtil.getMaintainerSubtitle()
        }

        rootView.findViewById<TextView>(R.id.about_header_pill)?.apply {
            text = DeviceInfoUtil.getVersionBadgeText(mContext)
            setOnClickListener {
                SubSettingLauncher(mContext)
                    .setDestination(FirmwareVersionSettings::class.java.name)
                    .setSourceMetricsCategory(SettingsEnums.DEVICEINFO)
                    .launch()
            }
        }
    }

    private fun bindCards(screen: PreferenceScreen) {
        val cardsPref = screen.findPreference<LayoutPreference>(KEY_CARDS) ?: return

        cardsPref.findViewById<TextView>(R.id.hk_device_name_value)?.text =
            DeviceInfoUtil.getDeviceName(mContext)

        cardsPref.findViewById<View>(R.id.hk_about_device_tile)?.setOnClickListener {
            if (onDeviceTileClicked != null) {
                onDeviceTileClicked.invoke()
            } else {
                screen.findPreference<ClickableDeviceNamePreference>(KEY_DEVICE_NAME)?.openDialog()
            }
        }

        cardsPref.findViewById<TextView>(R.id.hk_storage_summary)?.text =
            DeviceInfoUtil.getStorageUsedAndTotalFormatted(mContext)

        cardsPref.findViewById<ColorfulRatioBar>(R.id.hk_storage_progress)?.let { bar ->
            updateStorageBar(bar)
        }

        cardsPref.findViewById<View>(R.id.hk_about_storage_tile)?.setOnClickListener {
            val args = Bundle().apply {
                putString("android.os.storage.extra.VOLUME_ID", "private")
            }
            SubSettingLauncher(mContext)
                .setDestination(StorageDashboardFragment::class.java.name)
                .setArguments(args)
                .setSourceMetricsCategory(SettingsEnums.DEVICEINFO)
                .launch()
        }
    }

    private fun bindSpecCard(screen: PreferenceScreen) {
        val specPref = screen.findPreference<LayoutPreference>(KEY_SPEC) ?: return

        specPref.findViewById<TextView>(R.id.hk_proc_info)?.text = DeviceInfoUtil.getProcessor()
        specPref.findViewById<View>(R.id.processor_click_area)?.setOnClickListener {
            showProcessorDialog()
        }

        specPref.findViewById<TextView>(R.id.hk_battery_info)?.text =
            DeviceInfoUtil.getBatteryCapacity()

        specPref.findViewById<TextView>(R.id.hk_ram_value)?.text =
            DeviceInfoUtil.getPhysicalRamFormatted()
        specPref.findViewById<TextView>(R.id.hk_zram_badge)?.apply {
            val zram = DeviceInfoUtil.getZramFormatted()
            if (zram != null) {
                visibility = View.VISIBLE
                text = zram
            } else {
                visibility = View.GONE
            }
        }
        specPref.findViewById<View>(R.id.ram_click_area)?.setOnClickListener {
            launchRunningServices()
        }

        specPref.findViewById<TextView>(R.id.hk_camera_info)?.text =
            DeviceInfoUtil.getCameraSummary()

        specPref.findViewById<TextView>(R.id.hk_display_info)?.text =
            DeviceInfoUtil.getScreenResolution(mContext)

        specPref.findViewById<TextView>(R.id.hk_system_core_info)?.text =
            DeviceInfoUtil.getSystemCoreSummary(mContext)
        specPref.findViewById<View>(R.id.system_core_click_area)?.setOnClickListener {
            Toast.makeText(mContext, R.string.hk_about_trinity_toast, Toast.LENGTH_SHORT).show()
        }

        specPref.findViewById<TextView>(R.id.hk_model_info)?.text =
            DeviceInfoUtil.getHardwareModel()
        specPref.findViewById<View>(R.id.model_info_click_area)?.setOnClickListener {
            SubSettingLauncher(mContext)
                .setDestination(HardwareInfoFragment::class.java.name)
                .setSourceMetricsCategory(SettingsEnums.DEVICEINFO)
                .launch()
        }
    }

    private fun updateStorageBar(bar: ColorfulRatioBar) {
        val userId = UserHandle.myUserId()
        val cacheHelper = StorageCacheHelper(mContext, userId)
        val cached = cacheHelper.retrieveCachedSize()
        val (usedBytes, rawTotalBytes) = DeviceInfoUtil.getRawStorageBytes(mContext)
        renderStorageBar(bar, cached, usedBytes, rawTotalBytes)

        if (loadingStorage.compareAndSet(false, true)) {
            val barRef = WeakReference(bar)
            val appCtx = mContext.applicationContext
            STORAGE_EXECUTOR.execute {
                try {
                    val storageManager = appCtx.getSystemService(StorageManager::class.java)
                    val userManager = appCtx.getSystemService(UserManager::class.java)
                    val privateVol = storageManager?.findVolumeById(VolumeInfo.ID_PRIVATE_INTERNAL)
                    val uuid = privateVol?.fsUuid
                    if (userManager != null) {
                        val loader = StorageAsyncLoader(
                            appCtx,
                            userManager,
                            uuid,
                            StorageStatsSource(appCtx),
                            appCtx.packageManager
                        )
                        val result = loader.loadInBackground()
                        val data = result?.get(userId)
                        if (data != null) {
                            val fresh = StorageCacheHelper.StorageCache().apply {
                                imagesSize = data.imagesSize
                                videosSize = data.videosSize
                                audioSize = data.audioSize
                                allAppsExceptGamesSize = data.allAppsExceptGamesSize
                                gamesSize = data.gamesSize
                                documentsAndOtherSize = data.documentsAndOtherSize
                                trashSize = data.trashSize
                                var attributedSize = 0L
                                for (i in 0 until result.size()) {
                                    val other = result.valueAt(i)
                                    attributedSize += other.gamesSize + other.audioSize +
                                        other.videosSize + other.imagesSize +
                                        other.documentsAndOtherSize + other.trashSize +
                                        other.allAppsExceptGamesSize - other.duplicateCodeSize
                                }
                                systemSize = maxOf(1L shl 30, usedBytes - attributedSize)
                            }
                            StorageCacheHelper(appCtx, userId).cacheSizeInfo(fresh)
                            mainHandler.post {
                                barRef.get()?.let { targetBar ->
                                    if (targetBar.isAttachedToWindow) {
                                        renderStorageBar(targetBar, fresh, usedBytes, rawTotalBytes)
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {
                } finally {
                    loadingStorage.set(false)
                }
            }
        }
    }

    private fun renderStorageBar(
        bar: ColorfulRatioBar,
        cache: StorageCacheHelper.StorageCache,
        usedBytes: Long,
        rawTotalBytes: Long
    ) {
        val total = rawTotalBytes.coerceAtLeast(1L).toDouble()
        val cachedItems = listOf(
            R.color.settings_storage_category_app to cache.allAppsExceptGamesSize,
            R.color.settings_storage_category_image to cache.imagesSize,
            R.color.settings_storage_category_video to cache.videosSize,
            R.color.settings_storage_category_audio to cache.audioSize,
            R.color.settings_storage_category_doc to cache.documentsAndOtherSize,
            R.color.settings_storage_category_trash to cache.trashSize,
            R.color.settings_storage_category_apk to cache.gamesSize,
            R.color.settings_storage_category_system to cache.systemSize
        )

        val infos = ArrayList<ColorfulRatioBar.RatioInfo>()
        var sumCached = 0L
        for ((colorRes, bytes) in cachedItems) {
            if (bytes > 0L) {
                sumCached += bytes
                infos.add(
                    ColorfulRatioBar.RatioInfo(
                        0,
                        mContext.getColor(colorRes),
                        (bytes / total).coerceIn(0.0, 1.0)
                    )
                )
            }
        }

        if (sumCached <= 0L && usedBytes > 0L) {
            infos.add(
                ColorfulRatioBar.RatioInfo(
                    0,
                    mContext.getColor(R.color.settings_storage_category_system),
                    (usedBytes / total).coerceIn(0.0, 1.0)
                )
            )
        }
        bar.showRatioInfo(infos)
    }

    private fun showProcessorDialog() {
        AlertDialog.Builder(mContext)
            .setTitle(R.string.hk_about_processor_dialog_title)
            .setMessage(DeviceInfoUtil.getProcessorDetails())
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun launchRunningServices() {
        SubSettingLauncher(mContext)
            .setDestination("com.android.settings.applications.RunningServices")
            .setTitleRes(R.string.runningservices_settings_title)
            .setSourceMetricsCategory(SettingsEnums.DEVICEINFO)
            .launch()
    }

    companion object {
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_CARDS = "about_device_info_cards"
        private const val KEY_SPEC = "spec_rom"
        private val STORAGE_EXECUTOR = Executors.newSingleThreadExecutor()
    }
}
