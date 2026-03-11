package com.jagrosh.jmusicbot.utils;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.NowPlayingInfo;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

public class MessageFormatter {

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

        // Handle local file names using the util method
        String title = FormatUtil.getTrackTitle(info.track);

        try {
            eb.setTitle(title, info.track.getInfo().uri);
        } catch (Exception e) {
            eb.setTitle(title);
        }

        String rawAuthor = info.track.getInfo().author;
        String author = rawAuthor == null ? null : FormatUtil.filter(FormatUtil.fixMojibakeUtf8AsLatin1(rawAuthor));
        if (author != null && (!author.isEmpty() && !author.equalsIgnoreCase("unknown artist"))) {
            eb.addField("Author", author, false);
        }

        StringBuilder description = new StringBuilder();
        
        // Add progress bar if enabled
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
        
        description.append("**Playing from:** ").append(info.track.getSourceManager().getSourceName());
        eb.setDescription(description.toString());

        eb.addField("Duration", TimeUtil.formatTime(info.duration), true);
        eb.addField("Queue", String.valueOf(info.queueSize), true);
        eb.addField("Volume", info.volume + "%", true);

        RepeatMode repeatMode = bot.getSettingsManager().getSettings(info.guild).getRepeatMode();
        if (repeatMode != RepeatMode.OFF) {
            eb.addField("Repeat", repeatMode.getEmoji() + " " + repeatMode.getUserFriendlyName(), true);
        }

        RequestMetadata rm = info.track.getUserData(RequestMetadata.class);
        if (rm != null && rm.getOwner() != 0L) {
            User u = info.guild.getJDA().getUserById(rm.user.id);
            String requester = (u == null) ? FormatUtil.formatUsername(rm.user) : u.getAsMention();
            eb.addField("Requester", requester, false);
        }

        if (!(info.track instanceof LocalAudioTrack)  && bot.getConfig().useNPImages()) {
            var thumbnailUrl = info.track.getInfo().artworkUrl;
            if (thumbnailUrl == null || thumbnailUrl.isEmpty())
                thumbnailUrl = "https://img.youtube.com/vi/" + info.track.getIdentifier() + "/mqdefault.jpg";
            eb.setThumbnail(thumbnailUrl);
        }

        if (info.footerInfo != null && !info.footerInfo.isEmpty())
            eb.setFooter(info.footerInfo);

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

        String title = FormatUtil.getTrackTitle(info.track);
        try {
            eb.setTitle(title, info.track.getInfo().uri);
        } catch (Exception e) {
            eb.setTitle(title);
        }

        String description;
        if (bot.getConfig().showNpProgressBar()) {
            double progress = info.duration > 0 ? (double) info.position / info.duration : 0;
            String statusEmoji = info.isPaused ? AudioHandler.PAUSE_EMOJI : AudioHandler.PLAY_EMOJI;
            description = statusEmoji + " " + FormatUtil.progressBar(progress)
                    + " `[" + TimeUtil.formatTime(info.position) + "/" + TimeUtil.formatTime(info.duration) + "]` "
                    + FormatUtil.volumeIcon(info.volume);
        } else {
            String statusEmoji = info.isPaused ? AudioHandler.PAUSE_EMOJI : AudioHandler.PLAY_EMOJI;
            description = statusEmoji + " " + FormatUtil.volumeIcon(info.volume);
        }
        eb.setDescription(description);

        String sourceName = info.track.getSourceManager() != null
                ? info.track.getSourceManager().getSourceName()
                : "Unknown";
        String footerText = "Source: " + sourceName;
        if (info.footerInfo != null && !info.footerInfo.isEmpty()) {
            footerText += " • " + info.footerInfo;
        }
        eb.setFooter(footerText);

        mb.setEmbeds(eb.build());

        if (showButtons) {
            RepeatMode repeatMode = bot.getSettingsManager().getSettings(info.guild).getRepeatMode();
            applyNowPlayingButtons(mb, info, repeatMode);
        }

