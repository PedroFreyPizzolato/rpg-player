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

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.NowPlayingHandler;
import com.jagrosh.jmusicbot.audio.PlayerManager;
import com.jagrosh.jmusicbot.commands.v1.MusicCommand;
import com.jagrosh.jmusicbot.settings.Settings;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the {@link MusicCommand#forcesFreshNowPlaying} hook in isolation, using a minimal
 * dummy command instead of any real one - the real commands only decide whether to opt in.
 */
class MusicCommandTest
{
    private static final long GUILD_ID = 123456789L;

    private Bot bot;
    private CommandEvent event;
    private NowPlayingHandler nowPlayingHandler;

    private static class DummyMusicCommand extends MusicCommand
    {
        boolean doCommandCalled = false;

        DummyMusicCommand(Bot bot, boolean forcesFreshNowPlaying)
        {
            super(bot);
            this.forcesFreshNowPlaying = forcesFreshNowPlaying;
        }

        @Override
        public void doCommand(CommandEvent event)
        {
            doCommandCalled = true;
        }
    }

    @BeforeEach
    void setUp()
    {
        bot = mock(Bot.class);
        event = mock(CommandEvent.class);
        CommandClient client = mock(CommandClient.class);
        Guild guild = mock(Guild.class);
        Settings settings = mock(Settings.class);
        TextChannel textChannel = mock(TextChannel.class);
        Member member = mock(Member.class);
        JDA jda = mock(JDA.class);
        nowPlayingHandler = mock(NowPlayingHandler.class);

        when(guild.getIdLong()).thenReturn(GUILD_ID);
        when(event.getClient()).thenReturn(client);
        when(event.getGuild()).thenReturn(guild);
        when(event.getMember()).thenReturn(member);
        when(event.getTextChannel()).thenReturn(textChannel);
        when(event.getJDA()).thenReturn(jda);
        when(client.getSettingsFor(guild)).thenReturn(settings);
        when(client.getError()).thenReturn("❌");
        when(settings.getTextChannel(guild)).thenReturn(null);

        when(bot.getPlayerManager()).thenReturn(mock(PlayerManager.class));
        when(bot.getNowplayingHandler()).thenReturn(nowPlayingHandler);
    }

    private void invokeExecute(MusicCommand command) throws Exception
    {
        Method execute = com.jagrosh.jdautilities.command.Command.class
                .getDeclaredMethod("execute", CommandEvent.class);
        execute.setAccessible(true);
        execute.invoke(command, event);
    }

    @Test
    void execute_forcesFreshNowPlaying_whenCommandOptsIn() throws Exception
    {
        DummyMusicCommand command = new DummyMusicCommand(bot, true);

        invokeExecute(command);

        assertTrue(command.doCommandCalled);
        verify(nowPlayingHandler).forceFreshNowPlaying(GUILD_ID);
    }

    @Test
    void execute_doesNotForceFreshNowPlaying_whenCommandDoesNotOptIn() throws Exception
    {
        DummyMusicCommand command = new DummyMusicCommand(bot, false);

        invokeExecute(command);

        assertTrue(command.doCommandCalled);
        verify(nowPlayingHandler, never()).forceFreshNowPlaying(anyLong());
    }
}
