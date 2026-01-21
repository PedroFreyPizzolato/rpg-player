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
package com.jagrosh.jmusicbot.unit;

import com.jagrosh.jmusicbot.BaseConfigTest;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.audio.AudioSource;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.OnlineStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DisplayName("BotConfig Unit Tests")
class BotConfigUnitTest extends BaseConfigTest {
    
    @Mock
    private AudioTrack mockAudioTrack;
    
    @Override
    @org.junit.jupiter.api.BeforeEach
    protected void setUpBase() {
        super.setUpBase();
        MockitoAnnotations.openMocks(this);
    }
    
    @Nested
    @DisplayName("Getters Tests")
    class GettersTests {
        
        @Test
        @DisplayName("All getters return correct values after load")
        void allGettersReturnCorrectValuesAfterLoad() throws IOException {
            String configContent = """
                token = test_token_12345
                owner = 123456789
                prefix = "@mention"
                altprefix = "NONE"
                help = help
                game = DEFAULT
                status = ONLINE
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.isValid());
            assertEquals("test_token_12345", config.getToken());
            assertEquals(123456789L, config.getOwnerId());
            assertNotNull(config.getPrefix());
        }
        
        @Test
        @DisplayName("getAltPrefix() returns null for NONE")
        void getAltPrefixReturnsNullForNone() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                commands.altPrefix = "NONE"
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            // getAltPrefix() returns null when internal value is "NONE" (for API compatibility)
            assertNull(config.getAltPrefix());
        }
        
        @Test
        @DisplayName("getAltPrefix() returns value when not NONE")
        void getAltPrefixReturnsValueWhenNotNone() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                commands.altPrefix = "!!"
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertEquals("!!", config.getAltPrefix());
        }
        
        @Test
        @DisplayName("isGameNone() returns true for NONE game")
        void isGameNoneReturnsTrueForNoneGame() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                presence.game = NONE
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.isGameNone());
        }
        
        @Test
        @DisplayName("isGameNone() returns false for other games")
        void isGameNoneReturnsFalseForOtherGames() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                presence.game = Playing music
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertFalse(config.isGameNone());
        }
        
        @Test
        @DisplayName("getDBots() returns true for specific owner ID")
        void getDBotsReturnsTrueForSpecificOwnerId() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 113156185389092864
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.getDBots());
        }
        
        @Test
        @DisplayName("getDBots() returns false for other owner IDs")
        void getDBotsReturnsFalseForOtherOwnerIds() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertFalse(config.getDBots());
        }
    }
    
    @Nested
    @DisplayName("isTooLong() Tests")
    class IsTooLongTests {
        
        @Test
        @DisplayName("isTooLong() returns false when maxSeconds is 0")
        void isTooLongReturnsFalseWhenMaxSecondsIsZero() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                playback.maxTrackSeconds = 0
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            when(mockAudioTrack.getDuration()).thenReturn(3600000L); // 1 hour
            
            assertFalse(config.isTooLong(mockAudioTrack));
        }
        
        @Test
        @DisplayName("isTooLong() returns false when track is shorter than max")
        void isTooLongReturnsFalseWhenTrackIsShorter() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                playback.maxTrackSeconds = 300
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            when(mockAudioTrack.getDuration()).thenReturn(120000L); // 2 minutes
            
            assertFalse(config.isTooLong(mockAudioTrack));
        }
        
        @Test
        @DisplayName("isTooLong() returns true when track is longer than max")
        void isTooLongReturnsTrueWhenTrackIsLonger() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                playback.maxTrackSeconds = 300
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            when(mockAudioTrack.getDuration()).thenReturn(600000L); // 10 minutes
            
            assertTrue(config.isTooLong(mockAudioTrack));
        }
    }
    
    @Nested
    @DisplayName("getAliases() Tests")
    class GetAliasesTests {
        
        @Test
        @DisplayName("getAliases() returns aliases for existing command")
        void getAliasesReturnsAliasesForExistingCommand() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                commands.aliases {
                  play = [ p, playmusic ]
                  skip = [ voteskip ]
                }
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            String[] playAliases = config.getAliases("play");
            assertEquals(2, playAliases.length);
            assertTrue(java.util.Arrays.asList(playAliases).contains("p"));
            assertTrue(java.util.Arrays.asList(playAliases).contains("playmusic"));
        }
        
        @Test
        @DisplayName("getAliases() returns empty array for non-existent command")
        void getAliasesReturnsEmptyArrayForNonExistentCommand() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                commands.aliases {
                  play = [ p ]
                }
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            String[] aliases = config.getAliases("nonexistent");
            assertEquals(0, aliases.length);
        }
        
        @Test
        @DisplayName("getAliases() returns empty array when aliases config is missing")
        void getAliasesReturnsEmptyArrayWhenAliasesConfigMissing() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            String[] aliases = config.getAliases("play");
            assertEquals(0, aliases.length);
        }
    }
    
    @Nested
    @DisplayName("Audio Sources Tests")
    class AudioSourcesTests {
        
        @Test
        @DisplayName("getEnabledAudioSources() returns all sources when not specified")
        void getEnabledAudioSourcesReturnsAllWhenNotSpecified() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            Set<AudioSource> sources = config.getEnabledAudioSources();
            assertNotNull(sources);
            assertFalse(sources.isEmpty());
            // Should contain all AudioSource values
            assertEquals(AudioSource.values().length, sources.size());
        }
        
        @Test
        @DisplayName("getEnabledAudioSources() returns specified sources")
        void getEnabledAudioSourcesReturnsSpecifiedSources() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                playback.audioSources {
                  youtube = true
                  soundcloud = true
                  bandcamp = false
                }
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            Set<AudioSource> sources = config.getEnabledAudioSources();
            assertTrue(sources.contains(AudioSource.YOUTUBE));
            assertTrue(sources.contains(AudioSource.SOUNDCLOUD));
            assertFalse(sources.contains(AudioSource.BANDCAMP));
        }
        
        @Test
        @DisplayName("isAudioSourceEnabled() returns true for enabled source")
        void isAudioSourceEnabledReturnsTrueForEnabledSource() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                playback.audioSources {
                  youtube = true
                  soundcloud = false
                }
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.isAudioSourceEnabled(AudioSource.YOUTUBE));
            assertFalse(config.isAudioSourceEnabled(AudioSource.SOUNDCLOUD));
        }
        
        @Test
        @DisplayName("isAudioSourceEnabled() filters invalid source names")
        void isAudioSourceEnabledFiltersInvalidSourceNames() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                playback.audioSources {
                  youtube = true
                  soundcloud = true
                  invalid_source = true
                }
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            // After config update, missing audio source keys are added with template defaults (true)
            // So all valid sources that exist in the updated config file are considered "explicitly set"
            // Invalid source names are ignored (they don't match any AudioSource enum value)
            // Since only youtube and soundcloud were explicitly set to true in the original config,
            // and all other sources are added with defaults (true) during config update,
            // all valid sources end up enabled
            Set<AudioSource> sources = config.getEnabledAudioSources();
            assertTrue(sources.contains(AudioSource.YOUTUBE));
            assertTrue(sources.contains(AudioSource.SOUNDCLOUD));
            // After config update, all missing keys are added, so all sources are enabled
            assertEquals(AudioSource.values().length, sources.size(),
                "After config update, all missing keys are added with defaults, so all sources are enabled");
        }
    }
    
    @Nested
    @DisplayName("Status and Game Tests")
    class StatusAndGameTests {
        
        @Test
        @DisplayName("getStatus() returns correct OnlineStatus")
        void getStatusReturnsCorrectOnlineStatus() throws IOException {
            // Use legacy format - will be migrated to nested
            String configContent = """
                token = test_token
                owner = 123456789
                status = IDLE
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.isValid());
            assertEquals(OnlineStatus.IDLE, config.getStatus());
        }
        
        @Test
        @DisplayName("getGame() returns correct Activity")
        void getGameReturnsCorrectActivity() throws IOException {
            // Use legacy format - will be migrated to nested
            String configContent = """
                token = test_token
                owner = 123456789
                game = Playing music
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.isValid());
            assertNotNull(config.getGame());
            assertTrue(config.getGame().getName().contains("music"));
        }
    }
}
