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
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieRadarChartBase;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class FragmentSegmentPlot extends Fragment {
    Resources res;

    //for debug
    int debug_cnt;
    TextView cntView;

    //BPM
    TextView bpmView;
    TextView predictView;


    //plot
    int SAMPLING_LATE;
    float PERIOD;

    int SEGMENT_LENGTH;
    LineChart chart;


    //receiver
    Receiver receiver;
    IntentFilter theFilter;



    public FragmentSegmentPlot() {
        // Required empty public constructor
    }



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        debug_cnt = 0;

        res = getResources();

        SEGMENT_LENGTH = res.getInteger(R.integer.segment_length);

        theFilter = new IntentFilter();
        theFilter.addAction("segmentation");
        theFilter.addAction("INFORMATION");

        receiver = new Receiver(){
            @Override
            public void onReceive(Context context, Intent intent) {
                super.onReceive(context, intent);
                if(intent.getAction().equals("segmentation")){
                    Log.i(BROADCAST_TAG,"segmentation finish");
                    debug_cnt +=1;
                    cntView.setText(String.valueOf(debug_cnt));
                    plot(intent.getFloatArrayExtra("data"));
                }
                if(intent.getAction().equals("INFORMATION")){
                    Log.i(BROADCAST_TAG,intent.getAction());
                    bpmView.setText(String.valueOf(intent.getIntExtra("BPM",0)));
                    predictView.setText(String.valueOf((intent.getStringExtra("PREDICT"))));
                }
            }
        };


    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_segment_plot, container, false);

        debug_cnt = 0;
        cntView = view.findViewById(R.id.segmentIndex);
        cntView.setText(String.valueOf(debug_cnt));

        bpmView = view.findViewById(R.id.bpmSeg);
        predictView = view.findViewById(R.id.predictSeg);



        chart = view.findViewById(R.id.chartSegment);
        chart.setBackgroundColor(Color.WHITE);
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);

        requireActivity().registerReceiver(receiver,theFilter);

        return view;
    }
    @Override
    public void onPause() {
        super.onPause();
        requireActivity().unregisterReceiver(receiver);
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().registerReceiver(receiver,theFilter);
    }
    private void plot(float[] data){
        ArrayList<Entry> chart_entry = new ArrayList<Entry>();
        LineDataSet data_set;

        List<ILineDataSet> chart_set = new ArrayList<ILineDataSet>();
        LineData chart_data;


        for(int i =0;i < data.length; i++){
            Entry e = new Entry();
            e.setX(i);
            e.setY(data[i]);
            chart_entry.add(e);
        }
        Log.i("whywhy",String.valueOf(chart_entry.size()));
        data_set = new LineDataSet(chart_entry, "ECG");
        chart_set.add(data_set);
        chart_data = new LineData(chart_set);

        chart.setData(chart_data);
        chart.invalidate();

    }
}