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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Manages a persistent, randomly-generated instance ID for telemetry purposes.
 * The instance ID is a random UUID that is generated on first run and persisted
 * to disk. This ID is not derived from any user data and is used solely for
 * correlating telemetry events from the same bot instance.
 *
 * @author John Grosh (jagrosh)
 */
public class InstanceIdManager {
    private static final Logger LOG = LoggerFactory.getLogger(InstanceIdManager.class);
    private static final String JMUSICBOT_DIR = ".jmusicbot";
    private static final String INSTANCE_ID_FILE = "instance-id";

    private final Path dataDir;
    private final Path instanceIdPath;
    private String instanceId;

    /**
     * Creates a new InstanceIdManager using the default data directory.
     * The data directory is located at ~/.jmusicbot/ (user home directory).
     */
    public InstanceIdManager() {
        this(Path.of(System.getProperty("user.home"), JMUSICBOT_DIR));
    }

    /**
     * Creates a new InstanceIdManager using a custom data directory.
     * Primarily used for testing.
     *
     * @param dataDir The directory to store instance data
     */
    public InstanceIdManager(Path dataDir) {
        this.dataDir = dataDir;
        this.instanceIdPath = dataDir.resolve(INSTANCE_ID_FILE);
    }

    /**
     * Initializes the instance ID manager by loading an existing ID or generating a new one.
     * This method should be called during bot startup.
     */
    public void init() {
        try {
            ensureDataDirExists();
            instanceId = loadOrGenerateInstanceId();
            LOG.debug("Telemetry instance ID: {}", instanceId);
        } catch (IOException e) {
            LOG.warn("Failed to initialize instance ID, generating temporary ID: {}", e.getMessage());
            instanceId = UUID.randomUUID().toString();
        }
    }

    /**
     * Gets the instance ID. If not yet initialized, returns a temporary ID.
     *
     * @return The instance ID
     */
    public String getInstanceId() {
        if (instanceId == null) {
            LOG.warn("Instance ID requested before initialization, returning temporary ID");
            return UUID.randomUUID().toString();
        }
        return instanceId;
    }

    /**
     * Ensures the data directory exists, creating it if necessary.
     */
    private void ensureDataDirExists() throws IOException {
        if (!Files.exists(dataDir)) {
            Files.createDirectories(dataDir);
            LOG.debug("Created telemetry data directory: {}", dataDir);
        }
    }

    /**
     * Loads the instance ID from disk if it exists, otherwise generates and persists a new one.
     */
    private String loadOrGenerateInstanceId() throws IOException {
        if (Files.exists(instanceIdPath)) {
            String id = Files.readString(instanceIdPath).trim();
            if (isValidUUID(id)) {
                LOG.debug("Loaded existing instance ID from {}", instanceIdPath);
                return id;
            }
            LOG.warn("Invalid instance ID found in {}, generating new one", instanceIdPath);
        }

        String newId = UUID.randomUUID().toString();
        Files.writeString(instanceIdPath, newId);
        LOG.debug("Generated and saved new instance ID to {}", instanceIdPath);
        return newId;
    }

    /**
     * Validates that a string is a valid UUID format.
     */
    private boolean isValidUUID(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            UUID.fromString(str);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Gets the path to the data directory.
     * Primarily used for testing.
     */
    Path getDataDir() {
        return dataDir;
    }
}
