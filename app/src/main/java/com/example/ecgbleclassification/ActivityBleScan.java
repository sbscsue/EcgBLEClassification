package com.example.ecgbleclassification;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.ArrayAdapter;


import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;


public class ActivityBleScan extends AppCompatActivity {

    BluetoothManager manager;
    BluetoothAdapter bluetoothAdapter;

    Handler handler;
    private static final long SCAN_PERIOD = 10000;
    boolean mScanning;


    Resources res;


    Button scanButton;
    ListView ScanListView;

    ArrayList<HashMap<String,String>> deviceList;
    SimpleAdapter adpater;


    private ServiceBle bleService;
    ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ServiceBle.BleBinder mb = (ServiceBle.BleBinder) service;
            //oncreate 실행
            bleService = mb.getService();
            Log.i("check_service",bleService.toString());
            Log.i("SERVICE_CHECK","CONNECT SERVICES");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i("SERVICE_CHECK","DISCONNECT SERVICES");
        }
    };




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment1_1_scanner);

        res = getResources();

        Intent intent = new Intent(ActivityBleScan.this, ServiceBle.class);
        getApplicationContext().bindService(intent,conn,Context.BIND_AUTO_CREATE);


        manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = manager.getAdapter();

        handler = new Handler();




        scanButton = findViewById(R.id.ScanButton);
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deviceList.clear();
                scanLeDevice(true);
            }

        });


        String [] tag = new String[2];
        tag[0] = "name";
        tag[1] = "mac";

        Log.i("0704", Arrays.toString(tag));

        int[] id = new int[2];
        id[0] = R.id.scan_adapter_text1;
        id[1] = R.id.scan_adapter_text2;

        deviceList = new ArrayList<HashMap<String, String>>();
        adpater = new SimpleAdapter(this, deviceList,R.layout.adapter_view ,new String[]{"name","mac"},id);

        ScanListView = findViewById(R.id.ScansList);
        ScanListView.setAdapter(adpater);
        ScanListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Log.d("click_device",deviceList.get(position).get("mac"));

                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceList.get(position).get("mac"));
                bleService.deviceChangeConnectGatt(device);

                finish();

            }
        });
    }

    private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            HashMap<String,String> item = new HashMap<>();
            if(device.getName()==null){
                item.put("name","N/A");
            }
            else{
                item.put("name",device.getName());
            }

            item.put("mac",device.getAddress());

            if(!deviceList.contains(item)){
                deviceList.add(item);
                //Log.d("bluetoothenable",device.getName());
                Log.d("bluetoothenable",device.getAddress());

                adpater.notifyDataSetChanged();
            }
        }
    };


    @SuppressLint("MissingPermission")
    private void scanLeDevice(final boolean enable) {
        if (enable) {
            Log.d("bluetoothenable","true");
            // Stops scanning after a pre-defined scan period.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;

                    bluetoothAdapter.stopLeScan(leScanCallback);
                }
            }, SCAN_PERIOD);

            mScanning = true;
            bluetoothAdapter.startLeScan(leScanCallback);
        } else {
            Log.d("bluetoothenable","false");
            mScanning = false;
            bluetoothAdapter.stopLeScan(leScanCallback);
        }

    }


}