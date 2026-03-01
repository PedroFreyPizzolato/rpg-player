package com.jagrosh.jmusicbot.unit.utils;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.audio.NowPlayingInfo;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.settings.SettingsManager;
import com.jagrosh.jmusicbot.utils.MessageFormatter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("MessageFormatter Tests")
class MessageFormatterTest
{
    @Test
    @DisplayName("buildNowPlayingMessage() repairs mojibake in author field")
    void buildNowPlayingMessage_repairsMojibakeAuthor()
    {
        String expectedAuthor = "МР. CREDO [Этой]";
        String mojibakeAuthor = new String(expectedAuthor.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);

        MessageEmbed embed = buildNowPlayingEmbed("Test Title", mojibakeAuthor);
        Optional<MessageEmbed.Field> authorField = findField(embed, "Author");

        assertTrue(authorField.isPresent());
        assertEquals(expectedAuthor, authorField.get().getValue());
    }

    @Test
    @DisplayName("buildNowPlayingMessage() keeps ASCII author unchanged")
    void buildNowPlayingMessage_keepsAsciiAuthorUnchanged()
    {
        String asciiAuthor = "Rick Astley";

        MessageEmbed embed = buildNowPlayingEmbed("Test Title", asciiAuthor);
        Optional<MessageEmbed.Field> authorField = findField(embed, "Author");

        assertTrue(authorField.isPresent());
        assertEquals(asciiAuthor, authorField.get().getValue());
    }

    @Test
    @DisplayName("buildNowPlayingMessage() omits unknown artist")
    void buildNowPlayingMessage_omitsUnknownArtist()
    {
        MessageEmbed embed = buildNowPlayingEmbed("Test Title", "Unknown Artist");

        assertTrue(findField(embed, "Author").isEmpty());
    }

    private static MessageEmbed buildNowPlayingEmbed(String title, String author)
    {
        Bot bot = mock(Bot.class);
        BotConfig config = mock(BotConfig.class);
        SettingsManager settingsManager = mock(SettingsManager.class);
        Settings settings = mock(Settings.class);

        when(bot.getConfig()).thenReturn(config);
        when(bot.getSettingsManager()).thenReturn(settingsManager);
        when(config.updateNpProgressBar()).thenReturn(false);
        when(config.useNPImages()).thenReturn(false);

        Guild guild = mock(Guild.class, RETURNS_DEEP_STUBS);
        when(guild.getName()).thenReturn("Test Guild");
        when(guild.getIconUrl()).thenReturn(null);
        when(guild.getSelfMember().getColors().getPrimary()).thenReturn(Color.BLUE);
        when(settingsManager.getSettings(guild)).thenReturn(settings);
        when(settings.getRepeatMode()).thenReturn(RepeatMode.OFF);

        AudioTrack track = mock(AudioTrack.class, RETURNS_DEEP_STUBS);
        AudioTrackInfo trackInfo = new AudioTrackInfo(title, author, 243000L, "id-1", false, "https://example.com/track");
        when(track.getInfo()).thenReturn(trackInfo);
        when(track.getPosition()).thenReturn(0L);
        when(track.getDuration()).thenReturn(243000L);
        when(track.getSourceManager().getSourceName()).thenReturn("youtube");
        when(track.getUserData(RequestMetadata.class)).thenReturn(null);

        NowPlayingInfo info = new NowPlayingInfo(track, guild, false, 50, 0, "");
        return MessageFormatter.buildNowPlayingMessage(bot, info).getEmbeds().get(0);
    }

    private static Optional<MessageEmbed.Field> findField(MessageEmbed embed, String fieldName)
    {
        return embed.getFields().stream()
                .filter(field -> field.getName().equals(fieldName))
                .findFirst();
    }
}
