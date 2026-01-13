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
package com.jagrosh.jmusicbot.unit.config;

import com.jagrosh.jmusicbot.config.migration.versions.LegacyToV1;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MigrationV0ToV1 Unit Tests")
class LegacyToV1Test {
    
    private final LegacyToV1 migration = new LegacyToV1();
    
    @Test
    @DisplayName("getFromVersion returns 0")
    void testGetFromVersion() {
        assertEquals(0, migration.getFromVersion());
    }
    
    @Test
    @DisplayName("getToVersion returns 1")
    void testGetToVersion() {
        assertEquals(1, migration.getToVersion());
    }
    
    @Nested
    @DisplayName("Simple Key Mappings")
    class SimpleKeyMappings {
        
        @Test
        @DisplayName("migrate maps token to discord.token")
        void testMigrate_token() {
            Map<String, Object> configMap = new HashMap<>();
            configMap.put("token", "test_token_123");
            Config legacy = ConfigFactory.parseMap(configMap);
            
            Config migrated = migration.migrate(legacy);
            
            assertTrue(migrated.hasPath("discord.token"));
            assertEquals("test_token_123", migrated.getString("discord.token"));
        }
        
        @Test
        @DisplayName("migrate maps owner to discord.owner")
        void testMigrate_owner() {
            Map<String, Object> configMap = new HashMap<>();
            configMap.put("owner", 987654321L);
            Config legacy = ConfigFactory.parseMap(configMap);
            
            Config migrated = migration.migrate(legacy);
            
            assertTrue(migrated.hasPath("discord.owner"));
            assertEquals(987654321L, migrated.getLong("discord.owner"));
        }
        
        @Test
        @DisplayName("migrate maps prefix to commands.prefix")
        void testMigrate_prefix() {
            Map<String, Object> configMap = new HashMap<>();
            configMap.put("prefix", "!!");
            Config legacy = ConfigFactory.parseMap(configMap);
            
            Config migrated = migration.migrate(legacy);
            
            assertTrue(migrated.hasPath("commands.prefix"));
            assertEquals("!!", migrated.getString("commands.prefix"));
        }
        
        @Test
        @DisplayName("migrate maps altprefix NONE to commands.altPrefix NONE")
        void testMigrate_altprefix_noneToNone() {
            Map<String, Object> configMap = new HashMap<>();
            configMap.put("altprefix", "NONE");
            Config legacy = ConfigFactory.parseMap(configMap);
            
            Config migrated = migration.migrate(legacy);
            
            // Verify commands.altPrefix exists and is "NONE" (never null)
            assertTrue(migrated.hasPath("commands.altPrefix"));
            assertEquals("NONE", migrated.getString("commands.altPrefix"));
        }
        
        @Test
        @DisplayName("migrate maps altprefix value to commands.altPrefix")
        void testMigrate_altprefix_value() {
            Map<String, Object> configMap = new HashMap<>();
            configMap.put("altprefix", "??");
            Config legacy = ConfigFactory.parseMap(configMap);
            
            Config migrated = migration.migrate(legacy);
            
            assertTrue(migrated.hasPath("commands.altPrefix"));
            assertEquals("??", migrated.getString("commands.altPrefix"));
        }
        
        @Test
        @DisplayName("migrate adds meta.configVersion = 1")
        void testMigrate_addsMetaVersion() {
            Map<String, Object> configMap = new HashMap<>();
            Config legacy = ConfigFactory.parseMap(configMap);
            
            Config migrated = migration.migrate(legacy);
            
            assertTrue(migrated.hasPath("meta.configVersion"));
            assertEquals(1, migrated.getInt("meta.configVersion"));
        }
    }
    
    @Nested
    @DisplayName("Audio Sources Migration")
    class AudioSourcesMigration {
        
        @Test
        @DisplayName("migrate converts audiosources list to playback.audioSources booleans")
        void testMigrate_audiosources_listToBooleans() {
            Map<String, Object> configMap = new HashMap<>();
            configMap.put("audiosources", List.of("youtube", "soundcloud", "local"));
            Config legacy = ConfigFactory.parseMap(configMap);
            
            Config migrated = migration.migrate(legacy);
            
            assertTrue(migrated.hasPath("playback.audioSources"));
            Config audioSources = migrated.getConfig("playback.audioSources");
            assertTrue(audioSources.getBoolean("youtube"));
            assertTrue(audioSources.getBoolean("soundcloud"));
            assertTrue(audioSources.getBoolean("local"));
            assertFalse(audioSources.getBoolean("bandcamp"));
        }
        
        @Test
        @DisplayName("migrate sets all audioSources to true when audiosources key is missing")
        void testMigrate_audiosources_missing_defaultsToAll() {
            Map<String, Object> configMap = new HashMap<>();
            Config legacy = ConfigFactory.parseMap(configMap);
            
            Config migrated = migration.migrate(legacy);
            
            // When audiosources is missing, migration should still create audioSources with all enabled
            // (This is the default behavior - all sources enabled when key is missing)
            assertTrue(migrated.hasPath("playback.audioSources"));
            Config audioSources = migrated.getConfig("playback.audioSources");
            // All should be enabled by default
            assertTrue(audioSources.getBoolean("youtube"));
            assertTrue(audioSources.getBoolean("soundcloud"));
            assertTrue(audioSources.getBoolean("local"));
        }
        
