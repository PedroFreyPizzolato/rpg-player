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
package com.jagrosh.jmusicbot.unit.config.io;

import com.jagrosh.jmusicbot.config.io.ConfigResourceLoader;
import com.typesafe.config.Config;
import com.typesafe.config.parser.ConfigDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigResourceLoader Unit Tests")
class ConfigResourceLoaderTest {
    
    @Nested
    @DisplayName("loadDefaults() Tests")
    class LoadDefaultsTests {
        
        @Test
        @DisplayName("loadDefaults loads reference.conf from classpath")
        void loadDefaultsLoadsReferenceConf() {
            Config defaults = ConfigResourceLoader.loadDefaults();
            
            assertNotNull(defaults);
            // Should have meta.configVersion
            assertTrue(defaults.hasPath("meta.configVersion"));
        }
    }
    
    @Nested
    @DisplayName("loadReferenceConfigAsDocument() Tests")
    class LoadReferenceConfigAsDocumentTests {
        
        @Test
        @DisplayName("loadReferenceConfigAsDocument loads reference.conf as ConfigDocument")
        void loadReferenceConfigAsDocumentLoadsDocument() {
            ConfigDocument doc = ConfigResourceLoader.loadReferenceConfigAsDocument();
            
            assertNotNull(doc, "Should load reference.conf as ConfigDocument");
        }
        
        @Test
        @DisplayName("loadReferenceConfigAsDocument preserves comments")
        void loadReferenceConfigAsDocumentPreservesComments() {
            ConfigDocument doc = ConfigResourceLoader.loadReferenceConfigAsDocument();
            
            assertNotNull(doc);
            String rendered = doc.render();
            // reference.conf should have comments
            assertTrue(rendered.contains("#") || rendered.contains("//"), 
                "ConfigDocument should preserve comments from reference.conf");
        }
        
        @Test
        @DisplayName("loadReferenceConfigAsDocument preserves structure")
        void loadReferenceConfigAsDocumentPreservesStructure() {
            ConfigDocument doc = ConfigResourceLoader.loadReferenceConfigAsDocument();
            
            assertNotNull(doc);
            String rendered = doc.render();
            // Should have nested structure
            assertTrue(rendered.contains("meta {") || rendered.contains("meta"));
            assertTrue(rendered.contains("discord {") || rendered.contains("discord"));
        }
        
        @Test
        @DisplayName("loadReferenceConfigAsDocument can be parsed back to Config")
        void loadReferenceConfigAsDocumentCanBeParsedToConfig() {
            ConfigDocument doc = ConfigResourceLoader.loadReferenceConfigAsDocument();
            
            assertNotNull(doc);
            // Should be parseable as Config
            Config config = com.typesafe.config.ConfigFactory.parseString(doc.render());
            assertNotNull(config);
            assertTrue(config.hasPath("meta.configVersion"));
        }
    }
    
    @Nested
    @DisplayName("loadReferenceConfigAsString() Tests")
    class LoadReferenceConfigAsStringTests {
        
        @Test
        @DisplayName("loadReferenceConfigAsString loads reference.conf content")
        void loadReferenceConfigAsStringLoadsContent() {
            String content = ConfigResourceLoader.loadReferenceConfigAsString();
            
            assertNotNull(content);
            assertFalse(content.isEmpty());
            // Should contain key config elements
            assertTrue(content.contains("meta") || content.contains("discord"));
        }
    }
}
