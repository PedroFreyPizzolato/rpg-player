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
package com.jagrosh.jmusicbot.unit;

import com.jagrosh.jmusicbot.listener.PlaylistsInteractionListener;
import com.jagrosh.jmusicbot.service.MusicService;
import com.jagrosh.jmusicbot.testutil.listener.ListenerTestFixture;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("PlaylistsInteractionListener Tests")
class PlaylistsInteractionListenerTest
{
    private ListenerTestFixture fixture;
    private PlaylistsInteractionListener listener;

    @BeforeEach
    void setUp()
    {
        fixture = ListenerTestFixture.create();
        listener = new PlaylistsInteractionListener(fixture.getBot());
        when(fixture.getButtonInteractionEvent().getUser()).thenReturn(fixture.getUser());
    }

    @Test
    @DisplayName("onButtonInteraction() handles playlists_ button with invalid format and replies error")
    void onButtonInteraction_playlistsInvalidFormat_repliesError()
    {
        fixture.withButtonId("playlists_ab");

        listener.onButtonInteraction(fixture.getButtonInteractionEvent());

        verify(fixture.getButtonInteractionEvent()).reply(argThat((String s) -> s.contains("Invalid button state")));
        verify(fixture.getReplyAction()).setEphemeral(true);
        verify(fixture.getMusicService(), never()).queuePlaylist(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("onButtonInteraction() blocks users other than command invoker")
    void onButtonInteraction_wrongUser_repliesError()
    {
        fixture.withButtonId("playlists_prev_1_0_999");

        listener.onButtonInteraction(fixture.getButtonInteractionEvent());

        verify(fixture.getButtonInteractionEvent()).reply(argThat((String s) -> s.contains("Only the user who ran the command")));
        verify(fixture.getReplyAction()).setEphemeral(true);
        verify(fixture.getMusicService(), never()).queuePlaylist(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("onButtonInteraction() queue action dispatches to MusicService")
    void onButtonInteraction_queue_dispatchesToMusicService()
    {
        fixture.withButtonId("playlists_queue_1_1_" + fixture.getUser().getIdLong())
                .withMemberInVoiceChannel();

        when(fixture.getMusicService().getAvailablePlaylistNames())
                .thenReturn(MusicService.PlaylistNamesInfo.success(List.of("favorite")));

        MessageChannelUnion channelUnion = mock(MessageChannelUnion.class);
        when(channelUnion.asTextChannel()).thenReturn(fixture.getTextChannel());
        when(fixture.getButtonInteractionEvent().getChannel()).thenReturn(channelUnion);

        MessageEditCallbackAction editAction = mock(MessageEditCallbackAction.class);
        when(fixture.getButtonInteractionEvent().editMessageEmbeds(any(MessageEmbed[].class))).thenReturn(editAction);
        when(editAction.setComponents(anyList())).thenReturn(editAction);
        doNothing().when(editAction).queue();

        listener.onButtonInteraction(fixture.getButtonInteractionEvent());

        verify(fixture.getMusicService()).queuePlaylist(
                eq(fixture.getGuild()),
                eq(fixture.getMember()),
                eq("favorite"),
                eq(fixture.getTextChannel()),
                any(MusicService.OutputAdapter.class)
        );
    }

    @Test
    @DisplayName("onButtonInteraction() rejects select action not present on current page")
    void onButtonInteraction_selectOutsidePage_repliesError()
    {
        fixture.withButtonId("playlists_select11_1_0_" + fixture.getUser().getIdLong());
        when(fixture.getMusicService().getAvailablePlaylistNames()).thenReturn(MusicService.PlaylistNamesInfo.success(List.of(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"
        )));

        listener.onButtonInteraction(fixture.getButtonInteractionEvent());

        verify(fixture.getButtonInteractionEvent()).reply(argThat((String s) -> s.contains("isn't on this page")));
        verify(fixture.getReplyAction()).setEphemeral(true);
    }
}
