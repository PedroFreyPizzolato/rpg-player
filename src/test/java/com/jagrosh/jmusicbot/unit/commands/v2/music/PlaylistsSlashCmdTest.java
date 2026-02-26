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
package com.jagrosh.jmusicbot.unit.commands.v2.music;

import com.jagrosh.jmusicbot.commands.v2.music.PlaylistsSlashCmd;
import com.jagrosh.jmusicbot.playlist.PlaylistLoader;
import com.jagrosh.jmusicbot.testutil.commands.SlashCommandTestFixture;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaylistsSlashCmdTest
{
    private SlashCommandTestFixture fixture;
    private PlaylistsSlashCmd command;
    private PlaylistLoader playlistLoader;

    @BeforeEach
    void setUp()
    {
        fixture = SlashCommandTestFixture.create();
        command = new PlaylistsSlashCmd(fixture.getBot());
        playlistLoader = mock(PlaylistLoader.class);
        when(fixture.getBot().getPlaylistLoader()).thenReturn(playlistLoader);
    }

    @Test
    void doCommand_folderMissingAndCreateFails_repliesEphemeralWarning()
    {
        when(playlistLoader.folderExists()).thenReturn(false);

        command.doCommand(fixture.getEvent());

        verify(playlistLoader).createFolder();
        verify(fixture.getEvent()).reply(any(String.class));
        verify(fixture.getReplyAction()).setEphemeral(true);
    }

    @Test
    void doCommand_emptyPlaylists_repliesEphemeralWarning()
    {
        when(playlistLoader.folderExists()).thenReturn(true);
        when(playlistLoader.getPlaylistNames()).thenReturn(List.of());

        command.doCommand(fixture.getEvent());

        verify(fixture.getEvent()).reply(any(String.class));
        verify(fixture.getReplyAction()).setEphemeral(true);
    }

    @Test
    void doCommand_hasPlaylists_repliesEmbedAndComponents()
    {
        when(playlistLoader.folderExists()).thenReturn(true);
        when(playlistLoader.getPlaylistNames()).thenReturn(List.of("favorite", "night_drive"));

        command.doCommand(fixture.getEvent());

        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(fixture.getEvent()).replyEmbeds(embedCaptor.capture());
        assertEquals("Playlists", embedCaptor.getValue().getTitle());
        assertTrue(embedCaptor.getValue().getDescription().contains("favorite"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ActionRow>> componentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(fixture.getReplyAction()).setComponents(componentsCaptor.capture());
        assertFalse(componentsCaptor.getValue().isEmpty());
        assertEquals(2, componentsCaptor.getValue().size());
    }

    @Test
    void paginationHelpers_computeExpectedValues()
    {
        assertEquals(10, PlaylistsSlashCmd.getPlaylistsOnPage(1, 23));
        assertEquals(3, PlaylistsSlashCmd.getPlaylistsOnPage(3, 23));
        assertEquals(3, PlaylistsSlashCmd.getTotalPages(23));
    }

    @Test
    void buildPlaylistsComponents_sparseButtonsForPartialPage()
    {
        List<ActionRow> rows = PlaylistsSlashCmd.buildPlaylistsComponents(1, 1, 3, 0, 42L);
        assertEquals(2, rows.size());
        ActionRow selectRow = rows.get(0);
        assertEquals(3, selectRow.getComponents().size());
        assertEquals("1", ((Button) selectRow.getComponents().get(0)).getLabel());
        assertEquals("3", ((Button) selectRow.getComponents().get(2)).getLabel());
    }

    @Test
    void buildPlaylistsComponents_pageTwoUsesAbsoluteLabels()
    {
        List<ActionRow> rows = PlaylistsSlashCmd.buildPlaylistsComponents(2, 3, 10, 0, 42L);
        assertEquals(4, rows.size());
        ActionRow row1 = rows.get(0);
        ActionRow row2 = rows.get(1);
        assertEquals("11", ((Button) row1.getComponents().get(0)).getLabel());
        assertEquals("20", ((Button) row2.getComponents().get(4)).getLabel());
    }
}
