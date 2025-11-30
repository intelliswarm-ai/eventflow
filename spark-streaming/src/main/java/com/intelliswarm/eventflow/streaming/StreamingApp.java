package com.intelliswarm.eventflow.streaming;

import org.apache.spark.sql.*;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.apache.spark.sql.streaming.Trigger;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.apache.spark.sql.functions.*;

public class StreamingApp {
    private static final Logger logger = LoggerFactory.getLogger(StreamingApp.class);

    private final SparkSession spark;
    private final String kafkaBootstrapServers;
    private final String postgresUrl;
    private final String postgresUser;
    private final String postgresPassword;

    public StreamingApp(String kafkaBootstrapServers, String postgresUrl,
                       String postgresUser, String postgresPassword) {
        this.kafkaBootstrapServers = kafkaBootstrapServers;
        this.postgresUrl = postgresUrl;
        this.postgresUser = postgresUser;
        this.postgresPassword = postgresPassword;

        this.spark = SparkSession.builder()
                .appName("EventFlow Streaming")
                .master("local[*]")
                .config("spark.sql.streaming.checkpointLocation", "/tmp/checkpoint")
                .config("spark.sql.shuffle.partitions", "4")
                .getOrCreate();

        logger.info("Spark session created");
    }

    public void start() throws Exception {
        logger.info("Starting streaming queries...");

        initializeTables();

        StreamingQuery ordersQuery = processOrders();
        StreamingQuery revenueQuery = processRevenueAggregation();
        StreamingQuery conversionQuery = processConversionFunnel();
        // StreamingQuery recommendationsQuery = processProductRecommendations();  // Temporarily disabled
        // StreamingQuery sessionsQuery = processSessionAnalytics();  // Temporarily disabled

        ordersQuery.awaitTermination();
        revenueQuery.awaitTermination();
        conversionQuery.awaitTermination();
        // recommendationsQuery.awaitTermination();
        // sessionsQuery.awaitTermination();
    }

    private void initializeTables() {
        logger.info("Initializing PostgreSQL tables...");

        String createOrdersTable = "CREATE TABLE IF NOT EXISTS orders_raw (" +
                "order_id VARCHAR(50) PRIMARY KEY, " +
                "user_id VARCHAR(50), " +
                "product_id VARCHAR(50), " +
                "price DOUBLE PRECISION, " +
                "currency VARCHAR(10), " +
                "timestamp TIMESTAMP)";

        String createRevenueTable = "CREATE TABLE IF NOT EXISTS revenue_by_currency_5min (" +
                "window_start TIMESTAMP, " +
                "window_end TIMESTAMP, " +
                "currency VARCHAR(10), " +
                "total_revenue DOUBLE PRECISION, " +
                "order_count BIGINT, " +
                "avg_order_value DOUBLE PRECISION, " +
                "PRIMARY KEY (window_start, currency))";

        String createConversionTable = "CREATE TABLE IF NOT EXISTS conversion_funnel_5min (" +
                "window_start TIMESTAMP, " +
                "window_end TIMESTAMP, " +
                "user_id VARCHAR(50), " +
                "view_count BIGINT, " +
                "order_count BIGINT, " +
                "conversion_rate DOUBLE PRECISION, " +
                "PRIMARY KEY (window_start, user_id))";

        String createRecommendationsTable = "CREATE TABLE IF NOT EXISTS product_recommendations (" +
                "product_id VARCHAR(50), " +
                "recommended_product_id VARCHAR(50), " +
                "score DOUBLE PRECISION, " +
                "co_purchase_count BIGINT, " +
                "last_updated TIMESTAMP, " +
                "PRIMARY KEY (product_id, recommended_product_id))";

        String createSessionsTable = "CREATE TABLE IF NOT EXISTS user_sessions_5min (" +
                "window_start TIMESTAMP, " +
                "window_end TIMESTAMP, " +
                "user_id VARCHAR(50), " +
                "session_duration_seconds BIGINT, " +
                "events_count BIGINT, " +
                "views_count BIGINT, " +
                "orders_count BIGINT, " +
                "has_conversion BOOLEAN, " +
                "PRIMARY KEY (window_start, user_id))";

        executeSQL(createOrdersTable);
        executeSQL(createRevenueTable);
        executeSQL(createConversionTable);
        executeSQL(createRecommendationsTable);
        executeSQL(createSessionsTable);

        logger.info("Tables initialized successfully");
    }

