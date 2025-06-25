package com.app.letstravel;

public class NotificationRequest {
    private String title;
    private String body;
    private String topic;
    private String secret;

    public NotificationRequest(String title, String body, String topic, String secret) {
        this.title = title;
        this.body = body;
        this.topic = topic;
        this.secret = secret;
    }

}

