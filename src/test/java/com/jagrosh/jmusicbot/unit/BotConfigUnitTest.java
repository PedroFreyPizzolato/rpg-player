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
import com.jagrosh.jmusicbot.MockUserInteraction;
import com.jagrosh.jmusicbot.TestConfigFactory;
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
            Path configFile = TestConfigFactory.createFullTempConfigFile();
            System.setProperty("config.file", configFile.toString());
            
            BotConfig config = new BotConfig(mockUserInteraction);
            mockUserInteraction.addPromptResponse("test_token");
            mockUserInteraction.addPromptResponse("123456789");
            config.load();
            
            assertTrue(config.isValid());
            assertEquals("test_token", config.getToken());
            assertEquals(123456789L, config.getOwnerId());
            assertNotNull(config.getPrefix());
        }
        
        @Test
        @DisplayName("getAltPrefix() returns null for NONE")
        void getAltPrefixReturnsNullForNone() throws IOException {
            String configContent = """
                token = test_token
                owner = 123456789
                altprefix = NONE
                """;
            Path configFile = createTempConfigFile(configContent);
            System.setProperty("config.file", configFile.toString());
            
            BotConfig config = new BotConfig(mockUserInteraction);
            mockUserInteraction.addPromptResponse("test_token");
            mockUserInteraction.addPromptResponse("123456789");
            config.load();
            
            assertNull(config.getAltPrefix());
        }
        
        @Test
        @DisplayName("getAltPrefix() returns value when not NONE")
        void getAltPrefixReturnsValueWhenNotNone() throws IOException {
            String configContent = """
                token = test_token
                owner = 123456789
                altprefix = "!!"
                """;
            Path configFile = createTempConfigFile(configContent);
            System.setProperty("config.file", configFile.toString());
            
            BotConfig config = new BotConfig(mockUserInteraction);
            mockUserInteraction.addPromptResponse("test_token");
            mockUserInteraction.addPromptResponse("123456789");
            config.load();
            
            assertEquals("!!", config.getAltPrefix());
        }
        
        @Test
        @DisplayName("isGameNone() returns true for NONE game")
        void isGameNoneReturnsTrueForNoneGame() throws IOException {
            String configContent = """
                token = test_token
                owner = 123456789
                game = NONE
                """;
            Path configFile = createTempConfigFile(configContent);
            System.setProperty("config.file", configFile.toString());
            
            BotConfig config = new BotConfig(mockUserInteraction);
            mockUserInteraction.addPromptResponse("test_token");
            mockUserInteraction.addPromptResponse("123456789");
            config.load();
            
            assertTrue(config.isGameNone());
        }
        
        @Test
        @DisplayName("isGameNone() returns false for other games")
        void isGameNoneReturnsFalseForOtherGames() throws IOException {
            String configContent = """
                token = test_token
                owner = 123456789
                game = Playing music
                """;
            Path configFile = createTempConfigFile(configContent);
            System.setProperty("config.file", configFile.toString());
            
            BotConfig config = new BotConfig(mockUserInteraction);
            mockUserInteraction.addPromptResponse("test_token");
            mockUserInteraction.addPromptResponse("123456789");
            config.load();
            
            assertFalse(config.isGameNone());
        }
        
        @Test
        @DisplayName("getDBots() returns true for specific owner ID")
        void getDBotsReturnsTrueForSpecificOwnerId() throws IOException {
            String configContent = """
                token = test_token
                owner = 113156185389092864
                """;
            Path configFile = createTempConfigFile(configContent);
            System.setProperty("config.file", configFile.toString());
            
            BotConfig config = new BotConfig(mockUserInteraction);
            mockUserInteraction.addPromptResponse("test_token");
            mockUserInteraction.addPromptResponse("113156185389092864");
            config.load();
            
            assertTrue(config.getDBots());
        }
        
        @Test
        @DisplayName("getDBots() returns false for other owner IDs")
        void getDBotsReturnsFalseForOtherOwnerIds() throws IOException {
            String configContent = """
                token = test_token
                owner = 123456789
                """;
            Path configFile = createTempConfigFile(configContent);
            System.setProperty("config.file", configFile.toString());
            
            BotConfig config = new BotConfig(mockUserInteraction);
            mockUserInteraction.addPromptResponse("test_token");
            mockUserInteraction.addPromptResponse("123456789");
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
                token = test_token
                owner = 123456789
                maxtime = 0
                """;
            Path configFile = createTempConfigFile(configContent);
            System.setProperty("config.file", configFile.toString());
            
            BotConfig config = new BotConfig(mockUserInteraction);
            mockUserInteraction.addPromptResponse("test_token");
            mockUserInteraction.addPromptResponse("123456789");
            config.load();
            
            when(mockAudioTrack.getDuration()).thenReturn(3600000L); // 1 hour
            
            assertFalse(config.isTooLong(mockAudioTrack));
        }
        
        @Test
        @DisplayName("isTooLong() returns false when track is shorter than max")
        void isTooLongReturnsFalseWhenTrackIsShorter() throws IOException {
            String configContent = """
                token = test_token
                owner = 123456789
                maxtime = 300
                """;
            Path configFile = createTempConfigFile(configContent);
            System.setProperty("config.file", configFile.toString());
            
            BotConfig config = new BotConfig(mockUserInteraction);
            mockUserInteraction.addPromptResponse("test_token");
            mockUserInteraction.addPromptResponse("123456789");
            config.load();
            
            when(mockAudioTrack.getDuration()).thenReturn(120000L); // 2 minutes
            
            assertFalse(config.isTooLong(mockAudioTrack));
        }
        
        @Test
        @DisplayName("isTooLong() returns true when track is longer than max")
        void isTooLongReturnsTrueWhenTrackIsLonger() throws IOException {
            String configContent = """
                token = test_token
                owner = 123456789
                maxtime = 300
                """;
            Path configFile = createTempConfigFile(configContent);
            System.setProperty("config.file", configFile.toString());
            
            BotConfig config = new BotConfig(mockUserInteraction);
            mockUserInteraction.addPromptResponse("test_token");
            mockUserInteraction.addPromptResponse("123456789");
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
                token = test_token
                owner = 123456789
                aliases {
                  play = [ p, playmusic ]
                  skip = [ voteskip ]
                }
                """;
            Path configFile = createTempConfigFile(configContent);
            System.setProperty("config.file", configFile.toString());
            
            BotConfig config = new BotConfig(mockUserInteraction);
            mockUserInteraction.addPromptResponse("test_token");
            mockUserInteraction.addPromptResponse("123456789");
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
                token = test_token
                owner = 123456789
                aliases {
                  play = [ p ]
                }
                """;
            Path configFile = createTempConfigFile(configContent);
            System.setProperty("config.file", configFile.toString());
            
            BotConfig config = new BotConfig(mockUserInteraction);
            mockUserInteraction.addPromptResponse("test_token");
            mockUserInteraction.addPromptResponse("123456789");
            config.load();
            
            String[] aliases = config.getAliases("nonexistent");
            assertEquals(0, aliases.length);
        }
        
        @Test
        @DisplayName("getAliases() returns empty array when aliases config is missing")
        void getAliasesReturnsEmptyArrayWhenAliasesConfigMissing() throws IOException {
            String configContent = """
                token = test_token
                owner = 123456789
                """;
            Path configFile = createTempConfigFile(configContent);
            System.setProperty("config.file", configFile.toString());
            
            BotConfig config = new BotConfig(mockUserInteraction);
            mockUserInteraction.addPromptResponse("test_token");
            mockUserInteraction.addPromptResponse("123456789");
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
                token = test_token
                owner = 123456789
                """;
            Path configFile = createTempConfigFile(configContent);
            System.setProperty("config.file", configFile.toString());
            
            BotConfig config = new BotConfig(mockUserInteraction);
            mockUserInteraction.addPromptResponse("test_token");
            mockUserInteraction.addPromptResponse("123456789");
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
                token = test_token
                owner = 123456789
                audiosources = [ youtube, soundcloud ]
                """;
            Path configFile = createTempConfigFile(configContent);
            System.setProperty("config.file", configFile.toString());
            
            BotConfig config = new BotConfig(mockUserInteraction);
            mockUserInteraction.addPromptResponse("test_token");
            mockUserInteraction.addPromptResponse("123456789");
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
                token = test_token
                owner = 123456789
                audiosources = [ youtube ]
                """;
            Path configFile = createTempConfigFile(configContent);
            System.setProperty("config.file", configFile.toString());
            
            BotConfig config = new BotConfig(mockUserInteraction);
            mockUserInteraction.addPromptResponse("test_token");
            mockUserInteraction.addPromptResponse("123456789");
            config.load();
            
            assertTrue(config.isAudioSourceEnabled(AudioSource.YOUTUBE));
            assertFalse(config.isAudioSourceEnabled(AudioSource.SOUNDCLOUD));
        }
        
        @Test
        @DisplayName("isAudioSourceEnabled() filters invalid source names")
        void isAudioSourceEnabledFiltersInvalidSourceNames() throws IOException {
            String configContent = """
                token = test_token
                owner = 123456789
                audiosources = [ youtube, invalid_source, soundcloud ]
                """;
            Path configFile = createTempConfigFile(configContent);
            System.setProperty("config.file", configFile.toString());
            
            BotConfig config = new BotConfig(mockUserInteraction);
            mockUserInteraction.addPromptResponse("test_token");
            mockUserInteraction.addPromptResponse("123456789");
            config.load();
            
            // Invalid source should be filtered out
            Set<AudioSource> sources = config.getEnabledAudioSources();
            assertTrue(sources.contains(AudioSource.YOUTUBE));
            assertTrue(sources.contains(AudioSource.SOUNDCLOUD));
            assertEquals(2, sources.size());
        }
    }
    
    @Nested
    @DisplayName("Status and Game Tests")
    class StatusAndGameTests {
        
        @Test
        @DisplayName("getStatus() returns correct OnlineStatus")
        void getStatusReturnsCorrectOnlineStatus() throws IOException {
            String configContent = """
                token = test_token
                owner = 123456789
                status = IDLE
                """;
            Path configFile = createTempConfigFile(configContent);
            System.setProperty("config.file", configFile.toString());
            
            BotConfig config = new BotConfig(mockUserInteraction);
            mockUserInteraction.addPromptResponse("test_token");
            mockUserInteraction.addPromptResponse("123456789");
            config.load();
            
            assertEquals(OnlineStatus.IDLE, config.getStatus());
        }
        
        @Test
        @DisplayName("getGame() returns correct Activity")
        void getGameReturnsCorrectActivity() throws IOException {
            String configContent = """
                token = test_token
                owner = 123456789
                game = Playing music
                """;
            Path configFile = createTempConfigFile(configContent);
            System.setProperty("config.file", configFile.toString());
            
            BotConfig config = new BotConfig(mockUserInteraction);
            mockUserInteraction.addPromptResponse("test_token");
            mockUserInteraction.addPromptResponse("123456789");
            config.load();
            
            assertNotNull(config.getGame());
            assertTrue(config.getGame().getName().contains("music"));
        }
    }
}
