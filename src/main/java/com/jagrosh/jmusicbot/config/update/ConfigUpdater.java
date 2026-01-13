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
package com.jagrosh.jmusicbot.config.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jagrosh.jmusicbot.config.diagnostics.ConfigDiagnostics;
import com.jagrosh.jmusicbot.config.io.ConfigFileManager;
import com.jagrosh.jmusicbot.config.render.ConfigRenderer;
import com.typesafe.config.Config;

/**
 * Generates updated configuration files to guide users through config updates.
 * 
 * @author Arif Banai (arif-banai)
 */
public class ConfigUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigUpdater.class);
    private static final String BACKUP_SUFFIX = ".bak";
    
    /**
     * Updates the config file in place by backing up the original and writing the migrated config.
     * The original config file is backed up with a .bak extension.
     * 
     * @param userConfigPath the path to the user's config file
     * @param migratedUserConfig the migrated user configuration (without defaults merged)
     * @param diagnostics the diagnostic report
     * @return the path to the updated config file, or null if update failed
     */
    public static Path generateUpdatedConfig(Path userConfigPath, Config migratedUserConfig, 
                                             ConfigDiagnostics.Report diagnostics) {
        try {
            // Normalize to absolute path
            Path normalizedPath = userConfigPath.toAbsolutePath().normalize();
            Path backupPath = normalizedPath.resolveSibling(normalizedPath.getFileName().toString() + BACKUP_SUFFIX);
            
            // Backup the original config file if it exists
            if (normalizedPath.toFile().exists()) {
                Files.copy(normalizedPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Backed up original config to: {}", backupPath);
            }
            
            // Generate HOCON content using ConfigDocument to preserve template style
            String content = ConfigRenderer.generateConfigContent(migratedUserConfig, diagnostics);
            
            // Write the migrated config to the original location
            ConfigFileManager.writeConfigFile(normalizedPath, content);
            
            LOGGER.info("Updated config file: {} (original backed up to: {})", normalizedPath, backupPath);
            return normalizedPath;
        } catch (IOException e) {
            LOGGER.error("Failed to update config file: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Checks if a backup of the config file exists.
     * 
     * @param userConfigPath the path to the user's config file
     * @return true if a backup file exists
     */
    public static boolean backupExists(Path userConfigPath) {
        Path normalizedPath = userConfigPath.toAbsolutePath().normalize();
        Path backupPath = normalizedPath.resolveSibling(normalizedPath.getFileName().toString() + BACKUP_SUFFIX);
        return backupPath.toFile().exists();
    }
}
