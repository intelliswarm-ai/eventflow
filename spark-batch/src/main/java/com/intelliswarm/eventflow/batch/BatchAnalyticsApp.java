package com.intelliswarm.eventflow.batch;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchAnalyticsApp {
    private static final Logger logger = LoggerFactory.getLogger(BatchAnalyticsApp.class);

    private final SparkSession spark;
    private final String postgresUrl;
    private final String postgresUser;
    private final String postgresPassword;

    public BatchAnalyticsApp(String postgresUrl, String postgresUser, String postgresPassword) {
        this.postgresUrl = postgresUrl;
        this.postgresUser = postgresUser;
        this.postgresPassword = postgresPassword;

        this.spark = SparkSession.builder()
                .appName("EventFlow Batch Analytics")
                .master("local[*]")
                .config("spark.sql.shuffle.partitions", "4")
                .getOrCreate();

        logger.info("Spark session created for batch analytics");
    }

    public void run() {
        logger.info("Running batch analytics...");

        topRevenueAnalysis();
        conversionRateAnalysis();
        revenueByTimeAnalysis();
        userSegmentationAnalysis();

        spark.stop();
        logger.info("Batch analytics completed");
    }

    private Dataset<Row> loadTable(String tableName) {
        return spark.read()
                .format("jdbc")
                .option("url", postgresUrl)
                .option("dbtable", tableName)
                .option("user", postgresUser)
                .option("password", postgresPassword)
                .option("driver", "org.postgresql.Driver")
                .load();
    }

    private void topRevenueAnalysis() {
        logger.info("\n=== Top Revenue Analysis ===");

        Dataset<Row> revenue = loadTable("revenue_by_currency_5min");
        revenue.createOrReplaceTempView("revenue");

        Dataset<Row> topCurrencies = spark.sql(
                "SELECT currency, " +
                        "       SUM(total_revenue) as total_revenue, " +
                        "       SUM(order_count) as total_orders, " +
                        "       AVG(avg_order_value) as avg_order_value " +
                        "FROM revenue " +
                        "GROUP BY currency " +
                        "ORDER BY total_revenue DESC"
        );

        logger.info("Top Currencies by Revenue:");
        topCurrencies.show(10, false);
    }

    private void conversionRateAnalysis() {
        logger.info("\n=== Conversion Rate Analysis ===");

        Dataset<Row> conversion = loadTable("conversion_funnel_5min");
        conversion.createOrReplaceTempView("conversion");

        Dataset<Row> conversionRates = spark.sql(
                "SELECT user_id, " +
                        "       SUM(view_count) as total_views, " +
                        "       SUM(order_count) as total_orders, " +
                        "       CASE WHEN SUM(view_count) > 0 " +
                        "            THEN ROUND(SUM(order_count) * 100.0 / SUM(view_count), 2) " +
                        "            ELSE 0 " +
                        "       END as conversion_rate " +
                        "FROM conversion " +
                        "WHERE view_count > 0 " +
                        "GROUP BY user_id " +
                        "ORDER BY total_orders DESC " +
                        "LIMIT 20"
        );

        logger.info("Top Users by Conversion Rate:");
        conversionRates.show(20, false);

        Dataset<Row> overallConversion = spark.sql(
                "SELECT SUM(view_count) as total_views, " +
                        "       SUM(order_count) as total_orders, " +
                        "       ROUND(SUM(order_count) * 100.0 / SUM(view_count), 2) as overall_conversion_rate " +
                        "FROM conversion " +
                        "WHERE view_count > 0"
        );

        logger.info("Overall Conversion Rate:");
        overallConversion.show(false);
    }

    private void revenueByTimeAnalysis() {
        logger.info("\n=== Revenue by Time Window Analysis ===");

        Dataset<Row> revenue = loadTable("revenue_by_currency_5min");
        revenue.createOrReplaceTempView("revenue");

        Dataset<Row> revenueByTime = spark.sql(
                "SELECT window_start, " +
                        "       window_end, " +
                        "       SUM(total_revenue) as total_revenue, " +
                        "       SUM(order_count) as total_orders " +
                        "FROM revenue " +
                        "GROUP BY window_start, window_end " +
                        "ORDER BY window_start DESC " +
                        "LIMIT 20"
        );

        logger.info("Revenue by Time Window:");
        revenueByTime.show(20, false);
    }

    private void userSegmentationAnalysis() {
        logger.info("\n=== User Segmentation Analysis ===");

        Dataset<Row> orders = loadTable("orders_raw");
        orders.createOrReplaceTempView("orders_raw");

        Dataset<Row> userSegments = spark.sql(
                "SELECT user_id, " +
                        "       COUNT(*) as total_orders, " +
                        "       SUM(price) as total_spent, " +
                        "       AVG(price) as avg_order_value, " +
                        "       MIN(price) as min_order, " +
                        "       MAX(price) as max_order, " +
                        "       CASE " +
                        "           WHEN SUM(price) > 1000 THEN 'VIP' " +
                        "           WHEN SUM(price) > 500 THEN 'Premium' " +
                        "           WHEN SUM(price) > 100 THEN 'Regular' " +
                        "           ELSE 'New' " +
                        "       END as segment " +
                        "FROM orders_raw " +
                        "GROUP BY user_id " +
                        "ORDER BY total_spent DESC " +
                        "LIMIT 30"
        );

        logger.info("User Segmentation:");
        userSegments.show(30, false);

        Dataset<Row> segmentSummary = spark.sql(
                "WITH user_segments AS (" +
                        "    SELECT user_id, " +
                        "           SUM(price) as total_spent, " +
                        "           COUNT(*) as order_count, " +
                        "           CASE " +
                        "               WHEN SUM(price) > 1000 THEN 'VIP' " +
                        "               WHEN SUM(price) > 500 THEN 'Premium' " +
                        "               WHEN SUM(price) > 100 THEN 'Regular' " +
                        "               ELSE 'New' " +
                        "           END as segment " +
                        "    FROM orders_raw " +
                        "    GROUP BY user_id" +
                        ") " +
                        "SELECT segment, " +
                        "       COUNT(*) as user_count, " +
                        "       SUM(total_spent) as segment_revenue, " +
                        "       AVG(total_spent) as avg_user_value, " +
                        "       SUM(order_count) as total_orders " +
                        "FROM user_segments " +
                        "GROUP BY segment " +
                        "ORDER BY segment_revenue DESC"
        );

        logger.info("Segment Summary:");
        segmentSummary.show(false);
    }

    public static void main(String[] args) {
        String postgresUrl = System.getenv().getOrDefault("POSTGRES_URL", "jdbc:postgresql://localhost:5432/analytics");
        String postgresUser = System.getenv().getOrDefault("POSTGRES_USER", "analytics");
        String postgresPassword = System.getenv().getOrDefault("POSTGRES_PASSWORD", "secret");

        logger.info("EventFlow Batch Analytics App starting...");
        logger.info("PostgreSQL URL: {}", postgresUrl);

        BatchAnalyticsApp app = new BatchAnalyticsApp(postgresUrl, postgresUser, postgresPassword);
        app.run();
    }
}
