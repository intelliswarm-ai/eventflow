@echo off
REM EventFlow - Rebuild and Start All Services (Windows)
REM This script rebuilds and starts all EventFlow services including Grafana monitoring

echo ======================================
echo EventFlow - Rebuild and Start Script
echo ======================================
echo.

REM Change to project directory
cd /d "%~dp0\.."

echo Step 1: Stopping existing containers...
docker-compose down 2>nul
echo Done
echo.

echo Step 2: Cleaning Docker build cache...
docker builder prune -f
echo Done
echo.

echo Step 3: Building all services (this may take 5-10 minutes)...
echo Services to build:
echo   - Producer (Kafka event generator)
echo   - Spark Streaming (real-time processing)
echo   - Spark Batch (analytics)
echo   - JMX Exporter (Kafka metrics)
echo.

docker-compose build --no-cache

if errorlevel 1 (
    echo Build failed! Check for errors above.
    pause
    exit /b 1
)

echo All services built successfully
echo.

echo Step 4: Starting all services...
docker-compose up -d
echo Services starting...
echo.

echo Step 5: Waiting for services to initialize (30 seconds)...
timeout /t 30 /nobreak >nul
echo.

echo Step 6: Checking service status...
docker-compose ps
echo.

echo ======================================
echo Service URLs:
echo ======================================
echo Grafana:    http://localhost:3000 (admin/admin)
echo Prometheus: http://localhost:9090
echo Kafka:      localhost:9092
echo PostgreSQL: localhost:5432 (analytics/secret)
echo.

echo ======================================
echo Quick Commands:
echo ======================================
echo View producer logs:    docker-compose logs -f producer
echo View streaming logs:   docker-compose logs -f spark-streaming
echo View Grafana logs:     docker-compose logs -f grafana
echo Query database:        docker exec -it eventflow-postgres psql -U analytics -d analytics
echo Check Kafka topics:    docker exec eventflow-kafka kafka-topics --bootstrap-server localhost:9092 --list
echo.

echo ======================================
echo Next Steps:
echo ======================================
echo 1. Wait 1-2 minutes for data to accumulate
echo 2. Open http://localhost:3000 in your browser
echo 3. Login with admin/admin
echo 4. Navigate to: Dashboards - EventFlow folder
echo 5. Explore the 3 pre-built dashboards:
echo    - Real-Time Pipeline (live metrics)
echo    - Kafka Health ^& Performance
echo    - Revenue Analytics
echo.

echo EventFlow is ready!
echo.
pause
