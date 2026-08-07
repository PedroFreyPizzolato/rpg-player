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
import com.jagrosh.jmusicbot.audio.PhaseConfig;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Audio load result handlers for processing track/playlist loading results.
 * These handlers are used by MusicService for play and playNext operations.
 */
public final class AudioLoadResultHandlers
{
    private static final Logger LOG = LoggerFactory.getLogger(AudioLoadResultHandlers.class);

    /** Teto de 25 opções do Discord, menos a linha do "começar do zero". */
    private static final int MAX_PRESET_OPTIONS = 24;

    private AudioLoadResultHandlers()
    {
        // Utility class - prevent instantiation
    }

    /**
     * Base class for audio load result handlers with shared fields and common logic.
     */
    public static abstract class BaseResultHandler implements AudioLoadResultHandler
    {
        protected final MusicService musicService;
        protected final Bot bot;
        protected final MusicService.OutputAdapter output;
        protected final Guild guild;
        protected final Member member;
        protected final String args;
        protected final boolean ytsearch;
        protected final TextChannel channel;

        protected BaseResultHandler(MusicService musicService, Bot bot, MusicService.OutputAdapter output,
                                     Guild guild, Member member, String args, boolean ytsearch, TextChannel channel)
        {
            this.musicService = musicService;
            this.bot = bot;
            this.output = output;
            this.guild = guild;
            this.member = member;
            this.args = args;
            this.ytsearch = ytsearch;
            this.channel = channel;
        }

        /**
         * Creates a fallback handler for YouTube search when no direct match is found.
         */
        protected abstract BaseResultHandler createFallbackHandler();

        /**
         * Returns a descriptive name for logging (e.g., "Track" or "PlayNext").
         */
        protected abstract String getHandlerName();

        @Override
        public void noMatches()
        {
            if (ytsearch)
            {
                LOG.debug("{} no matches found: guild={}, query=\"{}\"", getHandlerName(), guild.getId(), args);
                output.editMessage(FormatUtil.filter(bot.getConfig().getWarning() + " No results found for `" + args + "`."));
            }
            else
            {
                LOG.debug("{} falling back to YouTube search: guild={}, query=\"{}\"",
                        getHandlerName(), guild.getId(), args);
                bot.getPlayerManager().loadItemOrdered(guild, "ytsearch:" + args,
                        bot.getAudioLoadWrapper().wrap("ytsearch:" + args, createFallbackHandler()));
            }
        }

        @Override
        public void loadFailed(FriendlyException throwable)
        {
            if (throwable.severity == Severity.COMMON)
            {
                LOG.warn("{} load failed (common): guild={}, query=\"{}\", error={}",
                        getHandlerName(), guild.getId(), args, throwable.getMessage());
                output.editMessage(bot.getConfig().getError() + " Error loading: " + throwable.getMessage());
            }
            else
            {
                LOG.error("{} load failed (severe): guild={}, query=\"{}\"",
                        getHandlerName(), guild.getId(), args, throwable);
                output.editMessage(bot.getConfig().getError() + " Error loading track.");
            }
        }
    }

    /**
     * Result handler for standard play command that adds tracks to the end of the queue.
     */
    public static class PlayResultHandler extends BaseResultHandler
    {
        private static final String PHASE_EMOJI = "🎬"; // 🎬
        private static final String LOAD = "\uD83D\uDCE5"; // 📥
        private static final String CANCEL = "\uD83D\uDEAB"; // 🚫

        public PlayResultHandler(MusicService musicService, Bot bot, MusicService.OutputAdapter output,
                                  Guild guild, Member member, String args, boolean ytsearch, TextChannel channel)
        {
            super(musicService, bot, output, guild, member, args, ytsearch, channel);
        }

        @Override
        protected BaseResultHandler createFallbackHandler()
        {
            return new PlayResultHandler(musicService, bot, output, guild, member, args, true, channel);
        }

        @Override
        protected String getHandlerName()
        {
            return "Track";
        }

        private void loadSingle(AudioTrack track, AudioPlaylist playlist)
        {
            MusicService.TrackAddResult result = musicService.addTrackToQueue(guild, member, track, args, channel);
            if (result == null)
            {
                output.editMessage(FormatUtil.filter(bot.getConfig().getWarning() + " " + musicService.formatTooLongError(track)));
                return;
            }

            String addMsg = FormatUtil.filter(bot.getConfig().getSuccess() + " " + result.formattedMessage);
            if (playlist == null || !guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_ADD_REACTION))
            {
                output.editMessage(addMsg);
            }
            else
            {
                promptForPlaylistLoad(track, playlist, addMsg);
            }
        }

