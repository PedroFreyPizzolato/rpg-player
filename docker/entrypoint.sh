#!/bin/sh
set -e

# Determine which jar to run based on BOT_VERSION environment variable
JAR=""

if [ -n "$BOT_VERSION" ]; then
    VERSIONED_JAR="/app/JMusicBot-${BOT_VERSION}-All.jar"
    if [ -f "$VERSIONED_JAR" ]; then
        JAR="$VERSIONED_JAR"
        echo "[INFO] Using versioned jar: $JAR (BOT_VERSION=${BOT_VERSION})"
    else
        echo "[WARN] BOT_VERSION=${BOT_VERSION} specified, but $VERSIONED_JAR not found. Trying fallback..."
    fi
fi

# Fallback to app.jar if not set
if [ -z "$JAR" ]; then
    if [ -f "/app/app.jar" ]; then
        JAR="/app/app.jar"
        echo "[INFO] Using default jar: $JAR"
    else
        # Last resort: find any *All.jar in /app
        FOUND_JAR=$(find /app -name "*All.jar" -type f | head -n 1)
        if [ -n "$FOUND_JAR" ]; then
            JAR="$FOUND_JAR"
            echo "[INFO] Using found jar: $JAR"
        else
            echo "[ERROR] No jar file found in /app!"
            echo "[ERROR] Expected one of:"
            echo "[ERROR]   - /app/app.jar"
            if [ -n "$BOT_VERSION" ]; then
                echo "[ERROR]   - /app/JMusicBot-${BOT_VERSION}-All.jar"
            fi
            echo "[ERROR]   - /app/*All.jar (any matching file)"
            exit 1
        fi
    fi
fi

# Print startup information
echo "[INFO] ========================================"
echo "[INFO] JMusicBot Docker Container"
echo "[INFO] ========================================"
echo "[INFO] Selected jar: $JAR"
echo "[INFO] Working directory: $(pwd)"
if [ -f "config.txt" ]; then
    echo "[INFO] config.txt: Found (existing)"
else
    echo "[INFO] config.txt: Not found (will be generated on first run)"
fi
echo "[INFO] ========================================"

# Build Java command
JAVA_CMD="java -Dnogui=true"

# Append JAVA_OPTS if provided
if [ -n "$JAVA_OPTS" ]; then
    JAVA_CMD="$JAVA_CMD $JAVA_OPTS"
fi

# Add jar
JAVA_CMD="$JAVA_CMD -jar \"$JAR\""

# Execute Java with exec to ensure proper signal handling
# Use sh -c to properly handle JAVA_OPTS that may contain multiple arguments
exec sh -c "$JAVA_CMD"

