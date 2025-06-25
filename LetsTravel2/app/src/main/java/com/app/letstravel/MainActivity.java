package com.app.letstravel;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {

    private static final String BASE_URL = "http://peter.serveblog.net:3000/";
    private FirebaseAuth mAuth;
    private static final long SESSION_TIMEOUT = 10 * 60 * 1000;
    private long lastActiveTime;
    private long backPressedTime = 0;
    private Toast backToast;
    private boolean isExitingApp = false;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 2001;

    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<String> galleryLauncher;
    private LinearLayout imagePreviewContainer;

    FusedLocationProviderClient fusedLocationClient;
    private Handler locationHandler;
    private Runnable locationRunnable;

    private TextView locationText;
    private boolean isLocationLoadingToastShown = false;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private SensorEventListener orientationListener;
    private boolean isPortraitLockEnabled = false;
    private boolean hasShownLandscapeToast = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        if (prefs.getBoolean("portraitLock", false)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            isPortraitLockEnabled = true;
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        locationText = findViewById(R.id.textLocationName);
        locationText.setText("Detecting location...");

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        getLastLocation();

        mAuth = FirebaseAuth.getInstance();

        boolean isLoggedIn = getSharedPreferences("AuthPrefs", MODE_PRIVATE).getBoolean("isLoggedIn", false);
        if (!isLoggedIn || mAuth.getCurrentUser() == null) {
            navigateToLogin();
            return;
        }

        fetchAdminSecret();
        checkNotificationPermissionAndSubscribe();

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (imagePreviewContainer != null) {
                        NewTripDialog.handleCameraResult(this, null, result.getResultCode(), result.getData(), imagePreviewContainer);
                    }
                });

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (imagePreviewContainer != null) {
                        NewTripDialog.handleGalleryResult(this, null, uri, imagePreviewContainer);
                    }
                });

        Button btnProfile = findViewById(R.id.btnProfile);
        btnProfile.setOnClickListener(v -> navigateToProfile());

        lastActiveTime = System.currentTimeMillis();

        // Initialize handler for refreshing location and weather
        locationHandler = new Handler();
        locationRunnable = () -> {
            getLastLocation();
            locationHandler.postDelayed(locationRunnable, 5000);
        };

        // Initialize orientation detection
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        orientationListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                float y = event.values[1];

                if (isPortraitLockEnabled) {
                    if (Math.abs(y) < 2 && !hasShownLandscapeToast) {
                        Toast.makeText(MainActivity.this, "Portrait lock is enabled", Toast.LENGTH_SHORT).show();
                        hasShownLandscapeToast = true;
                    } else if (Math.abs(y) > 6) { // Reset only when back to portrait
                        hasShownLandscapeToast = false;
                    }
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) { }
        };

        if (isPortraitLockEnabled) {
            sensorManager.registerListener(orientationListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }

        FloatingActionButton btnNews = findViewById(R.id.btnNews);
        btnNews.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, TravelNewsActivity.class)));

        SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipeRefreshLayoutMain);

        swipeRefreshLayout.setOnRefreshListener(() -> {
            refreshMainPage();
            swipeRefreshLayout.setRefreshing(false);
        });
    }

    private void checkNotificationPermissionAndSubscribe() {
        SharedPreferences prefs = getSharedPreferences("AuthPrefs", MODE_PRIVATE);
        boolean isAdmin = prefs.getBoolean("isAdmin", false);

        if (isAdmin) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
            } else {
                subscribeToAdminTopic();
            }
        }
    }

    private void subscribeToAdminTopic() {
        FirebaseMessaging.getInstance().subscribeToTopic("admin_topic")
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d("MainActivity", "Subscribed to admin_topic (auto-check)");
                    } else {
                        Log.e("MainActivity", "Subscription failed (auto-check)");
                    }
                });
    }

    private void fetchAdminSecret() {
        ApiService apiService = RetrofitClient.getRetrofitInstance().create(ApiService.class);

        apiService.getAdminSecret().enqueue(new Callback<ApiService.AdminSecretResponse>() {
            @Override
            public void onResponse(Call<ApiService.AdminSecretResponse> call, Response<ApiService.AdminSecretResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String secret = response.body().getSecret();
                    saveAdminSecretLocally(secret);
                    Log.d(TAG, "Admin secret fetched and saved.");
                } else {
                    Toast.makeText(MainActivity.this, "Failed to fetch admin secret", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiService.AdminSecretResponse> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Error fetching admin secret: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveAdminSecretLocally(String secret) {
        getSharedPreferences("AdminPrefs", MODE_PRIVATE)
                .edit()
                .putString("adminSecret", secret)
                .apply();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        if (prefs.getBoolean("eyeProtection", false)) {
            boolean isYellow = prefs.getBoolean("eyeProtectionColorYellow", false);
            View rootView = getWindow().getDecorView();
            rootView.setBackgroundColor(isYellow ? 0xFFFFF2CC : 0xFFFFFFFF);
            EyeProtectionManager.getInstance().start(this);
        } else {
            EyeProtectionManager.getInstance().stop();
            EyeProtectionManager.getInstance().resetBackground(this);
        }

        if (prefs.getBoolean("portraitLock", false)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            isPortraitLockEnabled = true;
            sensorManager.registerListener(orientationListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            isPortraitLockEnabled = false;
            sensorManager.unregisterListener(orientationListener);
        }

        if (!isExitingApp && System.currentTimeMillis() - lastActiveTime > SESSION_TIMEOUT && isBiometricEnabled()) {
            checkBiometricLogin();
        }

        isExitingApp = false;

        loadTrips();
        checkAdminStatusAndUpdateUI();

        locationHandler.post(locationRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        locationHandler.removeCallbacks(locationRunnable);
        sensorManager.unregisterListener(orientationListener);
    }

    public void loadTrips() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        RecyclerView recyclerView = findViewById(R.id.recyclerViewTrips);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        db.collection("trips")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Trip> tripList = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Trip trip = doc.toObject(Trip.class);
                        trip.setId(doc.getId());
                        tripList.add(trip);
                    }
                    Collections.sort(tripList, (a, b) -> {
                        Date dateA = a.getCreatedAt();
                        Date dateB = b.getCreatedAt();
                        if (dateA == null && dateB == null) return 0;
                        if (dateA == null) return 1;
                        if (dateB == null) return -1;
                        return dateB.compareTo(dateA);
                    });
                    recyclerView.setAdapter(new TripAdapter(this, tripList));
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load trips", Toast.LENGTH_SHORT).show());
    }

    private void checkAdminStatusAndUpdateUI() {
        Button btnNewTrip = findViewById(R.id.btnNewTrip);
        btnNewTrip.setVisibility(View.GONE);

        SharedPreferences prefs = getSharedPreferences("AuthPrefs", MODE_PRIVATE);
        boolean isAdmin = prefs.getBoolean("isAdmin", false);
        String cachedPass = prefs.getString("adminPass", null);

        String realSecret = getSharedPreferences("AdminPrefs", MODE_PRIVATE)
                .getString("adminSecret", null);

        if (isAdmin && cachedPass != null && cachedPass.equals(realSecret)) {
            btnNewTrip.setVisibility(View.VISIBLE);
            btnNewTrip.setOnClickListener(v -> {
                View dialogView = getLayoutInflater().inflate(R.layout.dialog_new_trip, null);
                imagePreviewContainer = dialogView.findViewById(R.id.imagePreviewContainer);
                NewTripDialog.show(this, getLayoutInflater(), cameraLauncher, galleryLauncher);
            });
        }
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        if (backPressedTime + 4000 > System.currentTimeMillis()) {
            if (backToast != null) backToast.cancel();
            isExitingApp = true;
            finishAffinity();
        } else {
            backToast = Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT);
            backToast.show();
        }
        backPressedTime = System.currentTimeMillis();
    }

    private void navigateToLogin() {
        Intent loginIntent = new Intent(this, LoginActivity.class);
        loginIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(loginIntent);
        finish();
    }

    private void navigateToProfile() {
        Intent profileIntent = new Intent(this, ProfileActivity.class);
        startActivity(profileIntent);
    }

    private boolean isBiometricEnabled() {
        return getSharedPreferences("AuthPrefs", MODE_PRIVATE)
                .getBoolean("biometricEnabled", false);
    }

    private void checkBiometricLogin() {
        if (!isBiometricEnabled()) {
            navigateToLogin();
            return;
        }
        BiometricManager biometricManager = BiometricManager.from(this);
        switch (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                BiometricPrompt biometricPrompt = new BiometricPrompt(this,
                        ContextCompat.getMainExecutor(this),
                        new BiometricPrompt.AuthenticationCallback() {
                            @Override
                            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                                Toast.makeText(MainActivity.this, "Biometric login successful", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onAuthenticationFailed() {
                                Toast.makeText(MainActivity.this, "Biometric authentication failed", Toast.LENGTH_SHORT).show();
                                navigateToLogin();
                            }
                        });

                BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Biometric Login")
                        .setSubtitle("Log in using your biometric credential")
                        .setNegativeButtonText("Cancel")
                        .build();

                biometricPrompt.authenticate(promptInfo);
                break;
            default:
                navigateToLogin();
                break;
        }
    }

    private void getLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1001);
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        fetchWeatherApiKey(location.getLatitude(), location.getLongitude());
                        resolveLocationName(location.getLatitude(), location.getLongitude());
                        isLocationLoadingToastShown = false;
                    } else {
                        if (!isLocationLoadingToastShown) {
                            Toast.makeText(this, "Unable to get location", Toast.LENGTH_SHORT).show();
                            isLocationLoadingToastShown = true;
                        }
                    }
                });
    }

    private void fetchWeatherApiKey(double lat, double lon) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        ApiService apiService = retrofit.create(ApiService.class);
        Call<ApiKeyResponse> call = apiService.getWeatherApiKey();

        call.enqueue(new Callback<ApiKeyResponse>() {
            @Override
            public void onResponse(Call<ApiKeyResponse> call, Response<ApiKeyResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String apiKey = response.body().getApiKey();
                    fetchWeather(lat, lon, apiKey);
                } else {
                    Toast.makeText(MainActivity.this, "Failed to fetch API Key", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiKeyResponse> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchWeather(double lat, double lon, String apiKey) {
        new Thread(() -> {
            try {
                String urlStr = "https://api.openweathermap.org/data/2.5/weather?lat=" + lat + "&lon=" + lon + "&appid=" + apiKey + "&units=metric";

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) result.append(line);
                reader.close();

                JSONObject json = new JSONObject(result.toString());
                String iconCode = json.getJSONArray("weather").getJSONObject(0).getString("icon");

                runOnUiThread(() -> displayWeatherIcon(iconCode));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void displayWeatherIcon(String iconCode) {
        int iconRes = getResources().getIdentifier(
                "icon_" + iconCode, "drawable", getPackageName());
        ImageView weatherIcon = findViewById(R.id.weatherIcon);
        if (iconRes != 0) {
            weatherIcon.setImageResource(iconRes);
        } else {
            weatherIcon.setImageResource(
                    getResources().getIdentifier(
                            "icon_01d", "drawable", getPackageName()
                    )
            );
        }
    }

    private void resolveLocationName(double lat, double lon) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());

        try {
            List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                StringBuilder locationBuilder = new StringBuilder();

                String subLocality = address.getSubLocality();
                String thoroughfare = address.getThoroughfare();
                String locality = address.getLocality();
                String adminArea = address.getAdminArea();
                String country = address.getCountryName();

                if (subLocality != null) locationBuilder.append(subLocality).append(", ");
                if (thoroughfare != null) locationBuilder.append(thoroughfare).append(", ");
                if (locality != null) locationBuilder.append(locality).append(", ");
                if (adminArea != null) locationBuilder.append(adminArea).append(", ");
                if (country != null) locationBuilder.append(country);

                String fullLocation = locationBuilder.toString().replaceAll(", $", "");

                locationText.setText(fullLocation);
            } else {
                locationText.setText("Unknown Location\nLat: " + lat + "\nLon: " + lon);
            }
        } catch (IOException e) {
            e.printStackTrace();
            locationText.setText("Geocoder failed\nLat: " + lat + "\nLon: " + lon);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                subscribeToAdminTopic();
            } else {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
    private void refreshMainPage() {
        getLastLocation(); // Will fetch the weather again
        loadTrips(); // Will reload the trips list
        checkAdminStatusAndUpdateUI(); // Optional: refresh admin UI
    }


}
