/*
 * Copyright 2018 John Grosh <john.a.grosh@gmail.com>.
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

import com.jagrosh.jmusicbot.Bot;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.beam.BeamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.getyarn.GetyarnAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.nico.NicoAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.vimeo.VimeoAudioSourceManager;
import com.sedmelluq.lava.extensions.youtuberotator.YoutubeIpRotatorSetup;
import com.sedmelluq.lava.extensions.youtuberotator.planner.NanoIpRoutePlanner;
import com.sedmelluq.lava.extensions.youtuberotator.tools.ip.Ipv6Block;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import net.dv8tion.jda.api.entities.Guild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

/**
 *
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public class PlayerManager extends DefaultAudioPlayerManager
{
    private final static Logger LOG = LoggerFactory.getLogger(PlayerManager.class);
    private final Bot bot;
    
    public PlayerManager(Bot bot)
    {
        this.bot = bot;
    }

    public void init()
    {
        TransformativeAudioSourceManager.createTransforms(bot.getConfig().getTransforms()).forEach(t -> registerSourceManager(t));

        YoutubeAudioSourceManager yt = new YoutubeAudioSourceManager(true);
        yt.setPlaylistPageCount(bot.getConfig().getMaxYTPlaylistPages());

        // Setup IPv6 Rotation if a block is provided
        String ipv6Block = bot.getConfig().getIPv6Block();
        if (ipv6Block != null && !ipv6Block.isEmpty())
        {
            LOG.info("Setting up IPv6 rotation with block: {}", ipv6Block);
            try
            {
                // We use NanoIpRoutePlanner from the rotator extension
                NanoIpRoutePlanner routePlanner = new NanoIpRoutePlanner(Collections.singletonList(new Ipv6Block(ipv6Block)), true);

                // Use YoutubeIpRotatorSetup to apply the planner to the source manager
                new YoutubeIpRotatorSetup(routePlanner)
                        .forConfiguration(yt.getHttpInterfaceManager(), false)
                        .withMainDelegateFilter(yt.getContextFilter())
                        .setup();
            }
            catch (Exception ex)
            {
                LOG.error("Failed to setup IPv6 rotation: {}", ex.getMessage());
            }
        }

        registerSourceManager(yt);

        registerSourceManager(SoundCloudAudioSourceManager.createDefault());
        registerSourceManager(new BandcampAudioSourceManager());
        registerSourceManager(new VimeoAudioSourceManager());
        registerSourceManager(new TwitchStreamAudioSourceManager());
        registerSourceManager(new BeamAudioSourceManager());
        registerSourceManager(new GetyarnAudioSourceManager());
        registerSourceManager(new NicoAudioSourceManager());
        registerSourceManager(new HttpAudioSourceManager(MediaContainerRegistry.DEFAULT_REGISTRY));

        AudioSourceManagers.registerLocalSource(this);

        // Removing this since it is deprecated/gone
        //DuncteBotSources.registerAll(this, "en-US");
    }
    
    public Bot getBot()
    {
        return bot;
    }
    
    public boolean hasHandler(Guild guild)
    {
        return guild.getAudioManager().getSendingHandler()!=null;
    }
    
    public AudioHandler setUpHandler(Guild guild)
    {
        AudioHandler handler;
        if(guild.getAudioManager().getSendingHandler()==null)
        {
            AudioPlayer player = createPlayer();
            player.setVolume(bot.getSettingsManager().getSettings(guild).getVolume());
            handler = new AudioHandler(this, guild, player);
            player.addListener(handler);
            guild.getAudioManager().setSendingHandler(handler);
        }
        else
            handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
        return handler;
    }
}
