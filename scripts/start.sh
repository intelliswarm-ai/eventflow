#!/bin/bash

echo "======================================"
echo "EventFlow - Starting Services"
echo "======================================"

echo ""
echo "Building and starting all services..."
docker-compose up -d --build

echo ""
echo "Waiting for services to be healthy..."
sleep 10

echo ""
echo "Checking service status..."
docker-compose ps

echo ""
echo "======================================"
echo "Services started successfully!"
echo "======================================"
echo ""
echo "Useful commands:"
echo "  - View producer logs:     docker-compose logs -f producer"
echo "  - View streaming logs:    docker-compose logs -f spark-streaming"
echo "  - View Kafka topics:      docker exec eventflow-kafka kafka-topics --bootstrap-server localhost:9092 --list"
echo "  - Connect to database:    docker exec -it eventflow-postgres psql -U analytics -d analytics"
echo "  - Run batch analytics:    docker-compose --profile batch up spark-batch"
echo "  - Stop all services:      docker-compose down"
echo ""
