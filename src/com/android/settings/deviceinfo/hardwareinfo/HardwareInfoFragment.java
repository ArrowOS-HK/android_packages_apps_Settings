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

package com.android.settings.deviceinfo.hardwareinfo;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.os.Build;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.deviceinfo.HardwareInfoPreferenceController;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.utils.DeviceInfoUtil;
import com.android.settingslib.search.SearchIndexable;

/**
 * Displays the device illustration card, brand header, and auto-detected hardware specifications.
 */
@SearchIndexable
public class HardwareInfoFragment extends DashboardFragment {

    public static final String TAG = "HardwareInfo";

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DIALOG_SETTINGS_HARDWARE_INFO;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.hardware_info;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public void onResume() {
        super.onResume();
        final RecyclerView listView = getListView();
        if (listView != null) {
            listView.post(this::bindModelInfo);
        }
    }

    private void bindModelInfo() {
        final View root = getView();
        final Context ctx = getContext();
        if (root == null || ctx == null) {
            return;
        }

        setText(root, R.id.custom_model_brand_name, DeviceInfoUtil.INSTANCE.getBrandDisplayName(ctx));
        setText(root, R.id.custom_model_device_value, DeviceInfoUtil.INSTANCE.getHardwareModel());
        setText(root, R.id.custom_model_serial_value, resolveSerialNumber());
        setText(root, R.id.custom_model_platform_value,
                SystemProperties.get("ro.board.platform", "Unknown"));
        setText(root, R.id.custom_model_ram_value, DeviceInfoUtil.INSTANCE.getTotalRam());
        setText(root, R.id.custom_model_hardware_value, resolveHardwareRevision());
        setText(root, R.id.custom_model_codename_value, Build.DEVICE);
    }

    private static void setText(View root, int id, CharSequence value) {
        TextView view = root.findViewById(id);
        if (view != null && !TextUtils.isEmpty(value)) {
            view.setText(value);
        }
    }

    private static String resolveSerialNumber() {
        try {
            String serial = Build.getSerial();
            if (!TextUtils.isEmpty(serial) && !Build.UNKNOWN.equalsIgnoreCase(serial)) {
                return serial;
            }
        } catch (SecurityException ignored) {
        }
        String propSerial = SystemProperties.get("ro.serialno", "");
        return !TextUtils.isEmpty(propSerial) ? propSerial : "Unknown";
    }

    private static String resolveHardwareRevision() {
        String rev = SystemProperties.get("ro.boot.hardware.revision", "");
        if (!TextUtils.isEmpty(rev)) {
            return rev;
        }
        String hw = SystemProperties.get("ro.hardware", Build.HARDWARE);
        return !TextUtils.isEmpty(hw) ? hw : "Unknown";
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.hardware_info) {
                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    return context.getResources().getBoolean(R.bool.config_show_device_model);
                }
            };
}
