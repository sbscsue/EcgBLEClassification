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

import org.junit.platform.engine.UniqueId;
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
import java.util.Map;
import java.util.Queue;

public class EcgProcess extends Service {
    final String FUNCTION_TAG = "FUNCTION_CHECK";
    final String FORDEBUG_TAG = "FORDEBUG_TAG";

    final String SERVICE_TAG = "ECG_SERVICE_CHECK";
    final String WINDOW_TAG = "WINDOW_CHECK";
    final String SEGMENTATION_TAG = "SEGMENT_CHECK";
    final String TENSORFLOW_TAG = "TENSORFLOW_CHECK";






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
    //notification
    NotificationManager notificationManager;
    Notification notification;

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

    

    //ecg data(window)
    int windowCnt;
    float[][] originalEcg;
    float[][] squareEcg;

    int windowFlag;
    int dataFlag;

    //threshold
    static int thresholdSetCnt=5;
    int thresholdMode;
    float thresholdValue;
    HashMap<String, Integer> thresholdStart;
    HashMap<String, Integer> thresholdEnd;

    //segment_index
    Queue<HashMap<String,Integer>> segmentIndexs = new LinkedList<>();

    //BPM
    int prevRpeak;






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
        Log.i(SERVICE_TAG,"START SERVICE");
        res = getResources();

        DATA_LENGTH =  res.getInteger(R.integer.data_length);
        SAMPLING_LATE =  res.getInteger(R.integer.sampling_rate);
        PERIOD = Float.valueOf(res.getString(R.string.period));
        WINDOW_LENGTH = res.getInteger(R.integer.window_length);
        SEGMENT_LENGTH = res.getInteger(R.integer.segment_length);

        //ecg save 
        originalEcg = new float[2][WINDOW_LENGTH];
        squareEcg = new float[2][WINDOW_LENGTH];

        dataFlag = 0;
        windowCnt = 0;
        windowFlag = 0;


        //threshold
        thresholdMode = -1;
        thresholdValue = 0;

        prevRpeak = 0;



        //tensorflow
        INPUT_LENGTH = res.getInteger(R.integer.modelInputShape);
        OUTPUT_LENGTH = res.getInteger(R.integer.modelOutputShape);
        ANN = res.getStringArray(R.array.NSV_ANN);
        interpreter = getTfliteInterpreter("model02.tflite");


        //notification
        createNotificationChannel();
        notificationManager = getBaseContext().getSystemService(NotificationManager.class);
        notification = new NotificationCompat.Builder(this, "EcgProcess")
                .setContentTitle("EcgStatus")
                .setContentText("--------")
                .setSmallIcon(R.mipmap.ic_launcher).build();

        startForeground(1,notification);


        //broadcast receiver
        final IntentFilter theFilter = new IntentFilter();
        theFilter.addAction("BLE");
        theFilter.addAction("Peak");
        receiver = new Receiver(){
            @Override
            public void onReceive(Context context, Intent intent) {
                super.onReceive(context, intent);
                if(intent.getAction().equals("BLE")){
                    Log.i(BROADCAST_TAG,intent.getAction());
                    Log.i(FORDEBUG_TAG,"dataFlag:"+String.valueOf(dataFlag));
                    if(intent.getFloatArrayExtra("BLE_DATA")!=null){
                        setData(intent.getFloatArrayExtra("BLE_DATA"));
                        //
                        differSignalFiltering(1);
                        squareSignalFiltering();
                        //averageSignalFiltering(50);
                        //findThresholdUpDown();
                        sendBleDataTestUse_preprocessing();
                        if (thresholdMode==1){
                            findThresholdUpDown();
                        }
                        updateDataFlag();
                        if(thresholdMode==1){
                            findPeak();
                            setSegment();
                        }
                        switchWindow();
                    }
                }
            }
        };
        registerReceiver(receiver,theFilter);




        //save
        startTime = LocalDateTime.now();
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


    private void setData(float[] data){
        Log.v(FUNCTION_TAG,"user:setData()");
        for(int i=0; i<DATA_LENGTH; i+=1){
            originalEcg[windowFlag][dataFlag+i] = data[i];
            //squareEcg[windowFlag][dataFlag+i] = data[i];
        }
    }

