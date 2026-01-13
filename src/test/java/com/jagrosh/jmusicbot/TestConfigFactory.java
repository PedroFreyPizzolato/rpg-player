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

import com.jagrosh.jmusicbot.testutil.config.LegacyConfigBuilder;
import com.jagrosh.jmusicbot.testutil.config.LegacyConfigTestData;
import com.jagrosh.jmusicbot.testutil.config.V1ConfigBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Factory for creating test Config objects and temporary config files.
 */
public class TestConfigFactory {
    
    /**
     * Creates a minimal valid config with only required fields.
     */
    public static Config createMinimalValidConfig() {
        return LegacyConfigTestData.minimal();
    }
    
    /**
     * Creates a config with all optional fields set to default values.
     */
    public static Config createFullConfig() {
        return LegacyConfigTestData.full();
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
        return createTempConfigFile(LegacyConfigTestData.minimal().root().render());
    }
    
    /**
     * Creates a temporary config file with full config.
     */
    public static Path createFullTempConfigFile() throws IOException {
        return createTempConfigFile(LegacyConfigTestData.full().root().render());
    }
    
    /**
     * Creates a config with invalid token (placeholder).
     */
    public static Config createConfigWithInvalidToken() {
        return LegacyConfigTestData.withInvalidToken();
    }
    
    /**
     * Creates a config with invalid owner (zero).
     */
    public static Config createConfigWithInvalidOwner() {
        return LegacyConfigTestData.withInvalidOwner();
    }
    
    /**
     * Creates a config with missing required fields.
     */
    public static Config createConfigWithMissingRequired() {
        return LegacyConfigTestData.withMissingRequired();
    }
    
    /**
     * Creates a config with audio sources configuration.
     */
    public static Config createConfigWithAudioSources(String... sources) {
        return LegacyConfigTestData.withAudioSources(sources);
    }
    
    /**
     * Creates a legacy-format (flat) config for testing migrations.
     */
    public static Config createLegacyConfig() {
        return LegacyConfigBuilder.create()
            .withToken("test_token_12345")
            .withOwner(123456789L)
            .withPrefix("@mention")
            .withAltPrefix("NONE")
            .withHelp("help")
            .withGame("DEFAULT")
            .withStatus("ONLINE")
            .build();
    }
    
    /**
     * Creates a new-format (nested) config for testing.
     */
    public static Config createNewFormatConfig() {
        return V1ConfigBuilder.create()
            .withMetaVersion(1)
            .withDiscordToken("test_token_12345")
            .withDiscordOwner(123456789L)
            .withCommandsPrefix("@mention")
            .withCommandsAltPrefix("NONE")
            .withCommandsHelp("help")
            .withPresenceGame("DEFAULT")
            .withPresenceStatus("ONLINE")
            .build();
    }
    
    /**
     * Creates a config with a specific version number.
     */
    public static Config createConfigWithVersion(int version) {
        return V1ConfigBuilder.create()
            .withMetaVersion(version)
            .withDiscordToken("test_token_12345")
            .withDiscordOwner(123456789L)
            .build();
    }
}
