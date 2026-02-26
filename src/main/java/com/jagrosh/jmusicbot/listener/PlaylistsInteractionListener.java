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
package com.jagrosh.jmusicbot.listener;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v2.music.PlaylistsSlashCmd;
import com.jagrosh.jmusicbot.listener.interaction.ComponentIdParsers;
import com.jagrosh.jmusicbot.listener.interaction.InteractionGuards;
import com.jagrosh.jmusicbot.listener.interaction.OutputAdapters;
import com.jagrosh.jmusicbot.service.MusicService;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.List;
import java.util.Optional;

/**
 * Handles playlists embed button interactions (playlists_*).
 */
public class PlaylistsInteractionListener extends ListenerAdapter
{
    private final Bot bot;

    public PlaylistsInteractionListener(Bot bot)
    {
        this.bot = bot;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event)
    {
        if (!event.getComponentId().startsWith("playlists_"))
        {
            return;
        }
        handlePlaylistsButton(event);
    }

    private void handlePlaylistsButton(ButtonInteractionEvent event)
    {
        if (!InteractionGuards.requireGuildAndMember(event))
        {
            return;
        }

        Optional<ComponentIdParsers.PaginatedButtonId> parsed = ComponentIdParsers.parsePlaylistsButtonId(event.getComponentId());
        if (parsed.isEmpty())
        {
            event.reply("Invalid button state.").setEphemeral(true).queue();
            return;
        }
        ComponentIdParsers.PaginatedButtonId id = parsed.get();
        if (event.getUser().getIdLong() != id.userId())
        {
            event.reply("Only the user who ran the command can use these buttons!").setEphemeral(true).queue();
            return;
        }

        List<String> playlists = loadPlaylistNames();
        if (playlists == null || playlists.isEmpty())
        {
            event.editMessage("There are no playlists in the Playlists folder!").setEmbeds().setComponents().queue();
            return;
        }

        int page = id.page();
        int selectedIndex = id.selectedTrack();
        long userId = id.userId();
        String action = id.action();

        int totalPages = PlaylistsSlashCmd.getTotalPages(playlists.size());
        page = Math.max(1, Math.min(page, totalPages));

        if (action.startsWith("select"))
        {
            int playlistIndexOnPage = Integer.parseInt(action.substring(6));
            int newSelectedIndex = (page - 1) * PlaylistsSlashCmd.PLAYLISTS_PER_PAGE + playlistIndexOnPage;
            if (newSelectedIndex == selectedIndex)
            {
                newSelectedIndex = 0;
            }
            if (newSelectedIndex > playlists.size())
            {
                event.reply("That playlist doesn't exist!").setEphemeral(true).queue();
                return;
            }
            updatePlaylistsEmbed(event, playlists, page, totalPages, newSelectedIndex, userId);
            return;
        }

        if (action.equals("prev"))
        {
            int newPage = Math.max(1, page - 1);
            updatePlaylistsEmbed(event, playlists, newPage, totalPages, 0, userId);
            return;
        }
        if (action.equals("next"))
        {
            int newPage = Math.min(totalPages, page + 1);
            updatePlaylistsEmbed(event, playlists, newPage, totalPages, 0, userId);
            return;
        }
        if (action.equals("refresh"))
        {
            int safePage = Math.min(page, totalPages);
            updatePlaylistsEmbed(event, playlists, safePage, totalPages, 0, userId);
            return;
        }

        if (selectedIndex <= 0 || selectedIndex > playlists.size())
        {
            event.reply("No playlist selected!").setEphemeral(true).queue();
            return;
        }

        String selectedPlaylist = playlists.get(selectedIndex - 1);
        MusicService musicService = bot.getMusicService();
        if (action.equals("details"))
        {
            MusicService.PlaylistDetailsInfo details = musicService.getPlaylistDetails(selectedPlaylist);
            if (details == null)
            {
                event.reply("Playlist no longer exists. Click Refresh.").setEphemeral(true).queue();
                return;
            }

            StringBuilder sb = new StringBuilder("**Playlist:** `")
                    .append(details.playlistName)
                    .append("`\n**Entries:** ")
                    .append(details.totalItems);

            if (!details.previewItems.isEmpty())
            {
                sb.append("\n\n**Preview:**");
                for (int i = 0; i < details.previewItems.size(); i++)
                {
                    sb.append("\n`").append(i + 1).append(".` ").append(details.previewItems.get(i));
                }
                if (details.hasMore)
                {
                    sb.append("\n...");
                }
            }
            event.reply(sb.toString()).setEphemeral(true).queue();
            return;
        }

        if (!InteractionGuards.ensureBotInUserVoiceChannel(event, bot))
        {
            return;
        }

        TextChannel channel = event.getChannel().asTextChannel();
        MusicService.OutputAdapter output = OutputAdapters.forPlaylistsReply(event);
        if (action.equals("queue"))
        {
            musicService.queuePlaylist(event.getGuild(), event.getMember(), selectedPlaylist, channel, output);
            updatePlaylistsEmbed(event, playlists, page, totalPages, 0, userId);
            return;
        }
        if (action.equals("playnow"))
        {
            musicService.playPlaylistNow(event.getGuild(), event.getMember(), selectedPlaylist, channel, output);
            updatePlaylistsEmbed(event, playlists, page, totalPages, 0, userId);
        }
    }

    private List<String> loadPlaylistNames()
    {
        if (!bot.getPlaylistLoader().folderExists())
        {
            bot.getPlaylistLoader().createFolder();
        }
        if (!bot.getPlaylistLoader().folderExists())
        {
            return null;
        }
        return bot.getPlaylistLoader().getPlaylistNames();
    }

    private void updatePlaylistsEmbed(ButtonInteractionEvent event, List<String> playlists,
                                      int page, int totalPages, int selectedIndex, long userId)
    {
        int playlistsOnPage = PlaylistsSlashCmd.getPlaylistsOnPage(page, playlists.size());
        MessageEmbed embed = PlaylistsSlashCmd.buildPlaylistsEmbed(playlists, page, totalPages, selectedIndex,
                event.getMember().getColor());
        List<ActionRow> components = PlaylistsSlashCmd.buildPlaylistsComponents(page, totalPages, playlistsOnPage, selectedIndex, userId);
        event.editMessageEmbeds(embed).setComponents(components).queue();
    }
}
