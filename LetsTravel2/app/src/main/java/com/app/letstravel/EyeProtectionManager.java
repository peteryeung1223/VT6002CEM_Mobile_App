package com.app.letstravel;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.view.View;

public class EyeProtectionManager {

    private static EyeProtectionManager instance;
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private SensorEventListener lightListener;
    private Handler handler = new Handler();
    private Runnable sensorCheckRunnable;
    private Activity currentActivity;

    private static final int CHECK_INTERVAL_MS = 1000;

    public static EyeProtectionManager getInstance() {
        if (instance == null) {
            instance = new EyeProtectionManager();
        }
        return instance;
    }

    public void start(Activity activity) {
        this.currentActivity = activity;

        SharedPreferences prefs = activity.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("eyeProtection", false)) return;

        sensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        if (lightSensor == null) return;

        lightListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                float lux = event.values[0];
                View rootView = activity.getWindow().getDecorView();
                if (lux < 20) {
                    rootView.setBackgroundColor(0xFFFFF2CC);
                    prefs.edit().putBoolean("eyeProtectionColorYellow", true).apply();
                } else {
                    rootView.setBackgroundColor(0xFFFFFFFF);
                    prefs.edit().putBoolean("eyeProtectionColorYellow", false).apply();
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };

        sensorCheckRunnable = new Runnable() {
            @Override
            public void run() {
                sensorManager.registerListener(lightListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
                handler.postDelayed(() -> sensorManager.unregisterListener(lightListener), 1000);
                handler.postDelayed(this, CHECK_INTERVAL_MS);
            }
        };

        handler.post(sensorCheckRunnable);
    }

    public void stop() {
        if (sensorManager != null && lightListener != null) {
            sensorManager.unregisterListener(lightListener);
        }
        handler.removeCallbacksAndMessages(null);
        currentActivity = null; // Optional: avoid memory leaks
    }

    public void resetBackground(Activity activity) {
        View rootView = activity.getWindow().getDecorView();
        rootView.setBackgroundColor(0xFFFFFFFF); // White background
    }

    public void detectOnceAndStart(final Activity activity) {
        this.currentActivity = activity;

        SharedPreferences prefs = activity.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("eyeProtection", false)) return;

        sensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        if (lightSensor == null) return;

        SensorEventListener oneTimeListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                float lux = event.values[0];
                View rootView = activity.getWindow().getDecorView();
                if (lux < 20) {
                    rootView.setBackgroundColor(0xFFFFF2CC); // yellow
                    prefs.edit().putBoolean("eyeProtectionColorYellow", true).apply();
                } else {
                    rootView.setBackgroundColor(0xFFFFFFFF); // white
                    prefs.edit().putBoolean("eyeProtectionColorYellow", false).apply();
                }

                sensorManager.unregisterListener(this); // Stop after one read
                start(activity); // Start 5s polling
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };

        sensorManager.registerListener(oneTimeListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

}
