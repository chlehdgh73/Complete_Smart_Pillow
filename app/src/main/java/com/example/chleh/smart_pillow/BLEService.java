package com.example.chleh.smart_pillow;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;
import static java.lang.StrictMath.abs;

public class BLEService extends Service {

    public static final int STATE_INIT = 0;
    public static final int STATE_LAIN = 1;
    public static final int STATE_DEEP = 2;
    public static final int STATE_SHALLOW = 3;
    public static final int STATE_TEMP_AWAKE = 4;
    public static final int STATE_RE_LAIN = 5;
    public static final int STATE_COMPLETE_AWAKE = 6;

    public static final String BLE_NOTIFY = "notify_broadcast.BLEService";
    public static final String DEVICE_ADDRESS = "this_device_address";
    public static final String BLE_WARNING = "warning_broadcast.BLEService";
    public static final String STATE_CHANGE_NOTIFY = "notify_state_changed";
    public static final String NOTIFY_STATE = "this_state_value";

    private static final int BLE_SCAN_PERIOD = 6000;
    private static final int DECISION_VALUE_LAIN = 200;
    private static final int DECISION_VALUE_DIFF = 50;

    private final static String TARGET_SERVICE_UUID = "0000ffe0-0000-1000-8000-00805f9b34fb";
    private final static String TARGET_CHARA_UUID = "0000ffe1-0000-1000-8000-00805f9b34fb";
    private final static String TARGET_NAME = "HMSoft";

    private final static String SAVED_STATE_FILE_NAME = "saved_state.txt";
    private final static String SAVED_ADDRESS_FILE_NAME = "saved_address.txt";
    private final static String SAVED_TEMP_LOG = "saved_log.txt";
    private final static String LOG_FILE_FOLDER = "log";

    private String TARGET_ADDRESS = "";

    private Context service_context;

    private List<BluetoothDevice> device_list = new ArrayList<BluetoothDevice>();
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothLeScanner mBLEScanner = null;
    private BluetoothGatt mBluetoothGatt = null;
    private BluetoothGattCharacteristic mBleCharacteristic = null;

    private Handler mHandler = new Handler();

    private boolean is_ble_possible = true;
    private boolean is_ble_scanned = false;
    private boolean is_ble_connected = false;
    private boolean mScanning = false;

    private boolean is_lain = false;
    private int now_state;
    private int[] sensor_value = new int[3];
    private long state_start_time;
    private boolean restart_flag = false;
    private boolean reset_flag = true;

    private final IBinder mBinder = new LocalBinder();

    @Override
    public void onCreate(){
        super.onCreate();

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            is_ble_possible = false;
            return;
        }

        final BluetoothManager bluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        if (mBluetoothAdapter == null) {
            is_ble_possible = false;
            return;
        }
        service_context = this;
        mScanning = false;
        restart_flag = false;
        reset_flag = true;