    private void updateDataFlag(){
        dataFlag += DATA_LENGTH;
    }



    private void switchWindow(){
        Log.v(FUNCTION_TAG,"user:switchWindow()");
        if(dataFlag==WINDOW_LENGTH){
            dataFlag=0;
            if((windowCnt == 0)||(windowCnt%thresholdSetCnt==0)){
                setThresholdValue(SAMPLING_LATE*3);
            }

            try {
                saveLocalWindow(windowFlag,windowCnt);
            } catch (IOException e) {
                e.printStackTrace();
            }

            windowFlag = otherwindowFlag(windowFlag);
            windowCnt+=1;
        }
    }
        private void setThresholdValue(int startIndex){
            Log.v(FUNCTION_TAG,"user:setThresholdValue()");
                float sum = 0;
                int endIndex = squareEcg[windowFlag].length;
                for(int i =startIndex; i< endIndex; i++){
                    sum += squareEcg[windowFlag][i];
                }
                thresholdValue = sum / (endIndex-startIndex);
                thresholdMode = 1 ;
        }


    private void differSignalFiltering(int length){
        Log.v(FUNCTION_TAG,"user:differSignalFiltering()");
        if (checkWindowCntIndexExcess(length)) {
            int N = (dataFlag - length)*(-1);

            int frontN = WINDOW_LENGTH-N;
            HashMap<String,Integer> frontStart = new HashMap();
            frontStart.put("windowFlag",otherwindowFlag(windowFlag));
            frontStart.put("dataFlag", frontN);

            Log.i(FORDEBUG_TAG,"frontWindow:"+ String.valueOf(frontStart.get("windowFlag")));
            Log.i(FORDEBUG_TAG,"frontFlag:"+ String.valueOf(frontStart.get("dataFlag")));

            int backN = dataFlag + DATA_LENGTH;
            HashMap<String,Integer> backStart = new HashMap();
            backStart.put("windowFlag",windowFlag);
            backStart.put("dataFlag",backN);

            Log.i(FORDEBUG_TAG,"backWindow:"+ String.valueOf(backStart.get("windowFlag")));
            Log.i(FORDEBUG_TAG,"backFlag:"+ String.valueOf(backStart.get("dataFlag")));

            float[] originalArray = new float[DATA_LENGTH+length];
            float[] caculateArray = new float[DATA_LENGTH];

            System.arraycopy(originalEcg[frontStart.get("windowFlag")],frontStart.get("dataFlag"),originalArray,0,N);
            System.arraycopy(originalEcg[backStart.get("windowFlag")],dataFlag,originalArray,N,DATA_LENGTH);
            Log.i(FORDEBUG_TAG,"originalArrayFirst:"+ String.valueOf(N));
            Log.i(FORDEBUG_TAG,"originalArraySecond:"+ String.valueOf(originalArray.length));

            for(int i=0; i<length; i++){
                int flag = i+length;
                caculateArray[i] = originalArray[flag] - originalArray[flag-length] ;
            }

            System.arraycopy(caculateArray,0,squareEcg[windowFlag],dataFlag,DATA_LENGTH);
        }
        else{
            for(int i=dataFlag; i< dataFlag+DATA_LENGTH; i++){
                squareEcg[windowFlag][i] =originalEcg[windowFlag][dataFlag]-originalEcg[windowFlag][i-length];
            }

        }


    }


    private void squareSignalFiltering(){
        Log.v(FUNCTION_TAG,"squareSignalFiltering()");
        for(int i=dataFlag; i< dataFlag+DATA_LENGTH; i++){
            squareEcg[windowFlag][i] =  (float)Math.pow(squareEcg[windowFlag][i],2);
        }
    }


