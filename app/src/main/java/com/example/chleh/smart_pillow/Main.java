package com.example.chleh.smart_pillow;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class Main extends AppCompatActivity {
    private static final String TAG = "AppPermission";
    private final int MY_PERMISSION_REQUEST_STORAGE = 100;
    Context context = this;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermission();
        Button button = (Button) findViewById(R.id.button6);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Intent i = new Intent(getApplicationContext(), Alram.class);
                startActivity(i);
            }
        });
        Button button2 = (Button) findViewById(R.id.button9);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Intent blue = new Intent(getApplicationContext(), Bluetooth.class);
                startActivity(blue);
            }
        });
        Button button3 = (Button) findViewById(R.id.button5);
        button3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Intent k = new Intent(getApplicationContext(), music_play.class);
                startActivity(k);
            }
        });
        Button button4 = (Button) findViewById(R.id.button7);
        button4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Intent j = new Intent(getApplicationContext(), AnalysisActivity.class);
                startActivity(j);
            }
        });
        Button button5 = (Button) findViewById(R.id.button4);
        button5.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Intent l = new Intent(getApplicationContext(), Call_control.class);
                startActivity(l);
            }
        });
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    private void checkPermission() {
        LocationManager locate = (LocationManager)getSystemService(LOCATION_SERVICE);
        if(!locate.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            alert_on_gps();
        }
        int permission_external_read = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        int permission_external_write = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        int permission_fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        int permission_coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
        int permission_read_contacts = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS);
        int permission_read_phone_state = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE);
        int permission_call_phone = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE);

        if(permission_external_read != PackageManager.PERMISSION_GRANTED || permission_external_write != PackageManager.PERMISSION_GRANTED
                || permission_fine != PackageManager.PERMISSION_GRANTED || permission_coarse != PackageManager.PERMISSION_GRANTED
                || permission_read_contacts != PackageManager.PERMISSION_GRANTED || permission_read_phone_state != PackageManager.PERMISSION_GRANTED
                || permission_call_phone != PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.CALL_PHONE}, MY_PERMISSION_REQUEST_STORAGE);
        }
        else{

            Intent service = new Intent(this, BLEService.class);
            startService(service);

            service = new Intent(this, Alram_Service.class);
            startService(service);

            service = new Intent(this, CCService.class);
            startService(service);

            service = new Intent(this,MusicService.class);
            startService(service);
        }
    }

    private void alert_on_gps(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder
                .setTitle("알림.")
                .setMessage("블루투스4.0을 사용하기 위한 GPS(위치)기능이 켜져있지 않습니다." +
                        "\n해당 기능이 켜져있지않으면 어플리케이션이 정상적으로 동작하지 않습니다.\n" +
                        "GPS기능을 켜주세요.")
                .setCancelable(false)
                .setPositiveButton("켜기",new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog, int button){
                        Intent gps_intent= new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivity(gps_intent);
                    }
                })
                .setNegativeButton("취소",new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog, int button){
                        dialog.cancel();
                    }
                });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSION_REQUEST_STORAGE:
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED
                        || grantResults[1] != PackageManager.PERMISSION_GRANTED
                        || grantResults[2] != PackageManager.PERMISSION_GRANTED
                        || grantResults[3] != PackageManager.PERMISSION_GRANTED
                        || grantResults[4] != PackageManager.PERMISSION_GRANTED
                        || grantResults[5] != PackageManager.PERMISSION_GRANTED) {


                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder
                            .setTitle("알림.")
                            .setMessage("어플리케이션에 필요한 권한을 얻지 못했습니다. \n설정->앱 에서 모든 권한을 켜주세요.")
                            .setCancelable(false)
                            .setPositiveButton("확인",new DialogInterface.OnClickListener(){
                                public void onClick(DialogInterface dialog, int button){
                                    dialog.cancel();
                                    Toast.makeText(context, "권한이 부족합니다.", Toast.LENGTH_SHORT).show();
                                    finish();
                                }
                            })
                            .setNegativeButton("취소",new DialogInterface.OnClickListener(){
                                public void onClick(DialogInterface dialog, int button){
                                    dialog.cancel();
                                    Toast.makeText(context, "권한이 부족합니다.", Toast.LENGTH_SHORT).show();
                                    finish();
                                }
                            });
                    AlertDialog dialog = builder.create();
                    dialog.show();

                } else {

                    Intent service = new Intent(this, BLEService.class);
                    startService(service);

                    service = new Intent(this, Alram_Service.class);
                    startService(service);

                    service = new Intent(this, CCService.class);
                    startService(service);

                    service = new Intent(this,MusicService.class);
                    startService(service);

                }
                break;
        }
    }
}