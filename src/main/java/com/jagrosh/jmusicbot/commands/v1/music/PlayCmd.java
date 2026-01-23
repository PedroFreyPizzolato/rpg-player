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
package com.jagrosh.jmusicbot.commands.v1.music;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.commands.v1.MusicCommand;
import com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist;
import com.jagrosh.jmusicbot.service.PlayerService;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import net.dv8tion.jda.api.entities.Message;

import java.util.function.Consumer;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class PlayCmd extends MusicCommand
{
    private final String loadingEmoji;
    private final PlayerService playerService;
    
    public PlayCmd(Bot bot)
    {
        super(bot);
        this.playerService = bot.getPlayerService();
        this.loadingEmoji = bot.getConfig().getLoading();
        this.name = "play";
        this.arguments = "<title|URL|subcommand>";
        this.help = "plays the provided song";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = true;
        this.bePlaying = false;
        this.children = new Command[]{new PlaylistCmd(bot)};
    }

    @Override
    public void doCommand(CommandEvent event)
    {
        String args = event.getArgs().startsWith("<") && event.getArgs().endsWith(">")
                ? event.getArgs().substring(1,event.getArgs().length()-1)
                : event.getArgs().isEmpty() ? event.getMessage().getAttachments().isEmpty() ? "" : event.getMessage().getAttachments().get(0).getUrl() : event.getArgs();

        if (args.isEmpty()) {
            playerService.play(event.getGuild(), event.getMember(), args, event.getTextChannel(), new PlayerService.OutputAdapter() {
                @Override public void replySuccess(String content) { event.replySuccess(content); }
                @Override public void replyError(String content) { event.replyError(content); }
                @Override public void replyWarning(String content) { event.replyWarning(content); }
                @Override public void editMessage(String content) { /* No-op for unpause */ }
                @Override public void editMessage(String content, Consumer<Message> onSuccess) { /* No-op for unpause */ }
                @Override public void editNowPlaying(AudioHandler handler) { /* No-op for v1 play */ }
                @Override public void editNoMusic(AudioHandler handler) { /* No-op for v1 play */ }
                @Override public void onShowHelp() {
                    StringBuilder builder = new StringBuilder(event.getClient().getWarning()+" Play Commands:\n");
                    builder.append("\n`").append(event.getClient().getPrefix()).append(name).append(" <song title>` - plays the first result from Youtube");
                    builder.append("\n`").append(event.getClient().getPrefix()).append(name).append(" <URL>` - plays the provided song, playlist, or stream");
                    for(Command cmd: children)
                        builder.append("\n`").append(event.getClient().getPrefix()).append(name).append(" ").append(cmd.getName()).append(" ").append(cmd.getArguments()).append("` - ").append(cmd.getHelp());
                    event.reply(builder.toString());
                }
            });
            return;
        }

        event.reply(loadingEmoji+" Loading... `["+args+"]`", m -> {
            playerService.play(event.getGuild(), event.getMember(), args, event.getTextChannel(), new PlayerService.OutputAdapter() {
                @Override public void replySuccess(String content) { /* Used for unpause, not here */ }
                @Override public void replyError(String content) { /* Used for unpause, not here */ }
                @Override public void replyWarning(String content) { /* Used for unpause, not here */ }

                @Override
                public void editMessage(String content) {
                    m.editMessage(content).queue();
                }

                @Override
                public void editMessage(String content, Consumer<Message> onSuccess) {
                    m.editMessage(content).queue(onSuccess);
                }

                @Override public void editNowPlaying(AudioHandler handler) { /* No-op for v1 play */ }
                @Override public void editNoMusic(AudioHandler handler) { /* No-op for v1 play */ }

                @Override public void onShowHelp() { /* Should not happen as args are checked */ }
            });
        });
    }
    
    public class PlaylistCmd extends MusicCommand
    {
        public PlaylistCmd(Bot bot)
        {
            super(bot);
            this.name = "playlist";
            this.aliases = new String[]{"pl"};
            this.arguments = "<name>";
            this.help = "plays the provided playlist";
            this.beListening = true;
            this.bePlaying = false;
        }

        @Override
        public void doCommand(CommandEvent event) 
        {
            if(event.getArgs().isEmpty())
            {
                event.reply(event.getClient().getError()+" Please include a playlist name.");
                return;
            }
            Playlist playlist = bot.getPlaylistLoader().getPlaylist(event.getArgs());
            if(playlist==null)
            {
                event.replyError("I could not find `"+event.getArgs()+".txt` in the Playlists folder.");
                return;
            }
            event.getChannel().sendMessage(loadingEmoji+" Loading playlist **"+event.getArgs()+"**... ("+playlist.getItems().size()+" items)").queue(m -> 
            {
                AudioHandler handler = (AudioHandler)event.getGuild().getAudioManager().getSendingHandler();
                playlist.loadTracks(bot.getPlayerManager(), (at)->handler.addTrack(new QueuedTrack(at, RequestMetadata.fromResultHandler(at, event))), () -> {
                    StringBuilder builder = new StringBuilder(playlist.getTracks().isEmpty() 
                            ? event.getClient().getWarning()+" No tracks were loaded!" 
                            : event.getClient().getSuccess()+" Loaded **"+playlist.getTracks().size()+"** tracks!");
                    if(!playlist.getErrors().isEmpty())
                        builder.append("\nThe following tracks failed to load:");
                    playlist.getErrors().forEach(err -> builder.append("\n`[").append(err.getIndex()+1).append("]` **").append(err.getItem()).append("**: ").append(err.getReason()));
                    String str = builder.toString();
                    if(str.length()>2000)
                        str = str.substring(0,1994)+" (...)";
                    m.editMessage(FormatUtil.filter(str)).queue();
                });
            });
        }
    }
}
