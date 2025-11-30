package com.intelliswarm.eventflow.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.intelliswarm.eventflow.producer.generator.OrderGenerator;
import com.intelliswarm.eventflow.producer.generator.ProductViewGenerator;
import com.intelliswarm.eventflow.producer.generator.UserEventGenerator;
import com.intelliswarm.eventflow.producer.model.Order;
import com.intelliswarm.eventflow.producer.model.ProductView;
import com.intelliswarm.eventflow.producer.model.UserEvent;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProducerApp {
    private static final Logger logger = LoggerFactory.getLogger(ProducerApp.class);

    private static final String USER_EVENTS_TOPIC = "user-events";
    private static final String PRODUCT_VIEW_TOPIC = "product-view";
    private static final String ORDERS_TOPIC = "orders";

    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;
    private final UserEventGenerator userEventGenerator;
    private final ProductViewGenerator productViewGenerator;
    private final OrderGenerator orderGenerator;
    private final Random random;
    private final AtomicBoolean running;

    public ProducerApp(String bootstrapServers) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        this.producer = new KafkaProducer<>(props);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.userEventGenerator = new UserEventGenerator();
        this.productViewGenerator = new ProductViewGenerator();
        this.orderGenerator = new OrderGenerator();
        this.random = new Random();
        this.running = new AtomicBoolean(true);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down producer...");
            running.set(false);
            producer.close();
        }));
    }

    public void run(long delayMs) {
        logger.info("Starting producer with {}ms delay between events", delayMs);

        while (running.get()) {
            try {
                double rand = random.nextDouble();

                if (rand < 0.4) {
                    sendUserEvent();
                } else if (rand < 0.8) {
                    sendProductView();
                } else {
                    sendOrder();
                }

                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                logger.warn("Producer interrupted", e);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error producing event", e);
            }
        }

        logger.info("Producer stopped");
    }

    private void sendUserEvent() throws Exception {
        UserEvent event = userEventGenerator.generate();
        String key = event.getUserId();
        String value = objectMapper.writeValueAsString(event);

        ProducerRecord<String, String> record = new ProducerRecord<>(USER_EVENTS_TOPIC, key, value);
        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                logger.error("Error sending user event", exception);
            } else {
                logger.debug("User event sent: topic={}, partition={}, offset={}",
                           metadata.topic(), metadata.partition(), metadata.offset());
            }
        });
    }

    private void sendProductView() throws Exception {
        UserEvent userEvent = userEventGenerator.generate();
        String userId = userEvent.getUserId();
        ProductView event = productViewGenerator.generate(userId);
        String key = userId;
        String value = objectMapper.writeValueAsString(event);

        ProducerRecord<String, String> record = new ProducerRecord<>(PRODUCT_VIEW_TOPIC, key, value);
        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                logger.error("Error sending product view", exception);
            } else {
                logger.debug("Product view sent: topic={}, partition={}, offset={}",
                           metadata.topic(), metadata.partition(), metadata.offset());
            }
        });
    }

    private void sendOrder() throws Exception {
        UserEvent userEvent = userEventGenerator.generate();
        String userId = userEvent.getUserId();
        ProductView productView = productViewGenerator.generate(userId);
        String productId = productView.getProductId();

        Order event = orderGenerator.generate(userId, productId);
        String key = userId;
        String value = objectMapper.writeValueAsString(event);

        ProducerRecord<String, String> record = new ProducerRecord<>(ORDERS_TOPIC, key, value);
        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                logger.error("Error sending order", exception);
            } else {
                logger.info("Order sent: orderId={}, userId={}, productId={}, price={} {}",
                          event.getOrderId(), event.getUserId(), event.getProductId(),
                          event.getPrice(), event.getCurrency());
            }
        });
    }

    public static void main(String[] args) {
        String bootstrapServers = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        long delayMs = Long.parseLong(System.getenv().getOrDefault("EVENT_DELAY_MS", "100"));

        logger.info("EventFlow Producer starting...");
        logger.info("Bootstrap servers: {}", bootstrapServers);
        logger.info("Event delay: {}ms", delayMs);

        ProducerApp app = new ProducerApp(bootstrapServers);
        app.run(delayMs);
    }
}
