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
package com.jagrosh.jmusicbot.testutil.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigException;
import com.typesafe.config.parser.ConfigDocument;
import com.typesafe.config.parser.ConfigDocumentFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder for creating version 1 config objects with nested structure.
 * 
 * <p>This builder provides a fluent API for creating v1 configs with the new nested format.
 * Keys are organized into sections like discord, commands, playback, etc.
 * 
 * <p>Example usage:
 * <pre>{@code
 * Config v1 = V1ConfigBuilder.create()
 *     .withDiscordToken("test_token")
 *     .withDiscordOwner(123456789L)
 *     .withCommandsPrefix("!!")
 *     .withPlaybackAudioSources(Map.of("youtube", true, "soundcloud", true))
 *     .build();
 * }</pre>
 * 
 * <p>This builder is based on the reference.conf file structure.
 */
public class V1ConfigBuilder implements ConfigBuilder {
    private final Map<String, Object> config = new HashMap<>();
    
    /**
     * All available audio source names (matching reference.conf).
     */
    public static final String[] ALL_AUDIO_SOURCES = {
        "youtube", "soundcloud", "bandcamp", "vimeo", "twitch", 
        "beam", "getyarn", "nico", "http", "local"
    };
    
    /**
     * Sets the config version in meta section.
     */
    public V1ConfigBuilder withMetaVersion(int version) {
        Map<String, Object> meta = getOrCreateSection("meta");
        meta.put("configVersion", version);
        return this;
    }
    
    /**
     * Sets the Discord bot token.
     */
    public V1ConfigBuilder withDiscordToken(String token) {
        Map<String, Object> discord = getOrCreateSection("discord");
        discord.put("token", token);
        return this;
    }
    
    /**
     * Sets the Discord owner ID.
     */
    public V1ConfigBuilder withDiscordOwner(Long owner) {
        Map<String, Object> discord = getOrCreateSection("discord");
        discord.put("owner", owner);
        return this;
    }
    
    /**
     * Sets the command prefix.
     */
    public V1ConfigBuilder withCommandsPrefix(String prefix) {
        Map<String, Object> commands = getOrCreateSection("commands");
        commands.put("prefix", prefix);
        return this;
    }
    
    /**
     * Sets the alternate prefix (use "NONE" to disable).
     */
    public V1ConfigBuilder withCommandsAltPrefix(String altPrefix) {
        Map<String, Object> commands = getOrCreateSection("commands");
        commands.put("altPrefix", altPrefix);
        return this;
    }
    
    /**
     * Sets the help command name.
     */
    public V1ConfigBuilder withCommandsHelp(String help) {
        Map<String, Object> commands = getOrCreateSection("commands");
        commands.put("help", help);
        return this;
    }
    
    /**
     * Sets the command aliases.
     */
    public V1ConfigBuilder withCommandsAliases(Map<String, List<String>> aliases) {
        Map<String, Object> commands = getOrCreateSection("commands");
        commands.put("aliases", aliases);
        return this;
    }
    
    /**
     * Sets the presence game.
     */
    public V1ConfigBuilder withPresenceGame(String game) {
        Map<String, Object> presence = getOrCreateSection("presence");
        presence.put("game", game);
        return this;
    }
    
    /**
     * Sets the presence status.
     */
    public V1ConfigBuilder withPresenceStatus(String status) {
        Map<String, Object> presence = getOrCreateSection("presence");
        presence.put("status", status);
        return this;
    }
    
    /**
     * Sets whether to show song in status.
     */
    public V1ConfigBuilder withPresenceSongInStatus(boolean songInStatus) {
        Map<String, Object> presence = getOrCreateSection("presence");
        presence.put("songInStatus", songInStatus);
        return this;
    }
    
    /**
     * Sets the success emoji.
     */
    public V1ConfigBuilder withUIEmojisSuccess(String success) {
        Map<String, Object> ui = getOrCreateSection("ui");
        Map<String, Object> emojis = getOrCreateNestedSection(ui, "emojis");
        emojis.put("success", success);
        return this;
    }
    
