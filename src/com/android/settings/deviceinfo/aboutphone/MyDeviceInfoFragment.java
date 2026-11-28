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

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.deviceinfo.DeviceNamePreferenceController;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.search.SearchIndexable;

import java.util.ArrayList;
import java.util.List;

/**
 * Main About Phone page hosting the ported hero card, device/storage tiles, and hardware specs.
 */
@SearchIndexable
public class MyDeviceInfoFragment extends DashboardFragment
        implements DeviceNamePreferenceController.DeviceNamePreferenceHost {

    private static final String LOG_TAG = "MyDeviceInfoFragment";
    private static final String KEY_DEVICE_NAME = "device_name";

    private AboutDeviceInfoController mAboutController;

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DEVICEINFO;
    }

    @Override
    public int getHelpResource() {
        return R.string.help_uri_about;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        use(DeviceNamePreferenceController.class).setHost(this);
        mAboutController = use(AboutDeviceInfoController.class);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final RecyclerView listView = getListView();
        if (listView != null) {
            listView.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
                @Override
                public void onChildViewAttachedToWindow(@NonNull View child) {
                    if (child.getId() == R.id.about_hero_root && mAboutController != null) {
                        mAboutController.bindHeroViewsFromRoot(child);
                    }
                }

                @Override
                public void onChildViewDetachedFromWindow(@NonNull View child) {
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mAboutController != null) {
            mAboutController.refresh(getPreferenceScreen());
        }
        final RecyclerView listView = getListView();
        if (listView != null) {
            listView.post(() -> {
                if (mAboutController != null && getView() != null) {
                    mAboutController.bindHeroViewsFromRoot(getView());
                }
            });
        }
    }

    @Override
    protected String getLogTag() {
        return LOG_TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.my_device_info;
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        return buildPreferenceControllers(context, this);
    }

    private static List<AbstractPreferenceController> buildPreferenceControllers(
            Context context, MyDeviceInfoFragment fragment) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();
        controllers.add(new AboutDeviceInfoController(context, () -> {
            if (fragment != null) {
                ClickableDeviceNamePreference pref =
                        fragment.findPreference(KEY_DEVICE_NAME);
                if (pref != null) {
                    pref.openDialog();
                }
            }
            return kotlin.Unit.INSTANCE;
        }));
        return controllers;
    }

    @Override
    public void showDeviceNameWarningDialog(String deviceName) {
        DeviceNameWarningDialog.show(this);
    }

    public void onSetDeviceNameConfirm(boolean confirm) {
        final DeviceNamePreferenceController controller = use(DeviceNamePreferenceController.class);
        controller.updateDeviceName(confirm);
        if (confirm && mAboutController != null) {
            mAboutController.refresh(getPreferenceScreen());
        }
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.my_device_info) {
                @Override
                public List<AbstractPreferenceController> createPreferenceControllers(Context context) {
                    return buildPreferenceControllers(context, null);
                }
            };
}
