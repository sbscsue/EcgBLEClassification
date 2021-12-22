package com.example.ecgbleclassification;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;

import androidx.core.app.ActivityCompat;
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
import java.util.List;


public class FragmentAllplot extends Fragment {
    Resources res;

    int DATA_LENGTH;
    int SAMPLING_LATE;
    float PERIOD;
    int PLOT_LENGTH ;


    final int PERMISSION_REQUEST_BLUETOOTH_CONNECT = 1;

    final String GATT_TAG = "GATT";

    BluetoothManager manager;
    BluetoothAdapter adapter;
    BLEReceiver receiver;





    int flag = 0;
    private LineChart chart;
    ArrayList<Entry> chart_entry = new ArrayList<Entry>();
    List<ILineDataSet> chart_set = new ArrayList<ILineDataSet>();
    LineData chart_data;






    public FragmentAllplot() {
        // Required empty public constructor
    }


    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);



    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_allplot, container, false);

        res = getResources();
        DATA_LENGTH =  res.getInteger(R.integer.segment_data_length);
        SAMPLING_LATE =  res.getInteger(R.integer.sampling_rate);
        PERIOD = Float.valueOf(res.getString(R.string.period));
        PLOT_LENGTH = res.getInteger(R.integer.all_plot_length);



        final IntentFilter theFilter = new IntentFilter();
        theFilter.addAction("toGraph");

        receiver = new BLEReceiver(){
            @Override
            public void onReceive(Context context, Intent intent) {
                super.onReceive(context, intent);
                if(intent.getAction().equals("toGraph")){
                    Log.i(BROADCAST_TAG,intent.getAction());
                    plot(intent.getByteArrayExtra("BLE_DATA"));
                }
            }
        };


        chart = view.findViewById(R.id.chart);
        chart.setBackgroundColor(Color.WHITE);
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);

        requireActivity().registerReceiver(receiver,theFilter);

        //x,y축 고정

        for(int i=0; i< PLOT_LENGTH; i++){
            Entry d = new Entry();
            //Log.i("check",Float.toString((float) (Math.floor(i*PERIOD*1000)/1000.0)));
            d.setX((float) (Math.floor(i*PERIOD*1000)/1000.0));
            //d.setX(i+1);
            d.setY(100);
            chart_entry.add(d);
        }


        chart_set.add(new LineDataSet(chart_entry,"ECG"));
        chart_data = new LineData(chart_set);
        chart.setData(chart_data);
        chart.invalidate();



        manager = (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager.getAdapter();


        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(),new String[]{Manifest.permission.BLUETOOTH_CONNECT}, PERMISSION_REQUEST_BLUETOOTH_CONNECT);
        }

        return view;
    }

    public void plot(byte[] data){
        //Log.i("plot",data.toString());

        for(int i=0; i<DATA_LENGTH; i+=1){
            //Log.i("data", String.valueOf((float)(data[i] & 0xff)));
            Entry d = new Entry();
            d.setX(chart_entry.get(i+flag).getX());
            d.setY((float)(data[i] & 0xff));
            d.setY((float)data[i]);

            chart_entry.set(i+flag,d);
        }
        flag += DATA_LENGTH;
        if(flag==PLOT_LENGTH){
            flag=0;
        }


        chart_set = null;
        chart_data = null;

        chart_set = new ArrayList<ILineDataSet>();
        chart_set.add(new LineDataSet(chart_entry,"ECG"));
        chart_data = new LineData(chart_set);

        chart.setData(chart_data);
        chart.invalidate();


    }
}