    private void executeSQL(String sql) {
        Properties props = new Properties();
        props.setProperty("user", postgresUser);
        props.setProperty("password", postgresPassword);
        props.setProperty("driver", "org.postgresql.Driver");

        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(postgresUrl, props);
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (Exception e) {
            logger.error("Error executing SQL: {}", sql, e);
            throw new RuntimeException(e);
        }
    }

    private StreamingQuery processOrders() throws Exception {
        logger.info("Starting orders processing...");

        StructType orderSchema = new StructType()
                .add("eventType", DataTypes.StringType)
                .add("orderId", DataTypes.StringType)
                .add("userId", DataTypes.StringType)
                .add("productId", DataTypes.StringType)
                .add("price", DataTypes.DoubleType)
                .add("currency", DataTypes.StringType)
                .add("timestamp", DataTypes.DoubleType);  // Changed to Double to match Unix timestamp

        Dataset<Row> kafkaOrders = spark.readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", kafkaBootstrapServers)
                .option("subscribe", "orders")
                .option("startingOffsets", "latest")
                .load();

        Dataset<Row> orders = kafkaOrders
                .selectExpr("CAST(value AS STRING) as json")
                .select(from_json(col("json"), orderSchema).as("data"))
                .select("data.*")
                .withColumn("timestamp", expr("CAST(timestamp AS TIMESTAMP)"))  // Convert Unix timestamp to SQL timestamp
                .withWatermark("timestamp", "10 minutes");

        return orders
                .drop("eventType")  // Drop eventType as it's not needed in the table
                .withColumnRenamed("orderId", "order_id")
                .withColumnRenamed("userId", "user_id")
                .withColumnRenamed("productId", "product_id")
                .writeStream()
                .outputMode("append")
                .foreachBatch((batchDF, batchId) -> {
                    if (batchDF.count() > 0) {
                        batchDF.write()
                                .mode(SaveMode.Append)
                                .format("jdbc")
                                .option("url", postgresUrl)
                                .option("dbtable", "orders_raw")
                                .option("user", postgresUser)
                                .option("password", postgresPassword)
                                .option("driver", "org.postgresql.Driver")
                                .save();
                        logger.info("Batch {} written: {} orders", batchId, batchDF.count());
                    }
                })
                .option("checkpointLocation", "/tmp/checkpoint/orders")
                .trigger(Trigger.ProcessingTime(10, TimeUnit.SECONDS))
                .start();
    }

    private StreamingQuery processRevenueAggregation() throws Exception {
        logger.info("Starting revenue aggregation...");

        StructType orderSchema = new StructType()
                .add("eventType", DataTypes.StringType)
                .add("orderId", DataTypes.StringType)
                .add("userId", DataTypes.StringType)
                .add("productId", DataTypes.StringType)
                .add("price", DataTypes.DoubleType)
                .add("currency", DataTypes.StringType)
                .add("timestamp", DataTypes.DoubleType);  // Changed to Double to match Unix timestamp

        Dataset<Row> kafkaOrders = spark.readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", kafkaBootstrapServers)
                .option("subscribe", "orders")
                .option("startingOffsets", "latest")
                .load();

        Dataset<Row> orders = kafkaOrders
                .selectExpr("CAST(value AS STRING) as json")
                .select(from_json(col("json"), orderSchema).as("data"))
                .select("data.*")
                .withColumn("timestamp", expr("CAST(timestamp AS TIMESTAMP)"))  // Convert Unix timestamp to SQL timestamp
                .withWatermark("timestamp", "10 minutes");

        Dataset<Row> revenueByWindow = orders
                .groupBy(
                        window(col("timestamp"), "5 minutes"),
                        col("currency")
                )
                .agg(
                        sum("price").alias("total_revenue"),
                        count("*").alias("order_count"),
                        avg("price").alias("avg_order_value")
                )
                .select(
                        col("window.start").alias("window_start"),
                        col("window.end").alias("window_end"),
                        col("currency"),
                        col("total_revenue"),
                        col("order_count"),
                        col("avg_order_value")
                );

        return revenueByWindow.writeStream()
                .outputMode("update")
                .foreachBatch((batchDF, batchId) -> {
                    if (batchDF.count() > 0) {
                        batchDF.write()
                                .mode(SaveMode.Overwrite)
                                .format("jdbc")
                                .option("url", postgresUrl)
                                .option("dbtable", "revenue_by_currency_5min")
                                .option("user", postgresUser)
                                .option("password", postgresPassword)
                                .option("driver", "org.postgresql.Driver")
                                .option("truncate", "true")
                                .save();
                        logger.info("Revenue aggregation batch {} written: {} windows", batchId, batchDF.count());
                        batchDF.show(20, false);
                    }
                })
                .option("checkpointLocation", "/tmp/checkpoint/revenue")
                .trigger(Trigger.ProcessingTime(10, TimeUnit.SECONDS))
                .start();
    }

