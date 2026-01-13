/*
 * Copyright 2018 John Grosh (jagrosh)
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

import static com.jagrosh.jmusicbot.config.ConfigOption.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;

import com.jagrosh.jmusicbot.audio.AudioSource;
import com.jagrosh.jmusicbot.config.ConfigFileManager;
import com.jagrosh.jmusicbot.config.ConfigLoader;
import com.jagrosh.jmusicbot.config.ConfigUpdater;
import com.jagrosh.jmusicbot.config.ConfigValidator;
import com.jagrosh.jmusicbot.config.ConfigValidator.ValidationResult;
import com.jagrosh.jmusicbot.entities.Prompt;
import com.jagrosh.jmusicbot.entities.UserInteraction;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import com.jagrosh.jmusicbot.utils.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * 
 * @author John Grosh (jagrosh)
 */
public class BotConfig {
    private final static Logger LOGGER = LoggerFactory.getLogger(BotConfig.class);

    private final UserInteraction userInteraction;

    private Path path = null;
    private String token, prefix, altprefix, helpWord, playlistsFolder, logLevel,
            successEmoji, warningEmoji, errorEmoji, loadingEmoji, searchingEmoji,
            evalEngine;
    private boolean stayInChannel, songInGame, npImages, updatealerts, useEval, dbots, useYouTubeOauth;
    private long owner, maxSeconds, aloneTimeUntilStop;
    private int maxYTPlaylistPages;
    private double skipratio;
    private OnlineStatus status;
    private Activity game;
    private Config aliases, transforms;
    private Set<AudioSource> enabledAudioSources;

    private boolean valid = false;

    public BotConfig(UserInteraction userInteraction) {
        this.userInteraction = userInteraction;
    }

    public void load() {
        valid = false;

        // read config from file
        try {
            // get the path to the config, default config.txt
            path = ConfigFileManager.getConfigPath();

            // Load user config and merged config
            Config userConfig = ConfigLoader.loadUserConfig(path);
            Config config = ConfigLoader.loadMergedConfig(path);

            // Load all config values
            loadConfigValues(config, userConfig);

            // Validate required fields
            ValidationResult tokenResult = ConfigValidator.validateToken(token, userInteraction, path);
            if (!tokenResult.isValid()) {
                return;
            }
            token = tokenResult.getValue();
            boolean needsWrite = tokenResult.needsWrite();

            ValidationResult ownerResult = ConfigValidator.validateOwner(owner, userInteraction, path);
            if (!ownerResult.isValid()) {
                return;
            }
            owner = ownerResult.getValue();
            needsWrite = needsWrite || ownerResult.needsWrite();

            // Write config file if needed
            if (needsWrite) {
                writeToFile();
            }
            
            // Check for missing config values and append them
            ConfigUpdater.updateConfigWithMissingValues(path, userConfig);

            // if we get through the whole config, it's good to go
            valid = true;
        } catch (ConfigException ex) {
            userInteraction.alert(Prompt.Level.ERROR, "Config",
                    ex + ": " + ex.getMessage() + "\n\nConfig Location: " + path.toAbsolutePath().toString());
        }
    }
    
    /**
     * Loads all configuration values from the merged config.
     */
    private void loadConfigValues(Config config, Config userConfig) {
        // set values using ConfigOption enum for type safety and standardization
        token = TOKEN.getString(config);
        prefix = PREFIX.getString(config);
        altprefix = ALTPREFIX.getString(config);
        helpWord = HELP_WORD.getString(config);
        owner = OWNER.getLong(config);
        successEmoji = SUCCESS_EMOJI.getString(config);
        warningEmoji = WARNING_EMOJI.getString(config);
        errorEmoji = ERROR_EMOJI.getString(config);
        loadingEmoji = LOADING_EMOJI.getString(config);
        searchingEmoji = SEARCHING_EMOJI.getString(config);
        game = OtherUtil.parseGame(GAME.getString(config));
        status = OtherUtil.parseStatus(STATUS.getString(config));
        stayInChannel = STAY_IN_CHANNEL.getBoolean(config);
        songInGame = SONG_IN_GAME.getBoolean(config);
        npImages = NP_IMAGES.getBoolean(config);
        updatealerts = UPDATE_ALERTS.getBoolean(config);
        logLevel = LOG_LEVEL.getString(config);
        useEval = USE_EVAL.getBoolean(config);
        evalEngine = EVAL_ENGINE.getString(config);
        maxSeconds = MAX_SECONDS.getLong(config);
        maxYTPlaylistPages = MAX_YT_PLAYLIST_PAGES.getInt(config);
        useYouTubeOauth = USE_YOUTUBE_OAUTH.getBoolean(config);
        aloneTimeUntilStop = ALONE_TIME_UNTIL_STOP.getLong(config);
        playlistsFolder = PLAYLISTS_FOLDER.getString(config);
        aliases = ALIASES.getConfig(config);
        transforms = TRANSFORMS.getConfig(config);
        
        // Handle audiosources: only use if explicitly set in user's config, otherwise default to null (all enabled)
        loadAudioSources(userConfig);
        
        skipratio = SKIP_RATIO.getDouble(config);
        dbots = owner == 113156185389092864L;
    }
    
