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


public class Fragment1_0_Main extends Fragment {

    Button button1;
    Button button2;
    Button button3;

    TextView text1;


    ServiceBle bleService;

    public static Fragment1_0_Main newInstance() {
        return new Fragment1_0_Main();
    }

    public Fragment1_0_Main() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {


        /*
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

         */

        View v = inflater.inflate(R.layout.fragment1_0_main, container, false);



        text1 = v.findViewById(R.id.fragment1_card1_1_connction);


        //text1.setText(String.valueOf(bleService.getConnectState()));

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


    }
}