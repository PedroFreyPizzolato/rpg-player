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
package com.jagrosh.jmusicbot.config;

import static com.jagrosh.jmusicbot.config.ConfigOption.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

/**
 * Handles updating configuration files with missing values from the default config.
 * 
 * @author Arif Banai (arif-banai)
 */
public class ConfigUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigUpdater.class);
    
    /**
     * Updates the user's config file by appending any missing config values from the default config.
     * This preserves all existing user values and only adds new keys that are missing.
     */
    public static void updateConfigWithMissingValues(Path configPath, Config userConfig) {
        try {
            if (!ConfigFileManager.configFileExists(configPath)) {
                return; // Config file doesn't exist, will be created by writeToFile if needed
            }
            
            // Load the default config section from reference.conf
            String defaultConfigContent = ConfigFileManager.loadDefaultConfig();
            
            // Find missing keys using ConfigOption enum (excluding required options)
            Set<String> missingKeys = findMissingKeys(userConfig);
            
            // If there are missing keys, append them to the config file
            if (!missingKeys.isEmpty()) {
                LOGGER.info("Found {} missing config value(s), appending to config file: {}", 
                           missingKeys.size(), String.join(", ", missingKeys));
                
                // Extract sections for missing keys from default config
                String[] lines = defaultConfigContent.split("\r?\n");
                List<String> sectionsToAppend = extractConfigSections(lines, missingKeys);
                
                if (!sectionsToAppend.isEmpty()) {
                    StringBuilder appendContent = new StringBuilder();
                    appendContent.append("\n\n");
                    appendContent.append("// The following config values were automatically added\n");
                    appendContent.append("// You can modify these values as needed\n");
                    appendContent.append("\n");
                    appendContent.append(String.join("\n", sectionsToAppend));
                    appendContent.append("\n");
                    
                    // Append to existing config file
                    ConfigFileManager.appendToConfigFile(configPath, appendContent.toString());
                    LOGGER.info("Successfully appended missing config values to {}", configPath.toAbsolutePath());
                }
            }
        } catch (IOException ex) {
            LOGGER.warn("Failed to update config with missing values: {}", ex.getMessage());
            // Don't fail config loading if this update fails
        } catch (Exception ex) {
            LOGGER.warn("Failed to update config with missing values: {}", ex.getMessage());
            // Don't fail config loading if this update fails
        }
    }
    
    /**
     * Finds missing config keys by comparing user config with all defined options.
     */
    private static Set<String> findMissingKeys(Config userConfig) {
        Set<String> missingKeys = new HashSet<>();
        for (ConfigOption option : values()) {
            if (!option.isRequired() && !option.hasValue(userConfig)) {
                missingKeys.add(option.getKey());
            }
        }
        return missingKeys;
    }
    
    /**
     * Extracts config sections (including comments) for the specified keys from the default config lines.
     */
    private static List<String> extractConfigSections(String[] lines, Set<String> keysToExtract) {
        List<String> sections = new ArrayList<>();
        
        for (String key : keysToExtract) {
            List<String> section = extractSectionForKey(lines, key);
            if (!section.isEmpty()) {
                sections.addAll(section);
                sections.add(""); // Add blank line between sections
            }
        }
        
        return sections;
    }
    
    /**
     * Extracts a single config section for a specific key, including preceding comments.
     */
    private static List<String> extractSectionForKey(String[] lines, String key) {
        List<String> section = new ArrayList<>();
        List<String> precedingComments = new ArrayList<>();
        boolean foundKey = false;
        int braceDepth = 0;
        Pattern keyPattern = Pattern.compile("^" + Pattern.quote(key) + "\\s*[={]");
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            
            if (!foundKey) {
                // Look for the key
                if (keyPattern.matcher(trimmed).find()) {
                    foundKey = true;
                    // Add preceding comments
                    section.addAll(precedingComments);
                    section.add(line);
                    precedingComments.clear();
                    
                    // Check if this is a nested block
                    if (trimmed.contains("{")) {
                        braceDepth = countChar(trimmed, '{') - countChar(trimmed, '}');
                    }
                } else {
                    // Accumulate comments before keys
                    if (trimmed.isEmpty() || trimmed.startsWith("//")) {
                        precedingComments.add(line);
                    } else {
                        // Hit a different key, clear accumulated comments
                        precedingComments.clear();
                    }
                }
            } else {
                // We're in the section, collect content
                section.add(line);
                
                // Track brace depth for nested configs
                if (trimmed.contains("{") || trimmed.contains("}")) {
                    braceDepth += countChar(trimmed, '{') - countChar(trimmed, '}');
                }
                
                // Check if we've finished this section
                if (braceDepth == 0) {
                    // For simple key-value pairs, we're done after the value line
                    // For nested blocks, we're done after closing brace
                    // Check if next non-comment line is a new key
                    boolean nextIsKey = false;
                    for (int j = i + 1; j < lines.length; j++) {
                        String nextTrimmed = lines[j].trim();
                        if (nextTrimmed.isEmpty() || nextTrimmed.startsWith("//")) {
                            continue;
                        }
                        // Check if it's a top-level key
                        if (Pattern.matches("^[a-zA-Z_][a-zA-Z0-9_]*\\s*[={]", nextTrimmed)) {
                            nextIsKey = true;
                        }
                        break;
                    }
                    
                    // If next line is a key or we're at the end, finish this section
                    if (nextIsKey || i == lines.length - 1) {
                        break;
                    }
                }
            }
        }
        
        return section;
    }
    
    /**
     * Counts occurrences of a character in a string.
     */
    private static int countChar(String str, char ch) {
        int count = 0;
        for (char c : str.toCharArray()) {
            if (c == ch) count++;
        }
        return count;
    }
}
