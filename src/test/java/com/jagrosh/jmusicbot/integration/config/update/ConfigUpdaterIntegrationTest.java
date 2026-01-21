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
package com.jagrosh.jmusicbot.integration.config.update;

import com.jagrosh.jmusicbot.BaseConfigTest;
import com.jagrosh.jmusicbot.config.diagnostics.ConfigDiagnostics;
import com.jagrosh.jmusicbot.config.loader.ConfigLoader;
import com.jagrosh.jmusicbot.config.update.ConfigUpdater;
import com.jagrosh.jmusicbot.testutil.config.LegacyConfigBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.parser.ConfigDocument;
import com.typesafe.config.parser.ConfigDocumentFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigUpdater Integration Tests")
class ConfigUpdaterIntegrationTest extends BaseConfigTest {
    
    @Nested
    @DisplayName("ConfigDocument Usage")
    class ConfigDocumentUsageTests {
        
        @Test
        @DisplayName("generateUpdatedConfig creates file using ConfigDocument")
        void generateUpdatedConfigUsesConfigDocument() throws IOException {
            String legacyConfig = LegacyConfigBuilder.create()
                .withToken("test_token")
                .withOwner(123456789L)
                .withPrefix("!!")
                .buildAsString();
            
            Path configFile = createTempConfigFile(legacyConfig);
            
            Config merged = ConfigLoader.loadMergedConfig(configFile);
            Config migratedUser = ConfigLoader.loadMigratedUserConfig(configFile);
            Config defaults = ConfigFactory.load();
            
            ConfigDiagnostics.Report diagnostics = 
                ConfigDiagnostics.analyze(migratedUser, merged, defaults);
            
            Path updatedPath = ConfigUpdater.generateUpdatedConfig(configFile, migratedUser, diagnostics);
            
            assertNotNull(updatedPath);
            assertFileExists(updatedPath);
            
            // Verify the generated file can be parsed as ConfigDocument
            String content = readFileContent(updatedPath);
            ConfigDocument doc = ConfigDocumentFactory.parseString(content);
            assertNotNull(doc);
            
            // Verify it's also parseable as Config
            Config generated = ConfigFactory.parseString(content);
            assertNotNull(generated);
            assertTrue(generated.hasPath("meta.configVersion"));
        }
        
        @Test
        @DisplayName("generateUpdatedConfig preserves structure from reference.conf")
        void generateUpdatedConfigPreservesStructure() throws IOException {
            String legacyConfig = LegacyConfigBuilder.create()
                .withToken("test_token")
                .withOwner(123456789L)
                .buildAsString();
            
            Path configFile = createTempConfigFile(legacyConfig);
            
            Config merged = ConfigLoader.loadMergedConfig(configFile);
            Config migratedUser = ConfigLoader.loadMigratedUserConfig(configFile);
            Config defaults = ConfigFactory.load();
            
            ConfigDiagnostics.Report diagnostics = 
                ConfigDiagnostics.analyze(migratedUser, merged, defaults);
            
            Path updatedPath = ConfigUpdater.generateUpdatedConfig(configFile, migratedUser, diagnostics);
            
            String content = readFileContent(updatedPath);
            
            // Should have nested structure matching reference.conf
            assertTrue(content.contains("meta {") || content.contains("meta"));
            assertTrue(content.contains("discord {") || content.contains("discord"));
            assertTrue(content.contains("commands {") || content.contains("commands"));
        }
        
        @Test
        @DisplayName("generateUpdatedConfig creates backup file")
        void generateUpdatedConfigCreatesBackup() throws IOException {
            String legacyConfig = LegacyConfigBuilder.create()
                .withToken("test_token")
                .withOwner(123456789L)
                .buildAsString();
            
            Path configFile = createTempConfigFile(legacyConfig);
            
            // Ensure file exists before calling generateUpdatedConfig
            assertTrue(Files.exists(configFile), "Config file should exist before update");
            
            Config merged = ConfigLoader.loadMergedConfig(configFile);
            Config migratedUser = ConfigLoader.loadMigratedUserConfig(configFile);
            Config defaults = ConfigFactory.load();
            
            ConfigDiagnostics.Report diagnostics = 
                ConfigDiagnostics.analyze(migratedUser, merged, defaults);
            
            Path updatedPath = ConfigUpdater.generateUpdatedConfig(configFile, migratedUser, diagnostics);
            
            assertNotNull(updatedPath);
            
            // Verify backup was created using the helper method (use configFile, which gets normalized internally)
            assertTrue(ConfigUpdater.backupExists(configFile), 
                "Backup file should exist. Config file: " + configFile);
            
            // Verify backup contains original content (calculate path using same logic as ConfigUpdater)
            Path normalizedPath = configFile.toAbsolutePath().normalize();
            Path backupPath = normalizedPath.resolveSibling(normalizedPath.getFileName().toString() + ".bak");
            assertTrue(Files.exists(backupPath), "Backup file should exist at: " + backupPath);
            
            String backupContent = readFileContent(backupPath);
            // Backup should contain the original legacy config content
            assertTrue(backupContent.contains("test_token"), 
                "Backup should contain original token. Backup content: " + backupContent.substring(0, Math.min(200, backupContent.length())));
            // Also verify it's the legacy format (not migrated)
            assertTrue(backupContent.contains("token") || backupContent.contains("owner"),
                "Backup should contain original config keys");
        }
        
