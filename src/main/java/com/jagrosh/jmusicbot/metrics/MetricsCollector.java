/*
 * Copyright 2026 John Grosh (jagrosh).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.metrics;

import com.jagrosh.jmusicbot.metrics.model.TelemetryEvent;
import com.jagrosh.jmusicbot.metrics.store.TelemetryStore;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Collects and records telemetry events for the bot.
 * Handles sanitization of sensitive data before storing.
 *
 * @author John Grosh (jagrosh)
 */
public class MetricsCollector {
    private static final Logger LOG = LoggerFactory.getLogger(MetricsCollector.class);
    
    // Patterns for sanitizing sensitive data from stack traces
    private static final Pattern USER_PATH_PATTERN = Pattern.compile(
            "(/home/[^/]+|/Users/[^/]+|C:\\\\Users\\\\[^\\\\]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "[A-Za-z0-9_-]{24}\\.[A-Za-z0-9_-]{6}\\.[A-Za-z0-9_-]{27,}");
    
    // Maximum stack trace length to prevent massive payloads
    private static final int MAX_STACK_TRACE_LENGTH = 4000;

    private final TelemetryStore store;
    private final InstanceIdManager instanceIdManager;
    private final Instant startTime;
    private final boolean enabled;

    /**
     * Creates a new MetricsCollector.
     *
     * @param store             The telemetry store for persisting events
     * @param instanceIdManager The instance ID manager
     * @param enabled           Whether metrics collection is enabled
     */
    public MetricsCollector(TelemetryStore store, InstanceIdManager instanceIdManager, boolean enabled) {
        this.store = store;
        this.instanceIdManager = instanceIdManager;
        this.startTime = Instant.now();
        this.enabled = enabled;
    }

    /**
     * Records a startup event with version and system information.
     * Should be called once when the bot starts successfully.
     */
    public void recordStartup() {
        if (!enabled) {
            return;
        }
        
        try {
            String version = OtherUtil.getCurrentVersion();
            String javaVersion = System.getProperty("java.version");
            String os = System.getProperty("os.name") + " " + System.getProperty("os.arch");
            
            TelemetryEvent event = TelemetryEvent.startup(version, javaVersion, os);
            store.appendEvent(event);
            LOG.debug("Recorded startup event: version={}, java={}, os={}", version, javaVersion, os);
        } catch (Exception e) {
            LOG.warn("Failed to record startup event: {}", e.getMessage());
        }
    }

    /**
     * Records a periodic snapshot of the bot's current state.
     * Should be called periodically (e.g., every 30 minutes).
     *
     * @param guildCount          Number of guilds the bot is in
     * @param activeAudioSessions Number of currently active audio sessions
     */
    public void recordSnapshot(int guildCount, int activeAudioSessions) {
        if (!enabled) {
            return;
        }
        
        try {
            long uptimeMinutes = Duration.between(startTime, Instant.now()).toMinutes();
            TelemetryEvent event = TelemetryEvent.snapshot(guildCount, activeAudioSessions, uptimeMinutes);
            store.appendEvent(event);
            LOG.debug("Recorded snapshot: guilds={}, audio={}, uptime={}min", 
                    guildCount, activeAudioSessions, uptimeMinutes);
        } catch (Exception e) {
            LOG.warn("Failed to record snapshot: {}", e.getMessage());
        }
    }

    /**
     * Records an error event with sanitized exception information.
     *
     * @param throwable The exception that occurred
     * @param context   Additional context about what was happening (e.g., command name)
     */
    public void recordError(Throwable throwable, Map<String, String> context) {
        if (!enabled) {
            return;
        }
        
        try {
            String errorClass = throwable.getClass().getSimpleName();
            String message = sanitizeMessage(throwable.getMessage());
            String stackTrace = sanitizeStackTrace(throwable);
            
            TelemetryEvent event = TelemetryEvent.error(errorClass, message, stackTrace, context);
            store.appendEvent(event);
            LOG.debug("Recorded error event: {}", errorClass);
        } catch (Exception e) {
            LOG.warn("Failed to record error event: {}", e.getMessage());
        }
    }

    /**
     * Records an error event with sanitized exception information and no additional context.
     *
     * @param throwable The exception that occurred
     */
    public void recordError(Throwable throwable) {
        recordError(throwable, null);
    }

    /**
     * Gets the instance ID for this bot instance.
     *
     * @return The instance ID
     */
    public String getInstanceId() {
        return instanceIdManager.getInstanceId();
    }

    /**
     * Gets the telemetry store.
     *
     * @return The telemetry store
     */
    public TelemetryStore getStore() {
        return store;
    }

    /**
     * Checks if metrics collection is enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sanitizes a stack trace by removing sensitive information.
     */
    private String sanitizeStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        String stackTrace = sw.toString();
        
        // Remove user paths
        stackTrace = USER_PATH_PATTERN.matcher(stackTrace).replaceAll("[USER_PATH]");
        
        // Remove any tokens that might have leaked
        stackTrace = TOKEN_PATTERN.matcher(stackTrace).replaceAll("[REDACTED_TOKEN]");
        
        // Truncate if too long
        if (stackTrace.length() > MAX_STACK_TRACE_LENGTH) {
            stackTrace = stackTrace.substring(0, MAX_STACK_TRACE_LENGTH) + "\n... [truncated]";
        }
        
        return stackTrace;
    }

    /**
     * Sanitizes an error message by removing sensitive information.
     */
    private String sanitizeMessage(String message) {
        if (message == null) {
            return "";
        }
        
        // Remove user paths
        message = USER_PATH_PATTERN.matcher(message).replaceAll("[USER_PATH]");
        
        // Remove any tokens that might have leaked
        message = TOKEN_PATTERN.matcher(message).replaceAll("[REDACTED_TOKEN]");
        
        return message;
    }
}
