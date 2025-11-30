# EventFlow Architecture Documentation

## System Overview

EventFlow is a real-time event processing platform demonstrating Kafka-centric architecture with Spark for stream and batch processing.

## Component Architecture

```
┌────────────────────────────────────────────────────────────────────────────┐
│                              CLIENT LAYER                                   │
│                                                                             │
│  ┌─────────────────────┐  ┌─────────────────────┐  ┌──────────────────┐   │
│  │  Event Generators   │  │    PostgreSQL       │  │  Kafka Admin     │   │
│  │  (Java Producers)   │  │    Clients          │  │  Tools           │   │
│  └─────────────────────┘  └─────────────────────┘  └──────────────────┘   │
└────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                           INGESTION LAYER                                   │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                          Apache Kafka                                 │  │
│  │                                                                       │  │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐     │  │
│  │  │  user-events    │  │  product-view   │  │     orders      │     │  │
│  │  │                 │  │                 │  │                 │     │  │
│  │  │  Partition 0    │  │  Partition 0    │  │  Partition 0    │     │  │
│  │  │  Partition 1    │  │  Partition 1    │  │  Partition 1    │     │  │
│  │  │  Partition 2    │  │  Partition 2    │  │  Partition 2    │     │  │
│  │  │                 │  │                 │  │                 │     │  │
│  │  │  Key: userId    │  │  Key: userId    │  │  Key: userId    │     │  │
│  │  │  Retention: 7d  │  │  Retention: 7d  │  │  Retention: 30d │     │  │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────┘     │  │
│  │                                                                       │  │
│  │  Config: acks=all, idempotence=true, replication=1                   │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                          PROCESSING LAYER                                   │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                   Spark Structured Streaming                          │  │
│  │                                                                       │  │
│  │  ┌────────────────────────────────────────────────────────────────┐  │  │
│  │  │  Query 1: Order Processing                                     │  │  │
│  │  │  ├─ Read: orders topic                                         │  │  │
│  │  │  ├─ Transform: Parse JSON, extract fields                      │  │  │
│  │  │  ├─ Watermark: 10 minutes                                      │  │  │
│  │  │  ├─ Output: Append mode                                        │  │  │
│  │  │  └─ Sink: PostgreSQL (orders_raw)                              │  │  │
│  │  └────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                       │  │
│  │  ┌────────────────────────────────────────────────────────────────┐  │  │
│  │  │  Query 2: Revenue Aggregation                                  │  │  │
│  │  │  ├─ Read: orders topic                                         │  │  │
│  │  │  ├─ Transform: Parse JSON                                      │  │  │
│  │  │  ├─ Watermark: 10 minutes                                      │  │  │
│  │  │  ├─ Window: 5-minute tumbling                                  │  │  │
│  │  │  ├─ Group By: window, currency                                 │  │  │
│  │  │  ├─ Aggregate: sum(price), count(*), avg(price)                │  │  │
│  │  │  ├─ Output: Update mode                                        │  │  │
│  │  │  └─ Sink: PostgreSQL (revenue_by_currency_5min)                │  │  │
│  │  └────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                       │  │
│  │  ┌────────────────────────────────────────────────────────────────┐  │  │
│  │  │  Query 3: Conversion Funnel                                    │  │  │
│  │  │  ├─ Read: product-view, orders topics                          │  │  │
│  │  │  ├─ Transform: Parse JSON for both streams                     │  │  │
│  │  │  ├─ Watermark: 10 minutes on both                              │  │  │
│  │  │  ├─ Window: 5-minute tumbling on both                          │  │  │
│  │  │  ├─ Aggregate: count views, count orders per user/window       │  │  │
│  │  │  ├─ Join: Full outer join on (window, userId)                  │  │  │
│  │  │  ├─ Output: Update mode                                        │  │  │
│  │  │  └─ Sink: PostgreSQL (conversion_funnel_5min)                  │  │  │
│  │  └────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                       │  │
│  │  Trigger: ProcessingTime(10 seconds)                                  │  │
│  │  Checkpoint: /tmp/checkpoint                                          │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                           STORAGE LAYER                                     │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                          PostgreSQL                                   │  │
│  │                                                                       │  │
│  │  ┌────────────────────────────────────────────────────────────────┐  │  │
│  │  │  orders_raw                                                     │  │  │
│  │  │  ├─ order_id (PK)                                               │  │  │
│  │  │  ├─ user_id                                                     │  │  │
│  │  │  ├─ product_id                                                  │  │  │
│  │  │  ├─ price                                                       │  │  │
│  │  │  ├─ currency                                                    │  │  │
│  │  │  └─ timestamp                                                   │  │  │
│  │  └────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                       │  │
│  │  ┌────────────────────────────────────────────────────────────────┐  │  │
│  │  │  revenue_by_currency_5min                                       │  │  │
│  │  │  ├─ window_start (PK)                                           │  │  │
│  │  │  ├─ window_end                                                  │  │  │
│  │  │  ├─ currency (PK)                                               │  │  │
│  │  │  ├─ total_revenue                                               │  │  │
│  │  │  ├─ order_count                                                 │  │  │
│  │  │  └─ avg_order_value                                             │  │  │
│  │  └────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                       │  │
│  │  ┌────────────────────────────────────────────────────────────────┐  │  │
│  │  │  conversion_funnel_5min                                         │  │  │
│  │  │  ├─ window_start (PK)                                           │  │  │
│  │  │  ├─ window_end                                                  │  │  │
│  │  │  ├─ user_id (PK)                                                │  │  │
│  │  │  ├─ view_count                                                  │  │  │
│  │  │  └─ order_count                                                 │  │  │
│  │  └────────────────────────────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                          ANALYTICS LAYER                                    │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                      Spark Batch Analytics                            │  │
│  │                                                                       │  │
│  │  ┌─────────────────────────────────────────────────────────────┐    │  │
│  │  │  Analysis 1: Top Revenue by Currency                        │    │  │
│  │  │  ├─ Read: revenue_by_currency_5min                          │    │  │
│  │  │  ├─ Group: currency                                          │    │  │
│  │  │  ├─ Aggregate: sum(revenue), sum(orders), avg(avg_value)    │    │  │
│  │  │  └─ Sort: total_revenue DESC                                │    │  │
│  │  └─────────────────────────────────────────────────────────────┘    │  │
│  │                                                                       │  │
│  │  ┌─────────────────────────────────────────────────────────────┐    │  │
│  │  │  Analysis 2: Conversion Rate Analysis                       │    │  │
│  │  │  ├─ Read: conversion_funnel_5min                            │    │  │
│  │  │  ├─ Group: user_id                                          │    │  │
│  │  │  ├─ Calculate: orders / views * 100                         │    │  │
│  │  │  └─ Sort: conversion_rate DESC                              │    │  │
│  │  └─────────────────────────────────────────────────────────────┘    │  │
│  │                                                                       │  │
│  │  ┌─────────────────────────────────────────────────────────────┐    │  │
│  │  │  Analysis 3: User Segmentation                              │    │  │
│  │  │  ├─ Read: orders_raw                                        │    │  │
│  │  │  ├─ Group: user_id                                          │    │  │
│  │  │  ├─ Segment: VIP (>$1K), Premium (>$500), Regular, New     │    │  │
│  │  │  └─ Aggregate: per-segment metrics                         │    │  │
│  │  └─────────────────────────────────────────────────────────────┘    │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────┘
```