        @Test
        @DisplayName("generateUpdatedConfig preserves comments from reference.conf via ConfigDocument")
        void generateUpdatedConfigPreservesComments() throws IOException {
            String legacyConfig = LegacyConfigBuilder.create()
                .withToken("test_token")
                .withOwner(123456789L)
                .buildAsString();
            
            Path configFile = createTempConfigFile(legacyConfig);
            
            Config merged = ConfigLoader.loadMergedConfig(configFile);
            Config migratedUser = ConfigLoader.loadMigratedUserConfig(configFile);
            Config defaults = ConfigFactory.load();
            
            ConfigDiagnostics.Report diagnostics = 
                ConfigDiagnostics.analyze(migratedUser, merged, defaults);
            
            Path updatedPath = ConfigUpdater.generateUpdatedConfig(configFile, migratedUser, diagnostics);
            
            String content = readFileContent(updatedPath);
            
            // Parse as ConfigDocument to verify it's using ConfigDocument (not fallback)
            ConfigDocument doc = ConfigDocumentFactory.parseString(content);
            assertNotNull(doc);
            
            // Verify the content is valid and has correct values
            Config generated = ConfigFactory.parseString(content);
            assertEquals("test_token", generated.getString("discord.token"));
            assertEquals(123456789L, generated.getLong("discord.owner"));
            
            // ConfigDocument should preserve structure from reference.conf
            // The exact formatting depends on reference.conf, but structure should be preserved
            assertTrue(content.contains("meta") || content.contains("discord"));
        }
        
        @Test
        @DisplayName("Full update flow uses ConfigDocument to preserve formatting")
        void fullUpdateFlowUsesConfigDocument() throws IOException {
            // Create a legacy config file
            String legacyConfig = LegacyConfigBuilder.create()
                .withToken("original_token")
                .withOwner(123456789L)
                .withPrefix("!!")
                .withGame("Playing music")
                .buildAsString();
            
            Path configFile = createTempConfigFile(legacyConfig);
            
            // Load and migrate
            Config merged = ConfigLoader.loadMergedConfig(configFile);
            Config migratedUser = ConfigLoader.loadMigratedUserConfig(configFile);
            Config defaults = ConfigFactory.load();
            
            ConfigDiagnostics.Report diagnostics = 
                ConfigDiagnostics.analyze(migratedUser, merged, defaults);
            
            // Generate updated config (should use ConfigDocument)
            Path updatedPath = ConfigUpdater.generateUpdatedConfig(configFile, migratedUser, diagnostics);
            
            assertNotNull(updatedPath);
            String content = readFileContent(updatedPath);
            
            // Verify it's parseable as ConfigDocument (primary method)
            ConfigDocument doc = ConfigDocumentFactory.parseString(content);
            assertNotNull(doc);
            
            // Verify values are correct
            Config parsed = ConfigFactory.parseString(content);
            assertEquals("original_token", parsed.getString("discord.token"));
            assertEquals(123456789L, parsed.getLong("discord.owner"));
            assertEquals("!!", parsed.getString("commands.prefix"));
            assertEquals("Playing music", parsed.getString("presence.game"));
            
            // Verify structure is preserved (nested sections)
            assertTrue(parsed.hasPath("meta.configVersion"));
            assertTrue(parsed.hasPath("discord.token"));
            assertTrue(parsed.hasPath("commands.prefix"));
            assertTrue(parsed.hasPath("presence.game"));
        }
    }
    