    private StreamingQuery processConversionFunnel() throws Exception {
        logger.info("Starting conversion funnel processing...");

        StructType productViewSchema = new StructType()
                .add("eventType", DataTypes.StringType)
                .add("userId", DataTypes.StringType)
                .add("productId", DataTypes.StringType)
                .add("category", DataTypes.StringType)
                .add("timestamp", DataTypes.DoubleType);  // Unix timestamp as double

        StructType orderSchema = new StructType()
                .add("eventType", DataTypes.StringType)
                .add("orderId", DataTypes.StringType)
                .add("userId", DataTypes.StringType)
                .add("productId", DataTypes.StringType)
                .add("price", DataTypes.DoubleType)
                .add("currency", DataTypes.StringType)
                .add("timestamp", DataTypes.DoubleType);  // Unix timestamp as double

        Dataset<Row> kafkaViews = spark.readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", kafkaBootstrapServers)
                .option("subscribe", "product-view")
                .option("startingOffsets", "earliest")  // Read all historical data
                .load();

        // Parse and tag views
        Dataset<Row> views = kafkaViews
                .selectExpr("CAST(value AS STRING) as json")
                .select(from_json(col("json"), productViewSchema).as("data"))
                .select("data.*")
                .withColumn("timestamp", expr("CAST(timestamp AS TIMESTAMP)"))
                .withColumn("event_type", lit("view"))
                .select("userId", "timestamp", "event_type");

        Dataset<Row> kafkaOrders = spark.readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", kafkaBootstrapServers)
                .option("subscribe", "orders")
                .option("startingOffsets", "earliest")  // Read all historical data
                .load();

        // Parse and tag orders
        Dataset<Row> orders = kafkaOrders
                .selectExpr("CAST(value AS STRING) as json")
                .select(from_json(col("json"), orderSchema).as("data"))
                .select("data.*")
                .withColumn("timestamp", expr("CAST(timestamp AS TIMESTAMP)"))
                .withColumn("event_type", lit("order"))
                .select("userId", "timestamp", "event_type");

        // Union both streams and aggregate together
        Dataset<Row> allEvents = views.union(orders)
                .withWatermark("timestamp", "10 minutes");

        // Aggregate all events by window and user, counting views and orders
        Dataset<Row> conversionMetrics = allEvents
                .groupBy(
                        window(col("timestamp"), "5 minutes"),
                        col("userId")
                )
                .agg(
                        sum(when(col("event_type").equalTo("view"), 1).otherwise(0)).alias("view_count"),
                        sum(when(col("event_type").equalTo("order"), 1).otherwise(0)).alias("order_count")
                )
                .select(
                        col("window.start").alias("window_start"),
                        col("window.end").alias("window_end"),
                        col("userId").alias("user_id"),
                        col("view_count"),
                        col("order_count"),
                        when(col("view_count").gt(0),
                                col("order_count").cast(DataTypes.DoubleType).divide(col("view_count")))
                                .otherwise(0.0).alias("conversion_rate")
                );

        return conversionMetrics.writeStream()
                .outputMode("update")  // Update mode for aggregations with watermark
                .foreachBatch((batchDF, batchId) -> {
                    if (batchDF.count() > 0) {
                        batchDF.write()
                                .mode(SaveMode.Overwrite)
                                .format("jdbc")
                                .option("url", postgresUrl)
                                .option("dbtable", "conversion_funnel_5min")
                                .option("user", postgresUser)
                                .option("password", postgresPassword)
                                .option("driver", "org.postgresql.Driver")
                                .option("truncate", "true")
                                .save();
                        logger.info("Conversion funnel batch {} written: {} records", batchId, batchDF.count());
                        batchDF.show(20, false);
                    }
                })
                .option("checkpointLocation", "/tmp/checkpoint/conversion_v2")  // New checkpoint location
                .trigger(Trigger.ProcessingTime(10, TimeUnit.SECONDS))
                .start();
    }

