package com.example.ecgbleclassification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

public class EcgProcess extends Service {
    final String SERVICE_TAG = "ECG_SERVICE_CHECK";

    final String WINDOW_TAG = "WINDOW_CHECK";
    final String SEGMENTATION_TAG = "SEGMENT_CHECK";
    final String TENSORFLOW_TAG = "TENSORFLOW_CHECK";
    final String FUNCTION_TAG = "FUNCTION_CHECK";

    final String FORDEBUG_TAG = "FORDEBUG_TAG";



    int DATA_LENGTH;
    int SAMPLING_LATE;
    float PERIOD;

    int WINDOW_LENGTH ;
    int SEGMENT_LENGTH;


    int INPUT_LENGTH;
    int OUTPUT_LENGTH;
    String[] ANN;



    //other
    Resources res;

    //Broadcast Receiver
    Receiver receiver;
    NotificationManager notificationManager;
    Notification notification;




    //ecg data(window)
    int[][] originalEcg;
    int[][] squareEcg;

    int next_flag;
    int window_flag;

    //threshold
    int thresholdMode;
    int thresholdValue;
    HashMap<String, Integer> thresholdStart;
    HashMap<String, Integer> thresholdEnd;

    //segment_index
    Queue<HashMap<String,Integer>> segmentIndexs = new LinkedList<>();

    //BPM
    int prevRpeak;
    //REAL VALUE MARKER
    int window_cnt;



    //tensorflow
    private Interpreter interpreter;



    //save
    //firebase
    double cnt;
    String time;
    private FirebaseAuth mAuth;
    private FirebaseDatabase mdb;
    DatabaseReference parent;

    //local
    LocalDateTime startTime;
    LocalDateTime endTime;
    File segmentIndexFile;
    File windowFile;

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
        Log.v(FUNCTION_TAG,"lifeCycle:onCreate()");



        createNotificationChannel();
        notificationManager = getBaseContext().getSystemService(NotificationManager.class);
        notification = new NotificationCompat.Builder(this, "EcgProcess")
              .setContentTitle("EcgStatus")
              .setContentText("--------")
              .setSmallIcon(R.mipmap.ic_launcher).build();

        startForeground(1,notification);


        Log.i(SERVICE_TAG,"START SERVICE");
        res = getResources();
        DATA_LENGTH =  res.getInteger(R.integer.data_length);
        SAMPLING_LATE =  res.getInteger(R.integer.sampling_rate);
        PERIOD = Float.valueOf(res.getString(R.string.period));
        WINDOW_LENGTH = res.getInteger(R.integer.window_length);
        SEGMENT_LENGTH = res.getInteger(R.integer.segment_length);

        INPUT_LENGTH = res.getInteger(R.integer.modelInputShape);
        OUTPUT_LENGTH = res.getInteger(R.integer.modelOutputShape);
        ANN = res.getStringArray(R.array.NSV_ANN);

        //ecg data
        originalEcg = new int[2][WINDOW_LENGTH];
        squareEcg = new int[2][WINDOW_LENGTH];

        window_cnt = 0;
        window_flag = 0;
        next_flag = 0;

        //threshold
        thresholdMode = -1;
        thresholdValue = 0;

        prevRpeak = 0;



        //tensorflow
        interpreter = getTfliteInterpreter("model02.tflite");




        //broadcast receiver
        final IntentFilter theFilter = new IntentFilter();
        theFilter.addAction("BLE");
        theFilter.addAction("Peak");
        receiver = new Receiver(){
            @Override
            public void onReceive(Context context, Intent intent) {
                super.onReceive(context, intent);
                if(intent.getAction().equals("BLE")){
                    //Log.i(BROADCAST_TAG,intent.getAction());
                    setWindow(intent.getByteArrayExtra("BLE_DATA"));
                    findPeak();
                    setSegment();
                    switchWindow();
                }
            }
        };
        registerReceiver(receiver,theFilter);


        startTime = LocalDateTime.now();

