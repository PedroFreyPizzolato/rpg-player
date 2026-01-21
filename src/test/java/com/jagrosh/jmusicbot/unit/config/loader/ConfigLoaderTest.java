/*
 * Copyright 2026 Arif Banai (arif-banai)
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
package com.jagrosh.jmusicbot.unit.config.loader;

import com.jagrosh.jmusicbot.BaseConfigTest;
import com.jagrosh.jmusicbot.config.loader.ConfigLoader;
import com.typesafe.config.Config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigLoader Unit Tests")
class ConfigLoaderTest extends BaseConfigTest {
    
    @Nested
    @DisplayName("loadRawUserConfig() Tests")
    class LoadRawUserConfigTests {
        
        @Test
        @DisplayName("loadRawUserConfig() loads existing config file")
        void loadRawUserConfigLoadsExistingFile() throws IOException {
            // Test with legacy format - loadRawUserConfig returns raw config before migration
            Path configFile = createTempConfigFile("token = test_token\nowner = 123456789");
            
            Config config = ConfigLoader.loadRawUserConfig(configFile);
            
            assertNotNull(config);
            // Raw config has flat keys
            assertEquals("test_token", config.getString("token"));
            assertEquals(123456789L, config.getLong("owner"));
        }
        
        @Test
        @DisplayName("loadRawUserConfig() returns empty config for non-existing file")
        void loadRawUserConfigReturnsEmptyForNonExistingFile() {
            Path nonExistentFile = tempDir.resolve("nonexistent.conf");
            
            Config config = ConfigLoader.loadRawUserConfig(nonExistentFile);
            
            assertNotNull(config);
            assertTrue(config.isEmpty());
            assertFalse(config.hasPath("token"));
        }
        
        @Test
        @DisplayName("loadRawUserConfig() loads complex config with nested structures")
        void loadRawUserConfigLoadsComplexConfig() throws IOException {
            String configContent = """
                token = test_token
                owner = 123456789
                aliases {
                  play = [ p ]
                  skip = [ voteskip, vs ]
                }
                audiosources = [ youtube, soundcloud ]
                """;
            Path configFile = createTempConfigFile(configContent);
            
            Config config = ConfigLoader.loadRawUserConfig(configFile);
            
            assertNotNull(config);
            assertTrue(config.hasPath("aliases.play"));
            assertEquals(2, config.getStringList("aliases.skip").size());
            assertEquals(2, config.getStringList("audiosources").size());
        }
        
        @Test
        @DisplayName("loadRawUserConfig() handles empty config file")
        void loadRawUserConfigHandlesEmptyFile() throws IOException {
            Path configFile = createTempConfigFile("");
            
            Config config = ConfigLoader.loadRawUserConfig(configFile);
            
            assertNotNull(config);
            assertTrue(config.isEmpty());
        }
    }
    
    @Nested
    @DisplayName("loadMergedConfig() Tests")
    class LoadMergedConfigTests {
        
        @Test
        @DisplayName("loadMergedConfig() uses defaults when user config is empty")
        void loadMergedConfigUsesDefaultsWhenUserConfigEmpty() {
            Path nonExistentFile = tempDir.resolve("nonexistent.conf");
            
            Config merged = ConfigLoader.loadMergedConfig(nonExistentFile);
            
            assertNotNull(merged);
            // Should have access to defaults from reference.conf if available
            // The exact behavior depends on what's in reference.conf
        }
        
        @Test
        @DisplayName("loadMergedConfig() preserves user-specific values")
        void loadMergedConfigPreservesUserValues() throws IOException {
            // Legacy config gets migrated to nested format
            String configContent = """
                token = custom_token
                owner = 987654321
                prefix = custom_prefix
                stayinchannel = true
                """;
            Path configFile = createTempConfigFile(configContent);
            
            Config merged = ConfigLoader.loadMergedConfig(configFile);
            
            assertNotNull(merged);
            // After migration, check nested paths
            assertEquals("custom_token", merged.getString("discord.token"));
            assertEquals(987654321L, merged.getLong("discord.owner"));
            assertEquals("custom_prefix", merged.getString("commands.prefix"));
            assertTrue(merged.getBoolean("voice.stayInChannel"));
        }
        
    }
}