        private void promptForPlaylistLoad(AudioTrack track, AudioPlaylist playlist, String addMsg)
        {
            String promptMsg = addMsg + "\n" + bot.getConfig().getWarning() + " This track has a playlist of **"
                    + playlist.getTracks().size() + "** tracks attached. Select " + LOAD + " to load playlist.";

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

        private int loadPlaylist(AudioPlaylist playlist, AudioTrack exclude)
        {
            int[] count = {0};
            AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
            playlist.getTracks().forEach((track) -> {
                if (!musicService.isTooLong(track) && !track.equals(exclude))
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
            LOG.debug("Track loaded: guild={}, track=\"{}\"", guild.getId(), track.getInfo().title);
            loadSingleOrOfferPhases(track);
        }

        /**
         * Só entra aqui quando o lavaplayer resolveu direto pra uma faixa (URL colada). Buscar
         * pelo nome volta como {@link AudioPlaylist} (resultado de busca) e cai em
         * {@link #playlistLoaded}, então essa checagem precisa valer lá também — senão tocar
         * uma faixa com fases cadastradas digitando o nome (em vez de colar o link) nunca
         * oferece o modo fase, mesmo quando a busca acha exatamente a mesma fonte.
         */
        private void loadSingleOrOfferPhases(AudioTrack track)
        {
            PhaseConfig.Track phased = bot.getPhaseService().isIdle(guild)
                    ? bot.getPhaseService().findMatchingPhases(track) : null;
            if (phased != null)
                offerPhaseMode(track, phased);
            else
                loadSingle(track, null);
        }

        /**
         * A fonte resolvida já tem fases cadastradas e nada está tocando: pergunta se o mestre
         * quer a faixa inteira ou entrar direto no modo fase, em vez de assumir uma das duas.
         */
        private void offerPhaseMode(AudioTrack track, PhaseConfig.Track phased)
        {
            if (PhaseService.needsPresetChoice(phased))
                offerPresetChoice(track, phased);
            else
                offerSinglePreset(track, phased);
        }

        /** Uma segmentação só: não há o que escolher, o botão já entra nela. */
        private void offerSinglePreset(AudioTrack track, PhaseConfig.Track phased)
        {
            PhaseConfig.Segmentation segmentation = phased.firstSegmentation();
            String question = FormatUtil.filter(bot.getConfig().getSuccess() + " **"
                    + FormatUtil.getTrackTitle(track) + "** tem fases cadastradas (`" + phased.name
                    + "`, " + segmentation.phases().size() + " fase(s)). Como tocar?");

            MessageEditBuilder editBuilder = new MessageEditBuilder()
                    .setContent(question)
                    .setComponents(ActionRow.of(
                            Button.secondary("phase_offer_normal", "Tocar normalmente"),
                            Button.success("phase_offer_phases", "Tocar com Fases").withEmoji(Emoji.fromUnicode(PHASE_EMOJI))
                    ));

            output.editMessage(question, m -> {
                m.editMessage(editBuilder.build()).queue(msg -> {
                    bot.getWaiter().waitForEvent(ButtonInteractionEvent.class,
                            event -> event.getMessageId().equals(msg.getId()) &&
                                    (event.getComponentId().equals("phase_offer_normal") || event.getComponentId().equals("phase_offer_phases")) &&
                                    event.getUser().getIdLong() == member.getIdLong(),
                            event -> {
                                if (event.getComponentId().equals("phase_offer_phases"))
                                {
                                    event.editMessage(PHASE_EMOJI + " Iniciando modo fase para **" + phased.name + "**...")
                                            .setComponents().queue();
                                    startPhases(track, phased, segmentation, errorsOnly(event));
                                }
                                else
                                {
                                    event.editMessage(question).setComponents().queue();
                                    loadSingle(track, null);
                                }
                            },
                            30, TimeUnit.SECONDS,
                            () -> {
                                msg.editMessage(question).setComponents().queue();
                                loadSingle(track, null);
                            });
                });
            });
        }

        /**
         * Duas ou mais segmentações: a escolha vira um menu, e o "tocar normalmente" continua
         * botão. O menu leva também a opção de entrar sem segmentação nenhuma, para quem quer
         * montar uma nova ouvindo a música inteira em loop.
         */
        private void offerPresetChoice(AudioTrack track, PhaseConfig.Track phased)
        {
            String question = FormatUtil.filter(bot.getConfig().getSuccess() + " **"
                    + FormatUtil.getTrackTitle(track) + "** tem " + phased.presets.size()
                    + " segmentações. Como tocar?");

            List<SelectOption> options = new ArrayList<>();
            // o menu ainda precisa caber a opção de começar do zero
            for (int i = 0; i < Math.min(phased.presets.size(), MAX_PRESET_OPTIONS); i++)
            {
                PhaseConfig.Preset preset = phased.presets.get(i);
                options.add(SelectOption.of(cutTo(preset.name, 100), "preset:" + i)
                        .withDescription(preset.phases.size() + " fase(s)"));
            }
            options.add(SelectOption.of("➕ Começar do zero", "blank")
                    .withDescription("Modo fase sem segmentação, para marcar ouvindo"));

            MessageEditBuilder editBuilder = new MessageEditBuilder()
                    .setContent(question)
                    .setComponents(
                            ActionRow.of(StringSelectMenu.create("phase_offer_preset")
                                    .setPlaceholder("Escolher segmentação...")
                                    .addOptions(options)
                                    .build()),
                            ActionRow.of(Button.secondary("phase_offer_normal", "Tocar normalmente")));

            output.editMessage(question, m -> {
                m.editMessage(editBuilder.build()).queue(msg -> {
                    // o menu e o botão são eventos de tipos diferentes, então são duas esperas;
                    // quem chegar primeiro desarma a mensagem, e a outra expira sozinha
                    bot.getWaiter().waitForEvent(StringSelectInteractionEvent.class,
                            event -> event.getMessageId().equals(msg.getId())
                                    && event.getComponentId().equals("phase_offer_preset")
                                    && event.getUser().getIdLong() == member.getIdLong(),
                            event -> {
                                String value = event.getValues().isEmpty() ? "" : event.getValues().get(0);
                                PhaseConfig.Preset chosen = resolveChoice(phased, value);
                                if (chosen == null)
                                {
                                    event.editMessage("Essa segmentação não existe mais.")
                                            .setComponents().queue();
                                    return;
                                }
                                event.editMessage(PHASE_EMOJI + " Iniciando **" + phased.name
                                                + "** — `" + chosen.name + "`...")
                                        .setComponents().queue();
                                startPhases(track, phased,
                                        new PhaseConfig.Segmentation(phased, chosen), errorsOnly(event));
                            },
                            30, TimeUnit.SECONDS, () -> { });

                    bot.getWaiter().waitForEvent(ButtonInteractionEvent.class,
                            event -> event.getMessageId().equals(msg.getId())
                                    && event.getComponentId().equals("phase_offer_normal")
                                    && event.getUser().getIdLong() == member.getIdLong(),
                            event -> {
                                event.editMessage(question).setComponents().queue();
                                loadSingle(track, null);
                            },
                            30, TimeUnit.SECONDS,
                            () -> {
                                msg.editMessage(question).setComponents().queue();
                                loadSingle(track, null);
                            });
                });
            });
        }

        /**
         * O preset que a opção escolhida representa. "Começar do zero" devolve um preset novo,
         * já gravado na faixa — é ele que a marcação ao vivo vai preencher.
         */
        private PhaseConfig.Preset resolveChoice(PhaseConfig.Track phased, String value)
        {
            if ("blank".equals(value))
                return createBlankPreset(phased);
            if (!value.startsWith("preset:"))
                return null;
            int index = parseIndex(value.substring("preset:".length()));
            return index >= 0 && index < phased.presets.size() ? phased.presets.get(index) : null;
        }

        /**
         * Cria e grava um preset vazio para o mestre montar ouvindo. Precisa existir no arquivo
         * antes de a reprodução começar: é o nome dele que o botão "Marcar aqui" procura.
         */
        private PhaseConfig.Preset createBlankPreset(PhaseConfig.Track phased)
        {
            String name = "Nova segmentação";
            for (int i = 2; phased.preset(name) != null; i++)
                name = "Nova segmentação " + i;

            String error = bot.getPresetService().create(phased.name, name, null);
            if (error != null)
            {
                LOG.warn("Não consegui criar o preset vazio de '{}': {}", phased.name, error);
                return null;
            }
            PhaseConfig.Preset created = new PhaseConfig.Preset();
            created.name = name;
            phased.presets.add(created);
            return created;
        }

        private void startPhases(AudioTrack track, PhaseConfig.Track phased,
                                 PhaseConfig.Segmentation segmentation, MusicService.OutputAdapter out)
        {
            // a faixa já está resolvida aqui: é o único caminho que sabe a duração, e é dela que
            // sai a fase implícita de um preset vazio
            bot.getPhaseService().startAt(guild, channel, segmentation, 0,
                    PhaseService.knownDurationMs(track), out);
        }

        private static int parseIndex(String text)
        {
            try
            {
                return Integer.parseInt(text.trim());
            }
            catch (NumberFormatException e)
            {
                return -1;
            }
        }

        private static String cutTo(String text, int limit)
        {
            if (text == null)
                return "(sem nome)";
            return text.length() <= limit ? text : text.substring(0, limit - 1) + "…";
        }

        private static MusicService.OutputAdapter errorsOnly(IReplyCallback event)
        {
            return new com.jagrosh.jmusicbot.commands.BaseOutputAdapter()
            {
                @Override
                public void replyError(String content)
                {
                    event.getHook().sendMessage(content).setEphemeral(true).queue();
                }
            };
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist)
        {
            LOG.debug("Playlist loaded: guild={}, name=\"{}\", tracks={}",
                    guild.getId(), playlist.getName(), playlist.getTracks().size());

            if (playlist.getTracks().size() == 1 || playlist.isSearchResult())
            {
                AudioTrack single = playlist.getSelectedTrack() == null ? playlist.getTracks().get(0) : playlist.getSelectedTrack();
                loadSingleOrOfferPhases(single);
            }
            else if (playlist.getSelectedTrack() != null)
            {
                AudioTrack single = playlist.getSelectedTrack();
                loadSingle(single, playlist);
            }
            else
            {
                int count = loadPlaylist(playlist, null);
                handlePlaylistLoadResult(playlist, count);
            }
        }

        private void handlePlaylistLoadResult(AudioPlaylist playlist, int count)
        {
            if (playlist.getTracks().size() == 0)
            {
                LOG.warn("Playlist empty or could not be loaded: guild={}, name=\"{}\"",
                        guild.getId(), playlist.getName());
                output.editMessage(FormatUtil.filter(bot.getConfig().getWarning() + " The playlist "
                        + (playlist.getName() == null ? "" : "(**" + playlist.getName() + "**) ")
                        + " could not be loaded or contained 0 entries"));
            }
            else if (count == 0)
            {
                LOG.warn("All playlist tracks too long: guild={}, name=\"{}\"",
                        guild.getId(), playlist.getName());
                output.editMessage(FormatUtil.filter(bot.getConfig().getWarning() + " All entries in this playlist "
                        + (playlist.getName() == null ? "" : "(**" + playlist.getName() + "**) ")
                        + "were longer than the allowed maximum (`" + bot.getConfig().getMaxTime() + "`)"));
            }
            else
            {
                LOG.info("Playlist added to queue: guild={}, name=\"{}\", tracksAdded={}/{}",
                        guild.getId(), playlist.getName(), count, playlist.getTracks().size());
                output.editMessage(FormatUtil.filter(bot.getConfig().getSuccess() + " Found "
                        + (playlist.getName() == null ? "a playlist" : "playlist **" + playlist.getName() + "**") + " with `"
                        + playlist.getTracks().size() + "` entries; added to the queue!"
                        + (count < playlist.getTracks().size() ? "\n" + bot.getConfig().getWarning()
                        + " Tracks longer than the allowed maximum (`" + bot.getConfig().getMaxTime() + "`) have been omitted." : "")));
            }
        }
    }

    /**
     * Result handler for playNext command that adds tracks to the front of the queue.
     */
    public static class PlayNextResultHandler extends BaseResultHandler
    {
        public PlayNextResultHandler(MusicService musicService, Bot bot, MusicService.OutputAdapter output,
                                      Guild guild, Member member, String args, boolean ytsearch, TextChannel channel)
        {
            super(musicService, bot, output, guild, member, args, ytsearch, channel);
        }

        @Override
        protected BaseResultHandler createFallbackHandler()
        {
            return new PlayNextResultHandler(musicService, bot, output, guild, member, args, true, channel);
        }

        @Override
        protected String getHandlerName()
        {
            return "PlayNext";
        }

        private void loadSingle(AudioTrack track)
        {
            LOG.debug("PlayNext loading track: guild={}, track=\"{}\"", guild.getId(), track.getInfo().title);

            MusicService.TrackAddResult result = musicService.addTrackToFront(guild, member, track, args, channel);
            if (result == null)
            {
                output.editMessage(FormatUtil.filter(bot.getConfig().getWarning() + " " + musicService.formatTooLongError(track)));
                return;
            }

            output.editMessage(FormatUtil.filter(bot.getConfig().getSuccess() + " " + result.formattedMessage));
        }

        @Override
        public void trackLoaded(AudioTrack track)
        {
            LOG.debug("PlayNext track loaded: guild={}, track=\"{}\"", guild.getId(), track.getInfo().title);
            loadSingle(track);
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist)
        {
            LOG.debug("PlayNext playlist loaded (selecting single): guild={}, name=\"{}\", tracks={}",
                    guild.getId(), playlist.getName(), playlist.getTracks().size());

            AudioTrack single;
            if (playlist.getTracks().size() == 1 || playlist.isSearchResult())
            {
                single = playlist.getSelectedTrack() == null ? playlist.getTracks().get(0) : playlist.getSelectedTrack();
            }
            else if (playlist.getSelectedTrack() != null)
            {
                single = playlist.getSelectedTrack();
            }
            else
            {
                single = playlist.getTracks().get(0);
            }
            loadSingle(single);
        }
    }
}
