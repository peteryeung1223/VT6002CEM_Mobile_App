package com.app.letstravel;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class ProfileActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private Button btnLogout;
    private Switch switchBiometric;
    private Switch switchAdmin;
    private Switch switchPortraitLock;

    private SharedPreferences prefs;

    private static final int NOTIFICATION_PERMISSION_REQUEST = 1001;

    private String pendingAdminPassword = null;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private SensorEventListener orientationListener;
    private boolean isPortraitLockEnabled = false;
    private boolean hasShownLandscapeToast = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        if (prefs.getBoolean("portraitLock", false)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
        GoogleSignInAccount googleAccount = GoogleSignIn.getLastSignedInAccount(this);

        TextView tvEmailDisplay = findViewById(R.id.tvEmailDisplay);
        Button btnChangePassword = findViewById(R.id.btnChangePassword);
        switchBiometric = findViewById(R.id.switchBiometric);
        btnLogout = findViewById(R.id.btnLogout);

        if (googleAccount != null) {
            tvEmailDisplay.setVisibility(View.GONE);
            btnChangePassword.setVisibility(View.GONE);
        } else if (user != null) {
            tvEmailDisplay.setText("Hello: " + user.getEmail());
            tvEmailDisplay.setVisibility(View.VISIBLE);
            btnChangePassword.setVisibility(View.VISIBLE);
        }

        btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());

        boolean isEnabled = getSharedPreferences("AuthPrefs", MODE_PRIVATE).getBoolean("biometricEnabled", false);
        switchBiometric.setChecked(isEnabled);

        switchBiometric.setOnCheckedChangeListener((buttonView, isChecked) -> {
            getSharedPreferences("AuthPrefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean("biometricEnabled", isChecked)
                    .apply();

            String status = isChecked ? "enabled" : "disabled";
            Toast.makeText(ProfileActivity.this, "Biometric authentication " + status, Toast.LENGTH_SHORT).show();
        });

        btnLogout.setOnClickListener(v -> new AlertDialog.Builder(ProfileActivity.this)
                .setTitle("Confirm Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> logoutUser())
                .setNegativeButton("Cancel", null)
                .show());

        switchAdmin = findViewById(R.id.switchAdmin);
        boolean isAdmin = getSharedPreferences("AuthPrefs", MODE_PRIVATE).getBoolean("isAdmin", false);
        switchAdmin.setChecked(isAdmin);

        switchAdmin.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                promptAdminPassword();
            } else {
                getSharedPreferences("AuthPrefs", MODE_PRIVATE)
                        .edit()
                        .putBoolean("isAdmin", false)
                        .remove("adminPass")
                        .apply();

                FirebaseMessaging.getInstance().unsubscribeFromTopic("admin_topic")
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Toast.makeText(this, "Admin mode disabled", Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        });

        Switch switchEyeProtection = findViewById(R.id.switchEyeProtection);
        boolean isEyeProtectionOn = prefs.getBoolean("eyeProtection", false);
        switchEyeProtection.setChecked(isEyeProtectionOn);

        switchEyeProtection.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("eyeProtection", isChecked).apply();

            if (isChecked) {
                EyeProtectionManager.getInstance().detectOnceAndStart(ProfileActivity.this);
            } else {
                EyeProtectionManager.getInstance().stop();
                EyeProtectionManager.getInstance().resetBackground(ProfileActivity.this);
                prefs.edit().putBoolean("eyeProtectionColorYellow", false).apply();
            }
        });

        // Portrait Lock Setup
        switchPortraitLock = findViewById(R.id.switchPortraitLock);
        switchPortraitLock.setChecked(prefs.getBoolean("portraitLock", false));
        isPortraitLockEnabled = switchPortraitLock.isChecked();

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        orientationListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                float y = event.values[1];

                if (isPortraitLockEnabled) {
                    if (Math.abs(y) < 2 && !hasShownLandscapeToast) {
                        Toast.makeText(ProfileActivity.this, "Portrait lock is enabled", Toast.LENGTH_SHORT).show();
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

        switchPortraitLock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("portraitLock", isChecked).apply();
            isPortraitLockEnabled = isChecked;

            if (isChecked) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                Toast.makeText(ProfileActivity.this, "Portrait Lock Enabled", Toast.LENGTH_SHORT).show();
                sensorManager.registerListener(orientationListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                Toast.makeText(ProfileActivity.this, "Portrait Lock Disabled", Toast.LENGTH_SHORT).show();
                sensorManager.unregisterListener(orientationListener);
            }
        });
    }

    private void promptAdminPassword() {
        EditText input = new EditText(this);
        input.setHint("Enter admin password");

        new AlertDialog.Builder(this)
                .setTitle("Admin Verification")
                .setView(input)
                .setPositiveButton("OK", (dialog, which) -> {
                    String enteredPass = input.getText().toString().trim();
                    verifyAdminPassword(enteredPass);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    switchAdmin.setChecked(false);
                })
                .show();
    }

    private void verifyAdminPassword(String enteredPass) {
        new Thread(() -> {
            try {
                URL url = new URL("http://localhost:3000/admin-secret");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) result.append(line);

                reader.close();
                conn.disconnect();

                JSONObject json = new JSONObject(result.toString());
                String serverSecret = json.getString("secret");

                runOnUiThread(() -> {
                    if (enteredPass.equals(serverSecret)) {
                        pendingAdminPassword = enteredPass;
                        requestNotificationPermission();
                    } else {
                        switchAdmin.setChecked(false);
                        Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    switchAdmin.setChecked(false);
                    Toast.makeText(this, "Failed to verify admin", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
        } else {
            enableAdminMode();
        }
    }

    private void enableAdminMode() {
        if (pendingAdminPassword == null) return;

        getSharedPreferences("AuthPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("isAdmin", true)
                .putString("adminPass", pendingAdminPassword)
                .apply();

        FirebaseMessaging.getInstance().subscribeToTopic("admin_topic")
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Admin mode enabled", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Admin mode enabled but subscription failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableAdminMode();
            } else {
                switchAdmin.setChecked(false);
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void logoutUser() {
        mAuth.signOut();

        GoogleSignInAccount googleAccount = GoogleSignIn.getLastSignedInAccount(this);
        if (googleAccount != null) {
            GoogleSignInClient googleClient = GoogleSignIn.getClient(this,
                    com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN);
            googleClient.signOut();
        }

        getSharedPreferences("AuthPrefs", MODE_PRIVATE)
                .edit()
                .clear()
                .apply();

        FirebaseMessaging.getInstance().unsubscribeFromTopic("admin_topic");

        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void showChangePasswordDialog() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_change_password, null);

        EditText etCurrentPassword = dialogView.findViewById(R.id.etCurrentPassword);
        EditText etNewPassword = dialogView.findViewById(R.id.etNewPassword);
        EditText etConfirmNewPassword = dialogView.findViewById(R.id.etConfirmNewPassword);
        TextView tvEmail = dialogView.findViewById(R.id.tvDialogEmail);
        tvEmail.setText("Email " + user.getEmail());

        new AlertDialog.Builder(this)
                .setTitle("Change Password")
                .setView(dialogView)
                .setPositiveButton("OK", (dialog, which) -> {
                    String currentPass = etCurrentPassword.getText().toString().trim();
                    String newPass = etNewPassword.getText().toString().trim();
                    String confirmPass = etConfirmNewPassword.getText().toString().trim();

                    if (currentPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
                        Toast.makeText(this, "All fields required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (!newPass.equals(confirmPass)) {
                        Toast.makeText(this, "New passwords do not match", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    com.google.firebase.auth.AuthCredential credential =
                            com.google.firebase.auth.EmailAuthProvider.getCredential(user.getEmail(), currentPass);

                    user.reauthenticate(credential).addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            user.updatePassword(newPass).addOnCompleteListener(updateTask -> {
                                if (updateTask.isSuccessful()) {
                                    Toast.makeText(this, "Password updated", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show();
                                }
                            });
                        } else {
                            Toast.makeText(this, "Reauthentication failed", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        EyeProtectionManager.getInstance().start(this);

        if (isPortraitLockEnabled) {
            sensorManager.registerListener(orientationListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        EyeProtectionManager.getInstance().stop();

        if (isPortraitLockEnabled) {
            sensorManager.unregisterListener(orientationListener);
        }
    }
}
