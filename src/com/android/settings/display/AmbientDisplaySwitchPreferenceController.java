/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.settings.display;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.widget.Switch;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.PreferenceControllerMixin;
import com.android.settings.widget.SwitchBar;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.widget.LayoutPreference;

public class AmbientDisplaySwitchPreferenceController extends AbstractPreferenceController
        implements PreferenceControllerMixin, SwitchBar.OnSwitchChangeListener {

    private static final String KEY = "ambient_display_switch";

    private Context mContext;
    private SwitchBar mSwitch;

    public AmbientDisplaySwitchPreferenceController(Context context) {
        super(context);

        mContext = context;
    }

    @Override
    public String getPreferenceKey() {
        return KEY;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        if (isAvailable()) {
            LayoutPreference pref = screen.findPreference(getPreferenceKey());
            if (pref != null) {
                mSwitch = pref.findViewById(R.id.switch_bar);
                if (mSwitch != null) {
                    mSwitch.addOnSwitchChangeListener(this);
                    mSwitch.show();
                }
            }
        }
    }

    public void setChecked(boolean isChecked) {
        if (mSwitch != null) {
            mSwitch.setChecked(isChecked);
        }
    }

    @Override
    public void updateState(Preference preference) {
        boolean alwaysOnSetting = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.DOZE_ALWAYS_ON, 0) == 1;
        boolean onChargeSetting = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.DOZE_ON_CHARGE, 0) == 1;
        setChecked(alwaysOnSetting || onChargeSetting);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void onSwitchChanged(Switch switchView, boolean isChecked) {
        boolean alwaysOnSetting = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.DOZE_ALWAYS_ON, 0) == 1;
        boolean onChargeSetting = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.DOZE_ON_CHARGE, 0) == 1;
        if (!isChecked) {
            Settings.Secure.putInt(mContext.getContentResolver(),
                    Settings.Secure.DOZE_ALWAYS_ON, 0);
            Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.DOZE_ON_CHARGE, 0);
        } else if (isChecked && !alwaysOnSetting && !onChargeSetting) {
            Settings.Secure.putInt(mContext.getContentResolver(),
                    Settings.Secure.DOZE_ALWAYS_ON, 1);
        }
    }
}
