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

import com.jagrosh.jmusicbot.metrics.model.TelemetryEvent;
import com.jagrosh.jmusicbot.metrics.store.TelemetryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TelemetryStore Unit Tests")
class TelemetryStoreTest {

    @TempDir
    Path tempDir;
    
    private TelemetryStore store;

    @BeforeEach
    void setUp() {
        store = new TelemetryStore(tempDir);
        store.init();
    }

    @Nested
    @DisplayName("init() Tests")
    class InitTests {

        @Test
        @DisplayName("init() creates data directory if it doesn't exist")
        void initCreatesDataDirectory() {
            Path nestedDir = tempDir.resolve("nested").resolve("store");
            TelemetryStore nestedStore = new TelemetryStore(nestedDir);
            
            nestedStore.init();
            
            assertTrue(Files.exists(nestedDir));
        }

        @Test
        @DisplayName("init() loads last collection ID from metadata")
        void initLoadsLastCollectionIdFromMetadata() throws IOException {
            Path metaFile = tempDir.resolve("telemetry-meta.json");
            Files.writeString(metaFile, "{\"lastCollectionId\":\"test-collection-123\"}");
            
            TelemetryStore newStore = new TelemetryStore(tempDir);
            newStore.init();
            
            assertEquals("test-collection-123", newStore.getLastCollectionId());
        }
    }

    @Nested
    @DisplayName("appendEvent() and readAllEvents() Tests")
    class AppendAndReadTests {

        @Test
        @DisplayName("appendEvent() creates events file if it doesn't exist")
        void appendEventCreatesFile() {
            TelemetryEvent event = TelemetryEvent.startup("1.0.0", "21", "Linux");
            
            store.appendEvent(event);
            
            Path eventsFile = tempDir.resolve("telemetry.bin");
            assertTrue(Files.exists(eventsFile));
        }

        @Test
        @DisplayName("readAllEvents() returns empty list when no events")
        void readAllEventsReturnsEmptyListWhenNoEvents() {
            List<TelemetryEvent> events = store.readAllEvents();
            assertTrue(events.isEmpty());
        }

        @Test
        @DisplayName("appendEvent() and readAllEvents() work together")
        void appendAndReadWorkTogether() {
            store.appendEvent(TelemetryEvent.startup("1.0.0", "21", "Linux"));
            store.appendEvent(TelemetryEvent.snapshot(5, 2, 60));
            store.appendEvent(TelemetryEvent.snapshot(6, 3, 90));
            
            List<TelemetryEvent> events = store.readAllEvents();
            
            assertEquals(3, events.size());
            assertEquals(TelemetryEvent.EventType.STARTUP, events.get(0).getType());
            assertEquals(TelemetryEvent.EventType.SNAPSHOT, events.get(1).getType());
            assertEquals(TelemetryEvent.EventType.SNAPSHOT, events.get(2).getType());
        }

        @Test
        @DisplayName("readAllEvents() preserves event data")
        void readAllEventsPreservesEventData() {
            store.appendEvent(TelemetryEvent.snapshot(10, 5, 120));
            
            List<TelemetryEvent> events = store.readAllEvents();
            
            assertEquals(1, events.size());
            TelemetryEvent event = events.get(0);
            assertEquals(10, event.getData().get("guildCount"));
            assertEquals(5, event.getData().get("activeAudioSessions"));
            assertEquals(120L, ((Number) event.getData().get("uptimeMinutes")).longValue());
        }
    }

    @Nested
    @DisplayName("clearEventsAfterCollection() Tests")
    class ClearEventsTests {

        @Test
        @DisplayName("clearEventsAfterCollection() deletes events file")
        void clearEventsDeletesFile() {
            store.appendEvent(TelemetryEvent.startup("1.0.0", "21", "Linux"));
            
            store.clearEventsAfterCollection("collection-1");
            
            Path eventsFile = tempDir.resolve("telemetry.bin");
            assertFalse(Files.exists(eventsFile));
        }

