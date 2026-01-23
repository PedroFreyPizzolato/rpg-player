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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jagrosh.jmusicbot.metrics.model.TelemetryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Persists telemetry events to disk using a compressed binary format.
 * 
 * <p>This class provides a high-level API for storing and retrieving telemetry
 * events, delegating to {@link CompressedRecordFile} for binary storage and
 * {@link TelemetryEventSerializer} for JSON conversion.
 * 
 * <p>Features:
 * <ul>
 *   <li>GZIP compression - typically 60-80% size reduction</li>
 *   <li>Append-only writes - no need to re-serialize the entire file</li>
 *   <li>7-day retention - old events are automatically pruned</li>
 *   <li>Thread-safe operations</li>
 *   <li>Separate metadata file for tracking collection state</li>
 * </ul>
 *
 * @author John Grosh (jagrosh)
 */
public class TelemetryStore {
    private static final Logger LOG = LoggerFactory.getLogger(TelemetryStore.class);
    private static final String JMUSICBOT_DIR = ".jmusicbot";
    private static final String EVENTS_FILE = "telemetry.bin";
    private static final String META_FILE = "telemetry-meta.json";
    private static final Duration RETENTION_PERIOD = Duration.ofDays(7);
    
    // Magic bytes to identify our telemetry file format (version 1)
    private static final byte[] TELEMETRY_MAGIC = {'T', 'L', 'M', 0x01};

    private final Path dataDir;
    private final CompressedRecordFile recordFile;
    private final TelemetryEventSerializer serializer;
    private final Path metaPath;
    private final ReentrantReadWriteLock metaLock = new ReentrantReadWriteLock();

    private String lastCollectionId;

    /**
     * Creates a new TelemetryStore using the default data directory.
     * The data directory is located at ~/.jmusicbot/ (user home directory).
     */
    public TelemetryStore() {
        this(Path.of(System.getProperty("user.home"), JMUSICBOT_DIR));
    }

    /**
     * Creates a new TelemetryStore using a custom data directory.
     * Primarily used for testing.
     *
     * @param dataDir The directory to store telemetry data
     */
    public TelemetryStore(Path dataDir) {
        this.dataDir = dataDir;
        this.recordFile = new CompressedRecordFile(dataDir.resolve(EVENTS_FILE), TELEMETRY_MAGIC);
        this.serializer = new TelemetryEventSerializer();
        this.metaPath = dataDir.resolve(META_FILE);
    }

    /**
     * Initializes the telemetry store by ensuring directories exist and loading metadata.
     */
    public void init() {
        try {
            ensureDataDirExists();
            loadMetadata();
            LOG.debug("Telemetry store initialized at {}", dataDir);
        } catch (IOException e) {
            LOG.warn("Failed to initialize telemetry store: {}", e.getMessage());
        }
    }

    /**
     * Appends a telemetry event to the store.
     * This is a thread-safe, append-only operation.
     *
     * @param event The event to store
     */
    public void appendEvent(TelemetryEvent event) {
        try {
            String json = serializer.serialize(event);
            recordFile.appendRecord(json);
            LOG.debug("Appended {} event to telemetry store", event.getType());
        } catch (IOException e) {
            LOG.warn("Failed to append telemetry event: {}", e.getMessage());
        }
    }

    /**
     * Reads all stored events.
     *
     * @return List of all events in the store
     */
    public List<TelemetryEvent> readAllEvents() {
        try {
            List<String> records = recordFile.readAllRecordsAsStrings();
            List<TelemetryEvent> events = new ArrayList<>(records.size());
            
            for (String json : records) {
                TelemetryEvent event = serializer.deserialize(json);
                if (event != null) {
                    events.add(event);
                }
            }
            
            return events;
        } catch (IOException e) {
            LOG.warn("Failed to read telemetry events: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Clears all stored events after a successful collection.
     * Also updates the last collection ID.
     *
     * @param collectionId The ID of the collection that was just completed
     */
    public void clearEventsAfterCollection(String collectionId) {
        try {
            recordFile.delete();
            setLastCollectionId(collectionId);
            LOG.debug("Cleared telemetry events after collection {}", collectionId);
        } catch (IOException e) {
            LOG.warn("Failed to clear telemetry events: {}", e.getMessage());
        }
    }

    /**
     * Removes events older than the retention period (7 days).
     * This should be called periodically (e.g., daily) to prevent unbounded growth.
     */
    public void pruneOldEvents() {
        try {
            List<TelemetryEvent> allEvents = readAllEvents();
            if (allEvents.isEmpty()) {
                return;
            }

            Instant cutoff = Instant.now().minus(RETENTION_PERIOD);
            List<TelemetryEvent> retainedEvents = allEvents.stream()
                    .filter(e -> e.getTimestamp().isAfter(cutoff))
                    .toList();
            
            int prunedCount = allEvents.size() - retainedEvents.size();
            
            if (prunedCount > 0) {
                // Serialize retained events and rewrite
                List<byte[]> records = new ArrayList<>(retainedEvents.size());
                for (TelemetryEvent event : retainedEvents) {
                    String json = serializer.serialize(event);
                    records.add(json.getBytes(StandardCharsets.UTF_8));
                }
                recordFile.writeAllRecords(records);
                LOG.debug("Pruned {} old telemetry events", prunedCount);
            }
        } catch (IOException e) {
            LOG.warn("Failed to prune old telemetry events: {}", e.getMessage());
        }
    }

    /**
     * Gets the last collection ID that was successfully processed.
     *
     * @return The last collection ID, or null if no collection has been done
     */
    public String getLastCollectionId() {
        metaLock.readLock().lock();
        try {
            return lastCollectionId;
        } finally {
            metaLock.readLock().unlock();
        }
    }

    /**
     * Checks if there are any events in the store.
     *
     * @return true if there are events to send
     */
    public boolean hasEvents() {
        return recordFile.hasRecords();
    }

    /**
     * Gets the path to the data directory.
     * Primarily used for testing.
     */
    Path getDataDir() {
        return dataDir;
    }

    /**
     * Gets the underlying record file.
     * Primarily used for testing.
     */
    CompressedRecordFile getRecordFile() {
        return recordFile;
    }

    private void setLastCollectionId(String collectionId) {
        metaLock.writeLock().lock();
        try {
            this.lastCollectionId = collectionId;
            saveMetadata();
        } catch (IOException e) {
            LOG.warn("Failed to save metadata: {}", e.getMessage());
        } finally {
            metaLock.writeLock().unlock();
        }
    }

    private void ensureDataDirExists() throws IOException {
        if (!Files.exists(dataDir)) {
            Files.createDirectories(dataDir);
        }
    }

    private void loadMetadata() throws IOException {
        metaLock.writeLock().lock();
        try {
            if (Files.exists(metaPath)) {
                try {
                    ObjectNode meta = (ObjectNode) serializer.getObjectMapper()
                            .readTree(Files.readString(metaPath));
                    if (meta.has("lastCollectionId")) {
                        this.lastCollectionId = meta.get("lastCollectionId").asText();
                    }
                } catch (JsonProcessingException e) {
                    LOG.warn("Failed to parse telemetry metadata, starting fresh: {}", e.getMessage());
                }
            }
        } finally {
            metaLock.writeLock().unlock();
        }
    }

    private void saveMetadata() throws IOException {
        ObjectNode meta = serializer.getObjectMapper().createObjectNode();
        if (lastCollectionId != null) {
            meta.put("lastCollectionId", lastCollectionId);
        }
        Files.writeString(metaPath, serializer.getObjectMapper().writeValueAsString(meta));
    }
}
