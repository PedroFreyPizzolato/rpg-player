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

import com.google.gson.JsonObject;
import com.jagrosh.jmusicbot.audio.lavalink.LavalinkManager;
import com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.jagrosh.jmusicbot.settings.Settings;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AudioHandler implementation for Lavalink.
 * Manages queue and playback control for a guild using Lavalink.
 * Extends AudioHandler for compatibility but uses Lavalink for playback.
 * 
 * @author JMusicBot Contributors
 */
public class LavalinkAudioHandler extends AudioHandler
{
    private final static Logger LOGGER = LoggerFactory.getLogger(LavalinkAudioHandler.class);
    
    private final LavalinkProvider provider;
    private final LavalinkManager lavalinkManager;
    private final long guildId;
    
    // Player state (override parent's audioPlayer-based state)
    private final AtomicReference<AudioTrack> currentTrack = new AtomicReference<>();
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicInteger volume = new AtomicInteger(100);
    private final List<AudioTrack> defaultQueue = new LinkedList<>();
    
    public LavalinkAudioHandler(LavalinkProvider provider, Guild guild, LavalinkManager lavalinkManager)
    {
        // Call parent with null manager and player - we'll override all methods
        super(null, guild, null);
        this.provider = provider;
        this.lavalinkManager = lavalinkManager;
        this.guildId = guild.getIdLong();
        
        // Initialize queue
        Settings settings = provider.getBot().getSettingsManager().getSettings(guildId);
        this.setQueueType(settings.getQueueType());
        
        // Set initial volume
        this.volume.set(settings.getVolume());
    }
    
    @Override
    public int addTrackToFront(QueuedTrack qtrack)
    {
        if(currentTrack.get() == null)
        {
            playTrack(qtrack.getTrack());
            return -1;
        }
        else
        {
            getQueue().addAt(0, qtrack);
            return 0;
        }
    }
    
    @Override
    public int addTrack(QueuedTrack qtrack)
    {
        if(currentTrack.get() == null)
        {
            playTrack(qtrack.getTrack());
            return -1;
        }
        else
        {
            return getQueue().add(qtrack);
        }
    }
    
    @Override
    public void stopAndClear()
    {
        getQueue().clear();
        stopTrack();
    }
    
    @Override
    public boolean isMusicPlaying(JDA jda)
    {
        Guild guild = jda.getGuildById(guildId);
        if(guild == null)
            return false;
        
        boolean isBotConnectedToVoice = guild.getSelfMember().getVoiceState().getChannel() != null;
        boolean isAudioPlaying = currentTrack.get() != null && !paused.get();
        return isBotConnectedToVoice && isAudioPlaying;
    }
    
    @Override
    public AudioPlayer getPlayer()
    {
        // Return null since we don't use Lavaplayer's AudioPlayer
        // Some code might check for null, so this is safe
        return null;
    }
    
    @Override
    public RequestMetadata getRequestMetadata()
    {
        AudioTrack track = currentTrack.get();
        if(track == null)
            return RequestMetadata.EMPTY;
        
        RequestMetadata rm = track.getUserData(RequestMetadata.class);
        return rm == null ? RequestMetadata.EMPTY : rm;
    }
    
    @Override
    public NowPlayingInfo getNowPlayingInfo(JDA jda)
    {
        return new NowPlayingInfo(
            currentTrack.get(),
            jda.getGuildById(guildId),
            paused.get(),
            volume.get()
        );
    }
    
    @Override
    public String getStatusEmoji()
    {
        return paused.get() ? PAUSE_EMOJI : PLAY_EMOJI;
    }
    
    /**
     * Play a track using Lavalink.
     * 
     * @param track The track to play
     */
    public void playTrack(AudioTrack track)
    {
        if(!(track instanceof com.jagrosh.jmusicbot.audio.lavalink.LavalinkAudioTrack))
        {
            LOGGER.warn("Attempted to play non-Lavalink track with LavalinkAudioHandler");
            return;
        }
        
        com.jagrosh.jmusicbot.audio.lavalink.LavalinkAudioTrack lavalinkTrack = 
            (com.jagrosh.jmusicbot.audio.lavalink.LavalinkAudioTrack) track;
        
        currentTrack.set(track);
        paused.set(false);
        
        // Send play command to Lavalink
        // Lavalink v4 uses "track" instead of "encodedTrack"
        JsonObject playerData = new JsonObject();
        playerData.addProperty("track", lavalinkTrack.getIdentifier());
        playerData.addProperty("position", 0);
        playerData.addProperty("volume", volume.get());
        playerData.addProperty("paused", false);
        
        lavalinkManager.updatePlayer(guildId, playerData);
        
        // Notify now playing handler
        provider.getBot().getNowplayingHandler().onTrackUpdate(guildId, track);
    }
    
