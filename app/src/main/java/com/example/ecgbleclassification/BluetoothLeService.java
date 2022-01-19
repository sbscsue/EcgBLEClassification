package com.example.ecgbleclassification;

import android.annotation.SuppressLint;
import android.app.Notification;
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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.UUID;

public class BluetoothLeService extends Service {
    final String SERVICE_TAG = "BLE_SERVICE_CHECK";
    final String GATT_TAG = "GATT_CHECK";

    boolean gattConnectionState = false;


    Resources res;
    NotificationManagerCompat notiManager;

    BluetoothManager manager;
    BluetoothAdapter adapter;
    BluetoothDevice device;
    BluetoothGatt bluetoothGatt;






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

        Intent intent = new Intent(getApplicationContext(),EcgProcess.class);
        startService(intent);

        res = getResources();
        //계속 켜지게
        //startForegroundService();



    }

    @SuppressLint("MissingPermission")
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(SERVICE_TAG,"DESTROY SERVICE");
        if(gattConnectionState){
            bluetoothGatt.disconnect();
        }

    }

    //bluetooth
    @SuppressLint("MissingPermission")
    public void getDevice(BluetoothDevice device){
        Log.i(SERVICE_TAG,"GET BLE DEVICE");
        this.device = device;

        Log.i(SERVICE_TAG,device.getName());
    }

    @SuppressLint("MissingPermission")
    public void connect(){
        Log.i(SERVICE_TAG,"CONNECT GATT SERVER");
        if(gattConnectionState==false){
            bluetoothGatt = device.connectGatt(this,true,gattCallback);
            if(bluetoothGatt.connect()==true){
                gattConnectionState = true;

            }
            else{
                gattConnectionState = false;
            }
        }
    }

    @SuppressLint("MissingPermission")
    public void disconnect(){
        Log.i(SERVICE_TAG,"DISCONNECT GATT SERVER");
        if(gattConnectionState){
            bluetoothGatt.disconnect();
        }

    }

    public boolean getConnectState(){
        return gattConnectionState;
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
            Log.i(GATT_TAG,"SERVICES IS DISCOVERED");


            for (BluetoothGattService service : gatt.getServices()) {
                Log.i(GATT_TAG,service.getUuid().toString());
                if(service.getUuid().toString().equals("00001523-1212-efde-1523-785feabcd123")){
                    Log.i(GATT_TAG,"SERVICE GET");
                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        Log.i(GATT_TAG,characteristic.getUuid().toString());
                        if(characteristic.getUuid().toString().equals("00001524-1212-efde-1523-785feabcd123")){
                            Log.i(GATT_TAG,"CHARATERISTIC GET");
                            gatt.setCharacteristicNotification(characteristic,true);

                            for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
                                Log.i("??",descriptor.getUuid().toString());
                            }

                            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            gatt.writeDescriptor(descriptor);




                            break;
                        }
                    }
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            Log.i(GATT_TAG,"Notify available");
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.i(GATT_TAG,"GATT DATA NOTIFY");

            byte[] data = characteristic.getValue();
            send_ble_data(data);

        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.i(GATT_TAG,"GATT DATA READ");


        }

    };


    private void send_ble_data(byte[] data){
        Intent intent1 = new Intent("toGraph");
        intent1.putExtra("BLE_DATA",data);
        sendBroadcast(intent1);


        Intent intent2 = new Intent("toService");
        intent2.putExtra("BLE_DATA",data);
        sendBroadcast(intent2);



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
        Intent notificationIntent = new Intent(this, MainActivity.class);
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



