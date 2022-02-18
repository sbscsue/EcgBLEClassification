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
import android.os.Debug;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.Window;

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

    final String VALUE_TAG = "VALUE_CHECK";

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
    private static float leastTrustConfidence = (float)0.8;

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

    float[] originalEcg;
    float[] squareEcg;
    float[] processingEcg;
    int dataFlag;
    int windowCnt;

    //threshold
    static int THRESHOLD_SET_CNT=5;
    int thresholdMode;
    float thresholdValue;
    int thresholdStart;
    int thresholdEnd;

    //segment_index
    Queue<int[]> segmentPeakQueue = new LinkedList<>();
    Queue<HashMap<String,int[]>> segmentIndexQueue = new LinkedList<>();

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
        originalEcg = new float[WINDOW_LENGTH];
        squareEcg = new float[WINDOW_LENGTH];
        processingEcg = new float[WINDOW_LENGTH];

        dataFlag = 0;
        windowCnt = 0;


        //threshold
        thresholdMode = -1;
        thresholdValue = 0;
        thresholdStart = -1;
        thresholdEnd = -1;

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
                    //Log.i(BROADCAST_TAG,intent.getAction());
                    //Log.i(FORDEBUG_TAG,"dataFlag:"+String.valueOf(dataFlag));
                    if(intent.getFloatArrayExtra("BLE_DATA")!=null){
                        Log.v(FORDEBUG_TAG,"WINDOW_CNT:"+String.valueOf(windowCnt));
                        setData(intent.getFloatArrayExtra("BLE_DATA"));
                        //
                        differSignalFiltering();
                        squareSignalFiltering();
                        averageSignalFiltering();
                        sendBleDataTestUse_preprocessing();
                        if (thresholdMode==1){
                            findThresholdUpDown();
                        }

                        updateDataFlag();
                        if(thresholdMode==1){
                            getSegment();
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
        Log.d(FORDEBUG_TAG,String.valueOf(dataFlag));
        for(int i=0; i<DATA_LENGTH; i+=1){
            int j = dataFlag+DATA_LENGTH+i;
            if(j<WINDOW_LENGTH){
                Log.d(FORDEBUG_TAG,"초과x");
                originalEcg[j] = data[i];
                squareEcg[j] = data[i];
            }
            else{
                originalEcg[i] = data[i];
                Log.d(FORDEBUG_TAG,"초과O");
                squareEcg[dataFlag+i] = data[i];
            }

        }
    }

    private void updateDataFlag(){
        dataFlag += DATA_LENGTH;
    }



    private void switchWindow(){
        Log.v(FUNCTION_TAG,"user:switchWindow()");
        if(dataFlag==WINDOW_LENGTH){
            dataFlag=0;
            if((windowCnt == 0) || (windowCnt%THRESHOLD_SET_CNT==0)){
                setThresholdValue(SAMPLING_LATE*3);
            }
            try {
                saveLocalWindow(windowCnt);
            } catch (IOException e) {
                e.printStackTrace();
            }
            windowCnt+=1;
        }
    }
        private void setThresholdValue(int startIndex){
            Log.v(FUNCTION_TAG,"user:setThresholdValue()");
                float sum = 0;
                int endIndex = squareEcg.length;
                for(int i =startIndex; i< endIndex; i++){
                    sum += processingEcg[i];
                }
                thresholdValue = sum / (endIndex-startIndex);
                thresholdValue = thresholdValue*5;
                thresholdMode = 1 ;
                Log.i(VALUE_TAG,"ThresholdValue:"+String.valueOf(thresholdValue));
        }
        public void saveLocalWindow(int cnt) throws IOException {
            Log.v(FUNCTION_TAG,"user:saveLocalWindow()");
            File f = new File(windowFile,String.valueOf(cnt)+".csv");
            BufferedWriter out = new BufferedWriter(new FileWriter(f,true));

            StringBuilder sb = new StringBuilder();
            for (float s : originalEcg) {
                sb.append(String.valueOf(s)).append(",");
            }
            out.write(sb.toString());
            out.flush();
        }



    private void differSignalFiltering(){
        int DIFF_N = 2;
        Log.v(FUNCTION_TAG,"user:differSignalFiltering()");
        if (checkPreprocessingExcess(DIFF_N)) {
            //앞뒤 diff_n만큼 자른 배열 갖고옴
            float[] originalArray;
            float[] caculateArray = new float[DATA_LENGTH];
            if(dataFlag-DIFF_N<0){
                originalArray = getPreviousWindowValue(DIFF_N,originalEcg);
                for(int i=0; i<DATA_LENGTH; i++){
                    int j = i+DIFF_N;
                    caculateArray[i] = (-2)*originalArray[j-2] + + originalArray[j] + (-1)*originalArray[j-1] + (1)*originalArray[j+1] + (2)*originalArray[j+2];
                }
            }
            else if(dataFlag+DATA_LENGTH+DIFF_N>WINDOW_LENGTH){
                originalArray = getNextWindowValue(DIFF_N,originalEcg);
                for(int i=0; i<DATA_LENGTH; i++){
                    int j = i+DIFF_N;
                    caculateArray[i] = (-2)*originalArray[j-2] + (-1)*originalArray[j-1] + (1)*originalArray[j+1] + (2)*originalArray[j+2];
                }
            }

            System.arraycopy(caculateArray,0,squareEcg,dataFlag,DATA_LENGTH);

        }
        else{
            for(int i=dataFlag; i< dataFlag+DATA_LENGTH; i++){
                squareEcg[i] =(-2)*originalEcg[i-2]+(-1)*originalEcg[i-1]+(1)*originalEcg[i+1]+(2)*originalEcg[i+2];
            }

        }
    }


    private void squareSignalFiltering(){
        Log.v(FUNCTION_TAG,"squareSignalFiltering()");
        for(int i=dataFlag; i< dataFlag+DATA_LENGTH; i++){
            squareEcg[i] =  Math.abs(squareEcg[i])*2;
        }
    }


    private void averageSignalFiltering(){
        Log.v(FUNCTION_TAG,"averageSignalFiltering()");
        int N = 10;
        if (checkPreprocessingExcess(N)) {
            //앞뒤 diff_n만큼 자른 배열 갖고옴
            float[] originalArray;
            float[] caculateArray = new float[DATA_LENGTH];
            if(dataFlag-N<0){
                originalArray = getPreviousWindowValue(N,squareEcg);
                for(int i=0; i<DATA_LENGTH; i++){
                    int flag = i+N;
                    float sum = 0;
                    for(int j =flag-N; j<=flag+N; j++){
                        sum += originalArray[j];
                    }
                    caculateArray[i] = sum / (N*2+1);
                }
            }
            else if(dataFlag+DATA_LENGTH+N>WINDOW_LENGTH){
                originalArray = getNextWindowValue(N,squareEcg);
                for(int i=0; i<DATA_LENGTH; i++){
                    int flag = i+N;
                    float sum = 0;
                    for(int j =flag-N; j<=flag+N; j++){
                        sum += originalArray[j];
                    }
                    caculateArray[i] = sum / (N*2+1);
                }
            }

            System.arraycopy(caculateArray,0,processingEcg,dataFlag,DATA_LENGTH);

        }
        else{
            for(int i=dataFlag; i< dataFlag+DATA_LENGTH; i++){
                float sum = 0;
                for(int j = i-N; j<=i+N; j++){
                    sum += squareEcg[j];
                }
                processingEcg[i] = sum/(N/2);
            }
        }

    }


        //함수 이름 변경
        private boolean checkPreprocessingExcess(int cnt){
            Log.v(FUNCTION_TAG,"user:checkWindowCntIndexExcess()");
            if((dataFlag-cnt<0) || (dataFlag+DATA_LENGTH+cnt>WINDOW_LENGTH)){
                //Log.i(FORDEBUG_TAG,"TRUE");
                return true;
            }
            else{
                //Log.i(FORDEBUG_TAG,"FALSE");
                return false;
            }
        }

        private float[] getPreviousWindowValue(int length,float[] windowEcg){
            //첫 FLAG - LENGTH;
            int N = (dataFlag - length)*(-1);
            int frontN = WINDOW_LENGTH-N;


            float[] originalArray = new float[DATA_LENGTH+(2*length)];

            System.arraycopy(windowEcg,frontN,originalArray,0,N);
            System.arraycopy(windowEcg,dataFlag,originalArray,N,DATA_LENGTH+length);

            //Log.i(FORDEBUG_TAG,"originalArrayFirst:"+ String.valueOf(N));
            //Log.i(FORDEBUG_TAG,"originalArraySecond:"+ String.valueOf(originalArray.length));

            return originalArray;
        }

    private float[] getNextWindowValue(int length,float[] windowEcg){
        //마지막 flag + length
        int N = dataFlag+DATA_LENGTH+length - WINDOW_LENGTH;
        int endN = N;


        float[] originalArray = new float[DATA_LENGTH+(length*2)];

        System.arraycopy(windowEcg,dataFlag-length,originalArray,0,DATA_LENGTH+length);
        System.arraycopy(windowEcg,0,originalArray,DATA_LENGTH+length,N);

        //Log.i(FORDEBUG_TAG,"originalArrayFirst:"+ String.valueOf(N));
        //Log.i(FORDEBUG_TAG,"originalArraySecond:"+ String.valueOf(originalArray.length));

        return originalArray;
    }



    private void findThresholdUpDown(){
        Log.v(FUNCTION_TAG,"user:findThresholdUpDown()");
        for(int i=dataFlag; i<dataFlag+DATA_LENGTH; i+=1){
            float data = processingEcg[i];
            //Log.i(FORDEBUG_TAG,String.valueOf(processingEcg[i]));
            if(thresholdStart == -1){
                if(data>thresholdValue){
                    //Log.i(FORDEBUG_TAG,"start");
                    thresholdStart=i;
                    //Log.i(FORDEBUG_TAG,String.valueOf(thresholdStart));
                }
            }
            else if(thresholdEnd == -1){
                if(data<thresholdValue){
                    //Log.i(FORDEBUG_TAG,"end");
                    thresholdEnd=i;
                    //Log.i(FORDEBUG_TAG,String.valueOf(thresholdEnd));
                }
            }
            else{
                findPeak();
            }
        }
    }





        private void findPeak() {
            Log.v(FUNCTION_TAG,"user:findPeak()");
            int peakIndex = -1;
            float peakValue = -99999;
            if (thresholdStart != -1 && thresholdEnd != -1) {
                if(checkThresholdExcess()){
                    //뒤
                    for(int i=thresholdStart; i<WINDOW_LENGTH; i++) {
                        if (peakValue <= originalEcg[i]) {
                            peakIndex = i;
                            peakValue = originalEcg[i];
                        }
                    }
                    //앞
                    for(int i=0; i<=thresholdEnd; i++){
                        if(peakValue<=originalEcg[i]){
                            peakIndex = i;
                            peakValue = originalEcg[i];
                        }
                    }
                }
                else{
                    for(int i=thresholdStart; i<=thresholdEnd; i++){
                        if(peakValue<=originalEcg[i]){
                            peakIndex = i;
                            peakValue = originalEcg[i];
                        }
                    }
                }

                Log.i(SEGMENTATION_TAG,"PEAK!");
                setPeakIndex(peakIndex,windowCnt);
                setSegmentIndex(peakIndex,windowCnt);

                thresholdStart = -1;
                thresholdEnd = -1;

            }
        }
            //함수 이름 변경
            private boolean checkThresholdExcess(){
                if(thresholdEnd > thresholdStart){
                    return false;
                }
                else{
                    return true;
                }
            }

            private void setPeakIndex(int peakIndex,int windowCnt){
                int[] peak = new int[2];
                peak[0] = windowCnt;
                peak[1] = peakIndex;
                segmentPeakQueue.offer(peak);
            }

            private void setSegmentIndex(int peakIndex,int windowCnt){
                Log.v(FUNCTION_TAG,"user:setSegmentIndex()");

                int startIndex = peakIndex - SEGMENT_LENGTH;
                int endIndex = peakIndex  + SEGMENT_LENGTH;

                int[] frontSegIndex = new int[3];
                int[] backSegIndex = new int[3];
                HashMap<String,int[]> segmentIndex = new HashMap<String,int[]>();

                if(checkSegmentExcess(startIndex,endIndex)){
                    if(startIndex < 0){
                        frontSegIndex[0] = windowCnt-1;
                        frontSegIndex[1] = WINDOW_LENGTH+startIndex;
                        frontSegIndex[2] = WINDOW_LENGTH;
                        backSegIndex[0] = windowCnt;
                        backSegIndex[1] = 0;
                        backSegIndex[2] = endIndex;
                    }
                    if(endIndex > WINDOW_LENGTH){
                        frontSegIndex[0] = windowCnt;
                        frontSegIndex[1] = startIndex;
                        frontSegIndex[2] = WINDOW_LENGTH;
                        backSegIndex[0] = windowCnt+1;
                        backSegIndex[1] = 0 ;
                        backSegIndex[2] = endIndex-WINDOW_LENGTH;
                    }
                }
                else{
                    frontSegIndex[0] = windowCnt;
                    frontSegIndex[1] = startIndex;
                    frontSegIndex[2] = peakIndex;
                    backSegIndex[0] = windowCnt;
                    backSegIndex[1] = peakIndex;
                    backSegIndex[2] = endIndex;
                }
                segmentIndex.put("front",frontSegIndex);
                segmentIndex.put("back",backSegIndex);
                segmentIndexQueue.offer(segmentIndex);
            }

                private boolean checkSegmentExcess(int start,int end){
                    //초과 되는 경우
                    if((start<0) || (end>WINDOW_LENGTH)){
                        return true;
                    }
                    else{
                        return false;
                    }
                }



    private void getSegment()  {
        Log.v(FUNCTION_TAG,"user:setSegment()");
        while(!segmentIndexQueue.isEmpty()){
            Log.i(FORDEBUG_TAG,"QueueLength:"+String.valueOf(segmentIndexQueue.size()));
            int[] peak = segmentPeakQueue.peek();
            HashMap<String,int[]> index = segmentIndexQueue.peek();


            if(index.get("back")[0]==windowCnt){
                Log.i(FORDEBUG_TAG,"조건1");
                if(index.get("back")[2]<=dataFlag){
                    Log.i(FORDEBUG_TAG,"조건2");
                    //indexing
                    float[] frontEcg = Arrays.copyOfRange(originalEcg,
                            index.get("front")[1],
                            index.get("front")[2]);
                    float[] backEcg = Arrays.copyOfRange(originalEcg,
                            index.get("back")[1],
                            index.get("back")[2]);

                    Log.i(FORDEBUG_TAG,"Front:"+ Arrays.toString(Arrays.copyOfRange(originalEcg,
                            index.get("front")[1],
                            index.get("front")[2])));
                    Log.i(FORDEBUG_TAG,"Back:"+Arrays.toString(Arrays.copyOfRange(originalEcg,
                            index.get("back")[1],
                            index.get("back")[2])));

                    float[] segmentEcg = new float[frontEcg.length+backEcg.length];
                    System.arraycopy(frontEcg,0,segmentEcg,0,frontEcg.length);
                    Log.i(FORDEBUG_TAG,"ALL1:"+Arrays.toString(segmentEcg));

                    System.arraycopy(backEcg,0,segmentEcg,frontEcg.length,backEcg.length);
                    Log.i(FORDEBUG_TAG,"ALL2:"+Arrays.toString(segmentEcg));
                    returnAllResult(peak,index,segmentEcg);

                    segmentPeakQueue.poll();
                    segmentIndexQueue.poll();
                }
                else{
                    break;
                }
            }
            else{
                break;
            }
        }
    }

        private void returnAllResult(int[] peakIndex,HashMap<String,int[]> segmentIndex,float[] segment){
            //predict
            float[] minMaxSegmentEcg = minMaxScale(segment);
            Log.i("tensorflow실행",String.valueOf(minMaxSegmentEcg.length));

            float[][] output = predict(minMaxSegmentEcg);

            //ann
            int flag = outputFlag(output);
            String predictAnn = outputAnn(flag);
            //bpm
            int bpm = getBpm(peakIndex[1]);
            //predictAccuracyCheck
            boolean accuracyCheck = predictAccuracyCheck(output[0][flag]);

            setNotification(bpm,predictAnn);
            setSegmentPlot(minMaxSegmentEcg,accuracyCheck,bpm,predictAnn);
            saveLocalSegmentIndex(peakIndex,segmentIndex,accuracyCheck,bpm,predictAnn);
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



            private float[][] predict(float[] data){
                Log.v(FUNCTION_TAG,"user:predict()");

                float[][][] input = inputProcessing(data);
                float[][] output = new float[1][OUTPUT_LENGTH];

                //Log.i(TENSORFLOW_TAG,Arrays.deepToString(output));
                interpreter.run(input,output);
                //Log.i(TENSORFLOW_TAG,Arrays.deepToString(output));

                return output;
            }

                //to tensor input
                private float[][][] inputProcessing(float[] data){
                    float[][][] input = new float[1][INPUT_LENGTH][1];
                    for(int i=0; i<data.length; i++){
                        input[0][i][0] = data[i];
                    }
                    return input;
                }
                //return high value index
                private int outputFlag(float[][] output){
                    float value = -1;
                    int flag = 0;
                    for(int i=0; i< output[0].length; i++){
                        if(output[0][i]>=value){
                            Log.i(TENSORFLOW_TAG,"upup");
                            flag = i;
                            value = output[0][i];
                        }
                    }
                    return flag;
                }

                private String outputAnn(int flag){
                    return ANN[flag];
                }

                private boolean predictAccuracyCheck(float value){
                    if(value<leastTrustConfidence){
                        return false;
                    }
                    else{
                        return true;
                    }
                }

            void setNotification(int BPM, String predict){
                notificationManager.notify(1,new NotificationCompat.Builder(this, "EcgProcess")
                        .setContentTitle("EcgStatus")
                        .setContentText("BPM: "+BPM+"  "+ "ANN: " + predict )
                        .setSmallIcon(R.mipmap.ic_launcher).build());
            }

            public void setSegmentPlot(int[] data,boolean predictAccuracyFlag,int bpm,String predict){
                Log.v(FUNCTION_TAG,"user:segmentPlot()");
                float[] reData = new float[data.length];
                for (int i=0; i<data.length; i++) {
                    reData[i] = (float)data[i];
                }

                Intent intent = new Intent("segmentation");
                intent.putExtra("data",data);
                if(predictAccuracyFlag){
                    intent.putExtra("accuracy",true);
                }
                else{
                    intent.putExtra("accuracy",false);
                }
                sendBroadcast(intent);

                intent = null;
                intent = new Intent("INFORMATION");
                intent.putExtra("BPM",bpm);
                intent.putExtra("PREDICT",predict);
                //intent.putExtra("predict",predict);
                sendBroadcast(intent);
            }


            public void setSegmentPlot(float[] data,boolean predictAccuracyFlag,int bpm,String predict){
                Log.v(FUNCTION_TAG,"user:segmentPlot()");
                Intent intent = new Intent("segmentation");
                intent.putExtra("data",data);
                if(predictAccuracyFlag){
                    intent.putExtra("accuracy",true);
                }
                else{
                    intent.putExtra("accuracy",false);
                }
                sendBroadcast(intent);

                intent = null;
                intent = new Intent("INFORMATION");
                intent.putExtra("BPM",bpm);
                intent.putExtra("PREDICT",predict);
                //intent.putExtra("predict",predict);
                sendBroadcast(intent);
            }




            public void saveLocalSegmentIndex(int[] peakIndex,HashMap<String,int[]> segmentIndex,boolean accuracyCheck,int bpm, String predict){
                Log.v(FUNCTION_TAG,"user:saveLocalSegmentIndex()");
                File f = segmentIndexFile;
                try {
                    BufferedWriter out = new BufferedWriter(new FileWriter(f,true));
                    StringBuilder sb = new StringBuilder();
                    sb.append(peakIndex[0]).append(",").append(peakIndex[1]).append(",")
                            .append(accuracyCheck).append(",").append(bpm).append(",").append(predict).append("\n");
                    int[] front = segmentIndex.get("front");
                    int[] back = segmentIndex.get("back");
                    sb.append(front[0]).append(",").append(front[1]).append(",").append(front[2]).append("\n");
                    sb.append(back[0]).append(",").append(back[1]).append(",").append(back[2]).append("\n");

                    out.write(sb.toString());
                    out.flush();

                } catch (IOException e) {
                    e.printStackTrace();
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



    private void sendBleDataTestUse_preprocessing(){
        Intent intent = new Intent("BLE");


        float[] allData = new float[DATA_LENGTH*3];
        //복붙!!
        System.arraycopy(originalEcg,dataFlag,allData,0,DATA_LENGTH);
        System.arraycopy(squareEcg,dataFlag,allData,DATA_LENGTH,DATA_LENGTH);
        System.arraycopy(processingEcg,dataFlag,allData,DATA_LENGTH*2,DATA_LENGTH);
        intent.putExtra("TestUse_preprocessing",allData);
        sendBroadcast(intent);

    }









    private int otherwindowFlag(int original){
        if(original==0){
            return 1;
        }
        else{
            return 0;
        }

    }


    /*

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
