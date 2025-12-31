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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.lavalink.LavalinkAudioPlaylist;
import com.jagrosh.jmusicbot.audio.lavalink.LavalinkAudioTrack;
import com.jagrosh.jmusicbot.audio.lavalink.LavalinkManager;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
// Voice server updates handled through AudioManager
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AudioProvider implementation using Lavalink.
 * Offloads audio processing to a separate Lavalink server.
 * 
 * @author JMusicBot Contributors
 */
public class LavalinkProvider implements AudioProvider
{
    private final static Logger LOGGER = LoggerFactory.getLogger(LavalinkProvider.class);
    
    private final Bot bot;
    private LavalinkManager lavalinkManager;
    private final Map<Long, LavalinkAudioHandler> handlers = new HashMap<>();
    private final Map<Long, VoiceServerData> voiceServerData = new HashMap<>();
    private final Map<Long, String> voiceSessionIds = new HashMap<>();
    private boolean initialized = false;
    
    /**
     * Stores voice server update data for a guild.
     */
    private static class VoiceServerData
    {
        final String token;
        final String endpoint;
        
        VoiceServerData(String token, String endpoint)
        {
            this.token = token;
            this.endpoint = endpoint;
        }
    }
    
    public LavalinkProvider(Bot bot)
    {
        this.bot = bot;
        // JDA will be set later, so we'll initialize LavalinkManager in init()
        this.lavalinkManager = null; // Will be initialized in init()
    }
    
    @Override
    public void init()
    {
        if(!initialized)
        {
            LOGGER.info("Initializing LavalinkProvider...");
            
            // Check if JDA is available - if not, we'll connect later
            JDA jda = bot.getJDA();
            if(jda == null)
            {
                LOGGER.info("JDA not ready yet, Lavalink connection will be delayed until JDA is available");
                return;
            }
            
            // Initialize LavalinkManager now that JDA is available
            if(lavalinkManager == null)
            {
                lavalinkManager = new LavalinkManager(bot.getConfig(), jda);
            }
            
            // Connect to Lavalink server
            connectToLavalink();
        }
    }
    
