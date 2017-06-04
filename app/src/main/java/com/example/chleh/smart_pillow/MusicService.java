package com.example.chleh.smart_pillow;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.provider.MediaStore;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

class music_item{
    String id;
    String name;
    String artist;

    public music_item(){
        id = "";
        name = "";
        artist = "";
    }
    public music_item(String id, String name, String artist){
        if(id == null){
            this.id = "";
        }
        else{
            this.id = id;
        }
        if(name == null){
            this.name = "";
        }
        else {
            this.name = name;
        }
        if(artist == null) {
            this.artist = "";
        }
        else{
            this.artist = artist;
        }
    }

    public boolean isEqual(music_item target){
        if(this.id.equals(target.id)){
            return true;
        }
        return false;
    }
}

class music_sorting implements Comparator<music_item> {
    @Override
    public int compare(music_item target1, music_item target2){
        return target1.name.compareTo(target2.name);
    }
}

public class MusicService extends Service {
    public final static String RESERVE_STOP_MUSIC_ALRAM = "reserve_event_triger";
    List<music_item> music_list = new ArrayList<>();
    private final static String SAVED_LIST_FILE_NAME = "music_list.txt";
    private final static String SAVED_STAET_FILE_NAME = "music_state.txt";
    private boolean on_off_state = false;
    private boolean random_play = false;
    private int play_time = 60 * 1000;
    private int last_play = -1;
    private MediaPlayer mediaPlayer = new MediaPlayer();
    private boolean is_stop_reserved = false;
    private int stop_alram_id = 123456789;
    int pre_lain_state = BLEService.STATE_LAIN;
    private BLEService bluetooth_service = null;
    private Alram_Service alram_service = null;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            bluetooth_service = ((BLEService.LocalBinder) service).getService();
            pre_lain_state = bluetooth_service.query_state();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bluetooth_service = null;
        }
    };

    private final ServiceConnection mServiceConnection2 = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            alram_service = ((Alram_Service.LocalBinder) service).getService();
        }
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            alram_service = null;
        }
    };
    private final IBinder mBinder = new LocalBinder();
    public class LocalBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate(){
        music_list.clear();
        FileInputStream file_in = null;
        BufferedReader buffer = null;
        music_item temp;
        String id, name, artist;
        try {
            file_in = openFileInput(SAVED_LIST_FILE_NAME);
            buffer = new BufferedReader(new InputStreamReader(file_in));

            while((id = buffer.readLine()) != null){
                name = buffer.readLine();
                artist = buffer.readLine();
                temp = new music_item(id, name, artist);
                music_list.add(temp);
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
            file_in = openFileInput(SAVED_STAET_FILE_NAME);
            buffer = new BufferedReader(new InputStreamReader(file_in));
            if(Integer.parseInt(buffer.readLine()) != 0){
                on_off_state = true;
            }
            else{
                on_off_state = false;
            }
            if(Integer.parseInt(buffer.readLine()) != 0){
                random_play = true;
            }
            else{
                random_play = false;
            }
            play_time = Integer.parseInt(buffer.readLine());
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
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){
            @Override
            public void onCompletion(MediaPlayer mp) {
                start_music();
            }
        });
        Intent service = new Intent(this, BLEService.class);
        bindService(service, mServiceConnection, 0);
        service = new Intent(this, Alram_Service.class);
        bindService(service, mServiceConnection2, 0);
        IntentFilter filter = new IntentFilter(BLEService.STATE_CHANGE_NOTIFY);
        IntentFilter filter2 = new IntentFilter(RESERVE_STOP_MUSIC_ALRAM);
        registerReceiver(mReceiver, filter);
        registerReceiver(mReceiver, filter2);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startid){
        return super.onStartCommand(intent, flags, startid);
    }

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
        mediaPlayer.release();
        mediaPlayer = null;
    }

    BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(on_off_state == false){
                return;
            }
            if(bluetooth_service == null) {
                return;
            }

            String action = intent.getAction();
            int next_state;

            if(action.equals(BLEService.STATE_CHANGE_NOTIFY)){
                next_state = intent.getIntExtra(BLEService.NOTIFY_STATE, 1);
                if(pre_lain_state == BLEService.STATE_INIT && next_state == BLEService.STATE_LAIN){

                    if(alram_service == null){
                        return;
                    }
                    if(alram_service.get_alram_state() == false){
                        return;
                    }
                    start_music();
                    reserve_stop_music();
                }
                else {
                    if(bluetooth_service.query_lain_state() == false) {
                        stop_music();
                    }
                }
                pre_lain_state = next_state;
            }
            else if(action.equals(MusicService.RESERVE_STOP_MUSIC_ALRAM)){
                is_stop_reserved = false;
                stop_music();
            }
        }
    };

    private void start_music(){
        Random random = new Random();
        String music_id;
        Uri musicURI;
        if(music_list.size() == 0){
            return;
        }
        if(random_play == true){
            last_play = random.nextInt(music_list.size());
        }
        else{
            last_play = (last_play + 1) % music_list.size();
        }
        try {
            music_id = music_list.get(last_play).id;
            musicURI = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "" + music_id);
            mediaPlayer.reset();
            mediaPlayer.setDataSource(this, musicURI);
            mediaPlayer.prepare();
            mediaPlayer.start();
        }
        catch (Exception e){}
    }

    private void stop_music(){
        if(is_stop_reserved == true){
            Intent alram = new Intent(this, music_stop_receiver.class);
            PendingIntent sender = PendingIntent.getBroadcast(this, stop_alram_id, alram, 0);
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            am.cancel(sender);
            is_stop_reserved = false;
        }
        if(mediaPlayer.isPlaying() == true){
            mediaPlayer.stop();
        }
    }

    private void reserve_stop_music(){
        Intent alram = new Intent(this, music_stop_receiver.class);
        PendingIntent sender = PendingIntent.getBroadcast(this, stop_alram_id, alram, 0);
        long timer = System.currentTimeMillis() + play_time;
        AlarmManager am = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,timer,sender);
    }

    public void turn_on(){
        on_off_state = true;
        state_file_write();
    }

    public void turn_off(){
        on_off_state = false;
        state_file_write();
    }

    public boolean get_on_off_state(){
        return on_off_state;
    }

    public boolean get_random_play_state(){
        return random_play;
    }

    public int get_play_time(){
        return play_time;
    }

    public List<music_item> get_all_list(){
        music_item temp;
        List<music_item> all_music_list = new ArrayList<>();
        Uri query_uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] selection = new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST};
        String sortOrder = MediaStore.Audio.Media.TITLE + " COLLATE LOCALIZED ASC";

        Cursor musicCursor = this.getContentResolver().query(query_uri, selection, null, null, sortOrder);

        if(musicCursor.moveToFirst()){
            do{
                String id = musicCursor.getString(0);
                String name = musicCursor.getString(1);
                String artist = musicCursor.getString(2);
                temp = new music_item(id, name, artist);
                all_music_list.add(temp);
            }
            while(musicCursor.moveToNext());
        }
        return all_music_list;
    }

    public List<music_item> get_allow_list(){
        return music_list;
    }

    public void add_list(List<music_item> item){
        boolean is_equal;
        for(music_item list : item){
            is_equal = false;
            for(music_item comp : music_list){
                if(list.isEqual(comp) == true){
                    is_equal = true;
                    break;
                }
            }
            if(is_equal == false){
                music_list.add(list);
            }
        }
        last_play = -1;
        Collections.sort(music_list, new music_sorting());
        music_list_file_write();
    }

    public void remove_list(List<music_item> items){
        int index;
        for(music_item list : items){
            index = 0;
            for(music_item comp : music_list){
                if(list.isEqual(comp) == true){
                    music_list.remove(index);
                    break;
                }
                index++;
            }
        }
        last_play = -1;
        music_list_file_write();
    }

    public void remove_list(music_item item){
        int index;
        index = 0;
        for(music_item comp : music_list){
            if(item.isEqual(comp) == true){
                music_list.remove(index);
                break;
            }
            index++;
        }
        last_play = -1;
        music_list_file_write();
    }

    public void set_random_play(boolean state){
        random_play = state;
        state_file_write();
    }

    public void set_play_time(int time){
        if(time < 0){
            return;
        }
        if(time > (100 * 60 * 1000)){
            play_time = 100 * 60 * 1000;
            return;
        }
        play_time = time;
        state_file_write();
    }

    private void state_file_write(){
        FileOutputStream file_out = null;
        PrintWriter writer = null;

        try {
            file_out = openFileOutput(SAVED_STAET_FILE_NAME,Context.MODE_PRIVATE);
            writer = new PrintWriter(file_out);
            if(on_off_state == true){
                writer.println(1);
            }
            else{
                writer.println(0);
            }
            if(random_play == true){
                writer.println(1);
            }
            else{
                writer.println(0);
            }
            writer.println(play_time);
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

    private void music_list_file_write(){
        FileOutputStream file_out = null;
        PrintWriter writer = null;

        try {
            file_out = openFileOutput(SAVED_LIST_FILE_NAME,Context.MODE_PRIVATE);
            writer = new PrintWriter(file_out);

            for(music_item list : music_list){
                writer.println(list.id);
                writer.println(list.name);
                writer.println(list.artist);
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
