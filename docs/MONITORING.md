# EventFlow Monitoring & Visualization

This guide covers the comprehensive monitoring and visualization capabilities built into EventFlow using Grafana, Prometheus, and Kafka JMX metrics.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Quick Start](#quick-start)
- [Dashboards](#dashboards)
- [Data Sources](#data-sources)
- [Metrics](#metrics)
- [Customization](#customization)
- [Troubleshooting](#troubleshooting)

## Overview

EventFlow includes a complete monitoring stack that showcases the strengths of the Kafka and Spark architecture:

- **Grafana** - Visualization and dashboarding platform
- **Prometheus** - Time-series metrics database
- **JMX Exporter** - Exposes Kafka JMX metrics to Prometheus
- **PostgreSQL** - Stores processed analytics data

This monitoring setup provides real-time visibility into:
- Event pipeline throughput and latency
- Kafka broker health and performance
- Revenue analytics and business metrics
- Spark streaming processing statistics

## Architecture

```
┌─────────────┐
│   Kafka     │──┐
│  (JMX 9999) │  │
└─────────────┘  │
                 │
                 ├──► ┌──────────────┐      ┌──────────────┐      ┌──────────┐
                 │    │ JMX Exporter │─────►│  Prometheus  │─────►│ Grafana  │
                 │    │  (port 5556) │      │ (port 9090)  │      │(port 3000)│
                 │    └──────────────┘      └──────────────┘      └─────┬────┘
                 │                                                        │
                 │                                                        │
┌─────────────┐  │                          ┌──────────────┐            │
│ PostgreSQL  │──┼─────────────────────────►│  PostgreSQL  │◄───────────┘
│ (port 5432) │  │                          │ Data Source  │
└─────────────┘  │                          └──────────────┘
                 │
```

## Quick Start

### 1. Start the Monitoring Stack

The monitoring services are included in the main docker-compose.yml file:

```bash
docker-compose up -d
```

This starts:
- **Grafana** on http://localhost:3000
- **Prometheus** on http://localhost:9090
- **JMX Exporter** on http://localhost:5556

### 2. Access Grafana

1. Open http://localhost:3000 in your browser
2. Login with default credentials:
   - **Username**: `admin`
   - **Password**: `admin`
3. Navigate to **Dashboards** → **EventFlow** folder

### 3. View Pre-configured Dashboards

Three dashboards are automatically provisioned:
- **EventFlow - Real-Time Pipeline** - Live event processing metrics
- **EventFlow - Kafka Health & Performance** - Kafka broker statistics
- **EventFlow - Revenue Analytics** - Business intelligence and revenue trends

## Dashboards

### 1. Real-Time Pipeline Dashboard

**Purpose**: Monitor live event processing, orders, and revenue in real-time

**Key Metrics**:
- Total orders processed
- Total revenue by currency
- Average order value
- Unique users
- Orders per minute
- Revenue trends by currency
- 5-minute windowed aggregations
- Revenue distribution pie chart

**Refresh Rate**: 5 seconds

**Best For**: Operational monitoring, real-time KPI tracking

**Access**: http://localhost:3000/d/eventflow-realtime

---

### 2. Kafka Health & Performance Dashboard

**Purpose**: Monitor Kafka broker health, throughput, and latency

**Key Metrics**:
- Messages in rate by topic
- Bytes in/out rate by topic
- Produce request latency
- Fetch request latency
- Total partition count
- Request rate by type (Produce, Fetch, Metadata, etc.)

**Refresh Rate**: 5 seconds

**Best For**: Infrastructure monitoring, capacity planning, troubleshooting

**Access**: http://localhost:3000/d/eventflow-kafka

**Data Source**: Prometheus (Kafka JMX metrics)

---

### 3. Revenue Analytics Dashboard

**Purpose**: Business intelligence and revenue analysis

**Key Metrics**:
- Revenue summary by currency
- Revenue trends (5-minute windows)
- Top 20 customers by revenue
- Top 20 products by revenue
- Order volume by currency
- Average order value trends

**Refresh Rate**: 10 seconds

**Best For**: Business analysis, customer segmentation, product performance

**Access**: http://localhost:3000/d/eventflow-revenue

**Data Source**: PostgreSQL Analytics Database

## Data Sources

EventFlow uses two data sources configured automatically in Grafana:

### 1. PostgreSQL-Analytics (Default)

- **Type**: PostgreSQL
- **URL**: postgres:5432
- **Database**: analytics
- **User**: analytics
- **Purpose**: Query processed data from Spark Streaming

**Tables Available**:
- `orders_raw` - Raw order events from Kafka
- `revenue_by_currency_5min` - 5-minute revenue aggregations
- `conversion_funnel_5min` - User conversion metrics (if enabled)

**Example Query**:
```sql
SELECT
  DATE_TRUNC('minute', timestamp) as time,
  COUNT(*) as "Orders per Minute"
FROM orders_raw
WHERE timestamp > NOW() - INTERVAL '1 hour'
GROUP BY time
ORDER BY time;
```

### 2. Prometheus

- **Type**: Prometheus
- **URL**: http://prometheus:9090
- **Purpose**: Time-series metrics from Kafka JMX

**Key Metrics Available**:
- `kafka_server_brokertopicmetrics_messagesinpersec` - Message ingestion rate
- `kafka_server_brokertopicmetrics_bytesinpersec` - Bytes in rate
- `kafka_server_brokertopicmetrics_bytesoutpersec` - Bytes out rate
- `kafka_network_requestmetrics_totaltimems` - Request latency
- `kafka_server_replicamanager_partitioncount` - Partition count

**Example Query**:
```promql
rate(kafka_server_brokertopicmetrics_messagesinpersec[1m])
```

## Metrics

### Kafka Metrics (via JMX)

**Broker Topic Metrics**:
- `MessagesInPerSec` - Rate of messages produced
- `BytesInPerSec` - Rate of bytes produced
- `BytesOutPerSec` - Rate of bytes consumed

**Request Metrics**:
- `TotalTimeMs` - End-to-end request latency
- `RequestsPerSec` - Request rate by type

**Replica Manager**:
- `PartitionCount` - Total partitions on broker
- `LeaderCount` - Partitions where this broker is leader

### Application Metrics (via PostgreSQL)

**Real-Time Metrics**:
- Orders per minute
- Revenue per minute
- Average order value
- Unique users

**Windowed Aggregations** (5-minute tumbling windows):
- Total revenue by currency
- Order count by currency
- Average order value by currency

**Business Metrics**:
- Customer lifetime value
- Product performance
- Currency distribution

## Customization

### Creating Custom Dashboards

1. In Grafana, click **+ → Dashboard**
2. Add a new panel
3. Select a data source (PostgreSQL or Prometheus)
4. Write your query
5. Configure visualization type
6. Save the dashboard to the **EventFlow** folder

### Adding New Metrics

**For Kafka Metrics**:

1. Edit `monitoring/jmx-exporter-config.yml`
2. Add new JMX patterns to `whitelistObjectNames`
3. Add transformation rules
4. Restart the JMX exporter:
   ```bash
   docker-compose restart kafka-jmx-exporter
   ```

**For Application Metrics**:

1. Create new Spark SQL aggregations in `StreamingApp.java`
2. Write results to new PostgreSQL tables
3. Query the tables in Grafana using SQL

### Exporting Dashboards

To save a dashboard for version control:

```bash
# From Grafana UI: Dashboard → Share → Export → Save to file
# Place the JSON in: monitoring/grafana/dashboards/
```

## Troubleshooting

### Grafana Not Showing Data

**Check Data Source Connection**:
1. Go to Configuration → Data Sources
2. Click on PostgreSQL-Analytics
3. Scroll down and click **Save & Test**
4. Should show "Database Connection OK"

**Check Prometheus Connection**:
1. Go to Configuration → Data Sources
2. Click on Prometheus
3. Scroll down and click **Save & Test**
4. Should show "Successfully queried the Prometheus API"

### No Kafka Metrics in Prometheus

**Verify JMX Exporter**:
```bash
# Check JMX exporter is running
docker ps | grep jmx-exporter

# Check JMX exporter logs
docker logs eventflow-kafka-jmx-exporter

# Test metrics endpoint
curl http://localhost:5556/metrics
```

**Verify Kafka JMX Port**:
```bash
# Check Kafka JMX port is accessible
docker exec eventflow-kafka nc -zv localhost 9999
```

### Empty PostgreSQL Tables

**Check Spark Streaming**:
```bash
# Check if Spark Streaming is running
docker logs eventflow-spark-streaming

# Check if data is being written
docker exec eventflow-postgres psql -U analytics -d analytics -c "SELECT COUNT(*) FROM orders_raw;"
```

**Check Kafka Topics**:
```bash
# Verify messages are in Kafka
docker exec eventflow-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic orders \
  --from-beginning \
  --max-messages 5
```

### Dashboard Shows "No Data"

1. **Check Time Range**: Ensure the dashboard time range includes recent data
2. **Check Refresh Rate**: Dashboards auto-refresh every 5-10 seconds
3. **Verify Producer**: Ensure the producer is running and generating events
4. **Check Queries**: Use Explore mode to test queries individually

### Prometheus Not Scraping Metrics

**Check Prometheus Configuration**:
```bash
# View Prometheus config
docker exec eventflow-prometheus cat /etc/prometheus/prometheus.yml

# Check Prometheus targets
# Open: http://localhost:9090/targets
```

**Verify Scrape Interval**:
- Default scrape interval is 15 seconds
- Metrics should appear within 30 seconds of startup

## Advanced Features

### Alerting

Configure alerts in Grafana:

1. Edit a panel
2. Click **Alert** tab
3. Create alert rule
4. Configure notification channel (email, Slack, etc.)

### Variables and Templating

Create dynamic dashboards with variables:

```sql
-- Create a currency variable
SELECT DISTINCT currency FROM orders_raw;

-- Use in query
SELECT * FROM orders_raw WHERE currency = '$currency';
```

### Annotations

Add event annotations to dashboards:

1. Dashboard Settings → Annotations
2. Add annotation query
3. Visualize deployments, incidents, etc. on graphs

## Best Practices

1. **Monitor Both Infrastructure and Application Metrics**
   - Use Prometheus for infrastructure (Kafka, system resources)
   - Use PostgreSQL for application/business metrics

2. **Set Appropriate Refresh Rates**
   - Real-time dashboards: 5-10 seconds
   - Analytical dashboards: 30-60 seconds
   - Historical dashboards: No auto-refresh

3. **Use Time Windows Wisely**
   - Operational monitoring: Last 15-60 minutes
   - Business analytics: Last 24 hours to 7 days
   - Trend analysis: Last 30-90 days

4. **Create Focused Dashboards**
   - One dashboard per persona (ops, business, dev)
   - 5-8 panels per dashboard
   - Clear, descriptive titles

5. **Document Custom Metrics**
   - Add descriptions to panels
   - Use consistent naming conventions
   - Include units and thresholds

## Next Steps

- **Add More Metrics**: Instrument your producer with custom metrics
- **Set Up Alerts**: Configure Grafana alerts for critical thresholds
- **Create User Dashboards**: Build role-specific dashboards
- **Export Reports**: Use Grafana's reporting feature for scheduled reports
- **Integrate with Incident Management**: Connect alerts to PagerDuty, Opsgenie, etc.

## Resources

- [Grafana Documentation](https://grafana.com/docs/)
- [Prometheus Query Language](https://prometheus.io/docs/prometheus/latest/querying/basics/)
- [Kafka JMX Metrics](https://kafka.apache.org/documentation/#monitoring)
- [PostgreSQL Grafana Plugin](https://grafana.com/docs/grafana/latest/datasources/postgres/)
