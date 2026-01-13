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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import java.io.InputStream;

import com.jagrosh.jmusicbot.JMusicBot;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.parser.ConfigDocument;
import com.typesafe.config.parser.ConfigDocumentFactory;

/**
 * Handles file operations for configuration files.
 * 
 * @author Arif Banai (arif-banai)
 */
public class ConfigFileManager {
    /**
     * Gets the path to the config file, defaulting to config.txt.
     */
    public static Path getConfigPath() {
        Path path = OtherUtil.getPath(System.getProperty("config.file", System.getProperty("config", "config.txt")));
        if (path.toFile().exists()) {
            if (System.getProperty("config.file") == null)
                System.setProperty("config.file", System.getProperty("config", path.toAbsolutePath().toString()));
            ConfigFactory.invalidateCaches();
        }
        return path;
    }
    
    /**
     * Loads the default config content from reference.conf.
     */
    public static String loadDefaultConfig() {
        String original = OtherUtil.loadResource(JMusicBot.class, "/reference.conf");
        if (original == null) {
            // Fallback to legacy format if resource not found
            return "token = BOT_TOKEN_HERE\r\nowner = 0 // OWNER ID";
        }
        
        return original.trim();
    }
    
    /**
     * Writes content to the config file.
     */
    public static void writeConfigFile(Path path, String content) throws IOException {
        Files.write(path, content.getBytes());
    }
    
    /**
     * Appends content to the config file.
     */
    public static void appendToConfigFile(Path path, String content) throws IOException {
        Files.write(path, content.getBytes(), StandardOpenOption.APPEND);
    }
    
    /**
     * Checks if the config file exists.
     */
    public static boolean configFileExists(Path path) {
        return path.toFile().exists();
    }
    
    /**
     * Loads the default configuration from reference.conf in the classpath.
     * This explicitly loads the reference.conf resource file.
     * 
     * @return the default configuration
     */
    public static Config loadDefaults() {
        try {
            return ConfigFactory.parseResources("reference.conf");
        } catch (Exception e) {
            // Fallback to ConfigFactory.load() which also loads reference.conf
            // but may include other sources
            return ConfigFactory.load();
        }
    }
    
    /**
     * Loads the reference.conf content as a String (template).
     * 
     * @return the reference.conf content as a string, or null if not found
     */
    public static String loadReferenceConfigAsString() {
        String original = OtherUtil.loadResource(JMusicBot.class, "/reference.conf");
        if (original == null) {
            return null;
        }
        
        return original.trim();
    }
    
    /**
     * Loads the reference.conf as a ConfigDocument (template).
     * This preserves the original formatting, comments, and structure.
     * 
     * @return the ConfigDocument for reference.conf, or null if not found
     */
    public static ConfigDocument loadReferenceConfigAsDocument() {
        try {
            String content = loadReferenceConfigAsString();
            if (content == null) {
                // Fallback: try to load directly from resource
                try (InputStream is = JMusicBot.class.getResourceAsStream("/reference.conf")) {
                    if (is == null) {
                        return null;
                    }
                    return ConfigDocumentFactory.parseReader(
                        new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8)
                    );
                }
            }
            return ConfigDocumentFactory.parseString(content);
        } catch (Exception e) {
            // If parsing fails, return null
            return null;
        }
    }
}
