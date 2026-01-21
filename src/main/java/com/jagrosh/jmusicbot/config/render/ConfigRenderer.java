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
package com.jagrosh.jmusicbot.config.render;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jagrosh.jmusicbot.config.diagnostics.ConfigDiagnostics;
import com.jagrosh.jmusicbot.config.io.ConfigResourceLoader;
import com.jagrosh.jmusicbot.config.model.ConfigOption;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.parser.ConfigDocument;

/**
 * Handles rendering of configuration objects to HOCON strings.
 * 
 * @author Arif Banai (arif-banai)
 */
public class ConfigRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigRenderer.class);
    
    /**
     * Generates the HOCON content for the updated config file.
     * Uses ConfigDocument to preserve the style, comments, and ordering from reference.conf.
     * 
     * @param migratedUserConfig the migrated user configuration (without defaults merged)
     * @param diagnostics the diagnostic report
     * @return the HOCON content as a string
     */
    public static String generateConfigContent(Config migratedUserConfig, ConfigDiagnostics.Report diagnostics) {
        // Load reference.conf as ConfigDocument (template)
        ConfigDocument templateDoc = ConfigResourceLoader.loadReferenceConfigAsDocument();
        
        if (templateDoc == null) {
            // Fallback to old behavior if template cannot be loaded
            LOGGER.warn("Could not load reference.conf as ConfigDocument, falling back to rendered config");
            return generateConfigContentFallback(migratedUserConfig, diagnostics);
        }
        
        // Start with the template document
        ConfigDocument outputDoc = templateDoc;
        
        // For each ConfigOption, if migratedUserConfig has a value, update the document
        for (ConfigOption option : ConfigOption.values()) {
            String key = option.getKey();
            
            // Check if user config has this key
            if (migratedUserConfig.hasPath(key)) {
                try {
                    // Handle nested configs (CONFIG type) specially
                    // For nested configs, we need to update individual keys to preserve
                    // template keys that aren't in the user config
                    if (option.getType() == ConfigOption.ConfigType.CONFIG) {
                        Config nestedConfig = option.getConfig(migratedUserConfig);
                        // Update each key within the nested config individually
                        // This preserves keys from the template that aren't in the user config
                        for (String nestedKey : nestedConfig.root().keySet()) {
                            String fullPath = key + "." + nestedKey;
                            try {
                                ConfigValue nestedValue = nestedConfig.getValue(nestedKey);
                                String renderedValue = HoconRenderUtil.renderValue(nestedValue);
                                outputDoc = outputDoc.withValueText(fullPath, renderedValue);
                            } catch (ConfigException e) {
                                LOGGER.debug("Could not get nested value for key {}: {}", fullPath, e.getMessage());
                            }
                        }
                    } else {
                        // For simple values, get the ConfigValue and render it
                        ConfigValue value = migratedUserConfig.getValue(key);
                        String renderedValue = HoconRenderUtil.renderValue(value);
                        // Update the document with the user's value
                        outputDoc = outputDoc.withValueText(key, renderedValue);
                    }
                } catch (ConfigException e) {
                    // If we can't get the value, skip it (will use template default)
                    LOGGER.debug("Could not get value for key {}: {}", key, e.getMessage());
                }
            }
            // If user config doesn't have this key, the template default remains
        }
        
        // Build the output with header/footer comments
        StringBuilder sb = new StringBuilder();
        
        // Header comment
        sb.append("# This file was automatically migrated and updated.\n");
        sb.append("# Your original config file has been backed up with a .bak extension.\n");
        sb.append("#\n");
        
        if (diagnostics.hasIssues()) {
            sb.append("# Changes detected:\n");
            if (!diagnostics.getMissingRequired().isEmpty()) {
                sb.append("# - Missing required keys: ").append(diagnostics.getMissingRequired()).append("\n");
            }
            if (!diagnostics.getMissingOptional().isEmpty()) {
                sb.append("# - Missing optional keys (new options): ").append(diagnostics.getMissingOptional()).append("\n");
            }
            if (!diagnostics.getDeprecated().isEmpty()) {
                sb.append("# - Deprecated keys removed: ").append(diagnostics.getDeprecated()).append("\n");
            }
            sb.append("#\n");
        }
        
        // Render the document (preserves comments and formatting)
        String configContent = outputDoc.render();
        sb.append(configContent);
        
        return sb.toString();
    }
    
    /**
     * Fallback method that uses the old rendering approach if ConfigDocument cannot be loaded.
     * Merges migratedUserConfig with defaults to match old behavior.
     * 
     * @param migratedUserConfig the migrated user configuration (without defaults merged)
     * @param diagnostics the diagnostic report
     * @return the HOCON content as a string
     */
    public static String generateConfigContentFallback(Config migratedUserConfig, ConfigDiagnostics.Report diagnostics) {
        // Merge with defaults to match old behavior
        Config defaults = ConfigResourceLoader.loadDefaults();
        Config config = migratedUserConfig.withFallback(defaults).resolve();
        StringBuilder sb = new StringBuilder();
        
        // Header comment
        sb.append("# This file was automatically migrated and updated.\n");
        sb.append("# Your original config file has been backed up with a .bak extension.\n");
        sb.append("#\n");
        
        if (diagnostics.hasIssues()) {
            sb.append("# Changes detected:\n");
            if (!diagnostics.getMissingRequired().isEmpty()) {
                sb.append("# - Missing required keys: ").append(diagnostics.getMissingRequired()).append("\n");
            }
            if (!diagnostics.getMissingOptional().isEmpty()) {
                sb.append("# - Missing optional keys (new options): ").append(diagnostics.getMissingOptional()).append("\n");
            }
            if (!diagnostics.getDeprecated().isEmpty()) {
                sb.append("# - Deprecated keys removed: ").append(diagnostics.getDeprecated()).append("\n");
            }
            sb.append("#\n");
        }
        
        // Render the config using old method
        com.typesafe.config.ConfigRenderOptions options = com.typesafe.config.ConfigRenderOptions.defaults()
                .setOriginComments(false)
                .setComments(true)
                .setFormatted(true)
                .setJson(false);
        
        String configContent = config.root().render(options);
        sb.append(configContent);
        
        return sb.toString();
    }
}
