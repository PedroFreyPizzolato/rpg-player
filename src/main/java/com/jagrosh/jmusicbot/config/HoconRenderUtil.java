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
package com.jagrosh.jmusicbot.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigValue;

/**
 * Utility class for rendering Config values as HOCON strings.
 * 
 * @author Arif Banai (arif-banai)
 */
public class HoconRenderUtil {
    private static final ConfigRenderOptions INLINE_VALUE_OPTIONS = ConfigRenderOptions.defaults()
            .setOriginComments(false)
            .setComments(false)
            .setFormatted(false)
            .setJson(false);
    
    private static final ConfigRenderOptions OBJECT_OPTIONS = ConfigRenderOptions.defaults()
            .setOriginComments(false)
            .setComments(false)
            .setFormatted(true)
            .setJson(false);
    
    /**
     * Renders a ConfigValue as a HOCON string suitable for inline use.
     * This preserves HOCON syntax (not JSON) and is appropriate for setting values in a ConfigDocument.
     * 
     * @param value the ConfigValue to render
     * @return the HOCON string representation
     */
    public static String renderValue(ConfigValue value) {
        if (value == null) {
            return "null";
        }
        return value.render(INLINE_VALUE_OPTIONS);
    }
    
    /**
     * Renders a Config object (nested config) as a HOCON object string.
     * This is used for nested configurations like commands.aliases, playback.transforms, etc.
     * The returned string is suitable for use with ConfigDocument.withValueText().
     * 
     * @param config the Config object to render
     * @return the HOCON object representation (with braces, no leading/trailing whitespace)
     */
    public static String renderConfigObject(Config config) {
        if (config == null || config.isEmpty()) {
            return "{}";
        }
        // Render with formatting, then trim to remove leading/trailing whitespace
        // (withValueText doesn't allow leading/trailing whitespace)
        String rendered = config.root().render(OBJECT_OPTIONS);
        return rendered.trim();
    }
}