    /**
     * Product Recommendations using co-purchase analysis
     */
    private StreamingQuery processProductRecommendations() throws Exception {
        logger.info("Starting product recommendations...");

        StructType orderSchema = new StructType()
                .add("eventType", DataTypes.StringType)
                .add("orderId", DataTypes.StringType)
                .add("userId", DataTypes.StringType)
                .add("productId", DataTypes.StringType)
                .add("price", DataTypes.DoubleType)
                .add("currency", DataTypes.StringType)
                .add("timestamp", DataTypes.DoubleType);

        Dataset<Row> kafkaOrders = spark.readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", kafkaBootstrapServers)
                .option("subscribe", "orders")
                .option("startingOffsets", "latest")
                .load();

        Dataset<Row> orders = kafkaOrders
                .selectExpr("CAST(value AS STRING) as json")
                .select(from_json(col("json"), orderSchema).as("data"))
                .select("data.*")
                .withColumn("timestamp", expr("CAST(timestamp AS TIMESTAMP)"));

        // Group by user and time window to get products purchased together
        // Use a longer aggregation window without watermark for co-purchase analysis
        Dataset<Row> userPurchases = orders
                .groupBy(
                        window(col("timestamp"), "1 hour", "30 minutes"),  // 1 hour window, sliding every 30 min
                        col("userId")
                )
                .agg(collect_set("productId").alias("products"))  // Use collect_set to avoid duplicates
                .select(
                        col("window.start").alias("window_start"),
                        col("userId"),
                        col("products")
                )
                .filter(size(col("products")).gt(1));  // Only sessions with multiple products

        // Generate product pairs (co-purchases)
        Dataset<Row> productPairs = userPurchases
                .selectExpr("explode(products) as product1", "products")
                .selectExpr("product1", "explode(products) as product2")
                .filter("product1 < product2")  // Avoid duplicates
                .groupBy("product1", "product2")
                .agg(count("*").alias("co_purchase_count"))
                .withColumn("score", col("co_purchase_count").cast(DataTypes.DoubleType))
                .withColumn("last_updated", current_timestamp())
                .select(
                        col("product1").alias("product_id"),
                        col("product2").alias("recommended_product_id"),
                        col("score"),
                        col("co_purchase_count"),
                        col("last_updated")
                );

        return productPairs.writeStream()
                .outputMode("complete")  // Use complete mode for aggregations without watermark
                .foreachBatch((batchDF, batchId) -> {
                    if (batchDF.count() > 0) {
                        batchDF.write()
                                .mode(SaveMode.Overwrite)  // Overwrite for complete output
                                .format("jdbc")
                                .option("url", postgresUrl)
                                .option("dbtable", "product_recommendations")
                                .option("user", postgresUser)
                                .option("password", postgresPassword)
                                .option("driver", "org.postgresql.Driver")
                                .option("truncate", "true")
                                .save();
                        logger.info("Product recommendations batch {} written: {} pairs", batchId, batchDF.count());
                        batchDF.show(10, false);
                    }
                })
                .option("checkpointLocation", "/tmp/checkpoint/recommendations")
                .trigger(Trigger.ProcessingTime(60, TimeUnit.SECONDS))  // Update every minute
                .start();
    }

