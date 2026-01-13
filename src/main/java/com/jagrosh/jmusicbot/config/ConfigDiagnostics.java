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

import java.util.HashSet;
import java.util.Set;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;

/**
 * Detects missing keys, deprecated keys, and unknown keys in configuration.
 * 
 * @author Arif Banai (arif-banai)
 */
public class ConfigDiagnostics {
    private static final String META_CONFIG_VERSION_KEY = "meta.configVersion";
    
    /**
     * Analyzes a user configuration against defaults and returns a diagnostic report.
     * 
     * @param migratedUserConfig the migrated user config (after migration, before merging with defaults)
     * @param mergedConfig the merged config (user + defaults)
     * @param defaults the default configuration
     * @return a diagnostic report
     */
    public static Report analyze(Config migratedUserConfig, Config mergedConfig, Config defaults) {
        Set<String> missingRequired = detectMissingRequiredKeys(migratedUserConfig, mergedConfig, defaults);
        Set<String> missingOptional = detectMissingOptionalKeys(migratedUserConfig, defaults);
        Set<String> deprecated = detectDeprecatedKeys(migratedUserConfig, defaults);
        
        return new Report(missingRequired, missingOptional, deprecated);
    }
    
    /**
     * Detects required keys that are missing from the user config.
     * 
     * @param migratedUserConfig the migrated user config (after migration, before merging)
     * @param mergedConfig the merged config (to check what's actually available)
     * @param defaults the default configuration
     * @return set of missing required key paths
     */
    private static Set<String> detectMissingRequiredKeys(Config migratedUserConfig, Config mergedConfig, Config defaults) {
        Set<String> missing = new HashSet<>();
        
        for (ConfigOption option : ConfigOption.values()) {
            if (option.isRequired()) {
                String key = option.getKey();

                if (!migratedUserConfig.hasPath(key)) {
                    missing.add(key);
                }
            }
        }
        
        return missing;
    }
    
    /**
     * Detects optional keys from defaults that are missing from the user config.
     * 
     * @param migratedUserConfig the migrated user config (before merging with defaults)
     * @param defaults the default configuration
     * @return set of missing optional key paths
     */
    private static Set<String> detectMissingOptionalKeys(Config migratedUserConfig, Config defaults) {
        Set<String> missing = new HashSet<>();
        Set<String> knownKeys = ConfigOption.getAllKeys();
        
        // Traverse all paths in defaults and check if they exist in user config
        collectPaths(defaults, "", missing, knownKeys, migratedUserConfig);
        
        return missing;
    }
    
    /**
     * Recursively collects paths from a config that are missing in the user config.
     * 
     * @param config the config to traverse
     * @param prefix the current path prefix
     * @param missing the set to add missing paths to
     * @param knownKeys set of known config option keys
     * @param userConfig the user config to check against
     */
    private static void collectPaths(Config config, String prefix, Set<String> missing, 
                                     Set<String> knownKeys, Config userConfig) {
        for (String key : config.root().keySet()) {
            String fullPath = prefix.isEmpty() ? key : prefix + "." + key;
            
            // Skip meta.configVersion as it's added by migration
            if (META_CONFIG_VERSION_KEY.equals(fullPath)) {
                continue;
            }
            
            ConfigValue value = config.getValue(key);
            
            // If it's a nested object, recurse
            if (value.valueType() == com.typesafe.config.ConfigValueType.OBJECT) {
                try {
                    Config nestedConfig = config.getConfig(key);
                    collectPaths(nestedConfig, fullPath, missing, knownKeys, userConfig);
                } catch (Exception e) {
                    // Skip if not a config object
                }
            } else {
                // It's a leaf value - check if it exists in user config
                if (!userConfig.hasPath(fullPath)) {
                    // Only report if it's a known key or we want to report all missing defaults
                    // For now, report all missing defaults as they might be new options
                    missing.add(fullPath);
                }
            }
        }
    }
    
