package com.intelliswarm.eventflow.producer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class Order {
    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("productId")
    private String productId;

    @JsonProperty("price")
    private double price;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("timestamp")
    private Instant timestamp;

    public Order() {}

    public Order(String eventType, String orderId, String userId, String productId,
                 double price, String currency, Instant timestamp) {
        this.eventType = eventType;
        this.orderId = orderId;
        this.userId = userId;
        this.productId = productId;
        this.price = price;
        this.currency = currency;
        this.timestamp = timestamp;
    }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