        @Test
        @DisplayName("migrate handles empty audiosources list")
        void testMigrate_audiosources_emptyList() {
            Map<String, Object> configMap = new HashMap<>();
            configMap.put("audiosources", List.of());
            Config legacy = ConfigFactory.parseMap(configMap);
            
            Config migrated = migration.migrate(legacy);
            
            assertTrue(migrated.hasPath("playback.audioSources"));
            Config audioSources = migrated.getConfig("playback.audioSources");
            // All should be false when list is empty
            assertFalse(audioSources.getBoolean("youtube"));
            assertFalse(audioSources.getBoolean("soundcloud"));
        }
    }
    
    @Nested
    @DisplayName("Complex Configurations")
    class ComplexConfigurations {
        
        @Test
        @DisplayName("migrate preserves aliases nested config")
        void testMigrate_aliases_preserved() {
            Map<String, Object> configMap = new HashMap<>();
            Map<String, Object> aliases = new HashMap<>();
            aliases.put("play", List.of("p", "playmusic"));
            aliases.put("skip", List.of("voteskip"));
            configMap.put("aliases", aliases);
            Config legacy = ConfigFactory.parseMap(configMap);
            
            Config migrated = migration.migrate(legacy);
            
            assertTrue(migrated.hasPath("commands.aliases"));
            Config aliasesConfig = migrated.getConfig("commands.aliases");
            assertEquals(List.of("p", "playmusic"), aliasesConfig.getStringList("play"));
            assertEquals(List.of("voteskip"), aliasesConfig.getStringList("skip"));
        }
        
        @Test
        @DisplayName("migrate preserves transforms nested config")
        void testMigrate_transforms_preserved() {
            Map<String, Object> configMap = new HashMap<>();
            Map<String, Object> transforms = new HashMap<>();
            transforms.put("test", "value");
            configMap.put("transforms", transforms);
            Config legacy = ConfigFactory.parseMap(configMap);
            
            Config migrated = migration.migrate(legacy);
            
            assertTrue(migrated.hasPath("playback.transforms"));
            Config transformsConfig = migrated.getConfig("playback.transforms");
            assertEquals("value", transformsConfig.getString("test"));
        }
    }
    
    @Test
    @DisplayName("migrate handles complete legacy config")
    void testMigrate_allLegacyKeys() {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("token", "test_token");
        configMap.put("owner", 123456789L);
        configMap.put("prefix", "!!");
        configMap.put("altprefix", "NONE");
        configMap.put("help", "help");
        configMap.put("game", "DEFAULT");
        configMap.put("status", "ONLINE");
        configMap.put("songinstatus", true);
        configMap.put("success", "✅");
        configMap.put("stayinchannel", false);
        configMap.put("maxtime", 3600L);
        configMap.put("skipratio", 0.75);
        configMap.put("loglevel", "info");
        Config legacy = ConfigFactory.parseMap(configMap);
        
        Config migrated = migration.migrate(legacy);
        
        // Verify all keys migrated correctly
        assertEquals("test_token", migrated.getString("discord.token"));
        assertEquals(123456789L, migrated.getLong("discord.owner"));
        assertEquals("!!", migrated.getString("commands.prefix"));
        // altPrefix should be "NONE" (never null)
        assertEquals("NONE", migrated.getString("commands.altPrefix"));
        assertEquals("help", migrated.getString("commands.help"));
        assertEquals("DEFAULT", migrated.getString("presence.game"));
        assertEquals("ONLINE", migrated.getString("presence.status"));
        assertTrue(migrated.getBoolean("presence.songInStatus"));
        assertEquals("✅", migrated.getString("ui.emojis.success"));
        assertFalse(migrated.getBoolean("voice.stayInChannel"));
        assertEquals(3600L, migrated.getLong("playback.maxTrackSeconds"));
        assertEquals(0.75, migrated.getDouble("playback.skipRatio"));
        assertEquals("info", migrated.getString("logging.level"));
        
        // Verify legacy flat keys are NOT present in migrated config
        assertFalse(migrated.hasPath("token"));
        assertFalse(migrated.hasPath("owner"));
        assertFalse(migrated.hasPath("prefix"));
        assertFalse(migrated.hasPath("altprefix"));
    }
    
    @Test
    @DisplayName("migrate converts status to uppercase")
    void testMigrate_statusUppercase() {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("status", "idle");
        Config legacy = ConfigFactory.parseMap(configMap);
        
        Config migrated = migration.migrate(legacy);
        
        assertEquals("IDLE", migrated.getString("presence.status"));
    }
    
    @Test
    @DisplayName("migrate ignores lyrics keys (lyrics functionality removed)")
    void testMigrate_lyricsDefault() {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("lyrics.default", "Genius");
        Config legacy = ConfigFactory.parseMap(configMap);
        
        Config migrated = migration.migrate(legacy);
        
        // Lyrics keys should NOT be migrated since lyrics functionality is being removed
        assertFalse(migrated.hasPath("lyrics.default"));
        assertFalse(migrated.hasPath("lyrics"));
    }
}