    /**
     * Session Analytics - Track user behavior within time windows
     */
    private StreamingQuery processSessionAnalytics() throws Exception {
        logger.info("Starting session analytics...");

        // Read both product views and orders
        StructType viewSchema = new StructType()
                .add("eventType", DataTypes.StringType)
                .add("userId", DataTypes.StringType)
                .add("productId", DataTypes.StringType)
                .add("category", DataTypes.StringType)
                .add("timestamp", DataTypes.DoubleType);

        StructType orderSchema = new StructType()
                .add("eventType", DataTypes.StringType)
                .add("orderId", DataTypes.StringType)
                .add("userId", DataTypes.StringType)
                .add("productId", DataTypes.StringType)
                .add("price", DataTypes.DoubleType)
                .add("currency", DataTypes.StringType)
                .add("timestamp", DataTypes.DoubleType);

        Dataset<Row> kafkaViews = spark.readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", kafkaBootstrapServers)
                .option("subscribe", "product-view")
                .option("startingOffsets", "latest")
                .load();

        Dataset<Row> views = kafkaViews
                .selectExpr("CAST(value AS STRING) as json")
                .select(from_json(col("json"), viewSchema).as("data"))
                .select("data.*")
                .withColumn("timestamp", expr("CAST(timestamp AS TIMESTAMP)"))
                .withColumn("event_type", lit("view"))
                .select("userId", "timestamp", "event_type");

        Dataset<Row> kafkaOrders = spark.readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", kafkaBootstrapServers)
                .option("subscribe", "orders")
                .option("startingOffsets", "latest")
                .load();

        Dataset<Row> orders = kafkaOrders
                .selectExpr("CAST(value AS STRING) as json")
                .select(from_json(col("json"), orderSchema).as("data"))
                .select("data.*")
                .withColumn("timestamp", expr("CAST(timestamp AS TIMESTAMP)"))
                .withColumn("event_type", lit("order"))
                .select("userId", "timestamp", "event_type");

        // Union all events
        Dataset<Row> allEvents = views.union(orders)
                .withWatermark("timestamp", "10 minutes");

        // Calculate session metrics
        Dataset<Row> sessionMetrics = allEvents
                .groupBy(
                        window(col("timestamp"), "5 minutes"),
                        col("userId")
                )
                .agg(
                        count("*").alias("events_count"),
                        sum(when(col("event_type").equalTo("view"), 1).otherwise(0)).alias("views_count"),
                        sum(when(col("event_type").equalTo("order"), 1).otherwise(0)).alias("orders_count"),
                        min("timestamp").alias("session_start"),
                        max("timestamp").alias("session_end")
                )
                .select(
                        col("window.start").alias("window_start"),
                        col("window.end").alias("window_end"),
                        col("userId").alias("user_id"),
                        unix_timestamp(col("session_end")).minus(unix_timestamp(col("session_start")))
                                .alias("session_duration_seconds"),
                        col("events_count"),
                        col("views_count"),
                        col("orders_count"),
                        when(col("orders_count").gt(0), lit(true)).otherwise(lit(false))
                                .alias("has_conversion")
                );

        return sessionMetrics.writeStream()
                .outputMode("append")
                .foreachBatch((batchDF, batchId) -> {
                    if (batchDF.count() > 0) {
                        batchDF.write()
                                .mode(SaveMode.Append)
                                .format("jdbc")
                                .option("url", postgresUrl)
                                .option("dbtable", "user_sessions_5min")
                                .option("user", postgresUser)
                                .option("password", postgresPassword)
                                .option("driver", "org.postgresql.Driver")
                                .save();
                        logger.info("Session analytics batch {} written: {} sessions", batchId, batchDF.count());
                        batchDF.show(20, false);
                    }
                })
                .option("checkpointLocation", "/tmp/checkpoint/sessions")
                .trigger(Trigger.ProcessingTime(10, TimeUnit.SECONDS))
                .start();
    }

    public static void main(String[] args) throws Exception {
        String kafkaBootstrapServers = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        String postgresUrl = System.getenv().getOrDefault("POSTGRES_URL", "jdbc:postgresql://localhost:5432/analytics");
        String postgresUser = System.getenv().getOrDefault("POSTGRES_USER", "analytics");
        String postgresPassword = System.getenv().getOrDefault("POSTGRES_PASSWORD", "secret");

        logger.info("EventFlow Streaming App starting...");
        logger.info("Kafka Bootstrap Servers: {}", kafkaBootstrapServers);
        logger.info("PostgreSQL URL: {}", postgresUrl);

        StreamingApp app = new StreamingApp(kafkaBootstrapServers, postgresUrl, postgresUser, postgresPassword);
        app.start();
    }
}
