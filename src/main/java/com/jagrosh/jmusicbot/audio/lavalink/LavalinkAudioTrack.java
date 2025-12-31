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
package com.jagrosh.jmusicbot.audio.lavalink;

import com.google.gson.JsonObject;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.TrackMarker;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Wrapper for Lavalink track data to work with Lavaplayer's AudioTrack interface.
 * 
 * @author JMusicBot Contributors
 */
public class LavalinkAudioTrack implements AudioTrack
{
    private final JsonObject trackData;
    private final String identifier;
    private final AudioTrackInfo info;
    private final AtomicLong position = new AtomicLong(0);
    private final AtomicReference<Object> userData = new AtomicReference<>();
    private final AtomicReference<TrackMarker> activeMarker = new AtomicReference<>();
    
    public LavalinkAudioTrack(JsonObject trackData, String identifier)
    {
        this.trackData = trackData;
        this.identifier = identifier;
        
        // Extract track info from Lavalink data
        JsonObject infoData = trackData.getAsJsonObject("info");
        this.info = new AudioTrackInfo(
                infoData.get("title").getAsString(),
                infoData.get("author").getAsString(),
                infoData.get("length").getAsLong(),
                infoData.get("identifier").getAsString(),
                infoData.get("isStream").getAsBoolean(),
                infoData.has("uri") ? infoData.get("uri").getAsString() : null
        );
    }
    
    @Override
    public AudioTrackInfo getInfo()
    {
        return info;
    }
    
    @Override
    public String getIdentifier()
    {
        return trackData.get("encoded").getAsString(); // Lavalink encoded track string
    }
    
    @Override
    public AudioTrack makeClone()
    {
        return new LavalinkAudioTrack(trackData, identifier);
    }
    
    @Override
    public Object getUserData()
    {
        return userData.get();
    }
    
    @Override
    public void setUserData(Object userData)
    {
        this.userData.set(userData);
    }
    
    @Override
    public <T> T getUserData(Class<T> klass)
    {
        Object data = userData.get();
        return klass.isInstance(data) ? klass.cast(data) : null;
    }
    
    @Override
    public long getDuration()
    {
        return info.length;
    }
    
    @Override
    public long getPosition()
    {
        return position.get();
    }
    
    @Override
    public void setPosition(long position)
    {
        this.position.set(position);
    }
    
    @Override
    public boolean isSeekable()
    {
        return !info.isStream;
    }
    
    @Override
    public AudioSourceManager getSourceManager()
    {
        return null; // Lavalink tracks don't have a source manager
    }
    
    @Override
    public com.sedmelluq.discord.lavaplayer.track.AudioTrackState getState()
    {
        // State is managed by Lavalink, return INACTIVE as default
        return com.sedmelluq.discord.lavaplayer.track.AudioTrackState.INACTIVE;
    }
    
    @Override
    public void stop()
    {
        // Stop is handled by Lavalink
    }
    
    @Override
    public void setMarker(TrackMarker marker)
    {
        activeMarker.set(marker);
    }
    
    @Override
    public void addMarker(TrackMarker marker)
    {
        activeMarker.set(marker);
    }
    
    @Override
    public void removeMarker(TrackMarker marker)
    {
        activeMarker.compareAndSet(marker, null);
    }
    
    /**
     * Get the raw Lavalink track data.
     * 
     * @return The JSON track data
     */
    public JsonObject getTrackData()
    {
        return trackData;
    }
    
    /**
     * Get the original identifier used to load this track.
     * 
     * @return The identifier
     */
    public String getOriginalIdentifier()
    {
        return identifier;
    }
}

