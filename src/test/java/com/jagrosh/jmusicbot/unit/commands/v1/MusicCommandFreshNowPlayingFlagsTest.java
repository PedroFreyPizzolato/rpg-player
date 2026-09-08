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
package com.jagrosh.jmusicbot.unit.commands.v1;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.commands.v1.MusicCommand;
import com.jagrosh.jmusicbot.commands.v1.dj.ForceRemoveCmd;
import com.jagrosh.jmusicbot.commands.v1.dj.ForceskipCmd;
import com.jagrosh.jmusicbot.commands.v1.dj.MoveTrackCmd;
import com.jagrosh.jmusicbot.commands.v1.dj.PauseCmd;
import com.jagrosh.jmusicbot.commands.v1.dj.PlaynextCmd;
import com.jagrosh.jmusicbot.commands.v1.dj.RepeatCmd;
import com.jagrosh.jmusicbot.commands.v1.dj.SkiptoCmd;
import com.jagrosh.jmusicbot.commands.v1.dj.StopCmd;
import com.jagrosh.jmusicbot.commands.v1.dj.VolumeCmd;
import com.jagrosh.jmusicbot.commands.v1.music.HistoryCmd;
import com.jagrosh.jmusicbot.commands.v1.music.LyricsCmd;
import com.jagrosh.jmusicbot.commands.v1.music.NowPlayingCmd;
import com.jagrosh.jmusicbot.commands.v1.music.PhaseCmd;
import com.jagrosh.jmusicbot.commands.v1.music.PlayCmd;
import com.jagrosh.jmusicbot.commands.v1.music.PlaylistsCmd;
import com.jagrosh.jmusicbot.commands.v1.music.QueueCmd;
import com.jagrosh.jmusicbot.commands.v1.music.RemoveCmd;
import com.jagrosh.jmusicbot.commands.v1.music.SCSearchCmd;
import com.jagrosh.jmusicbot.commands.v1.music.SearchCmd;
import com.jagrosh.jmusicbot.commands.v1.music.SeekCmd;
import com.jagrosh.jmusicbot.commands.v1.music.ShuffleCmd;
import com.jagrosh.jmusicbot.commands.v1.music.SkipCmd;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Locks down exactly which $-prefixed commands force a fresh now-playing message: the
 * ones that change what's currently playing, not the ones that only display information.
 */
class MusicCommandFreshNowPlayingFlagsTest
{
    private Bot bot;

    @BeforeEach
    void setUp()
    {
        bot = mock(Bot.class);
        BotConfig config = mock(BotConfig.class);
        when(bot.getConfig()).thenReturn(config);
        when(config.getAliases(any())).thenReturn(new String[0]);
        when(config.getLoading()).thenReturn(":loading:");
        when(config.getSearching()).thenReturn(":searching:");
        when(bot.getMusicService()).thenReturn(mock(com.jagrosh.jmusicbot.service.MusicService.class));
        when(bot.getPhaseService()).thenReturn(mock(com.jagrosh.jmusicbot.service.PhaseService.class));
        when(bot.getWaiter()).thenReturn(mock(com.jagrosh.jdautilities.commons.waiter.EventWaiter.class));
    }

    static Stream<Arguments> commandsThatOptIn()
    {
        return Stream.of(
                Arguments.of("PlayCmd", (java.util.function.Function<Bot, MusicCommand>) PlayCmd::new),
                Arguments.of("SkipCmd", (java.util.function.Function<Bot, MusicCommand>) SkipCmd::new),
                Arguments.of("ForceskipCmd", (java.util.function.Function<Bot, MusicCommand>) ForceskipCmd::new),
                Arguments.of("SkiptoCmd", (java.util.function.Function<Bot, MusicCommand>) SkiptoCmd::new),
                Arguments.of("PauseCmd", (java.util.function.Function<Bot, MusicCommand>) PauseCmd::new),
                Arguments.of("StopCmd", (java.util.function.Function<Bot, MusicCommand>) StopCmd::new),
                Arguments.of("VolumeCmd", (java.util.function.Function<Bot, MusicCommand>) VolumeCmd::new),
                Arguments.of("SeekCmd", (java.util.function.Function<Bot, MusicCommand>) SeekCmd::new),
                Arguments.of("ShuffleCmd", (java.util.function.Function<Bot, MusicCommand>) ShuffleCmd::new),
                Arguments.of("RemoveCmd", (java.util.function.Function<Bot, MusicCommand>) RemoveCmd::new),
                Arguments.of("ForceRemoveCmd", (java.util.function.Function<Bot, MusicCommand>) ForceRemoveCmd::new),
                Arguments.of("MoveTrackCmd", (java.util.function.Function<Bot, MusicCommand>) MoveTrackCmd::new),
                Arguments.of("PlaynextCmd", (java.util.function.Function<Bot, MusicCommand>) PlaynextCmd::new),
                Arguments.of("RepeatCmd", (java.util.function.Function<Bot, MusicCommand>) RepeatCmd::new)
        );
    }

    static Stream<Arguments> commandsThatDoNotOptIn()
    {
        return Stream.of(
                Arguments.of("LyricsCmd", (java.util.function.Function<Bot, MusicCommand>) LyricsCmd::new),
                Arguments.of("SearchCmd", (java.util.function.Function<Bot, MusicCommand>) SearchCmd::new),
                Arguments.of("SCSearchCmd", (java.util.function.Function<Bot, MusicCommand>) SCSearchCmd::new),
                Arguments.of("PlaylistsCmd", (java.util.function.Function<Bot, MusicCommand>) PlaylistsCmd::new),
                Arguments.of("HistoryCmd", (java.util.function.Function<Bot, MusicCommand>) HistoryCmd::new),
                Arguments.of("QueueCmd", (java.util.function.Function<Bot, MusicCommand>) QueueCmd::new),
                Arguments.of("NowPlayingCmd", (java.util.function.Function<Bot, MusicCommand>) NowPlayingCmd::new),
                Arguments.of("PhaseCmd", (java.util.function.Function<Bot, MusicCommand>) PhaseCmd::new)
        );
    }

    @ParameterizedTest(name = "{0} forces a fresh now-playing message")
    @MethodSource("commandsThatOptIn")
    void commandOptsIn(String label, java.util.function.Function<Bot, MusicCommand> factory) throws Exception
    {
        assertEquals(true, readFlag(factory.apply(bot)), label + " should force a fresh now-playing message");
    }

    @ParameterizedTest(name = "{0} does not force a fresh now-playing message")
    @MethodSource("commandsThatDoNotOptIn")
    void commandDoesNotOptIn(String label, java.util.function.Function<Bot, MusicCommand> factory) throws Exception
    {
        assertEquals(false, readFlag(factory.apply(bot)), label + " should not force a fresh now-playing message");
    }

    @Test
    void playlistSubcommandOptsIn() throws Exception
    {
        PlayCmd playCmd = new PlayCmd(bot);
        PlayCmd.PlaylistCmd playlistCmd = playCmd.new PlaylistCmd(bot);

        assertEquals(true, readFlag(playlistCmd), "PlayCmd.PlaylistCmd should force a fresh now-playing message");
    }

    private static boolean readFlag(MusicCommand command) throws Exception
    {
        Field field = MusicCommand.class.getDeclaredField("forcesFreshNowPlaying");
        field.setAccessible(true);
        return field.getBoolean(command);
    }
}
