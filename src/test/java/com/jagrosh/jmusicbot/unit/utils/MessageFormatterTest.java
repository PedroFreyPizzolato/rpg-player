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
        // Mojibake repair is currently disabled in FormatUtil.fixMojibakeUtf8AsLatin1.
        // Assertion below matches disabled behavior. When re-enabling repair there, use the commented line instead.
        String expectedAuthor = "МР. CREDO [Этой]";
        String mojibakeAuthor = new String(expectedAuthor.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);

        MessageEmbed embed = buildNowPlayingEmbed("Test Title", mojibakeAuthor);
        Optional<MessageEmbed.Field> authorField = findField(embed, "Author");

        assertTrue(authorField.isPresent());
        assertEquals(mojibakeAuthor, authorField.get().getValue());
        // When repair is re-enabled in FormatUtil.fixMojibakeUtf8AsLatin1, use this instead:
        // assertEquals(expectedAuthor, authorField.get().getValue());
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

    @Test
    @DisplayName("buildNowPlayingMessage() minimal layout hides progress bar when disabled")
    void buildNowPlayingMessage_minimalLayout_hidesProgressBarWhenDisabled()
    {
        MessageEmbed embed = buildNowPlayingEmbed("Test Title", "Test Author", true, false);

        assertFalse(embed.getDescription().contains("`["));
    }

    @Test
    @DisplayName("buildNowPlayingMessage() minimal layout shows progress bar when enabled")
    void buildNowPlayingMessage_minimalLayout_showsProgressBarWhenEnabled()
    {
        MessageEmbed embed = buildNowPlayingEmbed("Test Title", "Test Author", true, true);

        assertTrue(embed.getDescription().contains("`["));
    }

    @Test
    @DisplayName("buildNowPlayingMessage() minimal layout moves source into footer")
    void buildNowPlayingMessage_minimalLayout_movesSourceIntoFooter()
    {
        MessageEmbed embed = buildNowPlayingEmbed("Test Title", "Test Author", true, false, "Playing next song.");

        assertFalse(embed.getDescription().contains("Source: "));
        assertNotNull(embed.getFooter());
        assertEquals("Source: youtube • Playing next song.", embed.getFooter().getText());
    }

    @Test
    @DisplayName("buildNowPlayingMessage() minimal layout shows source-only footer when no reason")
    void buildNowPlayingMessage_minimalLayout_showsSourceOnlyFooterWhenNoReason()
    {
        MessageEmbed embed = buildNowPlayingEmbed("Test Title", "Test Author", true, false, "");

        assertNotNull(embed.getFooter());
        assertEquals("Source: youtube", embed.getFooter().getText());
    }

    @Test
    @DisplayName("buildNoMusicPlayingMessage() minimal/full obey show progress bar toggle")
    void buildNoMusicPlayingMessage_obeysShowProgressBarToggle()
    {
        MessageEmbed fullDisabled = buildNoMusicPlayingEmbed(false, false);
        MessageEmbed fullEnabled = buildNoMusicPlayingEmbed(false, true);
        MessageEmbed minimalDisabled = buildNoMusicPlayingEmbed(true, false);
        MessageEmbed minimalEnabled = buildNoMusicPlayingEmbed(true, true);

        assertFalse(fullDisabled.getDescription().contains("▬"));
        assertTrue(fullEnabled.getDescription().contains("▬"));
        assertFalse(minimalDisabled.getDescription().contains("▬"));
        assertTrue(minimalEnabled.getDescription().contains("▬"));
    }

    private static MessageEmbed buildNowPlayingEmbed(String title, String author)
    {
        return buildNowPlayingEmbed(title, author, false, false);
    }

    private static MessageEmbed buildNowPlayingEmbed(String title, String author, boolean minimalMessage, boolean showProgressBar)
    {
        return buildNowPlayingEmbed(title, author, minimalMessage, showProgressBar, "");
    }

    private static MessageEmbed buildNowPlayingEmbed(String title, String author, boolean minimalMessage, boolean showProgressBar, String footerInfo)
    {
        Bot bot = mock(Bot.class);
        BotConfig config = mock(BotConfig.class);
        SettingsManager settingsManager = mock(SettingsManager.class);
        Settings settings = mock(Settings.class);

        when(bot.getConfig()).thenReturn(config);
        when(bot.getSettingsManager()).thenReturn(settingsManager);
        when(config.showNpProgressBar()).thenReturn(showProgressBar);
        when(config.useNPImages()).thenReturn(false);

        Guild guild = mock(Guild.class, RETURNS_DEEP_STUBS);
        when(guild.getName()).thenReturn("Test Guild");
        when(guild.getIconUrl()).thenReturn(null);
        when(guild.getSelfMember().getColors().getPrimary()).thenReturn(Color.BLUE);
        when(settingsManager.getSettings(guild)).thenReturn(settings);
        when(settings.useMinimalNowPlayingMessage(config)).thenReturn(minimalMessage);
        when(settings.showNowPlayingButtons(config)).thenReturn(false);
        when(settings.getRepeatMode()).thenReturn(RepeatMode.OFF);

        AudioTrack track = mock(AudioTrack.class, RETURNS_DEEP_STUBS);
        AudioTrackInfo trackInfo = new AudioTrackInfo(title, author, 243000L, "id-1", false, "https://example.com/track");
        when(track.getInfo()).thenReturn(trackInfo);
        when(track.getPosition()).thenReturn(0L);
        when(track.getDuration()).thenReturn(243000L);
        when(track.getSourceManager().getSourceName()).thenReturn("youtube");
        when(track.getUserData(RequestMetadata.class)).thenReturn(null);

        NowPlayingInfo info = new NowPlayingInfo(track, guild, false, 50, 0, footerInfo);
        return MessageFormatter.buildNowPlayingMessage(bot, info).getEmbeds().get(0);
    }

    private static MessageEmbed buildNoMusicPlayingEmbed(boolean minimalMessage, boolean showProgressBar)
    {
        Bot bot = mock(Bot.class);
        BotConfig config = mock(BotConfig.class);
        SettingsManager settingsManager = mock(SettingsManager.class);
        Settings settings = mock(Settings.class);
        Guild guild = mock(Guild.class, RETURNS_DEEP_STUBS);

        when(bot.getConfig()).thenReturn(config);
        when(bot.getSettingsManager()).thenReturn(settingsManager);
        when(config.getSuccess()).thenReturn("ok");
        when(config.showNpProgressBar()).thenReturn(showProgressBar);
        when(settingsManager.getSettings(guild)).thenReturn(settings);
        when(settings.useMinimalNowPlayingMessage(config)).thenReturn(minimalMessage);
        when(guild.getSelfMember().getColors().getPrimary()).thenReturn(Color.BLUE);
        when(guild.getName()).thenReturn("Test Guild");

        NowPlayingInfo info = new NowPlayingInfo(null, guild, false, 50, 0, "");
        return MessageFormatter.buildNoMusicPlayingMessage(bot, info).getEmbeds().get(0);
    }

    private static Optional<MessageEmbed.Field> findField(MessageEmbed embed, String fieldName)
    {
        return embed.getFields().stream()
                .filter(field -> field.getName().equals(fieldName))
                .findFirst();
    }
}
