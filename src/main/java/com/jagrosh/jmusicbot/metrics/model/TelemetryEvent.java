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
package com.jagrosh.jmusicbot.metrics.model;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a single telemetry event to be stored and eventually sent.
 * Events are serialized to JSON format for storage and transmission.
 *
 * @author John Grosh (jagrosh)
 */
public class TelemetryEvent {
    
    /**
     * Types of telemetry events that can be recorded.
     */
    public enum EventType {
        STARTUP,
        SNAPSHOT,
        ERROR
    }

    private final EventType type;
    private final Instant timestamp;
    private final Map<String, Object> data;

    /**
     * Creates a new telemetry event.
     *
     * @param type      The type of event
     * @param timestamp When the event occurred
     * @param data      Additional event data
     */
    public TelemetryEvent(EventType type, Instant timestamp, Map<String, Object> data) {
        this.type = type;
        this.timestamp = timestamp;
        this.data = data;
    }

    /**
     * Creates a startup event with version and system information.
     */
    public static TelemetryEvent startup(String version, String javaVersion, String os) {
        return new TelemetryEvent(EventType.STARTUP, Instant.now(), Map.of(
                "version", version,
                "javaVersion", javaVersion,
                "os", os
        ));
    }

    /**
     * Creates a snapshot event with current bot state.
     */
    public static TelemetryEvent snapshot(int guildCount, int activeAudioSessions, long uptimeMinutes) {
        return new TelemetryEvent(EventType.SNAPSHOT, Instant.now(), Map.of(
                "guildCount", guildCount,
                "activeAudioSessions", activeAudioSessions,
                "uptimeMinutes", uptimeMinutes
        ));
    }

    /**
     * Creates an error event with exception information.
     */
    public static TelemetryEvent error(String errorClass, String message, String stackTrace, Map<String, String> context) {
        return new TelemetryEvent(EventType.ERROR, Instant.now(), Map.of(
                "errorClass", errorClass,
                "message", message != null ? message : "",
                "stackTrace", stackTrace,
                "context", context != null ? context : Map.of()
        ));
    }

    public EventType getType() {
        return type;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
