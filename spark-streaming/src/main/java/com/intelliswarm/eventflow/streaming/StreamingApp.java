package com.intelliswarm.eventflow.streaming;

import org.apache.spark.sql.*;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.apache.spark.sql.streaming.Trigger;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        // Stream-stream joins require complex watermark configuration - disabled for now
        // StreamingQuery conversionQuery = processConversionFunnel();

        ordersQuery.awaitTermination();
        revenueQuery.awaitTermination();
        // conversionQuery.awaitTermination();
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
                "PRIMARY KEY (window_start, user_id))";

        executeSQL(createOrdersTable);
        executeSQL(createRevenueTable);
        executeSQL(createConversionTable);

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
                .add("timestamp", DataTypes.TimestampType);

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
                .withWatermark("timestamp", "10 minutes");

        return orders.writeStream()
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
                .add("timestamp", DataTypes.TimestampType);

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
                .add("timestamp", DataTypes.TimestampType);

        StructType orderSchema = new StructType()
                .add("eventType", DataTypes.StringType)
                .add("orderId", DataTypes.StringType)
                .add("userId", DataTypes.StringType)
                .add("productId", DataTypes.StringType)
                .add("price", DataTypes.DoubleType)
                .add("currency", DataTypes.StringType)
                .add("timestamp", DataTypes.TimestampType);

        Dataset<Row> kafkaViews = spark.readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", kafkaBootstrapServers)
                .option("subscribe", "product-view")
                .option("startingOffsets", "latest")
                .load();

        Dataset<Row> views = kafkaViews
                .selectExpr("CAST(value AS STRING) as json")
                .select(from_json(col("json"), productViewSchema).as("data"))
                .select("data.*")
                .withWatermark("timestamp", "10 minutes");

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
                .withWatermark("timestamp", "10 minutes");

        Dataset<Row> viewsAgg = views
                .groupBy(
                        window(col("timestamp"), "5 minutes"),
                        col("userId")
                )
                .agg(count("*").alias("view_count"))
                .select(
                        col("window.start").alias("window_start"),
                        col("window.end").alias("window_end"),
                        col("userId"),
                        col("view_count")
                );

        Dataset<Row> ordersAgg = orders
                .groupBy(
                        window(col("timestamp"), "5 minutes"),
                        col("userId")
                )
                .agg(count("*").alias("order_count"))
                .select(
                        col("window.start").alias("window_start"),
                        col("window.end").alias("window_end"),
                        col("userId"),
                        col("order_count")
                );

        Dataset<Row> conversion = viewsAgg
                .join(ordersAgg,
                        viewsAgg.col("window_start").equalTo(ordersAgg.col("window_start"))
                                .and(viewsAgg.col("userId").equalTo(ordersAgg.col("userId"))),
                        "full_outer")
                .select(
                        coalesce(viewsAgg.col("window_start"), ordersAgg.col("window_start")).alias("window_start"),
                        coalesce(viewsAgg.col("window_end"), ordersAgg.col("window_end")).alias("window_end"),
                        coalesce(viewsAgg.col("userId"), ordersAgg.col("userId")).alias("user_id"),
                        coalesce(viewsAgg.col("view_count"), lit(0)).alias("view_count"),
                        coalesce(ordersAgg.col("order_count"), lit(0)).alias("order_count")
                );

        return conversion.writeStream()
                .outputMode("append")
                .foreachBatch((batchDF, batchId) -> {
                    if (batchDF.count() > 0) {
                        batchDF.write()
                                .mode(SaveMode.Append)
                                .format("jdbc")
                                .option("url", postgresUrl)
                                .option("dbtable", "conversion_funnel_5min")
                                .option("user", postgresUser)
                                .option("password", postgresPassword)
                                .option("driver", "org.postgresql.Driver")
                                .save();
                        logger.info("Conversion funnel batch {} written: {} records", batchId, batchDF.count());
                        batchDF.show(20, false);
                    }
                })
                .option("checkpointLocation", "/tmp/checkpoint/conversion")
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
