package com.example.chleh.smart_pillow;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class music_stop_receiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent broad = new Intent(MusicService.RESERVE_STOP_MUSIC_ALRAM);
        context.sendBroadcast(broad);
    }
}
