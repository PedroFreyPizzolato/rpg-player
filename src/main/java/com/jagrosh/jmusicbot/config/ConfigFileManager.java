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

import com.jagrosh.jmusicbot.JMusicBot;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import com.typesafe.config.ConfigFactory;

/**
 * Handles file operations for configuration files.
 * 
 * @author Arif Banai (arif-banai)
 */
public class ConfigFileManager {
    private static final String START_TOKEN = "/// START OF JMUSICBOT CONFIG ///";
    private static final String END_TOKEN = "/// END OF JMUSICBOT CONFIG ///";
    
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
        String original = OtherUtil.loadResource(new JMusicBot(), "/reference.conf");
        return original == null
                ? "token = BOT_TOKEN_HERE\r\nowner = 0 // OWNER ID"
                : original.substring(original.indexOf(START_TOKEN) + START_TOKEN.length(), original.indexOf(END_TOKEN))
                        .trim();
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
}
