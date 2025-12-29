package com.jagrosh.jmusicbot.commands.v2.music;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v2.MusicSlashCommand;
import com.jagrosh.jmusicbot.service.PlayerService;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class PlaySlashCmd extends MusicSlashCommand
{
    private final static String LOAD = "\uD83D\uDCE5"; // 📥
    private final static String CANCEL = "\uD83D\uDEAB"; // 🚫

    private final String loadingEmoji;
    private final PlayerService playerService;

    public PlaySlashCmd(Bot bot, PlayerService playerService)
    {
        super(bot);
        this.playerService = playerService;
        this.loadingEmoji = bot.getConfig().getLoading();
        this.name = "play";
        this.help = "plays the provided song";
        this.options = Collections.singletonList(new OptionData(OptionType.STRING, "query", "path to song OR song title OR URL", false).setAutoComplete(true));
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = true;
        this.bePlaying = false;
    }

    @Override
    public void doCommand(SlashCommandEvent event)
    {
        if (event.getOption("query") == null)
        {
            playerService.play(event.getGuild(), event.getMember(), "", event.getTextChannel(), new PlayerService.OutputAdapter() {
                @Override
                public void replySuccess(String content) {
                    event.reply(content).queue();
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
                    event.reply(content).queue();
                }

                @Override
                public void editMessage(String content, Consumer<Message> onSuccess) {
                    event.reply(content).queue(hook -> hook.retrieveOriginal().queue(onSuccess));
                }

                @Override
                public void editNowPlaying(com.jagrosh.jmusicbot.audio.AudioHandler handler) {
                    event.reply(handler.getNowPlaying(event.getJDA())).queue();
                }

                @Override
                public void editNoMusic(com.jagrosh.jmusicbot.audio.AudioHandler handler) {
                    event.reply(handler.getNoMusicPlaying(event.getJDA())).queue();
                }

                @Override
                public void onShowHelp() {
                    event.reply(event.getClient().getWarning() + " Please include a song title or URL!").setEphemeral(true).queue();
                }
            });
            return;
        }

        String args = event.getOption("query").getAsString();
        event.reply(loadingEmoji + " Loading... `[" + args + "]`").queue(hook -> {
            playerService.play(event.getGuild(), event.getMember(), args, event.getTextChannel(), new PlayerService.OutputAdapter() {
                @Override
                public void replySuccess(String content) {
                    hook.editOriginal(content).queue();
                }

                @Override
                public void replyError(String content) {
                    hook.editOriginal(content).queue();
                }

                @Override
                public void replyWarning(String content) {
                    hook.editOriginal(content).queue();
                }

                @Override
                public void editMessage(String content) {
                    hook.editOriginal(content).queue();
                }

                @Override
                public void editMessage(String content, Consumer<Message> onSuccess) {
                    hook.editOriginal(content).queue(onSuccess);
                }

                @Override
                public void editNowPlaying(com.jagrosh.jmusicbot.audio.AudioHandler handler) {
                    hook.editOriginal(MessageEditData.fromCreateData(handler.getNowPlaying(event.getJDA()))).queue();
                }

                @Override
                public void editNoMusic(com.jagrosh.jmusicbot.audio.AudioHandler handler) {
                    hook.editOriginal(MessageEditData.fromCreateData(handler.getNoMusicPlaying(event.getJDA()))).queue();
                }

                @Override
                public void onShowHelp() {
                    // This shouldn't be reached as input option is required
                    hook.editOriginal(event.getClient().getWarning() + " Please include a song title or URL!").queue();
                }
            });
        });
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event)
    {
        String input = event.getFocusedOption().getValue();
        if(input.isEmpty())
        {
            event.replyChoices().queue();
            return;
        }

        // Simple check to avoid searching if it's a URL or looks like a local file path
        if(input.startsWith("http://") || input.startsWith("https://")
                || input.contains(":\\") || input.startsWith("/") || input.contains("\\"))
        {
            event.replyChoices(new Command.Choice(input, input)).queue();
            return;
        }

        bot.getPlayerManager().loadItemOrdered(event.getGuild(), "ytsearch:" + input, new AudioLoadResultHandler()
        {
            @Override
            public void trackLoaded(AudioTrack track)
            {
                event.replyChoices(new Command.Choice(track.getInfo().title, track.getInfo().uri)).queue();
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist)
            {
                List<Command.Choice> choices = new ArrayList<>();
                for(int i = 0; i < playlist.getTracks().size() && i < 10; i++) // Limit to 10 choices
                {
                    AudioTrack track = playlist.getTracks().get(i);
                    // Ensure the title is not too long for Discord (100 chars max for name)
                    String title = track.getInfo().title;
                    if(title.length() > 100)
                        title = title.substring(0, 97) + "...";
                    choices.add(new Command.Choice(title, track.getInfo().uri));
                }
                event.replyChoices(choices).queue();
            }

            @Override
            public void noMatches()
            {
                event.replyChoices().queue();
            }

            @Override
            public void loadFailed(FriendlyException exception)
            {
                event.replyChoices().queue();
            }
        });
    }
}
