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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Lavalink playlist data to work with Lavaplayer's AudioPlaylist interface.
 * 
 * @author JMusicBot Contributors
 */
public class LavalinkAudioPlaylist implements AudioPlaylist
{
    private final String name;
    private final List<AudioTrack> tracks;
    private final String identifier;
    private final AudioTrack selectedTrack;
    
    public LavalinkAudioPlaylist(String name, JsonArray tracksArray, String identifier)
    {
        this.name = name;
        this.identifier = identifier;
        this.tracks = new ArrayList<>();
        this.selectedTrack = null;
        
        // Convert Lavalink tracks to AudioTrack objects
        for(int i = 0; i < tracksArray.size(); i++)
        {
            JsonObject trackData = tracksArray.get(i).getAsJsonObject();
            tracks.add(new LavalinkAudioTrack(trackData, identifier));
        }
    }
    
    @Override
    public String getName()
    {
        return name;
    }
    
    @Override
    public List<AudioTrack> getTracks()
    {
        return tracks;
    }
    
    @Override
    public AudioTrack getSelectedTrack()
    {
        return selectedTrack;
    }
    
    @Override
    public boolean isSearchResult()
    {
        return "Search Results".equals(name);
    }
}

