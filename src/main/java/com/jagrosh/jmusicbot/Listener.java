/*
 * Copyright 2016 John Grosh <john.a.grosh@gmail.com>.
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
package com.jagrosh.jmusicbot;

import com.jagrosh.jmusicbot.audio.AudioHandler;

import com.jagrosh.jmusicbot.commands.SlashCommandRegistry;
import com.jagrosh.jmusicbot.commands.v2.music.HistorySlashCmd;
import com.jagrosh.jmusicbot.commands.v2.music.QueueSlashCmd;
import com.jagrosh.jmusicbot.entities.UserInteraction.Level;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import com.jagrosh.jmusicbot.utils.YoutubeOauth2TokenHandler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.CloseCode;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import com.jagrosh.jmusicbot.service.MusicService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 *
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public class Listener extends ListenerAdapter
{
    private final Bot bot;
    
    public Listener(Bot bot)
    {
        this.bot = bot;
    }

    @Override
    public void onReady(ReadyEvent event)
    {
        if(event.getJDA().getGuildCache().isEmpty())
        {
            Logger log = LoggerFactory.getLogger("MusicBot");
            String inviteUrl = event.getJDA().getInviteUrl(JMusicBot.RECOMMENDED_PERMS);
            log.warn("This bot is not on any guilds! Use the following link to add the bot to your guilds!");
            log.warn(inviteUrl);
            bot.getUserInteraction().alert(Level.WARNING, "Setup",
                    "This bot is not on any guilds!\n\nUse this link to add the bot to your server:\n" + inviteUrl);
        }
        
        // Register slash commands if they have changed
        if(bot.getCommandClient() != null)
        {
            SlashCommandRegistry.registerIfChanged(event.getJDA(), bot.getCommandClient());
        }
        
        credit(event.getJDA());
        event.getJDA().getGuilds().forEach((Guild guild) ->
        {
            try
            {
                String defpl = bot.getSettingsManager().getSettings(guild).getDefaultPlaylist();
                VoiceChannel vc = bot.getSettingsManager().getSettings(guild).getVoiceChannel(guild);
                if(defpl!=null && vc!=null && bot.getPlayerManager().setUpHandler(guild).playFromDefault())
                {
                    guild.getAudioManager().openAudioConnection(vc);
                }
            }
            catch(Exception ignore) {}
        });
        if(bot.getConfig().useUpdateAlerts())
        {
            bot.getThreadpool().scheduleWithFixedDelay(() -> 
            {
                try
                {
                    User owner = bot.getJDA().retrieveUserById(bot.getConfig().getOwnerId()).complete();
                    String currentVersion = OtherUtil.getCurrentVersion();
                    // Use proxy-aware version check if proxy is configured for GitHub
                    String latestVersion = OtherUtil.getLatestVersion(bot.getConfig());
                    if(latestVersion != null && OtherUtil.isNewerVersion(currentVersion, latestVersion))
                    {
                        String msg = String.format(OtherUtil.NEW_VERSION_AVAILABLE, currentVersion, latestVersion);
                        owner.openPrivateChannel().queue(pc -> pc.sendMessage(msg).queue());
                    }
                }
                catch(Exception ignored) {} // ignored
            }, 0, 24, TimeUnit.HOURS);
        }
        if (bot.getConfig().useYouTubeOauth())
        {
            YoutubeOauth2TokenHandler.Data data = bot.getYouTubeOauth2Handler().getData();
            if (data != null)
            {
                try
                {
                    PrivateChannel channel = bot.getJDA().openPrivateChannelById(bot.getConfig().getOwnerId()).complete();
                    channel
                            .sendMessage(
                                    "# DO NOT AUTHORISE THIS WITH YOUR MAIN GOOGLE ACCOUNT!!!\n"
                                            + "## Create or use an alternative/burner Google account!\n"
                                            + "To give JMusicBot access to your Google account, go to "
                                            + data.getAuthorisationUrl()
                                            + " and enter the code **" + data.getCode() + "**")
                            .queue();
                }
                catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void onMessageDelete(@NotNull MessageDeleteEvent event)
    {
        if(event.isFromGuild())
            bot.getNowplayingHandler().onMessageDelete(event.getGuild(), event.getMessageIdLong());
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event)
    {
        String componentId = event.getComponentId();

        // Handle queue interactions separately
        if (componentId.startsWith("queue_"))
        {
            handleQueueInteraction(event);
            return;
        }

        // Handle history interactions separately
        if (componentId.startsWith("history_"))
        {
            handleHistoryInteraction(event);
            return;
        }

        if (!componentId.equals("stop") && !componentId.equals("pause") && !componentId.equals("skip")
                && !componentId.equals("previous") && !componentId.equals("shuffle")
                && !componentId.equals("repeat") && !componentId.equals("voldown")
                && !componentId.equals("volup"))
            return;

        if (event.getGuild() == null || event.getMember() == null) return;

        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        if (handler == null)
        {
            event.reply("There is no music playing!").setEphemeral(true).queue();
            return;
        }

        // Permissions check
        if (!event.getMember().getVoiceState().inAudioChannel() ||
                !event.getMember().getVoiceState().getChannel().equals(event.getGuild().getSelfMember().getVoiceState().getChannel()))
        {
            event.reply("You must be in the same voice channel to use this!").setEphemeral(true).queue();
            return;
        }

        MusicService musicService = bot.getMusicService();
        MusicService.OutputAdapter adapter = new MusicService.OutputAdapter() {
            @Override
            public void replySuccess(String content) {
                event.reply(content).setEphemeral(true).queue();
            }

            @Override
            public void replyError(String content) {
                event.reply(content).setEphemeral(true).queue();
            }

            @Override
            public void replyWarning(String content) {
                event.reply(content).setEphemeral(true).queue();
            }

            @Override
            public void editMessage(String content) {
                event.editMessage(content).queue();
            }

            @Override
            public void editMessage(String content, Consumer<net.dv8tion.jda.api.entities.Message> onSuccess) {
                event.editMessage(content).queue(hook -> hook.retrieveOriginal().queue(onSuccess));
            }

            @Override
            public void editNowPlaying(AudioHandler handler) {
                event.editMessage(MessageEditData.fromCreateData(handler.getNowPlaying(event.getJDA()))).queue();
            }

            @Override
            public void editNoMusic(AudioHandler handler) {
                event.editMessage(MessageEditData.fromCreateData(handler.getNoMusicPlaying(event.getJDA()))).queue();
            }

            @Override
            public void onShowHelp() {
                // Not used for buttons
            }
        };

        switch (event.getComponentId())
        {
            case "previous":
                musicService.previous(event.getGuild(), event.getMember(), adapter);
                break;
            case "shuffle":
                musicService.shuffle(event.getGuild(), event.getMember(), 0, adapter);
                break;
            case "repeat":
                musicService.cycleRepeatMode(event.getGuild(), event.getMember(), adapter);
                break;
            case "voldown":
                musicService.adjustVolume(event.getGuild(), event.getMember(), -10, adapter);
                break;
            case "volup":
                musicService.adjustVolume(event.getGuild(), event.getMember(), 10, adapter);
                break;
            case "stop":
                musicService.stop(event.getGuild(), event.getMember(), adapter);
                break;
            case "pause":
                musicService.pause(event.getGuild(), event.getMember(), adapter);
                break;
            case "skip":
                musicService.skip(event.getGuild(), event.getMember(), adapter);
                break;
        }
    }

    /**
     * Handles button interactions for the interactive queue embed.
     * Component ID format: queue_{action}_{page}_{selectedTrack}_{userId}
     */
    private void handleQueueInteraction(ButtonInteractionEvent event)
    {
        if (event.getGuild() == null || event.getMember() == null)
        {
            event.reply("This can only be used in a server!").setEphemeral(true).queue();
            return;
        }

        String componentId = event.getComponentId();
        String[] parts = componentId.split("_");
        // Expected format: queue_action_page_selectedTrack_userId
        if (parts.length < 5)
        {
            event.reply("Invalid button state.").setEphemeral(true).queue();
            return;
        }

        String action = parts[1];
        int page;
        int selectedTrack;
        long userId;
        try
        {
            page = Integer.parseInt(parts[2]);
            selectedTrack = Integer.parseInt(parts[3]);
            userId = Long.parseLong(parts[4]);
        }
        catch (NumberFormatException e)
        {
            event.reply("Invalid button state.").setEphemeral(true).queue();
            return;
        }

        // Verify user is the one who initiated the command
        if (event.getUser().getIdLong() != userId)
        {
            event.reply("Only the user who ran the command can use these buttons!").setEphemeral(true).queue();
            return;
        }

        // Voice channel check
        if (!event.getMember().getVoiceState().inAudioChannel() ||
                event.getGuild().getSelfMember().getVoiceState().getChannel() == null ||
                !event.getMember().getVoiceState().getChannel().equals(event.getGuild().getSelfMember().getVoiceState().getChannel()))
        {
            event.reply("You must be in the same voice channel to use this!").setEphemeral(true).queue();
            return;
        }

        MusicService musicService = bot.getMusicService();
        MusicService.QueueInfo queueInfo = musicService.getQueueInfo(event.getGuild(), event.getJDA());

        if (queueInfo == null || queueInfo.isEmpty())
        {
            event.editMessage("The queue is now empty!").setEmbeds().setComponents().queue();
            return;
        }

        int totalPages = QueueSlashCmd.getTotalPages(queueInfo.tracks.length);

        // Handle different actions
        if (action.startsWith("select"))
        {
            // Extract track number from action (e.g., "select3" -> 3)
            int trackIndexOnPage = Integer.parseInt(action.substring(6));
            int newSelectedTrack = (page - 1) * QueueSlashCmd.TRACKS_PER_PAGE + trackIndexOnPage;

            // Toggle selection: if already selected, deselect
            if (newSelectedTrack == selectedTrack)
            {
                newSelectedTrack = 0;
            }

            // Validate selection is within bounds
            if (newSelectedTrack > queueInfo.tracks.length)
            {
                event.reply("That track doesn't exist!").setEphemeral(true).queue();
                return;
            }

            updateQueueEmbed(event, queueInfo, page, totalPages, newSelectedTrack, userId);
        }
        else if (action.equals("prev"))
        {
            int newPage = Math.max(1, page - 1);
            updateQueueEmbed(event, queueInfo, newPage, totalPages, 0, userId);
        }
        else if (action.equals("next"))
        {
            int newPage = Math.min(totalPages, page + 1);
            updateQueueEmbed(event, queueInfo, newPage, totalPages, 0, userId);
        }
        else if (action.equals("shuffle"))
        {
            MusicService.OutputAdapter adapter = createQueueOutputAdapter(event);
            musicService.shuffle(event.getGuild(), event.getMember(), 0, adapter);

            // Refresh queue info and update embed
            MusicService.QueueInfo newQueueInfo = musicService.getQueueInfo(event.getGuild(), event.getJDA());
            if (newQueueInfo != null && !newQueueInfo.isEmpty())
            {
                int newTotalPages = QueueSlashCmd.getTotalPages(newQueueInfo.tracks.length);
                int safePage = Math.min(page, newTotalPages);
                updateQueueEmbed(event, newQueueInfo, safePage, newTotalPages, 0, userId);
            }
        }
        else if (action.equals("remove"))
        {
            if (selectedTrack <= 0 || selectedTrack > queueInfo.tracks.length)
            {
                event.reply("No track selected!").setEphemeral(true).queue();
                return;
            }

            MusicService.OutputAdapter adapter = createQueueOutputAdapter(event);
            musicService.removeTrack(event.getGuild(), event.getMember(), selectedTrack, adapter);

            // Refresh and update
            MusicService.QueueInfo newQueueInfo = musicService.getQueueInfo(event.getGuild(), event.getJDA());
            if (newQueueInfo == null || newQueueInfo.isEmpty())
            {
                event.editMessage("The queue is now empty!").setEmbeds().setComponents().queue();
            }
            else
            {
                int newTotalPages = QueueSlashCmd.getTotalPages(newQueueInfo.tracks.length);
                int safePage = Math.min(page, newTotalPages);
                updateQueueEmbed(event, newQueueInfo, safePage, newTotalPages, 0, userId);
            }
        }
        else if (action.equals("playnext"))
        {
            if (selectedTrack <= 0 || selectedTrack > queueInfo.tracks.length)
            {
                event.reply("No track selected!").setEphemeral(true).queue();
                return;
            }

            MusicService.OutputAdapter adapter = createQueueOutputAdapter(event);
            musicService.playNext(event.getGuild(), event.getMember(), selectedTrack, adapter);

            // Refresh and update - go to page 1 since track is now at position 1
            MusicService.QueueInfo newQueueInfo = musicService.getQueueInfo(event.getGuild(), event.getJDA());
            if (newQueueInfo != null && !newQueueInfo.isEmpty())
            {
                int newTotalPages = QueueSlashCmd.getTotalPages(newQueueInfo.tracks.length);
                updateQueueEmbed(event, newQueueInfo, 1, newTotalPages, 0, userId);
            }
        }
        else if (action.equals("move"))
        {
            if (selectedTrack <= 0 || selectedTrack > queueInfo.tracks.length)
            {
                event.reply("No track selected!").setEphemeral(true).queue();
                return;
            }

            // Show position select menu
            StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("queue_move_select_" + selectedTrack + "_" + page + "_" + userId)
                    .setPlaceholder("Select new position")
                    .setMinValues(1)
                    .setMaxValues(1);

            // Add position options (limit to 25 due to Discord limits)
            int maxOptions = Math.min(queueInfo.tracks.length, 25);
            for (int i = 1; i <= maxOptions; i++)
            {
                if (i != selectedTrack)
                {
                    menuBuilder.addOption("Position " + i, String.valueOf(i));
                }
            }

            // Keep the embed but replace buttons with select menu
            MessageEmbed embed = QueueSlashCmd.buildQueueEmbed(queueInfo, page, totalPages, selectedTrack,
                    event.getMember().getColor());

            event.editMessageEmbeds(embed)
                    .setComponents(ActionRow.of(menuBuilder.build()))
                    .queue();
        }
        else if (action.equals("playnow"))
        {
            if (selectedTrack <= 0 || selectedTrack > queueInfo.tracks.length)
            {
                event.reply("No track selected!").setEphemeral(true).queue();
                return;
            }

            MusicService.OutputAdapter adapter = createQueueOutputAdapter(event);
            musicService.playNow(event.getGuild(), event.getMember(), selectedTrack, adapter);

            // After playing now, the queue order changes - refresh
            MusicService.QueueInfo newQueueInfo = musicService.getQueueInfo(event.getGuild(), event.getJDA());
            if (newQueueInfo == null || newQueueInfo.isEmpty())
            {
                event.editMessage("The queue is now empty!").setEmbeds().setComponents().queue();
            }
            else
            {
                int newTotalPages = QueueSlashCmd.getTotalPages(newQueueInfo.tracks.length);
                updateQueueEmbed(event, newQueueInfo, 1, newTotalPages, 0, userId);
            }
        }
    }

    /**
     * Updates the queue embed with new state.
     */
    private void updateQueueEmbed(ButtonInteractionEvent event, MusicService.QueueInfo queueInfo,
                                  int page, int totalPages, int selectedTrack, long userId)
    {
        int tracksOnPage = QueueSlashCmd.getTracksOnPage(page, queueInfo.tracks.length);
        MessageEmbed embed = QueueSlashCmd.buildQueueEmbed(queueInfo, page, totalPages, selectedTrack,
                event.getMember().getColor());
        List<ActionRow> components = QueueSlashCmd.buildQueueComponents(page, totalPages, tracksOnPage, selectedTrack, userId);

        event.editMessageEmbeds(embed).setComponents(components).queue();
    }

    /**
     * Creates an output adapter for queue button interactions that doesn't send replies
     * (since we update the embed instead).
     */
    private MusicService.OutputAdapter createQueueOutputAdapter(ButtonInteractionEvent event)
    {
        return new MusicService.OutputAdapter()
        {
            @Override
            public void replySuccess(String content)
            {
                // Don't send separate reply - we update the embed instead
            }

            @Override
            public void replyError(String content)
            {
                event.reply(content).setEphemeral(true).queue();
            }

            @Override
            public void replyWarning(String content)
            {
                event.reply(content).setEphemeral(true).queue();
            }

            @Override
            public void editMessage(String content)
            {
                // Not used for queue buttons
            }

            @Override
            public void editMessage(String content, Consumer<net.dv8tion.jda.api.entities.Message> onSuccess)
            {
                // Not used for queue buttons
            }

            @Override
            public void editNowPlaying(AudioHandler handler)
            {
                // Not used for queue buttons
            }

            @Override
            public void editNoMusic(AudioHandler handler)
            {
                // Not used for queue buttons
            }

            @Override
            public void onShowHelp()
            {
                // Not used for queue buttons
            }
        };
    }

    /**
     * Handles button interactions for the interactive history embed.
     * Component ID format: history_{action}_{page}_{selectedTrack}_{userId}
     */
    private void handleHistoryInteraction(ButtonInteractionEvent event)
    {
        if (event.getGuild() == null || event.getMember() == null)
        {
            event.reply("This can only be used in a server!").setEphemeral(true).queue();
            return;
        }

        String componentId = event.getComponentId();
        String[] parts = componentId.split("_");
        if (parts.length < 5)
        {
            event.reply("Invalid button state.").setEphemeral(true).queue();
            return;
        }

        String action = parts[1];
        int page;
        int selectedTrack;
        long userId;
        try
        {
            page = Integer.parseInt(parts[2]);
            selectedTrack = Integer.parseInt(parts[3]);
            userId = Long.parseLong(parts[4]);
        }
        catch (NumberFormatException e)
        {
            event.reply("Invalid button state.").setEphemeral(true).queue();
            return;
        }

        if (event.getUser().getIdLong() != userId)
        {
            event.reply("Only the user who ran the command can use these buttons!").setEphemeral(true).queue();
            return;
        }

        MusicService musicService = bot.getMusicService();
        MusicService.HistoryInfo historyInfo = musicService.getHistoryInfo(event.getGuild(), event.getJDA());

        if (historyInfo == null || historyInfo.isEmpty())
        {
            event.editMessage("Playback history is now empty!").setEmbeds().setComponents().queue();
            return;
        }

        int totalPages = HistorySlashCmd.getTotalPages(historyInfo.tracks.length);

        if (action.startsWith("select"))
        {
            int trackIndexOnPage = Integer.parseInt(action.substring(6));
            int newSelectedTrack = (page - 1) * HistorySlashCmd.TRACKS_PER_PAGE + trackIndexOnPage;
            if (newSelectedTrack == selectedTrack)
            {
                newSelectedTrack = 0;
            }
            if (newSelectedTrack > historyInfo.tracks.length)
            {
                event.reply("That track doesn't exist!").setEphemeral(true).queue();
                return;
            }
            updateHistoryEmbed(event, historyInfo, page, totalPages, newSelectedTrack, userId);
        }
        else if (action.equals("prev"))
        {
            int newPage = Math.max(1, page - 1);
            updateHistoryEmbed(event, historyInfo, newPage, totalPages, 0, userId);
        }
        else if (action.equals("next"))
        {
            int newPage = Math.min(totalPages, page + 1);
            updateHistoryEmbed(event, historyInfo, newPage, totalPages, 0, userId);
        }
        else if (action.equals("queue"))
        {
            if (selectedTrack <= 0 || selectedTrack > historyInfo.tracks.length)
            {
                event.reply("No track selected!").setEphemeral(true).queue();
                return;
            }
            if (!event.getMember().getVoiceState().inAudioChannel()
                    || event.getGuild().getSelfMember().getVoiceState().getChannel() == null
                    || !event.getMember().getVoiceState().getChannel().equals(event.getGuild().getSelfMember().getVoiceState().getChannel()))
            {
                event.reply("You must be in the same voice channel to use this!").setEphemeral(true).queue();
                return;
            }
            MusicService.OutputAdapter adapter = createHistoryOutputAdapter(event);
            TextChannel channel = event.getChannel().asTextChannel();
            musicService.queueFromHistory(event.getGuild(), event.getMember(), selectedTrack, channel, adapter);
            MusicService.HistoryInfo newInfo = musicService.getHistoryInfo(event.getGuild(), event.getJDA());
            if (newInfo == null || newInfo.isEmpty())
            {
                event.editMessage("Playback history is now empty!").setEmbeds().setComponents().queue();
            }
            else
            {
                int newTotalPages = HistorySlashCmd.getTotalPages(newInfo.tracks.length);
                int safePage = Math.min(page, newTotalPages);
                updateHistoryEmbed(event, newInfo, safePage, newTotalPages, 0, userId);
            }
        }
        else if (action.equals("playnow"))
        {
            if (selectedTrack <= 0 || selectedTrack > historyInfo.tracks.length)
            {
                event.reply("No track selected!").setEphemeral(true).queue();
                return;
            }
            if (!event.getMember().getVoiceState().inAudioChannel()
                    || event.getGuild().getSelfMember().getVoiceState().getChannel() == null
                    || !event.getMember().getVoiceState().getChannel().equals(event.getGuild().getSelfMember().getVoiceState().getChannel()))
            {
                event.reply("You must be in the same voice channel to use this!").setEphemeral(true).queue();
                return;
            }
            MusicService.OutputAdapter adapter = createHistoryOutputAdapter(event);
            TextChannel channel = event.getChannel().asTextChannel();
            musicService.playFromHistoryNow(event.getGuild(), event.getMember(), selectedTrack, channel, adapter);
            MusicService.HistoryInfo newInfo = musicService.getHistoryInfo(event.getGuild(), event.getJDA());
            if (newInfo == null || newInfo.isEmpty())
            {
                event.editMessage("Playback history is now empty!").setEmbeds().setComponents().queue();
            }
            else
            {
                int newTotalPages = HistorySlashCmd.getTotalPages(newInfo.tracks.length);
                updateHistoryEmbed(event, newInfo, 1, newTotalPages, 0, userId);
            }
        }
        else if (action.equals("save"))
        {
            TextInput input = TextInput.create("playlist_name", TextInputStyle.SHORT)
                    .setPlaceholder("e.g. my-history")
                    .setMinLength(1)
                    .setMaxLength(100)
                    .setRequired(true)
                    .build();
            Modal modal = Modal.create("history_save_" + userId, "Save history as playlist")
                    .addComponents(Label.of("Playlist name", input))
                    .build();
            event.replyModal(modal).queue();
        }
    }

    private void updateHistoryEmbed(ButtonInteractionEvent event, MusicService.HistoryInfo historyInfo,
                                   int page, int totalPages, int selectedTrack, long userId)
    {
        int tracksOnPage = HistorySlashCmd.getTracksOnPage(page, historyInfo.tracks.length);
        MessageEmbed embed = HistorySlashCmd.buildHistoryEmbed(historyInfo, page, totalPages, selectedTrack,
                event.getMember().getColor());
        List<ActionRow> components = HistorySlashCmd.buildHistoryComponents(page, totalPages, tracksOnPage, selectedTrack, userId);
        event.editMessageEmbeds(embed).setComponents(components).queue();
    }

    private MusicService.OutputAdapter createHistoryOutputAdapter(ButtonInteractionEvent event)
    {
        return new MusicService.OutputAdapter()
        {
            @Override
            public void replySuccess(String content)
            {
                event.reply(content).setEphemeral(true).queue();
            }

            @Override
            public void replyError(String content)
            {
                event.reply(content).setEphemeral(true).queue();
            }

            @Override
            public void replyWarning(String content)
            {
                event.reply(content).setEphemeral(true).queue();
            }

            @Override
            public void editMessage(String content) { }

            @Override
            public void editMessage(String content, Consumer<net.dv8tion.jda.api.entities.Message> onSuccess) { }

            @Override
            public void editNowPlaying(AudioHandler handler) { }

            @Override
            public void editNoMusic(AudioHandler handler) { }

            @Override
            public void onShowHelp() { }
        };
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event)
    {
        String modalId = event.getModalId();
        if (!modalId.startsWith("history_save_"))
        {
            return;
        }

        if (event.getGuild() == null || event.getMember() == null)
        {
            event.reply("This can only be used in a server!").setEphemeral(true).queue();
            return;
        }

        long userId;
        try
        {
            userId = Long.parseLong(modalId.substring("history_save_".length()));
        }
        catch (NumberFormatException e)
        {
            event.reply("Invalid modal state.").setEphemeral(true).queue();
            return;
        }

        if (event.getUser().getIdLong() != userId)
        {
            event.reply("Only the user who opened the save dialog can submit it!").setEphemeral(true).queue();
            return;
        }

        String playlistName = event.getValues().stream()
                .filter(m -> "playlist_name".equals(m.getCustomId()))
                .findFirst()
                .map(net.dv8tion.jda.api.interactions.modals.ModalMapping::getAsString)
                .orElse("")
                .trim();

        if (playlistName.isEmpty())
        {
            event.reply("Please enter a playlist name!").setEphemeral(true).queue();
            return;
        }

        MusicService.OutputAdapter adapter = new MusicService.OutputAdapter()
        {
            @Override
            public void replySuccess(String content)
            {
                event.reply(content).setEphemeral(true).queue();
            }

            @Override
            public void replyError(String content)
            {
                event.reply(content).setEphemeral(true).queue();
            }

            @Override
            public void replyWarning(String content)
            {
                event.reply(content).setEphemeral(true).queue();
            }

            @Override
            public void editMessage(String content) { }

            @Override
            public void editMessage(String content, Consumer<net.dv8tion.jda.api.entities.Message> onSuccess) { }

            @Override
            public void editNowPlaying(AudioHandler handler) { }

            @Override
            public void editNoMusic(AudioHandler handler) { }

            @Override
            public void onShowHelp() { }
        };

        bot.getMusicService().saveHistoryAsPlaylist(event.getGuild(), event.getMember(), playlistName, adapter);
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event)
    {
        String componentId = event.getComponentId();

        // Handle queue move selection: queue_move_select_{fromPosition}_{page}_{userId}
        if (!componentId.startsWith("queue_move_select_"))
        {
            return;
        }

        if (event.getGuild() == null || event.getMember() == null)
        {
            event.reply("This can only be used in a server!").setEphemeral(true).queue();
            return;
        }

        String[] parts = componentId.split("_");
        // Expected format: queue_move_select_fromPosition_page_userId
        if (parts.length < 6)
        {
            event.reply("Invalid selection state.").setEphemeral(true).queue();
            return;
        }

        int fromPosition;
        int page;
        long userId;
        try
        {
            fromPosition = Integer.parseInt(parts[3]);
            page = Integer.parseInt(parts[4]);
            userId = Long.parseLong(parts[5]);
        }
        catch (NumberFormatException e)
        {
            event.reply("Invalid selection state.").setEphemeral(true).queue();
            return;
        }

        // Verify user
        if (event.getUser().getIdLong() != userId)
        {
            event.reply("Only the user who ran the command can use this!").setEphemeral(true).queue();
            return;
        }

        // Voice channel check
        if (!event.getMember().getVoiceState().inAudioChannel() ||
                event.getGuild().getSelfMember().getVoiceState().getChannel() == null ||
                !event.getMember().getVoiceState().getChannel().equals(event.getGuild().getSelfMember().getVoiceState().getChannel()))
        {
            event.reply("You must be in the same voice channel to use this!").setEphemeral(true).queue();
            return;
        }

        // Get selected position
        int toPosition;
        try
        {
            toPosition = Integer.parseInt(event.getValues().get(0));
        }
        catch (NumberFormatException e)
        {
            event.reply("Invalid position selected.").setEphemeral(true).queue();
            return;
        }

        MusicService musicService = bot.getMusicService();

        // Create a silent adapter (we'll update the embed manually)
        MusicService.OutputAdapter adapter = new MusicService.OutputAdapter()
        {
            @Override
            public void replySuccess(String content) { }
            @Override
            public void replyError(String content) { event.reply(content).setEphemeral(true).queue(); }
            @Override
            public void replyWarning(String content) { event.reply(content).setEphemeral(true).queue(); }
            @Override
            public void editMessage(String content) { }
            @Override
            public void editMessage(String content, Consumer<net.dv8tion.jda.api.entities.Message> onSuccess) { }
            @Override
            public void editNowPlaying(AudioHandler handler) { }
            @Override
            public void editNoMusic(AudioHandler handler) { }
            @Override
            public void onShowHelp() { }
        };

        musicService.moveTrack(event.getGuild(), event.getMember(), fromPosition, toPosition, adapter);

        // Refresh queue and update embed
        MusicService.QueueInfo queueInfo = musicService.getQueueInfo(event.getGuild(), event.getJDA());
        if (queueInfo == null || queueInfo.isEmpty())
        {
            event.editMessage("The queue is now empty!").setEmbeds().setComponents().queue();
            return;
        }

        int totalPages = QueueSlashCmd.getTotalPages(queueInfo.tracks.length);
        int safePage = Math.min(page, totalPages);
        int tracksOnPage = QueueSlashCmd.getTracksOnPage(safePage, queueInfo.tracks.length);

        MessageEmbed embed = QueueSlashCmd.buildQueueEmbed(queueInfo, safePage, totalPages, 0,
                event.getMember().getColor());
        List<ActionRow> components = QueueSlashCmd.buildQueueComponents(safePage, totalPages, tracksOnPage, 0, userId);

        event.editMessageEmbeds(embed).setComponents(components).queue();
    }

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event)
    {
        bot.getAloneInVoiceHandler().onVoiceUpdate(event);
    }

    @Override
    public void onSessionDisconnect(@NotNull SessionDisconnectEvent event)
    {
        CloseCode closeCode = event.getCloseCode();
        if (closeCode == CloseCode.DISALLOWED_INTENTS)
        {
            bot.getUserInteraction().alert(
                Level.ERROR,
                "JMusicBot",
                "Your bot is missing required Discord intents!\n\n" +
                "To fix this:\n" +
                "1. Go to https://discord.com/developers/applications\n" +
                "2. Select your bot application\n" +
                "3. Go to 'Bot' settings\n" +
                "4. Enable 'MESSAGE CONTENT INTENT' under Privileged Gateway Intents\n" +
                "5. Save changes and restart JMusicBot"
            );
        }
    }

    @Override
    public void onShutdown(@NotNull ShutdownEvent event)
    {
        bot.shutdown();
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) 
    {
        credit(event.getJDA());
    }
    
    // make sure people aren't adding clones to dbots
    private void credit(JDA jda)
    {
        Guild dbots = jda.getGuildById(110373943822540800L);
        if(dbots==null)
            return;
        if(bot.getConfig().getDBots())
            return;
        jda.getTextChannelById(119222314964353025L)
                .sendMessage("This account is running JMusicBot. Please do not list bot clones on this server, <@"+bot.getConfig().getOwnerId()+">.").complete();
        dbots.leave().queue();
    }
}
