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
    //int SEGMENT_LENGTH;
    int SEGMENT_START_LENGTH;
    int SEGMENT_END_LENGTH;



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


    //segment_index
    Queue<int[]> segmentPeakQueue = new LinkedList<>();
    Queue<HashMap<String,int[]>> segmentIndexQueue = new LinkedList<>();

    //interval _ BPM
    int prevRpeakIndex;
        //predict input??? ???????????? interval???(1???, 10???)
    float currentInterval1;
    float currentInterval10;
    float[] currentInterval10Array;
        int currentInteval10Flag;

    




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
        SEGMENT_START_LENGTH = res.getInteger((R.integer.segment_start_length));
        SEGMENT_END_LENGTH = res.getInteger((R.integer.segment_end_length));

        //ecg save 
        originalEcg = new float[WINDOW_LENGTH];
        squareEcg = new float[WINDOW_LENGTH];
        processingEcg = new float[WINDOW_LENGTH];

        dataFlag = 0;
        windowCnt = 0;


        prevRpeakIndex = 0;
        currentInterval1 = 0;
        currentInterval10Array = new float[10];
            currentInteval10Flag = 0;



        //tensorflow
        INPUT_LENGTH = res.getInteger(R.integer.modelInputShape);
        OUTPUT_LENGTH = res.getInteger(R.integer.modelOutputShape);
        ANN = res.getStringArray(R.array.NSV_ANN);
        interpreter = getTfliteInterpreter("540_model90.tflite");


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
        receiver = new Receiver(){
            @Override
            public void onReceive(Context context, Intent intent) {
                super.onReceive(context, intent);
                if(intent.getAction().equals("BLE")){
                    if( (intent.hasExtra("SAMPLE")) & (intent.hasExtra("PEAK_FLAG")) ){
                        float[] sample = intent.getFloatArrayExtra("SAMPLE");
                        int peakFlag = intent.getIntExtra("PEAK_FLAG",-100);
                        Log.d("??????",String.valueOf((peakFlag)));
                        if( (sample!= null) & (peakFlag != -100)) {
                            Log.v(FORDEBUG_TAG,"WINDOW_CNT:"+String.valueOf(windowCnt));
                            Log.v(FORDEBUG_TAG,"DATA_FLAG:"+String.valueOf(dataFlag));
                            setData(sample);
                            if( peakFlag != -128){
                                //R?????? ??????
                                Log.d("????????????",String.valueOf((peakFlag)));
                                getPeakIndex(peakFlag);
                            }
                            updateDataFlag();
                            getSegment();
                            switchWindow();
                        }
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
        //Log.d(FORDEBUG_TAG,String.valueOf(dataFlag));
        for(int i=0; i<DATA_LENGTH; i+=1){
            int j = dataFlag+i;
            if(j<WINDOW_LENGTH){
                //Log.d(FORDEBUG_TAG,"??????x");
                originalEcg[j] = data[i];
                squareEcg[j] = data[i];
            }
            else{
                originalEcg[i] = data[i];
                //Log.d(FORDEBUG_TAG,"??????O");
                squareEcg[dataFlag+i] = data[i];
            }
        }
        setBleDataPlot(data);
    }
        private void setBleDataPlot(float[] data){
            Log.v(FUNCTION_TAG,"user:setBleDataPlot()");
            Intent intent = new Intent("ALL");
            intent.putExtra("data",data);
            intent.putExtra("startIndex",String.valueOf(dataFlag));
            sendBroadcast(intent);
        }

    private void updateDataFlag(){
        dataFlag += DATA_LENGTH;
    }



    private void switchWindow(){
        Log.v(FUNCTION_TAG,"user:switchWindow()");
        if(dataFlag==WINDOW_LENGTH){
            dataFlag=0;
            try {
                saveLocalWindow(windowCnt);
            } catch (IOException e) {
                e.printStackTrace();
            }
            windowCnt+=1;
        }
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



    private void getPeakIndex(int peakFlag) {
        Log.v(FUNCTION_TAG,"user:getPeakIndex()");

        int peakWindowCnt = -1;
        int peakIndex = 0;

        if(peakFlag < 0 ){
            //peak??? ????????? ?????????
            if(checkPeakAvailableIndexsExcess()){
                Log.v("fortest1",String.valueOf(peakFlag));
                peakWindowCnt = windowCnt - 1 ;
                peakIndex = WINDOW_LENGTH + peakFlag ;
            }
            else{
                Log.v("fortest2",String.valueOf(peakFlag));
                peakWindowCnt = windowCnt;
                peakIndex = dataFlag + peakFlag ;
            }
        }
        else{
            Log.v("fortest3",String.valueOf(peakFlag));
            //peak??? ??????  ??????
            peakWindowCnt = windowCnt;
            peakIndex = dataFlag + peakFlag;

            Log.v("fortestRpeak",String.valueOf(dataFlag + peakFlag));
        }

        Log.i("forcheckfor_dataFlag",String.valueOf(dataFlag));
        Log.i("forcheckfor_peakFlag:",String.valueOf(peakFlag));
        Log.i("forcheckfor_peakIndex:",String.valueOf(peakIndex));;



        Log.i(SEGMENTATION_TAG,"PEAK!");
        setPeakIndex(peakWindowCnt,peakIndex);
        setSegmentIndex(peakWindowCnt, peakIndex);


    }
        private boolean checkPeakAvailableIndexsExcess(){
            if(dataFlag==0){
                return true;
            }
            else{
                return false;
            }
        }


        private void setPeakIndex(int windowCnt,int peakIndex){
            int[] peak = new int[2];
            peak[0] = windowCnt;
            peak[1] = peakIndex;
            segmentPeakQueue.offer(peak);
        }

        private void setSegmentIndex(int windowCnt,int peakIndex){
            Log.v(FUNCTION_TAG,"user:setSegmentIndex()");

            int startIndex = peakIndex - SEGMENT_START_LENGTH;
            int endIndex = peakIndex  + SEGMENT_END_LENGTH;

            int[] frontSegIndex = new int[3];
            int[] backSegIndex = new int[3];

            if(checkSegmentAvaliableIndexsExcess(startIndex,endIndex)){
                if(startIndex < 0){
                    //rpeak ??????
                    frontSegIndex[0] = windowCnt-1 ;
                    frontSegIndex[1] = WINDOW_LENGTH - Math.abs(startIndex) ;
                    frontSegIndex[2] = WINDOW_LENGTH;
                    backSegIndex[0] = windowCnt;
                    backSegIndex[1] = 0;
                    backSegIndex[2] = endIndex;
                }
                if(endIndex > WINDOW_LENGTH){
                    //rpeak ??????
                    frontSegIndex[0] = windowCnt;
                    frontSegIndex[1] = startIndex;
                    frontSegIndex[2] = WINDOW_LENGTH;
                    backSegIndex[0] = windowCnt+1;
                    backSegIndex[1] = 0 ;
                    backSegIndex[2] = endIndex - WINDOW_LENGTH;
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

            HashMap<String,int[]> segmentIndex = new HashMap<String,int[]>();
            segmentIndex.put("front",frontSegIndex);
            segmentIndex.put("back",backSegIndex);
            segmentIndexQueue.offer(segmentIndex);
        }

            private boolean checkSegmentAvaliableIndexsExcess(int start,int end){
                //?????? ?????? ??????
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
            //Log.i(FORDEBUG_TAG,"QueueLength:"+String.valueOf(segmentIndexQueue.size()));
            int[] peak = segmentPeakQueue.peek();
            HashMap<String,int[]> index = segmentIndexQueue.peek();
            if(index.get("back")[0]==windowCnt){
                //Log.i(FORDEBUG_TAG,"getSegment() : ??????1:flag ??????");
                if(index.get("back")[2]<=dataFlag){
                    //Log.i(FORDEBUG_TAG,"getSegment() : ??????2");
                    //indexing
                    float[] frontEcg = Arrays.copyOfRange(originalEcg,
                            index.get("front")[1],
                            index.get("front")[2]);
                    float[] backEcg = Arrays.copyOfRange(originalEcg,
                            index.get("back")[1],
                            index.get("back")[2]);

                    //Log.i(FORDEBUG_TAG,"Front:"+ Arrays.toString(Arrays.copyOfRange(originalEcg,
                    //        index.get("front")[1],
                    //        index.get("front")[2])));
                    //Log.i(FORDEBUG_TAG,"Back:"+Arrays.toString(Arrays.copyOfRange(originalEcg,
                    //        index.get("back")[1],
                     //       index.get("back")[2])));

                    float[] segmentEcg = new float[frontEcg.length+backEcg.length];
                    System.arraycopy(frontEcg,0,segmentEcg,0,frontEcg.length);
                    //Log.i(FORDEBUG_TAG,"ALL1:"+Arrays.toString(segmentEcg));

                    System.arraycopy(backEcg,0,segmentEcg,frontEcg.length,backEcg.length);
                    //Log.i(FORDEBUG_TAG,"ALL2:"+Arrays.toString(segmentEcg));
                    returnAllResult(peak,index,segmentEcg);

                    segmentPeakQueue.poll();
                    segmentIndexQueue.poll();
                }
                else{
                    break;
                }
            }
            else if(index.get("back")[0]<windowCnt){
                    Log.v("fortest","herererehrerhe!!!");
                    segmentPeakQueue.poll();
                    segmentIndexQueue.poll();
            }
            else{
                break;
            }
        }
    }

        private void returnAllResult(int[] peakIndex,HashMap<String,int[]> segmentIndex,float[] segment){
            Log.v(FUNCTION_TAG,"user:returnAllResult()");

            //interval
            float interval = getInterval(peakIndex[1]);
            setInterval(interval);

            //predict
            float[] inputEcg = minMaxScale(segment);
            float[] inputInterval = getInputInterval();

            Log.i(TENSORFLOW_TAG+"__INPUT_SAMPLE",String.valueOf(inputEcg.length));
            Log.i(TENSORFLOW_TAG+"__INPUT_INTERVAL",Arrays.toString(inputInterval));

            float[][] output = predict(inputEcg,inputInterval);

            //ann
            int flag = outputFlag(output);
            String predictAnn = outputAnn(flag);
            Log.i(TENSORFLOW_TAG+"__resultFlag",String.valueOf(flag));
            //bpm
            int bpm = getBpm(currentInterval1);
            //predictAccuracyCheck
            boolean accuracyCheck = predictAccuracyCheck(output[0][flag]);


            //plot _ notification
            setNotification(bpm,predictAnn);

            setAllPeakPlot(peakIndex[1],accuracyCheck,flag);
            setSegmentPeakPlot(inputEcg,accuracyCheck,bpm,predictAnn);

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




            public float getInterval(int currentRpeakIndex){
                Log.v(FUNCTION_TAG,"user:getBpm()");
                float RR_interval = 0;

                if(currentRpeakIndex < prevRpeakIndex){
                    Log.v("FORTEST_??????",String.valueOf(prevRpeakIndex));
                    Log.v("FORTEST_??????",String.valueOf(currentRpeakIndex));
                    RR_interval = currentRpeakIndex + (WINDOW_LENGTH-prevRpeakIndex);
                }
                else{
                    Log.v("FORTEST_?????????",String.valueOf(prevRpeakIndex));
                    Log.v("FORTEST_?????????",String.valueOf(currentRpeakIndex));
                    RR_interval = currentRpeakIndex - prevRpeakIndex;
                }

                RR_interval = RR_interval * PERIOD;
                Log.i("RR INTERVAL",String.valueOf(RR_interval));

                prevRpeakIndex = currentRpeakIndex;

                return RR_interval;
            }
                private void setInterval(float interval){
                    //1
                    currentInterval1 = interval;

                    int flag = currentInteval10Flag;
                    //10
                    if(flag<10){
                        currentInterval10Array[flag] = interval;

                        flag += 1;
                        currentInteval10Flag = flag;
                    }
                    else{
                        for(int i=0; i<(10-1); i++){
                            currentInterval10Array[i] = currentInterval10Array[i+1];
                        }
                        currentInterval10Array[currentInterval10Array.length-1] = interval;
                    }
                }
                    private float[] getInputInterval(){
                        float[] inputInterval = new float[2];
                        inputInterval[0] = currentInterval1;

                        float avgInterval = 0;
                        for(int i=0; i<10; i++){
                            avgInterval += currentInterval10Array[i];
                        }
                        avgInterval  /= 10;
                        inputInterval[1] = avgInterval;

                        return inputInterval;
                    }

                private int getBpm(float interval){
                    return (int)(60 / interval);
                }




            private float[][] predict(float[] inputSample,float[] inputInterval){
                Log.v(FUNCTION_TAG,"user:predict()");

                float[][][] inputSampleTensor = inputSampleProcessing(inputSample);
                float[][] inputIntervalTensor = inputIntervalProcessing(inputInterval);

                Object[] inputs = {inputSampleTensor,inputIntervalTensor};
                float[][] output = new float[1][OUTPUT_LENGTH];
                Map<Integer,Object> outputs = new HashMap<>();
                outputs.put(0,output);

                //Log.i(TENSORFLOW_TAG,Arrays.deepToString(output));
                interpreter.runForMultipleInputsOutputs(inputs,outputs);
                //Log.i(TENSORFLOW_TAG,Arrays.deepToString(output));
                Log.i(TENSORFLOW_TAG+"__result", String.valueOf(Arrays.toString(output[0])));
                return output;
            }

                //to tensor input
                private float[][][] inputSampleProcessing(float[] data){
                    float[][][] input = new float[1][INPUT_LENGTH][1];
                    for(int i=0; i<data.length; i++){
                        input[0][i][0] = data[i];
                    }
                    return input;
                }
                private float[][] inputIntervalProcessing(float[] data){
                    float[][] input = new float[1][2];
                    input[0][0] = currentInterval1;
                    input[0][1] = currentInterval10;
                    return input;
                }
            //return high value index
            private int outputFlag(float[][] output){
                float value = -1;
                int flag = 0;
                for(int i=0; i< output[0].length; i++){
                    if(output[0][i]>=value){
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
                Log.v(FUNCTION_TAG,"user:setNotification()");
                notificationManager.notify(1,new NotificationCompat.Builder(this, "EcgProcess")
                        .setContentTitle("EcgStatus")
                        .setContentText("BPM: "+BPM+"  "+ "ANN: " + predict )
                        .setSmallIcon(R.mipmap.ic_launcher).build());
            }


            
            private void setAllPeakPlot(int peakIndex,boolean predictAccuracy,int flag){
                Log.v(FUNCTION_TAG,"user:setAllPeakPlot()");
                    Intent intent = new Intent("ALL_PEAK");
                    intent.putExtra("index",peakIndex);
                    intent.putExtra("predictAcc",predictAccuracy);
                    intent.putExtra("flag",flag);
                    sendBroadcast(intent);
            }


            public void setSegmentPeakPlot(float[] data,boolean predictAccuracyFlag,int bpm,String predict){
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
