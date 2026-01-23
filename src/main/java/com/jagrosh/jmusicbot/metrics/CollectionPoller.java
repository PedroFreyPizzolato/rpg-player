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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jagrosh.jmusicbot.metrics.model.TelemetryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Polls a manifest URL periodically to check for telemetry collection requests.
 * When a new collection is detected (based on collectionId), sends accumulated
 * telemetry data to the specified endpoint.
 *
 * @author John Grosh (jagrosh)
 */
public class CollectionPoller {
    private static final Logger LOG = LoggerFactory.getLogger(CollectionPoller.class);
    
    // Polling interval: 45 minutes (randomized between 30-60 min effectively)
    private static final long POLL_INTERVAL_MINUTES = 45;
    
    // Daily cleanup interval for old events
    private static final long CLEANUP_INTERVAL_HOURS = 24;
    
    // HTTP timeouts
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final MetricsCollector collector;
    private final String manifestUrl;
    private final boolean enabled;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;

    /**
     * Creates a new CollectionPoller.
     *
     * @param collector   The metrics collector
     * @param manifestUrl URL to the telemetry manifest file
     * @param enabled     Whether metrics collection is enabled
     */
    public CollectionPoller(MetricsCollector collector, String manifestUrl, boolean enabled) {
        this.collector = collector;
        this.manifestUrl = manifestUrl;
        this.enabled = enabled;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TelemetryPoller");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts the polling scheduler.
     * This should be called after the bot has fully initialized.
     */
    public void start() {
        if (!enabled) {
            LOG.debug("Telemetry collection is disabled, not starting poller");
            return;
        }
        
        // Schedule periodic manifest polling
        // Initial delay of 5 minutes to let the bot stabilize
        scheduler.scheduleAtFixedRate(this::pollManifest, 5, POLL_INTERVAL_MINUTES, TimeUnit.MINUTES);
        
        // Schedule daily cleanup of old events
        scheduler.scheduleAtFixedRate(this::cleanup, 1, CLEANUP_INTERVAL_HOURS, TimeUnit.HOURS);
        
        LOG.debug("Started telemetry poller with {}min interval, manifest URL: {}", 
                POLL_INTERVAL_MINUTES, manifestUrl);
    }

    /**
     * Stops the polling scheduler.
     */
    public void stop() {
        scheduler.shutdownNow();
    }

    /**
     * Polls the manifest URL and sends telemetry if a new collection is requested.
     */
    void pollManifest() {
        try {
            LOG.debug("Polling telemetry manifest at {}", manifestUrl);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(manifestUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                LOG.debug("Manifest request returned status {}", response.statusCode());
                return;
            }
            
            JsonNode manifest = objectMapper.readTree(response.body());
            
            if (!manifest.has("collectionId") || !manifest.has("endpoint")) {
                LOG.debug("Manifest missing required fields (collectionId, endpoint)");
                return;
            }
            
            String collectionId = manifest.get("collectionId").asText();
            String endpoint = manifest.get("endpoint").asText();
            
            // Check if this is a new collection we haven't responded to
            String lastCollectionId = collector.getStore().getLastCollectionId();
            if (collectionId.equals(lastCollectionId)) {
                LOG.debug("Already responded to collection {}", collectionId);
                return;
            }
            
            // Check if we have events to send
            if (!collector.getStore().hasEvents()) {
                LOG.debug("No telemetry events to send for collection {}", collectionId);
                // Still mark as responded so we don't keep checking
                collector.getStore().clearEventsAfterCollection(collectionId);
                return;
            }
            
            // Send telemetry to the endpoint
            sendTelemetry(collectionId, endpoint);
            
        } catch (Exception e) {
            LOG.debug("Failed to poll telemetry manifest: {}", e.getMessage());
        }
    }

    /**
     * Sends accumulated telemetry to the collection endpoint.
     */
    private void sendTelemetry(String collectionId, String endpoint) {
        try {
            List<TelemetryEvent> events = collector.getStore().readAllEvents();
            
            if (events.isEmpty()) {
                LOG.debug("No events to send");
                return;
            }
            
            // Build the payload
            String payload = buildPayload(collectionId, collector.getInstanceId(), events);
            
            LOG.debug("Sending {} telemetry events to {}", events.size(), endpoint);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                LOG.info("Successfully sent telemetry for collection {}", collectionId);
                collector.getStore().clearEventsAfterCollection(collectionId);
            } else {
                LOG.warn("Failed to send telemetry, status {}: {}", response.statusCode(), response.body());
            }
            
        } catch (Exception e) {
            LOG.warn("Failed to send telemetry: {}", e.getMessage());
        }
    }

    /**
     * Builds the JSON payload to send to the collection endpoint.
     */
    private String buildPayload(String collectionId, String instanceId, List<TelemetryEvent> events) 
            throws com.fasterxml.jackson.core.JsonProcessingException {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("collectionId", collectionId);
        payload.put("instanceId", instanceId);
        
        ArrayNode eventsArray = payload.putArray("events");
        for (TelemetryEvent event : events) {
            ObjectNode eventNode = objectMapper.createObjectNode();
            eventNode.put("type", event.getType().name().toLowerCase());
            eventNode.put("timestamp", event.getTimestamp().toString());
            
            // Add all data fields
            for (var entry : event.getData().entrySet()) {
                addValueToNode(eventNode, entry.getKey(), entry.getValue());
            }
            
            eventsArray.add(eventNode);
        }
        
        return objectMapper.writeValueAsString(payload);
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
        } else if (value instanceof java.util.Map) {
            ObjectNode childNode = objectMapper.createObjectNode();
            ((java.util.Map<String, Object>) value).forEach((k, v) -> addValueToNode(childNode, k, v));
            node.set(key, childNode);
        } else {
            node.put(key, value.toString());
        }
    }

    /**
     * Cleans up old telemetry events.
     */
    private void cleanup() {
        try {
            collector.getStore().pruneOldEvents();
        } catch (Exception e) {
            LOG.debug("Failed to cleanup old events: {}", e.getMessage());
        }
    }
}
