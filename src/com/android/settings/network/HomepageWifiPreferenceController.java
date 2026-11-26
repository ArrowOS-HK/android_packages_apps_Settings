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

package com.android.settings.network;

import static androidx.lifecycle.Lifecycle.Event.ON_PAUSE;
import static androidx.lifecycle.Lifecycle.Event.ON_RESUME;

import android.content.Context;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.wifi.WifiSummaryUpdater;
import com.android.settings.widget.SummaryUpdater;
import com.android.settingslib.core.AbstractPreferenceController;

/**
 * Preference controller for the Wi-Fi entry on the Settings homepage.
 *
 * <p>Unlike {@link InternetPreferenceController}, this controller always reports the state of
 * Wi-Fi itself, regardless of whether the device is currently using cellular or ethernet. The
 * summary shows the connected SSID, "Off" when Wi-Fi is disabled, or "Not connected" when Wi-Fi
 * is enabled but not associated with a network.
 */
public class HomepageWifiPreferenceController extends AbstractPreferenceController implements
        LifecycleObserver, SummaryUpdater.OnSummaryChangeListener {

    public static final String KEY = InternetPreferenceController.KEY;

    private final WifiSummaryUpdater mSummaryHelper;
    private Preference mPreference;

    public HomepageWifiPreferenceController(Context context, Lifecycle lifecycle) {
        super(context);
        if (lifecycle == null) {
            throw new IllegalArgumentException("Lifecycle must be set");
        }
        mSummaryHelper = new WifiSummaryUpdater(context, this);
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
            preference.setSummary(mSummaryHelper.getSummary());
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
        mSummaryHelper.register(true);
    }

    /** @OnLifecycleEvent(ON_PAUSE) */
    @OnLifecycleEvent(ON_PAUSE)
    public void onPause() {
        mSummaryHelper.register(false);
    }

    @Override
    public void onSummaryChanged(String summary) {
        if (mPreference != null) {
            mPreference.setSummary(summary);
        }
    }
}