        @Test
        @DisplayName("clearEventsAfterCollection() updates last collection ID")
        void clearEventsUpdatesLastCollectionId() {
            store.appendEvent(TelemetryEvent.startup("1.0.0", "21", "Linux"));
            
            store.clearEventsAfterCollection("collection-xyz");
            
            assertEquals("collection-xyz", store.getLastCollectionId());
        }

        @Test
        @DisplayName("clearEventsAfterCollection() persists collection ID to disk")
        void clearEventsPersistsCollectionId() throws IOException {
            store.appendEvent(TelemetryEvent.startup("1.0.0", "21", "Linux"));
            store.clearEventsAfterCollection("persisted-collection");
            
            TelemetryStore newStore = new TelemetryStore(tempDir);
            newStore.init();
            
            assertEquals("persisted-collection", newStore.getLastCollectionId());
        }
    }

    @Nested
    @DisplayName("hasEvents() Tests")
    class HasEventsTests {

        @Test
        @DisplayName("hasEvents() returns false when no events")
        void hasEventsReturnsFalseWhenNoEvents() {
            assertFalse(store.hasEvents());
        }

        @Test
        @DisplayName("hasEvents() returns true when events exist")
        void hasEventsReturnsTrueWhenEventsExist() {
            store.appendEvent(TelemetryEvent.startup("1.0.0", "21", "Linux"));
            assertTrue(store.hasEvents());
        }

        @Test
        @DisplayName("hasEvents() returns false after clear")
        void hasEventsReturnsFalseAfterClear() {
            store.appendEvent(TelemetryEvent.startup("1.0.0", "21", "Linux"));
            store.clearEventsAfterCollection("test-collection");
            assertFalse(store.hasEvents());
        }
    }

    @Nested
    @DisplayName("pruneOldEvents() Tests")
    class PruneOldEventsTests {

        @Test
        @DisplayName("pruneOldEvents() keeps recent events")
        void pruneOldEventsKeepsRecentEvents() {
            store.appendEvent(TelemetryEvent.startup("1.0.0", "21", "Linux"));
            store.appendEvent(TelemetryEvent.snapshot(5, 2, 60));
            
            store.pruneOldEvents();
            
            List<TelemetryEvent> events = store.readAllEvents();
            assertEquals(2, events.size());
        }

        @Test
        @DisplayName("pruneOldEvents() handles empty store")
        void pruneOldEventsHandlesEmptyStore() {
            assertDoesNotThrow(() -> store.pruneOldEvents());
        }
    }

    @Nested
    @DisplayName("Error Event Tests")
    class ErrorEventTests {

        @Test
        @DisplayName("Can store and retrieve error events")
        void canStoreAndRetrieveErrorEvents() {
            TelemetryEvent errorEvent = TelemetryEvent.error(
                    "NullPointerException",
                    "Object is null",
                    "at com.test.Method.run(Method.java:42)",
                    Map.of("command", "play")
            );
            
            store.appendEvent(errorEvent);
            
            List<TelemetryEvent> events = store.readAllEvents();
            assertEquals(1, events.size());
            TelemetryEvent retrieved = events.get(0);
            assertEquals(TelemetryEvent.EventType.ERROR, retrieved.getType());
            assertEquals("NullPointerException", retrieved.getData().get("errorClass"));
            assertEquals("Object is null", retrieved.getData().get("message"));
        }
    }

    @Nested
    @DisplayName("Component Integration Tests")
    class ComponentIntegrationTests {

        @Test
        @DisplayName("Store uses CompressedRecordFile for storage")
        void storeUsesCompressedRecordFile() throws IOException {
            store.appendEvent(TelemetryEvent.startup("1.0.0", "21", "Linux"));
            
            // Verify the file has the magic header
            Path eventsFile = tempDir.resolve("telemetry.bin");
            byte[] bytes = Files.readAllBytes(eventsFile);
            
            // Magic bytes: 'T', 'L', 'M', 0x01
            assertEquals('T', bytes[0]);
            assertEquals('L', bytes[1]);
            assertEquals('M', bytes[2]);
            assertEquals(0x01, bytes[3]);
        }

