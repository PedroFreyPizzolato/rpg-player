/*
 * Copyright 2024
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
package com.jagrosh.jmusicbot.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
// VoiceServerUpdateEvent interface - implementation depends on JDA version

/**
 * Interface for audio providers (Lavaplayer, Lavalink, etc.)
 * This abstraction allows the bot to work with different audio processing backends.
 * 
 * @author JMusicBot Contributors
 */
public interface AudioProvider
{
    /**
     * Initialize the audio provider.
     * This should be called once during bot startup.
     */
    void init();
    
    /**
     * Create or get an AudioHandler for a specific guild.
     * 
     * @param guild The Discord guild to create a handler for
     * @return An AudioHandler instance for the guild
     */
    AudioHandler createHandler(Guild guild);
    
    /**
     * Check if a guild has an active audio handler.
     * 
     * @param guild The Discord guild to check
     * @return true if the guild has an active handler
     */
    boolean hasHandler(Guild guild);
    
    /**
     * Load an audio item (track, playlist, etc.) from an identifier.
     * The identifier can be a URL, search query, or other supported format.
     * 
     * @param identifier The identifier to load (URL, search query, etc.)
     * @param resultHandler The handler to receive the load result
     */
    void loadItem(String identifier, AudioLoadResultHandler resultHandler);
    
    /**
     * Shutdown the audio provider and clean up resources.
     * This should be called when the bot is shutting down.
     */
    void shutdown();
    
    /**
     * Check if the audio provider is available and ready to use.
     * 
     * @return true if the provider is available
     */
    boolean isAvailable();
    
    /**
     * Handle voice server update.
     * Used by Lavalink to receive voice server information.
     * 
     * @param guildId The guild ID
     * @param token The voice server token
     * @param endpoint The voice server endpoint
     */
    default void handleVoiceServerUpdate(long guildId, String token, String endpoint)
    {
        // Default implementation does nothing (for Lavaplayer)
    }
    
    /**
     * Handle voice state update event.
     * Used by Lavalink to receive voice state information.
     * 
     * @param event The voice state update event
     */
    default void handleVoiceStateUpdate(GuildVoiceUpdateEvent event)
    {
        // Default implementation does nothing (for Lavaplayer)
    }
}

