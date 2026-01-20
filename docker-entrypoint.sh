#!/bin/bash
set -e

echo "Waiting for MySQL to be ready..."

# Wait for MySQL to be available by checking TCP connection
# Using bash's built-in /dev/tcp feature (works in bash, not in sh)
# Fallback: simple timeout loop (docker-compose health check should handle this, but this is a safety net)
MAX_ATTEMPTS=30
ATTEMPT=0

while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
  if (timeout 1 bash -c "cat < /dev/null > /dev/tcp/mysql/3306" 2>/dev/null) || \
     (command -v nc >/dev/null 2>&1 && nc -z mysql 3306 2>/dev/null); then
    echo "MySQL is up - executing command"
    exec "$@"
    exit 0
  fi
  ATTEMPT=$((ATTEMPT + 1))
  echo "MySQL is unavailable - sleeping (attempt $ATTEMPT/$MAX_ATTEMPTS)"
  sleep 2
done

echo "MySQL did not become available in time, but proceeding anyway..."
# Execute the main command (Spring Boot application will handle connection retries)
exec "$@"
