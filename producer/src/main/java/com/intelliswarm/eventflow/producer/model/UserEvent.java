package com.intelliswarm.eventflow.producer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class UserEvent {
    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("country")
    private String country;

    @JsonProperty("device")
    private String device;

    @JsonProperty("timestamp")
    private Instant timestamp;

    public UserEvent() {}

    public UserEvent(String eventType, String userId, String country, String device, Instant timestamp) {
        this.eventType = eventType;
        this.userId = userId;
        this.country = country;
        this.device = device;
        this.timestamp = timestamp;
    }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getDevice() { return device; }
    public void setDevice(String device) { this.device = device; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
