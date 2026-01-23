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
package com.jagrosh.jmusicbot.metrics.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jagrosh.jmusicbot.metrics.model.TelemetryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles serialization and deserialization of TelemetryEvent objects to/from JSON.
 * 
 * <p>This class encapsulates all JSON-related logic for telemetry events,
 * providing a clean separation between the event model and its persistence format.
 *
 * @author John Grosh (jagrosh)
 */
public class TelemetryEventSerializer {
    private static final Logger LOG = LoggerFactory.getLogger(TelemetryEventSerializer.class);

    private final ObjectMapper objectMapper;

    /**
     * Creates a new TelemetryEventSerializer with default ObjectMapper configuration.
     */
    public TelemetryEventSerializer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Creates a new TelemetryEventSerializer with a custom ObjectMapper.
     *
     * @param objectMapper The ObjectMapper to use
     */
    public TelemetryEventSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Serializes a TelemetryEvent to JSON.
     *
     * @param event The event to serialize
     * @return JSON string representation of the event
     * @throws JsonProcessingException If serialization fails
     */
    public String serialize(TelemetryEvent event) throws JsonProcessingException {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", event.getType().name().toLowerCase());
        node.put("timestamp", event.getTimestamp().toString());
        
        for (var entry : event.getData().entrySet()) {
            addValueToNode(node, entry.getKey(), entry.getValue());
        }
        
        return objectMapper.writeValueAsString(node);
    }

    /**
     * Deserializes a JSON string to a TelemetryEvent.
     *
     * @param json The JSON string to deserialize
     * @return The deserialized TelemetryEvent, or null if deserialization fails
     */
    public TelemetryEvent deserialize(String json) {
        try {
            ObjectNode node = (ObjectNode) objectMapper.readTree(json);
            
            String typeStr = node.get("type").asText();
            TelemetryEvent.EventType type = TelemetryEvent.EventType.valueOf(typeStr.toUpperCase());
            Instant timestamp = Instant.parse(node.get("timestamp").asText());
            
            Map<String, Object> data = new HashMap<>();
            node.properties().forEach(entry -> {
                if (!entry.getKey().equals("type") && !entry.getKey().equals("timestamp")) {
                    data.put(entry.getKey(), nodeToValue(entry.getValue()));
                }
            });
            
            return new TelemetryEvent(type, timestamp, data);
        } catch (Exception e) {
            LOG.warn("Failed to deserialize telemetry event: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Gets the underlying ObjectMapper.
     *
     * @return The ObjectMapper instance
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @SuppressWarnings("unchecked")
    private void addValueToNode(ObjectNode node, String key, Object value) {
        if (value == null) {
            node.putNull(key);
        } else if (value instanceof String s) {
            node.put(key, s);
        } else if (value instanceof Integer i) {
            node.put(key, i);
        } else if (value instanceof Long l) {
            node.put(key, l);
        } else if (value instanceof Double d) {
            node.put(key, d);
        } else if (value instanceof Boolean b) {
            node.put(key, b);
        } else if (value instanceof Map) {
            ObjectNode childNode = objectMapper.createObjectNode();
            ((Map<String, Object>) value).forEach((k, v) -> addValueToNode(childNode, k, v));
            node.set(key, childNode);
        } else {
            node.put(key, value.toString());
        }
    }

    private Object nodeToValue(com.fasterxml.jackson.databind.JsonNode node) {
        if (node.isNull()) {
            return null;
        } else if (node.isTextual()) {
            return node.asText();
        } else if (node.isInt()) {
            return node.asInt();
        } else if (node.isLong()) {
            return node.asLong();
        } else if (node.isDouble()) {
            return node.asDouble();
        } else if (node.isBoolean()) {
            return node.asBoolean();
        } else if (node.isObject()) {
            Map<String, Object> map = new HashMap<>();
            node.properties().forEach(entry -> map.put(entry.getKey(), nodeToValue(entry.getValue())));
            return map;
        } else {
            return node.toString();
        }
    }
}
