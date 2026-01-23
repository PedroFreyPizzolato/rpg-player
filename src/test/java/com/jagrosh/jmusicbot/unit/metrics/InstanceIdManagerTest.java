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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InstanceIdManager Unit Tests")
class InstanceIdManagerTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("init() Tests")
    class InitTests {

        @Test
        @DisplayName("init() generates a new UUID when no instance ID file exists")
        void initGeneratesNewUuidWhenNoFileExists() {
            InstanceIdManager manager = new InstanceIdManager(tempDir);
            
            manager.init();
            
            String instanceId = manager.getInstanceId();
            assertNotNull(instanceId);
            assertDoesNotThrow(() -> UUID.fromString(instanceId));
        }

        @Test
        @DisplayName("init() persists the instance ID to disk")
        void initPersistsInstanceIdToDisk() throws IOException {
            InstanceIdManager manager = new InstanceIdManager(tempDir);
            
            manager.init();
            
            Path instanceIdFile = tempDir.resolve("instance-id");
            assertTrue(Files.exists(instanceIdFile));
            String fileContent = Files.readString(instanceIdFile).trim();
            assertEquals(manager.getInstanceId(), fileContent);
        }

        @Test
        @DisplayName("init() loads existing instance ID from disk")
        void initLoadsExistingInstanceIdFromDisk() throws IOException {
            String existingId = UUID.randomUUID().toString();
            Path instanceIdFile = tempDir.resolve("instance-id");
            Files.writeString(instanceIdFile, existingId);
            
            InstanceIdManager manager = new InstanceIdManager(tempDir);
            manager.init();
            
            assertEquals(existingId, manager.getInstanceId());
        }

        @Test
        @DisplayName("init() generates new ID if existing ID is invalid")
        void initGeneratesNewIdIfExistingIsInvalid() throws IOException {
            Path instanceIdFile = tempDir.resolve("instance-id");
            Files.writeString(instanceIdFile, "not-a-valid-uuid");
            
            InstanceIdManager manager = new InstanceIdManager(tempDir);
            manager.init();
            
            String instanceId = manager.getInstanceId();
            assertNotEquals("not-a-valid-uuid", instanceId);
            assertDoesNotThrow(() -> UUID.fromString(instanceId));
        }

        @Test
        @DisplayName("init() creates data directory if it doesn't exist")
        void initCreatesDataDirectoryIfNotExists() {
            Path nestedDir = tempDir.resolve("nested").resolve("telemetry");
            InstanceIdManager manager = new InstanceIdManager(nestedDir);
            
            manager.init();
            
            assertTrue(Files.exists(nestedDir));
            assertNotNull(manager.getInstanceId());
        }
    }

    @Nested
    @DisplayName("getInstanceId() Tests")
    class GetInstanceIdTests {

        @Test
        @DisplayName("getInstanceId() returns consistent ID across calls")
        void getInstanceIdReturnsConsistentId() {
            InstanceIdManager manager = new InstanceIdManager(tempDir);
            manager.init();
            
            String id1 = manager.getInstanceId();
            String id2 = manager.getInstanceId();
            String id3 = manager.getInstanceId();
            
            assertEquals(id1, id2);
            assertEquals(id2, id3);
        }

        @Test
        @DisplayName("getInstanceId() returns temporary ID before init")
        void getInstanceIdReturnsTemporaryIdBeforeInit() {
            InstanceIdManager manager = new InstanceIdManager(tempDir);
            
            // Call before init
            String id = manager.getInstanceId();
            
            assertNotNull(id);
            // Should be a valid UUID (temporary)
            assertDoesNotThrow(() -> UUID.fromString(id));
        }

        @Test
        @DisplayName("Different managers with same data dir use same ID")
        void differentManagersWithSameDataDirUseSameId() {
            InstanceIdManager manager1 = new InstanceIdManager(tempDir);
            manager1.init();
            String id1 = manager1.getInstanceId();
            
            InstanceIdManager manager2 = new InstanceIdManager(tempDir);
            manager2.init();
            String id2 = manager2.getInstanceId();
            
            assertEquals(id1, id2);
        }
    }
}
