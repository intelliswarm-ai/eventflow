package com.intelliswarm.eventflow.producer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class ProductView {
    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("productId")
    private String productId;

    @JsonProperty("category")
    private String category;

    @JsonProperty("timestamp")
    private Instant timestamp;

    public ProductView() {}

    public ProductView(String eventType, String userId, String productId, String category, Instant timestamp) {
        this.eventType = eventType;
        this.userId = userId;
        this.productId = productId;
        this.category = category;
        this.timestamp = timestamp;
    }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
