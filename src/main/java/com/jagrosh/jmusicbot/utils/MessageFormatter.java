package com.jagrosh.jmusicbot.utils;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.NowPlayingInfo;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.jagrosh.jmusicbot.settings.Settings;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

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
        MessageCreateBuilder mb = new MessageCreateBuilder();
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(info.guild.getSelfMember().getColors().getPrimary());
        eb.setAuthor(info.guild.getName(), null, info.guild.getIconUrl());

        String title = FormatUtil.filter(FormatUtil.getTrackTitle(info.track));
        try {
            eb.setTitle(title, info.track.getInfo().uri);
        } catch (Exception ignored) {
            eb.setTitle(title);
        }

        StringBuilder description = new StringBuilder();
        if (bot.getConfig().showNpProgressBar()) {
            String statusEmoji = info.isPaused ? AudioHandler.PAUSE_EMOJI : AudioHandler.PLAY_EMOJI;
            double progress = info.duration > 0 ? (double) info.position / info.duration : 0;
            description.append(statusEmoji)
                    .append(" ").append(FormatUtil.progressBar(progress))
                    .append(" `[").append(TimeUtil.formatTime(info.position))
                    .append("/").append(TimeUtil.formatTime(info.duration)).append("]` ")
                    .append(FormatUtil.volumeIcon(info.volume))
                    .append("\n\n");
        }
        description.append("**Playing from:** ").append(sourceNameForTrack(info));
        eb.setDescription(description.toString());

        String rawAuthor = info.track.getInfo().author;
        String author = rawAuthor == null ? null : FormatUtil.filter(FormatUtil.fixMojibakeUtf8AsLatin1(rawAuthor));
        if (author != null && (!author.isEmpty() && !author.equalsIgnoreCase("unknown artist"))) {
            eb.addField("Author", author, false);
        }

        eb.addField("Duration", TimeUtil.formatTime(info.duration), true);
        eb.addField("Queue", String.valueOf(info.queueSize), true);
        eb.addField("Volume", info.volume + "%", true);

        RepeatMode repeatMode = bot.getSettingsManager().getSettings(info.guild).getRepeatMode();
        if (repeatMode != RepeatMode.OFF) {
            eb.addField("Repeat", repeatMode.getEmoji() + " " + repeatMode.getUserFriendlyName(), true);
        }

        String requesterDetails = buildRequesterDetails(info);
        if (requesterDetails != null) {
            eb.addField("Requester", requesterDetails, false);
        }

        String artworkUrl = resolveArtworkUrl(bot, info);
        if (artworkUrl != null) {
            eb.setThumbnail(artworkUrl);
        }
        if (info.footerInfo != null && !info.footerInfo.isEmpty()) {
            eb.setFooter(info.footerInfo);
        }

        mb.setEmbeds(eb.build());
        if (showButtons) {
            applyNowPlayingButtons(mb, info, repeatMode);
        }
        return mb.build();
    }

    private static MessageCreateData buildMinimalNowPlayingMessage(Bot bot, NowPlayingInfo info, boolean showButtons) {
        MessageCreateBuilder mb = new MessageCreateBuilder();
        mb.setContent(FormatUtil.filter(bot.getConfig().getSuccess() + " **Now Playing in** " + getNowPlayingLocationName(info)));

        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(info.guild.getSelfMember().getColors().getPrimary());
        String title = FormatUtil.filter(FormatUtil.getTrackTitle(info.track));
        try {
            eb.setTitle(title, info.track.getInfo().uri);
        } catch (Exception ignored) {
            eb.setTitle(title);
        }
        RepeatMode repeatMode = bot.getSettingsManager().getSettings(info.guild).getRepeatMode();
        eb.setDescription(buildMinimalPlaybackDescription(bot, info));
        eb.setFooter(buildMinimalFooter(info, repeatMode));

        if (showButtons) {
            applyNowPlayingButtons(mb, info, repeatMode);
        }

        mb.setEmbeds(eb.build());
        return mb.build();
    }

    public static MessageCreateData buildNoMusicPlayingMessage(Bot bot, NowPlayingInfo info) {
        Settings settings = bot.getSettingsManager().getSettings(info.guild);
        boolean minimalMessage = settings.useMinimalNowPlayingMessage(bot.getConfig());

        if (minimalMessage) {
            return buildNoMusicPlayingMessageMinimal(bot, info);
        }

        String descriptionText = bot.getConfig().showNpProgressBar()
                ? AudioHandler.STOP_EMOJI + " " + FormatUtil.progressBar(-1) + " " + FormatUtil.volumeIcon(info.volume)
                : AudioHandler.STOP_EMOJI + " " + FormatUtil.volumeIcon(info.volume);

        return new MessageCreateBuilder()
                .setContent(FormatUtil.filter(bot.getConfig().getSuccess() + " **Now Playing...**"))
                .setEmbeds(new EmbedBuilder()
                        .setTitle("No music playing")
                        .setDescription(descriptionText)
                        .setColor(info.guild.getSelfMember().getColors().getPrimary())
                        .build())
                .build();
    }

    private static MessageCreateData buildNoMusicPlayingMessageMinimal(Bot bot, NowPlayingInfo info) {
        String descriptionText = bot.getConfig().showNpProgressBar()
                ? AudioHandler.STOP_EMOJI + " " + FormatUtil.progressBar(-1) + " " + FormatUtil.volumeIcon(info.volume)
                : AudioHandler.STOP_EMOJI + " " + FormatUtil.volumeIcon(info.volume);

        return new MessageCreateBuilder()
                .setContent(FormatUtil.filter(bot.getConfig().getSuccess() + " **Now Playing in** " + getNowPlayingLocationName(info)))
                .setEmbeds(new EmbedBuilder()
                        .setTitle("No music playing")
                        .setDescription(descriptionText)
                        .setColor(info.guild.getSelfMember().getColors().getPrimary())
                        .build())
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

    private static void applyNowPlayingButtons(MessageCreateBuilder mb, NowPlayingInfo info, RepeatMode repeatMode) {
        Button repeatButton = switch (repeatMode) {
            case ALL -> Button.primary(nowPlayingButtonId("repeat"), Emoji.fromUnicode("\uD83D\uDD01")); // 🔁
            case SINGLE -> Button.primary(nowPlayingButtonId("repeat"), Emoji.fromUnicode("\uD83D\uDD02")); // 🔂
            default -> Button.secondary(nowPlayingButtonId("repeat"), Emoji.fromUnicode("\uD83D\uDD01")); // 🔁
        };
        mb.setComponents(
                ActionRow.of(
                        Button.secondary(nowPlayingButtonId("previous"), Emoji.fromUnicode("\u23EE")), // Previous ⏮
                        info.isPaused
                                ? Button.primary(nowPlayingButtonId("pause"), Emoji.fromUnicode("\u25B6"))    // Resume ▶
                                : Button.secondary(nowPlayingButtonId("pause"), Emoji.fromUnicode("\u23F8")), // Pause ⏸
                        Button.secondary(nowPlayingButtonId("skip"), Emoji.fromUnicode("\u23ED")), // Skip ⏭
                        Button.secondary(nowPlayingButtonId("stop"), Emoji.fromUnicode("\u23F9")) // Stop ⏹
                ),
                ActionRow.of(
                        Button.secondary(nowPlayingButtonId("shuffle"), Emoji.fromUnicode("\uD83D\uDD00")), // Shuffle 🔀
                        repeatButton, // Repeat cycle
                        Button.secondary(nowPlayingButtonId("voldown"), Emoji.fromUnicode("\uD83D\uDD09")), // Vol Down 🔉
                        Button.secondary(nowPlayingButtonId("volup"), Emoji.fromUnicode("\uD83D\uDD0A")) // Vol Up 🔊
                )
        );
    }

    private static String nowPlayingButtonId(String action) {
        return NP_PREFIX + action;
    }
}
