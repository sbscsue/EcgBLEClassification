package com.example.ecgbleclassification;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
public class BluetoothLeService extends Service {
    public BluetoothLeService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}