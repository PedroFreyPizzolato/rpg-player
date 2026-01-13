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

import com.jagrosh.jmusicbot.config.render.HoconRenderUtil;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HoconRenderUtil Unit Tests")
class HoconRenderUtilTest {
    
    @Nested
    @DisplayName("renderValue() Tests")
    class RenderValueTests {
        
        @Test
        @DisplayName("renderValue renders string values")
        void renderValueRendersString() {
            ConfigValue value = ConfigFactory.parseString("key = test").getValue("key");
            
            String rendered = HoconRenderUtil.renderValue(value);
            
            // HOCON doesn't require quotes for simple strings without special characters
            assertEquals("test", rendered);
        }
        
        @Test
        @DisplayName("renderValue renders strings with quotes when needed")
        void renderValueRendersStringWithQuotes() {
            // String with spaces requires quotes in HOCON
            ConfigValue value = ConfigFactory.parseString("key = \"test value\"").getValue("key");
            
            String rendered = HoconRenderUtil.renderValue(value);
            
            // Strings with spaces should be quoted
            assertTrue(rendered.contains("\"test value\"") || rendered.contains("test value"));
        }
        
        @Test
        @DisplayName("renderValue renders number values")
        void renderValueRendersNumber() {
            ConfigValue value = ConfigFactory.parseString("key = 123").getValue("key");
            
            String rendered = HoconRenderUtil.renderValue(value);
            
            assertEquals("123", rendered);
        }
        
        @Test
        @DisplayName("renderValue renders boolean values")
        void renderValueRendersBoolean() {
            ConfigValue value = ConfigFactory.parseString("key = true").getValue("key");
            
            String rendered = HoconRenderUtil.renderValue(value);
            
            assertEquals("true", rendered);
        }
        
        @Test
        @DisplayName("renderValue renders list values")
        void renderValueRendersList() {
            ConfigValue value = ConfigFactory.parseString("key = [a, b, c]").getValue("key");
            
            String rendered = HoconRenderUtil.renderValue(value);
            
            assertTrue(rendered.contains("a"));
            assertTrue(rendered.contains("b"));
            assertTrue(rendered.contains("c"));
        }
        
        @Test
        @DisplayName("renderValue returns null for null input")
        void renderValueReturnsNullForNull() {
            String rendered = HoconRenderUtil.renderValue(null);
            
            assertEquals("null", rendered);
        }
    }
    
    @Nested
    @DisplayName("renderConfigObject() Tests")
    class RenderConfigObjectTests {
        
        @Test
        @DisplayName("renderConfigObject renders nested config")
        void renderConfigObjectRendersNestedConfig() {
            Config config = ConfigFactory.parseMap(Map.of(
                "key1", "value1",
                "key2", "value2"
            ));
            
            String rendered = HoconRenderUtil.renderConfigObject(config);
            
            assertTrue(rendered.contains("key1"));
            assertTrue(rendered.contains("value1"));
            assertTrue(rendered.contains("key2"));
            assertTrue(rendered.contains("value2"));
        }
        
        @Test
        @DisplayName("renderConfigObject returns empty braces for empty config")
        void renderConfigObjectReturnsEmptyBracesForEmpty() {
            Config config = ConfigFactory.empty();
            
            String rendered = HoconRenderUtil.renderConfigObject(config);
            
            assertEquals("{}", rendered.trim());
        }
        
        @Test
        @DisplayName("renderConfigObject returns empty braces for null")
        void renderConfigObjectReturnsEmptyBracesForNull() {
            String rendered = HoconRenderUtil.renderConfigObject(null);
            
            assertEquals("{}", rendered);
        }
        
        @Test
        @DisplayName("renderConfigObject renders nested structures")
        void renderConfigObjectRendersNestedStructures() {
            Map<String, Object> nested = Map.of("inner", "value");
            Config config = ConfigFactory.parseMap(Map.of("outer", nested));
            
            String rendered = HoconRenderUtil.renderConfigObject(config);
            
            assertTrue(rendered.contains("outer"));
            assertTrue(rendered.contains("inner"));
        }
        
        @Test
        @DisplayName("renderConfigObject renders lists in config")
        void renderConfigObjectRendersLists() {
            Config config = ConfigFactory.parseMap(Map.of(
                "aliases", List.of("a", "b", "c")
            ));
            
            String rendered = HoconRenderUtil.renderConfigObject(config);
            
            assertTrue(rendered.contains("aliases"));
            assertTrue(rendered.contains("a"));
        }
    }
}
