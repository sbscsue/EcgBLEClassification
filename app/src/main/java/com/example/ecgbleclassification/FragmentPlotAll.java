package com.example.ecgbleclassification;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;


public class FragmentPlotAll extends Fragment {
    Resources res;
    final String FUNCTION_TAG = "FUNCTION_CHECK";
    final String BIND_TAG = "BIND_CHECK";
    final String ICON_CHECK = "ICON_CHECK";


    //vairable
    int DATA_LENGTH;
    int SAMPLING_LATE;
    float PERIOD;

    int PLOT_LENGTH ;

    //chartEcg
    int flag = 0;
    private LineChart chart;
    ArrayList<Entry> chart_entry = new ArrayList<Entry>();

    ArrayList<Entry> chartEntryTestUse_preprocessing1 = new ArrayList<Entry>();
    ArrayList<Entry> chartEntryTestUse_preprocessing2 = new ArrayList<Entry>();

    //chartPeak
    Drawable[][] iconDrawable;

    //result
    TextView bpmView;
    TextView predictView;

    //통신
    ServiceEcgProcess ecgService;
    Receiver receiver;
    IntentFilter theFilter;




    public FragmentPlotAll() {
        // Required empty public constructor
    }

    ServiceConnection conn = new ServiceConnection() {
        public void onServiceConnected(ComponentName name,
                                       IBinder service) {
            // 서비스와 연결되었을 때 호출되는 메서드
            // 서비스 객체를 전역변수로 저장
            Log.d(BIND_TAG,"CONNECT");
            ServiceEcgProcess.EcgBinder mb = (ServiceEcgProcess.EcgBinder) service;
            ecgService = mb.getService();

            allPlot(ecgService.originalEcg);
        }
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(BIND_TAG,"DISCONNECT");
            ecgService = null;
        }

    };


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("checkcheck","onCreate()");

        res = getResources();
        DATA_LENGTH =  res.getInteger(R.integer.data_length);
        SAMPLING_LATE =  res.getInteger(R.integer.sampling_rate);
        PERIOD = Float.valueOf(res.getString(R.string.period));
        PLOT_LENGTH = res.getInteger(R.integer.all_plot_length);

        iconDrawable = getIconDrawable("NSV");
        Log.d(ICON_CHECK,iconDrawable[0][0].toString());

        Intent intent = new Intent(getActivity(), ServiceEcgProcess.class);
        getActivity().bindService(intent, conn, Context.BIND_AUTO_CREATE);
        Log.d(BIND_TAG,conn.toString());

        theFilter = new IntentFilter();
        theFilter.addAction("BLE");
        theFilter.addAction("ALL");
        theFilter.addAction("ALL_PEAK");
        theFilter.addAction("INFORMATION");


        receiver = new Receiver(){
            @Override
            public void onReceive(Context context, Intent intent) {
                super.onReceive(context, intent);
                if(intent.getAction().equals("BLE")){

                    if(intent.getFloatArrayExtra("TestUse_preprocessing")!=null){
                        //plotTestUse_preprocessing(intent.getFloatArrayExtra("TestUse_preprocessing"));
                    }
                     /*
                    if(intent.getFloatArrayExtra("BLE_DATA")!=null){
                        plot(intent.getFloatArrayExtra("BLE_DATA"));
                    }
                      */
                }
                if(intent.getAction().equals("ALL")){
                    if(intent.getFloatArrayExtra("data")!=null){
                        dataLengthPlot(intent.getFloatArrayExtra("data"),Integer.valueOf(intent.getStringExtra("startIndex")));
                    }
                }
                if(intent.getAction().equals("ALL_PEAK")){
                    if(intent.getIntExtra("index",-1)!=-1){
                        Log.v("check","checkcheck");
                        peakPlot(intent.getIntExtra("index",-1),
                                 intent.getBooleanExtra("predictAcc",false),
                                intent.getIntExtra("flag",-1)
                        );
                    }
                }
                if(intent.getAction().equals("INFORMATION")){
                    Log.i(BROADCAST_TAG,intent.getAction());
                    bpmView.setText(String.valueOf(intent.getIntExtra("BPM",0)));
                    predictView.setText(String.valueOf((intent.getStringExtra("PREDICT"))));
                }
            }
        };
    }
        private Drawable[][] getIconDrawable(String annType){
            Drawable[][] drawables;
            if(annType=="NSV"){
                drawables = new Drawable[2][res.getStringArray(R.array.NSV_ANN).length];
                drawables[0][0] = res.getDrawable(R.drawable.n1);
                drawables[1][0] = res.getDrawable(R.drawable.n2);
                drawables[0][1] = res.getDrawable(R.drawable.s1);
                drawables[1][1] = res.getDrawable(R.drawable.s2);
                drawables[0][2] = res.getDrawable(R.drawable.v1);
                drawables[1][2] = res.getDrawable(R.drawable.v2);

                return drawables;
            }
            else{
                return null;
            }
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
        chart.setDragEnabled(false);



        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setAxisMinimum(0);
        chart.getAxisLeft().setAxisMaximum(1500);




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
        dataSet.setColor(getContext().getColor(R.color.ECG));
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
        if(ecgService!=null){
            allPlot(ecgService.originalEcg);
        }
    }

    private void allPlot(float[] data){
        for(int i=0; i<PLOT_LENGTH; i++){
            //Log.d(BIND_TAG,String.valueOf(i));
            //Log.d(BIND_TAG,String.valueOf(data[i]));
            Entry d = new Entry();
            d.setX(chart_entry.get(i).getX());
            d.setY(data[i]);
            chart_entry.set(i,d);
        }
        chartUpdate();
    }
    private void dataLengthPlot(float[] data, int startIndex) {
        for(int i=0; i<DATA_LENGTH; i++){
            int j = i+startIndex;
            Entry d = new Entry();
            d.setX(chart_entry.get(j).getX());
            d.setY(data[i]);
            chart_entry.set(j,d);
        }
        chartUpdate();
    }
    private void peakPlot(int index,boolean predictAcc, int annFlag){
        Log.v(FUNCTION_TAG,"user:peakPlot()");
        if(index!=-1){
            int predictAccNum;
            if(predictAcc == true){
                predictAccNum = 0;
            }
            else{
                predictAccNum = 1;
            }
            float x = chart_entry.get(index).getX();
            float y =  chart_entry.get(index).getY();
            Drawable icon = iconDrawable[predictAccNum][annFlag];

            Entry entry = new Entry(x,y,icon);
            chart_entry.set(index,entry);


            chartUpdate();
        }
        else{
            return;
        }
    }

        private void chartUpdate(){
            chart.clear();
            LineDataSet dataSet = new LineDataSet(chart_entry,"ECG");
            dataSet.setColor(getContext().getColor(R.color.ECG));
            dataSet.setDrawCircles(false);
            dataSet.setLineWidth(2);
            dataSet.setDrawIcons(true);
            dataSet.setDrawValues(false);
            //https://weeklycoding.com/mpandroidchart-documentation/chartdata/
            ArrayList<ILineDataSet> chartSet = new ArrayList<ILineDataSet>();
            chartSet.add(dataSet);

            LineData lineData = new LineData(chartSet);
            chart.setData(lineData);
            chart.setMaxVisibleValueCount(3600);

            chart.invalidate();
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