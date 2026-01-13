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

import java.io.IOException;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigRenderOptions;

/**
 * Generates updated configuration files to guide users through config updates.
 * 
 * @author Arif Banai (arif-banai)
 */
public class ConfigUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigUpdater.class);
    private static final String UPDATED_CONFIG_FILENAME = "config.updated.conf";
    
    /**
     * Generates an updated config file with migrated values and missing defaults.
     * The generated file is placed next to the user's config file.
     * 
     * @param userConfigPath the path to the user's config file
     * @param migratedConfig the migrated/merged configuration
     * @param diagnostics the diagnostic report
     * @return the path to the generated file, or null if generation failed
     */
    public static Path generateUpdatedConfig(Path userConfigPath, Config migratedConfig, 
                                             ConfigDiagnostics.Report diagnostics) {
        try {
            Path outputPath = userConfigPath.getParent().resolve(UPDATED_CONFIG_FILENAME);
            
            // Generate HOCON content
            String content = generateConfigContent(migratedConfig, diagnostics);
            
            // Write to file
            ConfigFileManager.writeConfigFile(outputPath, content);
            
            LOGGER.info("Generated updated config file: {}", outputPath);
            return outputPath;
        } catch (IOException e) {
            LOGGER.error("Failed to generate updated config file: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Generates the HOCON content for the updated config file.
     * 
     * @param config the merged configuration
     * @param diagnostics the diagnostic report
     * @return the HOCON content as a string
     */
    private static String generateConfigContent(Config config, ConfigDiagnostics.Report diagnostics) {
        StringBuilder sb = new StringBuilder();
        
        // Header comment
        sb.append("// START OF JMUSICBOT CONFIG //\n");
        sb.append("//\n");
        sb.append("// This file was automatically generated.\n");
        sb.append("// Review the changes and manually merge them into your config file.\n");
        sb.append("// Your original config file has NOT been modified.\n");
        sb.append("//\n");
        
        if (diagnostics.hasIssues()) {
            sb.append("// Changes detected:\n");
            if (!diagnostics.getMissingRequired().isEmpty()) {
                sb.append("// - Missing required keys: ").append(diagnostics.getMissingRequired()).append("\n");
            }
            if (!diagnostics.getMissingOptional().isEmpty()) {
                sb.append("// - Missing optional keys (new options): ").append(diagnostics.getMissingOptional()).append("\n");
            }
            if (!diagnostics.getDeprecated().isEmpty()) {
                sb.append("// - Deprecated keys removed: ").append(diagnostics.getDeprecated()).append("\n");
            }
            sb.append("//\n");
        }
        
        // Render the config
        // Use ConfigRenderOptions to get a clean, formatted output
        ConfigRenderOptions options = ConfigRenderOptions.defaults()
                .setOriginComments(false)
                .setComments(true)
                .setFormatted(true)
                .setJson(false);
        
        String configContent = config.root().render(options);
        
        // Extract just the config part (remove outer braces if present)
        // The config should already be properly formatted
        sb.append(configContent);
        sb.append("\n// END OF JMUSICBOT CONFIG //\n");
        
        return sb.toString();
    }
    
    /**
     * Checks if an updated config file exists.
     * 
     * @param userConfigPath the path to the user's config file
     * @return true if the updated config file exists
     */
    public static boolean updatedConfigExists(Path userConfigPath) {
        Path updatedPath = userConfigPath.getParent().resolve(UPDATED_CONFIG_FILENAME);
        return updatedPath.toFile().exists();
    }
}
