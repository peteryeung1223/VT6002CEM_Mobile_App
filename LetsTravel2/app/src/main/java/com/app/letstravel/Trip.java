package com.app.letstravel;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Trip {
    private String id;
    private String subject, startDate, endDate, venue, activities, transportation, deadline, contact, remark;
    private int participants;
    private List<String> imageBase64List;
    private long currentParticipants;
    private long maxParticipants;
    private Date createdAt;

    public Trip() {
        // Required for Firestore deserialization
    }

    Trip(String subject, String startDate, String endDate, String venue, String activities,
         int participants, String transportation, String deadline, String contact, String remark,
         List<String> imageBase64List, long currentParticipants, long maxParticipants) {
        this.subject = subject;
        this.startDate = startDate;
        this.endDate = endDate;
        this.venue = venue;
        this.activities = activities;
        this.participants = participants;
        this.transportation = transportation;
        this.deadline = deadline;
        this.contact = contact;
        this.remark = remark;
        this.imageBase64List = imageBase64List != null ? imageBase64List : new ArrayList<>();
        this.currentParticipants = currentParticipants;
        this.maxParticipants = maxParticipants;
        this.createdAt = new Date(); // Default to now
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }

    public String getActivities() { return activities; }
    public void setActivities(String activities) { this.activities = activities; }

    public int getParticipants() { return participants; }
    public void setParticipants(int participants) { this.participants = participants; }

    public String getTransportation() { return transportation; }
    public void setTransportation(String transportation) { this.transportation = transportation; }

    public String getDeadline() { return deadline; }
    public void setDeadline(String deadline) { this.deadline = deadline; }

    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public List<String> getImageBase64List() { return imageBase64List; }
    public void setImageBase64List(List<String> imageBase64List) { this.imageBase64List = imageBase64List; }

    public long getCurrentParticipants() { return currentParticipants; }
    public void setCurrentParticipants(long currentParticipants) { this.currentParticipants = currentParticipants; }

    public long getMaxParticipants() { return maxParticipants; }
    public void setMaxParticipants(long maxParticipants) { this.maxParticipants = maxParticipants; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    // Firestore serialization
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("subject", subject);
        map.put("startDate", startDate);
        map.put("endDate", endDate);
        map.put("venue", venue);
        map.put("activities", activities);
        map.put("participants", participants);
        map.put("transportation", transportation);
        map.put("deadline", deadline);
        map.put("contact", contact);
        map.put("remark", remark);
        map.put("imageBase64List", imageBase64List);
        map.put("currentParticipants", currentParticipants);
        map.put("maxParticipants", maxParticipants);
        map.put("createdAt", createdAt);
        return map;
    }
}