    /**
     * Loads audio sources configuration.
     */
    private void loadAudioSources(Config userConfig) {
        if (AUDIO_SOURCES.hasValue(userConfig)) {
            // Key exists in user's config, read the values
            List<String> sourceNames = AUDIO_SOURCES.getStringList(userConfig);
            if (sourceNames != null) {
                enabledAudioSources = sourceNames.stream()
                        .map(AudioSource::fromConfigName)
                        .filter(java.util.Optional::isPresent)
                        .map(java.util.Optional::get)
                        .collect(Collectors.toSet());
            } else {
                enabledAudioSources = Set.of(AudioSource.values());
            }
        } else {
            // Key not found in user's config, use default behavior (all sources enabled)
            enabledAudioSources = Set.of(AudioSource.values());
            LOGGER.info("Audio sources config not found in config file, defaulting to all sources enabled");
        }
        
        LOGGER.info("Setup {} valid audio sources: {}", 
                    enabledAudioSources.size(), 
                    enabledAudioSources.stream()
                            .map(AudioSource::getConfigName)
                            .collect(Collectors.toList()));
    }

    private void writeToFile() {
        try {
            String content = ConfigFileManager.loadDefaultConfig()
                    .replace("BOT_TOKEN_HERE", token)
                    .replace("0 // OWNER ID", Long.toString(owner))
                    .trim();
            ConfigFileManager.writeConfigFile(path, content);
        } catch (Exception ex) {
            userInteraction.alert(Prompt.Level.WARNING, "Config", "Failed to write new config options to config.txt: " + ex
                    + "\nPlease make sure that the files are not on your desktop or some other restricted area.\n\nConfig Location: "
                    + path.toAbsolutePath().toString());
        }
    }

    public static void writeDefaultConfig() {
        Prompt prompt = new Prompt(null, null, true, true);
        prompt.alert(Prompt.Level.INFO, "JMusicBot Config", "Generating default config file");
        Path path = ConfigFileManager.getConfigPath();
        try {
            prompt.alert(Prompt.Level.INFO, "JMusicBot Config",
                    "Writing default config file to " + path.toAbsolutePath().toString());
            ConfigFileManager.writeConfigFile(path, ConfigFileManager.loadDefaultConfig());
        } catch (Exception ex) {
            prompt.alert(Prompt.Level.ERROR, "JMusicBot Config",
                    "An error occurred writing the default config file: " + ex.getMessage());
        }
    }

    public boolean isValid() {
        return valid;
    }

    public String getConfigLocation() {
        return path.toFile().getAbsolutePath();
    }

    public String getPrefix() {
        return prefix;
    }

    public String getAltPrefix() {
        return "NONE".equalsIgnoreCase(altprefix) ? null : altprefix;
    }

    public String getToken() {
        return token;
    }

    public double getSkipRatio() {
        return skipratio;
    }

    public long getOwnerId() {
        return owner;
    }

    public String getSuccess() {
        return successEmoji;
    }

    public String getWarning() {
        return warningEmoji;
    }

    public String getError() {
        return errorEmoji;
    }

    public String getLoading() {
        return loadingEmoji;
    }

    public String getSearching() {
        return searchingEmoji;
    }

    public Activity getGame() {
        return game;
    }

    public boolean isGameNone() {
        return game != null && game.getName().equalsIgnoreCase("none");
    }

    public OnlineStatus getStatus() {
        return status;
    }

    public String getHelp() {
        return helpWord;
    }

    public boolean getStay() {
        return stayInChannel;
    }

    public boolean getSongInStatus() {
        return songInGame;
    }

    public String getPlaylistsFolder() {
        return playlistsFolder;
    }

    public boolean getDBots() {
        return dbots;
    }

    public boolean useUpdateAlerts() {
        return updatealerts;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public boolean useEval() {
        return useEval;
    }

    public String getEvalEngine() {
        return evalEngine;
    }

    public boolean useNPImages() {
        return npImages;
    }

    public long getMaxSeconds() {
        return maxSeconds;
    }

    public int getMaxYTPlaylistPages() {
        return maxYTPlaylistPages;
    }

    public boolean useYouTubeOauth() {
        return useYouTubeOauth;
    }

    public String getMaxTime() {
        return TimeUtil.formatTime(maxSeconds * 1000);
    }

    public long getAloneTimeUntilStop() {
        return aloneTimeUntilStop;
    }

    public boolean isTooLong(AudioTrack track) {
        if (maxSeconds <= 0)
            return false;
        return Math.round(track.getDuration() / 1000.0) > maxSeconds;
    }

    public String[] getAliases(String command) {
        try {
            return aliases.getStringList(command).toArray(new String[0]);
        } catch (NullPointerException | ConfigException.Missing e) {
            return new String[0];
        }
    }

    public Config getTransforms() {
        return transforms;
    }

    public Set<AudioSource> getEnabledAudioSources() {
        return enabledAudioSources;
    }

    public boolean isAudioSourceEnabled(AudioSource source) {
        // If the set is empty, no sources are enabled
        if (enabledAudioSources.isEmpty())
            return false;
        return enabledAudioSources.contains(source);
    }
}
