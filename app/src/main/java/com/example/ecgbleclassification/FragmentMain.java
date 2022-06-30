package com.example.ecgbleclassification;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;


public class FragmentMain extends Fragment {

    Button button_main1;
    TextView text_main1;

    Button button_main2;



    private ServiceBle bleService;
    ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ServiceBle.BleBinder mb = (ServiceBle.BleBinder) service;
            //oncreate 실행
            bleService = mb.getService();
            if(bleService!=null){
                Log.i("method","onServiceConnected");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i("SERVICE_CHECK","DISCONNECT SERVICES");
        }
    };



    public FragmentMain() {
        // Required empty public constructor
    }



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);




    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {



        View v = inflater.inflate(R.layout.fragment_main, container, false);

        button_main1 = v.findViewById(R.id.main1_Connect);
        button_main2 = v.findViewById(R.id.main2_Scann);


        text_main1 = v.findViewById(R.id.main1_ConnectionState);

        Intent intent = new Intent(getActivity(), ServiceBle.class);
        getActivity().bindService(intent,conn, Context.BIND_AUTO_CREATE);

        if(bleService!=null){
            text_main1.setText(String.valueOf(bleService.getConnectState()));
        }

        button_main1.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v) {
                bleService.connect();
            }
        });


        button_main2.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), ActivityBleScan.class);
                startActivity(intent);
            }
        });

       return v;
    }

    @Override
    public void onResume() {
        super.onResume();


    }
}