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
package com.jagrosh.jmusicbot.commands.v2.music;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v2.MusicSlashCommand;
import com.jagrosh.jmusicbot.listener.interaction.PaginatedListComponents;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Slash command to show available playlists with interactive pagination and actions.
 */
public class PlaylistsSlashCmd extends MusicSlashCommand
{
    public static final int PLAYLISTS_PER_PAGE = 10;

    public PlaylistsSlashCmd(Bot bot)
    {
        super(bot);
        this.name = "playlists";
        this.help = "shows the available playlists";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = false;
        this.bePlaying = false;
    }

    @Override
    public void doCommand(SlashCommandEvent event)
    {
        if (!bot.getPlaylistLoader().folderExists())
            bot.getPlaylistLoader().createFolder();

        if (!bot.getPlaylistLoader().folderExists())
        {
            event.reply(event.getClient().getWarning() + " Playlists folder does not exist and could not be created!")
                    .setEphemeral(true).queue();
            return;
        }

        List<String> list = bot.getPlaylistLoader().getPlaylistNames();
        if (list == null)
        {
            event.reply(event.getClient().getError() + " Failed to load available playlists!")
                    .setEphemeral(true).queue();
        }
        else if (list.isEmpty())
        {
            event.reply(event.getClient().getWarning() + " There are no playlists in the Playlists folder!")
                    .setEphemeral(true).queue();
        }
        else
        {
            int totalPages = getTotalPages(list.size());
            int page = 1;
            long userId = event.getUser().getIdLong();
            int playlistsOnPage = getPlaylistsOnPage(page, list.size());
            Color color = event.getMember() == null ? null : event.getMember().getColor();

            MessageEmbed embed = buildPlaylistsEmbed(list, page, totalPages, 0, color);
            List<ActionRow> components = buildPlaylistsComponents(page, totalPages, playlistsOnPage, 0, userId);
            event.replyEmbeds(embed).setComponents(components).queue();
        }
    }

    /**
     * Builds the playlists embed with paginated playlist list.
     */
    public static MessageEmbed buildPlaylistsEmbed(List<String> playlists, int page, int totalPages,
                                                   int selectedIndex, Color memberColor)
    {
        int startIndex = (page - 1) * PLAYLISTS_PER_PAGE;
        int endIndex = Math.min(startIndex + PLAYLISTS_PER_PAGE, playlists.size());

        StringBuilder sb = new StringBuilder();
        sb.append("**Available playlists** *(select one below to queue or play now)*\n\n");
        for (int i = startIndex; i < endIndex; i++)
        {
            int displayNum = i + 1;
            if (selectedIndex > 0 && displayNum == selectedIndex)
            {
                sb.append("▶️ **`").append(displayNum).append(".`** `").append(playlists.get(i)).append("`\n");
            }
            else
            {
                sb.append("⬛ `").append(displayNum).append(".` `").append(playlists.get(i)).append("`\n");
            }
        }

        return new EmbedBuilder()
                .setTitle("Playlists")
                .setDescription(sb.toString())
                .addField("Entries", String.valueOf(playlists.size()), true)
                .setFooter("Page " + page + " of " + totalPages)
                .setTimestamp(Instant.now())
                .setColor(memberColor)
                .build();
    }

    /**
     * Builds interactive button rows for playlist list navigation and actions.
     * Component ID format: playlists_{action}_{page}_{selectedIndex}_{userId}
     */
    public static List<ActionRow> buildPlaylistsComponents(int page, int totalPages, int playlistsOnPage,
                                                           int selectedIndex, long userId)
    {
        List<ActionRow> rows = new ArrayList<>();
        String baseId = PaginatedListComponents.baseId("playlists", page, selectedIndex, userId);
        rows.addAll(PaginatedListComponents.buildSelectRows(baseId, page, PLAYLISTS_PER_PAGE, playlistsOnPage, selectedIndex));
        ActionRow paginationRow = PaginatedListComponents.buildPaginationRow(baseId, page, totalPages);
        if (paginationRow != null)
        {
            rows.add(paginationRow);
        }

        Button refreshBtn = Button.secondary(String.format(baseId, "refresh"), "Refresh").withEmoji(Emoji.fromUnicode("🔄"));
        if (selectedIndex > 0)
        {
            Button queueBtn = Button.secondary(String.format(baseId, "queue"), "Queue").withEmoji(Emoji.fromUnicode("➕"));
            Button playNowBtn = Button.success(String.format(baseId, "playnow"), "Play Now").withEmoji(Emoji.fromUnicode("▶️"));
            Button detailsBtn = Button.primary(String.format(baseId, "details"), "Details").withEmoji(Emoji.fromUnicode("ℹ️"));
            rows.add(ActionRow.of(queueBtn, playNowBtn, detailsBtn, refreshBtn));
        }
        else
        {
            rows.add(ActionRow.of(refreshBtn));
        }

        return rows;
    }

    public static int getPlaylistsOnPage(int page, int totalPlaylists)
    {
        int startIndex = (page - 1) * PLAYLISTS_PER_PAGE;
        return Math.min(PLAYLISTS_PER_PAGE, totalPlaylists - startIndex);
    }

    public static int getTotalPages(int totalPlaylists)
    {
        return (int) Math.ceil((double) totalPlaylists / PLAYLISTS_PER_PAGE);
    }
}
