package com.example.chleh.smart_pillow;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.ITelephony;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

class Phone_number{
    String name;
    String number;

    public Phone_number(){
        name = "";
        number = "";
    }
    public Phone_number(String name, String number){
        if(name == null){
            this.name = "";
        }
        else {
            this.name = name;
        }
        if(number == null) {
            this.number = "";
        }
        else{
            this.number = number;
        }
    }

    public boolean isEqual(Phone_number target){
        if(this.name.equals(target.name)){
            if(this.number.equals(target.number)){
                return true;
            }
        }
        return false;
    }
}

class sorting implements Comparator<Phone_number>{
    @Override
    public int compare(Phone_number target1, Phone_number target2){
        return target1.name.compareTo(target2.name);
    }
}

public class CCService extends Service {
    private final static String SAVED_LIST_FILE_NAME = "cc_allow_list.txt";
    private final static String SAVED_SATE_FILE_NAME = "cc_sate.txt";
    private List<Phone_number> allow_list = new ArrayList<Phone_number>();
    private boolean on_off_state;
    private String call_state = "unknown";
    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        CCService getService() {
            return CCService.this;
        }
    }
    private BLEService bluetooth_service = null;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            bluetooth_service = ((BLEService.LocalBinder) service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bluetooth_service = null;
        }
    };

    @Override
    public void onCreate(){
        allow_list.clear();
        FileInputStream file_in = null;
        BufferedReader buffer = null;
        Phone_number temp;
        String name, number;
        try {
            file_in = openFileInput(SAVED_LIST_FILE_NAME);
            buffer = new BufferedReader(new InputStreamReader(file_in));

            while((name = buffer.readLine()) != null){
                number = buffer.readLine();
                temp = new Phone_number(name, number);
                allow_list.add(temp);
            }
        }
        catch (FileNotFoundException e) {}
        catch (IOException e1){}
        finally {
            try {
                if(buffer != null){
                    buffer.close();
                    buffer = null;
                }
                if(file_in != null){
                    file_in.close();
                    file_in = null;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        on_off_state = false;
        try {
            file_in = openFileInput(SAVED_SATE_FILE_NAME);
            buffer = new BufferedReader(new InputStreamReader(file_in));
            if(Integer.parseInt(buffer.readLine()) != 0){
                on_off_state = true;
            }
            else{
                on_off_state = false;
            }
        }
        catch (FileNotFoundException e) {}
        catch (IOException e1){}
        finally {
            try {
                if(buffer != null) buffer.close();
                if(file_in != null) file_in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Intent service = new Intent(this, BLEService.class);
        bindService(service, mServiceConnection, 0);
        IntentFilter filter = new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        registerReceiver(mReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startid) {
        return super.onStartCommand(intent,flags,startid);
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
        unregisterReceiver(mReceiver);
    }

    BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(on_off_state == false){
                return;
            }
            if(bluetooth_service == null){
                return;
            }
            int lain_state = bluetooth_service.query_state();
            switch(lain_state){
                case BLEService.STATE_INIT:
                case BLEService.STATE_LAIN:
                case BLEService.STATE_TEMP_AWAKE:
                case BLEService.STATE_COMPLETE_AWAKE:
                    return;
            }
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            if(state.equals(call_state)){
                return;
            }
            else{
                call_state = state;
            }
            if(TelephonyManager.EXTRA_STATE_RINGING.equals(state)){
                String number = PhoneNumberUtils.formatNumber(intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER), Locale.getDefault().getCountry());
                boolean isEqual = false;
                for(Phone_number list : allow_list) {
                    if (list.number.equals(number)) {
                        isEqual = true;
                        break;
                    }
                }

                if(isEqual == false) {
                    try {
                        TelephonyManager tel_manager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                        ITelephony telephony;
                        Class tel_class = Class.forName(tel_manager.getClass().getName());
                        Method method = tel_class.getDeclaredMethod("getITelephony");
                        method.setAccessible(true);
                        telephony = (ITelephony) method.invoke(tel_manager);
                        telephony.endCall();
                    } catch (Exception e) {
                    }
                }
            }
        }
    };

    public void turn_on(){
        on_off_state = true;
        on_off_state_file_write();
    }

    public void turn_off(){
        on_off_state = false;
        call_state = "unknown";
        on_off_state_file_write();
    }

    public boolean get_on_off_state(){
        return on_off_state;
    }

    public List<Phone_number> get_all_list(){
        Phone_number temp;
        List<Phone_number> phone_list = new ArrayList<>();
        Uri query_uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] selection = new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER};
        String[] selectionArgs = null;
        String sortOrder = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC";
        Cursor contactCursor = this.getContentResolver().query(query_uri, selection, null, selectionArgs, sortOrder);

        if(contactCursor.moveToFirst()){
            do{
                String name = contactCursor.getString(0);
                String number = PhoneNumberUtils.formatNumber(contactCursor.getString(1), Locale.getDefault().getCountry());
                temp = new Phone_number(name, number);
                phone_list.add(temp);
            }
            while(contactCursor.moveToNext());
        }
        return phone_list;
    }

    public List<Phone_number> get_allow_list(){
        return allow_list;
    }

    public void add_list(List<Phone_number> item){
        boolean is_equal;
        for(Phone_number list : item){
            is_equal = false;
            for(Phone_number comp : allow_list){
                if(list.isEqual(comp) == true){
                    is_equal = true;
                    break;
                }
            }
            if(is_equal == false){
                allow_list.add(list);
            }
        }
        Collections.sort(allow_list, new sorting());
        allow_list_file_write();
    }

    public void remove_list(List<Phone_number> items){
        int index;
        for(Phone_number list : items){
            index = 0;
            for(Phone_number comp : allow_list){
                if(list.isEqual(comp) == true){
                    allow_list.remove(index);
                    break;
                }
                index++;
            }
        }
        allow_list_file_write();
    }

    public void remove_list(Phone_number item){
        int index;
        index = 0;
        for(Phone_number comp : allow_list){
            if(item.isEqual(comp) == true){
                allow_list.remove(index);
                break;
            }
            index++;
        }
        allow_list_file_write();
    }

    private void on_off_state_file_write(){
        FileOutputStream file_out = null;
        PrintWriter writer = null;
        try {
            file_out = openFileOutput(SAVED_SATE_FILE_NAME,Context.MODE_PRIVATE);
            writer = new PrintWriter(file_out);
            if(on_off_state == true){
                writer.println(1);
            }
            else{
                writer.println(0);
            }
        } catch (FileNotFoundException e) {
        }
        catch (IOException e1){
        }
        finally{
            try {
                if(writer != null) writer.close();
                if(file_out != null) file_out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void allow_list_file_write(){
        FileOutputStream file_out = null;
        PrintWriter writer = null;
        try {
            file_out = openFileOutput(SAVED_LIST_FILE_NAME,Context.MODE_PRIVATE);
            writer = new PrintWriter(file_out);
            for(Phone_number list : allow_list){
                writer.println(list.name);
                writer.println(list.number);
            }
        } catch (FileNotFoundException e) {
        }
        catch (IOException e1){
        }
        finally{
            try {
                if(writer != null) writer.close();
                if(file_out != null) file_out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}