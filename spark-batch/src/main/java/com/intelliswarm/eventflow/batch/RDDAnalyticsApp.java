package com.intelliswarm.eventflow.batch;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Tuple2;
import scala.Tuple3;

import java.io.Serializable;
import java.util.*;

/**
 * RDD-based Batch Analytics using classic MapReduce patterns
 * Demonstrates in-memory transformations that are 10-100x faster than Hadoop MapReduce
 */
public class RDDAnalyticsApp {
    private static final Logger logger = LoggerFactory.getLogger(RDDAnalyticsApp.class);

    private final JavaSparkContext sc;
    private final String postgresUrl;
    private final String postgresUser;
    private final String postgresPassword;

    // Data structures for analysis
    public static class Order implements Serializable {
        public String orderId;
        public String userId;
        public String productId;
        public Double price;
        public String currency;
        public Long timestamp;

        public Order(String orderId, String userId, String productId, Double price, String currency, Long timestamp) {
            this.orderId = orderId;
            this.userId = userId;
            this.productId = productId;
            this.price = price;
            this.currency = currency;
            this.timestamp = timestamp;
        }
    }

    public static class UserStats implements Serializable {
        public double totalSpent;
        public int orderCount;
        public Set<String> uniqueProducts;

        public UserStats() {
            this.totalSpent = 0.0;
            this.orderCount = 0;
            this.uniqueProducts = new HashSet<>();
        }

        public UserStats(double totalSpent, int orderCount, Set<String> uniqueProducts) {
            this.totalSpent = totalSpent;
            this.orderCount = orderCount;
            this.uniqueProducts = uniqueProducts;
        }
    }

    public RDDAnalyticsApp(String postgresUrl, String postgresUser, String postgresPassword) {
        this.postgresUrl = postgresUrl;
        this.postgresUser = postgresUser;
        this.postgresPassword = postgresPassword;

        SparkConf conf = new SparkConf()
                .setAppName("EventFlow RDD Analytics")
                .setMaster("local[*]")
                .set("spark.sql.warehouse.dir", "/tmp/spark-warehouse");

        this.sc = new JavaSparkContext(conf);
        logger.info("Spark Context initialized for RDD processing");
    }

    /**
     * EXAMPLE 1: Classic MapReduce - Total Revenue by Currency
     * Demonstrates: map() -> reduceByKey()
     */
    public void calculateRevenueByCurrency() {
        logger.info("=== MAPREDUCE EXAMPLE 1: Revenue by Currency ===");

        // Read orders from PostgreSQL
        SparkSession spark = SparkSession.builder().sparkContext(sc.sc()).getOrCreate();
        JavaRDD<Order> ordersRDD = spark.read()
                .format("jdbc")
                .option("url", postgresUrl)
                .option("dbtable", "orders_raw")
                .option("user", postgresUser)
                .option("password", postgresPassword)
                .load()
                .toJavaRDD()
                .map(row -> new Order(
                        row.getAs("order_id"),
                        row.getAs("user_id"),
                        row.getAs("product_id"),
                        row.getAs("price"),
                        row.getAs("currency"),
                        row.getTimestamp(row.fieldIndex("timestamp")).getTime()
                ));

        // MAP PHASE: Transform to (currency, price) pairs
        JavaPairRDD<String, Double> currencyRevenue = ordersRDD
                .mapToPair(order -> new Tuple2<>(order.currency, order.price));

        // REDUCE PHASE: Sum by key (currency)
        JavaPairRDD<String, Double> totalByCurrency = currencyRevenue
                .reduceByKey((a, b) -> a + b);

        // Collect and display results
        Map<String, Double> results = totalByCurrency.collectAsMap();
        logger.info("Revenue by Currency (MapReduce):");
        results.forEach((currency, revenue) ->
                logger.info("  {}: ${}", currency, String.format("%.2f", revenue)));

        // Cache for reuse (in-memory performance benefit)
        totalByCurrency.cache();
    }

