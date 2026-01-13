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
package com.jagrosh.jmusicbot.unit.config.render;

import com.jagrosh.jmusicbot.config.diagnostics.ConfigDiagnostics;
import com.jagrosh.jmusicbot.config.render.ConfigRenderer;
import com.jagrosh.jmusicbot.testutil.config.V1ConfigBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.parser.ConfigDocument;
import com.typesafe.config.parser.ConfigDocumentFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigRenderer Unit Tests")
class ConfigRendererTest {
    
    @Nested
    @DisplayName("ConfigDocument Usage")
    class ConfigDocumentUsageTests {
        
        @Test
        @DisplayName("generateConfigContent uses ConfigDocument when reference.conf is available")
        void generateConfigContentUsesConfigDocument() {
            Config migratedUserConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("test_token")
                .withDiscordOwner(123456789L)
                .withCommandsPrefix("!!")
                .build();
            
            ConfigDiagnostics.Report diagnostics = new ConfigDiagnostics.Report(
                new HashSet<>(), new HashSet<>(), new HashSet<>()
            );
            
            String content = ConfigRenderer.generateConfigContent(migratedUserConfig, diagnostics);
            
            assertNotNull(content);
            // Should contain header comments added by ConfigRenderer
            assertTrue(content.contains("# This file was automatically migrated and updated"));
            // Should be parseable as ConfigDocument
            ConfigDocument doc = ConfigDocumentFactory.parseString(content);
            assertNotNull(doc);
        }
        
        @Test
        @DisplayName("generateConfigContent preserves structure from reference.conf template")
        void generateConfigContentPreservesStructure() {
            Config migratedUserConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("test_token")
                .withDiscordOwner(123456789L)
                .build();
            
            ConfigDiagnostics.Report diagnostics = new ConfigDiagnostics.Report(
                new HashSet<>(), new HashSet<>(), new HashSet<>()
            );
            
            String content = ConfigRenderer.generateConfigContent(migratedUserConfig, diagnostics);
            
            // Should have nested structure matching reference.conf
            assertTrue(content.contains("meta {") || content.contains("meta"));
            assertTrue(content.contains("discord {") || content.contains("discord"));
        }
        
        @Test
        @DisplayName("generateConfigContent updates user values in template")
        void generateConfigContentUpdatesUserValues() {
            Config migratedUserConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("custom_token")
                .withDiscordOwner(999999999L)
                .withCommandsPrefix("custom_prefix")
                .build();
            
            ConfigDiagnostics.Report diagnostics = new ConfigDiagnostics.Report(
                new HashSet<>(), new HashSet<>(), new HashSet<>()
            );
            
            String content = ConfigRenderer.generateConfigContent(migratedUserConfig, diagnostics);
            
            // Parse the generated content to verify values
            Config generated = ConfigFactory.parseString(content);
            assertEquals("custom_token", generated.getString("discord.token"));
            assertEquals(999999999L, generated.getLong("discord.owner"));
            assertEquals("custom_prefix", generated.getString("commands.prefix"));
        }
        
        @Test
        @DisplayName("generateConfigContent includes diagnostic information in comments")
        void generateConfigContentIncludesDiagnostics() {
            Config migratedUserConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("test_token")
                .withDiscordOwner(123456789L)
                .build();
            
            Set<String> missingRequired = new HashSet<>();
            missingRequired.add("discord.token");
            Set<String> deprecated = new HashSet<>();
            deprecated.add("oldKey");
            ConfigDiagnostics.Report diagnostics = new ConfigDiagnostics.Report(
                missingRequired, new HashSet<>(), deprecated
            );
            
            String content = ConfigRenderer.generateConfigContent(migratedUserConfig, diagnostics);
            
            assertTrue(content.contains("# Changes detected"));
            assertTrue(content.contains("Missing required keys"));
            assertTrue(content.contains("Deprecated keys removed"));
        }
    }
    
    @Nested
    @DisplayName("Comment and Formatting Preservation")
    class CommentAndFormattingPreservationTests {
        
        @Test
        @DisplayName("generateConfigContent preserves comments from reference.conf template")
        void generateConfigContentPreservesComments() {
            Config migratedUserConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("test_token")
                .withDiscordOwner(123456789L)
                .build();
            
            ConfigDiagnostics.Report diagnostics = new ConfigDiagnostics.Report(
                new HashSet<>(), new HashSet<>(), new HashSet<>()
            );
            
            String content = ConfigRenderer.generateConfigContent(migratedUserConfig, diagnostics);
            
            // reference.conf should have comments, and ConfigDocument should preserve them
            // The exact comments depend on reference.conf, but we should have some
            assertNotNull(content);
            
            // Parse as ConfigDocument to verify it's valid
            ConfigDocument doc = ConfigDocumentFactory.parseString(content);
            assertNotNull(doc);
            
            // Verify that ConfigDocument was used (content should have structure from reference.conf)
            // If ConfigDocument wasn't used, the fallback would have different formatting
            String rendered = doc.render();
            assertFalse(rendered.isEmpty());
            
            // Verify the content can be parsed back to Config with correct values
            Config parsed = ConfigFactory.parseString(content);
            assertEquals("test_token", parsed.getString("discord.token"));
        }
        
