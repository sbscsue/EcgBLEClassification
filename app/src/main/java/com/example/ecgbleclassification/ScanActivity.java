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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import java.util.ArrayList;



public class ScanActivity extends AppCompatActivity {

    BluetoothManager manager;
    BluetoothAdapter bluetoothAdapter;

    Handler handler;
    private static final long SCAN_PERIOD = 10000;
    boolean mScanning;

    private BluetoothLeService bleService;
    ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothLeService.BleBinder mb = (BluetoothLeService.BleBinder) service;
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


    Resources res;


    Button scanButton;
    ListView ScanListView;

    ArrayList<String> deviceList;
    ArrayAdapter<String> adpater;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.scanner);

        res = getResources();

        Intent intent = new Intent(ScanActivity.this,BluetoothLeService.class);
        getApplicationContext().bindService(intent,conn,Context.BIND_AUTO_CREATE);

        handler = new Handler();





        deviceList = new ArrayList<String>();
        adpater = new ArrayAdapter<String>(this, R.layout.layout ,R.id.textview, deviceList);

        manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = manager.getAdapter();



        ScanListView = findViewById(R.id.ScansList);
        ScanListView.setAdapter(adpater);
        ScanListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Log.d("click_device",deviceList.get(position));


                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceList.get(position));
                bleService.setDevice(device);

                finish();


            }
        });

        scanButton = findViewById(R.id.ScanButton);
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deviceList.clear();
                scanLeDevice(true);
            }

        });




    }


    private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            if(!deviceList.contains(device.getAddress())){
                deviceList.add(device.getAddress());
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