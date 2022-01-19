package com.example.ecgbleclassification;

import android.app.ForegroundServiceStartNotAllowedException;
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
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.github.mikephil.charting.data.Entry;
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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PipedReader;
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
    final String FORDEBUG_TAG = "FORDEBUG_TAG";


    int DATA_LENGTH;
    int SAMPLING_LATE;
    float PERIOD;

    int WINDOW_LENGTH ;
    int SEGMENT_LENGTH;

 

    //Broadcast Receiver
    Receiver receiver;




    //firebase
    double cnt;
    String time;
    private FirebaseAuth mAuth;
    private FirebaseDatabase mdb;
    DatabaseReference parent;


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


    //other
    Resources res;


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

        /*
       createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, "EcgProcess")
                .setContentTitle("Foreground Service")
                .setContentText("ECGProcess")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();

        startForeground(1,notification);
        */
        Log.i(SERVICE_TAG,"START SERVICE");
        res = getResources();
        DATA_LENGTH =  res.getInteger(R.integer.data_length);
        SAMPLING_LATE =  res.getInteger(R.integer.sampling_rate);
        PERIOD = Float.valueOf(res.getString(R.string.period));
        WINDOW_LENGTH = res.getInteger(R.integer.window_length);
        SEGMENT_LENGTH = res.getInteger(R.integer.segment_length);

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
                    switchWindow();
                    findPeak();

                    setSegment();



                }
                if(intent.getAction().equals("Peak")){
                    Log.i(BROADCAST_TAG,intent.getAction());
                    //segmentation -predict - save
                }

            }
        };
        registerReceiver(receiver,theFilter);

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

    }

    @Override
    public void onDestroy() {

        super.onDestroy();

        Log.i(SERVICE_TAG,"onDestory()");
        unregisterReceiver(receiver);
    }


    public void setThresholdValue(){
        int sum = 0;
        for(int i=0; i<WINDOW_LENGTH; i++){
            sum +=  squareEcg[0][i];
        }

        thresholdMode=1;
        thresholdValue = (int) ((sum / WINDOW_LENGTH)*1.9);
        Log.i(SEGMENTATION_TAG,"threshold_value:"+String.valueOf(thresholdValue));

        window_flag=0;
    }

    private void setWindow(byte[] data){
        int d;
        int dd;


        for(int i=0; i<DATA_LENGTH; i+=1){
            if(i<244) {
                d = data[i] & 0xff;

            }
            else{
                d = data[243];
            }

            if(d < 0){
                d = originalEcg[window_flag][next_flag+i-1] ;
            }

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

    private void switchWindow(){
        if(next_flag==WINDOW_LENGTH){
            next_flag=0;
            if(thresholdMode==-1){
                setThresholdValue();
            }
            else{
                Log.i(SEGMENTATION_TAG,"SWITH_UpCnt:"+String.valueOf(window_cnt));
                Log.i(SEGMENTATION_TAG,"SWITH_Flag:"+String.valueOf(window_flag));

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


    private void findPeak() {
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
       //Log.i(SEGMENTATION_TAG,String.valueOf(segmentIndexs.size()));
        while(!segmentIndexs.isEmpty()){
            //Log.i(SEGMENTATION_TAG,String.valueOf(segmentIndexs.isEmpty()));
            //Log.i(SEGMENTATION_TAG,"Queue:"+String.valueOf(segmentIndexs.size()));
            HashMap<String,Integer> index = segmentIndexs.peek();
            if(index.get("back_flag")==window_flag){
                Log.i("indexd","f "+String.valueOf(index.get("back_end")));
                Log.i("indexd","o "+String.valueOf(next_flag));
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

                    //forDebug(index,segmentEcg);

                    int bpm = getBpm(index.get("peak_sample"));

                    segmentPlot(segmentEcg,bpm);
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

    public int getBpm(int currentRpeak){
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


    public void segmentPlot(int[] data,int bpm){
        Intent intent = new Intent("segmentation");
        intent.putExtra("data",data);
        sendBroadcast(intent);


        intent = null;
        intent = new Intent("INFORMATION");
        intent.putExtra("BPM",bpm);
        //intent.putExtra("predict",predict);
        sendBroadcast(intent);

    }

    public void toTensor(){

    }

    public String predict(){
        String output = "";

        return output;
    }

    public void save(){

    }


    public int[] getWindowCnt(int front, int end){
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



    private MappedByteBuffer loadModelFile(String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private int otherwindowFlag(int original){
        if(original==0){
            return 1;
        }
        else{
            return 0;
        }

    }





}