## Data Flow Diagram

```
Event Generation → Kafka Topic → Spark Streaming → PostgreSQL → Batch Analytics

1. Producer generates events every 100ms
   ├─ 40% user events (LOGIN, LOGOUT, SIGNUP)
   ├─ 40% product views
   └─ 20% orders

2. Events written to Kafka
   ├─ Partitioned by userId
   ├─ Acknowledged by all replicas (acks=all)
   └─ Deduplicated (idempotence=true)

3. Spark Streaming consumes
   ├─ Reads from latest offset
   ├─ Processes every 10 seconds
   ├─ Applies 10-minute watermark
   └─ Writes to PostgreSQL

4. PostgreSQL stores
   ├─ Raw orders (append only)
   ├─ Aggregated revenue (update mode)
   └─ Conversion metrics (update mode)

5. Batch Analytics queries
   ├─ Reads from PostgreSQL tables
   ├─ Performs complex SQL aggregations
   └─ Outputs to console/logs
```

## Event Timeline Example

```
Time: 11:00:00
├─ Producer: Generates LOGIN event for user u123
├─ Kafka: Writes to user-events topic, partition 1 (hash(u123) % 3)
├─ Spark: Reads event (if subscribed to user-events)
└─ Window: Assigned to [11:00:00 - 11:05:00) window

Time: 11:00:01
├─ Producer: Generates VIEW event for user u123, product p42
├─ Kafka: Writes to product-view topic, partition 1 (same as u123)
├─ Spark: Reads event
└─ Window: Assigned to [11:00:00 - 11:05:00) window

Time: 11:00:05
├─ Producer: Generates ORDER event for user u123, product p42, price 149.99 EUR
├─ Kafka: Writes to orders topic, partition 1 (same as u123)
├─ Spark: Reads event
├─ Window: Assigned to [11:00:00 - 11:05:00) window
├─ Query 1: Writes to orders_raw table
├─ Query 2: Adds to revenue aggregation for [11:00:00 - 11:05:00), EUR
└─ Query 3: Increments order count for u123 in [11:00:00 - 11:05:00)

Time: 11:05:00
├─ Window [11:00:00 - 11:05:00) closes
├─ Spark continues processing until watermark (11:05:00 - 10 min = 10:55:00)
└─ Late events with timestamp < 10:55:00 are dropped

Time: 11:15:00
├─ Watermark advances to 11:05:00
├─ Window [11:00:00 - 11:05:00) finalized
├─ Spark writes final aggregations to PostgreSQL
└─ Window results available for batch queries
```

## Component Details

### Producer Service (ProducerApp.java)

