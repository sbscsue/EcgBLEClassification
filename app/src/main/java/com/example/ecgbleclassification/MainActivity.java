package com.example.ecgbleclassification;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    BottomNavigationView bottomNavigationView;


    Intent intent1;
    Intent intent2;
    BluetoothManager manager;
    BluetoothAdapter bluetoothAdapter;

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




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        intent1 = new Intent(MainActivity.this,BluetoothLeService.class);
        startService(intent1);
        //getApplicationContext().bindService(intent,conn, Context.BIND_AUTO_CREATE);

        //SERVICE
        intent2 = new Intent(getApplicationContext(), EcgProcess.class);
        startService(intent2);

        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment frag1 = new FragmentMain();

        fragmentManager.beginTransaction().replace(R.id.fragment,frag1).commit();

        manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = manager.getAdapter();
        //permmision and bluetooth on check
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            Toast.makeText(this, R.string.bluetooth_not_supporting, Toast.LENGTH_SHORT).show();
            finish();
        }

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supporting, Toast.LENGTH_SHORT).show();
            finish();
        }

        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, R.string.bluetooth_off, Toast.LENGTH_SHORT).show();
        }


        bottomNavigationView = (BottomNavigationView)findViewById(R.id.bottom_menu_bar);
        bottomNavigationView.setOnNavigationItemSelectedListener(
                new BottomNavigationView.OnNavigationItemSelectedListener(){
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                        switch(item.getItemId()){
                            case R.id.page_1:
                                fragmentManager.beginTransaction().replace(R.id.fragment,frag1).commit();
                                return true;

                            case R.id.page_2:
                                Fragment frag2 = new FragmentAllplot();
                                fragmentManager.beginTransaction().replace(R.id.fragment,frag2).commit();
                                return true;

                            case R.id.page_3:
                                Fragment frag3 = new FragmentSegmentPlot();
                                fragmentManager.beginTransaction().replace(R.id.fragment,frag3).commit();
                                return true;

                            case R.id.page_4:
                                Fragment frag4 = new FragmentSetting();
                                fragmentManager.beginTransaction().replace(R.id.fragment,frag4).commit();
                                return true;
                        }

                        return false;
                    }



                }
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopService(intent1);
        stopService(intent2);
    }

}