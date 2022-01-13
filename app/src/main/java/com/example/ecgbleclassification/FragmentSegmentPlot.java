package com.example.ecgbleclassification;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;
import java.util.List;


public class FragmentSegmentPlot extends Fragment {
    Resources res;

    //plot
    int SEGMENT_LENGTH;

    private LineChart chart;
    ArrayList<Entry> chart_entry = new ArrayList<Entry>();
    List<ILineDataSet> chart_set = new ArrayList<ILineDataSet>();
    LineData chart_data;


    //receiver
    Receiver receiver;
    IntentFilter theFilter;



    public FragmentSegmentPlot() {
        // Required empty public constructor
    }



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        res = getResources();

        SEGMENT_LENGTH = res.getInteger(R.integer.segment_length);

        theFilter = new IntentFilter();
        theFilter.addAction("SEGMENT_PLOT");

        receiver = new Receiver(){
            @Override
            public void onReceive(Context context, Intent intent) {
                super.onReceive(context, intent);
                if(intent.getAction().equals("SEGMENT_PLOT")){
                    Log.i(BROADCAST_TAG,intent.getAction());
                    //plot(intent.getByteArrayExtra("BLE_DATA"));
                }
            }
        };


    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_segment_plot, container, false);

        chart = view.findViewById(R.id.chartSegment);
        chart.setBackgroundColor(Color.WHITE);
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);

        requireActivity().registerReceiver(receiver,theFilter);

        return view;
    }
}