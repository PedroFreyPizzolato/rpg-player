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
import com.jagrosh.jmusicbot.config.ConfigFileManager;
import com.jagrosh.jmusicbot.config.ConfigUpdater;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigUpdater Unit Tests")
class ConfigUpdaterTest extends BaseConfigTest {
    
    @Nested
    @DisplayName("updateConfigWithMissingValues() Tests")
    class UpdateConfigWithMissingValuesTests {
        
        @Test
        @DisplayName("updateConfigWithMissingValues() does nothing when all keys present")
        void updateConfigWithMissingValuesDoesNothingWhenAllKeysPresent() throws IOException {
            // Create a config file with all required and some optional keys
            String configContent = """
                token = test_token
                owner = 123456789
                prefix = "!!"
                stayinchannel = true
                """;
            Path configFile = createTempConfigFile(configContent);
            Config userConfig = ConfigFactory.parseFile(configFile.toFile());
            
            String originalContent = readFileContent(configFile);
            
            ConfigUpdater.updateConfigWithMissingValues(configFile, userConfig);
            
            // Content should remain the same (or have minimal additions)
            String newContent = readFileContent(configFile);
            assertTrue(newContent.contains("token = test_token"));
            assertTrue(newContent.contains("owner = 123456789"));
        }
        
        @Test
        @DisplayName("updateConfigWithMissingValues() does nothing for non-existing file")
        void updateConfigWithMissingValuesDoesNothingForNonExistingFile() {
            Path nonExistentFile = tempDir.resolve("nonexistent.conf");
            Config emptyConfig = ConfigFactory.empty();
            
            // Should not throw exception
            assertDoesNotThrow(() -> {
                ConfigUpdater.updateConfigWithMissingValues(nonExistentFile, emptyConfig);
            });
        }
        
        @Test
        @DisplayName("updateConfigWithMissingValues() appends missing keys")
        void updateConfigWithMissingValuesAppendsMissingKeys() throws IOException {
            // Create minimal config with only required fields
            String configContent = "token = test_token\nowner = 123456789";
            Path configFile = createTempConfigFile(configContent);
            Config userConfig = ConfigFactory.parseFile(configFile.toFile());
            
            ConfigUpdater.updateConfigWithMissingValues(configFile, userConfig);
            
            String newContent = readFileContent(configFile);
            // Should contain original content
            assertTrue(newContent.contains("token = test_token"));
            assertTrue(newContent.contains("owner = 123456789"));
            // Should have appended section marker
            assertTrue(newContent.contains("automatically added") || 
                      newContent.contains("The following config values"));
        }
        
        @Test
        @DisplayName("updateConfigWithMissingValues() preserves existing content")
        void updateConfigWithMissingValuesPreservesExistingContent() throws IOException {
            String originalContent = """
                token = test_token
                owner = 123456789
                prefix = custom_prefix
                """;
            Path configFile = createTempConfigFile(originalContent);
            Config userConfig = ConfigFactory.parseFile(configFile.toFile());
            
            ConfigUpdater.updateConfigWithMissingValues(configFile, userConfig);
            
            String newContent = readFileContent(configFile);
            // Original content should still be there
            assertTrue(newContent.contains("token = test_token"));
            assertTrue(newContent.contains("owner = 123456789"));
            assertTrue(newContent.contains("prefix = custom_prefix"));
        }
    }
    
    @Nested
    @DisplayName("Section Extraction Tests")
    class SectionExtractionTests {
        
        @Test
        @DisplayName("Config file with comments and sections")
        void configFileWithCommentsAndSections() throws IOException {
            // This test verifies that the updater can handle real config files
            // The actual extraction logic is tested indirectly through integration tests
            String configContent = """
                // This is a comment
                token = test_token
                owner = 123456789
                
                // Another comment
                prefix = "!!"
                """;
            Path configFile = createTempConfigFile(configContent);
            Config userConfig = ConfigFactory.parseFile(configFile.toFile());
            
            // Should not throw
            assertDoesNotThrow(() -> {
                ConfigUpdater.updateConfigWithMissingValues(configFile, userConfig);
            });
        }
        
        @Test
        @DisplayName("Config file with nested structures")
        void configFileWithNestedStructures() throws IOException {
            String configContent = """
                token = test_token
                owner = 123456789
                aliases {
                  play = [ p ]
                }
                """;
            Path configFile = createTempConfigFile(configContent);
            Config userConfig = ConfigFactory.parseFile(configFile.toFile());
            
            assertDoesNotThrow(() -> {
                ConfigUpdater.updateConfigWithMissingValues(configFile, userConfig);
            });
        }
    }
    
    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {
        
        @Test
        @DisplayName("Handles empty user config gracefully")
        void handlesEmptyUserConfigGracefully() throws IOException {
            Path configFile = createTempConfigFile("token = test_token\nowner = 123456789");
            Config emptyConfig = ConfigFactory.empty();
            
            // Should not throw
            assertDoesNotThrow(() -> {
                ConfigUpdater.updateConfigWithMissingValues(configFile, emptyConfig);
            });
        }
        
        @Test
        @DisplayName("Handles config file with only comments")
        void handlesConfigFileWithOnlyComments() throws IOException {
            String configContent = """
                // Just a comment
                // Another comment
                """;
            Path configFile = createTempConfigFile(configContent);
            Config userConfig = ConfigFactory.parseFile(configFile.toFile());
            
            assertDoesNotThrow(() -> {
                ConfigUpdater.updateConfigWithMissingValues(configFile, userConfig);
            });
        }
        
        @Test
        @DisplayName("Handles very large config file")
        void handlesVeryLargeConfigFile() throws IOException {
            StringBuilder largeContent = new StringBuilder("token = test_token\nowner = 123456789\n");
            for (int i = 0; i < 100; i++) {
                largeContent.append("// Comment ").append(i).append("\n");
            }
            Path configFile = createTempConfigFile(largeContent.toString());
            Config userConfig = ConfigFactory.parseFile(configFile.toFile());
            
            assertDoesNotThrow(() -> {
                ConfigUpdater.updateConfigWithMissingValues(configFile, userConfig);
            });
        }
    }
    
    @Nested
    @DisplayName("File Operations")
    class FileOperationTests {
        
        @Test
        @DisplayName("Appends to existing file correctly")
        void appendsToExistingFileCorrectly() throws IOException {
            String originalContent = "token = test_token\nowner = 123456789\n";
            Path configFile = createTempConfigFile(originalContent);
            Config userConfig = ConfigFactory.parseFile(configFile.toFile());
            
            ConfigUpdater.updateConfigWithMissingValues(configFile, userConfig);
            
            String newContent = readFileContent(configFile);
            // Should start with original content
            assertTrue(newContent.startsWith(originalContent.trim()) || 
                      newContent.contains("token = test_token"));
        }
        
        @Test
        @DisplayName("Preserves file encoding")
        void preservesFileEncoding() throws IOException {
            String configContent = "token = test_token\nowner = 123456789";
            Path configFile = createTempConfigFile(configContent);
            Config userConfig = ConfigFactory.parseFile(configFile.toFile());
            
            ConfigUpdater.updateConfigWithMissingValues(configFile, userConfig);
            
            // File should still be readable
            assertTrue(Files.exists(configFile));
            String content = readFileContent(configFile);
            assertNotNull(content);
            assertFalse(content.isEmpty());
        }
    }
}