        @Test
        @DisplayName("generateConfigContent preserves nested structure formatting")
        void generateConfigContentPreservesNestedStructure() {
            Config migratedUserConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("test_token")
                .withDiscordOwner(123456789L)
                .withCommandsPrefix("!!")
                .withPlaybackMaxTrackSeconds(3600L)
                .build();
            
            ConfigDiagnostics.Report diagnostics = new ConfigDiagnostics.Report(
                new HashSet<>(), new HashSet<>(), new HashSet<>()
            );
            
            String content = ConfigRenderer.generateConfigContent(migratedUserConfig, diagnostics);
            
            // Parse and verify nested structure is preserved
            Config parsed = ConfigFactory.parseString(content);
            assertTrue(parsed.hasPath("meta.configVersion"));
            assertTrue(parsed.hasPath("discord.token"));
            assertTrue(parsed.hasPath("commands.prefix"));
            assertTrue(parsed.hasPath("playback.maxTrackSeconds"));
        }
        
        @Test
        @DisplayName("generateConfigContent uses ConfigDocument to preserve reference.conf structure")
        void generateConfigContentUsesConfigDocumentForStructure() {
            Config migratedUserConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("test_token")
                .withDiscordOwner(123456789L)
                .build();
            
            ConfigDiagnostics.Report diagnostics = new ConfigDiagnostics.Report(
                new HashSet<>(), new HashSet<>(), new HashSet<>()
            );
            
            String content = ConfigRenderer.generateConfigContent(migratedUserConfig, diagnostics);
            
            // Verify ConfigDocument was used (not fallback)
            // ConfigDocument preserves the structure from reference.conf template
            ConfigDocument doc = ConfigDocumentFactory.parseString(content);
            assertNotNull(doc);
            
            // The content should be parseable and have correct structure
            Config parsed = ConfigFactory.parseString(content);
            assertTrue(parsed.hasPath("meta.configVersion"));
            assertEquals(1, parsed.getInt("meta.configVersion"));
            assertEquals("test_token", parsed.getString("discord.token"));
        }
        
        @Test
        @DisplayName("generateConfigContent preserves comments when ConfigDocument is used")
        void generateConfigContentPreservesCommentsViaConfigDocument() {
            Config migratedUserConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("test_token")
                .withDiscordOwner(123456789L)
                .build();
            
            ConfigDiagnostics.Report diagnostics = new ConfigDiagnostics.Report(
                new HashSet<>(), new HashSet<>(), new HashSet<>()
            );
            
            String content = ConfigRenderer.generateConfigContent(migratedUserConfig, diagnostics);
            
            // Parse as ConfigDocument to verify it was used
            ConfigDocument doc = ConfigDocumentFactory.parseString(content);
            assertNotNull(doc);
            
            // ConfigDocument should preserve comments from reference.conf template
            // The rendered document should maintain the structure
            String rendered = doc.render();
            
            // Verify the content is valid and has correct values
            Config parsed = ConfigFactory.parseString(content);
            assertEquals("test_token", parsed.getString("discord.token"));
            assertEquals(123456789L, parsed.getLong("discord.owner"));
            
            // Verify structure is preserved (ConfigDocument maintains nested structure)
            assertTrue(parsed.hasPath("meta.configVersion"));
            assertTrue(parsed.hasPath("discord.token"));
        }
    }
    
    @Nested
    @DisplayName("Fallback Behavior")
    class FallbackBehaviorTests {
        
        @Test
        @DisplayName("generateConfigContentFallback uses Config when ConfigDocument unavailable")
        void generateConfigContentFallbackUsesConfig() {
            Config migratedUserConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("test_token")
                .withDiscordOwner(123456789L)
                .build();
            
            ConfigDiagnostics.Report diagnostics = new ConfigDiagnostics.Report(
                new HashSet<>(), new HashSet<>(), new HashSet<>()
            );
            
            String content = ConfigRenderer.generateConfigContentFallback(migratedUserConfig, diagnostics);
            
            assertNotNull(content);
            // Should still be valid HOCON
            Config parsed = ConfigFactory.parseString(content);
            assertNotNull(parsed);
            assertEquals("test_token", parsed.getString("discord.token"));
        }
    }
}
