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
package com.jagrosh.jmusicbot.service;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.commands.v1.DJCommand;
import com.jagrosh.jmusicbot.queue.AbstractQueue;
import com.jagrosh.jmusicbot.settings.QueueType;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.jagrosh.jmusicbot.utils.TimeUtil;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Unified service for all music operations including player control and queue management.
 * This service encapsulates all interactions with AudioHandler.
 */
public class MusicService
{
    private final Bot bot;

    public MusicService(Bot bot)
    {
        this.bot = bot;
    }

    // ========== Shared Track Utilities ==========

    /**
     * Checks if a track exceeds the maximum allowed duration.
     *
     * @param track The track to check
     * @return true if the track is too long
     */
    public boolean isTooLong(AudioTrack track)
    {
        return bot.getConfig().isTooLong(track);
    }

    /**
     * Formats an error message for a track that is too long.
     *
     * @param track The track that is too long
     * @return Formatted error message
     */
    public String formatTooLongError(AudioTrack track)
    {
        String title = FormatUtil.getTrackTitle(track);
        return "This track (**" + title + "**) is longer than the allowed maximum: `"
                + TimeUtil.formatTime(track.getDuration()) + "` > `" + bot.getConfig().getMaxTime() + "`";
    }

    /**
     * Formats a success message for a track that was added to the queue.
     *
     * @param title    The track title
     * @param duration The track duration in milliseconds
     * @param position The queue position (0 = now playing, >0 = queue position)
     * @return Formatted success message
     */
    public String formatTrackAddedMessage(String title, long duration, int position)
    {
        return "Added **" + FormatUtil.filter(title) + "** (`" + TimeUtil.formatTime(duration) + "`) "
                + (position == 0 ? "to begin playing" : " to the queue at position " + position);
    }

    /**
     * Adds a track to the queue and returns the result.
     *
     * @param guild     The guild
     * @param member    The member adding the track
     * @param track     The track to add
     * @param queryArgs The original query/args used to find this track
     * @param channel   The text channel for request metadata
     * @return TrackAddResult containing position and formatted message, or null if track is too long
     */
    public TrackAddResult addTrackToQueue(Guild guild, Member member, AudioTrack track,
                                          String queryArgs, TextChannel channel)
    {
        if (isTooLong(track))
        {
            return null;
        }

        AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
        handler.setLastReason(member.getUser().getName() + " added to the queue.");
        int position = handler.addTrack(new QueuedTrack(track,
                new RequestMetadata(member.getUser(),
                        new RequestMetadata.RequestInfo(queryArgs, track.getInfo().uri),
                        channel.getIdLong()))) + 1;

        String title = FormatUtil.getTrackTitle(track);
        String message = formatTrackAddedMessage(title, track.getDuration(), position);
        return new TrackAddResult(position, message, title);
    }

    // ========== Player Operations ==========

    public void play(Guild guild, Member member, String args, TextChannel channel, OutputAdapter output)
    {
        if (args != null && args.startsWith("\"") && args.endsWith("\""))
            args = args.substring(1, args.length() - 1);

        if (args == null || args.isEmpty())
        {
            AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
            if (handler.getPlayer().getPlayingTrack() != null && handler.getPlayer().isPaused())
            {
                if (DJCommand.checkDJPermission(bot, guild, member))
                {
                    handler.getPlayer().setPaused(false);
                    output.replySuccess("Resumed **" + handler.getPlayer().getPlayingTrack().getInfo().title + "**.");
                }
                else
                    output.replyError("Only DJs can unpause the player!");
                return;
            }
            output.onShowHelp();
            return;
        }

        bot.getPlayerManager().loadItemOrdered(guild, args, new ResultHandler(output, guild, member, args, false, channel));
    }

