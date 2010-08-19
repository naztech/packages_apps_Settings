/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.settings;

import static android.provider.Settings.System.SCREEN_OFF_TIMEOUT;

import java.util.ArrayList;

import java.util.List;

import android.content.SharedPreferences;
import android.app.admin.DevicePolicyManager;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.Log;
import android.view.IWindowManager;


public class TechParts extends PreferenceActivity implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "TechParts";

    private static final String H_ICON_PREF = "h_icon";
    private static final String WINDOW_ANIMATIONS_PREF = "window_animations";
    private static final String TRANSITION_ANIMATIONS_PREF = "transition_animations";
    private static final String FANCY_IME_ANIMATIONS_PREF = "fancy_ime_animations";
    private static final String ROTATION_90_PREF = "rotation_90";
    private static final String ROTATION_180_PREF = "rotation_180";
    private static final String ROTATION_270_PREF = "rotation_270";
    private static final String TRACKBALL_WAKE_PREF = "pref_trackball_wake";

    private CheckBoxPreference mHIconPref;
    private ListPreference mWindowAnimationsPref;
    private ListPreference mTransitionAnimationsPref;
    private CheckBoxPreference mFancyImeAnimationsPref;
    private CheckBoxPreference mRotation90Pref;
    private CheckBoxPreference mRotation180Pref;
    private CheckBoxPreference mRotation270Pref;
    private CheckBoxPreference mTrackballWakePref;
    private float[] mAnimationScales;

    private IWindowManager mWindowManager;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mWindowManager = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));

        addPreferencesFromResource(R.xml.tech_parts);

        Boolean mCanEnableTrackball = true;

        /* 3G+H icon */
	mHIconPref = (CheckBoxPreference) findPreference(H_ICON_PREF);
	mHIconPref.setOnPreferenceChangeListener(this);
	mHIconPref.setChecked(Settings.System.getInt(getContentResolver(), Settings.System.SHOW_H_ICON, 0) == 1);

        /* Animation from Spareparts */
        mWindowAnimationsPref = (ListPreference) findPreference(WINDOW_ANIMATIONS_PREF);
        mWindowAnimationsPref.setOnPreferenceChangeListener(this);
        mTransitionAnimationsPref = (ListPreference) findPreference(TRANSITION_ANIMATIONS_PREF);
        mTransitionAnimationsPref.setOnPreferenceChangeListener(this);
        mFancyImeAnimationsPref = (CheckBoxPreference) findPreference(FANCY_IME_ANIMATIONS_PREF);

        /* Rotation */
	mRotation90Pref = (CheckBoxPreference) findPreference(ROTATION_90_PREF);
	mRotation90Pref.setOnPreferenceChangeListener(this);
	mRotation180Pref = (CheckBoxPreference) findPreference(ROTATION_180_PREF);
	mRotation180Pref.setOnPreferenceChangeListener(this);
	mRotation270Pref = (CheckBoxPreference) findPreference(ROTATION_270_PREF);
	mRotation270Pref.setOnPreferenceChangeListener(this);
	int mode = Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION_MODE, 5);
	mRotation90Pref.setChecked((mode & 1) != 0);
	mRotation180Pref.setChecked((mode & 2) != 0);
	mRotation270Pref.setChecked((mode & 4) != 0);

        /* Trackball Wake */
        mTrackballWakePref = (CheckBoxPreference) findPreference(TRACKBALL_WAKE_PREF);
        mTrackballWakePref.setEnabled(mCanEnableTrackball);
        mTrackballWakePref.setChecked(Settings.System.getInt(getContentResolver(), 
                Settings.System.TRACKBALL_WAKE_SCREEN, 0) == 1);
    }

   
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        if (FANCY_IME_ANIMATIONS_PREF.equals(key)) {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.FANCY_IME_ANIMATIONS,
                    mFancyImeAnimationsPref.isChecked() ? 1 : 0);
        } 
    }

    @Override
    protected void onResume() {
        super.onResume();
        readAnimationPreference(0, mWindowAnimationsPref);
        readAnimationPreference(1, mTransitionAnimationsPref);
        updateToggles();
    }

    int floatToIndex(float val, int resid) {
        String[] indices = getResources().getStringArray(resid);
        float lastVal = Float.parseFloat(indices[0]);
        for (int i=1; i<indices.length; i++) {
            float thisVal = Float.parseFloat(indices[i]);
            if (val < (lastVal + (thisVal-lastVal)*.5f)) {
                return i-1;
            }
            lastVal = thisVal;
        }
        return indices.length-1;
    }

    public void readAnimationPreference(int which, ListPreference pref) {
        try {
            float scale = mWindowManager.getAnimationScale(which);
            pref.setValueIndex(floatToIndex(scale,
                    R.array.entryvalues_animations));
        } catch (RemoteException e) {
        }
    }

    public void writeAnimationPreference(int which, Object objValue) {
        try {
            float val = Float.parseFloat(objValue.toString());
            mWindowManager.setAnimationScale(which, val);
        } catch (NumberFormatException e) {
        } catch (RemoteException e) {
        }
    }
 
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        boolean value;
        if (preference == mTrackballWakePref) {
            value = mTrackballWakePref.isChecked();
            Settings.System.putInt(getContentResolver(),
                    Settings.System.TRACKBALL_WAKE_SCREEN, value ? 1 : 0);
        } 
        return true;
    }

    private void updateToggles() {
        mFancyImeAnimationsPref.setChecked(Settings.System.getInt(
                getContentResolver(), 
                Settings.System.FANCY_IME_ANIMATIONS, 0) != 0);
    }

    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
        if (preference == mHIconPref) {
	    Settings.System.putInt(getContentResolver(), 
                    Settings.System.SHOW_H_ICON, mHIconPref.isChecked() ? 0 : 1);
        }
        if (preference == mWindowAnimationsPref) {
            writeAnimationPreference(0, objValue);
        }
        if (preference == mTransitionAnimationsPref) {
            writeAnimationPreference(1, objValue);
        }
        if (preference == mRotation90Pref || preference == mRotation180Pref || preference == mRotation270Pref) {
	    int mode = 0;
	    if (mRotation90Pref.isChecked()) mode += 1;
	    if (mRotation180Pref.isChecked()) mode += 2;
	    if (mRotation270Pref.isChecked()) mode += 4;
	    if (preference == mRotation90Pref) mode += 1;
	    if (preference == mRotation180Pref) mode += 2;
	    if (preference == mRotation270Pref) mode += 4;
	    Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION_MODE, mode);
	}
        return true;
    }
}
