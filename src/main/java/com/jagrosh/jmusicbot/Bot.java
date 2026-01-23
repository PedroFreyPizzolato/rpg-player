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
package com.jagrosh.jmusicbot;

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import com.jagrosh.jmusicbot.audio.AloneInVoiceHandler;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.NowPlayingHandler;
import com.jagrosh.jmusicbot.audio.PlayerManager;
import com.jagrosh.jmusicbot.gui.GUI;
import com.jagrosh.jmusicbot.metrics.CollectionPoller;
import com.jagrosh.jmusicbot.metrics.InstanceIdManager;
import com.jagrosh.jmusicbot.metrics.MetricsCollector;
import com.jagrosh.jmusicbot.metrics.store.TelemetryStore;
import com.jagrosh.jmusicbot.playlist.PlaylistLoader;
import com.jagrosh.jmusicbot.settings.SettingsManager;
import com.jagrosh.jmusicbot.utils.InstanceLock;
import com.jagrosh.jmusicbot.utils.YoutubeOauth2TokenHandler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class Bot
{
    private final EventWaiter waiter;
    private final ScheduledExecutorService threadpool;
    private final BotConfig config;
    private final SettingsManager settings;
    private final PlayerManager players;
    private final PlaylistLoader playlists;
    private final NowPlayingHandler nowplaying;
    private final AloneInVoiceHandler aloneInVoiceHandler;
    private final YoutubeOauth2TokenHandler youTubeOauth2TokenHandler;
    private final Instant startTime;
    
    // Metrics/Telemetry components
    private final InstanceIdManager instanceIdManager;
    private final TelemetryStore telemetryStore;
    private final MetricsCollector metricsCollector;
    private final CollectionPoller collectionPoller;
    
    private boolean shuttingDown = false;
    private JDA jda;
    private GUI gui;
    
    public Bot(EventWaiter waiter, BotConfig config, SettingsManager settings)
    {
        this.waiter = waiter;
        this.config = config;
        this.settings = settings;
        this.playlists = new PlaylistLoader(config);
        this.threadpool = Executors.newSingleThreadScheduledExecutor();
        this.startTime = Instant.now();
        this.youTubeOauth2TokenHandler = new YoutubeOauth2TokenHandler();
        this.youTubeOauth2TokenHandler.init();
        this.players = new PlayerManager(this);
        // Delay init of the PlayerManager until the GUI has started
        // this.players.init();
        this.nowplaying = new NowPlayingHandler(this);
        this.nowplaying.init();
        this.aloneInVoiceHandler = new AloneInVoiceHandler(this);
        this.aloneInVoiceHandler.init();
        
        // Initialize metrics/telemetry components
        this.instanceIdManager = new InstanceIdManager();
        this.instanceIdManager.init();
        this.telemetryStore = new TelemetryStore();
        this.telemetryStore.init();
        this.metricsCollector = new MetricsCollector(telemetryStore, instanceIdManager, config.isMetricsEnabled());
        this.collectionPoller = new CollectionPoller(metricsCollector, config.getMetricsManifestUrl(), config.isMetricsEnabled());
    }
    
    public BotConfig getConfig()
    {
        return config;
    }
    
    public SettingsManager getSettingsManager()
    {
        return settings;
    }
    
    public EventWaiter getWaiter()
    {
        return waiter;
    }
    
    public ScheduledExecutorService getThreadpool()
    {
        return threadpool;
    }
    
    public PlayerManager getPlayerManager()
    {
        return players;
    }
    
    public PlaylistLoader getPlaylistLoader()
    {
        return playlists;
    }
    
    public NowPlayingHandler getNowplayingHandler()
    {
        return nowplaying;
    }

    public AloneInVoiceHandler getAloneInVoiceHandler()
    {
        return aloneInVoiceHandler;
    }
    
    public JDA getJDA()
    {
        return jda;
    }
    
    public void closeAudioConnection(long guildId)
    {
        Guild guild = jda.getGuildById(guildId);
        if(guild!=null)
            threadpool.submit(() -> guild.getAudioManager().closeAudioConnection());
    }
    
    public void resetGame()
    {
        Activity game = config.getGame()==null || config.getGame().getName().equalsIgnoreCase("none") ? null : config.getGame();
        if(!Objects.equals(jda.getPresence().getActivity(), game))
            jda.getPresence().setActivity(game);
    }

    /**
     * Performs a full graceful shutdown with complete cleanup.
     * Use this for normal shutdowns (GUI close, /shutdown command).
     */
    public void shutdown()
    {
        if(shuttingDown)
            return;
        shuttingDown = true;
        
        // Stop the telemetry poller
        collectionPoller.stop();
        
        // Clean up audio connections first (before shutting down thread pool, as these may trigger events that use it)
        if(jda != null && jda.getStatus() != JDA.Status.SHUTTING_DOWN)
        {
            jda.getGuilds().stream().forEach(g -> 
            {
                AudioHandler ah = (AudioHandler)g.getAudioManager().getSendingHandler();
                if(ah!=null)
                {
                    ah.stopAndClear();
                    ah.getPlayer().destroy();
                }
                g.getAudioManager().closeAudioConnection();
            });
            jda.shutdown();
        }
        
        // Shut down thread pool after audio cleanup to avoid RejectedExecutionException
        threadpool.shutdownNow();
        
        if(gui!=null)
            gui.dispose();
        InstanceLock.release();
        System.exit(0);
    }

    public void setJDA(JDA jda)
    {
        this.jda = jda;
    }
    
    public void setGUI(GUI gui)
    {
        this.gui = gui;
    }

    public YoutubeOauth2TokenHandler getYouTubeOauth2Handler() {
        return youTubeOauth2TokenHandler;
    }
    
    public Instant getStartTime() {
        return startTime;
    }

    public MetricsCollector getMetricsCollector() {
        return metricsCollector;
    }

    /**
     * Starts the metrics/telemetry system.
     * Should be called after the bot has fully initialized and connected to Discord.
     */
    public void startMetrics() {
        if (!config.isMetricsEnabled()) {
            return;
        }
        
        // Record startup event
        metricsCollector.recordStartup();
        
        // Schedule periodic snapshots (every 30 minutes)
        threadpool.scheduleAtFixedRate(() -> {
            if (jda != null) {
                int guildCount = jda.getGuilds().size();
                int activeAudioSessions = (int) jda.getGuilds().stream()
                        .filter(g -> g.getAudioManager().isConnected())
                        .count();
                metricsCollector.recordSnapshot(guildCount, activeAudioSessions);
            }
        }, 30, 30, TimeUnit.MINUTES);
        
        // Start the collection poller
        collectionPoller.start();
    }
}
