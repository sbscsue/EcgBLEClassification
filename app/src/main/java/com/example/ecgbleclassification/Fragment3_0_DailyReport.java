package com.example.ecgbleclassification;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;


public class Fragment3_0_DailyReport extends Fragment {

    public static Fragment3_0_DailyReport newInstance() {
        return new Fragment3_0_DailyReport();
    }

    public Fragment3_0_DailyReport() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment3_0_daily_report, container, false);

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();


    }


}