    /**
     * Detects deprecated/unknown keys in the user config that don't exist in defaults.
     * 
     * @param migratedUserConfig the migrated user config (after migration, before merging)
     * @param defaults the default configuration
     * @return set of deprecated key paths
     */
    private static Set<String> detectDeprecatedKeys(Config migratedUserConfig, Config defaults) {
        Set<String> deprecated = new HashSet<>();
        Set<String> knownKeys = ConfigOption.getAllKeys();
        
        // Collect all paths from migrated user config
        collectUserPaths(migratedUserConfig, "", deprecated, knownKeys, defaults);
        
        return deprecated;
    }
    
    /**
     * Recursively collects paths from user config that don't exist in defaults.
     * 
     * @param config the user config to traverse
     * @param prefix the current path prefix
     * @param deprecated the set to add deprecated paths to
     * @param knownKeys set of known config option keys
     * @param defaults the default config to check against (may be a nested config)
     */
    private static void collectUserPaths(Config config, String prefix, Set<String> deprecated,
                                        Set<String> knownKeys, Config defaults) {
        for (String key : config.root().keySet()) {
            String fullPath = prefix.isEmpty() ? key : prefix + "." + key;
            
            // Skip meta.configVersion as it's expected in migrated configs
            if (META_CONFIG_VERSION_KEY.equals(fullPath)) {
                continue;
            }
            
            ConfigValue value = config.getValue(key);
            
            // If it's a nested object, recurse
            if (value.valueType() == com.typesafe.config.ConfigValueType.OBJECT) {
                try {
                    Config nestedConfig = config.getConfig(key);
                    // Check if the key exists in the current defaults context
                    if (!defaults.hasPath(key)) {
                        // Key doesn't exist in defaults at this level, mark as deprecated
                        deprecated.add(fullPath);
                    } else {
                        // Key exists, get nested config and recurse
                        try {
                            Config defaultNested = defaults.getConfig(key);
                            collectUserPaths(nestedConfig, fullPath, deprecated, knownKeys, defaultNested);
                        } catch (Exception e) {
                            // Not a config object in defaults, mark as deprecated
                            deprecated.add(fullPath);
                        }
                    }
                } catch (Exception e) {
                    // Not a config object in user config, treat as leaf
                    if (!defaults.hasPath(key)) {
                        deprecated.add(fullPath);
                    }
                }
            } else {
                // It's a leaf value - check if the key exists in the current defaults context
                if (!defaults.hasPath(key)) {
                    deprecated.add(fullPath);
                }
            }
        }
    }
    
    /**
     * Diagnostic report containing missing and deprecated keys.
     */
    public static class Report {
        private final Set<String> missingRequired;
        private final Set<String> missingOptional;
        private final Set<String> deprecated;
        
        public Report(Set<String> missingRequired, Set<String> missingOptional, Set<String> deprecated) {
            this.missingRequired = new HashSet<>(missingRequired);
            this.missingOptional = new HashSet<>(missingOptional);
            this.deprecated = new HashSet<>(deprecated);
        }
        
        public Set<String> getMissingRequired() {
            return new HashSet<>(missingRequired);
        }
        
        public Set<String> getMissingOptional() {
            return new HashSet<>(missingOptional);
        }
        
        public Set<String> getDeprecated() {
            return new HashSet<>(deprecated);
        }
        
        public boolean hasIssues() {
            return !missingRequired.isEmpty() || !missingOptional.isEmpty() || !deprecated.isEmpty();
        }
        
        public boolean hasErrors() {
            return !missingRequired.isEmpty();
        }
        
        public boolean hasWarnings() {
            return !missingOptional.isEmpty() || !deprecated.isEmpty();
        }
        
        /**
         * Generates a formatted diagnostic message.
         */
        public String generateMessage() {
            StringBuilder sb = new StringBuilder();
            
            if (!missingRequired.isEmpty()) {
                sb.append("ERROR: Missing required keys: ").append(missingRequired).append("\n");
            }
            
            if (!missingOptional.isEmpty()) {
                sb.append("WARN: Missing optional keys (new options available): ").append(missingOptional).append("\n");
            }
            
            if (!deprecated.isEmpty()) {
                sb.append("WARN: Deprecated/unknown keys (will be ignored): ").append(deprecated).append("\n");
            }
            
            return sb.toString();
        }
    }
}