**Responsibilities:**
- Generate realistic user events, product views, and orders
- Serialize events to JSON
- Publish to Kafka with proper keying
- Handle retries and errors

**Key Classes:**
- `ProducerApp`: Main application, Kafka producer setup
- `UserEventGenerator`: Generates user events (40% power users)
- `ProductViewGenerator`: Generates product views (40% popular products)
- `OrderGenerator`: Generates orders with realistic prices

**Configuration:**
- Bootstrap servers: From environment
- Delay between events: Configurable (default 100ms)
- Acks: all
- Idempotence: enabled

### Spark Streaming (StreamingApp.java)

**Responsibilities:**
- Consume from Kafka topics
- Parse JSON events
- Apply windowed aggregations
- Join multiple streams
- Write results to PostgreSQL

**Streaming Queries:**
1. **Order Processing**: Raw order ingestion
2. **Revenue Aggregation**: 5-minute windowed revenue by currency
3. **Conversion Funnel**: Join views and orders per user

**Trigger Interval:** 10 seconds
**Watermark:** 10 minutes
**Checkpoint Location:** /tmp/checkpoint

### Spark Batch (BatchAnalyticsApp.java)

**Responsibilities:**
- Read aggregated data from PostgreSQL
- Perform complex SQL analytics
- Calculate business metrics
- Output insights to console

**Analyses:**
1. Top revenue by currency
2. Conversion rates per user
3. Revenue trends by time
4. User segmentation (VIP, Premium, Regular, New)

### Kafka Cluster

**Topology:**
- 1 ZooKeeper node (dev setup)
- 1 Kafka broker (dev setup)
- 3 topics × 3 partitions = 9 total partitions

**Production Recommendation:**
- 3 ZooKeeper nodes (quorum)
- 3+ Kafka brokers
- Replication factor 3
- Min in-sync replicas 2

### PostgreSQL Database

**Tables:**
1. `orders_raw`: Raw order records (append only)
2. `revenue_by_currency_5min`: Windowed revenue aggregations
3. `conversion_funnel_5min`: View-to-order conversion metrics

**Indices:**
- Primary keys on order_id, (window_start, currency), (window_start, user_id)
- Timestamps for time-range queries

## Scalability Considerations

### Horizontal Scaling

**Kafka:**
- Add more brokers → distribute partitions
- Add more partitions → increase parallelism
- Current: 3 partitions → scale to 6, 12, 24

**Spark Streaming:**
- Add more executors → parallel processing
- Partitions limit: max consumers = partition count
- Current: 1 executor → scale to 3 (one per partition)

**PostgreSQL:**
- Read replicas for batch analytics
- Partition tables by time (daily, weekly)
- Archive old data to S3/data lake

### Vertical Scaling

**Kafka:**
- More memory → larger page cache → fewer disk reads
- More CPU → faster compression/decompression
- Faster disks → lower latency

**Spark:**
- More memory → larger shuffles, fewer spills
- More CPU → faster processing
- Current: 2GB heap → scale to 4GB, 8GB

## Fault Tolerance

### Kafka
- Leader/replica architecture
- Automatic leader election
- Offset commits in __consumer_offsets topic

### Spark Streaming
- Checkpointing for state recovery
- Exactly-once semantics with idempotent writes
- WAL for metadata

### PostgreSQL
- WAL for crash recovery
- Point-in-time recovery (PITR)
- Replication for HA

## Monitoring Points

1. **Producer:**
   - Events produced per second
   - Error rate
   - Average latency

2. **Kafka:**
   - Broker disk usage
   - Consumer lag
   - Under-replicated partitions

3. **Spark Streaming:**
   - Processing time vs batch interval
   - Input rate
   - Number of records per batch

4. **PostgreSQL:**
   - Connection count
   - Query performance
   - Table sizes

## Performance Characteristics

**Throughput:**
- Producer: ~10 events/second (configurable)
- Kafka: 1000+ events/second (broker limit)
- Spark: Limited by PostgreSQL write throughput

**Latency:**
- Producer → Kafka: <10ms
- Kafka → Spark: ~10s (trigger interval)
- Spark → PostgreSQL: <1s
- End-to-end: ~11-12 seconds (configurable)

**Storage:**
- Kafka: ~1GB per day (depends on retention)
- PostgreSQL: ~100MB per day (depends on data volume)

## Extension Points

1. **Add new event types:**
   - Create new topic
   - Add generator class
   - Update producer

2. **Add new aggregations:**
   - Add streaming query in StreamingApp
   - Create PostgreSQL table
   - Update batch analytics

3. **Add real-time dashboards:**
   - Query PostgreSQL from Grafana
   - Use Kafka Connect to sync to BI tools
   - Add REST API layer

4. **Add data quality:**
   - Schema validation with Schema Registry
   - Data quality checks in Spark
   - Alert on anomalies

## Summary

EventFlow demonstrates a production-ready event-driven architecture with:
- Strong durability and ordering guarantees
- Real-time and batch processing capabilities
- Scalable, fault-tolerant design
- Clear separation of concerns
- Comprehensive monitoring and observability
