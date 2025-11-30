# Kafka Configuration Deep Dive

This document explains the Kafka configuration choices in EventFlow and how they support the event-driven architecture.

## Topic Configuration

### Topic Structure

```bash
# user-events: User activity events
Partitions: 3
Replication Factor: 1 (dev), 3 (prod recommended)
Key: userId
Retention: 7 days (default)
Cleanup Policy: delete

# product-view: Product browsing events
Partitions: 3
Replication Factor: 1 (dev), 3 (prod recommended)
Key: userId
Retention: 7 days (default)
Cleanup Policy: delete

# orders: Purchase transactions
Partitions: 3
Replication Factor: 1 (dev), 3 (prod recommended)
Key: userId
Retention: 30 days (recommended for prod)
Cleanup Policy: delete
```

### Why 3 Partitions?

1. **Parallelism**: Allows 3 concurrent consumers per consumer group
2. **Load Distribution**: Spreads data across brokers (in multi-broker setup)
3. **Scalability**: Can add consumers up to partition count
4. **Resource Balance**: Moderate resource usage for demo environment

Adjust based on throughput needs:
```bash
# For higher throughput (10K+ msgs/sec)
--partitions 6

# For lower throughput (<1K msgs/sec)
--partitions 1
```

### Partition Key Strategy

All topics use `userId` as the partition key:

```java
ProducerRecord<String, String> record =
    new ProducerRecord<>(topic, userId, jsonValue);
```

**Benefits:**
- **Ordering**: All events for a user go to same partition → ordered processing
- **Locality**: User data co-located, enabling efficient joins
- **Consistency**: Easier to maintain per-user state

**Considerations:**
- **Hot Keys**: Popular users create hot partitions
- **Skew**: Uneven distribution if user activity varies widely

**Alternatives:**
- Random key: Better distribution, no ordering
- Product ID: Group by product for product-level analytics
- Composite key: `userId:productId` for finer granularity

## Producer Configuration

### Code: `ProducerApp.java:31-37`

```java
props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
props.put(ProducerConfig.ACKS_CONFIG, "all");
props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
props.put(ProducerConfig.RETRIES_CONFIG, 3);
props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
```

### Configuration Breakdown

#### `ACKS_CONFIG = "all"`

**What it does:**
- Producer waits for acknowledgment from all in-sync replicas (ISR)
- Ensures message is written to leader AND all replicas

**Durability:**
- Strongest durability guarantee
- Prevents message loss even if leader fails

**Performance:**
- Higher latency (~2-3x slower than `acks=1`)
- Lower throughput

**Alternatives:**
```java
// Faster, less durable
props.put(ProducerConfig.ACKS_CONFIG, "1");  // Leader only

// Fastest, no durability
props.put(ProducerConfig.ACKS_CONFIG, "0");  // Fire and forget
```

#### `ENABLE_IDEMPOTENCE_CONFIG = true`

**What it does:**
- Prevents duplicate messages on retry
- Kafka assigns sequence numbers to messages
- Broker deduplicates based on sequence

**Benefits:**
- Exactly-once semantics for producer
- Safe to retry without duplicates
- No application-level dedup needed

**Requirements:**
- Automatically sets:
  - `acks=all`
  - `retries > 0`
  - `max.in.flight.requests.per.connection ≤ 5`

**When to disable:**
- Legacy Kafka versions (<0.11)
- Don't care about duplicates
- Need absolute maximum throughput

#### `RETRIES_CONFIG = 3`

**What it does:**
- Retries failed sends up to 3 times
- Applies to transient errors (network issues, leader elections)

**Tuning:**
```java
// Production: higher retries
props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);

// With backoff
props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100);
```

#### `MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION = 5`

**What it does:**
- Max unacknowledged requests per connection
- Default is 5 (required for idempotence)

**Ordering implications:**
```java
// Strict ordering (slower)
props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);

// Balanced (used here)
props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
```

### Additional Tuning Options

