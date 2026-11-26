/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.settings.homepage;

import static com.android.settings.search.actionbar.SearchMenuController.NEED_SEARCH_ICON_IN_ACTION_BAR;
import static com.android.settingslib.search.SearchIndexable.MOBILE;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Process;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;
import androidx.window.embedding.ActivityEmbeddingController;

import com.android.internal.util.UserIcons;
import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.activityembedding.ActivityEmbeddingRulesController;
import com.android.settings.activityembedding.ActivityEmbeddingUtils;
import com.android.settings.bluetooth.TopLevelBluetoothPreferenceController;
import com.android.settings.connecteddevice.BluetoothDashboardFragment;
import com.android.settings.connecteddevice.ConnectedDeviceDashboardFragment;
import com.android.settings.core.OnActivityResultListener;
import com.android.settings.core.SubSettingLauncher;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.network.HomepageWifiPreferenceController;
import com.android.settings.network.NetworkDashboardFragment;
import com.android.settings.network.NetworkProviderSettings;
import com.android.settings.overlay.FeatureFactory;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.support.SupportPreferenceController;
import com.android.settings.widget.HomepagePreference;
import com.android.settings.widget.HomepagePreferenceLayoutHelper.HomepagePreferenceLayout;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.core.instrumentation.Instrumentable;
import com.android.settingslib.core.instrumentation.MetricsFeatureProvider;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.drawer.Tile;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.users.EditUserPhotoController;
import com.android.settingslib.utils.ThreadUtils;
import com.android.settingslib.widget.LayoutPreference;

import java.util.ArrayList;
import java.util.List;