        String folder_path = getFilesDir().getAbsolutePath() + File.separator + LOG_FILE_FOLDER;
        File folder = new File(folder_path);
        if(!folder.exists()){
            folder.mkdirs();
        }

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startid){

        if(is_ble_possible == false){
            return super.onStartCommand(intent,flags,startid);
        }

        if(intent != null){
            if(reset_flag == true){
                restart_flag = false;
                reset_flag = false;
                now_state = STATE_INIT;
                for(int i = 0 ; i < 3 ; i++) {
                    sensor_value[i] = 0;
                }
                state_start_time = System.currentTimeMillis();
                is_lain = false;
                start_connect();
            }
        }
        else{
            restart_flag = true;
            reset_flag = false;
            FileInputStream file_in = null;
            BufferedReader buffer = null;
            try {
                file_in = openFileInput(SAVED_STATE_FILE_NAME);
                buffer = new BufferedReader(new InputStreamReader(file_in));
                now_state = Integer.parseInt(buffer.readLine());
                state_start_time = Long.parseLong(buffer.readLine());
                if(now_state == 0 || now_state == 4 || now_state == 6){
                    is_lain = false;
                }
                else{
                    is_lain = true;
                }
            } catch (FileNotFoundException e) {
                now_state = STATE_INIT;
                state_start_time = System.currentTimeMillis();
                is_lain = false;
            }
            catch (IOException e1){
                now_state = STATE_INIT;
                state_start_time = System.currentTimeMillis();
                is_lain = false;
            }
            finally {
                try {
                    if(buffer != null) buffer.close();
                    if(file_in != null) file_in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            start_connect();
        }
        return super.onStartCommand(intent,flags,startid);
    }

    public class LocalBinder extends Binder {
        BLEService getService() {
            return BLEService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent){
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent){

    }

    @Override
    public boolean onUnbind(Intent intent){
        return true;
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        disconnect_gatt();
    }

    private void start_connect(){
        if(mScanning == true){
            return;
        }
        scanLeDevice(true);

        FileInputStream file_in = null;
        BufferedReader buffer = null;
        try {
            file_in = openFileInput(SAVED_ADDRESS_FILE_NAME);
            buffer = new BufferedReader(new InputStreamReader(file_in));
            TARGET_ADDRESS = buffer.readLine();
        } catch (FileNotFoundException e) {
            TARGET_ADDRESS = "null";
        }
        catch (IOException e1){
            TARGET_ADDRESS = "null";
        }
        finally {
            try {
                if(buffer != null) buffer.close();
                if(file_in != null) file_in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                connect_gatt(TARGET_ADDRESS);
            }
        }, BLE_SCAN_PERIOD);

    }
    private void scanLeDevice(final boolean enable) {
        if(!mBluetoothAdapter.isEnabled()){
            mBluetoothAdapter.enable();
        }

        mBLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
        if(mBLEScanner == null){
            is_ble_scanned = false;
            return;
        }

        if (enable) {
            mScanning = true;
            is_ble_scanned = false;

            device_list.clear();

            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    try {
                        mBLEScanner.stopScan(ScanCallBack);
                    }
                    catch(Exception e){
                    }
                }
            }, BLE_SCAN_PERIOD);

            mBLEScanner.startScan(ScanCallBack);

        } else {
            mScanning = false;
            mBLEScanner.stopScan(ScanCallBack);
        }

    }

    private void connect_gatt(String address){
        if(mBluetoothGatt != null){
            mBluetoothGatt.close();
            is_ble_connected = false;
            mBluetoothGatt = null;
        }
        synchronized (device_list) {
            for (BluetoothDevice device : device_list) {
                if (0 == device.getAddress().compareTo(address)) {
                    mBluetoothGatt = device.connectGatt(service_context, false, mGattCallback);
                    break;
                }
            }
        }
    }

    private void disconnect_gatt(){
        if(mBluetoothGatt != null){
            mBluetoothGatt.close();
            is_ble_connected = false;
            mBluetoothGatt = null;
        }
    }

    private ScanCallback ScanCallBack = new ScanCallback(){
        @Override
        public void onScanResult(int callbackType, ScanResult result){
            processResult(result);
        }
        @Override
        public void onBatchScanResults(List<ScanResult> results){
            for(ScanResult result : results) {
                processResult(result);
            }
        }
        @Override
        public void onScanFailed(int errorCode){
            is_ble_scanned = false;
        }

        private void processResult(final ScanResult result){
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    BluetoothDevice a = result.getDevice();

                    if (a != null && a.getName() != null && 0 == a.getName().compareTo(TARGET_NAME)) {
                        synchronized (device_list) {
                            if (!device_list.contains(a)) {
                                Intent intent = new Intent(BLE_NOTIFY);
                                intent.putExtra(DEVICE_ADDRESS, a.getAddress());
                                sendBroadcast(intent);
                                device_list.add(a);
                                is_ble_scanned = true;
                            }
                        }
                    }

                }
            });
        }
    };

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                is_ble_connected = true;
                mBluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                is_ble_connected = false;
                reScan_device();
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        connect_gatt(TARGET_ADDRESS);
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if(is_ble_connected == true){
                                    return;
                                }
                                long time = System.currentTimeMillis();
                                Intent intent = new Intent(STATE_CHANGE_NOTIFY);
                                switch(now_state){
                                    case STATE_INIT:
                                        break;
                                    case STATE_LAIN:
                                        save_state(STATE_INIT, time);
                                        clear_log_file();
                                        is_lain = false;
                                        now_state = STATE_INIT;
                                        state_start_time = time;
                                        intent.putExtra(NOTIFY_STATE, now_state);
                                        sendBroadcast(intent);
                                        break;
                                    case STATE_DEEP:
                                    case STATE_SHALLOW:
                                    case STATE_RE_LAIN:
                                        save_state(STATE_TEMP_AWAKE, time);
                                        save_change_log(STATE_TEMP_AWAKE, time);
                                        is_lain = false;
                                        now_state = STATE_TEMP_AWAKE;
                                        state_start_time = time;
                                        intent.putExtra(NOTIFY_STATE, now_state);
                                        sendBroadcast(intent);

                                        time = System.currentTimeMillis();
                                        intent = new Intent(STATE_CHANGE_NOTIFY);

                                        save_state(STATE_COMPLETE_AWAKE, time);
                                        save_change_log(STATE_COMPLETE_AWAKE, time);
                                        now_state = STATE_COMPLETE_AWAKE;
                                        state_start_time = time;
                                        intent.putExtra(NOTIFY_STATE, now_state);
                                        sendBroadcast(intent);

                                        time = System.currentTimeMillis();
                                        intent = new Intent(STATE_CHANGE_NOTIFY);

                                        complete_log();
                                        clear_log_file();
                                        save_state(STATE_INIT, time);
                                        now_state = STATE_INIT;
                                        state_start_time = time;
                                        intent.putExtra(NOTIFY_STATE, now_state);
                                        sendBroadcast(intent);

                                        break;
                                    case STATE_TEMP_AWAKE:
                                        save_state(STATE_COMPLETE_AWAKE, time);
                                        save_change_log(STATE_COMPLETE_AWAKE, time);
                                        now_state = STATE_COMPLETE_AWAKE;
                                        state_start_time = time;
                                        intent.putExtra(NOTIFY_STATE, now_state);
                                        sendBroadcast(intent);

                                        time = System.currentTimeMillis();
                                        intent = new Intent(STATE_CHANGE_NOTIFY);

                                        complete_log();
                                        clear_log_file();
                                        save_state(STATE_INIT, time);
                                        now_state = STATE_INIT;
                                        state_start_time = time;
                                        intent.putExtra(NOTIFY_STATE, now_state);
                                        sendBroadcast(intent);
                                        break;
                                    case STATE_COMPLETE_AWAKE:
                                        complete_log();
                                        clear_log_file();
                                        save_state(STATE_INIT, time);
                                        now_state = STATE_INIT;
                                        state_start_time = time;
                                        intent.putExtra(NOTIFY_STATE, now_state);
                                        sendBroadcast(intent);
                                        break;
                                }
                            }
                        },20000);
                    }
                }, BLE_SCAN_PERIOD);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mBleCharacteristic = null;
                find_target(mBluetoothGatt.getServices());
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            data_process(characteristic);
        }

        private void find_target(List<BluetoothGattService> service_list){
            for (BluetoothGattService gattService : service_list){
                if(0 != gattService.getUuid().toString().compareTo(TARGET_SERVICE_UUID)){
                    continue;
                }
                List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics){
                    if(0 == gattCharacteristic.getUuid().toString().compareTo(TARGET_CHARA_UUID)){
                        mBleCharacteristic = gattCharacteristic;
                        mBluetoothGatt.setCharacteristicNotification(mBleCharacteristic, true);
                        break;
                    }
                }
            }
            if(mBleCharacteristic == null){
                Intent intent = new Intent(BLE_WARNING);
                sendBroadcast(intent);
            }
        }

        private void data_process(BluetoothGattCharacteristic characteristic){
            final byte[] data = characteristic.getValue();
            String input_data = new String(data);
            input_data = input_data.substring(0,input_data.length() - 2);
            StringTokenizer tokenizer = new StringTokenizer(input_data,",");
            int[] value = new int[3];
            int i;
            if(tokenizer.countTokens() != 3){
                return;
            }
            i = 0;
            while(tokenizer.hasMoreTokens()){
                value[i] = Integer.parseInt(tokenizer.nextToken());
                i++;
            }
            if(restart_flag == true){
                restart_flag = false;
                i = 0;
                while(i < 3){
                    sensor_value[i] = value[i];
                    i++;
                }
                return;
            }
            int []diff = new int[3];
            i = 0;
            while(i < 3){
                diff[i] =  abs(value[i] - sensor_value[i]);
                i++;
            }
            long time = System.currentTimeMillis();

            Intent intent = new Intent(STATE_CHANGE_NOTIFY);

            switch(now_state){
                case STATE_INIT:
                    if(value[0] > DECISION_VALUE_LAIN || value[1] > DECISION_VALUE_LAIN || value[2] > DECISION_VALUE_LAIN){
                        save_state(STATE_LAIN, time);
                        save_change_log(STATE_LAIN, time);
                        is_lain = true;
                        now_state = STATE_LAIN;
                        state_start_time = time;

                        intent.putExtra(NOTIFY_STATE, now_state);
                        sendBroadcast(intent);
                    }

                    break;
                case STATE_LAIN:
                    if(value[0] > DECISION_VALUE_LAIN || value[1] > DECISION_VALUE_LAIN || value[2] > DECISION_VALUE_LAIN){
                        if(diff[0] > DECISION_VALUE_DIFF || diff[1] > DECISION_VALUE_DIFF || diff[2] > DECISION_VALUE_DIFF){
                            save_state(STATE_LAIN, time);
                            save_change_log(STATE_LAIN, time);
                            state_start_time = time;
                        }
                        else{
                            if((time - state_start_time) > 900000){
                                save_state(STATE_DEEP, time);
                                save_change_log(STATE_DEEP, time);
                                now_state = STATE_DEEP;
                                state_start_time = time;
                                intent.putExtra(NOTIFY_STATE, now_state);
                                sendBroadcast(intent);
                            }
                        }
                    }
                    else{
                        save_state(STATE_INIT, time);
                        clear_log_file();
                        is_lain = false;
                        now_state = STATE_INIT;
                        state_start_time = time;
                        intent.putExtra(NOTIFY_STATE, now_state);
                        sendBroadcast(intent);
                    }
                    break;
                case STATE_DEEP:
                    if(value[0] > DECISION_VALUE_LAIN || value[1] > DECISION_VALUE_LAIN || value[2] > DECISION_VALUE_LAIN){
                        if(diff[0] > DECISION_VALUE_DIFF || diff[1] > DECISION_VALUE_DIFF || diff[2] > DECISION_VALUE_DIFF){
                            save_state(STATE_SHALLOW, time);
                            save_change_log(STATE_SHALLOW, time);
                            now_state = STATE_SHALLOW;
                            state_start_time = time;
                            intent.putExtra(NOTIFY_STATE, now_state);
                            sendBroadcast(intent);
                        }
                    }
                    else{
                        save_state(STATE_TEMP_AWAKE, time);
                        save_change_log(STATE_TEMP_AWAKE, time);
                        is_lain = false;
                        now_state = STATE_TEMP_AWAKE;
                        state_start_time = time;
                        intent.putExtra(NOTIFY_STATE, now_state);
                        sendBroadcast(intent);
                    }
                    break;
                case STATE_SHALLOW:
                    if(value[0] > DECISION_VALUE_LAIN || value[1] > DECISION_VALUE_LAIN || value[2] > DECISION_VALUE_LAIN){
                        if(diff[0] > DECISION_VALUE_DIFF || diff[1] > DECISION_VALUE_DIFF || diff[2] > DECISION_VALUE_DIFF){
                            save_state(STATE_SHALLOW, time);
                            save_change_log(STATE_SHALLOW, time);
                            state_start_time = time;
                        }
                        else{
                            if((time - state_start_time) > 600000){
                                save_state(STATE_DEEP, time);
                                save_change_log(STATE_DEEP, time);
                                now_state = STATE_DEEP;
                                state_start_time = time;
                                intent.putExtra(NOTIFY_STATE, now_state);
                                sendBroadcast(intent);
                            }
                        }
                    }
                    else{
                        save_state(STATE_TEMP_AWAKE, time);
                        save_change_log(STATE_TEMP_AWAKE, time);
                        is_lain = false;
                        now_state = STATE_TEMP_AWAKE;
                        state_start_time = time;
                        intent.putExtra(NOTIFY_STATE, now_state);
                        sendBroadcast(intent);
                    }
                    break;
                case STATE_TEMP_AWAKE:
                    if(value[0] > DECISION_VALUE_LAIN || value[1] > DECISION_VALUE_LAIN || value[2] > DECISION_VALUE_LAIN){
                        save_state(STATE_RE_LAIN, time);
                        save_change_log(STATE_RE_LAIN, time);
                        is_lain = true;
                        now_state = STATE_RE_LAIN;
                        state_start_time = time;
                        intent.putExtra(NOTIFY_STATE, now_state);
                        sendBroadcast(intent);
                    }
                    else{
                        if((time - state_start_time) > 600000){
                            save_state(STATE_COMPLETE_AWAKE, time);
                            save_change_log(STATE_COMPLETE_AWAKE, time);
                            now_state = STATE_COMPLETE_AWAKE;
                            state_start_time = time;
                            intent.putExtra(NOTIFY_STATE, now_state);
                            sendBroadcast(intent);
                        }
                    }
                    break;
                case STATE_RE_LAIN:
                    if(value[0] > DECISION_VALUE_LAIN || value[1] > DECISION_VALUE_LAIN || value[2] > DECISION_VALUE_LAIN){
                        if(diff[0] > DECISION_VALUE_DIFF || diff[1] > DECISION_VALUE_DIFF || diff[2] > DECISION_VALUE_DIFF){
                            save_state(STATE_RE_LAIN, time);
                            save_change_log(STATE_RE_LAIN, time);
                            state_start_time = time;
                        }
                        else{
                            if((time - state_start_time) > 600000){
                                save_state(STATE_DEEP, time);
                                save_change_log(STATE_DEEP, time);
                                now_state = STATE_DEEP;
                                state_start_time = time;
                                intent.putExtra(NOTIFY_STATE, now_state);
                                sendBroadcast(intent);
                            }
                        }
                    }
                    else{
                        save_state(STATE_TEMP_AWAKE, time);
                        save_change_log(STATE_TEMP_AWAKE, time);
                        is_lain = false;
                        now_state = STATE_TEMP_AWAKE;
                        state_start_time = time;
                        intent.putExtra(NOTIFY_STATE, now_state);
                        sendBroadcast(intent);

                    }
                    break;
                case STATE_COMPLETE_AWAKE:
                    complete_log();
                    clear_log_file();
                    save_state(STATE_INIT, time);
                    now_state = STATE_INIT;
                    state_start_time = time;
                    intent.putExtra(NOTIFY_STATE, now_state);
                    sendBroadcast(intent);

                    break;
            }
            i = 0;
            while(i < 3){
                sensor_value[i] = value[i];
                i++;
            }
        }


    };

    private boolean save_state(int state, long time){
        FileOutputStream file_out = null;
        PrintWriter writer = null;

        try {
            file_out = openFileOutput(SAVED_STATE_FILE_NAME,Context.MODE_PRIVATE);
            writer = new PrintWriter(file_out);
            writer.println(state);
            writer.println(time);

        } catch (FileNotFoundException e) {
            return false;
        }
        catch (IOException e1){
            return false;
        }
        finally{
            try {
                if(writer != null) writer.close();
                if(file_out != null) file_out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    private boolean save_change_log(int state, long time){
        FileOutputStream file_out = null;
        PrintWriter writer = null;
        try {
            file_out = openFileOutput(SAVED_TEMP_LOG,Context.MODE_APPEND);
            writer = new PrintWriter(file_out);
            writer.println(state + ":" + time);
        } catch (FileNotFoundException e) {
            return false;
        }
        catch (IOException e1){
            return false;
        }
        finally{
            try {
                if(writer != null) writer.close();
                if(file_out != null) file_out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    private boolean clear_log_file(){
        String path = getFilesDir().getAbsolutePath() + File.separator + SAVED_TEMP_LOG;
        File file = new File(path);
        if(file != null){
            if(file.exists()){
                return file.delete();
            }
        }
        return false;
    }
    private boolean complete_log(){
        FileOutputStream file_out = null;
        PrintWriter writer = null;
        FileInputStream file_in = null;
        BufferedReader buffer = null;

        long first_time;
        String save_path = "Unknown.txt";
        String temp;

        Date start_time, end_time;
        SimpleDateFormat format = new SimpleDateFormat("yyyy:MM:dd:HH:mm:ss");

        try {
            file_in = openFileInput(SAVED_TEMP_LOG);
            buffer = new BufferedReader(new InputStreamReader(file_in));

            if((temp = buffer.readLine()) != null){
                StringTokenizer tokenizer = new StringTokenizer(temp,":");
                //처음 토큰(state)는 버리고
                tokenizer.nextToken();
                first_time = Long.parseLong(tokenizer.nextToken());
                start_time = new Date(first_time);
                end_time = new Date(state_start_time);
                save_path = getFilesDir().getAbsolutePath() + File.separator + LOG_FILE_FOLDER;
                save_path += File.separator + format.format(first_time) + "~" + format.format(end_time) + ".txt";
            }

            file_out = new FileOutputStream(save_path);
            writer = new PrintWriter(file_out);

            if(temp != null){
                writer.println(temp);
            }
            while((temp = buffer.readLine()) != null){
                writer.println(temp);
            }

        } catch (FileNotFoundException e) {
            return false;
        }
        catch (IOException e1){
            return false;
        }
        finally{
            try {
                if(writer != null) writer.close();
                if(file_out != null) file_out.close();
                if(buffer != null) buffer.close();
                if(file_in != null) file_in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;
    }



    public List<String> query_device() {
        List<String> result = new ArrayList<String>();

        synchronized (device_list) {
            for (BluetoothDevice device : device_list) {
                result.add(device.getAddress());
            }
        }
        return result;
    }

    public int query_state(){
        return now_state;
    }

    public boolean query_lain_state(){
        return is_lain;
    }

    public boolean query_connection(){
        return is_ble_connected;
    }

    public boolean select_device(String address){
        FileOutputStream file_out = null;
        PrintWriter writer = null;

        try {
            file_out = openFileOutput(SAVED_ADDRESS_FILE_NAME,Context.MODE_PRIVATE);
            writer = new PrintWriter(file_out);
            writer.println(address);
        } catch (FileNotFoundException e) {
            return false;
        }
        catch (IOException e1){
            return false;
        }
        finally{
            try {
                if(writer != null) writer.close();
                if(file_out != null) file_out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        TARGET_ADDRESS = address;
        connect_gatt(address);

        if(address.equals("")){
            long time = System.currentTimeMillis();
            Intent intent = new Intent(STATE_CHANGE_NOTIFY);
            save_state(STATE_INIT, time);
            clear_log_file();
            is_lain = false;
            now_state = STATE_INIT;
            state_start_time = time;
            intent.putExtra(NOTIFY_STATE, now_state);
            sendBroadcast(intent);
        }

        return true;
    }

    public boolean reScan_device(){
        if(mScanning){
            return false;
        }
        else{
            scanLeDevice(true);
            return true;
        }
    }
}
