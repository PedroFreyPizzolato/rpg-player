package com.jagrosh.jmusicbot.unit.utils;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.NowPlayingInfo;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.settings.SettingsManager;
import com.jagrosh.jmusicbot.utils.MessageFormatter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("MessageFormatter Tests")
class MessageFormatterTest
{
    @Test
    @DisplayName("buildNowPlayingMessage() keeps mojibake-compatible author text in full embed")
    void buildNowPlayingMessage_keepsMojibakeCompatibleAuthorInFullEmbed()
    {
        String expectedAuthor = "МР. CREDO [Этой]";
        String mojibakeAuthor = new String(expectedAuthor.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);

        MessageCreateData message = buildNowPlayingMessage("Test Title", mojibakeAuthor, false, false, "",
                RepeatMode.OFF, 0, false, 0L, false, false, "id-1");
        MessageEmbed embed = getSingleEmbed(message);
        MessageEmbed.Field authorField = getField(embed, "Author");
        assertNotNull(authorField);
        assertEquals(mojibakeAuthor, authorField.getValue());
    }

    @Test
    @DisplayName("buildNowPlayingMessage() minimal layout without progress bar still shows elapsed and total time")
    void buildNowPlayingMessage_minimalWithoutProgressBar_stillShowsElapsedAndTotalTime()
    {
        MessageCreateData message = buildNowPlayingMessage("Test Title", "Test Author", true, false, "",
                RepeatMode.OFF, 0, false, 0L, false, false, "id-1");
        MessageEmbed embed = getSingleEmbed(message);
        assertNotNull(embed.getDescription());
        assertTrue(embed.getDescription().contains("`["));
        assertFalse(embed.getDescription().contains("▬"));
    }

    @Test
    @DisplayName("buildNowPlayingMessage() minimal layout shows progress bar when enabled")
    void buildNowPlayingMessage_minimalShowsProgressBarWhenEnabled()
    {
        MessageCreateData message = buildNowPlayingMessage("Test Title", "Test Author", true, true, "",
                RepeatMode.OFF, 0, false, 0L, false, false, "id-1");
        MessageEmbed embed = getSingleEmbed(message);
        assertNotNull(embed.getDescription());
        assertTrue(embed.getDescription().contains("▬"));
        assertTrue(embed.getDescription().contains("`["));
    }

    @Test
    @DisplayName("buildNowPlayingMessage() minimal layout footer includes source, queue and repeat")
    void buildNowPlayingMessage_minimalFooterIncludesSourceQueueAndRepeat()
    {
        MessageCreateData message = buildNowPlayingMessage("Test Title", "Test Author", true, false, "ignored",
                RepeatMode.ALL, 2, false, 0L, false, false, "id-1");
        MessageEmbed embed = getSingleEmbed(message);
        assertEquals("Source: youtube • 2 songs queued • Repeat: All", embed.getFooter().getText());
    }

    @Test
    @DisplayName("buildNoMusicPlayingMessage() full and minimal obey progress bar toggle")
    void buildNoMusicPlayingMessage_obeysProgressBarToggle()
    {
        String fullDisabled = noMusicDescription(false, false);
        String fullEnabled = noMusicDescription(false, true);
        String minimalDisabled = noMusicDescription(true, false);
        String minimalEnabled = noMusicDescription(true, true);

        assertFalse(fullDisabled.contains("▬"));
        assertTrue(fullEnabled.contains("▬"));
        assertFalse(minimalDisabled.contains("▬"));
        assertTrue(minimalEnabled.contains("▬"));
        assertTrue(fullDisabled.contains(AudioHandler.STOP_EMOJI));
    }

    @Test
    @DisplayName("buildNowPlayingMessage() uses namespaced now-playing button IDs")
    void buildNowPlayingMessage_usesNamespacedNowPlayingButtonIds()
    {
        MessageCreateData message = buildNowPlayingMessage("Test Title", "Test Author", false, false, "",
                RepeatMode.OFF, 0, false, 0L, true);
        List<String> buttonIds = message.getComponents().stream()
                .flatMap(row -> row.asActionRow().getButtons().stream())
                .map(net.dv8tion.jda.api.components.buttons.Button::getCustomId)
                .toList();
        assertTrue(buttonIds.contains("np_previous"));
        assertTrue(buttonIds.contains("np_pause"));
        assertTrue(buttonIds.contains("np_skip"));
        assertTrue(buttonIds.contains("np_stop"));
        assertTrue(buttonIds.contains("np_shuffle"));
        assertTrue(buttonIds.contains("np_repeat"));
        assertTrue(buttonIds.contains("np_voldown"));
        assertTrue(buttonIds.contains("np_volup"));
    }

    @Test
    @DisplayName("buildNowPlayingMessage() full layout includes thumbnail when images enabled")
    void buildNowPlayingMessage_fullLayout_includesThumbnailWhenImagesEnabled()
    {
        MessageCreateData message = buildNowPlayingMessage("Test Title", "Test Author", false, true, "",
                RepeatMode.OFF, 2, false, 120_000L, false, true, "id-1");
        MessageEmbed embed = getSingleEmbed(message);
        assertEquals("https://img.youtube.com/vi/id-1/mqdefault.jpg", embed.getThumbnail().getUrl());
        assertNotNull(embed.getDescription());
        assertTrue(embed.getDescription().contains("**Playing from:** youtube"));
        MessageEmbed.Field authorField = getField(embed, "Author");
        assertNotNull(authorField);
        assertEquals("Test Author", authorField.getValue());
    }