@SearchIndexable(forTarget = MOBILE)
public class TopLevelSettings extends DashboardFragment implements SplitLayoutListener,
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback, OnActivityResultListener {

    private static final String TAG = "TopLevelSettings";
    private static final String SAVED_HIGHLIGHT_MIXIN = "highlight_mixin";
    private static final String PREF_KEY_SUPPORT = "top_level_support";
    private static final String PREF_KEY_PROFILE = "top_level_profile";
    private static final String PROFILE_PREFS = "arrow_homepage";
    private static final String PROFILE_SUMMARY_KEY = "profile_summary";

    private boolean mIsEmbeddingActivityEnabled;
    private TopLevelHighlightMixin mHighlightMixin;
    private int mPaddingHorizontal;
    private boolean mScrollNeeded = true;
    private boolean mFirstStarted = true;
    private ActivityEmbeddingController mActivityEmbeddingController;
    private EditUserPhotoController mEditUserPhotoController;
    private ImageView mProfileAvatar;
    private TextView mProfileTitle;
    private TextView mProfileSummary;

    public TopLevelSettings() {
        final Bundle args = new Bundle();
        // Disable the search icon because this page uses a full search view in actionbar.
        args.putBoolean(NEED_SEARCH_ICON_IN_ACTION_BAR, false);
        setArguments(args);
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.arrow_dashboard;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DASHBOARD_SUMMARY;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        HighlightableMenu.fromXml(context, getPreferenceScreenResId());
        use(SupportPreferenceController.class).setActivity(getActivity());
    }

    @Override
    public int getHelpResource() {
        // Disable the help icon because this page uses a full search view in actionbar.
        return 0;
    }

    @Override
    public Fragment getCallbackFragment() {
        return this;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (isDuplicateClick(preference)) {
            return true;
        }

        // Register SplitPairRule for SubSettings.
        ActivityEmbeddingRulesController.registerSubSettingsPairRule(getContext(),
                true /* clearTop */);

        setHighlightPreferenceKey(preference.getKey());
        if (launchHomepageSettings(preference)) {
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    /**
     * Launches the settings page behind one of the connectivity entries on the homepage.
     *
     * <p>These entries are started explicitly (instead of using the fragment declared in XML) so
     * that every page gets the proper title and destination: Wi-Fi opens the Wi-Fi network list,
     * Bluetooth opens the Bluetooth screen, Mobile network opens Network &amp; internet, and
     * Connection &amp; sharing opens the Connected devices screen.
     */
    private boolean launchHomepageSettings(Preference preference) {
        final String key = preference.getKey();
        if (key == null) {
            return false;
        }
        final String destination;
        final int titleRes;
        switch (key) {
            case HomepageWifiPreferenceController.KEY:
                destination = NetworkProviderSettings.class.getName();
                titleRes = R.string.wifi_settings;
                break;
            case TopLevelBluetoothPreferenceController.KEY:
                destination = BluetoothDashboardFragment.class.getName();
                titleRes = R.string.bluetooth_settings_title;
                break;
            case "top_level_mobile_network":
                destination = NetworkDashboardFragment.class.getName();
                titleRes = R.string.network_dashboard_title;
                break;
            case "connection_sharing":
                destination = ConnectedDeviceDashboardFragment.class.getName();
                titleRes = R.string.connected_devices_dashboard_title;
                break;
            default:
                return false;
        }

        new SubSettingLauncher(getActivity())
                .setDestination(destination)
                .setTitleRes(titleRes)
                .setSourceMetricsCategory(getMetricsCategory())
                .setIsSecondLayerPage(true)
                .launch();
        return true;
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
        new SubSettingLauncher(getActivity())
                .setDestination(pref.getFragment())
                .setArguments(pref.getExtras())
                .setSourceMetricsCategory(caller instanceof Instrumentable
                        ? ((Instrumentable) caller).getMetricsCategory()
                        : Instrumentable.METRICS_CATEGORY_UNKNOWN)
                .setTitleRes(-1)
                .setIsSecondLayerPage(true)
                .launch();
        return true;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mIsEmbeddingActivityEnabled =
                ActivityEmbeddingUtils.isEmbeddingActivityEnabled(getContext());
        if (!mIsEmbeddingActivityEnabled) {
            return;
        }

        boolean activityEmbedded = isActivityEmbedded();
        if (icicle != null) {
            mHighlightMixin = icicle.getParcelable(SAVED_HIGHLIGHT_MIXIN);
            mScrollNeeded = !mHighlightMixin.isActivityEmbedded() && activityEmbedded;
            mHighlightMixin.setActivityEmbedded(activityEmbedded);
        }
        if (mHighlightMixin == null) {
            mHighlightMixin = new TopLevelHighlightMixin(activityEmbedded);
        }
    }

    /** Wrap ActivityEmbeddingController#isActivityEmbedded for testing. */
    public boolean isActivityEmbedded() {
        if (mActivityEmbeddingController == null) {
            mActivityEmbeddingController = ActivityEmbeddingController.getInstance(getActivity());
        }
        return mActivityEmbeddingController.isActivityEmbedded(getActivity());
    }

    @Override
    public void onStart() {
        if (mFirstStarted) {
            mFirstStarted = false;
            FeatureFactory.getFactory(getContext()).getSearchFeatureProvider().sendPreIndexIntent(
                    getContext());
        } else if (mIsEmbeddingActivityEnabled && isOnlyOneActivityInTask()
                && !isActivityEmbedded()) {
            // Set default highlight menu key for 1-pane homepage since it will show the placeholder
            // page once changing back to 2-pane.
            Log.i(TAG, "Set default menu key");
            setHighlightMenuKey(getString(SettingsHomepageActivity.DEFAULT_HIGHLIGHT_MENU_KEY),
                    /* scrollNeeded= */ false);
        }
        super.onStart();
        initProfileCard();
    }

    private boolean isOnlyOneActivityInTask() {
        final ActivityManager.RunningTaskInfo taskInfo = getSystemService(ActivityManager.class)
                .getRunningTasks(1).get(0);
        return taskInfo.numActivities == 1;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mHighlightMixin != null) {
            outState.putParcelable(SAVED_HIGHLIGHT_MIXIN, mHighlightMixin);
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        initPreferenceCard();
    }

    private void initPreferenceCard() {
        final PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            return;
        }

        // The last card can be a tile injected by another package (for example the Google
        // settings tile), so it is resolved dynamically instead of by key.
        final Preference lastPreference = getLastVisiblePreference(screen);

        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            final Preference pref = screen.getPreference(i);
            final String key = pref.getKey();
            if (key == null) {
                continue;
            }

            if (PREF_KEY_PROFILE.equals(key)) {
                // The profile card provides its own layout.
                continue;
            }

            if ("airplane_mode".equals(key)) {
                pref.setLayoutResource(R.layout.arrow_homepage_prefswitch_top);
            } else if (key.contains("wellbeing")) {
                pref.setLayoutResource(R.layout.arrow_dashboard_pref_wellbeing);
            } else if (isCardTop(key)) {
                pref.setLayoutResource(R.layout.arrow_card_homepage_oplus_top);
            } else if (isCardBottom(key) || pref == lastPreference) {
                pref.setLayoutResource(isCardWithSummary(key)
                        ? R.layout.arrow_card_homepage_oplus_bottom_summary
                        : R.layout.arrow_card_homepage_oplus_bottom);
            } else if (isCardWithSummary(key)) {
                pref.setLayoutResource(R.layout.arrow_card_homepage_oplus_middle_summary);
            } else {
                pref.setLayoutResource(R.layout.arrow_card_homepage_oplus_middle);
            }
        }
    }

    /** First preference of a grouped card. */
    private static boolean isCardTop(String key) {
        switch (key) {
            case "top_level_sharpener":
            case "top_level_apps":
            case "top_level_privacy":
            case "top_level_accessibility":
                return true;
            default:
                return false;
        }
    }

    /** Last preference of a grouped card. The final card is resolved dynamically. */
    private static boolean isCardBottom(String key) {
        switch (key) {
            case "connection_sharing":
            case "top_level_sound":
            case "top_level_storage":
            case "top_level_security":
                return true;
            default:
                return false;
        }
    }

    /** Returns the last preference that is currently visible, or null if there is none. */
    private static Preference getLastVisiblePreference(PreferenceScreen screen) {
        for (int i = screen.getPreferenceCount() - 1; i >= 0; i--) {
            final Preference pref = screen.getPreference(i);
            if (pref.isVisible() && pref.getKey() != null) {
                return pref;
            }
        }
        return null;
    }

    /** Homepage cards that show a summary line; every other card is title-only. */
    private static boolean isCardWithSummary(String key) {
        switch (key) {
            case "internet_settings":
            case "top_level_bluetooth":
            case "top_level_battery":
            case "top_level_storage":
                return true;
            default:
                return false;
        }
    }

    /**
     * Wires up the profile card shown above the connectivity entries. Tapping the card opens a
     * single dialog where the user photo, name and custom status can all be edited at once.
     */
    private void initProfileCard() {
        final PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            return;
        }
        final Preference profilePreference = screen.findPreference(PREF_KEY_PROFILE);
        if (!(profilePreference instanceof LayoutPreference)) {
            return;
        }
        final LayoutPreference profileCard = (LayoutPreference) profilePreference;

        final View card = profileCard.findViewById(R.id.arrow_profile_card);
        mProfileAvatar = profileCard.findViewById(R.id.arrow_profile_avatar);
        mProfileTitle = profileCard.findViewById(R.id.arrow_profile_title);
        mProfileSummary = profileCard.findViewById(R.id.arrow_profile_summary);
        if (card == null || mProfileAvatar == null || mProfileTitle == null
                || mProfileSummary == null) {
            return;
        }

        updateProfileCard();

        card.setOnClickListener(v -> showProfileEditDialog());
        mProfileAvatar.setOnClickListener(v -> showProfileEditDialog());
        mProfileAvatar.setContentDescription(
                getString(R.string.arrow_dashboard_profile_edit_title));
        mProfileTitle.setOnClickListener(v -> showProfileEditDialog());
        mProfileSummary.setOnClickListener(v -> showProfileEditDialog());
    }

    private void updateProfileCard() {
        final Context context = getContext();
        if (context == null || mProfileTitle == null) {
            return;
        }

        final UserManager userManager = context.getSystemService(UserManager.class);
        final UserInfo userInfo = Utils.getExistingUser(userManager, Process.myUserHandle());
        if (userInfo == null) {
            return;
        }

        mProfileTitle.setText(getString(R.string.arrow_dashboard_profile_greeting, userInfo.name));
        mProfileSummary.setText(getProfileSummary());
        mProfileAvatar.setImageDrawable(
                com.android.settingslib.Utils.getUserIcon(context, userManager, userInfo));
    }

    private String getProfileSummary() {
        final Context context = getContext();
        if (context == null) {
            return "";
        }
        final String summary = context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
                .getString(PROFILE_SUMMARY_KEY, null);
        return TextUtils.isEmpty(summary)
                ? getString(R.string.arrow_dashboard_profile_summary_default)
                : summary;
    }

    private void showProfileEditDialog() {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        final UserManager userManager = activity.getSystemService(UserManager.class);
        final UserInfo userInfo = Utils.getExistingUser(userManager, Process.myUserHandle());
        if (userInfo == null) {
            return;
        }

        final Drawable oldIcon = com.android.settingslib.Utils.getUserIcon(
                activity, userManager, userInfo);
        final View content = LayoutInflater.from(activity)
                .inflate(R.layout.arrow_profile_edit_dialog, null);
        final ImageView avatar = content.findViewById(R.id.arrow_profile_dialog_avatar);
        final EditText nameInput = content.findViewById(R.id.arrow_profile_dialog_name);
        final EditText statusInput = content.findViewById(R.id.arrow_profile_dialog_status);

        avatar.setImageDrawable(oldIcon);
        nameInput.setText(userInfo.name);
        nameInput.setSelection(nameInput.getText().length());
        statusInput.setText(getProfileSummary());
        statusInput.setSelection(statusInput.getText().length());

        mEditUserPhotoController = new EditUserPhotoController(activity,
                this::startActivityForResult, avatar, null /* savedBitmap */, oldIcon,
                Utils.FILE_PROVIDER_AUTHORITY);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.arrow_dashboard_profile_edit_title)
                .setView(content)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    final Drawable newIcon = mEditUserPhotoController.getNewUserPhotoDrawable();
                    if (newIcon != null && newIcon != oldIcon) {
                        // Show the picked photo right away; persisting happens in the background.
                        mProfileAvatar.setImageDrawable(newIcon);
                        final Bitmap bitmap = UserIcons.convertToBitmapAtUserIconSize(
                                activity.getResources(), newIcon);
                        ThreadUtils.postOnBackgroundThread(
                                () -> userManager.setUserIcon(userInfo.id, bitmap));
                    }

                    final String newName = nameInput.getText().toString().trim();
                    if (!TextUtils.isEmpty(newName) && !newName.equals(userInfo.name)) {
                        userManager.setUserName(userInfo.id, newName);
                        mProfileTitle.setText(
                                getString(R.string.arrow_dashboard_profile_greeting, newName));
                    }

                    final String newStatus = statusInput.getText().toString().trim();
                    activity.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
                            .edit()
                            .putString(PROFILE_SUMMARY_KEY, newStatus)
                            .apply();
                    mProfileSummary.setText(getProfileSummary());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (mEditUserPhotoController != null) {
            mEditUserPhotoController.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        highlightPreferenceIfNeeded();
    }

    @Override
    public void onSplitLayoutChanged(boolean isRegularLayout) {
        iteratePreferences(preference -> {
            if (preference instanceof HomepagePreferenceLayout) {
                ((HomepagePreferenceLayout) preference).getHelper().setIconVisible(isRegularLayout);
            }
        });
    }

    @Override
    public void highlightPreferenceIfNeeded() {
        if (mHighlightMixin != null) {
            mHighlightMixin.highlightPreferenceIfNeeded();
        }
    }

    @Override
    public RecyclerView onCreateRecyclerView(LayoutInflater inflater, ViewGroup parent,
            Bundle savedInstanceState) {
        RecyclerView recyclerView = super.onCreateRecyclerView(inflater, parent,
                savedInstanceState);
        recyclerView.setPadding(mPaddingHorizontal, 0, mPaddingHorizontal, 0);
        return recyclerView;
    }

    /** Sets the horizontal padding */
    public void setPaddingHorizontal(int padding) {
        mPaddingHorizontal = padding;
        RecyclerView recyclerView = getListView();
        if (recyclerView != null) {
            recyclerView.setPadding(padding, 0, padding, 0);
        }
    }

    /** Updates the preference internal paddings */
    public void updatePreferencePadding(boolean isTwoPane) {
        iteratePreferences(new PreferenceJob() {
            private int mIconPaddingStart;
            private int mTextPaddingStart;

            @Override
            public void init() {
                mIconPaddingStart = getResources().getDimensionPixelSize(isTwoPane
                        ? R.dimen.homepage_preference_icon_padding_start_two_pane
                        : R.dimen.homepage_preference_icon_padding_start);
                mTextPaddingStart = getResources().getDimensionPixelSize(isTwoPane
                        ? R.dimen.homepage_preference_text_padding_start_two_pane
                        : R.dimen.homepage_preference_text_padding_start);
            }

            @Override
            public void doForEach(Preference preference) {
                if (preference instanceof HomepagePreferenceLayout) {
                    ((HomepagePreferenceLayout) preference).getHelper()
                            .setIconPaddingStart(mIconPaddingStart);
                    ((HomepagePreferenceLayout) preference).getHelper()
                            .setTextPaddingStart(mTextPaddingStart);
                }
            }
        });
    }

    /** Returns a {@link TopLevelHighlightMixin} that performs highlighting */
    public TopLevelHighlightMixin getHighlightMixin() {
        return mHighlightMixin;
    }

    /** Highlight a preference with specified preference key */
    public void setHighlightPreferenceKey(String prefKey) {
        // Skip Tips & support since it's full screen
        if (mHighlightMixin != null && !TextUtils.equals(prefKey, PREF_KEY_SUPPORT)) {
            mHighlightMixin.setHighlightPreferenceKey(prefKey);
        }
    }

    /** Returns whether clicking the specified preference is considered as a duplicate click. */
    public boolean isDuplicateClick(Preference pref) {
        /* Return true if
         * 1. the device supports activity embedding, and
         * 2. the target preference is highlighted, and
         * 3. the current activity is embedded */
        return mHighlightMixin != null
                && TextUtils.equals(pref.getKey(), mHighlightMixin.getHighlightPreferenceKey())
                && isActivityEmbedded();
    }

    /** Show/hide the highlight on the menu entry for the search page presence */
    public void setMenuHighlightShowed(boolean show) {
        if (mHighlightMixin != null) {
            mHighlightMixin.setMenuHighlightShowed(show);
        }
    }

    /** Highlight and scroll to a preference with specified menu key */
    public void setHighlightMenuKey(String menuKey, boolean scrollNeeded) {
        if (mHighlightMixin != null) {
            mHighlightMixin.setHighlightMenuKey(menuKey, scrollNeeded);
        }
    }

    @Override
    protected boolean shouldForceRoundedIcon() {
        return getContext().getResources()
                .getBoolean(R.bool.config_force_rounded_icon_TopLevelSettings);
    }

    @Override
    protected RecyclerView.Adapter onCreateAdapter(PreferenceScreen preferenceScreen) {
        if (!mIsEmbeddingActivityEnabled || !(getActivity() instanceof SettingsHomepageActivity)) {
            return super.onCreateAdapter(preferenceScreen);
        }
        return mHighlightMixin.onCreateAdapter(this, preferenceScreen, mScrollNeeded);
    }

    @Override
    protected Preference createPreference(Tile tile) {
        return new HomepagePreference(getPrefContext());
    }

    void reloadHighlightMenuKey() {
        if (mHighlightMixin != null) {
            mHighlightMixin.reloadHighlightMenuKey(getArguments());
        }
    }

    private void iteratePreferences(PreferenceJob job) {
        if (job == null || getPreferenceManager() == null) {
            return;
        }
        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            return;
        }

        job.init();
        int count = screen.getPreferenceCount();
        for (int i = 0; i < count; i++) {
            Preference preference = screen.getPreference(i);
            if (preference == null) {
                break;
            }
            job.doForEach(preference);
        }
    }

    private interface PreferenceJob {
        default void init() {
        }

        void doForEach(Preference preference);
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        return buildPreferenceControllers(context, getSettingsLifecycle(),
                mMetricsFeatureProvider, this /* fragment */);
    }

    private static List<AbstractPreferenceController> buildPreferenceControllers(Context context,
            Lifecycle lifecycle, MetricsFeatureProvider metricsFeatureProvider,
            Fragment fragment) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();
        controllers.add(new HomepageWifiPreferenceController(context, lifecycle));
        controllers.add(new TopLevelBluetoothPreferenceController(context, lifecycle));
        return controllers;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.arrow_dashboard) {

                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    // Never searchable, all entries in this page are already indexed elsewhere.
                    return false;
                }
            };
}