    private void averageSignalFiltering(int length){
        Log.v(FUNCTION_TAG,"averageSignalFiltering()");
        if (checkWindowCntIndexExcess(length)) {
            int frontN =(dataFlag - length)*(-1);
            int backN = length-frontN;

            HashMap<String,Integer> frontStart = new HashMap();
            frontStart.put("windowFlag",otherwindowFlag(windowFlag));
            frontStart.put("dataFlag",WINDOW_LENGTH + frontN);

            HashMap<String,Integer> backStart = new HashMap();
            frontStart.put("windowFlag",windowFlag);
            frontStart.put("dataFlag",backN);

            float[] originalArray = new float[frontN+length];
            float[] caculateArray = new float[length];

            System.arraycopy(originalEcg[frontStart.get("windowFlag")],frontStart.get("dataFlag"),originalArray,0,frontN);
            System.arraycopy(originalEcg[backStart.get("windowFlag")],backStart.get("dataFlag"),originalArray,frontN,backN);

            for(int i=0; i<length; i++){
                int flag = i+length;
                float sum = 0;
                for(int j =flag-length; j<=flag; j++){
                    sum += originalArray[j];
                }
                caculateArray[i] = sum / (length+1);
            }

            System.arraycopy(caculateArray,0,squareEcg[windowFlag],dataFlag,DATA_LENGTH);
        }
        else{
            for(int i=dataFlag; i< dataFlag+DATA_LENGTH; i++){
                float sum = 0;
                for(int j = i-length; j<=i; j++){
                    sum += originalEcg[windowFlag][j];
                }
                squareEcg[windowFlag][i] = sum/(length+1);
            }

        }
    }


        private boolean checkWindowCntIndexExcess(int cnt){
            Log.v(FUNCTION_TAG,"user:checkWindowCntIndexExcess()");
            if(dataFlag-cnt<0){
                Log.i(FORDEBUG_TAG,"TRUE");
                return true;
            }
            else{
                Log.i(FORDEBUG_TAG,"FALSE");
                return false;
            }
        }



    private void findThresholdUpDown(){
        Log.v(FUNCTION_TAG,"user:findThresholdUpDown()");
        for(int i=dataFlag; i<DATA_LENGTH; i+=1){
            float data = originalEcg[windowFlag][i];
            if(thresholdStart == null){
                if(data>thresholdValue){
                    Log.i(SEGMENTATION_TAG,"threshold_start:"+String.valueOf(dataFlag+i));
                     thresholdStart = new HashMap<>();
                    thresholdStart.put("window",windowFlag);
                    thresholdStart.put("sample",dataFlag+i);
                    Log.i(SEGMENTATION_TAG,"threshold_realvaue:"+String.valueOf(data));
                }
            }
            else if(thresholdEnd == null){
                if(data<thresholdValue){
                    Log.i(SEGMENTATION_TAG,"threshold_end:"+String.valueOf(dataFlag+i));
                    thresholdEnd = new HashMap<>();
                    thresholdEnd.put("window",windowFlag);
                    thresholdEnd.put("sample",dataFlag+i);
                    Log.i(SEGMENTATION_TAG,"threshold_realvaue:"+String.valueOf(data));
                }
            }
        }
    }





    private void findPeak() {
        Log.v(FUNCTION_TAG,"user:findPeak()");
        HashMap<String, Integer> peak_index;
        peak_index = new HashMap<>();

        float tmp_value = -1;
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


            Log.i("indexd_현재marker",String.valueOf(dataFlag));
            Log.i("indexd_현재cnt",String.valueOf(windowCnt));
            Log.i("indexd_현재flag",String.valueOf(windowFlag));

            segmentIndexs.offer(segment_index);

        }