    public void previous(Guild guild, Member member, OutputAdapter output)
    {
        AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
        boolean isDJ = DJCommand.checkDJPermission(bot, guild, member);

        if (!isDJ && handler.getRequestMetadata().getOwner() != member.getIdLong())
        {
            output.replyError("You need to be a DJ or the requester to go back!");
            return;
        }
        AudioTrack playing = handler.getPlayer().getPlayingTrack();

        if (playing != null && playing.getPosition() > 5000)
        {
            playing.setPosition(0);
            output.replySuccess("Restarted **" + playing.getInfo().title + "**");
            return;
        }

        if (handler.getQueue().getHistory().isEmpty())
        {
            output.replyError("There are no previous tracks!");
            return;
        }

        AudioTrack currentlyPlaying = handler.getPlayer().getPlayingTrack();
        QueuedTrack currentQueued = currentlyPlaying != null
                ? new QueuedTrack(currentlyPlaying.makeClone(), handler.getRequestMetadata())
                : null;

        QueuedTrack previous = handler.getQueue().rewind(currentQueued);
        if (previous != null)
        {
            handler.getPlayer().playTrack(previous.getTrack());
            output.replySuccess("Went back to **" + previous.getTrack().getInfo().title + "**");
        }
        else
        {
            output.replyError("There are no previous tracks!");
        }
    }

    public void shuffle(Guild guild, Member member, int startIndex, OutputAdapter output)
    {
        AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
        boolean isDJ = DJCommand.checkDJPermission(bot, guild, member);

        if (!isDJ)
        {
            output.replyError("You need to be a DJ to use this button!");
            return;
        }
        int s = handler.getQueue().shuffle(startIndex);
        output.replySuccess("Shuffled " + s + " tracks!");
    }

    public void cycleRepeatMode(Guild guild, Member member, OutputAdapter output)
    {
        AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
        boolean isDJ = DJCommand.checkDJPermission(bot, guild, member);

        if (!isDJ)
        {
            output.replyError("You need to be a DJ to use this button!");
            return;
        }
        RepeatMode mode = bot.getSettingsManager().getSettings(guild).getRepeatMode();
        RepeatMode nextMode;
        switch (mode) {
            case OFF:
                nextMode = RepeatMode.ALL;
                break;
            case ALL:
                nextMode = RepeatMode.SINGLE;
                break;
            case SINGLE:
            default:
                nextMode = RepeatMode.OFF;
                break;
        }
        bot.getSettingsManager().getSettings(guild).setRepeatMode(nextMode);
        output.editNowPlaying(handler);
    }

    public void adjustVolume(Guild guild, Member member, int change, OutputAdapter output)
    {
        AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
        boolean isDJ = DJCommand.checkDJPermission(bot, guild, member);

        if (!isDJ)
        {
            output.replyError("You need to be a DJ to use this button!");
            return;
        }
        int newVol = handler.getPlayer().getVolume() + change;
        newVol = Math.max(0, Math.min(150, newVol));
        handler.getPlayer().setVolume(newVol);
        bot.getSettingsManager().getSettings(guild).setVolume(newVol);
        output.editNowPlaying(handler);
    }

    public void stop(Guild guild, Member member, OutputAdapter output)
    {
        AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
        boolean isDJ = DJCommand.checkDJPermission(bot, guild, member);

        if (!isDJ)
        {
            output.replyError("You need to be a DJ to use this button!");
            return;
        }
        handler.stopAndClear();
        guild.getAudioManager().closeAudioConnection();
        output.editNoMusic(handler);
    }

    public void pause(Guild guild, Member member, OutputAdapter output)
    {
        AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
        boolean isDJ = DJCommand.checkDJPermission(bot, guild, member);

        if (!isDJ)
        {
            output.replyError("You need to be a DJ to use this button!");
            return;
        }
        handler.getPlayer().setPaused(!handler.getPlayer().isPaused());
        output.editNowPlaying(handler);
    }

    public void skip(Guild guild, Member member, OutputAdapter output)
    {
        AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
        boolean isDJ = DJCommand.checkDJPermission(bot, guild, member);

        RequestMetadata skipRm = handler.getRequestMetadata();
        if (!isDJ && skipRm.getOwner() != member.getIdLong())
        {
            output.replyError("You need to be a DJ or the requester to skip!");
            return;
        }
        if (bot.getSettingsManager().getSettings(guild).getRepeatMode() == RepeatMode.ALL)
        {
            var track = handler.getPlayer().getPlayingTrack();
            if (track != null)
                handler.addTrack(new QueuedTrack(track.makeClone(), track.getUserData(RequestMetadata.class)));
        }
        handler.setLastReason(member.getUser().getName() + " skipped forward.");
        handler.getPlayer().stopTrack();
        output.replySuccess("Skipped!");
    }

