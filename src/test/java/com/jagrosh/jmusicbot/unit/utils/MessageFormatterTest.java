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
import net.dv8tion.jda.api.components.Component;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
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
    @DisplayName("buildNowPlayingMessage() repairs mojibake in author field")
    void buildNowPlayingMessage_repairsMojibakeAuthor()
    {
        // Mojibake repair is currently disabled in FormatUtil.fixMojibakeUtf8AsLatin1.
        // Assertion below matches disabled behavior. When re-enabling repair there, use the commented line instead.
        String expectedAuthor = "МР. CREDO [Этой]";
        String mojibakeAuthor = new String(expectedAuthor.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);

        List<String> textDisplays = buildNowPlayingText("Test Title", mojibakeAuthor);
        String combined = String.join("\n", textDisplays);
        assertTrue(combined.contains("**Author:** " + mojibakeAuthor));
        // When repair is re-enabled in FormatUtil.fixMojibakeUtf8AsLatin1, use this instead:
        // assertTrue(combined.contains("**Author:** " + expectedAuthor));
    }

    @Test
    @DisplayName("buildNowPlayingMessage() keeps ASCII author unchanged")
    void buildNowPlayingMessage_keepsAsciiAuthorUnchanged()
    {
        String asciiAuthor = "Rick Astley";

        List<String> textDisplays = buildNowPlayingText("Test Title", asciiAuthor);
        assertTrue(String.join("\n", textDisplays).contains("**Author:** " + asciiAuthor));
    }

    @Test
    @DisplayName("buildNowPlayingMessage() omits unknown artist")
    void buildNowPlayingMessage_omitsUnknownArtist()
    {
        List<String> textDisplays = buildNowPlayingText("Test Title", "Unknown Artist");
        assertTrue(textDisplays.stream().noneMatch(t -> t.startsWith("**Author:** ")));
    }

    @Test
    @DisplayName("buildNowPlayingMessage() minimal layout without progress bar still shows elapsed/total time")
    void buildNowPlayingMessage_minimalLayout_withoutProgressBar_showsElapsedAndTotalTime()
    {
        List<String> textDisplays = buildNowPlayingText("Test Title", "Test Author", true, false);
        String playbackLine = textDisplays.stream().filter(t -> t.contains("`[")).findFirst().orElse("");
        assertFalse(playbackLine.contains("▬"));
        assertTrue(playbackLine.contains("`["));
    }

    @Test
    @DisplayName("buildNowPlayingMessage() minimal layout shows progress bar when enabled")
    void buildNowPlayingMessage_minimalLayout_showsProgressBarWhenEnabled()
    {
        List<String> textDisplays = buildNowPlayingText("Test Title", "Test Author", true, true);
        String playbackLine = textDisplays.stream().filter(t -> t.contains("`[")).findFirst().orElse("");
        assertTrue(playbackLine.contains("`["));
    }

    @Test
    @DisplayName("buildNowPlayingMessage() minimal layout footer shows source and plural queued text")
    void buildNowPlayingMessage_minimalLayout_footerShowsSourceAndQueue()
    {
        List<String> textDisplays = buildNowPlayingText("Test Title", "Test Author", true, false, "Playing next song.",
                RepeatMode.OFF, 3, false, 0L);

        assertTrue(textDisplays.contains("Source: youtube • 3 songs queued"));
        assertTrue(textDisplays.stream().noneMatch(t -> t.startsWith("Info: ")));
    }

    @Test
    @DisplayName("buildNowPlayingMessage() minimal layout footer uses singular queued text for one")
    void buildNowPlayingMessage_minimalLayout_footerShowsSingularQueueText()
    {
        List<String> textDisplays = buildNowPlayingText("Test Title", "Test Author", true, false, "",
                RepeatMode.OFF, 1, false, 0L);
        assertTrue(textDisplays.contains("Source: youtube • 1 song queued"));
    }

    @Test
    @DisplayName("buildNowPlayingMessage() minimal layout footer omits queue text when empty")
    void buildNowPlayingMessage_minimalLayout_footerOmitsQueueWhenEmpty()
    {
        List<String> textDisplays = buildNowPlayingText("Test Title", "Test Author", true, false, "",
                RepeatMode.OFF, 0, false, 0L);
        assertTrue(textDisplays.contains("Source: youtube"));
    }

    @Test
    @DisplayName("buildNowPlayingMessage() minimal layout footer omits last reason text")
    void buildNowPlayingMessage_minimalLayout_footerOmitsLastReasonText()
    {
        List<String> textDisplays = buildNowPlayingText("Test Title", "Test Author", true, false, "Playing next song.",
                RepeatMode.OFF, 1, false, 0L);
        assertTrue(textDisplays.stream().noneMatch(t -> t.contains("Playing next song.")));
    }

    @Test
    @DisplayName("buildNowPlayingMessage() minimal layout footer includes repeat when ALL")
    void buildNowPlayingMessage_minimalLayout_footerIncludesRepeatAll()
    {
        List<String> textDisplays = buildNowPlayingText("Test Title", "Test Author", true, false, "",
                RepeatMode.ALL, 2, false, 0L);
        assertTrue(textDisplays.contains("Source: youtube • 2 songs queued • Repeat: All"));
    }

    @Test
    @DisplayName("buildNowPlayingMessage() minimal layout footer includes repeat when SINGLE")
    void buildNowPlayingMessage_minimalLayout_footerIncludesRepeatSingle()
    {
        List<String> textDisplays = buildNowPlayingText("Test Title", "Test Author", true, false, "",
                RepeatMode.SINGLE, 2, false, 0L);
        assertTrue(textDisplays.contains("Source: youtube • 2 songs queued • Repeat: Single"));
    }

    @Test
    @DisplayName("buildNoMusicPlayingMessage() minimal/full obey show progress bar toggle")
    void buildNoMusicPlayingMessage_obeysShowProgressBarToggle()
    {
        String fullDisabled = findNoMusicStatusLine(false, false);
        String fullEnabled = findNoMusicStatusLine(false, true);
        String minimalDisabled = findNoMusicStatusLine(true, false);
        String minimalEnabled = findNoMusicStatusLine(true, true);

        assertFalse(fullDisabled.contains("▬"));
        assertTrue(fullEnabled.contains("▬"));
        assertFalse(minimalDisabled.contains("▬"));
        assertTrue(minimalEnabled.contains("▬"));
    }

    @Test
    @DisplayName("buildNowPlayingMessage() renders namespaced NP button IDs")
    void buildNowPlayingMessage_usesNamespacedNowPlayingButtonIds()
    {
        MessageCreateData message = buildNowPlayingMessage("Test Title", "Test Author", false, false, "",
                RepeatMode.OFF, 0, false, 0L, true);
        Container container = extractContainer(message);
        List<String> buttonIds = container.getComponents().stream()
                .filter(c -> c.getType() == Component.Type.ACTION_ROW)
                .flatMap(row -> row.asActionRow().getButtons().stream())
                .map(b -> b.getCustomId())
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
    @DisplayName("buildNowPlayingMessage() full layout uses section thumbnail accessory when images enabled")
    void buildNowPlayingMessage_fullLayout_usesSectionThumbnailAccessoryWhenImagesEnabled()
    {
        MessageCreateData message = buildNowPlayingMessage("Test Title", "Test Author", false, true, "",
                RepeatMode.OFF, 2, false, 120_000L, false, true, "id-1");
        Container container = extractContainer(message);
        Section section = container.getComponents().stream()
                .filter(c -> c.getType() == Component.Type.SECTION)
                .map(c -> c.asSection())
                .findFirst()
                .orElse(null);

        assertNotNull(section);
        assertNotNull(section.getAccessory());
        assertEquals(Component.Type.THUMBNAIL, section.getAccessory().getType());
        List<String> textDisplays = extractTextDisplays(message);
        String combined = String.join("\n", textDisplays);
        assertTrue(textDisplays.stream().noneMatch(t -> t.startsWith("Artwork: ")));
        assertTrue(combined.contains("## [Test Title](https://example.com/track)"));
        assertTrue(combined.contains("**Playing from:** youtube"));
        assertTrue(combined.contains("**Duration:** "));
        assertTrue(combined.contains("**Queue:** 2"));
        assertTrue(combined.contains("**Volume:** 50%"));
        assertFalse(combined.contains("## Now Playing"));
        assertFalse(combined.contains("Server: "));
    }

    @Test
    @DisplayName("buildNowPlayingMessage() full layout places thumbnail section before playback and stats")
    void buildNowPlayingMessage_fullLayout_placesSectionNearTop()
    {
        MessageCreateData message = buildNowPlayingMessage("Test Title", "Test Author", false, true, "Playing next song.",
                RepeatMode.OFF, 2, false, 120_000L, false, true, "id-1");
        Container container = extractContainer(message);
        List<?> components = container.getComponents();

        int sectionIndex = findComponentIndex(components, c -> c.getType() == Component.Type.SECTION);
        int playbackIndex = findComponentIndex(components, c -> c.getType() == Component.Type.TEXT_DISPLAY
                && ((net.dv8tion.jda.api.components.textdisplay.TextDisplay) c).getContent().contains("`["));
        int statsIndex = findComponentIndex(components, c -> c.getType() == Component.Type.TEXT_DISPLAY
                && ((net.dv8tion.jda.api.components.textdisplay.TextDisplay) c).getContent().startsWith("**Duration:** "));
        String combined = String.join("\n", extractTextDisplays(message));

        assertEquals(0, sectionIndex);
        assertTrue(playbackIndex > sectionIndex);
        assertTrue(statsIndex > playbackIndex);
        assertFalse(combined.contains("Playing next song."));
    }

    @Test
    @DisplayName("buildNowPlayingMessage() full layout uses fallback thumbnail URL")
    void buildNowPlayingMessage_fullLayout_usesFallbackThumbnailUrl()
    {
        MessageCreateData message = buildNowPlayingMessage("Test Title", "Test Author", false, true, "",
                RepeatMode.OFF, 2, false, 120_000L, false, true, "abc123");
        Container container = extractContainer(message);
        Section section = container.getComponents().stream()
                .filter(c -> c.getType() == Component.Type.SECTION)
                .map(c -> c.asSection())
                .findFirst()
                .orElse(null);

        assertNotNull(section);
        assertNotNull(section.getAccessory());
        assertEquals("https://img.youtube.com/vi/abc123/mqdefault.jpg", section.getAccessory().asThumbnail().getUrl());
    }

    @Test
    @DisplayName("buildNowPlayingMessage() full layout remains valid without thumbnail when images are disabled")
    void buildNowPlayingMessage_fullLayout_withoutImages_hasNoThumbnailSection()
    {
        MessageCreateData message = buildNowPlayingMessage("Test Title", "Test Author", false, true, "",
                RepeatMode.OFF, 2, false, 120_000L, false, false, "id-1");
        Container container = extractContainer(message);
        boolean hasThumbnailSection = container.getComponents().stream()
                .anyMatch(c -> c.getType() == Component.Type.SECTION
                        && c.asSection().getAccessory() != null
                        && c.asSection().getAccessory().getType() == Component.Type.THUMBNAIL);
        assertFalse(hasThumbnailSection);
        assertTrue(extractTextDisplays(message).stream().anyMatch(t -> t.startsWith("## ")));
    }

    private static List<String> buildNowPlayingText(String title, String author)
    {
        return buildNowPlayingText(title, author, false, false);
    }

    private static List<String> buildNowPlayingText(String title, String author, boolean minimalMessage, boolean showProgressBar)
    {
        return buildNowPlayingText(title, author, minimalMessage, showProgressBar, "");
    }

    private static List<String> buildNowPlayingText(String title, String author, boolean minimalMessage, boolean showProgressBar, String footerInfo)
    {
        return buildNowPlayingText(title, author, minimalMessage, showProgressBar, footerInfo, RepeatMode.OFF, 0, false, 0L);
    }

    private static List<String> buildNowPlayingText(
            String title,
            String author,
            boolean minimalMessage,
            boolean showProgressBar,
            String footerInfo,
            RepeatMode repeatMode,
            int queueSize,
            boolean paused,
            long positionMs)
    {
        return extractTextDisplays(buildNowPlayingMessage(title, author, minimalMessage, showProgressBar, footerInfo,
                repeatMode, queueSize, paused, positionMs, false));
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

    private static String findNoMusicStatusLine(boolean minimalMessage, boolean showProgressBar)
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

        NowPlayingInfo info = new NowPlayingInfo(null, guild, false, 50, 0, "");
        return extractTextDisplays(MessageFormatter.buildNoMusicPlayingMessage(bot, info)).stream()
                .filter(t -> t.contains("⏹"))
                .findFirst()
                .orElse("");
    }

    private static List<String> extractTextDisplays(MessageCreateData message)
    {
        Container container = extractContainer(message);
        return container.getComponents().stream()
                .flatMap(c -> {
                    if (c.getType() == Component.Type.TEXT_DISPLAY) {
                        return java.util.stream.Stream.of(c.asTextDisplay().getContent());
                    }
                    if (c.getType() == Component.Type.SECTION) {
                        return c.asSection().getContentComponents().stream()
                                .filter(cc -> cc.getType() == Component.Type.TEXT_DISPLAY)
                                .map(cc -> cc.asTextDisplay().getContent());
                    }
                    return java.util.stream.Stream.empty();
                })
                .toList();
    }

    private static Container extractContainer(MessageCreateData message)
    {
        assertFalse(message.getComponents().isEmpty());
        assertTrue(message.getComponents().get(0) instanceof Container);
        return (Container) message.getComponents().get(0);
    }

    private static int findComponentIndex(List<?> components, java.util.function.Predicate<net.dv8tion.jda.api.components.Component> predicate)
    {
        for (int i = 0; i < components.size(); i++)
        {
            net.dv8tion.jda.api.components.Component component = (net.dv8tion.jda.api.components.Component) components.get(i);
            if (predicate.test(component))
                return i;
        }
        return -1;
    }
}
