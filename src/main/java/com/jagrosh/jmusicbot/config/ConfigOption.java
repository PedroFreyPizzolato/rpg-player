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

import java.util.List;
import java.util.Set;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;

/**
 * Enumeration of all configuration options for the bot.
 * This provides a centralized, type-safe way to manage config options.
 * 
 * @author Arif Banai (arif-banai)
 */
public enum ConfigOption {
    // Required options
    TOKEN("token", ConfigType.STRING, true, "Bot token for authentication"),
    OWNER("owner", ConfigType.LONG, true, "Owner's Discord user ID"),
    
    // String options
    PREFIX("prefix", ConfigType.STRING, false, "Command prefix (use @mention for mention prefix)"),
    ALTPREFIX("altprefix", ConfigType.STRING, false, "Alternative command prefix (use NONE to disable)"),
    HELP_WORD("help", ConfigType.STRING, false, "Word used to view help"),
    SUCCESS_EMOJI("success", ConfigType.STRING, false, "Success emoji"),
    WARNING_EMOJI("warning", ConfigType.STRING, false, "Warning emoji"),
    ERROR_EMOJI("error", ConfigType.STRING, false, "Error emoji"),
    LOADING_EMOJI("loading", ConfigType.STRING, false, "Loading emoji"),
    SEARCHING_EMOJI("searching", ConfigType.STRING, false, "Searching emoji"),
    GAME("game", ConfigType.STRING, false, "Bot's game status (use DEFAULT for default, NONE for no game)"),
    STATUS("status", ConfigType.STRING, false, "Bot's online status (ONLINE, IDLE, DND, INVISIBLE)"),
    LOG_LEVEL("loglevel", ConfigType.STRING, false, "Logging verbosity (off, error, warn, info, debug, trace, all)"),
    EVAL_ENGINE("evalengine", ConfigType.STRING, false, "Eval engine name"),
    PLAYLISTS_FOLDER("playlistsfolder", ConfigType.STRING, false, "Alternative folder for playlists"),
    
    // Boolean options
    STAY_IN_CHANNEL("stayinchannel", ConfigType.BOOLEAN, false, "Whether to stay in voice channel after queue ends"),
    SONG_IN_GAME("songinstatus", ConfigType.BOOLEAN, false, "Whether to show current song in bot status"),
    NP_IMAGES("npimages", ConfigType.BOOLEAN, false, "Whether to show YouTube thumbnails in nowplaying"),
    UPDATE_ALERTS("updatealerts", ConfigType.BOOLEAN, false, "Whether to alert owner about updates"),
    USE_EVAL("eval", ConfigType.BOOLEAN, false, "Whether to enable eval command (DANGEROUS)"),
    USE_YOUTUBE_OAUTH("useyoutubeoauth", ConfigType.BOOLEAN, false, "Whether to use YouTube OAuth2 for playback"),
    
    // Numeric options
    MAX_SECONDS("maxtime", ConfigType.LONG, false, "Maximum track length in seconds (0 = no limit)"),
    MAX_YT_PLAYLIST_PAGES("maxytplaylistpages", ConfigType.INT, false, "Maximum YouTube playlist pages to load"),
    ALONE_TIME_UNTIL_STOP("alonetimeuntilstop", ConfigType.LONG, false, "Seconds to wait alone before leaving (0 = never)"),
    SKIP_RATIO("skipratio", ConfigType.DOUBLE, false, "Ratio of users needed to vote skip"),
    
    // Complex options - Nested configurations & string lists
    ALIASES("aliases", ConfigType.CONFIG, false, "Command aliases configuration"),
    TRANSFORMS("transforms", ConfigType.CONFIG, false, "Audio source transforms configuration"),
    AUDIO_SOURCES("audiosources", ConfigType.STRING_LIST, false, "List of enabled audio sources");
    
    private final String key;
    private final ConfigType type;
    private final boolean required;
    private final String description;
    
    ConfigOption(String key, ConfigType type, boolean required, String description) {
        this.key = key;
        this.type = type;
        this.required = required;
        this.description = description;
    }
    
    public String getKey() {
        return key;
    }
    
    public ConfigType getType() {
        return type;
    }
    
    public boolean isRequired() {
        return required;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Gets the value of this config option from the provided Config object.
     * 
     * @param config The Config object to read from
     * @return The value as an Object (cast appropriately based on type)
     * @deprecated Use type-specific getter methods instead for type safety
     */
    @Deprecated
    public Object getValue(Config config) {
        if (!config.hasPath(key)) {
            return null;
        }
        
        try {
            return switch (type) {
                case STRING -> config.getString(key);
                case LONG -> config.getLong(key);
                case INT -> config.getInt(key);
                case DOUBLE -> config.getDouble(key);
                case BOOLEAN -> config.getBoolean(key);
                case CONFIG -> config.getConfig(key);
                case STRING_LIST -> config.getStringList(key);
            };
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Type-safe getter for String values.
     * @throws ConfigException.Missing if the path doesn't exist
     */
    public String getString(Config config) {
        return config.getString(key);
    }
    
    /**
     * Type-safe getter for Long values.
     * @throws ConfigException.Missing if the path doesn't exist
     */
    public Long getLong(Config config) {
        return config.getLong(key);
    }
    
    /**
     * Type-safe getter for Integer values.
     * @throws ConfigException.Missing if the path doesn't exist
     */
    public Integer getInt(Config config) {
        return config.getInt(key);
    }
    
    /**
     * Type-safe getter for Double values.
     * @throws ConfigException.Missing if the path doesn't exist
     */
    public Double getDouble(Config config) {
        return config.getDouble(key);
    }
    
    /**
     * Type-safe getter for Boolean values.
     * @throws ConfigException.Missing if the path doesn't exist
     */
    public Boolean getBoolean(Config config) {
        return config.getBoolean(key);
    }
    
    /**
     * Type-safe getter for Config (nested config) values.
     * @throws ConfigException.Missing if the path doesn't exist
     */
    public Config getConfig(Config config) {
        return config.getConfig(key);
    }
    
    /**
     * Type-safe getter for String list values.
     * @throws ConfigException.Missing if the path doesn't exist
     */
    public List<String> getStringList(Config config) {
        return config.getStringList(key);
    }
    
    /**
     * Checks if the config has a value for this option.
     */
    public boolean hasValue(Config config) {
        return config.hasPath(key);
    }
    
    /**
     * Gets all config option keys as a set.
     */
    public static Set<String> getAllKeys() {
        Set<String> keys = new java.util.HashSet<>();
        for (ConfigOption option : values()) {
            keys.add(option.key);
        }
        return keys;
    }
    
    /**
     * Gets all optional (non-required) config option keys as a set.
     */
    public static Set<String> getOptionalKeys() {
        Set<String> keys = new java.util.HashSet<>();
        for (ConfigOption option : values()) {
            if (!option.required) {
                keys.add(option.key);
            }
        }
        return keys;
    }
    
    /**
     * Finds a ConfigOption by its key name.
     */
    public static java.util.Optional<ConfigOption> findByKey(String key) {
        for (ConfigOption option : values()) {
            if (option.key.equals(key)) {
                return java.util.Optional.of(option);
            }
        }
        return java.util.Optional.empty();
    }
    
    /**
     * Config value types supported by the configuration system.
     */
    public enum ConfigType {
        STRING,
        LONG,
        INT,
        DOUBLE,
        BOOLEAN,
        CONFIG,
        STRING_LIST
    }
}