    public void skipWithVote(Guild guild, Member member, int listeners, OutputAdapter output)
    {
        AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
        RequestMetadata rm = handler.getRequestMetadata();

        double skipRatio = bot.getSettingsManager().getSettings(guild).getSkipRatio();
        if (skipRatio == -1)
        {
            skipRatio = bot.getConfig().getSkipRatio();
        }

        if (member.getIdLong() == rm.getOwner() || skipRatio == 0)
        {
            handler.getPlayer().stopTrack();
            output.replySuccess("Skipped **" + handler.getPlayer().getPlayingTrack().getInfo().title + "**");
            return;
        }

        String oderId = member.getId();
        boolean alreadyVoted = handler.getVotes().contains(oderId);

        if (!alreadyVoted)
        {
            handler.getVotes().add(oderId);
        }

        int skippers = (int) handler.getVotes().stream()
                .filter(id -> guild.getMemberById(id) != null &&
                        guild.getMemberById(id).getVoiceState() != null &&
                        guild.getMemberById(id).getVoiceState().getChannel() != null)
                .count();
        int required = (int) Math.ceil(listeners * skipRatio);

        String voteStatus = "[" + skippers + " votes, " + required + "/" + listeners + " needed]";

        if (alreadyVoted)
        {
            output.replyWarning("You already voted to skip this song `" + voteStatus + "`");
        }
        else if (skippers >= required)
        {
            String trackTitle = handler.getPlayer().getPlayingTrack().getInfo().title;
            String requester = rm.getOwner() == 0L ? "(autoplay)" : "(requested by **" + FormatUtil.formatUsername(rm.user) + "**)";
            handler.getPlayer().stopTrack();
            output.replySuccess("You voted to skip the song `" + voteStatus + "`\nSkipped **" + trackTitle + "** " + requester);
        }
        else
        {
            output.replySuccess("You voted to skip the song `" + voteStatus + "`");
        }
    }

    public void seek(Guild guild, Member member, String timeString, OutputAdapter output)
    {
        AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
        AudioTrack playingTrack = handler.getPlayer().getPlayingTrack();

        if (playingTrack == null)
        {
            output.replyError("There is no track currently playing!");
            return;
        }

        if (!playingTrack.isSeekable())
        {
            output.replyError("This track is not seekable.");
            return;
        }

        boolean isDJ = DJCommand.checkDJPermission(bot, guild, member);
        RequestMetadata rm = playingTrack.getUserData(RequestMetadata.class);
        if (!isDJ && (rm == null || rm.getOwner() != member.getIdLong()))
        {
            output.replyError("You cannot seek **" + playingTrack.getInfo().title + "** because you didn't add it!");
            return;
        }

        TimeUtil.SeekTime seekTime = TimeUtil.parseTime(timeString);
        if (seekTime == null)
        {
            output.replyError("Invalid seek! Expected format: [+ | -] <HH:MM:SS | MM:SS | SS> or <0h0m0s>\nExamples: `1:02:23` `+1:10` `-90`, `1h10m`, `+90s`");
            return;
        }

        long currentPosition = playingTrack.getPosition();
        long trackDuration = playingTrack.getDuration();
        long seekMilliseconds = seekTime.relative ? currentPosition + seekTime.milliseconds : seekTime.milliseconds;

        if (seekMilliseconds < 0)
        {
            seekMilliseconds = 0;
        }
        if (seekMilliseconds > trackDuration)
        {
            output.replyError("Cannot seek to `" + TimeUtil.formatTime(seekMilliseconds) + "` because the current track is `" + TimeUtil.formatTime(trackDuration) + "` long!");
            return;
        }

        try
        {
            playingTrack.setPosition(seekMilliseconds);
            output.replySuccess("Successfully seeked to `" + TimeUtil.formatTime(playingTrack.getPosition()) + "/" + TimeUtil.formatTime(trackDuration) + "`!");
        }
        catch (Exception e)
        {
            output.replyError("An error occurred while trying to seek: " + e.getMessage());
        }
    }

    // ========== Queue Operations ==========