    /**
     * Connect to Lavalink server. Can be called after JDA is ready.
     */
    private void connectToLavalink()
    {
        if(lavalinkManager == null)
        {
            JDA jda = bot.getJDA();
            if(jda == null)
            {
                LOGGER.warn("Cannot connect to Lavalink: JDA not available");
                return;
            }
            lavalinkManager = new LavalinkManager(bot.getConfig(), jda);
        }
        
        // Set up event handler to dispatch events to handlers
        lavalinkManager.setEventHandler(this::handleLavalinkEvent);
        
        CompletableFuture<Void> connectFuture = lavalinkManager.connect();
        connectFuture.thenRun(() -> {
            initialized = true;
            LOGGER.info("LavalinkProvider initialized and connected");
        }).exceptionally(ex -> {
            LOGGER.error("Failed to initialize LavalinkProvider", ex);
            initialized = false;
            return null;
        });
        
        // Wait for connection (with timeout)
        try
        {
            connectFuture.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        catch(Exception e)
        {
            LOGGER.error("Lavalink connection timeout", e);
            initialized = false;
        }
    }
    
    /**
     * Initialize Lavalink connection after JDA is ready.
     * This should be called from the Ready event handler.
     */
    public void initAfterJDAReady()
    {
        if(!initialized)
        {
            LOGGER.info("JDA is ready, connecting to Lavalink...");
            connectToLavalink();
        }
    }
    
    @Override
    public AudioHandler createHandler(Guild guild)
    {
        long guildId = guild.getIdLong();
        
        if(!handlers.containsKey(guildId))
        {
            LavalinkAudioHandler handler = new LavalinkAudioHandler(this, guild, lavalinkManager);
            handlers.put(guildId, handler);
            return handler;
        }
        
        return handlers.get(guildId);
    }
    
    @Override
    public boolean hasHandler(Guild guild)
    {
        return handlers.containsKey(guild.getIdLong());
    }
    
    @Override
    public void loadItem(String identifier, AudioLoadResultHandler resultHandler)
    {
        if(!lavalinkManager.isConnected())
        {
            resultHandler.loadFailed(new FriendlyException("Lavalink server not connected", 
                    FriendlyException.Severity.COMMON, new RuntimeException("Not connected")));
            return;
        }
        
        lavalinkManager.loadTracks(identifier).thenAccept(json -> {
            try
            {
                JsonArray tracks = json.getAsJsonArray("tracks");
                
                if(tracks == null || tracks.size() == 0)
                {
                    resultHandler.noMatches();
                    return;
                }
                
                // Check load type - Lavalink v4 uses "loadType" as a string property
                String loadTypeStr = json.has("loadType") ? json.get("loadType").getAsString() : null;
                
                if("PLAYLIST_LOADED".equals(loadTypeStr) || "playlist".equals(loadTypeStr))
                {
                    // Handle playlist
                    JsonObject playlistInfo = json.getAsJsonObject("playlistInfo");
                    String playlistName = playlistInfo != null ? playlistInfo.get("name").getAsString() : "Unknown Playlist";
                    
                    // Convert Lavalink tracks to Lavaplayer tracks
                    // Note: This is a simplified conversion - in a full implementation,
                    // you'd want to create proper AudioTrack wrappers
                    AudioPlaylist playlist = new LavalinkAudioPlaylist(playlistName, tracks, identifier);
                    resultHandler.playlistLoaded(playlist);
                }
                else
                {
                    // Single track or search results
                    if(tracks.size() == 1)
                    {
                        JsonObject trackData = tracks.get(0).getAsJsonObject();
                        AudioTrack track = new LavalinkAudioTrack(trackData, identifier);
                        resultHandler.trackLoaded(track);
                    }
                    else
                    {
                        // Multiple tracks (search results)
                        AudioPlaylist searchResults = new LavalinkAudioPlaylist("Search Results", tracks, identifier);
                        resultHandler.playlistLoaded(searchResults);
                    }
                }
            }
            catch(Exception e)
            {
                LOGGER.error("Error processing Lavalink track load result", e);
                resultHandler.loadFailed(new FriendlyException("Failed to process track", 
                        FriendlyException.Severity.COMMON, e));
            }
        }).exceptionally(ex -> {
            LOGGER.error("Error loading tracks from Lavalink", ex);
            resultHandler.loadFailed(new FriendlyException("Failed to load tracks", 
                    FriendlyException.Severity.COMMON, ex));
            return null;
        });
    }
    
    @Override
    public void shutdown()
    {
        handlers.clear();
        lavalinkManager.shutdown();
        initialized = false;
        LOGGER.info("LavalinkProvider shutdown");
    }
    
    @Override
    public boolean isAvailable()
    {
        return initialized && lavalinkManager.isConnected();
    }
    
    /**
     * Get the LavalinkManager instance.
     * 
     * @return The LavalinkManager
     */
    public LavalinkManager getLavalinkManager()
    {
        return lavalinkManager;
    }
    
    /**
     * Remove a handler for a guild.
     * 
     * @param guildId The guild ID
     */
    public void removeHandler(long guildId)
    {
        handlers.remove(guildId);
    }
    
    /**
     * Get the Bot instance.
     * 
     * @return The Bot instance
     */
    public Bot getBot()
    {
        return bot;
    }
    
    @Override
    public void handleVoiceServerUpdate(long guildId, String token, String endpoint)
    {
        if(!lavalinkManager.isConnected())
        {
            return;
        }
        
        // Store voice server data
        voiceServerData.put(guildId, new VoiceServerData(token, endpoint));
        
        // Try to send voice update if we have both server and state data
        Guild guild = bot.getJDA().getGuildById(guildId);
        if(guild != null)
        {
            sendVoiceUpdateIfReady(guild);
        }
    }
    
    @Override
    public void handleVoiceStateUpdate(GuildVoiceUpdateEvent event)
    {
        if(!lavalinkManager.isConnected())
        {
            return;
        }
        
        // Only handle updates for the bot itself
        if(!event.getMember().equals(event.getGuild().getSelfMember()))
        {
            return;
        }
        
        long guildId = event.getGuild().getIdLong();
        
        // Get session ID from guild's self member voice state
        String sessionId = event.getGuild().getSelfMember().getVoiceState().getSessionId();
        if(sessionId != null)
        {
            voiceSessionIds.put(guildId, sessionId);
        }
        else
        {
            // Bot disconnected
            voiceSessionIds.remove(guildId);
            voiceServerData.remove(guildId);
            
            // Destroy player on Lavalink
            lavalinkManager.destroyPlayer(guildId);
            return;
        }
        
        // Try to send voice update if we have both server and state data
        sendVoiceUpdateIfReady(event.getGuild());
    }
    
    /**
     * Send voice update to Lavalink if we have both server and state data.
     * 
     * @param guild The guild
     */
    private void sendVoiceUpdateIfReady(Guild guild)
    {
        long guildId = guild.getIdLong();
        
        VoiceServerData serverData = voiceServerData.get(guildId);
        String sessionId = voiceSessionIds.get(guildId);
        
        if(serverData == null || sessionId == null)
        {
            // Don't have both pieces of data yet
            return;
        }
        
        // Send voice update to Lavalink
        JsonObject voiceUpdate = new JsonObject();
        voiceUpdate.addProperty("op", "voiceUpdate");
        voiceUpdate.addProperty("guildId", String.valueOf(guildId));
        
        JsonObject event = new JsonObject();
        event.addProperty("token", serverData.token);
        event.addProperty("endpoint", serverData.endpoint);
        event.addProperty("sessionId", sessionId);
        voiceUpdate.add("event", event);
        
        lavalinkManager.sendWebSocketMessage(voiceUpdate);
        LOGGER.debug("Sent voice update to Lavalink for guild {}", guildId);
    }
    
    /**
     * Handle Lavalink events and dispatch them to the appropriate handler.
     * 
     * @param eventData The event data JSON object
     */
    private void handleLavalinkEvent(JsonObject eventData)
    {
        String type = eventData.get("type").getAsString();
        
        // Get guild ID from event
        long guildId = -1;
        if(eventData.has("guildId"))
        {
            try
            {
                guildId = Long.parseLong(eventData.get("guildId").getAsString());
            }
            catch(Exception e)
            {
                LOGGER.warn("Could not parse guildId from event", e);
                return;
            }
        }
        else if(eventData.has("data") && eventData.getAsJsonObject("data").has("guildId"))
        {
            try
            {
                guildId = Long.parseLong(eventData.getAsJsonObject("data").get("guildId").getAsString());
            }
            catch(Exception e)
            {
                LOGGER.warn("Could not parse guildId from event data", e);
                return;
            }
        }
        
        if(guildId == -1)
        {
            LOGGER.warn("Event missing guildId: {}", type);
            return;
        }
        
        LavalinkAudioHandler handler = handlers.get(guildId);
        if(handler == null)
        {
            LOGGER.debug("No handler for guild {} in event {}", guildId, type);
            return;
        }
        
        // Dispatch event to handler
        switch(type)
        {
            case "TrackStartEvent":
                JsonObject trackData = eventData.has("track") ? eventData.getAsJsonObject("track") : null;
                if(trackData != null)
                {
                    // Find the track in the handler's current track
                    AudioTrack currentTrack = handler.getPlayingTrack();
                    if(currentTrack != null)
                    {
                        handler.onTrackStart(currentTrack);
                    }
                }
                break;
                
            case "TrackEndEvent":
                // endReason is available but not needed for our handler
                handler.onTrackEnd();
                break;
                
            case "TrackExceptionEvent":
                String error = eventData.has("error") ? eventData.get("error").getAsString() : "Unknown error";
                AudioTrack track = handler.getPlayingTrack();
                if(track != null)
                {
                    handler.onTrackException(track, error);
                }
                break;
                
            case "playerUpdate":
                // Handle position updates
                if(eventData.has("data"))
                {
                    JsonObject data = eventData.getAsJsonObject("data");
                    if(data.has("state"))
                    {
                        JsonObject state = data.getAsJsonObject("state");
                        long position = state.has("position") ? state.get("position").getAsLong() : 0;
                        long timestamp = state.has("time") ? state.get("time").getAsLong() : System.currentTimeMillis();
                        handler.onLavalinkPlayerUpdate(position, timestamp);
                    }
                }
                break;
                
            default:
                LOGGER.debug("Unhandled Lavalink event type: {}", type);
        }
    }
}

