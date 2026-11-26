/*
 * Copyright (C) 2026 HK Projection
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

package com.android.settings.bluetooth;

import static androidx.lifecycle.Lifecycle.Event.ON_PAUSE;
import static androidx.lifecycle.Lifecycle.Event.ON_RESUME;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.text.TextUtils;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.widget.SummaryUpdater;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.core.AbstractPreferenceController;

import java.util.Set;

/**
 * Preference controller for the Bluetooth entry on the Settings homepage.
 *
 * <p>The summary mirrors the OxygenOS style: "Off" while Bluetooth is disabled, "On" while it is
 * enabled but idle, and the name of the connected device when something is paired and connected.
 */
public class TopLevelBluetoothPreferenceController extends AbstractPreferenceController
        implements LifecycleObserver, SummaryUpdater.OnSummaryChangeListener {

    public static final String KEY = "top_level_bluetooth";

    private final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private final BluetoothSummaryUpdater mSummaryUpdater;
    private Preference mPreference;

    public TopLevelBluetoothPreferenceController(Context context, Lifecycle lifecycle) {
        super(context);
        if (lifecycle == null) {
            throw new IllegalArgumentException("Lifecycle must be set");
        }
        final LocalBluetoothManager bluetoothManager = Utils.getLocalBtManager(context);
        mSummaryUpdater = bluetoothManager != null
                ? new BluetoothSummaryUpdater(context, this, bluetoothManager) : null;
        lifecycle.addObserver(this);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(KEY);
    }

    @Override
    public void updateState(Preference preference) {
        if (preference == null) {
            preference = mPreference;
        }
        if (preference != null) {
            preference.setSummary(getHomepageSummary());
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getPreferenceKey() {
        return KEY;
    }

    /** @OnLifecycleEvent(ON_RESUME) */
    @OnLifecycleEvent(ON_RESUME)
    public void onResume() {
        if (mSummaryUpdater != null) {
            mSummaryUpdater.register(true);
        }
        updateSummary();
    }

    /** @OnLifecycleEvent(ON_PAUSE) */
    @OnLifecycleEvent(ON_PAUSE)
    public void onPause() {
        if (mSummaryUpdater != null) {
            mSummaryUpdater.register(false);
        }
    }

    @Override
    public void onSummaryChanged(String summary) {
        updateSummary();
    }

    private void updateSummary() {
        if (mPreference != null) {
            mPreference.setSummary(getHomepageSummary());
        }
    }

    private String getHomepageSummary() {
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            return mContext.getString(R.string.bluetooth_disabled);
        }

        final String deviceName = getConnectedDeviceName();
        if (!TextUtils.isEmpty(deviceName)) {
            return deviceName;
        }

        return mContext.getString(R.string.switch_on_text);
    }

    private String getConnectedDeviceName() {
        try {
            final Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
            if (devices == null) {
                return null;
            }

            int connectedCount = 0;
            String connectedName = null;
            for (BluetoothDevice device : devices) {
                if (device.isConnected()) {
                    connectedCount++;
                    if (connectedName == null) {
                        connectedName = !TextUtils.isEmpty(device.getAlias())
                                ? device.getAlias() : device.getName();
                    }
                }
            }

            if (connectedCount > 1) {
                return mContext.getString(
                        R.string.bluetooth_connected_multiple_devices_summary);
            }
            return connectedName;
        } catch (SecurityException e) {
            return null;
        }
    }
}
