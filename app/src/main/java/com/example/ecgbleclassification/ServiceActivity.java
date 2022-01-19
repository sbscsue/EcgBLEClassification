package com.example.ecgbleclassification;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.os.NetworkOnMainThreadException;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class ServiceActivity extends AppCompatActivity {

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




    int count = 0;
    TextView sample;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.service);

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
        registerReceiver(receiver,theFilter);


        //Log.i("check_user",mAuth.getCurrentUser().toString());
        //Log.i("check_user", String.valueOf(mAuth.getCurrentUser().isAnonymous()));



        chart = findViewById(R.id.chart);
        chart.setBackgroundColor(Color.WHITE);
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
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



        manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager.getAdapter();


        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(ServiceActivity.this,new String[]{Manifest.permission.BLUETOOTH_CONNECT}, PERMISSION_REQUEST_BLUETOOTH_CONNECT);
        }

        //bleService.connect();
    }


    @SuppressLint("MissingPermission")
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
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
