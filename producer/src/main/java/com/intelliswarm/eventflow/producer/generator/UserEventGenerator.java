package com.intelliswarm.eventflow.producer.generator;

import com.intelliswarm.eventflow.producer.model.UserEvent;
import java.time.Instant;
import java.util.Random;

public class UserEventGenerator {
    private static final String[] EVENT_TYPES = {"LOGIN", "LOGOUT", "SIGNUP"};
    private static final String[] COUNTRIES = {"US", "UK", "DE", "FR", "JP", "IN", "BR", "CA"};
    private static final String[] DEVICES = {"mobile", "desktop", "tablet"};

    private final Random random;
    private int userCounter = 0;

    public UserEventGenerator() {
        this.random = new Random();
    }

    public UserEvent generate() {
        String eventType = EVENT_TYPES[random.nextInt(EVENT_TYPES.length)];
        String userId = generateUserId();
        String country = COUNTRIES[random.nextInt(COUNTRIES.length)];
        String device = DEVICES[random.nextInt(DEVICES.length)];
        Instant timestamp = Instant.now();

        return new UserEvent(eventType, userId, country, device, timestamp);
    }

    private String generateUserId() {
        if (random.nextDouble() < 0.3) {
            return "u" + (random.nextInt(10) + 1);
        } else {
            userCounter++;
            return "u" + (100 + userCounter);
        }
    }

    public String getUserIdForKey() {
        return generate().getUserId();
    }
}