        return mb.build();
    }

    public static MessageCreateData buildNoMusicPlayingMessage(Bot bot, NowPlayingInfo info) {
        Settings settings = bot.getSettingsManager().getSettings(info.guild);
        boolean minimalMessage = settings.useMinimalNowPlayingMessage(bot.getConfig());

        if (minimalMessage) {
            return buildNoMusicPlayingMessageMinimal(bot, info);
        }

        String descriptionText;
        if (bot.getConfig().showNpProgressBar()) {
            // Show progress bar in "no music" state (all segments empty)
            descriptionText = AudioHandler.STOP_EMOJI + " " + FormatUtil.progressBar(-1) + " " + FormatUtil.volumeIcon(info.volume);
        } else {
            descriptionText = AudioHandler.STOP_EMOJI + " " + FormatUtil.volumeIcon(info.volume);
        }
        
        return new MessageCreateBuilder()
                .setContent(FormatUtil.filter(bot.getConfig().getSuccess() + " **Now Playing...**"))
                .setEmbeds(new EmbedBuilder()
                        .setTitle("No music playing")
                        .setDescription(descriptionText)
                        .setColor(info.guild.getSelfMember().getColors().getPrimary())
                        .build()).build();
    }

    private static MessageCreateData buildNoMusicPlayingMessageMinimal(Bot bot, NowPlayingInfo info) {
        String descriptionText;
        if (bot.getConfig().showNpProgressBar()) {
            descriptionText = AudioHandler.STOP_EMOJI + " " + FormatUtil.progressBar(-1)
                    + " " + FormatUtil.volumeIcon(info.volume);
        } else {
            descriptionText = AudioHandler.STOP_EMOJI + " " + FormatUtil.volumeIcon(info.volume);
        }
        return new MessageCreateBuilder()
                .setContent(FormatUtil.filter(bot.getConfig().getSuccess() + " **Now Playing in** " + getNowPlayingLocationName(info)))
                .setEmbeds(new EmbedBuilder()
                        .setTitle("No music playing")
                        .setDescription(descriptionText)
                        .setColor(info.guild.getSelfMember().getColors().getPrimary())
                        .build())
                .build();
    }

    private static String getNowPlayingLocationName(NowPlayingInfo info) {
        if (info.guild.getSelfMember() != null
                && info.guild.getSelfMember().getVoiceState() != null
                && info.guild.getSelfMember().getVoiceState().getChannel() != null) {
            return info.guild.getSelfMember().getVoiceState().getChannel().getName();
        }
        return info.guild.getName();
    }

    private static void applyNowPlayingButtons(MessageCreateBuilder mb, NowPlayingInfo info, RepeatMode repeatMode) {
        Button repeatButton = switch (repeatMode) {
            case ALL -> Button.primary("repeat", Emoji.fromUnicode("\uD83D\uDD01")); // 🔁
            case SINGLE -> Button.primary("repeat", Emoji.fromUnicode("\uD83D\uDD02")); // 🔂
            default -> Button.secondary("repeat", Emoji.fromUnicode("\uD83D\uDD01")); // 🔁
        };

        mb.setComponents(
                ActionRow.of(
                        Button.secondary("previous", Emoji.fromUnicode("\u23EE")), // Previous ⏮
                        info.isPaused
                                ? Button.primary("pause", Emoji.fromUnicode("\u25B6"))    // Pause ⏸
                                : Button.secondary("pause", Emoji.fromUnicode("\u23F8")), // or Resume ▶
                        Button.secondary("skip", Emoji.fromUnicode("\u23ED")), // Skip ⏭
                        Button.secondary("stop", Emoji.fromUnicode("\u23F9")) // Stop ⏹
                ),
                ActionRow.of(
                        Button.secondary("shuffle", Emoji.fromUnicode("\uD83D\uDD00")), // Shuffle 🔀
                        repeatButton, // Repeat cycle
                        Button.secondary("voldown", Emoji.fromUnicode("\uD83D\uDD09")), // Vol Down 🔉
                        Button.secondary("volup", Emoji.fromUnicode("\uD83D\uDD0A")) // Vol Up 🔊
                )
        );
    }
}
