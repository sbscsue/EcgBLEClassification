package com.example.ecgbleclassification;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

public class Fragment4_0_Setting extends PreferenceFragmentCompat {
    final String SETTING_TAG = "SETTING_CHECK";

    SharedPreferences prefs;
    ClipboardManager clipboard;

    Preference gitPreference;
    Preference mailPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.fragment4_0_setting, rootKey);

        prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        prefs.registerOnSharedPreferenceChangeListener(prefListener);

    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        clipboard = (ClipboardManager) getActivity().getSystemService(getContext().CLIPBOARD_SERVICE);


        gitPreference = findPreference("Github");
        mailPreference = findPreference("Mail");

        gitPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Log.i("0705","gitgit");

                Intent intentUrl = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sbscsue/EcgBLEClassification"));
                startActivity(intentUrl);
                return false;
            }
        });

        mailPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                ClipData clip = ClipData.newPlainText("label", "sbscsue@gmail.com");
                clipboard.setPrimaryClip(clip);

                Toast.makeText(getActivity().getApplicationContext(),"메일 주소가 클립보드에 복사되었습니다",Toast.LENGTH_SHORT).show();
                return false;
            }
        });





    }

    SharedPreferences.OnSharedPreferenceChangeListener prefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            // key값에 해당하는 명령 넣기

            if(key.equals("TopNotification")){
                Log.v("0705",String.valueOf(prefs.getBoolean(key,true)));

                if(getActivity().getApplicationContext()!=null){
                    Toast.makeText(getActivity().getApplicationContext(),"재시작 후 적용",Toast.LENGTH_SHORT).show();
                }
            }

        }


    };

}