    public void removeTrack(Guild guild, Member member, int position, OutputAdapter output)
    {
        AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();

        if (handler.getQueue().isEmpty())
        {
            output.replyError("There is nothing in the queue!");
            return;
        }

        if (position < 1 || position > handler.getQueue().size())
        {
            output.replyError("Position must be a valid integer between 1 and " + handler.getQueue().size() + "!");
            return;
        }

        boolean isDJ = DJCommand.checkDJPermission(bot, guild, member);
        QueuedTrack qt = handler.getQueue().get(position - 1);

        if (qt.getIdentifier() == member.getIdLong())
        {
            handler.getQueue().remove(position - 1);
            output.replySuccess("Removed **" + qt.getTrack().getInfo().title + "** from the queue");
        }
        else if (isDJ)
        {
            handler.getQueue().remove(position - 1);
            User u = null;
            try
            {
                u = guild.getJDA().getUserById(qt.getIdentifier());
            }
            catch (Exception ignored) {}

            output.replySuccess("Removed **" + qt.getTrack().getInfo().title
                    + "** from the queue (requested by " + (u == null ? "someone" : "**" + u.getName() + "**") + ")");
        }
        else
        {
            output.replyError("You cannot remove **" + qt.getTrack().getInfo().title + "** because you didn't add it!");
        }
    }

    public void removeAllTracks(Guild guild, Member member, OutputAdapter output)
    {
        AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();

        if (handler.getQueue().isEmpty())
        {
            output.replyError("There is nothing in the queue!");
            return;
        }

        int count = handler.getQueue().removeAll(member.getIdLong());
        if (count == 0)
        {
            output.replyWarning("You don't have any songs in the queue!");
        }
        else
        {
            output.replySuccess("Successfully removed your " + count + " entries.");
        }
    }

    public void moveTrack(Guild guild, Member member, int from, int to, OutputAdapter output)
    {
        boolean isDJ = DJCommand.checkDJPermission(bot, guild, member);
        if (!isDJ)
        {
            output.replyError("You need to be a DJ to move tracks!");
            return;
        }

        if (from == to)
        {
            output.replyError("Can't move a track to the same position.");
            return;
        }

        AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
        AbstractQueue<QueuedTrack> queue = handler.getQueue();

        if (isInvalidPosition(queue, from))
        {
            output.replyError("`" + from + "` is not a valid position in the queue!");
            return;
        }
        if (isInvalidPosition(queue, to))
        {
            output.replyError("`" + to + "` is not a valid position in the queue!");
            return;
        }

        QueuedTrack track = queue.moveItem(from - 1, to - 1);
        String trackTitle = track.getTrack().getInfo().title;
        output.replySuccess("Moved **" + trackTitle + "** from position `" + from + "` to `" + to + "`.");
    }

    public void skipTo(Guild guild, Member member, int position, OutputAdapter output)
    {
        boolean isDJ = DJCommand.checkDJPermission(bot, guild, member);
        if (!isDJ)
        {
            output.replyError("You need to be a DJ to skip to a specific position!");
            return;
        }

        AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();

        if (position < 1 || position > handler.getQueue().size())
        {
            output.replyError("Position must be a valid integer between 1 and " + handler.getQueue().size() + "!");
            return;
        }

        handler.getQueue().skip(position - 1);
        String trackTitle = handler.getQueue().get(0).getTrack().getInfo().title;
        handler.getPlayer().stopTrack();
        output.replySuccess("Skipped to **" + trackTitle + "**");
    }

    // ========== Queue Info ==========

    public QueueInfo getQueueInfo(Guild guild, JDA jda)
    {
        AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
        if (handler == null)
        {
            return null;
        }

        List<QueuedTrack> list = handler.getQueue().getList();
        Settings settings = bot.getSettingsManager().getSettings(guild);

        long totalDuration = 0;
        String[] trackStrings = new String[list.size()];
        for (int i = 0; i < list.size(); i++)
        {
            totalDuration += list.get(i).getTrack().getDuration();
            trackStrings[i] = list.get(i).toString();
        }

        String nowPlayingTitle = null;
        String statusEmoji = handler.getStatusEmoji();
        if (handler.getPlayer().getPlayingTrack() != null)
        {
            nowPlayingTitle = handler.getPlayer().getPlayingTrack().getInfo().title;
        }

        return new QueueInfo(
                trackStrings,
                totalDuration,
                nowPlayingTitle,
                statusEmoji,
                settings.getRepeatMode(),
                settings.getQueueType(),
                handler.getNowPlaying(jda),
                handler.getNoMusicPlaying(jda)
        );
    }

