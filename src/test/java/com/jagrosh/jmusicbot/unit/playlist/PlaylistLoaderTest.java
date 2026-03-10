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
package com.jagrosh.jmusicbot.unit.playlist;

import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.playlist.PlaylistLoader;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("PlaylistLoader Tests")
class PlaylistLoaderTest
{
    @TempDir
    Path tempDir;

    private PlaylistLoader loader;
    private AudioPlayerManager manager;

    @BeforeEach
    void setUp()
    {
        BotConfig config = mock(BotConfig.class);
        when(config.getPlaylistsFolder()).thenReturn(tempDir.toString());
        when(config.isTooLong(any(AudioTrack.class))).thenReturn(false);
        loader = new PlaylistLoader(config);
        manager = mock(AudioPlayerManager.class);
    }

    @Test
    @DisplayName("loadTracks() invokes callback only after all items complete")
    void loadTracks_invokesCallbackOnlyAfterAllItemsComplete() throws IOException
    {
        Files.writeString(tempDir.resolve("race.txt"), "url1\nurl2\nurl3\n");
        PlaylistLoader.Playlist playlist = loader.getPlaylist("race");
        assertNotNull(playlist);

        Map<String, AudioLoadResultHandler> handlersByItem = new HashMap<>();
        doAnswer(invocation ->
        {
            String item = invocation.getArgument(1);
            AudioLoadResultHandler handler = invocation.getArgument(2);
            handlersByItem.put(item, handler);
            return null;
        }).when(manager).loadItemOrdered(eq("race"), anyString(), any(AudioLoadResultHandler.class));

        AtomicInteger callbackCount = new AtomicInteger(0);
        List<AudioTrack> consumedTracks = new ArrayList<>();
        playlist.loadTracks(manager, consumedTracks::add, callbackCount::incrementAndGet);

        assertEquals(3, handlersByItem.size());

        AudioTrack track3 = mock(AudioTrack.class);
        handlersByItem.get("url3").trackLoaded(track3);
        assertEquals(0, callbackCount.get());

        AudioTrack track1 = mock(AudioTrack.class);
        handlersByItem.get("url1").trackLoaded(track1);
        assertEquals(0, callbackCount.get());

        handlersByItem.get("url2").noMatches();
        assertEquals(1, callbackCount.get());
        assertEquals(2, consumedTracks.size());
    }

    @Test
    @DisplayName("loadTracks() invokes callback immediately when playlist has no items")
    void loadTracks_invokesCallbackImmediately_whenPlaylistHasNoItems() throws IOException
    {
        Files.writeString(tempDir.resolve("empty.txt"), "\n");
        PlaylistLoader.Playlist playlist = loader.getPlaylist("empty");
        assertNotNull(playlist);

        AtomicInteger callbackCount = new AtomicInteger(0);
        playlist.loadTracks(manager, track -> {}, callbackCount::incrementAndGet);

        assertEquals(1, callbackCount.get());
        verify(manager, never()).loadItemOrdered(anyString(), anyString(), any(AudioLoadResultHandler.class));
    }
}
