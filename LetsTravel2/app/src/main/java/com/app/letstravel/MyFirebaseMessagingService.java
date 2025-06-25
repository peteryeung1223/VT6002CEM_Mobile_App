package com.app.letstravel;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d("FCM", "New token: " + token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Log.d("FCM", "Message received: " + remoteMessage.getData());

        String title = "New Trip Join Request";
        String body = "You have a new trip join request.";

        // If notification block exists (in case backend sends it)
        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            body = remoteMessage.getNotification().getBody();
        }

        // If data block exists
        if (remoteMessage.getData().size() > 0) {
            Map<String, String> data = remoteMessage.getData();
            if (data.containsKey("tripId") && data.containsKey("userId")) {
                title = "New Trip Join Request";
                body = "User " + data.get("userId") + " joined trip " + data.get("tripId");
            }
        }

        // Show the notification regardless of where it came from
        NotificationHelper.showNotification(this, title, body);
    }
}
