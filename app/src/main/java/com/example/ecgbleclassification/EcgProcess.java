package com.example.ecgbleclassification;

import android.app.Service;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.os.IBinder;

import androidx.annotation.Nullable;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class EcgProcess extends Service {
    private Interpreter interpreter;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }





    public String predict(){
        String output = "";

        return output;
    }

    private void save(){

    }

    private void peakDetection(){

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
