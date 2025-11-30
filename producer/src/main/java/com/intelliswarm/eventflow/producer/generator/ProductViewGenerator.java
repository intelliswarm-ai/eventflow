package com.intelliswarm.eventflow.producer.generator;

import com.intelliswarm.eventflow.producer.model.ProductView;
import java.time.Instant;
import java.util.Random;

public class ProductViewGenerator {
    private static final String[] CATEGORIES = {
        "electronics", "clothing", "books", "home", "sports", "toys"
    };

    private final Random random;

    public ProductViewGenerator() {
        this.random = new Random();
    }

    public ProductView generate(String userId) {
        String eventType = "VIEW";
        String productId = generateProductId();
        String category = CATEGORIES[random.nextInt(CATEGORIES.length)];
        Instant timestamp = Instant.now();

        return new ProductView(eventType, userId, productId, category, timestamp);
    }

    private String generateProductId() {
        if (random.nextDouble() < 0.4) {
            return "p" + (random.nextInt(20) + 1);
        } else {
            return "p" + (random.nextInt(1000) + 100);
        }
    }
}