    /**
     * EXAMPLE 2: Advanced MapReduce - User Segmentation
     * Demonstrates: map() -> aggregateByKey() -> filter() -> sortByKey()
     */
    public void segmentUsers() {
        logger.info("=== MAPREDUCE EXAMPLE 2: User Segmentation ===");

        SparkSession spark = SparkSession.builder().sparkContext(sc.sc()).getOrCreate();
        JavaRDD<Order> ordersRDD = spark.read()
                .format("jdbc")
                .option("url", postgresUrl)
                .option("dbtable", "orders_raw")
                .option("user", postgresUser)
                .option("password", postgresPassword)
                .load()
                .toJavaRDD()
                .map(row -> new Order(
                        row.getAs("order_id"),
                        row.getAs("user_id"),
                        row.getAs("product_id"),
                        row.getAs("price"),
                        row.getAs("currency"),
                        row.getTimestamp(row.fieldIndex("timestamp")).getTime()
                ));

        // MAP to (userId, Order)
        JavaPairRDD<String, Order> userOrders = ordersRDD
                .mapToPair(order -> new Tuple2<>(order.userId, order));

        // AGGREGATE BY KEY: Combine orders per user
        UserStats zeroValue = new UserStats();

        JavaPairRDD<String, UserStats> userStats = userOrders.aggregateByKey(
                zeroValue,
                // Sequence function: add order to stats
                (stats, order) -> {
                    stats.totalSpent += order.price;
                    stats.orderCount++;
                    stats.uniqueProducts.add(order.productId);
                    return stats;
                },
                // Combiner function: merge stats from partitions
                (stats1, stats2) -> {
                    UserStats merged = new UserStats();
                    merged.totalSpent = stats1.totalSpent + stats2.totalSpent;
                    merged.orderCount = stats1.orderCount + stats2.orderCount;
                    merged.uniqueProducts.addAll(stats1.uniqueProducts);
                    merged.uniqueProducts.addAll(stats2.uniqueProducts);
                    return merged;
                }
        );

        // FILTER: VIP users (>$1000 spent)
        JavaPairRDD<String, UserStats> vipUsers = userStats
                .filter(tuple -> tuple._2.totalSpent > 1000);

        // MAP to (totalSpent, userId) and SORT
        JavaPairRDD<Double, String> sortedByRevenue = vipUsers
                .mapToPair(tuple -> new Tuple2<>(tuple._2.totalSpent, tuple._1))
                .sortByKey(false);  // Descending order

        // Display top 10 VIP users
        List<Tuple2<Double, String>> top10 = sortedByRevenue.take(10);
        logger.info("Top 10 VIP Users:");
        for (int i = 0; i < top10.size(); i++) {
            Tuple2<Double, String> user = top10.get(i);
            UserStats stats = userStats.lookup(user._2).get(0);
            logger.info("  {}. User {}: ${} ({} orders, {} unique products)",
                    i + 1, user._2, String.format("%.2f", user._1),
                    stats.orderCount, stats.uniqueProducts.size());
        }
    }

    /**
     * EXAMPLE 3: Product Co-occurrence Analysis (Market Basket)
     * Demonstrates: flatMapToPair() -> reduceByKey() -> join()
     */
    public void analyzeProductCooccurrence() {
        logger.info("=== MAPREDUCE EXAMPLE 3: Product Co-occurrence (Market Basket) ===");

        SparkSession spark = SparkSession.builder().sparkContext(sc.sc()).getOrCreate();
        JavaRDD<Order> ordersRDD = spark.read()
                .format("jdbc")
                .option("url", postgresUrl)
                .option("dbtable", "orders_raw")
                .option("user", postgresUser)
                .option("password", postgresPassword)
                .load()
                .toJavaRDD()
                .map(row -> new Order(
                        row.getAs("order_id"),
                        row.getAs("user_id"),
                        row.getAs("product_id"),
                        row.getAs("price"),
                        row.getAs("currency"),
                        row.getTimestamp(row.fieldIndex("timestamp")).getTime()
                ));

        // Group by user to find products bought together
        JavaPairRDD<String, Iterable<String>> userProducts = ordersRDD
                .mapToPair(order -> new Tuple2<>(order.userId, order.productId))
                .groupByKey();

        // Generate product pairs (combinations of 2)
        JavaPairRDD<Tuple2<String, String>, Integer> productPairs = userProducts
                .flatMapToPair(userAndProducts -> {
                    List<Tuple2<Tuple2<String, String>, Integer>> pairs = new ArrayList<>();
                    List<String> products = new ArrayList<>();
                    userAndProducts._2.forEach(products::add);

                    // Generate all pairs
                    for (int i = 0; i < products.size(); i++) {
                        for (int j = i + 1; j < products.size(); j++) {
                            String p1 = products.get(i);
                            String p2 = products.get(j);
                            // Ensure consistent ordering
                            if (p1.compareTo(p2) < 0) {
                                pairs.add(new Tuple2<>(new Tuple2<>(p1, p2), 1));
                            } else {
                                pairs.add(new Tuple2<>(new Tuple2<>(p2, p1), 1));
                            }
                        }
                    }
                    return pairs.iterator();
                });

        // Count co-occurrences
        JavaPairRDD<Tuple2<String, String>, Integer> cooccurrenceCounts = productPairs
                .reduceByKey((a, b) -> a + b);

        // Sort by frequency
        JavaPairRDD<Integer, Tuple2<String, String>> sortedPairs = cooccurrenceCounts
                .mapToPair(tuple -> new Tuple2<>(tuple._2, tuple._1))
                .sortByKey(false);

        // Display top 10 product pairs
        List<Tuple2<Integer, Tuple2<String, String>>> top10Pairs = sortedPairs.take(10);
        logger.info("Top 10 Products Frequently Bought Together:");
        for (int i = 0; i < top10Pairs.size(); i++) {
            Tuple2<Integer, Tuple2<String, String>> pair = top10Pairs.get(i);
            logger.info("  {}. {} + {} : {} times",
                    i + 1, pair._2._1, pair._2._2, pair._1);
        }
    }

