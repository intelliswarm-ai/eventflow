# EventFlow Quick Start Guide

Get the EventFlow platform up and running in 5 minutes!

## Prerequisites Check

```bash
# Check Docker
docker --version  # Should be 20.10+

# Check Docker Compose
docker-compose --version  # Should be 2.0+

# Check available memory
docker info | grep Memory  # Should have at least 4GB
```

## Step 1: Start Services (2 minutes)

```bash
# Clone or navigate to the project directory
cd eventflow

# Start all services
./scripts/start.sh

# Or manually:
docker-compose up -d --build
```

Wait for all services to start. You should see:
- eventflow-zookeeper
- eventflow-kafka
- eventflow-postgres
- eventflow-producer
- eventflow-spark-streaming

## Step 2: Verify Data Flow (1 minute)

```bash
# Check producer is generating events
docker-compose logs --tail=20 producer

# You should see output like:
# Order sent: orderId=o123, userId=u5, productId=p42, price=149.99 USD

# Check Kafka topics exist
docker exec eventflow-kafka kafka-topics --bootstrap-server localhost:9092 --list

# Should show:
# orders
# product-view
# user-events
```

## Step 3: View Real-time Processing (1 minute)

```bash
# Watch Spark streaming process data
docker-compose logs --tail=50 -f spark-streaming

# Look for output showing:
# - Batch written messages
# - Revenue aggregation results
# - Conversion funnel data
```

## Step 4: View Grafana Dashboards (30 seconds)

```bash
# Open Grafana in your browser
open http://localhost:3000

# Login credentials:
# Username: admin
# Password: admin

# Navigate to: Dashboards → EventFlow folder

# Explore 3 pre-built dashboards:
# 1. Real-Time Pipeline - Live orders, revenue, trends
# 2. Kafka Health & Performance - Broker metrics, throughput
# 3. Revenue Analytics - Business insights, top products/customers
```

Dashboards auto-refresh every 5-10 seconds showing real-time metrics!

## Step 5: Query the Database (1 minute)

```bash
# Use the helper script
./scripts/view-data.sh

# Or connect directly to PostgreSQL
docker exec -it eventflow-postgres psql -U analytics -d analytics

# Run queries:
SELECT COUNT(*) FROM orders_raw;
SELECT * FROM revenue_by_currency_5min ORDER BY window_start DESC LIMIT 5;
SELECT * FROM conversion_funnel_5min LIMIT 10;
```

## Step 6: Run Batch Analytics (Optional)

```bash
# Wait for some data to accumulate (1-2 minutes)
# Then run batch analytics
docker-compose --profile batch up spark-batch

# View comprehensive analytics output
```

## What's Happening?

1. **Producer** generates realistic events:
   - User events (login, logout, signup)
   - Product views
   - Orders
   - Sends to Kafka topics at ~10 events/second

2. **Kafka** distributes events:
   - 3 topics with 3 partitions each
   - Events keyed by userId for ordering
   - Persistent storage for replay

3. **Spark Streaming** processes in real-time:
   - Reads from all 3 topics
   - Performs windowed aggregations (5-minute windows)
   - Joins streams (views + orders = conversion)
   - Writes results to PostgreSQL

4. **PostgreSQL** stores:
   - Raw orders
   - Revenue aggregations by currency
   - Conversion funnel metrics

5. **Spark Batch** analyzes:
   - Top revenue by currency
   - User segmentation
   - Conversion rates
   - Time-based trends

## Common Commands

```bash
# View logs
docker-compose logs -f producer
docker-compose logs -f spark-streaming
docker-compose logs -f kafka

# Check service health
docker-compose ps

# Restart a service
docker-compose restart producer

# Stop everything
docker-compose down

# Stop and remove all data
docker-compose down -v

# View Kafka messages
docker exec eventflow-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic orders \
  --from-beginning \
  --max-messages 10
```

## Sample Queries

### Recent Orders
```sql
SELECT
    order_id,
    user_id,
    product_id,
    price,
    currency,
    timestamp
FROM orders_raw
ORDER BY timestamp DESC
LIMIT 10;
```

### Revenue Summary
```sql
SELECT
    currency,
    SUM(total_revenue) as total,
    SUM(order_count) as orders,
    AVG(avg_order_value) as avg_value
FROM revenue_by_currency_5min
GROUP BY currency
ORDER BY total DESC;
```

### Top Users
```sql
SELECT
    user_id,
    SUM(view_count) as views,
    SUM(order_count) as orders,
    ROUND(SUM(order_count) * 100.0 / NULLIF(SUM(view_count), 0), 2) as conversion_rate
FROM conversion_funnel_5min
GROUP BY user_id
HAVING SUM(view_count) > 0
ORDER BY orders DESC
LIMIT 10;
```

## Troubleshooting

### Services won't start
```bash
# Check Docker resources
docker system df
docker system prune  # Clean up if needed

# Check ports aren't in use
lsof -i :9092  # Kafka
lsof -i :5432  # PostgreSQL
```

### No data in database
```bash
# Wait 30 seconds after startup
# Check producer logs
docker-compose logs producer | grep "Order sent"

# Check streaming logs
docker-compose logs spark-streaming | grep "Batch"

# Restart services
docker-compose restart producer spark-streaming
```

### Out of memory
```bash
# Increase Docker memory (Docker Desktop > Settings > Resources)
# Or reduce event rate
docker-compose stop producer
# Edit docker-compose.yml: EVENT_DELAY_MS: 500 (slower)
docker-compose up -d producer
```

## Next Steps

- Explore the full README.md for detailed architecture
- Read MONITORING.md for Grafana dashboard customization
- Read KAFKA_CONFIG.md for Kafka configuration details
- Modify event generators in producer/src/main/java/
- Add custom analytics in spark-batch/src/main/java/
- Experiment with window sizes and watermarks
- Add more topics and event types
- Create custom Grafana dashboards for your metrics

## Success Indicators

After 2-3 minutes of running, you should see:

1. Producer logs showing "Order sent" messages
2. Kafka topics with messages (check with kafka-console-consumer)
3. Spark streaming logs showing batch completions
4. PostgreSQL tables with rows:
   - orders_raw: hundreds of rows
   - revenue_by_currency_5min: multiple windows
   - conversion_funnel_5min: user metrics

Enjoy exploring EventFlow!