    @Test
    @DisplayName("buildNowPlayingMessage() full layout omits thumbnail when images disabled")
    void buildNowPlayingMessage_fullLayout_omitsThumbnailWhenImagesDisabled()
    {
        MessageCreateData message = buildNowPlayingMessage("Test Title", "Test Author", false, true, "",
                RepeatMode.OFF, 2, false, 120_000L, false, false, "id-1");
        MessageEmbed embed = getSingleEmbed(message);
        assertNull(embed.getThumbnail());
    }

    @Test
    @DisplayName("buildNowPlayingMessage() full layout uses original duration/queue/volume fields")
    void buildNowPlayingMessage_fullLayout_usesOriginalStatsFields()
    {
        MessageCreateData message = buildNowPlayingMessage("Test Title", "Test Author", false, false, "",
                RepeatMode.OFF, 3, false, 120_000L, false);
        MessageEmbed embed = getSingleEmbed(message);
        assertNull(getField(embed, "Info"));
        MessageEmbed.Field durationField = getField(embed, "Duration");
        MessageEmbed.Field queueField = getField(embed, "Queue");
        MessageEmbed.Field volumeField = getField(embed, "Volume");
        assertNotNull(durationField);
        assertNotNull(queueField);
        assertNotNull(volumeField);
        assertEquals("04:03", durationField.getValue());
        assertEquals("3", queueField.getValue());
        assertEquals("50%", volumeField.getValue());
    }

    @Test
    @DisplayName("buildNowPlayingMessage() full layout progress bar follows toggle")
    void buildNowPlayingMessage_fullLayout_progressBarFollowsToggle()
    {
        MessageCreateData noProgress = buildNowPlayingMessage("Test Title", "Test Author", false, false, "",
                RepeatMode.OFF, 0, false, 20_000L, false);
        MessageCreateData withProgress = buildNowPlayingMessage("Test Title", "Test Author", false, true, "",
                RepeatMode.OFF, 0, false, 20_000L, false);

        String noProgressDescription = getSingleEmbed(noProgress).getDescription();
        String withProgressDescription = getSingleEmbed(withProgress).getDescription();
        assertNotNull(noProgressDescription);
        assertNotNull(withProgressDescription);
        assertFalse(noProgressDescription.contains("`["));
        assertTrue(withProgressDescription.contains("`["));
        assertTrue(withProgressDescription.contains("▬"));
    }

    private static MessageCreateData buildNowPlayingMessage(
            String title,
            String author,
            boolean minimalMessage,
            boolean showProgressBar,
            String footerInfo,
            RepeatMode repeatMode,
            int queueSize,
            boolean paused,
            long positionMs,
            boolean showButtons)
    {
        return buildNowPlayingMessage(title, author, minimalMessage, showProgressBar, footerInfo, repeatMode,
                queueSize, paused, positionMs, showButtons, false, "id-1");
    }

    private static MessageCreateData buildNowPlayingMessage(
            String title,
            String author,
            boolean minimalMessage,
            boolean showProgressBar,
            String footerInfo,
            RepeatMode repeatMode,
            int queueSize,
            boolean paused,
            long positionMs,
            boolean showButtons,
            boolean useNpImages,
            String trackIdentifier)
    {
        Bot bot = mock(Bot.class);
        BotConfig config = mock(BotConfig.class);
        SettingsManager settingsManager = mock(SettingsManager.class);
        Settings settings = mock(Settings.class);

        when(bot.getConfig()).thenReturn(config);
        when(bot.getSettingsManager()).thenReturn(settingsManager);
        when(config.showNpProgressBar()).thenReturn(showProgressBar);
        when(config.useNPImages()).thenReturn(useNpImages);

        Guild guild = mock(Guild.class, RETURNS_DEEP_STUBS);
        when(guild.getName()).thenReturn("Test Guild");
        when(guild.getIconUrl()).thenReturn(null);
        when(guild.getSelfMember().getVoiceState().getChannel().getName()).thenReturn("Music VC");
        when(settingsManager.getSettings(guild)).thenReturn(settings);
        when(settings.useMinimalNowPlayingMessage(config)).thenReturn(minimalMessage);
        when(settings.showNowPlayingButtons(config)).thenReturn(showButtons);
        when(settings.getRepeatMode()).thenReturn(repeatMode);

        AudioTrack track = mock(AudioTrack.class, RETURNS_DEEP_STUBS);
        AudioTrackInfo trackInfo = new AudioTrackInfo(title, author, 243000L, trackIdentifier, false, "https://example.com/track");
        when(track.getInfo()).thenReturn(trackInfo);
        when(track.getIdentifier()).thenReturn(trackIdentifier);
        when(track.getPosition()).thenReturn(positionMs);
        when(track.getDuration()).thenReturn(243000L);
        when(track.getSourceManager().getSourceName()).thenReturn("youtube");
        when(track.getUserData(RequestMetadata.class)).thenReturn(null);

        NowPlayingInfo info = new NowPlayingInfo(track, guild, paused, 50, queueSize, footerInfo);
        return MessageFormatter.buildNowPlayingMessage(bot, info);
    }

    private static String noMusicDescription(boolean minimalMessage, boolean showProgressBar)
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
        when(guild.getName()).thenReturn("Test Guild");
        when(guild.getSelfMember().getVoiceState().getChannel().getName()).thenReturn("Music VC");

        NowPlayingInfo info = new NowPlayingInfo(null, guild, false, 50, 0, "");
        MessageCreateData message = MessageFormatter.buildNoMusicPlayingMessage(bot, info);
        return getSingleEmbed(message).getDescription();
    }

    private static MessageEmbed getSingleEmbed(MessageCreateData message)
    {
        assertEquals(1, message.getEmbeds().size());
        return message.getEmbeds().get(0);
    }

    private static MessageEmbed.Field getField(MessageEmbed embed, String fieldName)
    {
        return embed.getFields().stream()
                .filter(f -> fieldName.equals(f.getName()))
                .findFirst()
                .orElse(null);
    }
}
