package com.example.ecgbleclassification;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

public class FragmentSetting extends PreferenceFragmentCompat {
    final String SETTING_TAG = "SETTING_CHECK";

    SharedPreferences prefs;



    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.fragment_setting, rootKey);
        prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
    }


    SharedPreferences.OnSharedPreferenceChangeListener prefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            // key값에 해당하는 명령 넣기
            if (key.equals("bluetooth")) {
                Log.v(SETTING_TAG,"bluetooth");
            }
            if (key.equals("ecg")) {
                Log.v(SETTING_TAG,"ecg");
            }
        }
    };

}