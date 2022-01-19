package com.example.ecgbleclassification;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
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
    final int DATA_LENGTH = 250;
    final int SAMPLING_LATE = 1000;
    final float PERIOD = (float) 0.001;
    final int PLOT_LENGTH = 20000;

    final int PERMISSION_REQUEST_BLUETOOTH_CONNECT = 1;
    final String GATT_TAG = "GATT";


    BluetoothManager manager;
    BluetoothAdapter adapter;
    BluetoothDevice device;
    BluetoothGatt bluetoothGatt;


    int flag = 0;
    private LineChart chart;
    ArrayList<Entry> chart_entry = new ArrayList<Entry>();
    List<ILineDataSet> chart_set = new ArrayList<ILineDataSet>();
    LineData chart_data;

    double cnt;
    String time;
    private FirebaseAuth mAuth;
    private FirebaseDatabase mdb;
    DatabaseReference parent;




    int count = 0;
    TextView sample;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.service);

        FirebaseApp.initializeApp(this);
        mAuth = FirebaseAuth.getInstance();
        mAuth.signInAnonymously().addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if(task.isSuccessful()){
                    Log.i("check_user","login");

                    mdb = FirebaseDatabase.getInstance();
                    time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    parent = mdb.getReference(time);
                    parent.child("segment").setValue(DATA_LENGTH);
                    parent.child("Fs").setValue(SAMPLING_LATE);
                    parent.child("T").setValue(PERIOD);
                    cnt = -1.0;
                }
            }
        });

        //Log.i("check_user",mAuth.getCurrentUser().toString());
        //Log.i("check_user", String.valueOf(mAuth.getCurrentUser().isAnonymous()));



        chart = findViewById(R.id.chart);
        chart.setBackgroundColor(Color.WHITE);
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        //x,y축 고정

        for(int i=0; i< PLOT_LENGTH; i++){
            Entry d = new Entry();
            Log.i("check",Float.toString((float) (Math.floor(i*PERIOD*1000)/1000.0)));
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

        Intent intent = getIntent();
        String address = intent.getStringExtra("address");

        device = adapter.getRemoteDevice(address);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(ServiceActivity.this,new String[]{Manifest.permission.BLUETOOTH_CONNECT}, PERMISSION_REQUEST_BLUETOOTH_CONNECT);
        }

        bluetoothGatt = device.connectGatt(this, true, gattCallback);
        bluetoothGatt.connect();

        sample = findViewById(R.id.example);
        sample.setText(bluetoothGatt.getDevice().getName());


    }


    @SuppressLint("MissingPermission")
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothGatt!=null){
            bluetoothGatt.disconnect();
        }
    }


    private BluetoothGattCallback gattCallback= new BluetoothGattCallback(){
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.i(GATT_TAG,"GATT SERVER CONNECT CHANGE");
            if(newState==BluetoothProfile.STATE_DISCONNECTED){
                Log.i(GATT_TAG,"GATT SERVER DISCONNECTED");
            }
            if(newState==BluetoothProfile.STATE_CONNECTED){
                Log.i(GATT_TAG,"GATT SERVER CONNECTED");
                gatt.discoverServices();


            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.i(GATT_TAG,"SERVICES IS DISCOVERED");


            for (BluetoothGattService service : gatt.getServices()) {
                Log.i(GATT_TAG,service.getUuid().toString());
                if(service.getUuid().toString().equals("00001523-1212-efde-1523-785feabcd123")){
                    Log.i(GATT_TAG,"SERVICE GET");
                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        Log.i(GATT_TAG,characteristic.getUuid().toString());
                        if(characteristic.getUuid().toString().equals("00001524-1212-efde-1523-785feabcd123")){
                            Log.i(GATT_TAG,"CHARATERISTIC GET");
                            gatt.setCharacteristicNotification(characteristic,true);

                            for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
                                Log.i("??",descriptor.getUuid().toString());
                            }

                            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            gatt.writeDescriptor(descriptor);




                            break;
                        }
                    }
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            Log.i(GATT_TAG,"?");
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.i(GATT_TAG,"GATT DATA AVAIABLE");
            cnt+=1;
            if(cnt%4==0){
                Log.i("COUNT CHECK",String.valueOf(cnt));
            }

            byte[] data = characteristic.getValue();
            plot(data);
            //saveFirebase(data);

        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.i(GATT_TAG,"GATT DATA AVAIABLE");


        }

    };

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
    public void saveFirebase(byte[] data){
        cnt += 1.0;

        StringBuilder builder = new StringBuilder();
        for(int i=0; i<DATA_LENGTH; i++){
            builder.append(data[i]);
            builder.append(",");
        }


        Log.i("check_n", String.valueOf((int)cnt));
        Log.i("check_firebase", builder.toString());

        parent.child("data").child(String.valueOf((int)cnt)).setValue(builder.toString());
    }


}
