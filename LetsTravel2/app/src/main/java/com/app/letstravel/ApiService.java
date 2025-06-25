package com.app.letstravel;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface ApiService {
    public class AdminSecretResponse {
        private String secret;

        public String getSecret() {
            return secret;
        }
    }

    @GET("/firebase-config")
    Call<FirebaseConfigResponse> getFirebaseConfig();

    @GET("/auth/google-client-ids")
    Call<GoogleClientIdsResponse> getGoogleClientIds();

    @POST("send-notification")
    Call<Void> sendNotification(@Body NotificationRequest notificationRequest);

    @GET("admin-secret")
    Call<AdminSecretResponse> getAdminSecret();

    @POST("join-trip")
    Call<Void> joinTrip(@Body JoinTripRequest request);

    @GET("trip-news-api-key")
    Call<ApiKeyResponse> getTripNewsApiKey();

    @GET("weather-api-key")
    Call<ApiKeyResponse> getWeatherApiKey();
}