    /**
     * EXAMPLE 4: Price Distribution Analysis
     * Demonstrates: map() -> histogram(), percentile calculations
     */
    public void analyzePriceDistribution() {
        logger.info("=== MAPREDUCE EXAMPLE 4: Price Distribution ===");

        SparkSession spark = SparkSession.builder().sparkContext(sc.sc()).getOrCreate();
        JavaRDD<Order> ordersRDD = spark.read()
                .format("jdbc")
                .option("url", postgresUrl)
                .option("dbtable", "orders_raw")
                .option("user", postgresUser)
                .option("password", postgresPassword)
                .load()
                .toJavaRDD()
                .map(row -> new Order(
                        row.getAs("order_id"),
                        row.getAs("user_id"),
                        row.getAs("product_id"),
                        row.getAs("price"),
                        row.getAs("currency"),
                        row.getTimestamp(row.fieldIndex("timestamp")).getTime()
                ));

        // Extract prices as RDD
        JavaRDD<Double> prices = ordersRDD.map(order -> order.price);

        // Calculate statistics using reduce operations
        double sum = prices.reduce((a, b) -> a + b);
        long count = prices.count();
        double mean = sum / count;

        double min = prices.reduce(Math::min);
        double max = prices.reduce(Math::max);

        // Calculate standard deviation
        double variance = prices
                .map(price -> Math.pow(price - mean, 2))
                .reduce((a, b) -> a + b) / count;
        double stdDev = Math.sqrt(variance);

        logger.info("Price Statistics:");
        logger.info("  Count: {}", count);
        logger.info("  Mean: ${}", String.format("%.2f", mean));
        logger.info("  Min: ${}", String.format("%.2f", min));
        logger.info("  Max: ${}", String.format("%.2f", max));
        logger.info("  Std Dev: ${}", String.format("%.2f", stdDev));

        // Calculate percentiles manually
        List<Double> sortedPrices = prices.sortBy(p -> p, true, prices.getNumPartitions()).collect();
        int size = sortedPrices.size();
        logger.info("  25th Percentile: ${}", String.format("%.2f", sortedPrices.get(size / 4)));
        logger.info("  50th Percentile (Median): ${}", String.format("%.2f", sortedPrices.get(size / 2)));
        logger.info("  75th Percentile: ${}", String.format("%.2f", sortedPrices.get(3 * size / 4)));
    }

    /**
     * EXAMPLE 5: Time-based Analysis with MapReduce
     * Demonstrates: keyBy() -> mapValues() -> reduceByKey()
     */
    public void analyzeOrdersByHour() {
        logger.info("=== MAPREDUCE EXAMPLE 5: Orders by Hour of Day ===");

        SparkSession spark = SparkSession.builder().sparkContext(sc.sc()).getOrCreate();
        JavaRDD<Order> ordersRDD = spark.read()
                .format("jdbc")
                .option("url", postgresUrl)
                .option("dbtable", "orders_raw")
                .option("user", postgresUser)
                .option("password", postgresPassword)
                .load()
                .toJavaRDD()
                .map(row -> new Order(
                        row.getAs("order_id"),
                        row.getAs("user_id"),
                        row.getAs("product_id"),
                        row.getAs("price"),
                        row.getAs("currency"),
                        row.getTimestamp(row.fieldIndex("timestamp")).getTime()
                ));

        // Extract hour of day
        JavaPairRDD<Integer, Integer> hourCounts = ordersRDD
                .mapToPair(order -> {
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(order.timestamp);
                    int hour = cal.get(Calendar.HOUR_OF_DAY);
                    return new Tuple2<>(hour, 1);
                })
                .reduceByKey((a, b) -> a + b)
                .sortByKey(true);

        logger.info("Orders by Hour of Day:");
        hourCounts.collect().forEach(tuple ->
                logger.info("  Hour {}: {} orders", tuple._1, tuple._2));
    }

    /**
     * Run all RDD analytics examples
     */
    public void runAllAnalytics() {
        long startTime = System.currentTimeMillis();
        logger.info("Starting RDD Batch Analytics...");

        try {
            calculateRevenueByCurrency();
            System.out.println();

            segmentUsers();
            System.out.println();

            analyzeProductCooccurrence();
            System.out.println();

            analyzePriceDistribution();
            System.out.println();

            analyzeOrdersByHour();

            long endTime = System.currentTimeMillis();
            logger.info("All analytics completed in {} ms", endTime - startTime);
            logger.info("(10-100x faster than Hadoop MapReduce due to in-memory processing!)");

        } catch (Exception e) {
            logger.error("Error during analytics", e);
        } finally {
            sc.close();
        }
    }

    public static void main(String[] args) {
        String postgresUrl = System.getenv().getOrDefault("POSTGRES_URL", "jdbc:postgresql://localhost:5432/analytics");
        String postgresUser = System.getenv().getOrDefault("POSTGRES_USER", "analytics");
        String postgresPassword = System.getenv().getOrDefault("POSTGRES_PASSWORD", "secret");

        logger.info("EventFlow RDD Analytics starting...");
        logger.info("PostgreSQL URL: {}", postgresUrl);

        RDDAnalyticsApp app = new RDDAnalyticsApp(postgresUrl, postgresUser, postgresPassword);
        app.runAllAnalytics();
    }
}