    /**
     * Sets the warning emoji.
     */
    public V1ConfigBuilder withUIEmojisWarning(String warning) {
        Map<String, Object> ui = getOrCreateSection("ui");
        Map<String, Object> emojis = getOrCreateNestedSection(ui, "emojis");
        emojis.put("warning", warning);
        return this;
    }
    
    /**
     * Sets the error emoji.
     */
    public V1ConfigBuilder withUIEmojisError(String error) {
        Map<String, Object> ui = getOrCreateSection("ui");
        Map<String, Object> emojis = getOrCreateNestedSection(ui, "emojis");
        emojis.put("error", error);
        return this;
    }
    
    /**
     * Sets the loading emoji.
     */
    public V1ConfigBuilder withUIEmojisLoading(String loading) {
        Map<String, Object> ui = getOrCreateSection("ui");
        Map<String, Object> emojis = getOrCreateNestedSection(ui, "emojis");
        emojis.put("loading", loading);
        return this;
    }
    
    /**
     * Sets the searching emoji.
     */
    public V1ConfigBuilder withUIEmojisSearching(String searching) {
        Map<String, Object> ui = getOrCreateSection("ui");
        Map<String, Object> emojis = getOrCreateNestedSection(ui, "emojis");
        emojis.put("searching", searching);
        return this;
    }
    
    /**
     * Sets whether to show now playing images.
     */
    public V1ConfigBuilder withNowPlayingImages(boolean images) {
        Map<String, Object> nowPlaying = getOrCreateSection("nowPlaying");
        nowPlaying.put("images", images);
        return this;
    }
    
    /**
     * Sets whether to stay in channel.
     */
    public V1ConfigBuilder withVoiceStayInChannel(boolean stayInChannel) {
        Map<String, Object> voice = getOrCreateSection("voice");
        voice.put("stayInChannel", stayInChannel);
        return this;
    }
    
    /**
     * Sets the alone time until stop in seconds.
     */
    public V1ConfigBuilder withVoiceAloneTimeUntilStopSeconds(Long seconds) {
        Map<String, Object> voice = getOrCreateSection("voice");
        voice.put("aloneTimeUntilStopSeconds", seconds);
        return this;
    }
    
    /**
     * Sets the maximum track time in seconds.
     */
    public V1ConfigBuilder withPlaybackMaxTrackSeconds(Long seconds) {
        Map<String, Object> playback = getOrCreateSection("playback");
        playback.put("maxTrackSeconds", seconds);
        return this;
    }
    
    /**
     * Sets the maximum YouTube playlist pages.
     */
    public V1ConfigBuilder withPlaybackMaxYouTubePlaylistPages(Integer pages) {
        Map<String, Object> playback = getOrCreateSection("playback");
        playback.put("maxYouTubePlaylistPages", pages);
        return this;
    }
    
    /**
     * Sets the skip ratio.
     */
    public V1ConfigBuilder withPlaybackSkipRatio(Double ratio) {
        Map<String, Object> playback = getOrCreateSection("playback");
        playback.put("skipRatio", ratio);
        return this;
    }
    
    /**
     * Sets whether to use YouTube OAuth.
     */
    public V1ConfigBuilder withPlaybackYouTubeUseOAuth(boolean useOAuth) {
        Map<String, Object> playback = getOrCreateSection("playback");
        Map<String, Object> youtube = getOrCreateNestedSection(playback, "youtube");
        youtube.put("useOAuth", useOAuth);
        return this;
    }
    
    /**
     * Sets the audio sources as a map of source names to boolean values.
     * 
     * <p>Available sources (from reference.conf):
     * youtube, soundcloud, bandcamp, vimeo, twitch, beam, getyarn, nico, http, local
     */
    public V1ConfigBuilder withPlaybackAudioSources(Map<String, Boolean> audioSources) {
        Map<String, Object> playback = getOrCreateSection("playback");
        playback.put("audioSources", audioSources);
        return this;
    }
    