        @Test
        @DisplayName("Multiple events compress efficiently")
        void multipleEventsCompressEfficiently() throws IOException {
            for (int i = 0; i < 50; i++) {
                store.appendEvent(TelemetryEvent.snapshot(i, i / 2, i * 30L));
            }
            
            List<TelemetryEvent> events = store.readAllEvents();
            assertEquals(50, events.size());
            
            // Verify data integrity
            for (int i = 0; i < 50; i++) {
                assertEquals(i, events.get(i).getData().get("guildCount"));
            }
        }
    }

    @Nested
    @DisplayName("End-to-End Integration Tests")
    class EndToEndIntegrationTests {

        @Test
        @DisplayName("Full lifecycle: append, read, clear, verify empty")
        void fullLifecycle() {
            // Append events
            store.appendEvent(TelemetryEvent.startup("1.0.0", "21", "Linux"));
            store.appendEvent(TelemetryEvent.snapshot(10, 5, 60));
            
            // Verify they exist
            assertTrue(store.hasEvents());
            assertEquals(2, store.readAllEvents().size());
            
            // Clear after collection
            store.clearEventsAfterCollection("collection-1");
            
            // Verify empty
            assertFalse(store.hasEvents());
            assertTrue(store.readAllEvents().isEmpty());
            assertEquals("collection-1", store.getLastCollectionId());
        }

        @Test
        @DisplayName("Data survives store re-initialization")
        void dataSurvivesReinitialization() {
            // Store events
            store.appendEvent(TelemetryEvent.startup("1.0.0", "21", "Linux"));
            store.appendEvent(TelemetryEvent.snapshot(10, 5, 60));
            
            // Create new store pointing to same directory
            TelemetryStore newStore = new TelemetryStore(tempDir);
            newStore.init();
            
            // Verify data is still there
            List<TelemetryEvent> events = newStore.readAllEvents();
            assertEquals(2, events.size());
            assertEquals(TelemetryEvent.EventType.STARTUP, events.get(0).getType());
            assertEquals(TelemetryEvent.EventType.SNAPSHOT, events.get(1).getType());
        }

        @Test
        @DisplayName("Collection ID survives store re-initialization")
        void collectionIdSurvivesReinitialization() {
            store.appendEvent(TelemetryEvent.startup("1.0.0", "21", "Linux"));
            store.clearEventsAfterCollection("persisted-id");
            
            // Create new store pointing to same directory
            TelemetryStore newStore = new TelemetryStore(tempDir);
            newStore.init();
            
            assertEquals("persisted-id", newStore.getLastCollectionId());
        }

        @Test
        @DisplayName("Mixed event types preserve all data")
        void mixedEventTypesPreserveAllData() {
            // Add one of each type
            store.appendEvent(TelemetryEvent.startup("1.0.0", "21", "Linux"));
            store.appendEvent(TelemetryEvent.snapshot(100, 50, 1440));
            store.appendEvent(TelemetryEvent.error(
                    "NullPointerException",
                    "Object is null",
                    "at com.example.Test.run()",
                    Map.of("command", "play")
            ));
            
            List<TelemetryEvent> events = store.readAllEvents();
            assertEquals(3, events.size());
            
            // Verify startup
            TelemetryEvent startup = events.get(0);
            assertEquals(TelemetryEvent.EventType.STARTUP, startup.getType());
            assertEquals("1.0.0", startup.getData().get("version"));
            
            // Verify snapshot
            TelemetryEvent snapshot = events.get(1);
            assertEquals(TelemetryEvent.EventType.SNAPSHOT, snapshot.getType());
            assertEquals(100, snapshot.getData().get("guildCount"));
            
            // Verify error
            TelemetryEvent error = events.get(2);
            assertEquals(TelemetryEvent.EventType.ERROR, error.getType());
            assertEquals("NullPointerException", error.getData().get("errorClass"));
            @SuppressWarnings("unchecked")
            Map<String, Object> context = (Map<String, Object>) error.getData().get("context");
            assertEquals("play", context.get("command"));
        }

