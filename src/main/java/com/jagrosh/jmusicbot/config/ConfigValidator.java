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

import java.nio.file.Path;

import com.jagrosh.jmusicbot.entities.Prompt;
import com.jagrosh.jmusicbot.entities.UserInteraction;

/**
 * Handles validation of required configuration values.
 * 
 * @author Arif Banai (arif-banai)
 */
public class ConfigValidator {
    private static final String CONTEXT = "Config";
    
    /**
     * Validates the bot token, prompting the user if missing or invalid.
     * 
     * @return ValidationResult containing the token (or null if invalid) and whether a write is needed
     */
    public static ValidationResult validateToken(String token, UserInteraction userInteraction, Path configPath) {
        if (token == null || token.isEmpty() || token.equalsIgnoreCase("BOT_TOKEN_HERE")) {
            String newToken = userInteraction.prompt("Please provide a bot token."
                    + "\nInstructions for obtaining a token can be found here:"
                    + "\nhttps://github.com/jagrosh/MusicBot/wiki/Getting-a-Bot-Token."
                    + "\nBot Token: ");
            if (newToken == null) {
                userInteraction.alert(Prompt.Level.WARNING, CONTEXT,
                        "No token provided! Exiting.\n\nConfig Location: " + configPath.toAbsolutePath().toString());
                return new ValidationResult(null, false, false);
            }
            return new ValidationResult(newToken, true, true);
        }
        return new ValidationResult(token, true, false);
    }
    
    /**
     * Validates the bot owner ID, prompting the user if missing or invalid.
     * 
     * @return ValidationResult containing the owner ID (or null if invalid) and whether a write is needed
     */
    public static ValidationResult validateOwner(Long owner, UserInteraction userInteraction, Path configPath) {
        if (owner == null || owner <= 0) {
            try {
                String ownerInput = userInteraction.prompt("Owner ID was missing, or the provided owner ID is not valid."
                        + "\nPlease provide the User ID of the bot's owner."
                        + "\nInstructions for obtaining your User ID can be found here:"
                        + "\nhttps://github.com/jagrosh/MusicBot/wiki/Finding-Your-User-ID"
                        + "\nOwner User ID: ");
                if (ownerInput != null) {
                    long newOwner = Long.parseLong(ownerInput);
                    if (newOwner > 0) {
                        return new ValidationResult(newOwner, true, true);
                    }
                }
            } catch (NumberFormatException | NullPointerException ex) {
                // Fall through to error
            }
            userInteraction.alert(Prompt.Level.ERROR, CONTEXT,
                    "Invalid User ID! Exiting.\n\nConfig Location: " + configPath.toAbsolutePath().toString());
            return new ValidationResult(null, false, false);
        }
        return new ValidationResult(owner, true, false);
    }
    
    /**
     * Result of a validation operation.
     */
    public static class ValidationResult {
        private final Object value;
        private final boolean valid;
        private final boolean needsWrite;
        
        public ValidationResult(Object value, boolean valid, boolean needsWrite) {
            this.value = value;
            this.valid = valid;
            this.needsWrite = needsWrite;
        }
        
        @SuppressWarnings("unchecked")
        public <T> T getValue() {
            return (T) value;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public boolean needsWrite() {
            return needsWrite;
        }
    }
}