    /**
     * Sets all audio sources enabled (matching reference.conf defaults).
     */
    public V1ConfigBuilder withAllPlaybackAudioSources() {
        Map<String, Boolean> allSources = new HashMap<>();
        for (String source : ALL_AUDIO_SOURCES) {
            allSources.put(source, true);
        }
        return withPlaybackAudioSources(allSources);
    }
    
    /**
     * Sets the transforms configuration.
     */
    public V1ConfigBuilder withPlaybackTransforms(Map<String, Object> transforms) {
        Map<String, Object> playback = getOrCreateSection("playback");
        playback.put("transforms", transforms);
        return this;
    }
    
    /**
     * Sets the playlists folder path.
     */
    public V1ConfigBuilder withPathsPlaylistsFolder(String folder) {
        Map<String, Object> paths = getOrCreateSection("paths");
        paths.put("playlistsFolder", folder);
        return this;
    }
    
    /**
     * Sets whether to show update alerts.
     */
    public V1ConfigBuilder withUpdatesAlerts(boolean alerts) {
        Map<String, Object> updates = getOrCreateSection("updates");
        updates.put("alerts", alerts);
        return this;
    }
    
    /**
     * Sets the logging level.
     */
    public V1ConfigBuilder withLoggingLevel(String level) {
        Map<String, Object> logging = getOrCreateSection("logging");
        logging.put("level", level);
        return this;
    }
    
    /**
     * Sets whether to enable eval.
     */
    public V1ConfigBuilder withDangerousEval(boolean eval) {
        Map<String, Object> dangerous = getOrCreateSection("dangerous");
        dangerous.put("eval", eval);
        return this;
    }
    
    /**
     * Sets the eval engine.
     */
    public V1ConfigBuilder withDangerousEvalEngine(String engine) {
        Map<String, Object> dangerous = getOrCreateSection("dangerous");
        dangerous.put("evalEngine", engine);
        return this;
    }
    
    /**
     * Sets a custom nested key-value pair.
     */
    public V1ConfigBuilder withCustom(String section, String key, Object value) {
        Map<String, Object> sectionMap = getOrCreateSection(section);
        sectionMap.put(key, value);
        return this;
    }
    
    /**
     * Builds the ConfigDocument from the configured values.
     * This is the primary method, matching how application code works.
     * 
     * @return the built ConfigDocument
     */
    @Override
    public ConfigDocument buildDocument() {
        try {
            // First try to build as ConfigDocument from the string representation
            String hoconString = buildAsStringFromConfig();
            return ConfigDocumentFactory.parseString(hoconString);
        } catch (ConfigException e) {
            // If ConfigDocument parsing fails, we'll fall back to Config in build()
            // This matches the application pattern where ConfigDocument is preferred
            // but Config is used as fallback for error cases
            throw new IllegalStateException("Failed to build ConfigDocument, use build() as fallback", e);
        }
    }
    
    /**
     * Builds the Config object from the configured values.
     * This is a fallback method for cases where ConfigDocument parsing fails
     * or when Config is needed for migration/validation.
     * 
     * @return the built Config object
     */
    @Override
    public Config build() {
        return ConfigFactory.parseMap(config);
    }
    
    /**
     * Builds the config as a HOCON string.
     * Uses ConfigDocument when possible to preserve formatting.
     * 
     * @return the config as a HOCON-formatted string
     */
    @Override
    public String buildAsString() {
        try {
            return buildDocument().render();
        } catch (Exception e) {
            // Fallback to Config rendering if ConfigDocument fails
            return buildAsStringFromConfig();
        }
    }
    
    /**
     * Internal helper to build string from Config (fallback method).
     */
    private String buildAsStringFromConfig() {
        return build().root().render();
    }
    
    /**
     * Creates a new builder instance.
     */
    public static V1ConfigBuilder create() {
        return new V1ConfigBuilder();
    }
    