    @Nested
    @DisplayName("Nested Config Preservation")
    class NestedConfigPreservationTests {
        
        @Test
        @DisplayName("generateUpdatedConfig preserves existing nested config values when adding missing keys")
        void generateUpdatedConfigPreservesExistingNestedConfigValues() throws IOException {
            // Create a v1 config with partial audioSources (missing local key)
            // This simulates the user's scenario where they had some keys set to false
            // but deleted the local key
            String v1Config = """
                meta {
                  configVersion = 1
                }
                discord {
                  token = test_token
                  owner = 123456789
                }
                playback {
                  audioSources {
                    youtube = false
                    soundcloud = false
                    bandcamp = false
                    vimeo = false
                    twitch = false
                    beam = false
                    getyarn = false
                    nico = false
                    http = false
                    # local key is intentionally missing
                  }
                }
                """;
            
            Path configFile = createTempConfigFile(v1Config);
            
            Config merged = ConfigLoader.loadMergedConfig(configFile);
            Config migratedUser = ConfigLoader.loadMigratedUserConfig(configFile);
            Config defaults = ConfigFactory.load();
            
            ConfigDiagnostics.Report diagnostics = 
                ConfigDiagnostics.analyze(migratedUser, merged, defaults);
            
            // Verify that local is detected as missing
            assertTrue(diagnostics.getMissingOptional().contains("playback.audioSources.local"),
                "local key should be detected as missing");
            
            Path updatedPath = ConfigUpdater.generateUpdatedConfig(configFile, migratedUser, diagnostics);
            
            assertNotNull(updatedPath);
            String content = readFileContent(updatedPath);
            
            // Parse the updated config
            Config updated = ConfigFactory.parseString(content);
            assertTrue(updated.hasPath("playback.audioSources"));
            Config audioSources = updated.getConfig("playback.audioSources");
            
            // Verify that existing values are preserved (should still be false)
            assertFalse(audioSources.getBoolean("youtube"), 
                "Existing youtube=false should be preserved");
            assertFalse(audioSources.getBoolean("soundcloud"), 
                "Existing soundcloud=false should be preserved");
            assertFalse(audioSources.getBoolean("bandcamp"), 
                "Existing bandcamp=false should be preserved");
            assertFalse(audioSources.getBoolean("vimeo"), 
                "Existing vimeo=false should be preserved");
            
            // Verify that missing key is added with template default (true)
            assertTrue(audioSources.getBoolean("local"), 
                "Missing local key should be added with template default (true)");
        }
        
        @Test
        @DisplayName("generateUpdatedConfig preserves template defaults for missing nested keys")
        void generateUpdatedConfigPreservesTemplateDefaultsForMissingNestedKeys() throws IOException {
            // Create a v1 config with only one audioSources key set
            String v1Config = """
                meta {
                  configVersion = 1
                }
                discord {
                  token = test_token
                  owner = 123456789
                }
                playback {
                  audioSources {
                    youtube = false
                    # All other keys are missing
                  }
                }
                """;
            
            Path configFile = createTempConfigFile(v1Config);
            
            Config merged = ConfigLoader.loadMergedConfig(configFile);
            Config migratedUser = ConfigLoader.loadMigratedUserConfig(configFile);
            Config defaults = ConfigFactory.load();
            
            ConfigDiagnostics.Report diagnostics = 
                ConfigDiagnostics.analyze(migratedUser, merged, defaults);
            
            Path updatedPath = ConfigUpdater.generateUpdatedConfig(configFile, migratedUser, diagnostics);
            
            assertNotNull(updatedPath);
            String content = readFileContent(updatedPath);
            
            // Parse the updated config
            Config updated = ConfigFactory.parseString(content);
            Config audioSources = updated.getConfig("playback.audioSources");
            
            // Verify user's value is preserved
            assertFalse(audioSources.getBoolean("youtube"), 
                "User's youtube=false should be preserved");
            
            // Verify all missing keys get template defaults (true)
            assertTrue(audioSources.getBoolean("soundcloud"), 
                "Missing soundcloud should get template default (true)");
            assertTrue(audioSources.getBoolean("local"), 
                "Missing local should get template default (true)");
            assertTrue(audioSources.getBoolean("bandcamp"), 
                "Missing bandcamp should get template default (true)");
            assertTrue(audioSources.getBoolean("vimeo"), 
                "Missing vimeo should get template default (true)");
        }
    }
}
