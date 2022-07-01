package com.example.ecgbleclassification;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;


public class Fragment2_0_Log extends Fragment {

    public static Fragment2_0_Log newInstance() {
        return new Fragment2_0_Log();
    }

    public Fragment2_0_Log() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment2_0_log, container, false);

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();


    }


}