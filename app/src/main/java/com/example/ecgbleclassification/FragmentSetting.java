package com.example.ecgbleclassification;

import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;

public class FragmentSetting extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.fragment_setting, rootKey);
    }
}