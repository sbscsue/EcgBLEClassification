package com.example.ecgbleclassification;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetFileDescriptor;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class EcgProcess extends Service {
    final String SERVICE_TAG = "ECG_SERVICE_CHECK";

    boolean saveFlag = false;
    boolean predictFlag = false;

    BLEReceiver receiver;

    private Interpreter interpreter;



    IBinder serviceBinder = new EcgBinder();
    class EcgBinder extends Binder {
       EcgProcess getService() {
            return EcgProcess.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(SERVICE_TAG,"START SERVICE");

        final IntentFilter theFilter = new IntentFilter();
        theFilter.addAction("toService");
        receiver = new BLEReceiver(){
            @Override
            public void onReceive(Context context, Intent intent) {
                super.onReceive(context, intent);
                if(intent.getAction().equals("toService")){
                    Log.i(BROADCAST_TAG,intent.getAction());
                }
            }
        };
        registerReceiver(receiver,theFilter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }



    private void setSave(){

    }
    private void save(){

    }


    private void setPredict(){

    }
    private void peakDetection(){

    }

    public String predict(){
        String output = "";

        return output;
    }













    private MappedByteBuffer loadModelFile(String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }




}