```java
// Batching (improves throughput)
props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);  // 16KB batches
props.put(ProducerConfig.LINGER_MS_CONFIG, 10);      // Wait 10ms for batch

// Compression (reduces network usage)
props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");  // or "lz4", "gzip"

// Buffer memory (for high throughput)
props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);  // 32MB

// Request timeout
props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);  // 30s
```

## Consumer Configuration

### Code: `StreamingApp.java:77-82`

```java
Dataset<Row> kafkaOrders = spark.readStream()
    .format("kafka")
    .option("kafka.bootstrap.servers", kafkaBootstrapServers)
    .option("subscribe", "orders")
    .option("startingOffsets", "latest")
    .load();
```

### Configuration Breakdown

#### `startingOffsets = "latest"`

**What it does:**
- New consumer starts from latest offset (newest messages)
- Ignores historical data

**Alternatives:**
```java
// Start from beginning (reprocess all)
.option("startingOffsets", "earliest")

// Start from specific offsets
.option("startingOffsets", """
    {"orders":{"0":100,"1":200,"2":300}}
""")

// Start from timestamp
.option("startingOffsetsByTimestamp", """
    {"orders":{"0":1638360000000,"1":1638360000000}}
""")
```

### Consumer Group ID

Spark Structured Streaming auto-generates consumer group IDs.

To set manually:
```java
.option("kafka.group.id", "spark-streaming-app")
```

### Additional Consumer Options

```java
// Poll settings
.option("kafka.max.poll.records", "500")
.option("kafka.max.poll.interval.ms", "300000")  // 5 min

// Fetch settings
.option("kafka.fetch.min.bytes", "1")
.option("kafka.fetch.max.wait.ms", "500")

// Security (if needed)
.option("kafka.security.protocol", "SASL_SSL")
.option("kafka.sasl.mechanism", "PLAIN")
```

## Broker Configuration

### `docker-compose.yml` Settings

```yaml
KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
```

### Production Recommendations

```yaml
# Multi-broker setup (3 brokers minimum)
KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 3
KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 2

# Disable auto-create (explicit topic management)
KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"

# Retention policies
KAFKA_LOG_RETENTION_HOURS: 168  # 7 days
KAFKA_LOG_RETENTION_BYTES: 1073741824  # 1GB per partition

# Segment settings
KAFKA_LOG_SEGMENT_BYTES: 1073741824  # 1GB segments
KAFKA_LOG_SEGMENT_MS: 604800000  # 7 days

# Performance
KAFKA_NUM_NETWORK_THREADS: 8
KAFKA_NUM_IO_THREADS: 8
KAFKA_SOCKET_SEND_BUFFER_BYTES: 102400
KAFKA_SOCKET_RECEIVE_BUFFER_BYTES: 102400
```

## Topic Management

### Creating Topics Manually

```bash
# Create with custom config
docker exec eventflow-kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --create \
  --topic orders \
  --partitions 6 \
  --replication-factor 3 \
  --config retention.ms=2592000000 \
  --config compression.type=snappy \
  --config min.insync.replicas=2
```

### Describing Topics

```bash
# Full topic details
docker exec eventflow-kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --describe \
  --topic orders

# Output shows:
# - Partition count
# - Replication factor
# - Leader and replica assignments
# - ISR (in-sync replicas)
```

### Modifying Topics

```bash
# Add partitions (can't reduce!)
docker exec eventflow-kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --alter \
  --topic orders \
  --partitions 6

# Update config
docker exec eventflow-kafka kafka-configs \
  --bootstrap-server localhost:9092 \
  --entity-type topics \
  --entity-name orders \
  --alter \
  --add-config retention.ms=604800000
```

## Monitoring

### Consumer Lag

```bash
# Check all consumer groups
docker exec eventflow-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --list

# Describe specific group
docker exec eventflow-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe \
  --group spark-streaming-app

# Shows:
# - Current offset
# - Log end offset
# - Lag (messages behind)
# - Consumer ID
```

### Topic Metrics

