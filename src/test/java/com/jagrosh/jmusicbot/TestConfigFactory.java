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
package com.jagrosh.jmusicbot;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating test Config objects and temporary config files.
 */
public class TestConfigFactory {
    
    /**
     * Creates a minimal valid config with only required fields.
     */
    public static Config createMinimalValidConfig() {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("token", "test_token_12345");
        configMap.put("owner", 123456789L);
        return ConfigFactory.parseMap(configMap);
    }
    
    /**
     * Creates a config with all optional fields set to default values.
     */
    public static Config createFullConfig() {
        Map<String, Object> configMap = new HashMap<>();
        
        // Required
        configMap.put("token", "test_token_12345");
        configMap.put("owner", 123456789L);
        
        // String options
        configMap.put("prefix", "@mention");
        configMap.put("altprefix", "NONE");
        configMap.put("help", "help");
        configMap.put("success", "🎶");
        configMap.put("warning", "💡");
        configMap.put("error", "🚫");
        configMap.put("loading", "⌚");
        configMap.put("searching", "🔎");
        configMap.put("game", "DEFAULT");
        configMap.put("status", "ONLINE");
        configMap.put("loglevel", "info");
        configMap.put("evalengine", "Nashorn");
        configMap.put("playlistsfolder", "Playlists");
        
        // Boolean options
        configMap.put("stayinchannel", false);
        configMap.put("songinstatus", false);
        configMap.put("npimages", false);
        configMap.put("updatealerts", true);
        configMap.put("eval", false);
        configMap.put("useyoutubeoauth", false);
        
        // Numeric options
        configMap.put("maxtime", 0L);
        configMap.put("maxytplaylistpages", 10);
        configMap.put("alonetimeuntilstop", 0L);
        configMap.put("skipratio", 0.55);
        
        // Complex options
        Map<String, Object> aliases = new HashMap<>();
        aliases.put("play", java.util.Collections.emptyList());
        aliases.put("skip", java.util.List.of("voteskip"));
        configMap.put("aliases", aliases);
        
        configMap.put("transforms", new HashMap<>());
        configMap.put("audiosources", java.util.List.of("youtube", "soundcloud"));
        
        return ConfigFactory.parseMap(configMap);
    }
    
    /**
     * Creates a config with specific values.
     */
    public static Config createConfig(Map<String, Object> values) {
        return ConfigFactory.parseMap(values);
    }
    
    /**
     * Creates a config from a string (HOCON format).
     */
    public static Config createConfigFromString(String configString) {
        return ConfigFactory.parseString(configString);
    }
    
    /**
     * Creates a temporary config file with the given content.
     */
    public static Path createTempConfigFile(String content) throws IOException {
        Path tempFile = Files.createTempFile("test-config-", ".conf");
        Files.write(tempFile, content.getBytes());
        return tempFile;
    }
    
    /**
     * Creates a temporary config file with minimal valid config.
     */
    public static Path createMinimalTempConfigFile() throws IOException {
        return createTempConfigFile("token = test_token_12345\nowner = 123456789");
    }
    
    /**
     * Creates a temporary config file with full config.
     */
    public static Path createFullTempConfigFile() throws IOException {
        String content = """
            token = test_token_12345
            owner = 123456789
            prefix = @mention
            altprefix = NONE
            help = help
            success = 🎶
            warning = 💡
            error = 🚫
            loading = ⌚
            searching = 🔎
            game = DEFAULT
            status = ONLINE
            loglevel = info
            evalengine = Nashorn
            playlistsfolder = Playlists
            stayinchannel = false
            songinstatus = false
            npimages = false
            updatealerts = true
            eval = false
            useyoutubeoauth = false
            maxtime = 0
            maxytplaylistpages = 10
            alonetimeuntilstop = 0
            skipratio = 0.55
            aliases {
              play = []
              skip = [ voteskip ]
            }
            transforms = {}
            audiosources = [ youtube, soundcloud ]
            """;
        return createTempConfigFile(content);
    }
    
    /**
     * Creates a config with invalid token (placeholder).
     */
    public static Config createConfigWithInvalidToken() {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("token", "BOT_TOKEN_HERE");
        configMap.put("owner", 123456789L);
        return ConfigFactory.parseMap(configMap);
    }
    
    /**
     * Creates a config with invalid owner (zero).
     */
    public static Config createConfigWithInvalidOwner() {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("token", "test_token_12345");
        configMap.put("owner", 0L);
        return ConfigFactory.parseMap(configMap);
    }
    
    /**
     * Creates a config with missing required fields.
     */
    public static Config createConfigWithMissingRequired() {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("prefix", "@mention");
        return ConfigFactory.parseMap(configMap);
    }
    
    /**
     * Creates a config with audio sources configuration.
     */
    public static Config createConfigWithAudioSources(String... sources) {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("token", "test_token_12345");
        configMap.put("owner", 123456789L);
        configMap.put("audiosources", java.util.List.of(sources));
        return ConfigFactory.parseMap(configMap);
    }
}
