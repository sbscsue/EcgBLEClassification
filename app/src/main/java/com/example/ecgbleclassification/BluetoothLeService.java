package com.example.ecgbleclassification;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.IntStream;
import uk.me.berndporr.iirj.Butterworth;
import uk.me.berndporr.iirj.ChebyshevI;

public class BluetoothLeService extends Service {
    //values
    final String SERVICE_TAG = "BLE_SERVICE_CHECK";
    final String GATT_TAG = "GATT_CHECK";

    private boolean gattConnectionState = false;
    private String mac_address;
    BluetoothDevice device;


    int DATA_LENGTH;

    //default
    private Resources res;

    private NotificationManagerCompat notiManager;

    private SharedPreferences preferences;
    private SharedPreferences.Editor editor;


    //ble
    BluetoothManager manager;
    BluetoothAdapter bluetoothAdapter;
    BluetoothGatt bluetoothGatt;

    //service
    Intent intent;

    Butterworth butterworthHightPassFilter = new Butterworth();
    Butterworth butterworthLowPassFilter = new Butterworth();

    ChebyshevI chebyshevIFilter = new ChebyshevI();



    public BluetoothLeService() {

    }

    IBinder serviceBinder = new BleBinder();

    class BleBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(SERVICE_TAG,"CREATE SERVICE");

        //DEFALT
        res = getResources();
        DATA_LENGTH =  res.getInteger(R.integer.data_length);
        preferences = getSharedPreferences(getString(R.string.S_NAME),Context.MODE_PRIVATE);
        editor = preferences.edit();

        manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = manager.getAdapter();

        //chebyshevIFilter.highPass(2    ,400,3,3);
        chebyshevIFilter.bandPass(2,400,21,19,3);

        butterworthHightPassFilter.highPass(2,400,0.5);
        butterworthLowPassFilter.lowPass(3,400,40);



        //계속 켜지게
        //startForegroundService(intent);

