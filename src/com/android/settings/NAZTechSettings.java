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

import com.android.settings.ShellInterface;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.app.admin.DevicePolicyManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.StatFs;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.provider.Settings;
import android.text.format.Formatter;
import android.util.Log;
import android.view.IWindowManager;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class NAZTechSettings extends PreferenceActivity 
    implements Preference.OnPreferenceChangeListener {
    private static final String TAG = "NAZTechSettings";

    private static final String H_ICON_PREF = "h_icon";
    private CheckBoxPreference mHIconPref;
    private static final String WINDOW_ANIMATIONS_PREF = "window_animations";
    private ListPreference mWindowAnimationsPref;
    private static final String TRANSITION_ANIMATIONS_PREF = "transition_animations";
    private ListPreference mTransitionAnimationsPref;
    private static final String ROTATION_90_PREF = "rotation_90";
    private CheckBoxPreference mRotation90Pref;
    private static final String ROTATION_180_PREF = "rotation_180";
    private CheckBoxPreference mRotation180Pref;
    private static final String ROTATION_270_PREF = "rotation_270";
    private CheckBoxPreference mRotation270Pref;
    private static final String TRACKBALL_WAKE_PREF = "pref_trackball_wake";
    private CheckBoxPreference mTrackballWakePref;
    private static final String TRACKBALL_UNLOCK_PREF = "pref_trackball_unlock";
    private CheckBoxPreference mTrackballUnlockPref;

    private static final String APP2SD_PREF = "app2sd";
    private ListPreference mApp2sdPref;
    private static final String ADB_WIFI_PREF = "adb_wifi";
    private CheckBoxPreference mAdbWifiPref;


    private static final String ADB_PORT = "5555";

    public ProgressDialog patience = null;
    final Handler mHandler = new Handler();
    
    private float[] mAnimationScales;

    private IWindowManager mWindowManager;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mWindowManager = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));

        addPreferencesFromResource(R.xml.naztech_settings);

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

        /* Trackball unlock */
        mTrackballUnlockPref = (CheckBoxPreference) findPreference(TRACKBALL_UNLOCK_PREF);
        mTrackballUnlockPref.setChecked(Settings.System.getInt(getContentResolver(),
                Settings.System.TRACKBALL_UNLOCK_SCREEN, 0) == 1);

        /* Tweaks */
        mApp2sdPref = (ListPreference) findPreference(APP2SD_PREF);
	mApp2sdPref.setOnPreferenceChangeListener(this);
	mAdbWifiPref = (CheckBoxPreference) findPreference(ADB_WIFI_PREF);
	mAdbWifiPref.setOnPreferenceChangeListener(this);

    }

    @Override
    protected void onResume() {
        super.onResume();
        readAnimationPreference(0, mWindowAnimationsPref);
        readAnimationPreference(1, mTransitionAnimationsPref);
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
        else if (preference == mTrackballUnlockPref) {
            value = mTrackballUnlockPref.isChecked();
            Settings.System.putInt(getContentResolver(),
                    Settings.System.TRACKBALL_UNLOCK_SCREEN, value ? 1 : 0);
	}
        return true;
    }


    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
        if (preference == mHIconPref) {
	    Settings.System.putInt(getContentResolver(), 
                    Settings.System.SHOW_H_ICON, mHIconPref.isChecked() ? 0 : 1);
            toast(getResources().getString(R.string.should_reboot));
        }
        if (preference == mWindowAnimationsPref) {
            writeAnimationPreference(0, objValue);
        }
        if (preference == mTransitionAnimationsPref) {
            writeAnimationPreference(1, objValue);
        }
        if (preference == mAdbWifiPref) {
	    boolean have = mAdbWifiPref.isChecked();
	    if (!have) {
		WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
		WifiInfo wifiInfo = wifiManager.getConnectionInfo();
		String ipAddress = null;
		if (wifiInfo != null) {
		    long addr = wifiInfo.getIpAddress();
		    if (addr != 0) {
			// handle negative values whe first octet > 127
			if (addr < 0) addr += 0x100000000L;
			ipAddress = String.format("%d.%d.%d.%d", addr & 0xFF, (addr >> 8) & 0xFF, (addr >> 16) & 0xFF, (addr >> 24) & 0xFF);
		    }
		}
		String[] commands = {
		    "setprop service.adb.tcp.port " + ADB_PORT,
		    "stop adbd",
		    "start adbd"
		};
		sendshell(commands, false,
			  getResources().getString(R.string.adb_instructions_on)
			  .replaceFirst("%ip%", ipAddress)
			  .replaceFirst("%P%", ADB_PORT));
	    } else {
		String[] commands = {
		    "setprop service.adb.tcp.port -1",
		    "stop adbd",
		    "start adbd"
		};
		sendshell(commands, false, getResources().getString(R.string.adb_instructions_off));
	    }
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
        if (preference == mApp2sdPref) {
	    String[] commands = {
		"pm setInstallLocation " + objValue
	    };
	    sendshell(commands, false, getResources().getString(R.string.activate_stock_a2sd));
       }
        return true;
    }



  

    /**
     *  Methods for popups
     */

    public void toast(final CharSequence message) {
	Toast toast = Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT);
	toast.show();
    }

    public void toastLong(final CharSequence message) {
	Toast toast = Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG);
	toast.show();
    }

    public void popup(final String title, final String message) {
	Log.i(TAG, "popup");
	AlertDialog.Builder builder = new AlertDialog.Builder(this);
	builder.setTitle(title)
	    .setMessage(message)
	    .setCancelable(false)
	    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
		    public void onClick(DialogInterface dialog, int id) {
		    }
		});
	AlertDialog alert = builder.create();
	alert.show();
    }

    final Runnable mNeedReboot = new Runnable() {
	    public void run() { needreboot(); }
	};

    public void needreboot() {
	Log.i(TAG, "needreboot");
	AlertDialog.Builder builder = new AlertDialog.Builder(this);
	builder.setMessage("Reboot is requiered to apply. Would you like to reboot now?")
	    .setCancelable(false)
	    .setPositiveButton(getResources().getString(R.string.yes),
			       new DialogInterface.OnClickListener() {
				   public void onClick(DialogInterface dialog, int id) {
				       String[] commands = { "reboot" };
				       sendshell(commands, false, getResources().getString(R.string.rebooting));
				   }
			       })
	    .setNegativeButton(getResources().getString(R.string.no),
			       new DialogInterface.OnClickListener() {
				   public void onClick(DialogInterface dialog, int id) {
				   }
			       });
	AlertDialog alert = builder.create();
	alert.show();
    }

    final Runnable mCommandFinished = new Runnable() {
	    public void run() { patience.cancel(); }
	};

    public boolean sendshell(final String[] commands, final boolean reboot, final String message) {
	if (message != null)
	    patience = ProgressDialog.show(this, "", message, true);
	Thread t = new Thread() {
		public void run() {
		    ShellInterface shell = new ShellInterface(commands);
		    shell.start();
		    while (shell.isAlive())
			{
			    if (message != null)
				patience.setProgress(shell.getStatus());
			    try {
				Thread.sleep(500);
			    }
			    catch (InterruptedException e) {
				e.printStackTrace();
			    }
			}
		    if (message != null)
			mHandler.post(mCommandFinished);
		    if (shell.interrupted())
			popup(getResources().getString(R.string.error), getResources().getString(R.string.download_install_error));
		    if (reboot == true)
			mHandler.post(mNeedReboot);
		}
	    };
	t.start();
	return true;
    }
}
