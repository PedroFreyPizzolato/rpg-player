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
package com.jagrosh.jmusicbot.unit.metrics;

import com.jagrosh.jmusicbot.metrics.InstanceIdManager;
import com.jagrosh.jmusicbot.metrics.MetricsCollector;
import com.jagrosh.jmusicbot.metrics.model.TelemetryEvent;
import com.jagrosh.jmusicbot.metrics.store.TelemetryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MetricsCollector Unit Tests")
class MetricsCollectorTest {

    @TempDir
    Path tempDir;

    private TelemetryStore store;
    private InstanceIdManager instanceIdManager;
    private MetricsCollector collector;

    @BeforeEach
    void setUp() {
        store = new TelemetryStore(tempDir);
        store.init();
        instanceIdManager = new InstanceIdManager(tempDir);
        instanceIdManager.init();
        collector = new MetricsCollector(store, instanceIdManager, true);
    }

    @Nested
    @DisplayName("Enabled/Disabled State Tests")
    class EnabledDisabledTests {

        @Test
        @DisplayName("isEnabled() returns true when enabled")
        void isEnabledReturnsTrueWhenEnabled() {
            assertTrue(collector.isEnabled());
        }

        @Test
        @DisplayName("isEnabled() returns false when disabled")
        void isEnabledReturnsFalseWhenDisabled() {
            MetricsCollector disabledCollector = new MetricsCollector(store, instanceIdManager, false);
            assertFalse(disabledCollector.isEnabled());
        }

        @Test
        @DisplayName("Disabled collector does not record events")
        void disabledCollectorDoesNotRecordEvents() {
            MetricsCollector disabledCollector = new MetricsCollector(store, instanceIdManager, false);
            
            disabledCollector.recordStartup();
            disabledCollector.recordSnapshot(5, 2);
            disabledCollector.recordError(new RuntimeException("test"));
            
            assertFalse(store.hasEvents());
        }
    }

    @Nested
    @DisplayName("recordStartup() Tests")
    class RecordStartupTests {

        @Test
        @DisplayName("recordStartup() stores a startup event")
        void recordStartupStoresEvent() {
            collector.recordStartup();
            
            List<TelemetryEvent> events = store.readAllEvents();
            assertEquals(1, events.size());
            assertEquals(TelemetryEvent.EventType.STARTUP, events.get(0).getType());
        }

        @Test
        @DisplayName("recordStartup() includes version information")
        void recordStartupIncludesVersionInfo() {
            collector.recordStartup();
            
            List<TelemetryEvent> events = store.readAllEvents();
            TelemetryEvent event = events.get(0);
            assertNotNull(event.getData().get("version"));
            assertNotNull(event.getData().get("javaVersion"));
            assertNotNull(event.getData().get("os"));
        }
    }

    @Nested
    @DisplayName("recordSnapshot() Tests")
    class RecordSnapshotTests {

        @Test
        @DisplayName("recordSnapshot() stores a snapshot event")
        void recordSnapshotStoresEvent() {
            collector.recordSnapshot(10, 3);
            
            List<TelemetryEvent> events = store.readAllEvents();
            assertEquals(1, events.size());
            assertEquals(TelemetryEvent.EventType.SNAPSHOT, events.get(0).getType());
        }

        @Test
        @DisplayName("recordSnapshot() includes guild and audio session counts")
        void recordSnapshotIncludesCounts() {
            collector.recordSnapshot(15, 5);
            
            List<TelemetryEvent> events = store.readAllEvents();
            TelemetryEvent event = events.get(0);
            assertEquals(15, event.getData().get("guildCount"));
            assertEquals(5, event.getData().get("activeAudioSessions"));
        }

        @Test
        @DisplayName("recordSnapshot() includes uptime")
        void recordSnapshotIncludesUptime() {
            collector.recordSnapshot(10, 3);
            
            List<TelemetryEvent> events = store.readAllEvents();
            TelemetryEvent event = events.get(0);
            assertNotNull(event.getData().get("uptimeMinutes"));
        }
    }

    @Nested
    @DisplayName("recordError() Tests")
    class RecordErrorTests {

        @Test
        @DisplayName("recordError() stores an error event")
        void recordErrorStoresEvent() {
            collector.recordError(new RuntimeException("test error"));
            
            List<TelemetryEvent> events = store.readAllEvents();
            assertEquals(1, events.size());
            assertEquals(TelemetryEvent.EventType.ERROR, events.get(0).getType());
        }

        @Test
        @DisplayName("recordError() includes exception details")
        void recordErrorIncludesExceptionDetails() {
            collector.recordError(new NullPointerException("null value"));
            
            List<TelemetryEvent> events = store.readAllEvents();
            TelemetryEvent event = events.get(0);
            assertEquals("NullPointerException", event.getData().get("errorClass"));
            assertTrue(((String) event.getData().get("message")).contains("null value"));
        }

        @Test
        @DisplayName("recordError() includes context when provided")
        void recordErrorIncludesContext() {
            collector.recordError(
                    new RuntimeException("test"),
                    Map.of("command", "play", "source", "youtube")
            );
            
            List<TelemetryEvent> events = store.readAllEvents();
            TelemetryEvent event = events.get(0);
            @SuppressWarnings("unchecked")
            Map<String, String> context = (Map<String, String>) event.getData().get("context");
            assertEquals("play", context.get("command"));
            assertEquals("youtube", context.get("source"));
        }

        @Test
        @DisplayName("recordError() sanitizes user paths in stack traces")
        void recordErrorSanitizesUserPaths() {
            RuntimeException ex = new RuntimeException("Error at /home/username/secret/file.txt");
            collector.recordError(ex);
            
            List<TelemetryEvent> events = store.readAllEvents();
            TelemetryEvent event = events.get(0);
            String stackTrace = (String) event.getData().get("stackTrace");
            assertFalse(stackTrace.contains("/home/username"));
            assertTrue(stackTrace.contains("[USER_PATH]"));
        }

        @Test
        @DisplayName("recordError() sanitizes tokens in messages")
        void recordErrorSanitizesTokens() {
            // A Discord bot token has a specific format
            String fakeToken = "MTIzNDU2Nzg5MDEyMzQ1Njc4.ABC123.abcdefghijklmnopqrstuvwxyz1234";
            RuntimeException ex = new RuntimeException("Token: " + fakeToken);
            collector.recordError(ex);
            
            List<TelemetryEvent> events = store.readAllEvents();
            TelemetryEvent event = events.get(0);
            String message = (String) event.getData().get("message");
            assertFalse(message.contains(fakeToken));
            assertTrue(message.contains("[REDACTED_TOKEN]"));
        }
    }

    @Nested
    @DisplayName("getInstanceId() Tests")
    class GetInstanceIdTests {

        @Test
        @DisplayName("getInstanceId() returns the instance ID")
        void getInstanceIdReturnsInstanceId() {
            String id = collector.getInstanceId();
            assertNotNull(id);
            assertEquals(instanceIdManager.getInstanceId(), id);
        }
    }

    @Nested
    @DisplayName("getStore() Tests")
    class GetStoreTests {

        @Test
        @DisplayName("getStore() returns the telemetry store")
        void getStoreReturnsStore() {
            assertEquals(store, collector.getStore());
        }
    }
}
