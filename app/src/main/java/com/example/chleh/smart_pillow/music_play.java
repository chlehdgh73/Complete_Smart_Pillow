package com.example.chleh.smart_pillow;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class music_play extends AppCompatActivity {
    private Button button1, button2;
    private ListView listview;
    private ListViewAdapter list_adapter;
    private MusicService music_service = null;
    private Context context = this;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            music_service = ((MusicService.LocalBinder) service).getService();
            list_adapter.modifyItem(music_service.get_allow_list());
            list_adapter.notifyDataSetChanged();
        }
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            music_service = null;
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_play);
        button1 = (Button)findViewById(R.id.music_button1);
        button2 = (Button)findViewById(R.id.music_button2);
        listview = (ListView)findViewById(R.id.music_listView);
        list_adapter = new ListViewAdapter();
        listview.setAdapter(list_adapter);

        Intent service = new Intent(this, MusicService.class);
        bindService(service, mServiceConnection, 0);

        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(music_service == null){
                    return;
                }
                final List<music_item> all_list = music_service.get_all_list();
                CharSequence[] items = new String[all_list.size()];
                for(int i = 0 ; i < all_list.size() ; i++){
                    items[i] = all_list.get(i).name + " - " + all_list.get(i).artist;
                }
                final List<music_item> selected_items = new ArrayList<>();
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder
                        .setTitle("음악 리스트")
                        .setCancelable(true)
                        .setMultiChoiceItems(items, null, new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                                if(isChecked == true){

                                    selected_items.add(all_list.get(which));
                                }
                                else{
                                    if(selected_items.contains(all_list.get(which))){
                                        selected_items.remove(all_list.get(which));
                                    }
                                }
                            }
                        })
                        .setPositiveButton("확인",new DialogInterface.OnClickListener(){
                            public void onClick(DialogInterface dialog, int button){
                                music_service.add_list(selected_items);
                                list_adapter.modifyItem(music_service.get_allow_list());
                                list_adapter.notifyDataSetChanged();
                                dialog.cancel();
                            }
                        })
                        .setNegativeButton("취소", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });

        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LayoutInflater dialog = LayoutInflater.from(context);
                final View dialogLayout = dialog.inflate(R.layout.music_setting_activity, null);
                final Dialog myDialog = new Dialog(context);
                myDialog.setTitle("설정");
                myDialog.setContentView(dialogLayout);
                myDialog.setCancelable(true);
                boolean pre_on_off_state = false;
                boolean pre_random_play_state = false;
                int pre_play_time = 0;
                if(music_service != null){
                    pre_on_off_state = music_service.get_on_off_state();
                    pre_random_play_state = music_service.get_random_play_state();
                    pre_play_time = music_service.get_play_time();
                    pre_play_time /= 60*1000;
                }

                final EditText editText = (EditText)dialogLayout.findViewById(R.id.music_editText);
                Switch random_switch = (Switch)dialogLayout.findViewById(R.id.music_switch1);
                Switch on_off_switch = (Switch)dialogLayout.findViewById(R.id.music_switch2);
                Button button_ok = (Button)dialogLayout.findViewById(R.id.music_button3);
                Button button_cancel = (Button)dialogLayout.findViewById(R.id.music_button4);
                editText.setText(""+pre_play_time);
                random_switch.setChecked(pre_random_play_state);
                on_off_switch.setChecked(pre_on_off_state);

                random_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if(music_service == null){
                            return;
                        }
                        if(isChecked == true){
                            music_service.set_random_play(true);
                        }
                        else{
                            music_service.set_random_play(false);
                        }
                    }
                });

                on_off_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if(music_service == null){
                            return;
                        }
                        if(isChecked == true){
                            music_service.turn_on();
                        }
                        else{
                            music_service.turn_off();
                        }
                    }
                });

                button_ok.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        int input = Integer.parseInt(editText.getText().toString());
                        if(music_service != null){
                            music_service.set_play_time(input * 60 * 1000);
                        }
                        myDialog.cancel();
                    }
                });

                button_cancel.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        myDialog.cancel();
                    }
                });
                myDialog.show();
            }
        });

        listview.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final music_item item = (music_item) parent.getItemAtPosition(position);
                if(music_service == null){
                    return true;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder
                        .setTitle("알림.")
                        .setMessage("해당 곡을 리스트에서 제외시키겠습니까?")
                        .setCancelable(true)
                        .setPositiveButton("확인",new DialogInterface.OnClickListener(){
                            public void onClick(DialogInterface dialog, int button){
                                music_service.remove_list(item);
                                list_adapter.modifyItem(music_service.get_allow_list());
                                list_adapter.notifyDataSetChanged();
                                dialog.cancel();
                            }
                        })
                        .setNegativeButton("취소", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
                AlertDialog dialog = builder.create();
                dialog.show();
                return true;
            }
        });
    }

    private class ListViewAdapter extends BaseAdapter {
        private List<music_item> list_item = new ArrayList<>();
        @Override
        public int getCount() {
            return list_item.size();
        }
        @Override
        public Object getItem(int i) {
            return list_item.get(i);
        }
        @Override
        public long getItemId(int i) {
            return i;
        }
        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            Context context = viewGroup.getContext();
            if (view == null) {
                LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(R.layout.music_list_view_item, viewGroup, false);
            }

            TextView text_name = (TextView)view.findViewById(R.id.music_textView1);
            TextView text_artist = (TextView)view.findViewById(R.id.music_textView2);

            music_item item = list_item.get(i);
            text_name.setText(item.name);
            text_artist.setText(item.artist);

            return view;
        }

        public void modifyItem(List<music_item> list){
            list_item.clear();
            for(music_item item : list){
                list_item.add(new music_item(item.id, item.name, item.artist));
            }
        }
    }
}
