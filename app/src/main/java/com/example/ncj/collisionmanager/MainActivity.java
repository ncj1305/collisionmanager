package com.example.ncj.collisionmanager;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

public class MainActivity extends AppCompatActivity implements SensorEventListener{

    private SensorManager mSensorManager;
    private Sensor mProximity;
    private RelativeLayout mMainLayout;
    private TextView mStatusTextView;
    private Context mContext;

    private final String UPDATE_DB_URL = "http://ec2-52-78-198-17.ap-northeast-2.compute.amazonaws.com/updatecollisiondb.php";
    private final String GCM_URL = "http://ec2-52-78-198-17.ap-northeast-2.compute.amazonaws.com/sendgcm.php";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        mMainLayout = (RelativeLayout) findViewById(R.id.content_main);
        mStatusTextView = (TextView) findViewById(R.id.statusTextView);
        setSupportActionBar(toolbar);
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                initView();
            }
        });

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mProximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mContext = getApplicationContext();
        mSensorManager.registerListener(this, mProximity, SensorManager.SENSOR_DELAY_NORMAL);
        initView();
    }

    private void initView() {
        mStatusTextView.setTextColor(Color.BLACK);
        mStatusTextView.setText(R.string.normal_operation_status);
        mMainLayout.setBackgroundColor(Color.WHITE);
    }

    @Override
    protected void onPause() {
        // Be sure to unregister the sensor when the activity pauses.
        super.onPause();
        mSensorManager.unregisterListener(this);
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float distance = event.values[0];
        // Do something with this sensor data.
//        String s = ""+distance;
//        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
        if (distance == 0.0) {
            mStatusTextView.setTextColor(Color.WHITE);
            mStatusTextView.setText(R.string.emergency_operation_status);
            mMainLayout.setBackgroundColor(Color.RED);

            showLocation();
            String phonenum = getPhoneNumber();
            double latitude = getLatitude();
            double longitude = getLongitude();

            sendGCM(phonenum, latitude, longitude);
            updateDB(phonenum, latitude, longitude);
            sendSMS("01090688021", "차량 사고가 발생하였습니다.");
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void showLocation() {
        GpsInfo gps = new GpsInfo(this);
        // GPS 사용유무 가져오기
        if (gps.isGetLocation()) {

            double latitude = gps.getLatitude();
            double longitude = gps.getLongitude();

            Toast.makeText(
                    getApplicationContext(),
                    "당신의 위치 - \n위도: " + latitude + "\n경도: " + longitude,
                    Toast.LENGTH_LONG).show();
        } else {
            // GPS 를 사용할수 없으므로
            gps.showSettingsAlert();
        }
    }

    public String getPhoneNumber() {
        TelephonyManager telManager = (TelephonyManager)mContext.getSystemService(mContext.TELEPHONY_SERVICE);
        String phoneNum = telManager.getLine1Number();
        if (phoneNum == null) {
            phoneNum = "UNKNOWN";
        }
        Log.d("ncj", phoneNum);
        return phoneNum;
    }

    public double getLatitude() {
        GpsInfo gps = new GpsInfo(this);
        // GPS 사용유무 가져오기
        double latitude;
        if (gps.isGetLocation()) {
            latitude = gps.getLatitude();
            //double longitude = gps.getLongitude();
        } else {
            // GPS 를 사용할수 없으므로
            latitude = 0;
        }
        return latitude;
    }

    public double getLongitude() {
        GpsInfo gps = new GpsInfo(this);
        // GPS 사용유무 가져오기
        double longitude;
        if (gps.isGetLocation()) {
            longitude = gps.getLongitude();
            //double longitude = gps.getLongitude();
        } else {
            // GPS 를 사용할수 없으므로
            longitude = 0;
        }
        return longitude;
    }

    private void updateDB(String phoneNum, double latitude, double longitude){

        class InsertData extends AsyncTask<String, Void, String> {
            ProgressDialog loading;



            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                loading = ProgressDialog.show(MainActivity.this, "Please Wait", null, true, true);
            }

            @Override
            protected void onPostExecute(String s) {
                super.onPostExecute(s);
                loading.dismiss();
                Toast.makeText(getApplicationContext(),s,Toast.LENGTH_LONG).show();
            }

            @Override
            protected String doInBackground(String... params) {

                try{
                    String phoneNum = (String)params[0];
                    String latitude = (String)params[1];
                    String longitude = (String)params[2];

                    String data  = URLEncoder.encode("phonenum", "UTF-8") + "=" + URLEncoder.encode(phoneNum, "UTF-8");
                    data += "&" + URLEncoder.encode("latitude", "UTF-8") + "=" + URLEncoder.encode(latitude, "UTF-8");
                    data += "&" + URLEncoder.encode("longitude", "UTF-8") + "=" + URLEncoder.encode(longitude, "UTF-8");

                    URL url = new URL(UPDATE_DB_URL);
                    URLConnection conn = url.openConnection();

                    conn.setDoOutput(true);
                    OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());

                    wr.write( data );
                    wr.flush();

                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));

                    StringBuilder sb = new StringBuilder();
                    String line = null;

                    // Read Server Response
                    while((line = reader.readLine()) != null)
                    {
                        sb.append(line);
                        break;
                    }
                    return sb.toString();
                }
                catch(Exception e){
                    return new String("Exception: " + e.getMessage());
                }

            }
        }

        InsertData task = new InsertData();
        task.execute(phoneNum, latitude+"", longitude+"");
    }

    public void sendGCM (String phoneNum, double latitude, double longitude) {

        class InsertData extends AsyncTask<String, Void, String> {
            ProgressDialog loading;



            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                loading = ProgressDialog.show(MainActivity.this, "Please Wait", null, true, true);
            }

            @Override
            protected void onPostExecute(String s) {
                super.onPostExecute(s);
                loading.dismiss();
            }

            @Override
            protected String doInBackground(String... params) {

                try{
                    String phoneNum = (String)params[0];
                    String latitude = (String)params[1];
                    String longitude = (String)params[2];

                    String data  = URLEncoder.encode("phonenum", "UTF-8") + "=" + URLEncoder.encode(phoneNum, "UTF-8");
                    data += "&" + URLEncoder.encode("latitude", "UTF-8") + "=" + URLEncoder.encode(latitude, "UTF-8");
                    data += "&" + URLEncoder.encode("longitude", "UTF-8") + "=" + URLEncoder.encode(longitude, "UTF-8");

                    URL url = new URL(GCM_URL);
                    URLConnection conn = url.openConnection();

                    conn.setDoOutput(true);
                    OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());

                    wr.write( data );
                    wr.flush();

                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));

                    StringBuilder sb = new StringBuilder();
                    String line = null;

                    // Read Server Response
                    while((line = reader.readLine()) != null)
                    {
                        sb.append(line);
                        break;
                    }
                    Log.d("ncj", sb.toString());
                    return sb.toString();
                }
                catch(Exception e){
                    return new String("Exception: " + e.getMessage());
                }

            }
        }

        InsertData task = new InsertData();
        task.execute(phoneNum, latitude+"", longitude+"");
    }

    public void sendSMS(String smsNumber, String smsText){
        PendingIntent sentIntent = PendingIntent.getBroadcast(this, 0, new Intent("SMS_SENT_ACTION"), 0);
        PendingIntent deliveredIntent = PendingIntent.getBroadcast(this, 0, new Intent("SMS_DELIVERED_ACTION"), 0);

        SmsManager mSmsManager = SmsManager.getDefault();
        mSmsManager.sendTextMessage(smsNumber, null, smsText, sentIntent, deliveredIntent);
    }
}
