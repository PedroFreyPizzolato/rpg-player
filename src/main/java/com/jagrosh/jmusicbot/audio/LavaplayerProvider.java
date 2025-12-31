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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AudioProvider implementation using direct Lavaplayer.
 * This is the default provider that processes audio locally.
 * 
 * @author JMusicBot Contributors
 */
public class LavaplayerProvider implements AudioProvider
{
    private final static Logger LOGGER = LoggerFactory.getLogger(LavaplayerProvider.class);
    private final PlayerManager playerManager;
    private boolean initialized = false;
    
    public LavaplayerProvider(PlayerManager playerManager)
    {
        this.playerManager = playerManager;
    }
    
    @Override
    public void init()
    {
        if(!initialized)
        {
            playerManager.init();
            initialized = true;
            LOGGER.info("LavaplayerProvider initialized");
        }
    }
    
    @Override
    public AudioHandler createHandler(Guild guild)
    {
        return playerManager.setUpHandler(guild);
    }
    
    @Override
    public boolean hasHandler(Guild guild)
    {
        return playerManager.hasHandler(guild);
    }
    
    @Override
    public void loadItem(String identifier, AudioLoadResultHandler resultHandler)
    {
        playerManager.loadItem(identifier, resultHandler);
    }
    
    @Override
    public void shutdown()
    {
        // PlayerManager doesn't need explicit shutdown
        // Individual players are destroyed when guilds disconnect
        LOGGER.info("LavaplayerProvider shutdown");
    }
    
    @Override
    public boolean isAvailable()
    {
        return initialized;
    }
    
    /**
     * Get the underlying PlayerManager instance.
     * 
     * @return The PlayerManager instance
     */
    public PlayerManager getPlayerManager()
    {
        return playerManager;
    }
}

