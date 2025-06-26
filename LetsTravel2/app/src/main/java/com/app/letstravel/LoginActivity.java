package com.app.letstravel;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private EditText etEmail, etPassword;
    private Button btnLogin, btnGoogleSignIn, btnSignup, btnBiometricLogin;
    private GoogleSignInClient googleSignInClient;
    private static final int RC_SIGN_IN = 9001;
    private static final String BASE_URL = "http://localhost:3000/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        fetchFirebaseConfig();
    }

    private void fetchFirebaseConfig() {
        SharedPreferences prefs = getSharedPreferences("FirebaseConfig", MODE_PRIVATE);
        if (prefs.contains("apiKey")) {
            FirebaseConfigResponse cachedConfig = new FirebaseConfigResponse();
            cachedConfig.setApiKey(prefs.getString("apiKey", ""));
            cachedConfig.setAppId(prefs.getString("appId", ""));
            cachedConfig.setProjectId(prefs.getString("projectId", ""));
            cachedConfig.setStorageBucket(prefs.getString("storageBucket", ""));
            cachedConfig.setMessagingSenderId(prefs.getString("messagingSenderId", ""));
            initializeFirebase(cachedConfig);
            fetchGoogleClientId();
            return;
        }

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        ApiService apiService = retrofit.create(ApiService.class);
        Call<FirebaseConfigResponse> call = apiService.getFirebaseConfig();
        call.enqueue(new Callback<FirebaseConfigResponse>() {
            @Override
            public void onResponse(Call<FirebaseConfigResponse> call, Response<FirebaseConfigResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    initializeFirebase(response.body());
                    cacheFirebaseConfig(response.body());
                    fetchGoogleClientId();
                } else {
                    Toast.makeText(LoginActivity.this, "Failed to fetch Firebase config", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<FirebaseConfigResponse> call, Throwable t) {
                Log.e(TAG, "Error fetching Firebase config", t);
                Toast.makeText(LoginActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void cacheFirebaseConfig(FirebaseConfigResponse config) {
        getSharedPreferences("FirebaseConfig", MODE_PRIVATE)
                .edit()
                .putString("apiKey", config.getApiKey())
                .putString("appId", config.getAppId())
                .putString("projectId", config.getProjectId())
                .putString("storageBucket", config.getStorageBucket())
                .putString("messagingSenderId", config.getMessagingSenderId())
                .apply();
    }

    private void initializeFirebase(FirebaseConfigResponse config) {
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setApiKey(config.getApiKey())
                    .setApplicationId(config.getAppId())
                    .setProjectId(config.getProjectId())
                    .setStorageBucket(config.getStorageBucket())
                    .setGcmSenderId(config.getMessagingSenderId())
                    .build();
            FirebaseApp.initializeApp(this, options);
        }
        mAuth = FirebaseAuth.getInstance();
    }

    private void fetchGoogleClientId() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        ApiService apiService = retrofit.create(ApiService.class);
        Call<GoogleClientIdsResponse> call = apiService.getGoogleClientIds();
        call.enqueue(new Callback<GoogleClientIdsResponse>() {
            @Override
            public void onResponse(Call<GoogleClientIdsResponse> call, Response<GoogleClientIdsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    configureGoogleSignIn(response.body().getWebClientId());
                    initializeUI();
                } else {
                    Toast.makeText(LoginActivity.this, "Failed to fetch Google client ID", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<GoogleClientIdsResponse> call, Throwable t) {
                Log.e(TAG, "Error fetching Google client ID", t);
                Toast.makeText(LoginActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void configureGoogleSignIn(String webClientId) {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    private void initializeUI() {
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        btnSignup = findViewById(R.id.btnSignup);
        btnBiometricLogin = findViewById(R.id.btnBiometricLogin);

        FirebaseUser currentUser = mAuth.getCurrentUser();
        boolean isLoggedIn = getSharedPreferences("AuthPrefs", MODE_PRIVATE).getBoolean("isLoggedIn", false);
        boolean biometricEnabled = isBiometricEnabled();

        if (currentUser != null && isLoggedIn && biometricEnabled) {
            hideAllUI();
            checkBiometricLogin();
            return;
        }

        if (currentUser != null && isLoggedIn && !biometricEnabled) {
            etEmail.setVisibility(View.VISIBLE);
            etPassword.setVisibility(View.VISIBLE);
            btnLogin.setVisibility(View.VISIBLE);
            btnGoogleSignIn.setVisibility(View.VISIBLE);
            btnSignup.setVisibility(View.GONE);
            btnBiometricLogin.setVisibility(View.GONE);
        } else {
            etEmail.setVisibility(View.VISIBLE);
            etPassword.setVisibility(View.VISIBLE);
            btnLogin.setVisibility(View.VISIBLE);
            btnGoogleSignIn.setVisibility(View.VISIBLE);
            btnSignup.setVisibility(View.VISIBLE);
            btnBiometricLogin.setVisibility(View.GONE);
        }

        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(LoginActivity.this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            saveSessionStatus(true);
                            assignUserIdIfFirstTime(user);
                            Toast.makeText(LoginActivity.this, "Login successful", Toast.LENGTH_SHORT).show();
                            navigateToHome();
                        } else {
                            Toast.makeText(LoginActivity.this, "Authentication failed.", Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        btnSignup.setOnClickListener(v -> navigateToSignup());

        btnGoogleSignIn.setOnClickListener(v -> {
            if (googleSignInClient == null) {
                Toast.makeText(LoginActivity.this, "Google Sign-In not initialized", Toast.LENGTH_SHORT).show();
                return;
            }
            googleSignInClient.signOut().addOnCompleteListener(this, task -> {
                Intent signInIntent = googleSignInClient.getSignInIntent();
                startActivityForResult(signInIntent, RC_SIGN_IN);
            });
        });
    }

    private void saveSessionStatus(boolean isLoggedIn) {
        getSharedPreferences("AuthPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("isLoggedIn", isLoggedIn)
                .apply();
    }

    private void navigateToHome() {
        Log.d(TAG, "Navigating to Home");
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void navigateToSignup() {
        Log.d(TAG, "Navigating to Signup");
        startActivity(new Intent(this, SignupActivity.class));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                Log.w(TAG, "Google sign in failed", e);
                Toast.makeText(LoginActivity.this, "Google sign in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        Log.d(TAG, "Google login successful: " + user.getEmail());
                        Toast.makeText(LoginActivity.this, "Google sign in successful", Toast.LENGTH_SHORT).show();
                        saveSessionStatus(true);
                        assignUserIdIfFirstTime(user);
                        navigateToHome();
                    } else {
                        Log.w(TAG, "signInWithCredential:failure", task.getException());
                        Toast.makeText(LoginActivity.this, "Authentication failed.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void checkBiometricLogin() {
        if (!isBiometricEnabled()) {
            return;
        }

        BiometricManager biometricManager = BiometricManager.from(this);
        switch (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                authenticateWithBiometrics();
                break;
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                Toast.makeText(this, "Biometric not supported or enrolled", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private boolean isBiometricEnabled() {
        return getSharedPreferences("AuthPrefs", MODE_PRIVATE)
                .getBoolean("biometricEnabled", false);
    }

    private void authenticateWithBiometrics() {
        BiometricPrompt biometricPrompt = new BiometricPrompt(this,
                ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        Toast.makeText(LoginActivity.this, "Biometric login successful", Toast.LENGTH_SHORT).show();
                        navigateToHome();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Toast.makeText(LoginActivity.this, "Biometric authentication failed", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                                errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                                errorCode == BiometricPrompt.ERROR_CANCELED) {
                            finishAffinity();
                        } else {
                            Toast.makeText(LoginActivity.this, "Biometric error: " + errString, Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Login")
                .setSubtitle("Authenticate to continue")
                .setNegativeButtonText("Cancel")
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void hideAllUI() {
        etEmail.setVisibility(View.GONE);
        etPassword.setVisibility(View.GONE);
        btnLogin.setVisibility(View.GONE);
        btnGoogleSignIn.setVisibility(View.GONE);
        btnSignup.setVisibility(View.GONE);
        btnBiometricLogin.setVisibility(View.GONE);
    }

    private void assignUserIdIfFirstTime(FirebaseUser firebaseUser) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String email = firebaseUser.getEmail();

        db.collection("users").document(firebaseUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String existingUserId = documentSnapshot.getString("userId");
                        saveUserIdLocally(existingUserId);
                        Log.d(TAG, "Existing User ID: " + existingUserId);
                    } else {
                        generateNewUserId(firebaseUser);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to check user ID.", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error checking user document", e);
                });
    }

    private void generateNewUserId(FirebaseUser firebaseUser) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("meta").document("userCounter")
                .get()
                .addOnSuccessListener(counterSnapshot -> {
                    long lastNumber = 0;
                    if (counterSnapshot.exists() && counterSnapshot.contains("lastUserNumber")) {
                        lastNumber = counterSnapshot.getLong("lastUserNumber");
                    }

                    long newNumber = lastNumber + 1;
                    String newUserId = String.format("id%05d", newNumber);

                    db.collection("users").document(firebaseUser.getUid())
                            .set(new UserRecord(newUserId, firebaseUser.getEmail()))
                            .addOnSuccessListener(aVoid -> {
                                db.collection("meta").document("userCounter")
                                        .update("lastUserNumber", newNumber);

                                saveUserIdLocally(newUserId);
                                Log.d(TAG, "New User ID Assigned: " + newUserId);
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Failed to save user ID.", Toast.LENGTH_SHORT).show();
                                Log.e(TAG, "Error saving user document", e);
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to get user counter.", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error getting user counter", e);
                });
    }

    private void saveUserIdLocally(String userId) {
        getSharedPreferences("UserPrefs", MODE_PRIVATE)
                .edit()
                .putString("userId", userId)
                .apply();
    }
}