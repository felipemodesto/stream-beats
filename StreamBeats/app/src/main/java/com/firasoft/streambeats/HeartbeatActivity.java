package com.firasoft.streambeats;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.support.wearable.activity.WearableActivity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.provider.Settings.Secure;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HeartbeatActivity extends WearableActivity implements SensorEventListener {

    private static final int ITERATIONS = 10000;
    private static final int KEY_LENGTH = 256;
    private TextView mheartRateText;
    private TextView mAccuracyText;
    //private Button btnStart;
    //private Button btnPause;
    //private Drawable imgStart;
    private SensorManager mSensorManager;
    private Sensor mHeartRateSensor;

    private String mDeviceID;
    private String mIP;
    private int accuracy = 0;
    private boolean connected = false;
    private int heartrate = 0;
    private String mSalt = "SaltySagansSuperSecretSauce";
    private byte[] hashKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_heartbeat);

        //Ensuring we request access to the body sensor permission as it's considered "dangerous" and is not given automatically (for some reason *sigh*)
        requestPermissions(new String[]{"android.permission.BODY_SENSORS"},6969);

        //final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        mheartRateText = (TextView) findViewById(R.id.heart);
        mAccuracyText = (TextView) findViewById(R.id.accuracy);
        TextView ipText = (TextView) findViewById(R.id.ipAddress);
        TextView uidText = (TextView) findViewById(R.id.uid);

        //Getting phone properties
        mDeviceID = Secure.getString(this.getContentResolver(),Secure.ANDROID_ID);
        mIP = Network.getIPAddress(true);
        ipText.setText("IP: "+ mIP);
        uidText.setText("modesto.io/heart/" + mDeviceID);

        //Activating Sensor
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mHeartRateSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);

        //Hashing UID to enable communication
        hashKey = hash(mDeviceID.toCharArray(),mSalt.getBytes());
        Log.d("HeartRate","My Hash is: " + hashKey);

        setAmbientEnabled();
        resumeMeasuring();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        startServer();
    }

    protected void startServer() {
        Network.connectToServer(mIP, hashKey.toString(),mDeviceID);
    }

    protected void pauseMeasuring() {
        //btnPause.setVisibility(ImageButton.GONE);
        //btnStart.setVisibility(ImageButton.VISIBLE);
        mheartRateText.setText("Paused");
        mAccuracyText.setText("Accuracy: Paused");
        mAccuracyText.setTextColor(Color.BLACK);
        mheartRateText.setTextColor(Color.BLACK);
        accuracy = 0;
        connected = false;
        heartrate = 0;
        stopMeasure();
    }

    protected void resumeMeasuring() {
        //btnStart.setVisibility(ImageButton.GONE);
        //btnPause.setVisibility(ImageButton.VISIBLE);
        mheartRateText.setText("Warming Up & Calibrating");
        mAccuracyText.setText("Accuracy: Unknown");
        mAccuracyText.setTextColor(Color.WHITE);
        mheartRateText.setTextColor(Color.WHITE);
        startMeasure();
    }

    private void startMeasure() {
        if (mHeartRateSensor != null) {
            boolean sensorRegistered = mSensorManager.registerListener(this, mHeartRateSensor, SensorManager.SENSOR_DELAY_FASTEST);
            Log.d("HeartRate", "Sensor Status: Sensor registered? " + (sensorRegistered ? "yes" : "no"));

            if (!sensorRegistered) {
                Log.d("HeartRate", "Is it Powered? " + mHeartRateSensor.getPower());
            }
        } else {
            Log.d("HeartRate", "Warning: Sensor is not set!");
        }
    }

    private void stopMeasure() {
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float mHeartRateFloat = event.values[0];
        int mHeartRate = Math.round(mHeartRateFloat);
        mheartRateText.setText(Integer.toString(mHeartRate)+ " bpm");
        heartrate = mHeartRate;

        Log.d("HeartRate","Heart Rate Sensor updated with value: "+ mHeartRate);
        mheartRateText.setTextColor(Color.BLUE);
        if (mHeartRate > 65) {
            mheartRateText.setTextColor(Color.CYAN);
        }
        if (mHeartRate > 80) {
            mheartRateText.setTextColor(Color.GREEN);
        }
        if (mHeartRate > 95) {
            mheartRateText.setTextColor(Color.YELLOW);
        }
        if (mHeartRate > 115) {
            mheartRateText.setTextColor(Color.parseColor("#ce611c"));   //Orange
        }
        if (mHeartRate > 130) {
            mheartRateText.setTextColor(Color.RED);
        }
        if (mHeartRate > 150) {
            mheartRateText.setTextColor(Color.parseColor("#6f1cce"));   //Purple
        }
        Network.sendToServer(heartrate,accuracy,mIP,hashKey.toString(),mDeviceID);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        String sensorAcc = "Unknown";
        switch(accuracy) {
            case SensorManager.SENSOR_STATUS_ACCURACY_HIGH:
                sensorAcc = "High";
                mAccuracyText.setTextColor(Color.GREEN);
                break;
            case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM:
                sensorAcc = "Medium";
                mAccuracyText.setTextColor(Color.YELLOW);
                break;
            case SensorManager.SENSOR_STATUS_ACCURACY_LOW:
                sensorAcc = "Low";
                mAccuracyText.setTextColor(Color.RED);
                break;

        }
        this.accuracy = accuracy;
        Log.d("HeartRate", "onAccuracyChanged - accuracy: " + sensorAcc);
        mAccuracyText.setText("Acc: " + sensorAcc);
        Network.sendToServer(heartrate,accuracy,mIP,hashKey.toString(),mDeviceID);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumeMeasuring();
    }

    @Override
    protected void onPause() {
        super.onPause();
        pauseMeasuring();
    }


    /**
     * Returns a salted and hashed password using the provided hash.<br>
     * Note - side effect: the password is destroyed (the char[] is filled with zeros)
     *
     * @param password the password to be hashed
     * @param salt     a 16 bytes salt, ideally obtained with the getNextSalt method
     *
     * @return the hashed password with a pinch of salt
     */
    public byte[] hash(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);
        Arrays.fill(password, Character.MIN_VALUE);
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            return skf.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new AssertionError("Error while hashing a password: " + e.getMessage(), e);
        } finally {
            spec.clearPassword();
        }
    }
}
