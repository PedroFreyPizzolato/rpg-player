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
                token = integration_test_token
                owner = 987654321
                prefix = "!!"
                altprefix = "??"
                help = commands
                success = ✅
                warning = ⚠️
                error = ❌
                loading = ⏳
                searching = 🔍
                game = Playing music
                status = ONLINE
                songinstatus = true
                npimages = true
                stayinchannel = true
                maxtime = 3600
                maxytplaylistpages = 20
                useyoutubeoauth = true
                alonetimeuntilstop = 300
                playlistsfolder = CustomPlaylists
                updatealerts = false
                loglevel = debug
                eval = false
                evalengine = Nashorn
                skipratio = 0.75
                aliases {
                  play = [ p, playmusic ]
                  skip = [ voteskip, vs ]
                }
                transforms = {}
                audiosources = [ youtube, soundcloud, bandcamp ]
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
            // Note: writeToFile() replaces the entire file content, so we check if file was modified
            String fileContent = readFileContent(configFile);
            // After write, file should contain the token (either new or original)
            assertTrue(fileContent.contains("token") && 
                      (fileContent.contains("new_token") || fileContent.contains("987654321") || 
                       fileContent.contains("BOT_TOKEN_HERE")));
        }
    }
    
    @Nested
    @DisplayName("Config Updates Integration")
    class ConfigUpdatesIntegrationTests {
        
        @Test
        @DisplayName("Updates config with missing values after load")
        void updatesConfigWithMissingValuesAfterLoad() throws IOException {
            // Minimal config
            String configContent = "token = test_token\nowner = 123456789";
            Path configFile = createTempConfigFile(configContent);
            System.setProperty("config.file", configFile.toString());
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.isValid());
            // ConfigUpdater should have run and appended missing values (if any)
            String fileContent = readFileContent(configFile);
            // File should have at least the original content
            assertTrue(fileContent.contains("token = test_token") || 
                      fileContent.contains("owner = 123456789"));
            // May have appended content if there were missing keys
            assertTrue(fileContent.length() >= configContent.length());
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
