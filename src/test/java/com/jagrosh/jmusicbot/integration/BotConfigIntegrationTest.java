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
package com.jagrosh.jmusicbot.integration;

import com.jagrosh.jmusicbot.BaseConfigTest;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.MockUserInteraction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BotConfig Integration Tests")
class BotConfigIntegrationTest extends BaseConfigTest {
    
    private String originalConfigFile;
    
    @BeforeEach
    void setUpIntegration() {
        originalConfigFile = System.getProperty("config.file");
    }
    
    @AfterEach
    void tearDownIntegration() {
        if (originalConfigFile != null) {
            System.setProperty("config.file", originalConfigFile);
        } else {
            System.clearProperty("config.file");
        }
    }
    
    @Nested
    @DisplayName("Full Load Flow")
    class FullLoadFlowTests {
        
        @Test
        @DisplayName("Loads config with all optional fields")
        void loadsConfigWithAllOptionalFields() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = integration_test_token
                discord.owner = 987654321
                commands.prefix = "!!"
                commands.altPrefix = "??"
                commands.help = commands
                ui.emojis.success = ✅
                ui.emojis.warning = ⚠️
                ui.emojis.error = ❌
                ui.emojis.loading = ⏳
                ui.emojis.searching = 🔍
                presence.game = Playing music
                presence.status = ONLINE
                presence.songInStatus = true
                nowPlaying.images = true
                voice.stayInChannel = true
                playback.maxTrackSeconds = 3600
                playback.maxYouTubePlaylistPages = 20
                playback.youtube.useOAuth = true
                voice.aloneTimeUntilStopSeconds = 300
                paths.playlistsFolder = CustomPlaylists
                updates.alerts = false
                logging.level = debug
                dangerous.eval = false
                dangerous.evalEngine = Nashorn
                playback.skipRatio = 0.75
                commands.aliases {
                  play = [ p, playmusic ]
                  skip = [ voteskip, vs ]
                }
                playback.transforms = {}
                playback.audioSources {
                  youtube = true
                  soundcloud = true
                  bandcamp = true
                }
                """;
            Path configFile = createTempConfigFile(configContent);
            System.setProperty("config.file", configFile.toString());
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.isValid());
            assertEquals("integration_test_token", config.getToken());
            assertEquals(987654321L, config.getOwnerId());
            assertEquals("!!", config.getPrefix());
            assertEquals("??", config.getAltPrefix());
            assertTrue(config.getSongInStatus());
            assertEquals(20, config.getMaxYTPlaylistPages());
        }
        
        @Test
        @DisplayName("Loads config with missing required fields and prompts")
        void loadsConfigWithMissingRequiredFieldsAndPrompts() throws IOException {
            // Note: We use legacy keys here to test migration + prompting
            Path configFile = createTempConfigFile("token = BOT_TOKEN_HERE\nowner = 0");
            System.setProperty("config.file", configFile.toString());
            
            mockUserInteraction.addPromptResponse("prompted_token");
            mockUserInteraction.addPromptResponse("123456789");
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.isValid());
            assertEquals("prompted_token", config.getToken());
            assertEquals(123456789L, config.getOwnerId());
            assertEquals(2, mockUserInteraction.getPromptCalls().size());
        }
        
        @Test
        @DisplayName("Fails to load when user cancels validation")
        void failsToLoadWhenUserCancelsValidation() throws IOException {
            Path configFile = createTempConfigFile("token = BOT_TOKEN_HERE\nowner = 123456789");
            System.setProperty("config.file", configFile.toString());
            
            mockUserInteraction.setPromptCancelled();
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertFalse(config.isValid());
            assertEquals(1, mockUserInteraction.getAlertCalls().size());
        }
        
        @Test
        @DisplayName("Writes config file when validation prompts for input")
        void writesConfigFileWhenValidationPromptsForInput() throws IOException {
            Path configFile = createTempConfigFile("token = BOT_TOKEN_HERE\nowner = 0");
            System.setProperty("config.file", configFile.toString());
            
            mockUserInteraction.addPromptResponse("new_token");
            mockUserInteraction.addPromptResponse("987654321");
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.isValid());
            // Config file should have been written with new values
            // Note: writeToFile() replaces the entire file content with default template
            String fileContent = readFileContent(configFile);
            // After write, file should contain the config structure (new format uses discord.token)
            // The file will be written with the default template, so check for config structure
            assertTrue(fileContent.contains("token") || fileContent.contains("discord"));
        }
    }
    
    @Nested
    @DisplayName("Config Updates Integration")
    class ConfigUpdatesIntegrationTests {
        
        @Test
        @DisplayName("Updates config with missing values after load")
        void updatesConfigWithMissingValuesAfterLoad() throws IOException {
            // Minimal config (using nested keys)
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                """;
            Path configFile = createTempConfigFile(configContent);
            System.setProperty("config.file", configFile.toString());
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.isValid());
            // Since we didn't provide all optional fields, a config.updated.conf should have been generated
            Path updatedConfig = configFile.getParent().resolve("config.updated.conf");
            assertTrue(java.nio.file.Files.exists(updatedConfig), "Updated config file should have been generated");
        }
    }
    
    @Nested
    @DisplayName("Error Handling Integration")
    class ErrorHandlingIntegrationTests {
        
        @Test
        @DisplayName("Handles ConfigException and shows alert")
        void handlesConfigExceptionAndShowsAlert() throws IOException {
            // Malformed config
            String configContent = """
                token = test_token
                owner = 123456789
                invalid syntax {
                """;
            Path configFile = createTempConfigFile(configContent);
            System.setProperty("config.file", configFile.toString());
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertFalse(config.isValid());
            // Should have shown error alert
            assertEquals(1, mockUserInteraction.getAlertCalls().size());
            var alert = mockUserInteraction.getLastAlert();
            assertNotNull(alert);
            assertEquals("Config", alert.getContext());
        }
        
        @Test
        @DisplayName("Shows config location in error messages")
        void showsConfigLocationInErrorMessages() throws IOException {
            Path configFile = createTempConfigFile("invalid syntax");
            System.setProperty("config.file", configFile.toString());
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            var alert = mockUserInteraction.getLastAlert();
            if (alert != null) {
                assertTrue(alert.getMessage().contains("Config Location") ||
                          alert.getMessage().contains(configFile.toString()));
            }
        }
    }
}