        //BLE
        getDevice();
        if(mac_address!=null) {
            Log.i(SERVICE_TAG, "FISRT BLE CONNECT - SUCESS");
            connect();
        }

    }

    @SuppressLint("MissingPermission")
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(SERVICE_TAG,"DESTROY SERVICE");
        disconnect();

    }

    //bluetooth
    public void getDevice(){
        Log.i(SERVICE_TAG,"GET BLE DEVICE");

        String check = null;
        mac_address = preferences.getString(res.getString(R.string.S_BLUETOOTH),check);
        if(mac_address==null){
            Toast.makeText(this,R.string.mac_address_empty,Toast.LENGTH_SHORT).show();
        }
        else{
            device = bluetoothAdapter.getRemoteDevice(mac_address);
            Log.i(SERVICE_TAG,mac_address);
        }

    }

    @SuppressLint("MissingPermission")
    public void setDevice(BluetoothDevice device){
        Log.i(SERVICE_TAG,"SET BLE DEVICE");

        editor.putString(res.getString(R.string.S_BLUETOOTH),device.getAddress());
        editor.commit();

        disconnect();
        getDevice();
        connect();
    }

    public boolean getConnectState(){
        return gattConnectionState;
    }

    @SuppressLint("MissingPermission")
    //디바이스 등록안해놓고 접근할때 오류 !
    public void connect(){
        Log.i(GATT_TAG,"CONNECT GATT SERVER");
        if(gattConnectionState==false){
            bluetoothGatt = device.connectGatt(this,true,gattCallback);
            if(bluetoothGatt.connect()==true){
                Log.i(GATT_TAG,"TRUE");
                gattConnectionState = true;
            }
            else{
                Log.i(GATT_TAG,"FALSE");
                gattConnectionState = false;
            }
        }
    }

    @SuppressLint("MissingPermission")
    public void disconnect(){
        Log.i(GATT_TAG,"DISCONNECT GATT SERVER");
        if(gattConnectionState){
            Log.i(GATT_TAG,"TRUE");
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            gattConnectionState = false;
            //stopService(intent);
        }

    }






    private BluetoothGattCallback gattCallback= new BluetoothGattCallback(){
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.i(GATT_TAG,"GATT SERVER CONNECT CHANGE");
            if(newState==BluetoothProfile.STATE_DISCONNECTED){
                Log.i(GATT_TAG,"GATT SERVER DISCONNECTED");
            }
            if(newState==BluetoothProfile.STATE_CONNECTED){
                Log.i(GATT_TAG,"GATT SERVER CONNECTED");
                gatt.discoverServices();
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.i(GATT_TAG, "SERVICES IS DISCOVERED");

            String[] s_uuid = res.getStringArray(R.array.S_UUID);
            String[] c_uuid = res.getStringArray(R.array.C_UUID);

            for(int i =0; i<s_uuid.length; i++){
                Log.i(GATT_TAG,s_uuid[i]);
                BluetoothGattService service = bluetoothGatt.getService(UUID.fromString(s_uuid[i]));
                if (service != null){
                    BluetoothGattCharacteristic charac = service.getCharacteristic(UUID.fromString(c_uuid[i]));
                    if (charac != null){
                        //notifi
                        gatt.setCharacteristicNotification(charac,true);
                        //startService(intent);
                        //descripter
                        for(BluetoothGattDescriptor des : charac.getDescriptors()){
                            Log.i(GATT_TAG,"SET DESCRIPTOR");
                            des.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            gatt.writeDescriptor(des);
                            break;
                        }


                    }
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            //Log.i(GATT_TAG,"GATT DATA NOTIFY");

            byte[] data = characteristic.getValue();
            Log.i("for10bitCheck",String.valueOf(data.length));
            sendBleData(data);
            //sendBleDataTestUse_filter(data);
        }


        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            Log.i(GATT_TAG,"NOTIFY SET");
        }


        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.i(GATT_TAG,"GATT DATA READ");
        }

    };

    //ecg sample + r peak index 받을 때
    private void sendBleData(byte[] data){
        //Log.d("blecheck",String.valueOf(data.length));

        //!8bit/10bit parsing 설정
        int[] parsingData = parsing10bitByteArrayToIntArray(data);

        //ecg sample data
        float[] sampleData = new float[parsingData.length-1];
        for(int i=0; i<(sampleData.length); i++){
            sampleData[i] = Float.valueOf(parsingData[i]);
        }
        float[] filterData = baseLineRemoveFiltering(sampleData);

        //ecg peak data
        int peakDetectionIndexBLE = parsingData[parsingData.length-1];

        Intent intent = new Intent("BLE");
        intent.putExtra("SAMPLE",sampleData);
        intent.putExtra("PEAK_FLAG",(short)peakDetectionIndexBLE);
        sendBroadcast(intent);

    }

    //*filter확인용 ble test
    private void sendBleDataTestUse_filter(byte[] data){
        int[] parsingData = parsing8bitByteArrayToIntArray(data);

        //ecg sample data
        float[] sampleData = new float[parsingData.length-1];
        for(int i=0; i<sampleData.length-1; i++){
            sampleData[i] = Float.valueOf(parsingData[i]);
        }
        float[] filterData = baseLineRemoveFiltering(sampleData);

        float[] allData = new float[parsingData.length*2];
        //복붙!!
        System.arraycopy(sampleData,0,allData,0,sampleData.length);
        System.arraycopy(filterData,0,allData,parsingData.length,filterData.length);

        Intent intent = new Intent("BLE");
        intent.putExtra("TestUse_filter",allData);
        sendBroadcast(intent);

    }

    //nordic용 parsing
    // byte(int_1byte) -> int
    private int[] parsing8bitByteArrayToIntArray(byte[] data){
        int[] parsingData = new int[data.length];

        for(int i=0; i<data.length; i++){
            parsingData[i] = Byte.toUnsignedInt(data[i]);
        }

        Log.i("forOsiloCheck",Arrays.toString(parsingData));
        return parsingData;
    }

    //nordic용 parsing
    //byte(int_2byte) -> int
    private int[] parsing10bitByteArrayToIntArray(byte[] data){
        int[] parsingData = new int[data.length/2];

        int t = 0;
        for(int i=0; i<(data.length)/2 -1; i=i+1){
            parsingData[i] = ((((data[i*2+1] & 0xff) << 8) | (data[i*2] & 0xff)) & 0x03ff) ;
            Log.i("forOsiloCheck_front",String.valueOf((data[i*2] & 0xff)));
            Log.i("forOsiloCheck_back",String.valueOf((data[i*2+1] & 0xff)));
            Log.i("forOsiloCheck_all",String.valueOf( parsingData[i] ));
            t= i;
        }

        int flag = parsingData.length-1;


        parsingData[flag] = ((((data[flag*2+1] & 0xff) << 8) | (data[flag*2] & 0xff)) );
        Log.i("forOsiloCheck_index",String.valueOf((short)parsingData[flag]));
        return parsingData;
    }

    //컴퓨터 ble 테스트용 parsing
    private float[] parsingStringCsvToFloatArray(byte[] data){
        String segmentStringEcg = new String(data);
        String [] sampleStringEcg = segmentStringEcg.split(",");

        float [] sampleFloatEcg = new float[DATA_LENGTH];
        for(int i=0; i<DATA_LENGTH; i++){
            sampleFloatEcg[i] = Byte.toUnsignedInt(data[i]);
        }
        return sampleFloatEcg;
    }


    //sample 필터링
    private float[] baseLineRemoveFiltering(float[] data){
        //Log.i("baselineRemove","check");
        double[] doubleData = new double[data.length];
        IntStream.range(0, data.length).forEach(index -> doubleData[index] = data[index]);

        double[] filterData = new double[data.length];
        float[] floatData = new float[data.length];
        for(int i=0; i<doubleData.length; i++){
            filterData[i] = doubleData[i];
            filterData[i] = butterworthHightPassFilter.filter(filterData[i]);
            filterData[i] = butterworthLowPassFilter.filter(filterData[i]);
            //filterData[i] = chebyshevIFilter.filter(filterData[i]);
            floatData[i] = (float) filterData[i];

        }

        return floatData;
    }




    NotificationCompat.Builder builder1 = new NotificationCompat.Builder(this,"CONNECT_STATE")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("ECG MONITORING DEVICE")
            .setContentText("BLE NOT CONNECT");


    NotificationCompat.Builder builder2 = new NotificationCompat.Builder(this,"CONNECT_STATE")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("ECG MONITORING DEVICE")
            .setContentText("BLE CONNECT");

    //종료 안됨!
    void startForegroundService() {
        Intent notificationIntent = new Intent(this, ScanActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);


        if (Build.VERSION.SDK_INT >= 26) {
            String CHANNEL_ID = "CONNECT_STATE";

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "CONNECT STATE",
                    NotificationManager.IMPORTANCE_DEFAULT);

            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                    .createNotificationChannel(channel);

        }
        startForeground(1, builder1.build());
    }
}



