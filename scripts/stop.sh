#!/bin/bash

echo "======================================"
echo "EventFlow - Stopping Services"
echo "======================================"

echo ""
echo "Stopping all services..."
docker-compose down

echo ""
echo "======================================"
echo "Services stopped successfully!"
echo "======================================"
echo ""
echo "To remove all data volumes, run:"
echo "  docker-compose down -v"
echo ""
