/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.settings.development;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserManager;
import android.provider.SearchIndexableResource;
import android.support.annotation.VisibleForTesting;
import android.util.Log;
import android.widget.Switch;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.Utils;
import com.android.settings.dashboard.RestrictedDashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.widget.SwitchBar;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.development.DevelopmentSettingsEnabler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DevelopmentSettingsDashboardFragment extends RestrictedDashboardFragment
        implements SwitchBar.OnSwitchChangeListener, OemUnlockDialogHost {

    private static final String TAG = "DevSettingsDashboard";

    private boolean mIsAvailable = true;
    private SwitchBar mSwitchBar;
    private DevelopmentSwitchBarController mSwitchBarController;
    private List<AbstractPreferenceController> mPreferenceControllers = new ArrayList<>();

    public DevelopmentSettingsDashboardFragment() {
        super(UserManager.DISALLOW_DEBUGGING_FEATURES);
    }

    @Override
    public void onActivityCreated(Bundle icicle) {
        super.onActivityCreated(icicle);
        // Apply page-level restrictions
        setIfOnlyAvailableForAdmins(true);
        if (isUiRestricted() || !Utils.isDeviceProvisioned(getActivity())) {
            // Block access to developer options if the user is not the owner, if user policy
            // restricts it, or if the device has not been provisioned
            mIsAvailable = false;
            // Show error message
            if (!isUiRestrictedByOnlyAdmin()) {
                getEmptyTextView().setText(R.string.development_settings_not_available);
            }
            getPreferenceScreen().removeAll();
            return;
        }
        // Set up master switch
        mSwitchBar = ((SettingsActivity) getActivity()).getSwitchBar();
        mSwitchBarController = new DevelopmentSwitchBarController(
                this /* DevelopmentSettings */, mSwitchBar, mIsAvailable, getLifecycle());
        mSwitchBar.show();
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.DEVELOPMENT;
    }

    @Override
    public void onSwitchChanged(Switch switchView, boolean isChecked) {
        if (switchView != mSwitchBar.getSwitch()) {
            return;
        }
        final boolean developmentEnabledState =
                DevelopmentSettingsEnabler.isDevelopmentSettingsEnabled(getContext());
        if (isChecked != developmentEnabledState) {
            if (isChecked) {
                EnableDevelopmentSettingWarningDialog.show(this /* host */);
            } else {
                DevelopmentSettingsEnabler.setDevelopmentSettingsEnabled(getContext(), false);
                for (AbstractPreferenceController controller : mPreferenceControllers) {
                    if (controller instanceof DeveloperOptionsPreferenceController) {
                        ((DeveloperOptionsPreferenceController) controller)
                                .onDeveloperOptionsDisabled();
                    }
                }
            }
        }
    }

    @Override
    public void onOemUnlockDialogConfirmed() {
        final OemUnlockPreferenceController controller = getDevelopmentOptionsController(
                OemUnlockPreferenceController.class);
        controller.onOemUnlockConfirmed();
    }

    @Override
    public void onOemUnlockDialogDismissed() {
        final OemUnlockPreferenceController controller = getDevelopmentOptionsController(
                OemUnlockPreferenceController.class);
        controller.onOemUnlockDismissed();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        for (AbstractPreferenceController controller : mPreferenceControllers) {
            if (controller instanceof DeveloperOptionsPreferenceController) {
                if (((DeveloperOptionsPreferenceController) controller).onActivityResult(
                        requestCode, resultCode, data)) {
                    return;
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getHelpResource() {
        return 0;
    }

    @Override
    protected int getPreferenceScreenResId() {
        Log.d(TAG, "Creating pref screen");
        return R.xml.development_prefs;
    }

    @Override
    protected List<AbstractPreferenceController> getPreferenceControllers(Context context) {
        mPreferenceControllers = buildPreferenceControllers(context, getActivity(), getLifecycle(),
                this /* devOptionsDashboardFragment */);
        return mPreferenceControllers;
    }

    void onEnableDevelopmentOptionsConfirmed() {
        DevelopmentSettingsEnabler.setDevelopmentSettingsEnabled(getContext(), true);
        for (AbstractPreferenceController controller : mPreferenceControllers) {
            if (controller instanceof DeveloperOptionsPreferenceController) {
                ((DeveloperOptionsPreferenceController) controller).onDeveloperOptionsEnabled();
            }
        }
    }

    void onEnableDevelopmentOptionsRejected() {
        // Reset the toggle
        mSwitchBar.setChecked(false);
    }

    private static List<AbstractPreferenceController> buildPreferenceControllers(Context context,
            Activity activity, Lifecycle lifecycle, DevelopmentSettingsDashboardFragment fragment) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();
        controllers.add(new StayAwakePreferenceController(context, lifecycle));
        controllers.add(new BluetoothSnoopLogPreferenceController(context));
        controllers.add(new OemUnlockPreferenceController(context, activity, fragment));
        return controllers;
    }

    @VisibleForTesting
    <T extends AbstractPreferenceController> T getDevelopmentOptionsController(Class<T> clazz) {
        return getPreferenceController(clazz);
    }

    /**
     * For Search.
     */
    public static final Indexable.SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {

                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    return DevelopmentSettingsEnabler.isDevelopmentSettingsEnabled(context);
                }

                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(
                        Context context, boolean enabled) {

                    final SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.development_prefs;
                    return Arrays.asList(sir);
                }

                @Override
                public List<AbstractPreferenceController> getPreferenceControllers(Context
                        context) {
                    return buildPreferenceControllers(context, null /* activity */,
                            null /* lifecycle */, null /* devOptionsDashboardFragment */);
                }
            };
}
