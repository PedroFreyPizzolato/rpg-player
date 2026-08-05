/*
 * Copyright 2026 John Grosh <john.a.grosh@gmail.com>.
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
package com.jagrosh.jmusicbot.listener;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.PhaseConfig;
import com.jagrosh.jmusicbot.audio.SegmentPlayer;
import com.jagrosh.jmusicbot.listener.interaction.OutputAdapters;
import com.jagrosh.jmusicbot.service.PhaseService;
import com.jagrosh.jmusicbot.utils.PhaseMessageFormatter;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IModalCallback;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.modals.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Botões, selects e modais do modo fase: controlar a reprodução e editar as segmentações
 * sem sair do Discord.
 *
 * <p>Os ids seguem {@code phase:<ação>[:<índice>...]}. Faixas e fases entram por índice
 * (ver {@link PhaseMessageFormatter#buildPanel}), então o painel é sempre redesenhado depois
 * de uma alteração para os índices não ficarem velhos.
 */
public class PhaseInteractionListener extends ListenerAdapter
{
    private static final Logger LOG = LoggerFactory.getLogger(PhaseInteractionListener.class);

    private final Bot bot;

    public PhaseInteractionListener(Bot bot)
    {
        this.bot = bot;
    }

    // ── botões ───────────────────────────────────────────────────────────────

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event)
    {
        String id = event.getComponentId();
        if (!id.startsWith(PhaseMessageFormatter.PREFIX) || event.getGuild() == null)
            return;

        String[] parts = id.substring(PhaseMessageFormatter.PREFIX.length()).split(":");
        String action = parts[0];
        PhaseService phases = bot.getPhaseService();

        switch (action)
        {
            case "panel" -> showPanel(event, -1, null);
            case "refresh" -> refreshPanel(event, argInt(parts, 1, -1), null);
            case "newtrack" -> event.replyModal(PhaseMessageFormatter.trackModal(-1, null, null)).queue();
            case "editsrc" -> openTrackModal(event, argInt(parts, 1, -1));
            case "linksrc" -> linkCurrentSource(event, argInt(parts, 1, -1));
            case "add" -> openPhaseModal(event, argInt(parts, 1, -1), -1);
            case "play" -> playTrack(event, argInt(parts, 1, -1));

            case "tophases" -> switchMode(event, true);
            case "tonormal" -> switchMode(event, false);

            case "next" -> withRefresh(event, () ->
                    phases.next(event.getGuild(), silentOn(event)));
            case "pause" -> withRefresh(event, () ->
                    phases.togglePause(event.getGuild(), silentOn(event)));
            case "stop" -> withRefresh(event, () ->
                    phases.stop(event.getGuild(), silentOn(event)));
            case "mark" -> markHere(event);
            default -> { }
        }
    }

    private void playTrack(ButtonInteractionEvent event, int trackIndex)
    {
        PhaseConfig config = load(event);
        if (config == null)
            return;
        if (trackIndex < 0 || trackIndex >= config.tracks.size())
        {
            reply(event, "Essa faixa não existe mais.");
            return;
        }
        if (!inSameVoiceChannel(event))
            return;

        PhaseConfig.Track track = config.tracks.get(trackIndex);
        // TAREFA 2: preset escolhido, não o primeiro
        PhaseConfig.Segmentation segmentation = track.firstSegmentation();
        if (segmentation == null)
        {
            reply(event, "`" + track.name + "` não tem nenhuma fase definida.");
            return;
        }

        event.deferReply(true).queue();
        bot.getPhaseService().startAt(event.getGuild(), event.getChannel(),
                segmentation, 0, OutputAdapters.forPhaseDeferred(event));
    }

    /**
     * Alterna entre tocar normalmente e tocar em fases sem parar a música. Os dois lados podem
     * demorar (decodificar o trecho, ou carregar a faixa no lavaplayer), então a interação é
     * adiada — a tela do player é redesenhada pelo próprio serviço quando a troca acontece.
     */
    private void switchMode(ButtonInteractionEvent event, boolean toPhases)
    {
        if (!inSameVoiceChannel(event))
            return;

        event.deferReply(true).queue();
        var output = OutputAdapters.forPhaseDeferred(event);
        if (toPhases)
            bot.getPhaseService().switchToPhases(event.getGuild(), event.getChannel(), output);
        else
            bot.getPhaseService().switchToNormal(event.getGuild(), event.getChannel(), output);
    }

    private void linkCurrentSource(ButtonInteractionEvent event, int trackIndex)
    {
        PhaseConfig config = load(event);
        if (config == null || !validTrack(event, config, trackIndex))
            return;

        var handler = (com.jagrosh.jmusicbot.audio.AudioHandler)
                event.getGuild().getAudioManager().getSendingHandler();
        var playing = handler == null ? null : handler.getPlayer().getPlayingTrack();
        if (playing == null)
        {
            reply(event, "Nada tocando agora pra vincular. Dê play na outra fonte primeiro.");
            return;
        }

        String trackName = config.tracks.get(trackIndex).name;
        String error = bot.getPhaseService().linkCurrentSource(trackName, playing);
        if (error != null)
        {
            reply(event, error);
            return;
        }
        refreshPanel(event, trackIndex, "Fonte atual vinculada.");
    }

    private void markHere(ButtonInteractionEvent event)
    {
        SegmentPlayer player = bot.getPhaseService().getSegmentPlayer(event.getGuild());
        if (player == null)
        {
            reply(event, "Nenhuma fase tocando.");
            return;
        }
        event.reply(PhaseMessageFormatter.buildMarkPrompt(player, player.getPositionMs()))
                .setEphemeral(true).queue();
    }

    // ── selects ──────────────────────────────────────────────────────────────

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event)
    {
        String id = event.getComponentId();
        if (!id.startsWith(PhaseMessageFormatter.PREFIX) || event.getGuild() == null)
            return;

        String[] parts = id.substring(PhaseMessageFormatter.PREFIX.length()).split(":");
        String action = parts[0];
        String value = event.getValues().isEmpty() ? "" : event.getValues().get(0);

        switch (action)
        {
            case "selecttrack" -> refreshPanel(event, parseInt(value, -1), null);
            case "editphase" -> openPhaseModal(event, argInt(parts, 1, -1), parseInt(value, -1));
            case "delphase" -> deletePhase(event, argInt(parts, 1, -1), parseInt(value, -1));
            case "jump" -> jumpToPhase(event, parseInt(value, -1));
            case "markto" -> applyMark(event, argLong(parts, 1, -1), value);
            default -> { }
        }
    }

    private void jumpToPhase(StringSelectInteractionEvent event, int phaseIndex)
    {
        SegmentPlayer player = bot.getPhaseService().getSegmentPlayer(event.getGuild());
        if (player == null)
        {
            reply(event, "Nenhuma fase tocando.");
            return;
        }
        if (!inSameVoiceChannel(event))
            return;

        event.deferReply(true).queue();
        bot.getPhaseService().startAt(event.getGuild(), event.getChannel(),
                player.getSegmentation(), phaseIndex, OutputAdapters.forPhaseDeferred(event));
    }

    private void deletePhase(StringSelectInteractionEvent event, int trackIndex, int phaseIndex)
    {
        PhaseConfig config = load(event);
        if (config == null || !validTrack(event, config, trackIndex))
            return;

        String trackName = config.tracks.get(trackIndex).name;
        String error = bot.getPhaseService().deletePhase(trackName, phaseIndex);
        if (error != null)
        {
            reply(event, error);
            return;
        }
        bot.getPhaseService().reloadIfPlaying(event.getGuild(), trackName, event.getChannel());
        refreshPanel(event, trackIndex, "Fase excluída.");
    }

    private void applyMark(StringSelectInteractionEvent event, long positionMs, String target)
    {
        SegmentPlayer player = bot.getPhaseService().getSegmentPlayer(event.getGuild());
        if (player == null)
        {
            reply(event, "Nenhuma fase tocando.");
            return;
        }

        String trackName = player.getSegmentation().trackName();
        String error = bot.getPhaseService().applyMark(trackName, positionMs, target);
        if (error != null)
        {
            reply(event, error);
            return;
        }
        bot.getPhaseService().reloadIfPlaying(event.getGuild(), trackName, event.getChannel());
        event.editMessage("✅ Marcado em `" + positionMs / 1000.0 + "s`. Segmentação salva.")
                .setComponents()
                .queue();
    }

    // ── modais ───────────────────────────────────────────────────────────────

    @Override
    public void onModalInteraction(ModalInteractionEvent event)
    {
        String id = event.getModalId();
        if (!id.startsWith(PhaseMessageFormatter.PREFIX) || event.getGuild() == null)
            return;

        String[] parts = id.substring(PhaseMessageFormatter.PREFIX.length()).split(":");
        PhaseConfig config = load(event);
        if (config == null)
            return;

        if ("trackmodal".equals(parts[0]))
        {
            int trackIndex = argInt(parts, 1, -1);
            String original = trackIndex >= 0 && trackIndex < config.tracks.size()
                    ? config.tracks.get(trackIndex).name : null;
            String name = field(event, "name");
            String error = bot.getPhaseService().saveTrack(original, name, field(event, "source"));
            if (error != null)
            {
                reply(event, error);
                return;
            }
            refreshPanelInPlace(event, name, "Faixa salva.");
            return;
        }

        if ("phasemodal".equals(parts[0]))
        {
            int trackIndex = argInt(parts, 1, -1);
            int phaseIndex = argInt(parts, 2, -1);
            if (!validTrack(event, config, trackIndex))
                return;

            String trackName = config.tracks.get(trackIndex).name;
            String error = bot.getPhaseService().savePhase(trackName, phaseIndex,
                    field(event, "name"), field(event, "start"), field(event, "end"),
                    field(event, "fade"));
            if (error != null)
            {
                reply(event, error);
                return;
            }
            bot.getPhaseService().reloadIfPlaying(event.getGuild(), trackName, event.getChannel());
            refreshPanelInPlace(event, trackName, "Fase salva.");
        }
    }

    /**
     * Reabre o painel na mesma mensagem que originou o modal (o Discord repassa qual foi),
     * já com a faixa que acabou de mudar selecionada — em vez de mandar uma confirmação solta
     * e deixar o painel velho, sem atualizar, parado no chat.
     */
    private void refreshPanelInPlace(ModalInteractionEvent event, String trackName, String notice)
    {
        PhaseConfig fresh = load(event);
        if (fresh == null)
            return;
        int trackIndex = fresh.indexOfName(trackName);
        event.editMessage(PhaseMessageFormatter.buildPanel(fresh, trackIndex, notice)).queue();
    }

    private <T extends IReplyCallback & IModalCallback> void openPhaseModal(T event, int trackIndex, int phaseIndex)
    {
        PhaseConfig config = load(event);
        if (config == null || !validTrack(event, config, trackIndex))
            return;

        PhaseConfig.Track track = config.tracks.get(trackIndex);
        // TAREFA 2: preset escolhido, não o primeiro
        List<PhaseConfig.Phase> phases = track.presets.isEmpty()
                ? List.of() : track.presets.get(0).phases;
        PhaseConfig.Phase existing = phaseIndex >= 0 && phaseIndex < phases.size()
                ? phases.get(phaseIndex) : null;

        // fase nova já vem sugerida logo depois da última, que é o caso comum
        double suggestedStart = phases.isEmpty() ? 0 : phases.get(phases.size() - 1).end;
        Modal modal = PhaseMessageFormatter.phaseModal(trackIndex, phaseIndex, existing,
                suggestedStart, suggestedStart + 60);
        event.replyModal(modal).queue();
    }

    private void openTrackModal(ButtonInteractionEvent event, int trackIndex)
    {
        PhaseConfig config = load(event);
        if (config == null || !validTrack(event, config, trackIndex))
            return;

        PhaseConfig.Track track = config.tracks.get(trackIndex);
        event.replyModal(PhaseMessageFormatter.trackModal(trackIndex, track.name, track.identifier()))
                .queue();
    }

    // ── painel ───────────────────────────────────────────────────────────────

    /**
     * Primeira abertura: mensagem efêmera nova, só para quem clicou.
     *
     * <p>{@code trackIndex} negativo pede pra detectar sozinho: se a faixa (ou fase) que já
     * está tocando bate com alguma cadastrada, o painel abre nela em vez da tela vazia de
     * "escolha uma faixa" — evita ter que procurar de novo o que você acabou de ouvir.
     */
    private void showPanel(ButtonInteractionEvent event, int trackIndex, String notice)
    {
        PhaseConfig config = load(event);
        if (config == null)
            return;
        if (trackIndex < 0)
            trackIndex = detectCurrentTrackIndex(event.getGuild(), config);
        event.reply(net.dv8tion.jda.api.utils.messages.MessageCreateData.fromEditData(
                        PhaseMessageFormatter.buildPanel(config, trackIndex, notice)))
                .setEphemeral(true).queue();
    }

    private int detectCurrentTrackIndex(Guild guild, PhaseConfig config)
    {
        var handler = (com.jagrosh.jmusicbot.audio.AudioHandler) guild.getAudioManager().getSendingHandler();
        if (handler == null)
            return -1;

        SegmentPlayer segments = handler.getSegmentPlayer();
        if (segments != null)
            return config.indexOfName(segments.getSegmentation().trackName());

        var playing = handler.getPlayer().getPlayingTrack();
        return playing == null ? -1 : config.indexMatchingPlayback(playing);
    }

    /** Redesenha o painel já aberto (o índice das faixas pode ter mudado). */
    private void refreshPanel(net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent event,
                              int trackIndex, String notice)
    {
        PhaseConfig config = load(event);
        if (config == null)
            return;
        event.editMessage(PhaseMessageFormatter.buildPanel(config, trackIndex, notice)).queue();
    }

    /** Executa a ação e redesenha o now-playing do modo fase por cima do botão clicado. */
    private void withRefresh(ButtonInteractionEvent event, Runnable action)
    {
        action.run();
        var handler = (com.jagrosh.jmusicbot.audio.AudioHandler)
                event.getGuild().getAudioManager().getSendingHandler();
        if (handler == null)
            return;

        var message = handler.getSegmentPlayer() != null
                ? handler.getNowPlaying(event.getJDA())
                : handler.getNoMusicPlaying(event.getJDA());
        if (message == null || event.isAcknowledged())
            return;
        event.editMessage(net.dv8tion.jda.api.utils.messages.MessageEditData.fromCreateData(message)).queue();
    }

    // ── util ─────────────────────────────────────────────────────────────────

    private PhaseConfig load(IReplyCallback event)
    {
        try
        {
            return bot.getPhaseService().loadConfig();
        }
        catch (IOException e)
        {
            LOG.warn("Falha ao ler o arquivo de fases", e);
            reply(event, "Não consegui ler o `" + PhaseConfig.FILE_NAME + "`: " + e.getMessage());
            return null;
        }
    }

    private boolean validTrack(IReplyCallback event, PhaseConfig config, int trackIndex)
    {
        if (trackIndex >= 0 && trackIndex < config.tracks.size())
            return true;
        reply(event, "Essa faixa não existe mais. Reabra o painel.");
        return false;
    }

    private boolean inSameVoiceChannel(IReplyCallback event)
    {
        Guild guild = event.getGuild();
        var self = guild.getSelfMember().getVoiceState();
        var member = event.getMember() == null ? null : event.getMember().getVoiceState();

        if (member == null || !member.inAudioChannel())
        {
            reply(event, "Você precisa estar num canal de voz.");
            return false;
        }
        if (self != null && self.getChannel() != null && !member.getChannel().equals(self.getChannel()))
        {
            reply(event, "Você precisa estar no mesmo canal de voz que eu.");
            return false;
        }
        return true;
    }

    private static void reply(IReplyCallback event, String content)
    {
        if (event.isAcknowledged())
            event.getHook().sendMessage(content).setEphemeral(true).queue();
        else
            event.reply(content).setEphemeral(true).queue();
    }

    private static String field(ModalInteractionEvent event, String id)
    {
        var value = event.getValue(id);
        return value == null ? null : value.getAsString();
    }

    /** Para ações cuja resposta visível é o próprio painel redesenhado. */
    private static com.jagrosh.jmusicbot.service.MusicService.OutputAdapter silentOn(IReplyCallback event)
    {
        return new com.jagrosh.jmusicbot.commands.BaseOutputAdapter()
        {
            @Override
            public void replyError(String content)
            {
                reply(event, content);
            }

            @Override
            public void replyWarning(String content)
            {
                reply(event, content);
            }
        };
    }

    private static int argInt(String[] parts, int index, int fallback)
    {
        return index < parts.length ? parseInt(parts[index], fallback) : fallback;
    }

    private static long argLong(String[] parts, int index, long fallback)
    {
        if (index >= parts.length)
            return fallback;
        try
        {
            return Long.parseLong(parts[index]);
        }
        catch (NumberFormatException e)
        {
            return fallback;
        }
    }

    private static int parseInt(String value, int fallback)
    {
        try
        {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException e)
        {
            return fallback;
        }
    }
}