    /**
     * Creates a minimal valid v1 config with only required fields.
     */
    public static Config minimal() {
        return new V1ConfigBuilder()
            .withMetaVersion(1)
            .withDiscordToken("test_token_12345")
            .withDiscordOwner(123456789L)
            .build();
    }
    
    /**
     * Creates a v1 config matching the reference.conf defaults.
     * This includes all default values from the reference file.
     */
    public static Config withReferenceDefaults() {
        Map<String, List<String>> defaultAliases = new HashMap<>();
        defaultAliases.put("settings", List.of("status"));
        defaultAliases.put("lyrics", List.of());
        defaultAliases.put("nowplaying", List.of("np", "current"));
        defaultAliases.put("play", List.of());
        defaultAliases.put("playlists", List.of("pls"));
        defaultAliases.put("queue", List.of("list"));
        defaultAliases.put("remove", List.of("delete"));
        defaultAliases.put("scsearch", List.of());
        defaultAliases.put("search", List.of("ytsearch"));
        defaultAliases.put("shuffle", List.of());
        defaultAliases.put("skip", List.of("voteskip"));
        defaultAliases.put("prefix", List.of("setprefix"));
        defaultAliases.put("setdj", List.of());
        defaultAliases.put("setskip", List.of("setskippercent", "skippercent", "setskipratio"));
        defaultAliases.put("settc", List.of());
        defaultAliases.put("setvc", List.of());
        defaultAliases.put("forceremove", List.of("forcedelete", "modremove", "moddelete", "modelete"));
        defaultAliases.put("forceskip", List.of("modskip"));
        defaultAliases.put("movetrack", List.of("move"));
        defaultAliases.put("pause", List.of());
        defaultAliases.put("playnext", List.of());
        defaultAliases.put("queuetype", List.of());
        defaultAliases.put("repeat", List.of());
        defaultAliases.put("skipto", List.of("jumpto"));
        defaultAliases.put("stop", List.of("leave"));
        defaultAliases.put("volume", List.of("vol"));
        
        Map<String, Boolean> allAudioSources = new HashMap<>();
        for (String source : ALL_AUDIO_SOURCES) {
            allAudioSources.put(source, true);
        }
        
        return new V1ConfigBuilder()
            .withMetaVersion(1)
            .withDiscordToken("BOT_TOKEN_HERE")
            .withDiscordOwner(0L)
            .withCommandsPrefix("@mention")
            .withCommandsAltPrefix("NONE")
            .withCommandsHelp("help")
            .withCommandsAliases(defaultAliases)
            .withPresenceGame("DEFAULT")
            .withPresenceStatus("ONLINE")
            .withPresenceSongInStatus(false)
            .withUIEmojisSuccess("🎶")
            .withUIEmojisWarning("💡")
            .withUIEmojisError("🚫")
            .withUIEmojisLoading("⌚")
            .withUIEmojisSearching("🔎")
            .withNowPlayingImages(false)
            .withVoiceStayInChannel(false)
            .withVoiceAloneTimeUntilStopSeconds(0L)
            .withPlaybackMaxTrackSeconds(0L)
            .withPlaybackMaxYouTubePlaylistPages(10)
            .withPlaybackSkipRatio(0.55)
            .withPlaybackYouTubeUseOAuth(false)
            .withPlaybackAudioSources(allAudioSources)
            .withPlaybackTransforms(new HashMap<>())
            .withPathsPlaylistsFolder("Playlists")
            .withUpdatesAlerts(true)
            .withLoggingLevel("info")
            .withDangerousEval(false)
            .withDangerousEvalEngine("Nashorn")
            .build();
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> getOrCreateSection(String section) {
        return (Map<String, Object>) config.computeIfAbsent(section, k -> new HashMap<>());
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> getOrCreateNestedSection(Map<String, Object> parent, String key) {
        return (Map<String, Object>) parent.computeIfAbsent(key, k -> new HashMap<>());
    }
}