    public String formatQueueTitle(QueueInfo info, String successEmoji)
    {
        StringBuilder sb = new StringBuilder();
        if (info.nowPlayingTitle != null)
        {
            sb.append(info.statusEmoji).append(" **").append(info.nowPlayingTitle).append("**\n");
        }

        return FormatUtil.filter(sb.append(successEmoji).append(" Current Queue | ").append(info.tracks.length)
                .append(" entries | `").append(TimeUtil.formatTime(info.totalDuration)).append("` ")
                .append("| ").append(info.queueType.getEmoji()).append(" `").append(info.queueType.getUserFriendlyName()).append('`')
                .append(info.repeatMode.getEmoji() != null ? " | " + info.repeatMode.getEmoji() : "").toString());
    }

    private boolean isInvalidPosition(AbstractQueue<QueuedTrack> queue, int position)
    {
        return position < 1 || position > queue.size();
    }

    // ========== Inner Classes ==========

    /**
     * Result of adding a track to the queue.
     */
    public static class TrackAddResult
    {
        public final int position;
        public final String formattedMessage;
        public final String trackTitle;

        public TrackAddResult(int position, String formattedMessage, String trackTitle)
        {
            this.position = position;
            this.formattedMessage = formattedMessage;
            this.trackTitle = trackTitle;
        }
    }

    /**
     * Data class containing queue information for display.
     */
    public static class QueueInfo
    {
        public final String[] tracks;
        public final long totalDuration;
        public final String nowPlayingTitle;
        public final String statusEmoji;
        public final RepeatMode repeatMode;
        public final QueueType queueType;
        public final Object nowPlayingMessage;
        public final Object noMusicMessage;

        public QueueInfo(String[] tracks, long totalDuration, String nowPlayingTitle, String statusEmoji,
                         RepeatMode repeatMode, QueueType queueType, Object nowPlayingMessage, Object noMusicMessage)
        {
            this.tracks = tracks;
            this.totalDuration = totalDuration;
            this.nowPlayingTitle = nowPlayingTitle;
            this.statusEmoji = statusEmoji;
            this.repeatMode = repeatMode;
            this.queueType = queueType;
            this.nowPlayingMessage = nowPlayingMessage;
            this.noMusicMessage = noMusicMessage;
        }

        public boolean isEmpty()
        {
            return tracks.length == 0;
        }
    }

    /**
     * Adapter interface for abstracting output operations.
     * <p>
     * This interface allows services to be command-type agnostic - the same service
     * methods work for text commands, slash commands, and button interactions.
     * Each command type provides its own implementation.
     *
     * @see com.jagrosh.jmusicbot.commands.BaseOutputAdapter
     * @see com.jagrosh.jmusicbot.commands.v1.TextOutputAdapters
     * @see com.jagrosh.jmusicbot.commands.v2.SlashOutputAdapters
     */
    public interface OutputAdapter
    {
        void replySuccess(String content);
        void replyError(String content);
        void replyWarning(String content);
        void editMessage(String content);
        void editMessage(String content, Consumer<Message> onSuccess);
        void editNowPlaying(AudioHandler handler);
        void editNoMusic(AudioHandler handler);
        void onShowHelp();
    }

    private class ResultHandler implements AudioLoadResultHandler
    {
        private final static String LOAD = "\uD83D\uDCE5"; // 📥
        private final static String CANCEL = "\uD83D\uDEAB"; // 🚫

        private final OutputAdapter output;
        private final Guild guild;
        private final Member member;
        private final String args;
        private final boolean ytsearch;
        private final TextChannel channel;

        private ResultHandler(OutputAdapter output, Guild guild, Member member, String args, boolean ytsearch, TextChannel channel)
        {
            this.output = output;
            this.guild = guild;
            this.member = member;
            this.args = args;
            this.ytsearch = ytsearch;
            this.channel = channel;
        }

