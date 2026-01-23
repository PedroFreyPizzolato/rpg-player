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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TelemetryEvent Unit Tests")
class TelemetryEventTest {

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("startup() creates STARTUP event with correct data")
        void startupCreatesCorrectEvent() {
            TelemetryEvent event = TelemetryEvent.startup("1.0.0", "21", "Linux x64");
            
            assertEquals(TelemetryEvent.EventType.STARTUP, event.getType());
            assertNotNull(event.getTimestamp());
            assertEquals("1.0.0", event.getData().get("version"));
            assertEquals("21", event.getData().get("javaVersion"));
            assertEquals("Linux x64", event.getData().get("os"));
        }

        @Test
        @DisplayName("snapshot() creates SNAPSHOT event with correct data")
        void snapshotCreatesCorrectEvent() {
            TelemetryEvent event = TelemetryEvent.snapshot(100, 25, 1440);
            
            assertEquals(TelemetryEvent.EventType.SNAPSHOT, event.getType());
            assertNotNull(event.getTimestamp());
            assertEquals(100, event.getData().get("guildCount"));
            assertEquals(25, event.getData().get("activeAudioSessions"));
            assertEquals(1440L, event.getData().get("uptimeMinutes"));
        }

        @Test
        @DisplayName("error() creates ERROR event with correct data")
        void errorCreatesCorrectEvent() {
            Map<String, String> context = Map.of("command", "play", "guild", "123");
            TelemetryEvent event = TelemetryEvent.error(
                    "NullPointerException",
                    "Value is null",
                    "at com.test.Main.run(Main.java:42)",
                    context
            );
            
            assertEquals(TelemetryEvent.EventType.ERROR, event.getType());
            assertNotNull(event.getTimestamp());
            assertEquals("NullPointerException", event.getData().get("errorClass"));
            assertEquals("Value is null", event.getData().get("message"));
            assertEquals("at com.test.Main.run(Main.java:42)", event.getData().get("stackTrace"));
            assertNotNull(event.getData().get("context"));
        }

        @Test
        @DisplayName("error() handles null message gracefully")
        void errorHandlesNullMessage() {
            TelemetryEvent event = TelemetryEvent.error(
                    "Exception",
                    null,
                    "stack trace",
                    null
            );
            
            assertEquals("", event.getData().get("message"));
            assertNotNull(event.getData().get("context"));
        }

        @Test
        @DisplayName("error() handles null context gracefully")
        void errorHandlesNullContext() {
            TelemetryEvent event = TelemetryEvent.error(
                    "Exception",
                    "message",
                    "stack trace",
                    null
            );
            
            assertNotNull(event.getData().get("context"));
            assertTrue(((Map<?, ?>) event.getData().get("context")).isEmpty());
        }
    }

    @Nested
    @DisplayName("Timestamp Tests")
    class TimestampTests {

        @Test
        @DisplayName("Timestamp is set to current time")
        void timestampIsCurrentTime() {
            Instant before = Instant.now();
            TelemetryEvent event = TelemetryEvent.startup("1.0.0", "21", "Linux");
            Instant after = Instant.now();
            
            assertFalse(event.getTimestamp().isBefore(before));
            assertFalse(event.getTimestamp().isAfter(after));
        }

        @Test
        @DisplayName("Each event gets unique timestamp")
        void eachEventGetsUniqueTimestamp() throws InterruptedException {
            TelemetryEvent event1 = TelemetryEvent.startup("1.0.0", "21", "Linux");
            Thread.sleep(10);
            TelemetryEvent event2 = TelemetryEvent.startup("1.0.0", "21", "Linux");
            
            assertNotEquals(event1.getTimestamp(), event2.getTimestamp());
        }
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Constructor preserves all parameters")
        void constructorPreservesParameters() {
            Instant timestamp = Instant.parse("2026-01-15T10:30:00Z");
            Map<String, Object> data = Map.of("key1", "value1", "key2", 42);
            
            TelemetryEvent event = new TelemetryEvent(
                    TelemetryEvent.EventType.SNAPSHOT,
                    timestamp,
                    data
            );
            
            assertEquals(TelemetryEvent.EventType.SNAPSHOT, event.getType());
            assertEquals(timestamp, event.getTimestamp());
            assertEquals("value1", event.getData().get("key1"));
            assertEquals(42, event.getData().get("key2"));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Handles empty strings in data")
        void handlesEmptyStrings() {
            TelemetryEvent event = TelemetryEvent.startup("", "", "");
            
            assertEquals("", event.getData().get("version"));
            assertEquals("", event.getData().get("javaVersion"));
            assertEquals("", event.getData().get("os"));
        }

        @Test
        @DisplayName("Handles special characters in strings")
        void handlesSpecialCharacters() {
            String specialChars = "Test with émojis 🎵 and special chars: <>&\"'\\n\\t";
            TelemetryEvent event = TelemetryEvent.startup(specialChars, "21", "Linux");
            
            assertEquals(specialChars, event.getData().get("version"));
        }

        @Test
        @DisplayName("Handles unicode characters")
        void handlesUnicodeCharacters() {
            String unicode = "日本語 中文 한국어 العربية";
            TelemetryEvent event = TelemetryEvent.startup(unicode, "21", "Linux");
            
            assertEquals(unicode, event.getData().get("version"));
        }

        @Test
        @DisplayName("Handles zero values in snapshot")
        void handlesZeroValuesInSnapshot() {
            TelemetryEvent event = TelemetryEvent.snapshot(0, 0, 0);
            
            assertEquals(0, event.getData().get("guildCount"));
            assertEquals(0, event.getData().get("activeAudioSessions"));
            assertEquals(0L, event.getData().get("uptimeMinutes"));
        }

        @Test
        @DisplayName("Handles large numbers in snapshot")
        void handlesLargeNumbers() {
            TelemetryEvent event = TelemetryEvent.snapshot(
                    Integer.MAX_VALUE,
                    Integer.MAX_VALUE,
                    Long.MAX_VALUE
            );
            
            assertEquals(Integer.MAX_VALUE, event.getData().get("guildCount"));
            assertEquals(Integer.MAX_VALUE, event.getData().get("activeAudioSessions"));
            assertEquals(Long.MAX_VALUE, event.getData().get("uptimeMinutes"));
        }

        @Test
        @DisplayName("Handles very long stack trace")
        void handlesVeryLongStackTrace() {
            StringBuilder longTrace = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                longTrace.append("at com.example.Class").append(i).append(".method(Class.java:").append(i).append(")\n");
            }
            
            TelemetryEvent event = TelemetryEvent.error(
                    "Exception",
                    "message",
                    longTrace.toString(),
                    null
            );
            
            assertEquals(longTrace.toString(), event.getData().get("stackTrace"));
        }
    }
}
