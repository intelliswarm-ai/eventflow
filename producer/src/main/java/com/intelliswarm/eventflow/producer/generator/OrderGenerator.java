package com.intelliswarm.eventflow.producer.generator;

import com.intelliswarm.eventflow.producer.model.Order;
import java.time.Instant;
import java.util.Random;

public class OrderGenerator {
    private static final String[] CURRENCIES = {"USD", "EUR", "GBP", "JPY", "INR"};

    private final Random random;
    private int orderCounter = 0;

    public OrderGenerator() {
        this.random = new Random();
    }

    public Order generate(String userId, String productId) {
        String eventType = "ORDER";
        String orderId = "o" + (++orderCounter);
        double price = 10.0 + (random.nextDouble() * 490.0);
        String currency = CURRENCIES[random.nextInt(CURRENCIES.length)];
        Instant timestamp = Instant.now();

        return new Order(eventType, orderId, userId, productId,
                        Math.round(price * 100.0) / 100.0, currency, timestamp);
    }
}
