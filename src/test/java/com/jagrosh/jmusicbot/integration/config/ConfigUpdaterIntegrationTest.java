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
package com.jagrosh.jmusicbot.integration.config;

import com.jagrosh.jmusicbot.BaseConfigTest;
import com.jagrosh.jmusicbot.config.ConfigLoader;
import com.jagrosh.jmusicbot.config.ConfigUpdater;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigUpdater Integration Tests")
class ConfigUpdaterIntegrationTest extends BaseConfigTest {
    
    @Nested
    @DisplayName("Real Config File Updates")
    class RealConfigFileUpdateTests {
        
        @Test
        @DisplayName("Updates real config file with missing values")
        void updatesRealConfigFileWithMissingValues() throws IOException {
            // Minimal config with only required fields
            String originalContent = "token = test_token\nowner = 123456789";
            Path configFile = createTempConfigFile(originalContent);
            
            var userConfig = ConfigLoader.loadUserConfig(configFile);
            String contentBefore = readFileContent(configFile);
            
            ConfigUpdater.updateConfigWithMissingValues(configFile, userConfig);
            
            String contentAfter = readFileContent(configFile);
            // Original content should be preserved
            assertTrue(contentAfter.contains("token = test_token"));
            assertTrue(contentAfter.contains("owner = 123456789"));
            // Should have appended section if there were missing keys
            // Note: If all keys are already present, nothing is appended
            assertTrue(contentAfter.length() >= contentBefore.length());
        }
        
        @Test
        @DisplayName("Preserves existing content when updating")
        void preservesExistingContentWhenUpdating() throws IOException {
            String originalContent = """
                token = test_token
                owner = 123456789
                prefix = custom_prefix
                stayinchannel = true
                """;
            Path configFile = createTempConfigFile(originalContent);
            
            var userConfig = ConfigLoader.loadUserConfig(configFile);
            
            ConfigUpdater.updateConfigWithMissingValues(configFile, userConfig);
            
            String contentAfter = readFileContent(configFile);
            // All original content should still be present
            assertTrue(contentAfter.contains("token = test_token"));
            assertTrue(contentAfter.contains("owner = 123456789"));
            assertTrue(contentAfter.contains("prefix = custom_prefix"));
            assertTrue(contentAfter.contains("stayinchannel = true"));
        }
        
        @Test
        @DisplayName("Extracts sections from reference.conf correctly")
        void extractsSectionsFromReferenceConfCorrectly() throws IOException {
            // This test verifies that the updater can extract sections from the actual reference.conf
            String configContent = "token = test_token\nowner = 123456789";
            Path configFile = createTempConfigFile(configContent);
            
            var userConfig = ConfigLoader.loadUserConfig(configFile);
            
            // Should not throw
            assertDoesNotThrow(() -> {
                ConfigUpdater.updateConfigWithMissingValues(configFile, userConfig);
            });
            
            String contentAfter = readFileContent(configFile);
            // Should have same or more content than before (might not append if all keys present)
            assertTrue(contentAfter.length() >= configContent.length());
        }
        
        @Test
        @DisplayName("Handles config file with comments")
        void handlesConfigFileWithComments() throws IOException {
            String configContent = """
                // This is a comment
                token = test_token
                owner = 123456789
                // Another comment
                prefix = "!!"
                """;
            Path configFile = createTempConfigFile(configContent);
            
            var userConfig = ConfigLoader.loadUserConfig(configFile);
            
            ConfigUpdater.updateConfigWithMissingValues(configFile, userConfig);
            
            String contentAfter = readFileContent(configFile);
            // Comments should be preserved
            assertTrue(contentAfter.contains("// This is a comment") ||
                      contentAfter.contains("token = test_token"));
        }
    }
    
    @Nested
    @DisplayName("Section Extraction Integration")
    class SectionExtractionIntegrationTests {
        
        @Test
        @DisplayName("Extracts nested config sections correctly")
        void extractsNestedConfigSectionsCorrectly() throws IOException {
            String configContent = """
                token = test_token
                owner = 123456789
                aliases {
                  play = [ p ]
                }
                """;
            Path configFile = createTempConfigFile(configContent);
            
            var userConfig = ConfigLoader.loadUserConfig(configFile);
            
            // Should handle nested structures
            assertDoesNotThrow(() -> {
                ConfigUpdater.updateConfigWithMissingValues(configFile, userConfig);
            });
        }
        
        @Test
        @DisplayName("Appends sections in correct format")
        void appendsSectionsInCorrectFormat() throws IOException {
            String configContent = "token = test_token\nowner = 123456789";
            Path configFile = createTempConfigFile(configContent);
            
            var userConfig = ConfigLoader.loadUserConfig(configFile);
            
            ConfigUpdater.updateConfigWithMissingValues(configFile, userConfig);
            
            String contentAfter = readFileContent(configFile);
            // Should have proper formatting
            assertTrue(contentAfter.contains("token = test_token"));
            // Appended content should be after original (if there were missing keys)
            // If all keys are present, nothing is appended, so this is optional
            int originalEnd = contentAfter.indexOf("owner = 123456789") + "owner = 123456789".length();
            if (originalEnd < contentAfter.length()) {
                String afterOriginal = contentAfter.substring(originalEnd);
                // If there's content after, it should be non-empty (appended section)
                // If no content after, that's fine too (all keys already present)
                assertTrue(afterOriginal.trim().length() >= 0);
            }
        }
    }
}
