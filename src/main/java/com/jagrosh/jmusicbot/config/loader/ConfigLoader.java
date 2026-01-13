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
package com.jagrosh.jmusicbot.config.loader;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jagrosh.jmusicbot.config.io.ConfigResourceLoader;
import com.jagrosh.jmusicbot.config.migration.ConfigMigration;
import com.jagrosh.jmusicbot.config.migration.ConfigMigrationException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;

/**
 * Handles loading and parsing configuration files with migration support.
 * 
 * @author Arif Banai (arif-banai)
 */
public class ConfigLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigLoader.class);
    
    /**
     * Loads the raw user config file without merging with defaults.
     * This is used for version detection and migration.
     * 
     * @param configPath the path to the config file
     * @return the raw parsed config, or empty config if file doesn't exist
     */
    public static Config loadRawUserConfig(Path configPath) {
        if (!configPath.toFile().exists()) {
            return ConfigFactory.empty();
        }
        try {
            return ConfigFactory.parseFile(configPath.toFile());
        } catch (ConfigException.Parse e) {
            LOGGER.error("Failed to parse config file at {}: {}", configPath, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Loads the user's config file if it exists, otherwise returns an empty config.
     * This is a convenience method that calls loadRawUserConfig.
     * 
     * @deprecated Use loadRawUserConfig for migration-aware loading, or loadMergedConfig for merged config
     */
    @Deprecated
    public static Config loadUserConfig(Path configPath) {
        return loadRawUserConfig(configPath);
    }
    
    /**
     * Loads the migrated user config (before merging with defaults).
     * This is useful for diagnostics to check what the user actually provided.
     * 
     * @param configPath the path to the config file
     * @return the migrated user config (without defaults merged)
     */
    public static Config loadMigratedUserConfig(Path configPath) {
        // Load raw user config (before merging)
        Config rawUserConfig = loadRawUserConfig(configPath);
        
        // Load defaults to get latest version
        Config defaults = ConfigResourceLoader.loadDefaults();
        
        // Detect versions
        int userVersion = ConfigMigration.detectVersion(rawUserConfig);
        int latestVersion = ConfigMigration.getLatestVersion(defaults);
        
        // Apply migrations if needed
        if (userVersion < latestVersion) {
            try {
                return ConfigMigration.migrate(rawUserConfig, userVersion, latestVersion);
            } catch (ConfigMigrationException e) {
                LOGGER.error("Config migration failed: {}", e.getMessage());
                // Fall back to using raw config (may cause validation failures)
                return rawUserConfig;
            }
        } else {
            return rawUserConfig;
        }
    }
    
    /**
     * Loads the merged config (user config with defaults as fallback).
     * This method now handles version detection and migration automatically.
     * 
     * @param configPath the path to the config file
     * @return the merged config with migrations applied
     */
    public static Config loadMergedConfig(Path configPath) {
        // Load raw user config (before merging)
        Config rawUserConfig = loadRawUserConfig(configPath);
        
        // Load defaults
        Config defaults = ConfigResourceLoader.loadDefaults();
        
        // Detect versions
        int userVersion = ConfigMigration.detectVersion(rawUserConfig);
        int latestVersion = ConfigMigration.getLatestVersion(defaults);
        
        LOGGER.info("Config version detected: {}, latest version: {}", userVersion, latestVersion);
        
        // Apply migrations if needed
        Config migratedUserConfig;
        if (userVersion < latestVersion) {
            try {
                migratedUserConfig = ConfigMigration.migrate(rawUserConfig, userVersion, latestVersion);
                LOGGER.info("Config migrated from version {} to version {}", userVersion, latestVersion);
            } catch (ConfigMigrationException e) {
                LOGGER.error("Config migration failed: {}", e.getMessage());
                // Fall back to using raw config (may cause validation failures)
                migratedUserConfig = rawUserConfig;
            }
        } else {
            migratedUserConfig = rawUserConfig;
        }
        
        // Merge with defaults (migrated user config takes precedence)
        return migratedUserConfig.withFallback(defaults).resolve();
    }
    
    /**
     * Loads the merged config using an already-migrated user config.
     * This avoids re-running migration when the migrated config is already available.
     * 
     * @param migratedUserConfig the already-migrated user config
     * @return the merged config with defaults
     */
    public static Config loadMergedConfig(Config migratedUserConfig) {
        // Load defaults
        Config defaults = ConfigResourceLoader.loadDefaults();
        
        // Merge with defaults (migrated user config takes precedence)
        return migratedUserConfig.withFallback(defaults).resolve();
    }
}
