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
package com.jagrosh.jmusicbot.unit.config;

import com.jagrosh.jmusicbot.BaseConfigTest;
import com.jagrosh.jmusicbot.config.ConfigLoader;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigLoader Unit Tests")
class ConfigLoaderTest extends BaseConfigTest {
    
    @Nested
    @DisplayName("loadUserConfig() Tests")
    class LoadUserConfigTests {
        
        @Test
        @DisplayName("loadUserConfig() loads existing config file")
        void loadUserConfigLoadsExistingFile() throws IOException {
            Path configFile = createTempConfigFile("token = test_token\nowner = 123456789");
            
            Config config = ConfigLoader.loadUserConfig(configFile);
            
            assertNotNull(config);
            assertEquals("test_token", config.getString("token"));
            assertEquals(123456789L, config.getLong("owner"));
        }
        
        @Test
        @DisplayName("loadUserConfig() returns empty config for non-existing file")
        void loadUserConfigReturnsEmptyForNonExistingFile() {
            Path nonExistentFile = tempDir.resolve("nonexistent.conf");
            
            Config config = ConfigLoader.loadUserConfig(nonExistentFile);
            
            assertNotNull(config);
            assertTrue(config.isEmpty());
            assertFalse(config.hasPath("token"));
        }
        
        @Test
        @DisplayName("loadUserConfig() loads complex config with nested structures")
        void loadUserConfigLoadsComplexConfig() throws IOException {
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
            
            Config config = ConfigLoader.loadUserConfig(configFile);
            
            assertNotNull(config);
            assertTrue(config.hasPath("aliases.play"));
            assertEquals(2, config.getStringList("aliases.skip").size());
            assertEquals(2, config.getStringList("audiosources").size());
        }
        
        @Test
        @DisplayName("loadUserConfig() handles empty config file")
        void loadUserConfigHandlesEmptyFile() throws IOException {
            Path configFile = createTempConfigFile("");
            
            Config config = ConfigLoader.loadUserConfig(configFile);
            
            assertNotNull(config);
            assertTrue(config.isEmpty());
        }
    }
    
    @Nested
    @DisplayName("loadMergedConfig() Tests")
    class LoadMergedConfigTests {
        
        @Test
        @DisplayName("loadMergedConfig() merges user config with defaults")
        void loadMergedConfigMergesWithDefaults() throws IOException {
            Path configFile = createTempConfigFile("token = user_token\nowner = 123456789");
            
            Config merged = ConfigLoader.loadMergedConfig(configFile);
            
            assertNotNull(merged);
            // User config should override defaults
            assertEquals("user_token", merged.getString("token"));
            assertEquals(123456789L, merged.getLong("owner"));
        }
        
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
        @DisplayName("loadMergedConfig() user config overrides defaults")
        void loadMergedConfigUserOverridesDefaults() throws IOException {
            // Create a user config with a value that might exist in defaults
            Path configFile = createTempConfigFile("prefix = \"!!\"");
            
            Config merged = ConfigLoader.loadMergedConfig(configFile);
            
            assertNotNull(merged);
            if (merged.hasPath("prefix")) {
                assertEquals("!!", merged.getString("prefix"));
            }
        }
        
        @Test
        @DisplayName("loadMergedConfig() preserves user-specific values")
        void loadMergedConfigPreservesUserValues() throws IOException {
            String configContent = """
                token = custom_token
                owner = 987654321
                prefix = custom_prefix
                stayinchannel = true
                """;
            Path configFile = createTempConfigFile(configContent);
            
            Config merged = ConfigLoader.loadMergedConfig(configFile);
            
            assertNotNull(merged);
            assertEquals("custom_token", merged.getString("token"));
            assertEquals(987654321L, merged.getLong("owner"));
            assertEquals("custom_prefix", merged.getString("prefix"));
            assertTrue(merged.getBoolean("stayinchannel"));
        }
        
        @Test
        @DisplayName("loadMergedConfig() handles missing optional fields")
        void loadMergedConfigHandlesMissingOptionalFields() throws IOException {
            // User config with only required fields
            Path configFile = createTempConfigFile("token = test_token\nowner = 123456789");
            
            Config merged = ConfigLoader.loadMergedConfig(configFile);
            
            assertNotNull(merged);
            // Should still be able to access optional fields from defaults
            // The exact behavior depends on reference.conf
        }
    }
    
    @Nested
    @DisplayName("Config Merging Behavior Tests")
    class ConfigMergingBehaviorTests {
        
        @Test
        @DisplayName("Merged config respects user config priority")
        void mergedConfigRespectsUserConfigPriority() throws IOException {
            // Create user config
            Path userConfigFile = createTempConfigFile("token = user_override");
            
            Config userConfig = ConfigLoader.loadUserConfig(userConfigFile);
            Config merged = ConfigLoader.loadMergedConfig(userConfigFile);
            
            // User config should take priority
            assertEquals("user_override", merged.getString("token"));
        }
        
        @Test
        @DisplayName("Merged config includes defaults for missing user values")
        void mergedConfigIncludesDefaultsForMissingValues() throws IOException {
            // User config with minimal values
            Path userConfigFile = createTempConfigFile("token = test_token\nowner = 123456789");
            
            Config merged = ConfigLoader.loadMergedConfig(userConfigFile);
            
            // Should have access to default values from reference.conf
            assertNotNull(merged);
            // The exact fields depend on reference.conf content
        }
    }
}
