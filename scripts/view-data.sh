#!/bin/bash

echo "======================================"
echo "EventFlow - Database Query Tool"
echo "======================================"

echo ""
echo "1. Recent Orders:"
docker exec -it eventflow-postgres psql -U analytics -d analytics -c "SELECT order_id, user_id, product_id, price, currency, timestamp FROM orders_raw ORDER BY timestamp DESC LIMIT 10;"

echo ""
echo "2. Revenue by Currency (Last 5 windows):"
docker exec -it eventflow-postgres psql -U analytics -d analytics -c "SELECT window_start, window_end, currency, ROUND(total_revenue::numeric, 2) as total_revenue, order_count, ROUND(avg_order_value::numeric, 2) as avg_order_value FROM revenue_by_currency_5min ORDER BY window_start DESC LIMIT 10;"

echo ""
echo "3. Conversion Funnel (Top Users):"
docker exec -it eventflow-postgres psql -U analytics -d analytics -c "SELECT user_id, SUM(view_count) as total_views, SUM(order_count) as total_orders FROM conversion_funnel_5min GROUP BY user_id ORDER BY total_orders DESC LIMIT 10;"

echo ""
echo "4. Table Row Counts:"
docker exec -it eventflow-postgres psql -U analytics -d analytics -c "SELECT 'orders_raw' as table_name, COUNT(*) as row_count FROM orders_raw UNION ALL SELECT 'revenue_by_currency_5min', COUNT(*) FROM revenue_by_currency_5min UNION ALL SELECT 'conversion_funnel_5min', COUNT(*) FROM conversion_funnel_5min;"

echo ""
