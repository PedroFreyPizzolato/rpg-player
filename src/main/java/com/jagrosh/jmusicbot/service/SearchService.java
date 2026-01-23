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
package com.jagrosh.jmusicbot.service;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.utils.TimeUtil;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.List;

/**
 * Service for search-related operations.
 * Uses PlayerService for shared track operations (validation, queue addition).
 */
public class SearchService
{
    private final Bot bot;

    public SearchService(Bot bot)
    {
        this.bot = bot;
    }

    /**
     * Performs a search and handles the results.
     *
     * @param guild        The guild
     * @param member       The member requesting search
     * @param query        The search query
     * @param searchPrefix The search prefix (e.g., "ytsearch:" or "scsearch:")
     * @param channel      The text channel for request metadata
     * @param callback     The callback for handling results
     */
    public void search(Guild guild, Member member, String query, String searchPrefix,
                       TextChannel channel, SearchCallback callback)
    {
        if (query == null || query.isEmpty())
        {
            callback.onError("Please include a query.");
            return;
        }

        bot.getPlayerManager().loadItemOrdered(guild, searchPrefix + query,
                new SearchResultHandler(guild, member, query, channel, callback));
    }

    /**
     * Formats search result choices for display.
     *
     * @param tracks The tracks from search results
     * @param limit  Maximum number of choices to return
     * @return Array of formatted choice strings
     */
    public String[] formatSearchChoices(List<AudioTrack> tracks, int limit)
    {
        int count = Math.min(limit, tracks.size());
        String[] choices = new String[count];
        for (int i = 0; i < count; i++)
        {
            AudioTrack track = tracks.get(i);
            choices[i] = "`[" + TimeUtil.formatTime(track.getDuration()) + "]` [**"
                    + track.getInfo().title + "**](" + track.getInfo().uri + ")";
        }
        return choices;
    }

    /**
     * Callback interface for search results.
     */
    public interface SearchCallback
    {
        /**
         * Called when a single track is loaded.
         */
        void onTrackLoaded(AudioTrack track, int queuePosition, String formattedMessage);

        /**
         * Called when search results (playlist) are loaded.
         */
        void onSearchResults(AudioPlaylist playlist, String[] formattedChoices);

        /**
         * Called when no matches are found.
         */
        void onNoMatches(String query);

        /**
         * Called when loading fails.
         */
        void onLoadFailed(String errorMessage);

        /**
         * Called for general errors.
         */
        void onError(String message);
    }

    private class SearchResultHandler implements AudioLoadResultHandler
    {
        private final Guild guild;
        private final Member member;
        private final String query;
        private final TextChannel channel;
        private final SearchCallback callback;

        private SearchResultHandler(Guild guild, Member member, String query,
                                    TextChannel channel, SearchCallback callback)
        {
            this.guild = guild;
            this.member = member;
            this.query = query;
            this.channel = channel;
            this.callback = callback;
        }

        @Override
        public void trackLoaded(AudioTrack track)
        {
            MusicService musicService = bot.getMusicService();

            // Use shared track operations from MusicService
            MusicService.TrackAddResult result = musicService.addTrackToQueue(guild, member, track, query, channel);
            if (result == null)
            {
                callback.onLoadFailed(musicService.formatTooLongError(track));
                return;
            }

            callback.onTrackLoaded(track, result.position, result.formattedMessage);
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist)
        {
            String[] choices = formatSearchChoices(playlist.getTracks(), 4);
            callback.onSearchResults(playlist, choices);
        }

        @Override
        public void noMatches()
        {
            callback.onNoMatches(query);
        }

        @Override
        public void loadFailed(FriendlyException throwable)
        {
            if (throwable.severity == Severity.COMMON)
            {
                callback.onLoadFailed("Error loading: " + throwable.getMessage());
            }
            else
            {
                callback.onLoadFailed("Error loading track.");
            }
        }
    }
}