    private void setSegment()  {
        Log.v(FUNCTION_TAG,"user:setSegment()");
       //Log.i(SEGMENTATION_TAG,String.valueOf(segmentIndexs.size()));
        while(!segmentIndexs.isEmpty()){
            //Log.i(SEGMENTATION_TAG,String.valueOf(segmentIndexs.isEmpty()));
            //Log.i(SEGMENTATION_TAG,"Queue:"+String.valueOf(segmentIndexs.size()));
            HashMap<String,Integer> index = segmentIndexs.peek();
            if(index.get("back_flag")==windowFlag){
                //Log.i("indexd","f "+String.valueOf(index.get("back_end")));
                //Log.i("indexd","o "+String.valueOf(dataFlag));
                Log.i("indexd_length",String.valueOf(segmentIndexs.size()));
                if(index.get("back_end")<dataFlag){
                    Log.i("indexd","sliceslicslicslice");


                    float[] frontEcg = Arrays.copyOfRange(originalEcg[index.get("front_flag")],
                            index.get("front_start"),
                            index.get("front_end"));
                    float[] backEcg = Arrays.copyOfRange(originalEcg[index.get("back_flag")],
                            index.get("back_start"),
                            index.get("back_end"));
                    float[] segmentEcg = new float[frontEcg.length+backEcg.length];
                    System.arraycopy(frontEcg,0,segmentEcg,0,frontEcg.length);
                    System.arraycopy(backEcg,0,segmentEcg,frontEcg.length,backEcg.length);

                    float[] minMaxSegmentEcg = minMaxScale(segmentEcg);
                    if(reCheckSegment(minMaxSegmentEcg)){
                        int bpm = getBpm(index.get("peak_sample"));
                        String predictAnn = predict(minMaxSegmentEcg);


                        setNotification(bpm,predictAnn);
                        setSegmentPlot(minMaxSegmentEcg,bpm,predictAnn);
                        saveLocalSegmentIndex(index,bpm,predictAnn);
                    }
                    //forDebug(index,segmentEcg);

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

    private float[] minMaxScale(float[] data){
        float minValue=99999;
        float maxValue=-1;

        for(int i=0; i<data.length;i++){
            float d = data[i];
            if(d>maxValue){
                maxValue =d;
            }
            if(d<minValue){
                minValue = d;
            }
        }

        float[] scaleData = new float[data.length];
        float under =  maxValue-minValue;
        for(int i=0; i<data.length; i++){
            float up = data[i]-minValue;
            scaleData[i] = up/under;
        }


        return scaleData;
    }

    private boolean reCheckSegment(float[] minMaxData){
        int flag = minMaxData.length/2;
        Log.i("CHECK_RECHECKSEGMENT",String.valueOf(minMaxData[flag]));
        if((minMaxData[flag]==1.0)) {
            return true;
        }
        else{
            return false;
        }
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
        Log.i(TENSORFLOW_TAG,Arrays.deepToString(output));
        interpreter.run(input,output);

        Log.i(TENSORFLOW_TAG,Arrays.deepToString(output));
        float value = -1;
        int flag = 0;
        for(int i=0; i< output[0].length; i++){
            if(output[0][i]>=value){
                Log.i(TENSORFLOW_TAG,"upup");
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

            if(index.get("peak_flag")!=windowFlag){
                peak_cnt = windowCnt-1;
            }
            else{
                peak_cnt = windowCnt;
            }
            if(index.get("front_flag")!=windowFlag){
                front_cnt = windowCnt-1;
            }
            else{
                front_cnt = windowCnt;
            }
            if(index.get("back_flag")!=windowFlag){
                back_cnt = windowCnt-1;
            }
            else{
                back_cnt = windowCnt;
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




    public void saveLocalWindow(int flag, int cnt) throws IOException {
        Log.v(FUNCTION_TAG,"user:saveLocalWindow()");
        File f = new File(windowFile,String.valueOf(cnt)+".csv");
        BufferedWriter out = new BufferedWriter(new FileWriter(f,true));

        StringBuilder sb = new StringBuilder();
        for (float s : originalEcg[flag]) {
            sb.append(String.valueOf(s)).append(",");
        }
        out.write(sb.toString());
        out.flush();
    }






    public int[] getWindowCnt(int front, int end){
        Log.v(FUNCTION_TAG,"user:getWindowCnt()");
        int [] cnt = new int[2];
        if(front==end){
            cnt[0] = windowCnt;
            cnt[1] = windowCnt;
        }
        else{
            cnt[0] = windowCnt-1;
            cnt[1] = windowCnt;
        }
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
            cnt[i] += windowCnt;
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

    private void sendBleDataTestUse_preprocessing(){
        Intent intent = new Intent("BLE");


        float[] allData = new float[DATA_LENGTH*2];
        //복붙!!
        System.arraycopy(originalEcg[windowFlag],dataFlag,allData,0,DATA_LENGTH);
        System.arraycopy(squareEcg[windowFlag],dataFlag,allData,DATA_LENGTH,DATA_LENGTH);
        intent.putExtra("TestUse_preprocessing",allData);
        sendBroadcast(intent);

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
