package com.jagrosh.jmusicbot.utils;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.NowPlayingInfo;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.jagrosh.jmusicbot.settings.Settings;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioTrack;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class MessageFormatter {
    private static final String NP_PREFIX = "np_";

    public static MessageCreateData buildNowPlayingMessage(Bot bot, NowPlayingInfo info) {
        if (info.track == null)
            return buildNoMusicPlayingMessage(bot, info);

        Settings settings = bot.getSettingsManager().getSettings(info.guild);
        boolean minimalMessage = settings.useMinimalNowPlayingMessage(bot.getConfig());
        boolean showButtons = settings.showNowPlayingButtons(bot.getConfig());

        return minimalMessage
                ? buildMinimalNowPlayingMessage(bot, info, showButtons)
                : buildFullNowPlayingMessage(bot, info, showButtons);
    }

    private static MessageCreateData buildFullNowPlayingMessage(Bot bot, NowPlayingInfo info, boolean showButtons) {
        List<ContainerChildComponent> children = new ArrayList<>();

        String title = FormatUtil.filter(FormatUtil.getTrackTitle(info.track));
        String uri = info.track.getInfo().uri;
        String titleLine = (uri != null && !uri.isBlank())
                ? "[" + title + "](" + uri + ")"
                : title;
        String titleDisplay = "## " + titleLine;
        String metadataLine = buildFullMetadataLine(info);
        String playbackLine = buildMinimalPlaybackDescription(bot, info);

        RepeatMode repeatMode = bot.getSettingsManager().getSettings(info.guild).getRepeatMode();
        String statusLine = buildFullStatusLine(bot, info, repeatMode);

        String requesterDetails = buildRequesterDetails(info);
        String artworkUrl = resolveArtworkUrl(bot, info);
        if (artworkUrl != null)
        {
            try
            {
                Thumbnail artwork = Thumbnail.fromUrl(artworkUrl);
                if (requesterDetails == null)
                {
                    children.add(Section.of(
                            artwork,
                            TextDisplay.of(titleDisplay),
                            TextDisplay.of(metadataLine)
                    ));
                }
                else
                {
                    children.add(Section.of(
                            artwork,
                            TextDisplay.of(titleDisplay),
                            TextDisplay.of(metadataLine),
                            TextDisplay.of("**Requester:** " + requesterDetails)
                    ));
                }
                children.add(TextDisplay.of(playbackLine));
                children.add(Separator.createDivider(Separator.Spacing.SMALL));
                children.add(TextDisplay.of(statusLine));
            }
            catch (RuntimeException ignored)
            {
                // Keep rendering stable if source metadata provides an invalid artwork URL.
                children.add(TextDisplay.of(titleDisplay));
                children.add(TextDisplay.of(metadataLine));
                if (requesterDetails != null)
                    children.add(TextDisplay.of("**Requester:** " + requesterDetails));
                children.add(TextDisplay.of(playbackLine));
                children.add(Separator.createDivider(Separator.Spacing.SMALL));
                children.add(TextDisplay.of(statusLine));
            }
        }
        else
        {
            children.add(TextDisplay.of(titleDisplay));
            children.add(TextDisplay.of(metadataLine));
            if (requesterDetails != null)
                children.add(TextDisplay.of("**Requester:** " + requesterDetails));
            children.add(TextDisplay.of(playbackLine));
            children.add(Separator.createDivider(Separator.Spacing.SMALL));
            children.add(TextDisplay.of(statusLine));
        }

        if (showButtons) {
            children.add(Separator.createDivider(Separator.Spacing.SMALL));
            addNowPlayingButtonRows(children, info, repeatMode);
        }

        return new MessageCreateBuilder()
                .setComponents(List.of(Container.of(children)))
                .useComponentsV2()
                .build();
    }

    private static MessageCreateData buildMinimalNowPlayingMessage(Bot bot, NowPlayingInfo info, boolean showButtons) {
        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of(FormatUtil.filter(bot.getConfig().getSuccess() + " **Now Playing in** " + getNowPlayingLocationName(info))));
        String title = FormatUtil.filter(FormatUtil.getTrackTitle(info.track));
        String uri = info.track.getInfo().uri;
        String titleLine = (uri != null && !uri.isBlank())
                ? "[" + title + "](" + uri + ")"
                : title;
        children.add(TextDisplay.of(titleLine));
        RepeatMode repeatMode = bot.getSettingsManager().getSettings(info.guild).getRepeatMode();
        children.add(TextDisplay.of(buildMinimalPlaybackDescription(bot, info)));
        children.add(TextDisplay.of(buildMinimalFooter(info, repeatMode)));

        if (showButtons) {
            children.add(Separator.createDivider(Separator.Spacing.SMALL));
            addNowPlayingButtonRows(children, info, repeatMode);
        }

        return new MessageCreateBuilder()
                .setComponents(List.of(Container.of(children)))
                .useComponentsV2()
                .build();
    }

    public static MessageCreateData buildNoMusicPlayingMessage(Bot bot, NowPlayingInfo info) {
        Settings settings = bot.getSettingsManager().getSettings(info.guild);
        boolean minimalMessage = settings.useMinimalNowPlayingMessage(bot.getConfig());

        if (minimalMessage) {
            return buildNoMusicPlayingMessageMinimal(bot, info);
        }

        return new MessageCreateBuilder()
                .setComponents(buildNoMusicComponents(bot, info, false))
                .useComponentsV2()
                .build();
    }

    private static MessageCreateData buildNoMusicPlayingMessageMinimal(Bot bot, NowPlayingInfo info) {
        return new MessageCreateBuilder()
                .setComponents(buildNoMusicComponents(bot, info, true))
                .useComponentsV2()
                .build();
    }

    private static String buildMinimalPlaybackDescription(Bot bot, NowPlayingInfo info) {
        String statusEmoji = info.isPaused ? AudioHandler.PAUSE_EMOJI : AudioHandler.PLAY_EMOJI;
        String timeDisplay = "`[" + TimeUtil.formatTime(info.position) + "/" + TimeUtil.formatTime(info.duration) + "]`";
        if (bot.getConfig().showNpProgressBar()) {
            double progress = info.duration > 0 ? (double) info.position / info.duration : 0;
            return statusEmoji + " " + FormatUtil.progressBar(progress) + " " + timeDisplay + " " + FormatUtil.volumeIcon(info.volume);
        }
        return statusEmoji + " " + timeDisplay + " " + FormatUtil.volumeIcon(info.volume);
    }

    private static String buildMinimalFooter(NowPlayingInfo info, RepeatMode repeatMode) {
        String sourceName = info.track.getSourceManager() != null
                ? info.track.getSourceManager().getSourceName()
                : "Unknown";
        StringBuilder footer = new StringBuilder("Source: ")
                .append(sourceName);
        String queuedLabel = formatQueuedLabel(info.queueSize);
        if (queuedLabel != null) {
            footer.append(" • ").append(queuedLabel);
        }
        if (repeatMode == RepeatMode.ALL || repeatMode == RepeatMode.SINGLE) {
            footer.append(" • Repeat: ").append(repeatMode.getUserFriendlyName());
        }
        return footer.toString();
    }

    private static String buildFullMetadataLine(NowPlayingInfo info) {
        StringBuilder metadata = new StringBuilder("**Source:** ").append(sourceNameForTrack(info));
        String rawAuthor = info.track.getInfo().author;
        String author = rawAuthor == null ? null : FormatUtil.filter(FormatUtil.fixMojibakeUtf8AsLatin1(rawAuthor));
        if (author != null && (!author.isEmpty() && !author.equalsIgnoreCase("unknown artist"))) {
            metadata.append(" • **Author:** ").append(author);
        }
        return metadata.toString();
    }

    private static String buildFullStatusLine(Bot bot, NowPlayingInfo info, RepeatMode repeatMode) {
        StringJoiner statusParts = new StringJoiner(" • ");
        String queuedLabel = formatQueuedLabel(info.queueSize);
        if (queuedLabel != null) {
            statusParts.add(queuedLabel);
        }
        if (!bot.getConfig().showNpProgressBar()) {
            statusParts.add(TimeUtil.formatTime(info.duration));
        }
        statusParts.add("Volume: " + info.volume + "%");
        if (repeatMode == RepeatMode.ALL || repeatMode == RepeatMode.SINGLE) {
            statusParts.add("Repeat: " + repeatMode.getUserFriendlyName());
        }
        return statusParts.toString();
    }

    private static String formatQueuedLabel(int queueSize) {
        if (queueSize <= 0) {
            return null;
        }
        return queueSize == 1 ? "1 song queued" : queueSize + " songs queued";
    }

    private static String getNowPlayingLocationName(NowPlayingInfo info) {
        if (info.guild.getSelfMember() != null
                && info.guild.getSelfMember().getVoiceState() != null
                && info.guild.getSelfMember().getVoiceState().getChannel() != null) {
            return info.guild.getSelfMember().getVoiceState().getChannel().getName();
        }
        return info.guild.getName();
    }

    private static String sourceNameForTrack(NowPlayingInfo info)
    {
        return info.track.getSourceManager() != null
                ? info.track.getSourceManager().getSourceName()
                : "Unknown";
    }

    private static String resolveArtworkUrl(Bot bot, NowPlayingInfo info)
    {
        if (info.track instanceof LocalAudioTrack || !bot.getConfig().useNPImages())
            return null;
        var artworkUrl = info.track.getInfo().artworkUrl;
        if (artworkUrl == null || artworkUrl.isEmpty())
            artworkUrl = "https://img.youtube.com/vi/" + info.track.getIdentifier() + "/mqdefault.jpg";
        return artworkUrl;
    }

    private static String buildRequesterDetails(NowPlayingInfo info)
    {
        RequestMetadata rm = info.track.getUserData(RequestMetadata.class);
        if (rm != null && rm.getOwner() != 0L)
        {
            User u = info.guild.getJDA().getUserById(rm.user.id);
            return (u == null) ? FormatUtil.formatUsername(rm.user) : u.getAsMention();
        }
        return null;
    }

    private static List<MessageTopLevelComponent> buildNoMusicComponents(Bot bot, NowPlayingInfo info, boolean minimal) {
        String descriptionText;
        if (bot.getConfig().showNpProgressBar()) {
            descriptionText = AudioHandler.STOP_EMOJI + " " + FormatUtil.progressBar(-1)
                    + " " + FormatUtil.volumeIcon(info.volume);
        } else {
            descriptionText = AudioHandler.STOP_EMOJI + " " + FormatUtil.volumeIcon(info.volume);
        }

        List<ContainerChildComponent> children = new ArrayList<>();
        if (minimal) {
            children.add(TextDisplay.of(FormatUtil.filter(bot.getConfig().getSuccess() + " **Now Playing in** " + getNowPlayingLocationName(info))));
        } else {
            children.add(TextDisplay.of(FormatUtil.filter(bot.getConfig().getSuccess() + " **Now Playing...**")));
        }
        children.add(TextDisplay.of("No music playing"));
        children.add(TextDisplay.of(descriptionText));
        return List.of(Container.of(children));
    }

    private static void addNowPlayingButtonRows(List<ContainerChildComponent> children, NowPlayingInfo info, RepeatMode repeatMode) {
        Button repeatButton = switch (repeatMode) {
            case ALL -> Button.primary(nowPlayingButtonId("repeat"), Emoji.fromUnicode("\uD83D\uDD01")); // 🔁
            case SINGLE -> Button.primary(nowPlayingButtonId("repeat"), Emoji.fromUnicode("\uD83D\uDD02")); // 🔂
            default -> Button.secondary(nowPlayingButtonId("repeat"), Emoji.fromUnicode("\uD83D\uDD01")); // 🔁
        };
        children.add(ActionRow.of(
                Button.secondary(nowPlayingButtonId("previous"), Emoji.fromUnicode("\u23EE")), // Previous ⏮
                info.isPaused
                        ? Button.primary(nowPlayingButtonId("pause"), Emoji.fromUnicode("\u25B6"))    // Resume ▶
                        : Button.secondary(nowPlayingButtonId("pause"), Emoji.fromUnicode("\u23F8")), // Pause ⏸
                Button.secondary(nowPlayingButtonId("skip"), Emoji.fromUnicode("\u23ED")), // Skip ⏭
                Button.secondary(nowPlayingButtonId("stop"), Emoji.fromUnicode("\u23F9")) // Stop ⏹
        ));
        children.add(ActionRow.of(
                Button.secondary(nowPlayingButtonId("shuffle"), Emoji.fromUnicode("\uD83D\uDD00")), // Shuffle 🔀
                repeatButton, // Repeat cycle
                Button.secondary(nowPlayingButtonId("voldown"), Emoji.fromUnicode("\uD83D\uDD09")), // Vol Down 🔉
                Button.secondary(nowPlayingButtonId("volup"), Emoji.fromUnicode("\uD83D\uDD0A")) // Vol Up 🔊
        ));
    }

    private static String nowPlayingButtonId(String action) {
        return NP_PREFIX + action;
    }
}
