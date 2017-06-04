package com.example.chleh.smart_pillow;

import android.app.AlertDialog;
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
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class Call_control extends AppCompatActivity {

    private Button button1;
    private Switch switch1;
    private ListView listview;
    private ListViewAdapter list_adapter;
    private CCService cc_service = null;
    private Context context = this;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            cc_service = ((CCService.LocalBinder) service).getService();
            switch1.setChecked(cc_service.get_on_off_state());
            list_adapter.modifyItem(cc_service.get_allow_list());
            list_adapter.notifyDataSetChanged();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            cc_service = null;
        }
    };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call_control);
        button1 = (Button)findViewById(R.id.cc_button1);
        switch1 = (Switch)findViewById(R.id.cc_switch1);
        listview = (ListView)findViewById(R.id.cc_listView);
        list_adapter = new ListViewAdapter();
        listview.setAdapter(list_adapter);
        Intent service = new Intent(this, CCService.class);
        bindService(service, mServiceConnection, 0);

        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(cc_service == null){
                    return;
                }
                final List<Phone_number> all_list = cc_service.get_all_list();
                CharSequence[] items = new String[all_list.size()];
                for(int i = 0 ; i < all_list.size() ; i++){
                    items[i] = all_list.get(i).name + "\n" + all_list.get(i).number;
                }

                final List<Phone_number> selected_items = new ArrayList<Phone_number>();
                Phone_number temp;
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder
                        .setTitle("전화번호 리스트")
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
                                cc_service.add_list(selected_items);
                                list_adapter.modifyItem(cc_service.get_allow_list());
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

        switch1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(cc_service == null){
                    return;
                }
                if(isChecked == true){
                    cc_service.turn_on();
                }
                else{
                    cc_service.turn_off();
                }
            }
        });

        listview.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final Phone_number item = (Phone_number)parent.getItemAtPosition(position);
                if(cc_service == null){
                    return true;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder
                        .setTitle("알림.")
                        .setMessage("해당 번호를 예외 리스트에서 제외시키겠습니까?")
                        .setCancelable(true)
                        .setPositiveButton("확인",new DialogInterface.OnClickListener(){
                            public void onClick(DialogInterface dialog, int button){
                                cc_service.remove_list(item);
                                list_adapter.modifyItem(cc_service.get_allow_list());
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

    private class ListViewAdapter extends BaseAdapter{
        private List<Phone_number> list_item = new ArrayList<>();

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
                view = inflater.inflate(R.layout.cc_list_view_item, viewGroup, false);
            }

            TextView text_name = (TextView)view.findViewById(R.id.cc_textView1);
            TextView text_number = (TextView)view.findViewById(R.id.cc_textView2);

            Phone_number item = list_item.get(i);

            text_name.setText(item.name);
            text_number.setText(item.number);

            return view;
        }

        public void modifyItem(List<Phone_number> list){
            list_item.clear();
            for(Phone_number item : list){
                list_item.add(new Phone_number((item.name), item.number));
            }
        }
    }
}