        @Test
        @DisplayName("Large number of events maintains integrity")
        void largeNumberOfEventsMaintainsIntegrity() {
            int eventCount = 200;
            
            // Add many events
            store.appendEvent(TelemetryEvent.startup("1.0.0", "21", "Linux"));
            for (int i = 0; i < eventCount - 1; i++) {
                store.appendEvent(TelemetryEvent.snapshot(i, i / 2, i * 30L));
            }
            
            // Verify all events
            List<TelemetryEvent> events = store.readAllEvents();
            assertEquals(eventCount, events.size());
            
            // Verify first event is startup
            assertEquals(TelemetryEvent.EventType.STARTUP, events.get(0).getType());
            
            // Verify snapshot data integrity
            for (int i = 1; i < eventCount; i++) {
                TelemetryEvent event = events.get(i);
                assertEquals(TelemetryEvent.EventType.SNAPSHOT, event.getType());
                assertEquals(i - 1, event.getData().get("guildCount"));
            }
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Handles unicode in events")
        void handlesUnicodeInEvents() {
            String unicode = "日本語 中文 🎵🎶";
            store.appendEvent(TelemetryEvent.startup(unicode, "21", "Linux"));
            
            List<TelemetryEvent> events = store.readAllEvents();
            assertEquals(1, events.size());
            assertEquals(unicode, events.get(0).getData().get("version"));
        }

        @Test
        @DisplayName("Handles empty error message")
        void handlesEmptyErrorMessage() {
            store.appendEvent(TelemetryEvent.error(
                    "Exception",
                    "",
                    "stack trace",
                    null
            ));
            
            List<TelemetryEvent> events = store.readAllEvents();
            assertEquals(1, events.size());
            assertEquals("", events.get(0).getData().get("message"));
        }

        @Test
        @DisplayName("Handles very long stack traces")
        void handlesVeryLongStackTraces() {
            StringBuilder longTrace = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                longTrace.append("at com.example.Class").append(i).append(".method(Class.java:").append(i).append(")\n");
            }
            
            store.appendEvent(TelemetryEvent.error(
                    "Exception",
                    "message",
                    longTrace.toString(),
                    null
            ));
            
            List<TelemetryEvent> events = store.readAllEvents();
            assertEquals(1, events.size());
            assertEquals(longTrace.toString(), events.get(0).getData().get("stackTrace"));
        }

        @Test
        @DisplayName("Multiple clear operations work correctly")
        void multipleClearOperationsWork() {
            store.appendEvent(TelemetryEvent.startup("1.0.0", "21", "Linux"));
            store.clearEventsAfterCollection("collection-1");
            assertEquals("collection-1", store.getLastCollectionId());
            
            store.appendEvent(TelemetryEvent.snapshot(10, 5, 60));
            store.clearEventsAfterCollection("collection-2");
            assertEquals("collection-2", store.getLastCollectionId());
            
            store.appendEvent(TelemetryEvent.snapshot(20, 10, 120));
            store.clearEventsAfterCollection("collection-3");
            assertEquals("collection-3", store.getLastCollectionId());
            
            assertFalse(store.hasEvents());
        }
    }

    @Nested
    @DisplayName("Thread Safety Tests")
    class ThreadSafetyTests {

        @Test
        @DisplayName("Concurrent appends don't lose events")
        void concurrentAppendsWork() throws Exception {
            int numThreads = 5;
            int eventsPerThread = 20;
            Thread[] threads = new Thread[numThreads];
            
            for (int t = 0; t < numThreads; t++) {
                final int threadId = t;
                threads[t] = new Thread(() -> {
                    for (int i = 0; i < eventsPerThread; i++) {
                        store.appendEvent(TelemetryEvent.snapshot(threadId * 100 + i, i, i * 30L));
                    }
                });
            }
            
            for (Thread thread : threads) {
                thread.start();
            }
            for (Thread thread : threads) {
                thread.join();
            }
            
            List<TelemetryEvent> events = store.readAllEvents();
            assertEquals(numThreads * eventsPerThread, events.size());
        }
    }
}
