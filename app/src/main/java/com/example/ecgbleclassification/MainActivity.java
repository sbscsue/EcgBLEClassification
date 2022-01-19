package com.example.ecgbleclassification;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.MenuItem;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    BottomNavigationView bottomNavigationView;

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

        Intent intent = new Intent(MainActivity.this,BluetoothLeService.class);
        startService(intent);
        //getApplicationContext().bindService(intent,conn, Context.BIND_AUTO_CREATE);

        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment frag1 = new FragmentMain();

        fragmentManager.beginTransaction().replace(R.id.fragment,frag1).commit();


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

}