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
 * Builder for creating legacy (version 0) config objects with flat key structure.
 * 
 * <p>This builder provides a fluent API for creating legacy configs used in migration tests.
 * All keys are flat (not nested) as in the original config format.
 * 
 * <p>Example usage:
 * <pre>{@code
 * Config legacy = LegacyConfigBuilder.create()
 *     .withToken("test_token")
 *     .withOwner(123456789L)
 *     .withPrefix("!!")
 *     .withAudioSources("youtube", "soundcloud")
 *     .build();
 * }</pre>
 * 
 * <p>This builder is based on the reference-legacy.conf file structure.
 */
public class LegacyConfigBuilder implements ConfigBuilder {
    private final Map<String, Object> config = new HashMap<>();
    
    /**
     * All available audio source names (matching reference-legacy.conf).
     */
    public static final String[] ALL_AUDIO_SOURCES = {
        "youtube", "soundcloud", "bandcamp", "vimeo", "twitch", 
        "beam", "getyarn", "nico", "http", "local"
    };
    
    /**
     * Sets the bot token.
     */
    public LegacyConfigBuilder withToken(String token) {
        config.put("token", token);
        return this;
    }
    
    /**
     * Sets the owner ID.
     */
    public LegacyConfigBuilder withOwner(Long owner) {
        config.put("owner", owner);
        return this;
    }
    
    /**
     * Sets the command prefix.
     */
    public LegacyConfigBuilder withPrefix(String prefix) {
        config.put("prefix", prefix);
        return this;
    }
    
    /**
     * Sets the alternate prefix (use "NONE" to disable).
     */
    public LegacyConfigBuilder withAltPrefix(String altPrefix) {
        config.put("altprefix", altPrefix);
        return this;
    }
    
    /**
     * Sets the help command name.
     */
    public LegacyConfigBuilder withHelp(String help) {
        config.put("help", help);
        return this;
    }
    
    /**
     * Sets the game status.
     */
    public LegacyConfigBuilder withGame(String game) {
        config.put("game", game);
        return this;
    }
    
    /**
     * Sets the bot status (ONLINE, IDLE, DND, etc.).
     */
    public LegacyConfigBuilder withStatus(String status) {
        config.put("status", status);
        return this;
    }
    
    /**
     * Sets whether to show song in status.
     */
    public LegacyConfigBuilder withSongInStatus(boolean songInStatus) {
        config.put("songinstatus", songInStatus);
        return this;
    }
    
    /**
     * Sets the success emoji.
     */
    public LegacyConfigBuilder withSuccess(String success) {
        config.put("success", success);
        return this;
    }
    
    /**
     * Sets the warning emoji.
     */
    public LegacyConfigBuilder withWarning(String warning) {
        config.put("warning", warning);
        return this;
    }
    
    /**
     * Sets the error emoji.
     */
    public LegacyConfigBuilder withError(String error) {
        config.put("error", error);
        return this;
    }
    
    /**
     * Sets the loading emoji.
     */
    public LegacyConfigBuilder withLoading(String loading) {
        config.put("loading", loading);
        return this;
    }
    
    /**
     * Sets the searching emoji.
     */
    public LegacyConfigBuilder withSearching(String searching) {
        config.put("searching", searching);
        return this;
    }
    
    /**
     * Sets whether to stay in channel.
     */
    public LegacyConfigBuilder withStayInChannel(boolean stayInChannel) {
        config.put("stayinchannel", stayInChannel);
        return this;
    }
    
    /**
     * Sets the maximum track time in seconds.
     */
    public LegacyConfigBuilder withMaxTime(Long maxTime) {
        config.put("maxtime", maxTime);
        return this;
    }
    
    /**
     * Sets the skip ratio.
     */
    public LegacyConfigBuilder withSkipRatio(Double skipRatio) {
        config.put("skipratio", skipRatio);
        return this;
    }
    
    /**
     * Sets the log level.
     */
    public LegacyConfigBuilder withLogLevel(String logLevel) {
        config.put("loglevel", logLevel);
        return this;
    }
    
    /**
     * Sets whether to enable eval.
     */
    public LegacyConfigBuilder withEval(boolean eval) {
        config.put("eval", eval);
        return this;
    }
    
    /**
     * Sets the eval engine.
     */
    public LegacyConfigBuilder withEvalEngine(String evalEngine) {
        config.put("evalengine", evalEngine);
        return this;
    }
    