        //firebase
        FirebaseApp.initializeApp(this);
        mAuth = FirebaseAuth.getInstance();
        mAuth.signInAnonymously().addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if(task.isSuccessful()){
                    Log.i("check_user","login");

                    mdb = FirebaseDatabase.getInstance();
                    if(mdb!=null){
                        time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                        parent = mdb.getReference(time);
                        parent.child("segment").setValue(DATA_LENGTH);
                        parent.child("Fs").setValue(SAMPLING_LATE);
                        parent.child("T").setValue(PERIOD);
                        cnt = -1.0;
                    }
                }
            }

        });

        initalizeLocalSave();



    }

    private void initalizeLocalSave(){
        StringBuilder pathBuilder = new StringBuilder();

        String folderName = Environment.getExternalStorageDirectory().getAbsolutePath()+"/EcgData";
        File folder = new File(folderName);
        String timeName = startTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        folder = new File(folder,timeName);

        segmentIndexFile = new File(folder+"/segment.txt");
        windowFile = new File(folder+"/window");

        windowFile.mkdirs();
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(FUNCTION_TAG,"lifeCycle:onDesctroy()");

        unregisterReceiver(receiver);

        endTime = LocalDateTime.now();
        try {
            timestampLocalSave(endTime);
        } catch (IOException e) {
            e.printStackTrace();
        }
        stopForeground(true);
    }

    private void timestampLocalSave(LocalDateTime endTime) throws IOException {
        Log.v(FUNCTION_TAG,"user:timestampLocalSave");
        String folderName = Environment.getExternalStorageDirectory().getAbsolutePath()+"/EcgData";
        File folder = new File(folderName);
        String fileName = "timeStamp.txt";
        File file = new File(folder,fileName);


        BufferedWriter out =new BufferedWriter(new FileWriter(file,true));

        StringBuilder record = new StringBuilder()
                .append(startTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")))
                .append(" ")
                .append(endTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")))
                .append("\n");

        out.write(record.toString());
        out.flush();
        out.close();
    }


    public void setThresholdValue(){
        Log.v(FUNCTION_TAG,"user:setThresholdValue()");
        int sum = 0;
        for(int i=0; i<WINDOW_LENGTH; i++){
            sum +=  squareEcg[0][i];
        }

        thresholdMode=1;
        thresholdValue = (int) ((sum / WINDOW_LENGTH)*1.45);
        Log.i(SEGMENTATION_TAG,"threshold_value:"+String.valueOf(thresholdValue));

        window_flag=0;
    }

    private void setWindow(byte[] data){
        Log.v(FUNCTION_TAG,"user:setWindow()");
        int d;
        int dd;


        for(int i=0; i<DATA_LENGTH; i+=1){

            d = data[i] & 0xff;
            dd = d*d;
            originalEcg[window_flag][next_flag+i] = d;
            squareEcg[window_flag][next_flag+i] = dd;


            if(thresholdMode!=-1){
                if(thresholdStart == null){
                    if(dd>thresholdValue){
                        Log.i(SEGMENTATION_TAG,"threshold_start:"+String.valueOf(next_flag+i));
                        thresholdStart = new HashMap<>();
                        thresholdStart.put("window",window_flag);
                        thresholdStart.put("sample",next_flag+i);
                        Log.i(SEGMENTATION_TAG,"threshold_reavlaue:"+String.valueOf(d));
                    }
                }
                else if(thresholdEnd == null){
                    if(dd<thresholdValue){
                        Log.i(SEGMENTATION_TAG,"threshold_end:"+String.valueOf(next_flag+i));
                        thresholdEnd = new HashMap<>();
                        thresholdEnd.put("window",window_flag);
                        thresholdEnd.put("sample",next_flag+i);
                        Log.i(SEGMENTATION_TAG,"threshold_reavlaue:"+String.valueOf(d));
                    }
                }
            }
        }

            next_flag += DATA_LENGTH;
    }




    private void findPeak() {
        Log.v(FUNCTION_TAG,"user:findPeak()");
        HashMap<String, Integer> peak_index;
        peak_index = new HashMap<>();

        int tmp_value = -1;
        if (thresholdStart != null && thresholdEnd != null) {
            int start_flag = thresholdStart.get("window");
            int end_flag = thresholdEnd.get("window");

            int start_sample = thresholdStart.get("sample");
            int end_sample = thresholdEnd.get("sample");

            if (start_flag == end_flag) {
                int flag = start_flag;
                for (int i = start_sample; i <= end_sample; i++) {
                    if (squareEcg[flag][i] > tmp_value) {
                        peak_index.put("sample", i);
                        tmp_value = squareEcg[flag][i];
                    }
                }
                peak_index.put("window", flag);
            } else {
                peak_index = new HashMap<>();
                for (int i = start_sample; i < WINDOW_LENGTH; i++) {
                    if (squareEcg[start_flag][i] > tmp_value) {
                        peak_index.put("sample", i);
                    }
                    peak_index.put("window", start_flag);
                }
                for (int i = 0; i < end_sample; i++) {
                    if (squareEcg[end_flag][i] > tmp_value) {
                        peak_index.put("sample", i);
                        peak_index.put("window", end_flag);
                    }
                }

            }
            Log.i(SEGMENTATION_TAG,"PEAK!");
            setSegmentIndex(peak_index);
            thresholdStart = null;
            thresholdEnd = null;

        }
    }


    private void setSegmentIndex(HashMap<String,Integer> peak_index){
        Log.v(FUNCTION_TAG,"user:setSegmentIndex()");
        int p = peak_index.get("sample");
        int start = p - SEGMENT_LENGTH;
        int end = p  + SEGMENT_LENGTH;

        int front_start = 0;
        int front_end = 0;
        int front_flag = 0;

        int back_start = 0;
        int back_end = 0;
        int back_flag = 0;

        int flag = peak_index.get("window");
        HashMap<String,Integer> segment_index = new HashMap<String,Integer>();

        if((start > 0) && (end < WINDOW_LENGTH)){
            front_start = start;
            front_end = p;
            front_flag = peak_index.get("window");

            back_start = p;
            back_end = end;
            back_flag = peak_index.get("window");

        }
        else if (start<0){
            front_flag = otherwindowFlag(flag);
            front_start = WINDOW_LENGTH + start;
            front_end = WINDOW_LENGTH;

            back_start = flag;
            back_end = end;
            back_flag = flag;


        }
        else if (end>WINDOW_LENGTH){
            front_flag = flag;
            front_start = start;
            front_end = WINDOW_LENGTH;

            back_flag = otherwindowFlag(flag);
            back_start = 0;
            back_end = end-WINDOW_LENGTH;
        }

        segment_index.put("peak_flag",flag);
        segment_index.put("peak_sample",p);

        segment_index.put("front_flag",front_flag);
        segment_index.put("front_start", front_start);
        segment_index.put("front_end", front_end);

        segment_index.put("back_flag",back_flag);
        segment_index.put("back_start", back_start);
        segment_index.put("back_end", back_end);


        Log.i("indexd",String.valueOf(front_flag));
        Log.i("indexd",String.valueOf(front_start));
        Log.i("indexd",String.valueOf(front_end));

        Log.i("indexd",String.valueOf(back_flag));
        Log.i("indexd",String.valueOf(back_start));
        Log.i("indexd",String.valueOf(back_end));


        Log.i("indexd_현재marker",String.valueOf(next_flag));
        Log.i("indexd_현재cnt",String.valueOf(window_cnt));
        Log.i("indexd_현재flag",String.valueOf(window_flag));

        segmentIndexs.offer(segment_index);

    }

    private void setSegment()  {
        Log.v(FUNCTION_TAG,"user:setSegment()");
       //Log.i(SEGMENTATION_TAG,String.valueOf(segmentIndexs.size()));
        while(!segmentIndexs.isEmpty()){
            //Log.i(SEGMENTATION_TAG,String.valueOf(segmentIndexs.isEmpty()));
            //Log.i(SEGMENTATION_TAG,"Queue:"+String.valueOf(segmentIndexs.size()));
            HashMap<String,Integer> index = segmentIndexs.peek();
            if(index.get("back_flag")==window_flag){
                //Log.i("indexd","f "+String.valueOf(index.get("back_end")));
                //Log.i("indexd","o "+String.valueOf(next_flag));
                Log.i("indexd_length",String.valueOf(segmentIndexs.size()));
                if(index.get("back_end")<next_flag){
                    Log.i("indexd","sliceslicslicslice");


                    int[] frontEcg = Arrays.copyOfRange(originalEcg[index.get("front_flag")],
                            index.get("front_start"),
                            index.get("front_end"));
                    int[] backEcg = Arrays.copyOfRange(originalEcg[index.get("back_flag")],
                            index.get("back_start"),
                            index.get("back_end"));
                    int[] segmentEcg = new int[frontEcg.length+backEcg.length];
                    System.arraycopy(frontEcg,0,segmentEcg,0,frontEcg.length);
                    System.arraycopy(backEcg,0,segmentEcg,frontEcg.length,backEcg.length);

                    float[] minMaxSegmentEcg = minMaxScale(segmentEcg);
                    //forDebug(index,segmentEcg);

                    int bpm = getBpm(index.get("peak_sample"));
                    String predictAnn = predict(minMaxSegmentEcg);


                    setNotification(bpm,predictAnn);
                    setSegmentPlot(minMaxSegmentEcg,bpm,predictAnn);
                    saveLocalSegmentIndex(index,bpm,predictAnn);

                    segmentIndexs.poll();
                }
                else{
                    return;
                }
            }
            else{
                return;
            }

        }
    }

    private float[] minMaxScale(int[] data){
        int minValue=256;
        int maxValue=-1;

        for(int i=0; i<data.length;i++){
            int d = data[i];
            if(d>maxValue){
                maxValue =d;
            }
            if(d<minValue){
                minValue = d;
            }
        }

        float[] scaleData = new float[data.length];
        int under =  maxValue-minValue;
        for(int i=0; i<data.length; i++){
            int up = data[i]-minValue;
            scaleData[i] = up/under;
        }


        return scaleData;
    }


    public int getBpm(int currentRpeak){
        Log.v(FUNCTION_TAG,"user:getBpm()");
        float RR_interval = 0;

        if(currentRpeak < prevRpeak){
            RR_interval = currentRpeak + (WINDOW_LENGTH-prevRpeak);
        }
        else{
            RR_interval = currentRpeak - prevRpeak;
        }

        RR_interval = RR_interval * PERIOD;
        Log.i("RR INTERVAL",String.valueOf(RR_interval));

        prevRpeak = currentRpeak;

        return (int)(60 / RR_interval);
    }




    public String predict(float[] data){
        Log.v(FUNCTION_TAG,"user:predict()");

        float[][][] input = new float[1][INPUT_LENGTH][1];
        for(int i=0; i<data.length; i++){
            input[0][i][0] = data[i];
        }


        float[][] output = new float[1][OUTPUT_LENGTH];
        interpreter.run(input,output);

        double value = 0;
        int flag = 0;
        for(int i=0; i< output.length; i++){
            if(output[0][i]>=value){
                flag = i;
                value = output[0][i];
            }
        }

        String result = ANN[flag];
        Log.i(TENSORFLOW_TAG,String.valueOf(result));

        return result;
    }

    void setNotification(int BPM, String predict){
        notificationManager.notify(1,new NotificationCompat.Builder(this, "EcgProcess")
                .setContentTitle("EcgStatus")
                .setContentText("BPM: "+BPM+"  "+ "ANN: " + predict )
                .setSmallIcon(R.mipmap.ic_launcher).build());
    }

    public void setSegmentPlot(int[] data,int bpm,String predict){
        float[] reData = new float[data.length];
        for (int i=0; i<data.length; i++) {
            reData[i] = (float)data[i];
        }

        Log.v(FUNCTION_TAG,"user:segmentPlot()");
        Intent intent = new Intent("segmentation");
        intent.putExtra("data",reData);
        sendBroadcast(intent);


        intent = null;
        intent = new Intent("INFORMATION");
        intent.putExtra("BPM",bpm);
        intent.putExtra("PREDICT",predict);
        //intent.putExtra("predict",predict);
        sendBroadcast(intent);

    }


    public void setSegmentPlot(float[] data,int bpm,String predict){
        Log.v(FUNCTION_TAG,"user:segmentPlot()");
        Intent intent = new Intent("segmentation");
        intent.putExtra("data",data);
        sendBroadcast(intent);


        intent = null;
        intent = new Intent("INFORMATION");
        intent.putExtra("BPM",bpm);
        intent.putExtra("PREDICT",predict);
        //intent.putExtra("predict",predict);
        sendBroadcast(intent);

    }



    public void saveLocalSegmentIndex(HashMap<String,Integer> index,int bpm, String predict){
        Log.v(FUNCTION_TAG,"user:saveLocalSegmentIndex()");
        File f = segmentIndexFile;
        try {
            int peak_cnt=-1;
            int front_cnt=-1;
            int back_cnt=-1;

            if(index.get("peak_flag")!=window_flag){
                peak_cnt = window_cnt-1;
            }
            else{
                peak_cnt = window_cnt;
            }
            if(index.get("front_flag")!=window_flag){
                front_cnt = window_cnt-1;
            }
            else{
                front_cnt = window_cnt;
            }
            if(index.get("back_flag")!=window_flag){
                back_cnt = window_cnt-1;
            }
            else{
                back_cnt = window_cnt;
            }

            BufferedWriter out = new BufferedWriter(new FileWriter(f,true));
            StringBuilder sb = new StringBuilder();
            sb.append(peak_cnt).append(",").append(index.get("peak_sample")).append(",")
                    .append(bpm).append(",").append(predict).append("\n");
            sb.append(front_cnt).append(",").append(index.get("front_start")).append(",").append(index.get("front_end")).append("\n");
            sb.append(back_cnt).append(",").append(index.get("back_start")).append(",").append(index.get("back_end")).append("\n");

            out.write(sb.toString());
            out.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }



    private void switchWindow(){
        Log.v(FUNCTION_TAG,"user:switchWindow()");
        if(next_flag==WINDOW_LENGTH){
            next_flag=0;
            if(thresholdMode==-1){
                setThresholdValue();
            }
            else{
                try {
                    saveLocalWindow(window_flag,window_cnt);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if(window_flag==0){
                    window_flag = 1;
                }
                else if(window_flag==1){
                    window_flag = 0;
                }
                window_cnt+=1;
                Log.i(WINDOW_TAG,String.valueOf(window_flag));
            }
        }
    }

    public void saveLocalWindow(int flag, int cnt) throws IOException {
        Log.v(FUNCTION_TAG,"user:saveLocalWindow()");
        File f = new File(windowFile,String.valueOf(cnt)+".csv");
        BufferedWriter out = new BufferedWriter(new FileWriter(f,true));

        StringBuilder sb = new StringBuilder();
        for (int s : originalEcg[flag]) {
            sb.append(String.valueOf(s)).append(",");
        }
        out.write(sb.toString());
        out.flush();
    }






    public int[] getWindowCnt(int front, int end){
        Log.v(FUNCTION_TAG,"user:getWindowCnt()");
        int [] cnt = new int[2];
        if(front==end){
            cnt[0] = window_cnt;
            cnt[1] = window_cnt;
        }
        else{
            cnt[0] = window_cnt-1;
            cnt[1] = window_cnt;
        }
        Log.i(FORDEBUG_TAG,cnt.toString());
        return cnt;
    }





    private int otherwindowFlag(int original){
        if(original==0){
            return 1;
        }
        else{
            return 0;
        }

    }


    public void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getBaseContext().getSystemService(NotificationManager.class);
            if(manager.getNotificationChannel("EcgProcess")==null){
                NotificationChannel serviceChannel = new NotificationChannel(
                        "EcgProcess",
                        "EcgProcess",
                        NotificationManager.IMPORTANCE_NONE
                );
                manager.createNotificationChannel(serviceChannel);
            }

        }
    }



    private MappedByteBuffer loadModelFile(String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }


    private Interpreter getTfliteInterpreter(String modelPath){
        try {
            return new Interpreter(loadModelFile(modelPath));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }








    public void forDebug(HashMap<String,Integer> index, int[] data) throws IOException {
        //save;
        int[] cnt = getWindowCnt(index.get("front_flag"),index.get("back_flag"));
        for(int i=0; i<cnt.length; i++){
            cnt[i] += window_cnt;
        }
        String path = Environment.getExternalStorageDirectory().getAbsolutePath()+"/segmentData";
        File folder = new File(path);
        String name = String.valueOf(cnt[0])+"-"+index.get("front_start")+"-"+index.get("front_end")
                +"_"
                +cnt[1]+"-"+index.get("back_start")+"-"+index.get("back_end")
                +".csv";

        Log.i(SEGMENTATION_TAG,folder+"/"+name);
        File file = new File(folder+"/"+name);


        BufferedWriter out =new BufferedWriter(new FileWriter(file,true));


        String result = "";
        StringBuilder sb = new StringBuilder();
        for (int s : data) {
            sb.append(String.valueOf(s)).append(",");
        }
        result = sb.deleteCharAt(sb.length() - 1).toString();
        out.write(result);
        out.flush();
        Log.i(SEGMENTATION_TAG,"WRTIE FILE");
        out.close();

    }

    /*
    public void saveFirebase(byte[] data){
        cnt += 1.0;

        StringBuilder builder = new StringBuilder();
        for(int i=0; i<DATA_LENGTH; i++){
            builder.append(data[i]);
            builder.append(",");
        }


        Log.i("check_n", String.valueOf((int)cnt));
        Log.i("check_firebase", builder.toString());

        parent.child("data").child(String.valueOf((int)cnt)).setValue(builder.toString());
    }

     */






}
