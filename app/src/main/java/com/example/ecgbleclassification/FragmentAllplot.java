package com.example.ecgbleclassification;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class FragmentAllplot extends Fragment {
    Resources res;


    //plot
    int DATA_LENGTH;
    int SAMPLING_LATE;
    float PERIOD;

    int PLOT_LENGTH ;


    int flag = 0;
    private LineChart chart;
    ArrayList<Entry> chart_entry = new ArrayList<Entry>();
    ArrayList<Entry> chartEntryTestUse_preprocessing1 = new ArrayList<Entry>();
    ArrayList<Entry> chartEntryTestUse_preprocessing2 = new ArrayList<Entry>();


    TextView bpmView;
    TextView predictView;

    Receiver receiver;
    IntentFilter theFilter;






    public FragmentAllplot() {
        // Required empty public constructor
    }



    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("checkcheck","onCreate()");

        res = getResources();
        DATA_LENGTH =  res.getInteger(R.integer.data_length);
        SAMPLING_LATE =  res.getInteger(R.integer.sampling_rate);
        PERIOD = Float.valueOf(res.getString(R.string.period));
        PLOT_LENGTH = res.getInteger(R.integer.all_plot_length);



        theFilter = new IntentFilter();
        theFilter.addAction("BLE");
        theFilter.addAction("INFORMATION");

        receiver = new Receiver(){
            @Override
            public void onReceive(Context context, Intent intent) {
                super.onReceive(context, intent);
                if(intent.getAction().equals("BLE")){

                    if(intent.getFloatArrayExtra("TestUse_preprocessing")!=null){
                        plotTestUse_preprocessing(intent.getFloatArrayExtra("TestUse_preprocessing"));
                    }
                     /*
                    if(intent.getFloatArrayExtra("BLE_DATA")!=null){
                        plot(intent.getFloatArrayExtra("BLE_DATA"));
                    }
                      */

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
        View view = inflater.inflate(R.layout.fragment_allplot, container, false);
        Log.d("checkcheck","onCreatView()");



        chart = view.findViewById(R.id.chartAll);
        chart.setBackgroundColor(Color.WHITE);
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);



        //x,y축 고정

        for(int i=0; i< PLOT_LENGTH; i++){
            Log.d("checkcheck","plot초기화");
            Entry d = new Entry();
            //Log.i("check",Float.toString((float) (Math.floor(i*PERIOD*1000)/1000.0)));
            d.setX((float) (Math.floor(i*PERIOD*1000)/1000.0));
            //d.setX(i+1);
            d.setY(1);
            chart_entry.add(d);
            chartEntryTestUse_preprocessing1.add(d);
            chartEntryTestUse_preprocessing2.add(d);
        }


        LineDataSet dataSet = new LineDataSet(chart_entry,"ECG");
        dataSet.setDrawCircles(false);
        dataSet.setLineWidth(2);


        ArrayList<ILineDataSet> chartSet = new ArrayList<ILineDataSet>();
        chartSet.add(dataSet);

        LineData lineData = new LineData(chartSet);
        chart.setData(lineData);
        chart.invalidate();

        bpmView = view.findViewById(R.id.bpmAll);
        predictView = view.findViewById(R.id.predictAll);

        requireActivity().registerReceiver(receiver,theFilter);
        Log.d("checkcheck","plot초기화 끝");
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

    public void plot(byte[] data){
        //Log.i("plot",data.toString());

        for(int i=0; i<DATA_LENGTH; i+=1){
            //Log.i("data", String.valueOf((float)(data[i] & 0xff)));
            Entry d = new Entry();

            d.setX(chart_entry.get(i+flag).getX());
            d.setY((float)(data[i] & 0xff));

            chart_entry.set(i+flag,d);
        }
        flag += DATA_LENGTH;
        if(flag==PLOT_LENGTH){
            flag=0;
        }


        chart.clear();

        LineDataSet dataSet = new LineDataSet(chart_entry,"ECG");
        dataSet.setDrawCircles(false);
        dataSet.setLineWidth(2);

        //https://weeklycoding.com/mpandroidchart-documentation/chartdata/

        ArrayList<ILineDataSet> chartSet = new ArrayList<ILineDataSet>();
        chartSet.add(dataSet);

        LineData lineData = new LineData(chartSet);
        chart.setData(lineData);


        chart.invalidate();
    }



    private void plot(float[] parsingData){
        //Log.i("plot", Arrays.toString(parsingData));

        for(int i=0; i<parsingData.length; i+=1){
            Entry d = new Entry();

            d.setX(chart_entry.get(i+flag).getX());
            d.setY(parsingData[i]);

            chart_entry.set(i+flag,d);
        }
        flag += DATA_LENGTH;
        if(flag==PLOT_LENGTH){
            flag=0;
        }


        chart.clear();

        LineDataSet dataSet = new LineDataSet(chart_entry,"ECG");
        dataSet.setDrawCircles(false);
        dataSet.setLineWidth(2);

        //https://weeklycoding.com/mpandroidchart-documentation/chartdata/

       ArrayList<ILineDataSet> chartSet = new ArrayList<ILineDataSet>();
       chartSet.add(dataSet);


        LineData lineData = new LineData(chartSet);
        chart.setData(lineData);


        chart.invalidate();
    }

    private void plotTestUse_preprocessing(float[] allData){
        //Log.i("TEST", Arrays.toString(originalAndFilterData));

        int n = DATA_LENGTH;
        for(int i=0; i<n; i+=1){
            Entry d1 = new Entry();

            d1.setX(chart_entry.get(i+flag).getX());
            d1.setY(allData[i]);

            Entry d2 = new Entry();
            d2.setX(chartEntryTestUse_preprocessing1.get(i+flag).getX());
            d2.setY(allData[i+n]);

            Entry d3 = new Entry();
            d3.setX(chartEntryTestUse_preprocessing1.get(i+flag).getX());
            d3.setY(allData[i+(n*2)]);


            chart_entry.set(i+flag,d1);
            chartEntryTestUse_preprocessing1.set(i+flag,d2);
            chartEntryTestUse_preprocessing2.set(i+flag,d3);
        }
        flag += DATA_LENGTH;
        if(flag==PLOT_LENGTH){
            flag=0;
        }


        chart.clear();

        LineDataSet dataSet = new LineDataSet(chart_entry,"original");
        dataSet.setDrawCircles(false);
        dataSet.setLineWidth(2);
        dataSet.setColor(R.color.black);

        LineDataSet dataSetTestUse_sqaure = new LineDataSet(chartEntryTestUse_preprocessing1,"square");
        dataSetTestUse_sqaure.setDrawCircles(false);
        dataSetTestUse_sqaure.setLineWidth(2);

        LineDataSet dataSetTestUse_ma = new LineDataSet(chartEntryTestUse_preprocessing2,"ma");
        dataSetTestUse_ma.setDrawCircles(false);
        dataSetTestUse_ma.setLineWidth(2);
        dataSetTestUse_ma.setColor(getContext().getColor(R.color.purple_500));


        //https://weeklycoding.com/mpandroidchart-documentation/chartdata/

        ArrayList<ILineDataSet> chartSet = new ArrayList<ILineDataSet>();
        chartSet.add(dataSet);
        chartSet.add(dataSetTestUse_sqaure);
        chartSet.add(dataSetTestUse_ma);

        LineData lineData = new LineData(chartSet);
        chart.setData(lineData);


        chart.invalidate();
    }





}