    /**
     * Stop the current track.
     */
    public void stopTrack()
    {
        currentTrack.set(null);
        paused.set(false);
        
        JsonObject playerData = new JsonObject();
        playerData.addProperty("track", (String) null);
        
        lavalinkManager.updatePlayer(guildId, playerData);
        
        provider.getBot().getNowplayingHandler().onTrackUpdate(guildId, null);
    }
    
    /**
     * Pause or resume playback.
     * 
     * @param paused true to pause, false to resume
     */
    public void setPaused(boolean paused)
    {
        this.paused.set(paused);
        
        JsonObject playerData = new JsonObject();
        playerData.addProperty("paused", paused);
        
        lavalinkManager.updatePlayer(guildId, playerData);
    }
    
    /**
     * Check if playback is paused.
     * 
     * @return true if paused
     */
    public boolean isPaused()
    {
        return paused.get();
    }
    
    /**
     * Set the volume.
     * 
     * @param volume Volume (0-1000, where 100 is normal)
     */
    public void setVolume(int volume)
    {
        this.volume.set(volume);
        
        JsonObject playerData = new JsonObject();
        playerData.addProperty("volume", volume);
        
        lavalinkManager.updatePlayer(guildId, playerData);
    }
    
    /**
     * Get the current volume.
     * 
     * @return Volume (0-1000)
     */
    public int getVolume()
    {
        return volume.get();
    }
    
    /**
     * Get the currently playing track.
     * 
     * @return The current track, or null if none
     */
    public AudioTrack getPlayingTrack()
    {
        return currentTrack.get();
    }
    
    @Override
    public boolean playFromDefault()
    {
        if(!defaultQueue.isEmpty())
        {
            playTrack(defaultQueue.remove(0));
            return true;
        }
        
        Settings settings = provider.getBot().getSettingsManager().getSettings(guildId);
        if(settings == null || settings.getDefaultPlaylist() == null)
            return false;
        
        Playlist pl = provider.getBot().getPlaylistLoader().getPlaylist(settings.getDefaultPlaylist());
        if(pl == null || pl.getItems().isEmpty())
            return false;
        
        pl.loadTracks(provider.getBot().getPlayerManager(), (at) -> {
            if(currentTrack.get() == null)
                playTrack(at);
            else
                defaultQueue.add(at);
        }, () -> {
            if(pl.getTracks().isEmpty() && !provider.getBot().getConfig().getStay())
                provider.getBot().closeAudioConnection(guildId);
        });
        
        return true;
    }
    
    /**
     * Handle track end event from Lavalink.
     * This should be called when Lavalink sends a TrackEndEvent.
     */
    public void onTrackEnd()
    {
        AudioTrack track = currentTrack.get();
        if(track == null)
            return;
        
        RepeatMode repeatMode = provider.getBot().getSettingsManager().getSettings(guildId).getRepeatMode();
        
        // Handle repeat mode
        if(repeatMode != RepeatMode.OFF)
        {
            QueuedTrack clone = new QueuedTrack(track.makeClone(), track.getUserData(RequestMetadata.class));
            if(repeatMode == RepeatMode.ALL)
                getQueue().add(clone);
            else
                getQueue().addAt(0, clone);
        }
        
        // Play next track or default playlist
        if(getQueue().isEmpty())
        {
            if(!playFromDefault())
            {
                provider.getBot().getNowplayingHandler().onTrackUpdate(guildId, null);
                if(!provider.getBot().getConfig().getStay())
                    provider.getBot().closeAudioConnection(guildId);
            }
        }
        else
        {
            QueuedTrack qt = getQueue().pull();
            playTrack(qt.getTrack());
        }
        
        currentTrack.set(null);
    }
    
    /**
     * Handle track start event from Lavalink.
     * This should be called when Lavalink sends a TrackStartEvent.
     */
    public void onTrackStart(AudioTrack track)
    {
        getVotes().clear();
        provider.getBot().getNowplayingHandler().onTrackUpdate(guildId, track);
    }
    
    /**
     * Handle track exception from Lavalink.
     * This should be called when Lavalink sends a TrackExceptionEvent.
     */
    public void onTrackException(AudioTrack track, String error)
    {
        LOGGER.error("Track {} failed to play: {}", track.getIdentifier(), error);
        onTrackEnd(); // Move to next track
    }
    
    /**
     * Handle player update from Lavalink (position updates).
     * 
     * @param position The current track position in milliseconds
     * @param timestamp The timestamp when this position was recorded
     */
    public void onLavalinkPlayerUpdate(long position, long timestamp)
    {
        AudioTrack track = currentTrack.get();
        if(track != null)
        {
            track.setPosition(position);
        }
    }
}

