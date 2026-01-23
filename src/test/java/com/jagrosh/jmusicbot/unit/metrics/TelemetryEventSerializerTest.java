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
import com.jagrosh.jmusicbot.metrics.store.TelemetryEventSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TelemetryEventSerializer Unit Tests")
class TelemetryEventSerializerTest {

    private TelemetryEventSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new TelemetryEventSerializer();
    }

    @Nested
    @DisplayName("serialize() Tests")
    class SerializeTests {

        @Test
        @DisplayName("serialize() produces valid JSON for startup event")
        void serializeProducesValidJsonForStartup() throws Exception {
            TelemetryEvent event = TelemetryEvent.startup("1.0.0", "21", "Linux");
            
            String json = serializer.serialize(event);
            
            assertNotNull(json);
            assertTrue(json.contains("\"type\":\"startup\""));
            assertTrue(json.contains("\"version\":\"1.0.0\""));
            assertTrue(json.contains("\"javaVersion\":\"21\""));
            assertTrue(json.contains("\"os\":\"Linux\""));
            assertTrue(json.contains("\"timestamp\""));
        }

        @Test
        @DisplayName("serialize() produces valid JSON for snapshot event")
        void serializeProducesValidJsonForSnapshot() throws Exception {
            TelemetryEvent event = TelemetryEvent.snapshot(10, 5, 120);
            
            String json = serializer.serialize(event);
            
            assertNotNull(json);
            assertTrue(json.contains("\"type\":\"snapshot\""));
            assertTrue(json.contains("\"guildCount\":10"));
            assertTrue(json.contains("\"activeAudioSessions\":5"));
            assertTrue(json.contains("\"uptimeMinutes\":120"));
        }

        @Test
        @DisplayName("serialize() produces valid JSON for error event")
        void serializeProducesValidJsonForError() throws Exception {
            TelemetryEvent event = TelemetryEvent.error(
                    "NullPointerException",
                    "Object is null",
                    "at com.test.Main.run()",
                    Map.of("command", "play")
            );
            
            String json = serializer.serialize(event);
            
            assertNotNull(json);
            assertTrue(json.contains("\"type\":\"error\""));
            assertTrue(json.contains("\"errorClass\":\"NullPointerException\""));
            assertTrue(json.contains("\"message\":\"Object is null\""));
        }
    }

    @Nested
    @DisplayName("deserialize() Tests")
    class DeserializeTests {

        @Test
        @DisplayName("deserialize() reconstructs startup event")
        void deserializeReconstructsStartupEvent() throws Exception {
            TelemetryEvent original = TelemetryEvent.startup("2.0.0", "17", "Windows");
            String json = serializer.serialize(original);
            
            TelemetryEvent deserialized = serializer.deserialize(json);
            
            assertNotNull(deserialized);
            assertEquals(TelemetryEvent.EventType.STARTUP, deserialized.getType());
            assertEquals("2.0.0", deserialized.getData().get("version"));
            assertEquals("17", deserialized.getData().get("javaVersion"));
            assertEquals("Windows", deserialized.getData().get("os"));
        }

        @Test
        @DisplayName("deserialize() reconstructs snapshot event")
        void deserializeReconstructsSnapshotEvent() throws Exception {
            TelemetryEvent original = TelemetryEvent.snapshot(25, 10, 3600);
            String json = serializer.serialize(original);
            
            TelemetryEvent deserialized = serializer.deserialize(json);
            
            assertNotNull(deserialized);
            assertEquals(TelemetryEvent.EventType.SNAPSHOT, deserialized.getType());
            assertEquals(25, deserialized.getData().get("guildCount"));
            assertEquals(10, deserialized.getData().get("activeAudioSessions"));
            assertEquals(3600L, ((Number) deserialized.getData().get("uptimeMinutes")).longValue());
        }

        @Test
        @DisplayName("deserialize() preserves timestamp")
        void deserializePreservesTimestamp() throws Exception {
            TelemetryEvent original = TelemetryEvent.startup("1.0.0", "21", "Linux");
            String json = serializer.serialize(original);
            
            TelemetryEvent deserialized = serializer.deserialize(json);
            
            assertNotNull(deserialized);
            assertEquals(original.getTimestamp(), deserialized.getTimestamp());
        }

        @Test
        @DisplayName("deserialize() returns null for invalid JSON")
        void deserializeReturnsNullForInvalidJson() {
            TelemetryEvent result = serializer.deserialize("not valid json");
            assertNull(result);
        }

        @Test
        @DisplayName("deserialize() returns null for missing type")
        void deserializeReturnsNullForMissingType() {
            TelemetryEvent result = serializer.deserialize("{\"timestamp\":\"2026-01-01T00:00:00Z\"}");
            assertNull(result);
        }

        @Test
        @DisplayName("deserialize() returns null for invalid type")
        void deserializeReturnsNullForInvalidType() {
            TelemetryEvent result = serializer.deserialize(
                    "{\"type\":\"invalid\",\"timestamp\":\"2026-01-01T00:00:00Z\"}");
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Round-trip Tests")
    class RoundTripTests {

        @Test
        @DisplayName("Serialize then deserialize preserves all data types")
        void roundTripPreservesAllDataTypes() throws Exception {
            // Test with nested map (context in error event)
            TelemetryEvent original = TelemetryEvent.error(
                    "TestException",
                    "Test message",
                    "stack trace here",
                    Map.of("key1", "value1", "key2", "value2")
            );
            
            String json = serializer.serialize(original);
            TelemetryEvent deserialized = serializer.deserialize(json);
            
            assertNotNull(deserialized);
            assertEquals(original.getType(), deserialized.getType());
            assertEquals(original.getTimestamp(), deserialized.getTimestamp());
            assertEquals("TestException", deserialized.getData().get("errorClass"));
            assertEquals("Test message", deserialized.getData().get("message"));
            assertEquals("stack trace here", deserialized.getData().get("stackTrace"));
            
            @SuppressWarnings("unchecked")
            Map<String, Object> context = (Map<String, Object>) deserialized.getData().get("context");
            assertEquals("value1", context.get("key1"));
            assertEquals("value2", context.get("key2"));
        }

        @Test
        @DisplayName("Round-trip preserves startup event completely")
        void roundTripPreservesStartupEvent() throws Exception {
            TelemetryEvent original = TelemetryEvent.startup("1.2.3", "21.0.1", "Windows 10 amd64");
            
            String json = serializer.serialize(original);
            TelemetryEvent deserialized = serializer.deserialize(json);
            
            assertNotNull(deserialized);
            assertEquals(original.getType(), deserialized.getType());
            assertEquals(original.getTimestamp(), deserialized.getTimestamp());
            assertEquals(original.getData().get("version"), deserialized.getData().get("version"));
            assertEquals(original.getData().get("javaVersion"), deserialized.getData().get("javaVersion"));
            assertEquals(original.getData().get("os"), deserialized.getData().get("os"));
        }

        @Test
        @DisplayName("Round-trip preserves snapshot event completely")
        void roundTripPreservesSnapshotEvent() throws Exception {
            TelemetryEvent original = TelemetryEvent.snapshot(500, 100, 86400);
            
            String json = serializer.serialize(original);
            TelemetryEvent deserialized = serializer.deserialize(json);
            
            assertNotNull(deserialized);
            assertEquals(original.getType(), deserialized.getType());
            assertEquals(original.getTimestamp(), deserialized.getTimestamp());
            assertEquals(original.getData().get("guildCount"), deserialized.getData().get("guildCount"));
            assertEquals(original.getData().get("activeAudioSessions"), deserialized.getData().get("activeAudioSessions"));
            // Long values might be returned as Integer if small enough
            assertEquals(
                    ((Number) original.getData().get("uptimeMinutes")).longValue(),
                    ((Number) deserialized.getData().get("uptimeMinutes")).longValue()
            );
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Handles empty strings")
        void handlesEmptyStrings() throws Exception {
            TelemetryEvent original = TelemetryEvent.startup("", "", "");
            
            String json = serializer.serialize(original);
            TelemetryEvent deserialized = serializer.deserialize(json);
            
            assertNotNull(deserialized);
            assertEquals("", deserialized.getData().get("version"));
            assertEquals("", deserialized.getData().get("javaVersion"));
            assertEquals("", deserialized.getData().get("os"));
        }

        @Test
        @DisplayName("Handles unicode characters")
        void handlesUnicodeCharacters() throws Exception {
            String unicode = "日本語 中文 한국어 🎵🎶";
            TelemetryEvent original = TelemetryEvent.startup(unicode, "21", "Linux");
            
            String json = serializer.serialize(original);
            TelemetryEvent deserialized = serializer.deserialize(json);
            
            assertNotNull(deserialized);
            assertEquals(unicode, deserialized.getData().get("version"));
        }

        @Test
        @DisplayName("Handles special JSON characters in strings")
        void handlesSpecialJsonCharacters() throws Exception {
            String special = "Quote: \" Backslash: \\ Newline: \n Tab: \t";
            TelemetryEvent original = TelemetryEvent.startup(special, "21", "Linux");
            
            String json = serializer.serialize(original);
            TelemetryEvent deserialized = serializer.deserialize(json);
            
            assertNotNull(deserialized);
            assertEquals(special, deserialized.getData().get("version"));
        }

        @Test
        @DisplayName("Handles zero values")
        void handlesZeroValues() throws Exception {
            TelemetryEvent original = TelemetryEvent.snapshot(0, 0, 0);
            
            String json = serializer.serialize(original);
            TelemetryEvent deserialized = serializer.deserialize(json);
            
            assertNotNull(deserialized);
            assertEquals(0, deserialized.getData().get("guildCount"));
            assertEquals(0, deserialized.getData().get("activeAudioSessions"));
            assertEquals(0L, ((Number) deserialized.getData().get("uptimeMinutes")).longValue());
        }

        @Test
        @DisplayName("Handles large numbers")
        void handlesLargeNumbers() throws Exception {
            TelemetryEvent original = TelemetryEvent.snapshot(
                    Integer.MAX_VALUE,
                    Integer.MAX_VALUE,
                    Long.MAX_VALUE
            );
            
            String json = serializer.serialize(original);
            TelemetryEvent deserialized = serializer.deserialize(json);
            
            assertNotNull(deserialized);
            assertEquals(Integer.MAX_VALUE, deserialized.getData().get("guildCount"));
            assertEquals(Integer.MAX_VALUE, deserialized.getData().get("activeAudioSessions"));
            assertEquals(Long.MAX_VALUE, ((Number) deserialized.getData().get("uptimeMinutes")).longValue());
        }

        @Test
        @DisplayName("Handles empty context map")
        void handlesEmptyContextMap() throws Exception {
            TelemetryEvent original = TelemetryEvent.error(
                    "Exception",
                    "message",
                    "stack",
                    Map.of()
            );
            
            String json = serializer.serialize(original);
            TelemetryEvent deserialized = serializer.deserialize(json);
            
            assertNotNull(deserialized);
            @SuppressWarnings("unchecked")
            Map<String, Object> context = (Map<String, Object>) deserialized.getData().get("context");
            assertTrue(context.isEmpty());
        }

        @Test
        @DisplayName("Handles very long strings")
        void handlesVeryLongStrings() throws Exception {
            String longString = "x".repeat(10000);
            TelemetryEvent original = TelemetryEvent.error(
                    "Exception",
                    longString,
                    longString,
                    null
            );
            
            String json = serializer.serialize(original);
            TelemetryEvent deserialized = serializer.deserialize(json);
            
            assertNotNull(deserialized);
            assertEquals(longString, deserialized.getData().get("message"));
            assertEquals(longString, deserialized.getData().get("stackTrace"));
        }
    }

    @Nested
    @DisplayName("Deserialization Error Handling")
    class DeserializationErrorHandling {

        @Test
        @DisplayName("Returns null for empty string")
        void returnsNullForEmptyString() {
            assertNull(serializer.deserialize(""));
        }

        @Test
        @DisplayName("Returns null for null input")
        void returnsNullForNullInput() {
            assertNull(serializer.deserialize(null));
        }

        @Test
        @DisplayName("Returns null for missing timestamp")
        void returnsNullForMissingTimestamp() {
            assertNull(serializer.deserialize("{\"type\":\"startup\"}"));
        }

        @Test
        @DisplayName("Returns null for invalid timestamp format")
        void returnsNullForInvalidTimestampFormat() {
            assertNull(serializer.deserialize(
                    "{\"type\":\"startup\",\"timestamp\":\"not-a-timestamp\"}"));
        }

        @Test
        @DisplayName("Returns null for JSON array instead of object")
        void returnsNullForJsonArray() {
            assertNull(serializer.deserialize("[1, 2, 3]"));
        }
    }
}
