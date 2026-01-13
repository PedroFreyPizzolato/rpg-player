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
package com.jagrosh.jmusicbot.config.migrations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jagrosh.jmusicbot.audio.AudioSource;
import com.jagrosh.jmusicbot.config.migration.Migration;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;

/**
 * Migration from version 0 (legacy flat config) to version 1 (nested canonical schema).
 * 
 * @author Arif Banai (arif-banai)
 */
public class MigrationV0ToV1 implements Migration {
    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationV0ToV1.class);
    
    @Override
    public int getFromVersion() {
        return 0;
    }
    
    @Override
    public int getToVersion() {
        return 1;
    }
    
    @Override
    public Config migrate(Config source) {
        LOGGER.debug("Starting migration from version 0 to version 1");
        
        // Build new config structure from scratch, mapping all legacy keys to new format
        Map<String, Object> migrated = new HashMap<>();
        
        // Add meta version
        Map<String, Object> meta = new HashMap<>();
        meta.put("configVersion", 1);
        migrated.put("meta", meta);
        
        // Discord section: token, owner
        Map<String, Object> discord = new HashMap<>();
        if (source.hasPath("token")) {
            discord.put("token", source.getString("token"));
        }
        if (source.hasPath("owner")) {
            discord.put("owner", source.getLong("owner"));
        }
        if (!discord.isEmpty()) {
            migrated.put("discord", discord);
        }
        
        // Commands section: prefix, altprefix, help, aliases
        Map<String, Object> commands = new HashMap<>();
        if (source.hasPath("prefix")) {
            commands.put("prefix", source.getString("prefix"));
        }
        if (source.hasPath("altprefix")) {
            commands.put("altPrefix", source.getString("altprefix"));
        }
        if (source.hasPath("help")) {
            commands.put("help", source.getString("help"));
        }
        if (source.hasPath("aliases")) {
            // aliases is already a nested structure, just move it to commands.aliases
            commands.put("aliases", source.getConfig("aliases").root().unwrapped());
        }
        if (!commands.isEmpty()) {
            migrated.put("commands", commands);
        }
        
        // Presence section: game, status, songinstatus
        Map<String, Object> presence = new HashMap<>();
        if (source.hasPath("game")) {
            presence.put("game", source.getString("game"));
        }
        if (source.hasPath("status")) {
            // Normalize status case to uppercase
            String status = source.getString("status");
            if (status != null) {
                status = status.toUpperCase();
            }
            presence.put("status", status);
        }
        if (source.hasPath("songinstatus")) {
            presence.put("songInStatus", source.getBoolean("songinstatus"));
        }
        if (!presence.isEmpty()) {
            migrated.put("presence", presence);
        }
        
        // UI section: success, warning, error, loading, searching
        Map<String, Object> ui = new HashMap<>();
        Map<String, Object> emojis = new HashMap<>();
        if (source.hasPath("success")) {
            emojis.put("success", source.getString("success"));
        }
        if (source.hasPath("warning")) {
            emojis.put("warning", source.getString("warning"));
        }
        if (source.hasPath("error")) {
            emojis.put("error", source.getString("error"));
        }
        if (source.hasPath("loading")) {
            emojis.put("loading", source.getString("loading"));
        }
        if (source.hasPath("searching")) {
            emojis.put("searching", source.getString("searching"));
        }
        if (!emojis.isEmpty()) {
            ui.put("emojis", emojis);
        }
        if (!ui.isEmpty()) {
            migrated.put("ui", ui);
        }
        
        // NowPlaying section: npimages
        Map<String, Object> nowPlaying = new HashMap<>();
        if (source.hasPath("npimages")) {
            nowPlaying.put("images", source.getBoolean("npimages"));
        }
        if (!nowPlaying.isEmpty()) {
            migrated.put("nowPlaying", nowPlaying);
        }
        
        // Voice section: stayinchannel, alonetimeuntilstop
        Map<String, Object> voice = new HashMap<>();
        if (source.hasPath("stayinchannel")) {
            voice.put("stayInChannel", source.getBoolean("stayinchannel"));
        }
        if (source.hasPath("alonetimeuntilstop")) {
            voice.put("aloneTimeUntilStopSeconds", source.getLong("alonetimeuntilstop"));
        }
        if (!voice.isEmpty()) {
            migrated.put("voice", voice);
        }
        
        // Playback section: maxtime, maxytplaylistpages, skipratio, useyoutubeoauth, audiosources, transforms
        Map<String, Object> playback = new HashMap<>();
        if (source.hasPath("maxtime")) {
            playback.put("maxTrackSeconds", source.getLong("maxtime"));
        }
        if (source.hasPath("maxytplaylistpages")) {
            playback.put("maxYouTubePlaylistPages", source.getInt("maxytplaylistpages"));
        }
        if (source.hasPath("skipratio")) {
            playback.put("skipRatio", source.getDouble("skipratio"));
        }
        
        // YouTube OAuth nested under playback.youtube
        if (source.hasPath("useyoutubeoauth")) {
            Map<String, Object> youtube = new HashMap<>();
            youtube.put("useOAuth", source.getBoolean("useyoutubeoauth"));
            playback.put("youtube", youtube);
        }
        
        // Audio sources migration (special handling: list to boolean map)
        // Always migrate audioSources - if missing, defaults to all enabled
        Map<String, Boolean> audioSourcesMap = migrateAudioSources(source);
        if (!audioSourcesMap.isEmpty()) {
            playback.put("audioSources", audioSourcesMap);
        }
        
        // Transforms is already a nested structure, just move it to playback.transforms
        if (source.hasPath("transforms")) {
            playback.put("transforms", source.getConfig("transforms").root().unwrapped());
        }
        
        if (!playback.isEmpty()) {
            migrated.put("playback", playback);
        }
        
        // Paths section: playlistsfolder
        Map<String, Object> paths = new HashMap<>();
        if (source.hasPath("playlistsfolder")) {
            paths.put("playlistsFolder", source.getString("playlistsfolder"));
        }
        if (!paths.isEmpty()) {
            migrated.put("paths", paths);
        }
        
        // Updates section: updatealerts
        Map<String, Object> updates = new HashMap<>();
        if (source.hasPath("updatealerts")) {
            updates.put("alerts", source.getBoolean("updatealerts"));
        }
        if (!updates.isEmpty()) {
            migrated.put("updates", updates);
        }
        
        // Lyrics section: lyrics.default (note: dot notation in legacy, stays as nested in new format)
        Map<String, Object> lyrics = new HashMap<>();
        if (source.hasPath("lyrics.default")) {
            lyrics.put("default", source.getString("lyrics.default"));
        }
        if (!lyrics.isEmpty()) {
            migrated.put("lyrics", lyrics);
        }
        
        // Logging section: loglevel
        Map<String, Object> logging = new HashMap<>();
        if (source.hasPath("loglevel")) {
            logging.put("level", source.getString("loglevel"));
        }
        if (!logging.isEmpty()) {
            migrated.put("logging", logging);
        }
        
        // Dangerous section: eval, evalengine
        Map<String, Object> dangerous = new HashMap<>();
        if (source.hasPath("eval")) {
            dangerous.put("eval", source.getBoolean("eval"));
        }
        if (source.hasPath("evalengine")) {
            dangerous.put("evalEngine", source.getString("evalengine"));
        }
        if (!dangerous.isEmpty()) {
            migrated.put("dangerous", dangerous);
        }
        
        // Build the migrated config
        Config migratedConfig = ConfigFactory.parseMap(migrated);
        LOGGER.debug("Migration from version 0 to version 1 completed");
        
        return migratedConfig;
    }
    
    /**
     * Migrates audio sources from legacy list format to new boolean map format.
     * 
     * @param source the legacy config
     * @return a map of audio source names to boolean values
     */
    private Map<String, Boolean> migrateAudioSources(Config source) {
        Map<String, Boolean> audioSourcesMap = new HashMap<>();
        
        // Initialize all sources to false
        for (AudioSource audioSource : AudioSource.values()) {
            audioSourcesMap.put(audioSource.getConfigName(), false);
        }
        
        if (source.hasPath("audiosources")) {
            try {
                List<String> enabledSources = source.getStringList("audiosources");
                LOGGER.debug("Migrating audio sources list: {}", enabledSources);
                
                // Set enabled sources to true
                for (String sourceName : enabledSources) {
                    AudioSource.fromConfigName(sourceName).ifPresent(sourceEnum -> {
                        audioSourcesMap.put(sourceEnum.getConfigName(), true);
                        LOGGER.debug("Enabled audio source: {}", sourceEnum.getConfigName());
                    });
                }
            } catch (ConfigException e) {
                LOGGER.warn("Failed to read audiosources list, defaulting to all enabled: {}", e.getMessage());
                // If parsing fails, enable all sources (default behavior)
                for (AudioSource audioSource : AudioSource.values()) {
                    audioSourcesMap.put(audioSource.getConfigName(), true);
                }
            }
        } else {
            // No audiosources key means all enabled (default behavior)
            LOGGER.debug("No audiosources key found, defaulting to all enabled");
            for (AudioSource audioSource : AudioSource.values()) {
                audioSourcesMap.put(audioSource.getConfigName(), true);
            }
        }
        
        return audioSourcesMap;
    }
    
    /**
     * Checks if a key is a legacy flat key that should be migrated to nested structure.
     * Based on reference-legacy.conf, these are all the flat keys in the legacy format.
     * 
     * @param key the key to check
     * @return true if it's a legacy flat key that needs migration
     */
    private boolean isLegacyFlatKey(String key) {
        // List of known legacy flat keys from reference-legacy.conf
        return key.equals("token") || key.equals("owner") ||
               key.equals("prefix") || key.equals("altprefix") || key.equals("help") ||
               key.equals("game") || key.equals("status") || key.equals("songinstatus") ||
               key.equals("success") || key.equals("warning") || key.equals("error") ||
               key.equals("loading") || key.equals("searching") ||
               key.equals("npimages") || key.equals("stayinchannel") || key.equals("alonetimeuntilstop") ||
               key.equals("maxtime") || key.equals("maxytplaylistpages") || key.equals("skipratio") ||
               key.equals("useyoutubeoauth") || key.equals("playlistsfolder") || key.equals("updatealerts") ||
               key.equals("loglevel") || key.equals("eval") || key.equals("evalengine") ||
               key.equals("audiosources") || key.equals("transforms") || key.equals("aliases") ||
               key.startsWith("lyrics.");
    }
}
