package com.example.ecgbleclassification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ReceiverBleConnection extends BroadcastReceiver {
    final String BROADCAST_TAG = "BLE_CONNECTION";
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("RECEIVER:",BROADCAST_TAG);
    }
}