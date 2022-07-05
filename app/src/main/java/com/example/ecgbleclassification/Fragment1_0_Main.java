package com.example.ecgbleclassification;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;


public class Fragment1_0_Main extends Fragment {

    Button button1;
    Button button2;
    Button button3;

    TextView text1;

    ServiceBle bleService;

    SharedPreferences preferences;
    ReceiverBleConnection receiver;
    IntentFilter theFilter;

    ServiceBle.BleBinder mb;
    public static Fragment1_0_Main newInstance() {
        return new Fragment1_0_Main();
    }

    public Fragment1_0_Main() {
        // Required empty public constructor
    }


    ServiceConnection conn = new ServiceConnection() {
        public void onServiceConnected(ComponentName name,
                                       IBinder service) {
            // 서비스와 연결되었을 때 호출되는 메서드
            // 서비스 객체를 전역변수로 저장

            if(service.getClass().getSimpleName()=="BleBinder"){
                Log.i("0701",service.getClass().getSimpleName());
                mb = (ServiceBle.BleBinder) service;
                bleService = mb.getService();

                Intent intent = new Intent();
                intent.setAction("BLECONNECTION");
                intent.putExtra("state",bleService.getConnectState());
                getActivity().sendBroadcast(intent);
            }

        }
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bleService = null;
        }

    };



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        Intent intent = new Intent(getActivity(), ServiceEcgProcess.class);
        getActivity().bindService(intent, conn, Context.BIND_AUTO_CREATE);



        View v = inflater.inflate(R.layout.fragment1_0_main, container, false);

        preferences = getContext().getSharedPreferences(getString(R.string.S_NAME),Context.MODE_PRIVATE);

        boolean state = preferences.getBoolean(getString(R.string.S_BLUETOOTH_CONNECTION_STATE),false);
        Log.i("0705_check3?",String.valueOf(preferences.getBoolean(getString(R.string.S_BLUETOOTH_CONNECTION_STATE),false)));
        text1 = v.findViewById(R.id.fragment1_card1_1_connction);
        text1.setText(String.valueOf(state));




        theFilter = new IntentFilter();
        theFilter.addAction("BLECONNECTION");

        receiver = new ReceiverBleConnection(){
            @Override
            public void onReceive(Context context, Intent intent) {
                super.onReceive(context, intent);
                if(intent.getAction().equals("BLECONNECTION")){
                    Log.i("0701","BLECONNECTION_RECEIVE");
                    boolean state = preferences.getBoolean(getString(R.string.S_BLUETOOTH_CONNECTION_STATE),false);

                    Log.i("0701",String.valueOf(state));
                    text1.setText(String.valueOf(state));
                }
            }
        };

        requireActivity().registerReceiver(receiver,theFilter);


        button1 = v.findViewById(R.id.fragment1_button1);
        button2 = v.findViewById(R.id.fragment1_button2);
        button3 = v.findViewById(R.id.fragment1_button3);

        button1.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), ActivityBleScan.class);
                startActivity(intent);
            }
        });

        button2.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v) {
                ((ActivityMain)getActivity()).replaceFragment(Fragment1_2_AllPlot.newInstance());

            }
        });

        button3.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v) {
                ((ActivityMain)getActivity()).replaceFragment(Fragment1_3_SegmentPlot.newInstance());
            }
        });




        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i("0701","???????????");
        if(bleService!=null){
            //안먹는중
            text1.setText(String.valueOf(bleService.getConnectState()));
            Log.i("0701","??????????????????????????????");
        }


    }
}