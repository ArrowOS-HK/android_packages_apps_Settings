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

package com.android.settings.deviceinfo.aboutphone;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.PreferenceViewHolder;

import com.android.settings.widget.ValidatedEditTextPreference;

/**
 * Hosts the device-name edit dialog controller cleanly while allowing an external hero tile to open it.
 */
public class ClickableDeviceNamePreference extends ValidatedEditTextPreference {

    public ClickableDeviceNamePreference(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ClickableDeviceNamePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ClickableDeviceNamePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ClickableDeviceNamePreference(Context context) {
        super(context);
    }

    public void openDialog() {
        onClick();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.itemView.setBackground(null);
        holder.setDividerAllowedAbove(false);
        holder.setDividerAllowedBelow(false);
    }
}