        private void loadSingle(AudioTrack track, AudioPlaylist playlist)
        {
            TrackAddResult result = addTrackToQueue(guild, member, track, args, channel);
            if (result == null)
            {
                output.editMessage(FormatUtil.filter(bot.getConfig().getWarning() + " " + formatTooLongError(track)));
                return;
            }

            String addMsg = FormatUtil.filter(bot.getConfig().getSuccess() + " " + result.formattedMessage);
            if (playlist == null || !guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_ADD_REACTION))
                output.editMessage(addMsg);
            else
            {
                String promptMsg = addMsg + "\n" + bot.getConfig().getWarning() + " This track has a playlist of **" + playlist.getTracks().size() + "** tracks attached. Select " + LOAD + " to load playlist.";

                MessageEditBuilder editBuilder = new MessageEditBuilder()
                        .setContent(promptMsg)
                        .setComponents(ActionRow.of(
                                Button.success("load_playlist", Emoji.fromUnicode(LOAD)).withLabel("Load Playlist"),
                                Button.danger("cancel_playlist", Emoji.fromUnicode(CANCEL)).withLabel("Cancel")
                        ));

                output.editMessage(addMsg, m -> {
                    m.editMessage(editBuilder.build()).queue(msg -> {
                        bot.getWaiter().waitForEvent(ButtonInteractionEvent.class,
                                event -> event.getMessageId().equals(msg.getId()) &&
                                        (event.getComponentId().equals("load_playlist") || event.getComponentId().equals("cancel_playlist")) &&
                                        event.getUser().getIdLong() == member.getIdLong(),
                                event -> {
                                    if (event.getComponentId().equals("load_playlist"))
                                    {
                                        int loaded = loadPlaylist(playlist, track);
                                        event.editMessage(addMsg + "\n" + bot.getConfig().getSuccess() + " Loaded **" + loaded + "** additional tracks!").setComponents().queue();
                                    }
                                    else
                                    {
                                        event.editMessage(addMsg).setComponents().queue();
                                    }
                                },
                                30, TimeUnit.SECONDS,
                                () -> msg.editMessage(addMsg).setComponents().queue());
                    });
                });
            }
        }

        private int loadPlaylist(AudioPlaylist playlist, AudioTrack exclude)
        {
            int[] count = {0};
            AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
            playlist.getTracks().forEach((track) -> {
                if (!isTooLong(track) && !track.equals(exclude))
                {
                    handler.setLastReason(member.getUser().getName() + " added a playlist.");
                    handler.addTrack(new QueuedTrack(track,
                            new RequestMetadata(member.getUser(),
                                    new RequestMetadata.RequestInfo(args, track.getInfo().uri),
                                    channel.getIdLong())));
                    count[0]++;
                }
            });
            return count[0];
        }

        @Override
        public void trackLoaded(AudioTrack track)
        {
            loadSingle(track, null);
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist)
        {
            if (playlist.getTracks().size() == 1 || playlist.isSearchResult())
            {
                AudioTrack single = playlist.getSelectedTrack() == null ? playlist.getTracks().get(0) : playlist.getSelectedTrack();
                loadSingle(single, null);
            }
            else if (playlist.getSelectedTrack() != null)
            {
                AudioTrack single = playlist.getSelectedTrack();
                loadSingle(single, playlist);
            }
            else
            {
                int count = loadPlaylist(playlist, null);
                if (playlist.getTracks().size() == 0)
                {
                    output.editMessage(FormatUtil.filter(bot.getConfig().getWarning() + " The playlist " + (playlist.getName() == null ? "" : "(**" + playlist.getName()
                            + "**) ") + " could not be loaded or contained 0 entries"));
                }
                else if (count == 0)
                {
                    output.editMessage(FormatUtil.filter(bot.getConfig().getWarning() + " All entries in this playlist " + (playlist.getName() == null ? "" : "(**" + playlist.getName()
                            + "**) ") + "were longer than the allowed maximum (`" + bot.getConfig().getMaxTime() + "`)"));
                }
                else
                {
                    output.editMessage(FormatUtil.filter(bot.getConfig().getSuccess() + " Found "
                            + (playlist.getName() == null ? "a playlist" : "playlist **" + playlist.getName() + "**") + " with `"
                            + playlist.getTracks().size() + "` entries; added to the queue!"
                            + (count < playlist.getTracks().size() ? "\n" + bot.getConfig().getWarning() + " Tracks longer than the allowed maximum (`"
                            + bot.getConfig().getMaxTime() + "`) have been omitted." : "")));
                }
            }
        }

        @Override
        public void noMatches()
        {
            if (ytsearch)
                output.editMessage(FormatUtil.filter(bot.getConfig().getWarning() + " No results found for `" + args + "`."));
            else
                bot.getPlayerManager().loadItemOrdered(guild, "ytsearch:" + args, new ResultHandler(output, guild, member, args, true, channel));
        }

        @Override
        public void loadFailed(FriendlyException throwable)
        {
            if (throwable.severity == Severity.COMMON)
                output.editMessage(bot.getConfig().getError() + " Error loading: " + throwable.getMessage());
            else
                output.editMessage(bot.getConfig().getError() + " Error loading track.");
        }
    }
}