    /**
     * Sets whether to use YouTube OAuth.
     */
    public LegacyConfigBuilder withUseYouTubeOAuth(boolean useYouTubeOAuth) {
        config.put("useyoutubeoauth", useYouTubeOAuth);
        return this;
    }
    
    /**
     * Sets the maximum YouTube playlist pages.
     */
    public LegacyConfigBuilder withMaxYTPlaylistPages(Integer maxYTPlaylistPages) {
        config.put("maxytplaylistpages", maxYTPlaylistPages);
        return this;
    }
    
    /**
     * Sets the alone time until stop in seconds.
     */
    public LegacyConfigBuilder withAloneTimeUntilStop(Long aloneTimeUntilStop) {
        config.put("alonetimeuntilstop", aloneTimeUntilStop);
        return this;
    }
    
    /**
     * Sets the playlists folder path.
     */
    public LegacyConfigBuilder withPlaylistsFolder(String playlistsFolder) {
        config.put("playlistsfolder", playlistsFolder);
        return this;
    }
    
    /**
     * Sets whether to show update alerts.
     */
    public LegacyConfigBuilder withUpdateAlerts(boolean updateAlerts) {
        config.put("updatealerts", updateAlerts);
        return this;
    }
    
    /**
     * Sets whether to show now playing images.
     */
    public LegacyConfigBuilder withNPImages(boolean npImages) {
        config.put("npimages", npImages);
        return this;
    }
    
    /**
     * Sets the audio sources as a list of strings.
     * 
     * <p>Available sources (from reference-legacy.conf):
     * youtube, soundcloud, bandcamp, vimeo, twitch, beam, getyarn, nico, http, local
     * 
     * @param sources audio source names (e.g., "youtube", "soundcloud", "local", "bandcamp")
     */
    public LegacyConfigBuilder withAudioSources(String... sources) {
        config.put("audiosources", List.of(sources));
        return this;
    }
    
    /**
     * Sets all audio sources enabled (matching reference-legacy.conf defaults).
     */
    public LegacyConfigBuilder withAllAudioSources() {
        return withAudioSources(ALL_AUDIO_SOURCES);
    }
    
    /**
     * Sets the audio sources as a list.
     */
    public LegacyConfigBuilder withAudioSources(List<String> sources) {
        config.put("audiosources", sources);
        return this;
    }
    
    /**
     * Sets the command aliases.
     * 
     * @param aliases map of command names to lists of aliases
     */
    public LegacyConfigBuilder withAliases(Map<String, List<String>> aliases) {
        config.put("aliases", aliases);
        return this;
    }
    
    /**
     * Sets the transforms configuration.
     */
    public LegacyConfigBuilder withTransforms(Map<String, Object> transforms) {
        config.put("transforms", transforms);
        return this;
    }
    
    /**
     * Sets a custom key-value pair.
     */
    public LegacyConfigBuilder withCustom(String key, Object value) {
        config.put(key, value);
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
    public static LegacyConfigBuilder create() {
        return new LegacyConfigBuilder();
    }
    
    /**
     * Creates a minimal valid legacy config with only required fields.
     */
    public static Config minimal() {
        return new LegacyConfigBuilder()
            .withToken("test_token_12345")
            .withOwner(123456789L)
            .build();
    }
    
    /**
     * Creates a legacy config matching the reference-legacy.conf defaults.
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
        
        return new LegacyConfigBuilder()
            .withToken("BOT_TOKEN_HERE")
            .withOwner(0L)
            .withPrefix("@mention")
            .withAltPrefix("NONE")
            .withHelp("help")
            .withGame("DEFAULT")
            .withStatus("ONLINE")
            .withSongInStatus(false)
            .withSuccess("🎶")
            .withWarning("💡")
            .withError("🚫")
            .withLoading("⌚")
            .withSearching("🔎")
            .withNPImages(false)
            .withStayInChannel(false)
            .withMaxTime(0L)
            .withMaxYTPlaylistPages(10)
            .withUseYouTubeOAuth(false)
            .withSkipRatio(0.55)
            .withAloneTimeUntilStop(0L)
            .withPlaylistsFolder("Playlists")
            .withUpdateAlerts(true)
            .withLogLevel("info")
            .withEval(false)
            .withEvalEngine("Nashorn")
            .withAllAudioSources()
            .withAliases(defaultAliases)
            .withTransforms(new HashMap<>())
            .build();
    }
}
