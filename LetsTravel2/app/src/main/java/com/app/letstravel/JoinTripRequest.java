package com.app.letstravel;

public class JoinTripRequest {
    private String userId;
    private String tripId;
    private String tripSubject;

    public JoinTripRequest(String userId, String tripId, String tripSubject) {
        this.userId = userId;
        this.tripId = tripId;
        this.tripSubject = tripSubject;
    }

    public String getUserId() {
        return userId;
    }

    public String getTripId() {
        return tripId;
    }

    public String getTripSubject() {
        return tripSubject;
    }
}
