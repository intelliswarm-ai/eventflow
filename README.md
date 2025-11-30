# EventFlow: Kafka-Centric Real-Time Event Pipeline with Spark

A production-ready, real-time event processing platform showcasing the power of Apache Kafka and Apache Spark for event-driven architectures. This project demonstrates streaming data processing, windowed aggregations, and batch analytics using Java, Maven, and Docker.

## Table of Contents

- [Architecture](#architecture)
- [Features](#features)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Project Structure](#project-structure)
- [Data Model](#data-model)
- [Kafka Topics](#kafka-topics)
- [Analytics](#analytics)
- [Monitoring](#monitoring)
- [Configuration](#configuration)
- [Development](#development)
- [Troubleshooting](#troubleshooting)

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                          Event Producers                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐               │
│  │ User Events  │  │ Product Views│  │    Orders    │               │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘               │
└─────────┼─────────────────┼─────────────────┼───────────────────────┘
          │                 │                 │
          ▼                 ▼                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        Apache Kafka                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐               │
│  │ user-events  │  │ product-view │  │    orders    │               │
│  │ (3 partitions)  │ (3 partitions)  │ (3 partitions)               │
│  └──────────────┘  └──────────────┘  └──────────────┘               │
└─────────────────────────────────────────────────────────────────────┘
          │                  │                  │
          ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                   Spark Structured Streaming                        │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │  • Real-time order processing                               │    │
│  │  • Windowed revenue aggregation (5-minute windows)          │    │
│  │  • Conversion funnel tracking (views → orders)              │    │
│  │  • Multi-stream joins with watermarking                     │    │
│  └─────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         PostgreSQL                                  │
│  ┌──────────────────┐  ┌──────────────────────────────────────┐     │
│  │   orders_raw     │  │  revenue_by_currency_5min            │     │
│  │ conversion_funnel│  │  (aggregated tables)                 │     │
│  └──────────────────┘  └──────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     Spark Batch Analytics                           │
│  • Top revenue by currency                                          │
│  • Conversion rate analysis                                         │
│  • User segmentation (VIP, Premium, Regular, New)                   │
│  • Time-based revenue trends                                        │
└─────────────────────────────────────────────────────────────────────┘
```

## Features

### Kafka Features
- **Multiple topics** for different event types (user-events, product-view, orders)
- **Proper keying and partitioning** (userId as key for co-location)
- **Producer configuration** with `acks=all` and `enable.idempotence=true`
- **3 partitions per topic** for parallel processing
- **Consumer groups** for scalable consumption

### Spark Streaming Features
- **Structured Streaming** with Kafka integration
- **Windowed aggregations** (5-minute tumbling windows)
- **Watermarking** (10-minute watermark for late data handling)
- **Stream-stream joins** (product views + orders for conversion analysis)
- **Multiple output modes** (append, update)
- **PostgreSQL sink** for persistent storage

### Spark Batch Features
- **JDBC connectivity** to PostgreSQL
- **Complex SQL queries** with window functions
- **User segmentation** based on spending
- **Conversion rate analysis**
- **Revenue trend analysis**

### Producer Features
- **Realistic event generation** with proper distributions
- **Configurable event rate** via environment variables
- **Multiple event types** (LOGIN, LOGOUT, SIGNUP, VIEW, ORDER)
- **JSON serialization** with timestamp support
- **Heavy user simulation** (30% traffic from top 10 users)
- **Popular products** (40% views for top 20 products)

## Prerequisites

- Docker and Docker Compose (v2.0+)
- Java 11 or higher (for local development)
- Maven 3.6+ (for local development)
- At least 4GB RAM available for Docker
- 10GB disk space

## Quick Start

### 1. Clone and Start

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f producer
docker-compose logs -f spark-streaming

# Check service health
docker-compose ps
```

### 2. Verify Kafka Topics

```bash
# List topics
docker exec eventflow-kafka kafka-topics --bootstrap-server localhost:9092 --list

# Describe a topic
docker exec eventflow-kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic orders

# Consume messages (sample)
docker exec eventflow-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic orders \
  --from-beginning \
  --max-messages 10
```

### 3. Check PostgreSQL Data

```bash
# Connect to PostgreSQL
docker exec -it eventflow-postgres psql -U analytics -d analytics

# View orders
SELECT * FROM orders_raw LIMIT 10;

# View revenue aggregations
SELECT * FROM revenue_by_currency_5min ORDER BY window_start DESC LIMIT 10;

# View conversion funnel
SELECT * FROM conversion_funnel_5min ORDER BY window_start DESC LIMIT 10;
```

### 4. View Real-Time Dashboards

```bash
# Open Grafana in browser
open http://localhost:3000

# Login: admin / admin
# Navigate to: Dashboards → EventFlow folder

# Available dashboards:
# - EventFlow - Real-Time Pipeline (live metrics)
# - EventFlow - Kafka Health & Performance
# - EventFlow - Revenue Analytics
```

### 5. Run Batch Analytics

```bash
# Run the batch analytics job
docker-compose --profile batch up spark-batch

# Or run it manually after data accumulates
docker-compose run --rm spark-batch
```

## Project Structure

```
eventflow/
├── pom.xml                          # Parent POM
├── docker-compose.yml               # Docker Compose configuration
├── README.md                        # This file
│
├── producer/                        # Event Producer Service
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/intelliswarm/eventflow/producer/
│       ├── ProducerApp.java        # Main application
│       ├── model/
│       │   ├── UserEvent.java      # User event model
│       │   ├── ProductView.java    # Product view model
│       │   └── Order.java          # Order model
│       └── generator/
│           ├── UserEventGenerator.java
│           ├── ProductViewGenerator.java
│           └── OrderGenerator.java
│
├── spark-streaming/                 # Spark Streaming Service
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/intelliswarm/eventflow/streaming/
│       └── StreamingApp.java       # Streaming application with multiple queries
│
└── spark-batch/                     # Spark Batch Analytics
    ├── pom.xml
    ├── Dockerfile
    └── src/main/java/com/intelliswarm/eventflow/batch/
        └── BatchAnalyticsApp.java  # Batch analytics application
```

## Data Model

### User Event
```json
{
  "eventType": "LOGIN",
  "userId": "u123",
  "country": "DE",
  "device": "mobile",
  "timestamp": "2025-11-30T11:15:32Z"
}
```

Event types: `LOGIN`, `LOGOUT`, `SIGNUP`

### Product View
```json
{
  "eventType": "VIEW",
  "userId": "u123",
  "productId": "p10",
  "category": "electronics",
  "timestamp": "2025-11-30T11:16:10Z"
}
```

Categories: `electronics`, `clothing`, `books`, `home`, `sports`, `toys`

### Order
```json
{
  "eventType": "ORDER",
  "orderId": "o777",
  "userId": "u123",
  "productId": "p10",
  "price": 59.99,
  "currency": "EUR",
  "timestamp": "2025-11-30T11:17:00Z"
}
```

Currencies: `USD`, `EUR`, `GBP`, `JPY`, `INR`

## Kafka Topics

| Topic | Partitions | Key | Description |
|-------|-----------|-----|-------------|
| `user-events` | 3 | userId | User login, logout, signup events |
| `product-view` | 3 | userId | Product browsing events |
| `orders` | 3 | userId | Purchase transactions |

### Key Design Decisions

1. **Partitioning by userId**: Ensures all events for a user go to the same partition, enabling:
   - Ordered processing per user
   - Efficient joins on userId
   - Better locality for user-based aggregations

2. **3 partitions per topic**: Balances parallelism with resource usage

3. **Replication factor 1**: Suitable for development (use 3 in production)

## Analytics

### Real-time Streaming Queries

1. **Order Processing** (`processOrders`):
   - Writes raw orders to PostgreSQL
   - Append mode with 10-second trigger interval
   - Location: `StreamingApp.java:77`

2. **Revenue Aggregation** (`processRevenueAggregation`):
   - 5-minute tumbling windows
   - Groups by currency
   - Calculates total revenue, order count, avg order value
   - Update mode (updates existing windows)
   - Location: `StreamingApp.java:106`

3. **Conversion Funnel** (`processConversionFunnel`):
   - Joins product views and orders streams
   - 5-minute windows per user
   - Full outer join to capture views without orders
   - Location: `StreamingApp.java:166`

### Batch Analytics Queries

1. **Top Revenue Analysis** (`topRevenueAnalysis`):
   - Total revenue and orders by currency
   - Average order value per currency
   - Location: `BatchAnalyticsApp.java:49`

2. **Conversion Rate Analysis** (`conversionRateAnalysis`):
   - Conversion rate per user (orders / views)
   - Overall conversion metrics
   - Location: `BatchAnalyticsApp.java:66`

3. **Revenue by Time** (`revenueByTimeAnalysis`):
   - Revenue trends across time windows
   - Location: `BatchAnalyticsApp.java:97`

4. **User Segmentation** (`userSegmentationAnalysis`):
   - Segments: VIP (>$1000), Premium (>$500), Regular (>$100), New
   - Per-segment metrics
   - Location: `BatchAnalyticsApp.java:115`

## Monitoring

EventFlow includes a comprehensive monitoring stack with **Grafana**, **Prometheus**, and **Kafka JMX metrics** for full visibility into your real-time pipeline.

### Grafana Dashboards

Access Grafana at **http://localhost:3000** (admin/admin)

**Pre-configured Dashboards**:

1. **EventFlow - Real-Time Pipeline** (`/d/eventflow-realtime`)
   - Live event processing metrics
   - Orders per minute, revenue trends
   - 5-minute windowed aggregations
   - Currency distribution
   - Auto-refresh: 5 seconds

2. **EventFlow - Kafka Health & Performance** (`/d/eventflow-kafka`)
   - Messages in/out rate by topic
   - Bytes in/out rate
   - Request latency (Produce, Fetch)
   - Partition count
   - Auto-refresh: 5 seconds

3. **EventFlow - Revenue Analytics** (`/d/eventflow-revenue`)
   - Revenue summary by currency
   - Top customers and products
   - Order volume trends
   - Average order value analysis
   - Auto-refresh: 10 seconds

### Quick Monitoring Commands

**View Producer Logs**:
```bash
docker-compose logs -f producer
```

**View Streaming Logs**:
```bash
docker-compose logs -f spark-streaming
```

**Monitor Kafka Lag**:
```bash
docker exec eventflow-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe \
  --all-groups
```

**Check Database Tables**:
```bash
docker exec -it eventflow-postgres psql -U analytics -d analytics -c "\dt"
```

**View Kafka Metrics**:
```bash
# Open Prometheus UI
open http://localhost:9090

# Query Kafka message rate
# kafka_server_brokertopicmetrics_messagesinpersec
```

For detailed monitoring setup and customization, see [MONITORING.md](MONITORING.md).

## Configuration

### Environment Variables

#### Producer
- `KAFKA_BOOTSTRAP_SERVERS`: Kafka broker address (default: `kafka:9092`)
- `EVENT_DELAY_MS`: Delay between events in milliseconds (default: `100`)

#### Spark Streaming
- `KAFKA_BOOTSTRAP_SERVERS`: Kafka broker address (default: `kafka:9092`)
- `POSTGRES_URL`: PostgreSQL JDBC URL (default: `jdbc:postgresql://postgres:5432/analytics`)
- `POSTGRES_USER`: Database user (default: `analytics`)
- `POSTGRES_PASSWORD`: Database password (default: `secret`)

#### Spark Batch
- `POSTGRES_URL`: PostgreSQL JDBC URL
- `POSTGRES_USER`: Database user
- `POSTGRES_PASSWORD`: Database password

### Tuning Parameters

#### Kafka Producer
In `ProducerApp.java:31`:
```java
props.put(ProducerConfig.ACKS_CONFIG, "all");
props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
props.put(ProducerConfig.RETRIES_CONFIG, 3);
```

#### Spark Streaming
In `StreamingApp.java:28`:
```java
.config("spark.sql.shuffle.partitions", "4")
```

Watermark: `10 minutes` (adjust based on late data requirements)
Window size: `5 minutes` (adjust based on analytics needs)

## Development

### Build Locally

```bash
# Build all modules
mvn clean package

# Build specific module
cd producer && mvn clean package
cd spark-streaming && mvn clean package
cd spark-batch && mvn clean package
```

### Run Locally (without Docker)

1. Start Kafka and PostgreSQL:
```bash
docker-compose up -d zookeeper kafka postgres kafka-init
```

2. Run producer:
```bash
cd producer
java -jar target/producer-1.0.0-shaded.jar
```

3. Run streaming app:
```bash
cd spark-streaming
java -jar target/spark-streaming-1.0.0-shaded.jar
```

4. Run batch analytics:
```bash
cd spark-batch
java -jar target/spark-batch-1.0.0-shaded.jar
```

### Modify Event Rate

Increase event production rate:
```bash
docker-compose stop producer
docker-compose up -d -e EVENT_DELAY_MS=50 producer
```

Or edit `docker-compose.yml` and restart.

## Troubleshooting

### Kafka Not Ready

If producer fails to connect:
```bash
# Check Kafka health
docker-compose logs kafka

# Restart Kafka
docker-compose restart kafka

# Wait for topics to be created
docker-compose logs kafka-init
```

### PostgreSQL Connection Issues

```bash
# Check PostgreSQL health
docker exec eventflow-postgres pg_isready -U analytics

# View PostgreSQL logs
docker-compose logs postgres

# Reset database
docker-compose down -v
docker-compose up -d
```

### Spark Streaming Checkpoint Issues

If streaming app fails to start:
```bash
# Remove old checkpoints
docker-compose down
docker volume rm eventflow_spark-checkpoint
docker-compose up -d
```

### Out of Memory

Increase Docker memory:
- Docker Desktop: Settings → Resources → Memory (set to at least 4GB)
- Reduce Spark memory in Dockerfiles: `-Xmx2g` → `-Xmx1g`

### No Data in Database

1. Check if producer is running:
```bash
docker-compose logs producer | grep "Order sent"
```

2. Check if streaming app is processing:
```bash
docker-compose logs spark-streaming | grep "Batch"
```

3. Verify Kafka topics have data:
```bash
docker exec eventflow-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic orders \
  --from-beginning \
  --max-messages 5
```

## Cleanup

```bash
# Stop all services
docker-compose down

# Remove all data volumes
docker-compose down -v

# Remove all images
docker-compose down --rmi all
```

## Next Steps

- Add schema registry (Confluent Schema Registry or Apicurio)
- Implement authentication and authorization
- Add more complex event patterns
- Implement exactly-once semantics
- Add CDC (Change Data Capture) with Debezium
- Implement data quality checks

## License

MIT License - feel free to use this for learning and demonstration purposes.

## Contributing

This is a demonstration project. Feel free to fork and extend!

## Author

Built to showcase Kafka and Spark capabilities for event-driven architectures.
