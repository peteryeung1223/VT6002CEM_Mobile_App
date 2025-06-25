package com.app.letstravel;

public class UserRecord {
    private String userId;
    private String email;

    public UserRecord() { } // Needed for Firestore

    public UserRecord(String userId, String email) {
        this.userId = userId;
        this.email = email;
    }

    public String getUserId() { return userId; }
    public String getEmail() { return email; }
}
