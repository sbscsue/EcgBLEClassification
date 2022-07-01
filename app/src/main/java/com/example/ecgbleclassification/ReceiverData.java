package com.example.ecgbleclassification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

public class ReceiverData extends BroadcastReceiver {
    final String BROADCAST_TAG = "DATA";


    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("RECEIVER:",BROADCAST_TAG);

    }
}