```bash
# Message count (approximate)
docker exec eventflow-kafka kafka-run-class \
  kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 \
  --topic orders

# Disk usage
docker exec eventflow-kafka du -sh /var/lib/kafka/data/orders-*
```

## Performance Tuning Guide

### High Throughput Setup

```java
// Producer
props.put(ProducerConfig.BATCH_SIZE_CONFIG, 32768);
props.put(ProducerConfig.LINGER_MS_CONFIG, 20);
props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 67108864);  // 64MB

// Consumer
.option("kafka.fetch.min.bytes", "50000")
.option("kafka.fetch.max.wait.ms", "500")
.option("kafka.max.partition.fetch.bytes", "1048576")  // 1MB
```

### Low Latency Setup

```java
// Producer
props.put(ProducerConfig.BATCH_SIZE_CONFIG, 1);
props.put(ProducerConfig.LINGER_MS_CONFIG, 0);
props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "none");

// Consumer
.option("kafka.fetch.min.bytes", "1")
.option("kafka.fetch.max.wait.ms", "0")
```

### Balanced Setup (EventFlow default)

```java
// Good balance of throughput and latency
// See ProducerApp.java for settings
```

## Security Considerations

### SSL/TLS

```java
props.put("security.protocol", "SSL");
props.put("ssl.truststore.location", "/path/to/truststore.jks");
props.put("ssl.truststore.password", "password");
props.put("ssl.keystore.location", "/path/to/keystore.jks");
props.put("ssl.keystore.password", "password");
props.put("ssl.key.password", "password");
```

### SASL Authentication

```java
props.put("security.protocol", "SASL_SSL");
props.put("sasl.mechanism", "PLAIN");
props.put("sasl.jaas.config",
    "org.apache.kafka.common.security.plain.PlainLoginModule required " +
    "username='alice' password='secret';");
```

### ACLs

```bash
# Grant produce permission
docker exec eventflow-kafka kafka-acls \
  --bootstrap-server localhost:9092 \
  --add \
  --allow-principal User:producer-app \
  --operation Write \
  --topic orders

# Grant consume permission
docker exec eventflow-kafka kafka-acls \
  --bootstrap-server localhost:9092 \
  --add \
  --allow-principal User:spark-app \
  --operation Read \
  --topic orders \
  --group spark-streaming-app
```

## Advanced Topics

### Compacted Topics

For state management (e.g., user profiles):

```bash
docker exec eventflow-kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --create \
  --topic user-profiles \
  --partitions 3 \
  --replication-factor 3 \
  --config cleanup.policy=compact \
  --config min.cleanable.dirty.ratio=0.5 \
  --config segment.ms=100
```

### Transactional Producers

```java
props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "producer-1");
props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");

producer.initTransactions();
try {
    producer.beginTransaction();
    producer.send(record1);
    producer.send(record2);
    producer.commitTransaction();
} catch (Exception e) {
    producer.abortTransaction();
}
```

### Schema Registry Integration

```java
// With Confluent Schema Registry
props.put("schema.registry.url", "http://schema-registry:8081");
props.put("key.serializer", StringSerializer.class.getName());
props.put("value.serializer", KafkaAvroSerializer.class.getName());
```

## References

- [Kafka Producer Configs](https://kafka.apache.org/documentation/#producerconfigs)
- [Kafka Consumer Configs](https://kafka.apache.org/documentation/#consumerconfigs)
- [Kafka Broker Configs](https://kafka.apache.org/documentation/#brokerconfigs)
- [Confluent Best Practices](https://docs.confluent.io/platform/current/kafka/deployment.html)

## Summary

EventFlow uses Kafka with:
- Strong durability (`acks=all`)
- Idempotent producers (no duplicates)
- User-keyed partitioning (ordering per user)
- 3 partitions per topic (parallelism)
- Latest offset consumption (real-time processing)

These settings balance reliability, performance, and ease of use for a demonstration platform. Adjust based on your production requirements.
