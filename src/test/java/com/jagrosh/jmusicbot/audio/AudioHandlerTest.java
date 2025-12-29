package com.jagrosh.jmusicbot.audio;

import com.jagrosh.jmusicbot.TestBase;
import com.jagrosh.jmusicbot.settings.QueueType;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AudioHandlerTest extends TestBase {

    @Mock
    private SelfMember selfMember;
    @Mock
    private GuildVoiceState voiceState;

    private AudioHandler audioHandler;

    @Before
    @Override
    public void setUp() {
        super.setUp();
        when(settings.getQueueType()).thenReturn(QueueType.FAIR);

        audioHandler = new AudioHandler(playerManager, guild, audioPlayer);
    }

    @Test
    public void testAddTrackWhenNothingPlaying() {
        QueuedTrack qtrack = mock(QueuedTrack.class);
        AudioTrack track = mock(AudioTrack.class);
        when(qtrack.getTrack()).thenReturn(track);
        when(audioPlayer.getPlayingTrack()).thenReturn(null);

        int result = audioHandler.addTrack(qtrack);

        assertEquals(-1, result);
        verify(audioPlayer).playTrack(track);
    }

    @Test
    public void testAddTrackWhenSomethingPlaying() {
        QueuedTrack qtrack = mock(QueuedTrack.class);
        AudioTrack track = mock(AudioTrack.class);
        AudioTrackInfo info = new AudioTrackInfo("Title", "Author", 1000, "identifier", true, "uri");
        when(track.getInfo()).thenReturn(info);
        when(qtrack.getTrack()).thenReturn(track);
        when(audioPlayer.getPlayingTrack()).thenReturn(mock(AudioTrack.class));

        int result = audioHandler.addTrack(qtrack);

        assertTrue(result >= 0);
        assertEquals(1, audioHandler.getQueue().size());
    }

    @Test
    public void testStopAndClear() {
        audioHandler.stopAndClear();

        verify(audioPlayer).stopTrack();
        assertTrue(audioHandler.getQueue().isEmpty());
    }

    @Test
    public void testIsMusicPlaying() {
        when(jda.getGuildById(anyLong())).thenReturn(guild);
        when(guild.getSelfMember()).thenReturn(selfMember);
        when(selfMember.getVoiceState()).thenReturn(voiceState);
        when(voiceState.getChannel()).thenReturn(audioChannel);
        when(audioPlayer.getPlayingTrack()).thenReturn(audioTrack);

        assertTrue(audioHandler.isMusicPlaying(jda));

        when(voiceState.getChannel()).thenReturn(null);
        assertFalse(audioHandler.isMusicPlaying(jda));

        when(voiceState.getChannel()).thenReturn(audioChannel);
        when(audioPlayer.getPlayingTrack()).thenReturn(null);
        assertFalse(audioHandler.isMusicPlaying(jda));
    }
}
