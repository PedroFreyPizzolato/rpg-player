package com.jagrosh.jmusicbot.service;

import com.jagrosh.jdautilities.menu.ButtonMenu;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.commands.v1.DJCommand;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.jagrosh.jmusicbot.utils.TimeUtil;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.PermissionException;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class PlayerService
{
    private final Bot bot;

    public PlayerService(Bot bot)
    {
        this.bot = bot;
    }

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

    public interface OutputAdapter
    {
        void replySuccess(String content);
        void replyError(String content);
        void replyWarning(String content);
        void editMessage(String content);
        void editMessage(String content, Consumer<Message> onSuccess);
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

            // Get the formatted title (handles local files)
            String title = FormatUtil.getTrackTitle(track);

            if(bot.getConfig().isTooLong(track))
            {
                output.editMessage(FormatUtil.filter(bot.getConfig().getWarning()+" This track (**"+title+"**) is longer than the allowed maximum: `"
                        + TimeUtil.formatTime(track.getDuration())+"` > `"+ TimeUtil.formatTime(bot.getConfig().getMaxSeconds()*1000)+"`"));
                return;
            }



            AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
            handler.setLastReason(member.getUser().getName() + " added to the queue.");
            int pos = handler.addTrack(new QueuedTrack(track, new RequestMetadata(member.getUser(), new RequestMetadata.RequestInfo(args, track.getInfo().uri), channel.getIdLong()))) + 1;
            String addMsg = FormatUtil.filter(bot.getConfig().getSuccess()+" Added **"+title
                    +"** (`"+ TimeUtil.formatTime(track.getDuration())+"`) "+(pos==0?"to begin playing":" to the queue at position "+pos));
            if(playlist==null || !guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_ADD_REACTION))
                output.editMessage(addMsg);
            else
            {
                output.editMessage(addMsg, m -> {
                    new ButtonMenu.Builder()
                            .setText(addMsg+"\n"+bot.getConfig().getWarning()+" This track has a playlist of **"+playlist.getTracks().size()+"** tracks attached. Select "+LOAD+" to load playlist.")
                            .setChoices(LOAD, CANCEL)
                            .setEventWaiter(bot.getWaiter())
                            .setTimeout(30, TimeUnit.SECONDS)
                            .setAction(re ->
                            {
                                if(re.getName().equals(LOAD))
                                    m.editMessage(addMsg+"\n"+bot.getConfig().getSuccess()+" Loaded **"+loadPlaylist(playlist, track)+"** additional tracks!").queue();
                                else
                                    m.editMessage(addMsg).queue();
                            }).setFinalAction(msg ->
                            {
                                try{ msg.clearReactions().queue(); }catch(PermissionException ignore) {}
                            }).build().display(m);
                });
            }
        }

        private int loadPlaylist(AudioPlaylist playlist, AudioTrack exclude)
        {
            int[] count = {0};
            playlist.getTracks().stream().forEach((track) -> {
                if(!bot.getConfig().isTooLong(track) && !track.equals(exclude))
                {
                    AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
                    handler.setLastReason(member.getUser().getName() + " added a playlist.");
                    handler.addTrack(new QueuedTrack(track, new RequestMetadata(member.getUser(), new RequestMetadata.RequestInfo(args, track.getInfo().uri), channel.getIdLong())));
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
            if(playlist.getTracks().size()==1 || playlist.isSearchResult())
            {
                AudioTrack single = playlist.getSelectedTrack()==null ? playlist.getTracks().get(0) : playlist.getSelectedTrack();
                loadSingle(single, null);
            }
            else if (playlist.getSelectedTrack()!=null)
            {
                AudioTrack single = playlist.getSelectedTrack();
                loadSingle(single, playlist);
            }
            else
            {
                int count = loadPlaylist(playlist, null);
                if(playlist.getTracks().size() == 0)
                {
                    output.editMessage(FormatUtil.filter(bot.getConfig().getWarning()+" The playlist "+(playlist.getName()==null ? "" : "(**"+playlist.getName()
                            +"**) ")+" could not be loaded or contained 0 entries"));
                }
                else if(count==0)
                {
                    output.editMessage(FormatUtil.filter(bot.getConfig().getWarning()+" All entries in this playlist "+(playlist.getName()==null ? "" : "(**"+playlist.getName()
                            +"**) ")+"were longer than the allowed maximum (`"+bot.getConfig().getMaxTime()+"`)"));
                }
                else
                {
                    output.editMessage(FormatUtil.filter(bot.getConfig().getSuccess()+" Found "
                            +(playlist.getName()==null?"a playlist":"playlist **"+playlist.getName()+"**")+" with `"
                            + playlist.getTracks().size()+"` entries; added to the queue!"
                            + (count<playlist.getTracks().size() ? "\n"+bot.getConfig().getWarning()+" Tracks longer than the allowed maximum (`"
                            + bot.getConfig().getMaxTime()+"`) have been omitted." : "")));
                }
            }
        }

        @Override
        public void noMatches()
        {
            if(ytsearch)
                output.editMessage(FormatUtil.filter(bot.getConfig().getWarning()+" No results found for `"+args+"`."));
            else
                bot.getPlayerManager().loadItemOrdered(guild, "ytsearch:"+args, new ResultHandler(output, guild, member, args, true, channel));
        }

        @Override
        public void loadFailed(FriendlyException throwable)
        {
            if(throwable.severity==Severity.COMMON)
                output.editMessage(bot.getConfig().getError()+" Error loading: "+throwable.getMessage());
            else
                output.editMessage(bot.getConfig().getError()+" Error loading track.");
        }
    }
}
