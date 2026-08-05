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
package com.jagrosh.jmusicbot.service;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.PhaseConfig;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.audio.SegmentPlayer;
import com.jagrosh.jmusicbot.utils.TimeUtil;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Modo fase: toca um trecho de uma faixa em loop até o mestre liberar a continuação, e mantém
 * o arquivo de segmentações.
 *
 * <p>Enquanto está ativo o {@link AudioHandler} entrega os frames do {@link SegmentPlayer} em
 * vez dos do lavaplayer, e o player normal fica pausado (pausar em vez de parar evita que o
 * fim da faixa desconecte o bot do canal e preserva a fila).
 */
public class PhaseService
{
    private static final Logger LOG = LoggerFactory.getLogger(PhaseService.class);

    /** Sobreposição entre o fim de um segmento e o começo do próximo, quando a fase não define. */
    private static final int CROSSFADE_MS = PhaseConfig.DEFAULT_FADE_MS;

    private final Bot bot;

    public PhaseService(Bot bot)
    {
        this.bot = bot;
    }

    // ── reprodução ───────────────────────────────────────────────────────────

    public void start(Guild guild, MessageChannel channel, String query, MusicService.OutputAdapter output)
    {
        PhaseConfig config = loadOrReport(output);
        if (config == null)
            return;

        PhaseConfig.Match match = config.find(query);
        if (match == null)
        {
            output.replyError("Não achei `" + query + "` (nem como faixa, nem como fase) no `"
                    + PhaseConfig.FILE_NAME + "`.");
            return;
        }
        startAt(guild, channel, match.segmentation, match.phaseIndex, output);
    }

    /**
     * Começa (ou reinicia) o modo fase numa fase específica. Decodificar o trecho leva alguns
     * segundos, então o aviso vai pelo canal quando termina, não como resposta da interação.
     */
    public void startAt(Guild guild, MessageChannel channel, PhaseConfig.Segmentation segmentation,
                        int phaseIndex, MusicService.OutputAdapter output)
    {
        if (segmentation.phases().isEmpty())
        {
            output.replyError("`" + segmentation.trackName() + "` não tem nenhuma fase definida.");
            return;
        }
        if (segmentation.identifier() == null)
        {
            output.replyError("`" + segmentation.trackName() + "` não tem fonte definida (URL ou arquivo).");
            return;
        }

        AudioHandler handler = getHandler(guild);
        if (handler == null)
        {
            output.replyError("Não estou conectado a um canal de voz.");
            return;
        }

        int index = Math.max(0, Math.min(phaseIndex, segmentation.phases().size() - 1));
        PhaseConfig.Phase phase = segmentation.phases().get(index);
        // entrando pela fase 0: toca desde o 0:00 até o início dela, em vez de pular a intro —
        // mesma regra do fim da última fase até o fim do arquivo, agora do outro lado da música
        long captureStart = index == 0 ? 0 : phase.startMs();
        output.replySuccess("Decodificando **" + segmentation.trackName() + "** — fase `"
                + phase.name + "`...");

        bot.getSegmentCapture()
                .captureAsync(segmentation.identifier(), captureStart, phase.endMs())
                .whenComplete((segment, error) ->
                {
                    if (error != null)
                    {
                        LOG.warn("Falha ao capturar a fase '{}' de '{}'", phase.name,
                                segmentation.trackName(), error);
                        channel.sendMessage("Não consegui carregar **" + segmentation.trackName()
                                + "**: " + rootMessage(error)).queue();
                        return;
                    }

                    handler.getPlayer().setPaused(true);
                    handler.setSegmentPlayer(SegmentPlayer.resumingAt(
                            bot.getSegmentCapture()::captureAsync, segmentation, index, segment,
                            captureStart, captureStart, CROSSFADE_MS, volumeOf(guild),
                            onPhaseChange(handler, guild, channel),
                            onFinish(handler, channel, segmentation.trackName())));

                    LOG.info("Modo fase iniciado: guild={}, faixa=\"{}\", fase=\"{}\"",
                            guild.getId(), segmentation.trackName(), phase.name);
                    // a tela do modo fase já traz os controles; é ela que o mestre opera
                    refreshNowPlaying(handler, guild, channel);
                });
    }

    // ── troca de modo com a música tocando ───────────────────────────────────

    /**
     * Entra no modo fase sem cortar o que já está tocando: decodifica a fase em que a música
     * está agora e assume a saída de áudio no ponto exato em que o lavaplayer parou.
     *
     * <p>A decodificação leva alguns segundos e a música continua tocando nesse meio tempo, por
     * isso o ponto de entrada só é lido depois que o trecho fica pronto — e o trecho capturado é
     * a fase <i>inteira</i>, não só o que falta dela, senão o loop ficaria preso no pedaço final.
     */
    public void switchToPhases(Guild guild, MessageChannel channel, MusicService.OutputAdapter output)
    {
        AudioHandler handler = getHandler(guild);
        if (handler == null)
        {
            output.replyError("Não estou conectado a um canal de voz.");
            return;
        }
        if (handler.getSegmentPlayer() != null)
        {
            output.replyError("Já estou no modo fase.");
            return;
        }

        AudioTrack playing = handler.getPlayer().getPlayingTrack();
        if (playing == null)
        {
            output.replyError("Nada tocando para colocar em fases.");
            return;
        }

        PhaseConfig.Track track = findMatchingPhases(playing);
        // TAREFA 2: preset escolhido, não o primeiro
        PhaseConfig.Segmentation segmentation = track == null ? null : track.firstSegmentation();
        // preset sem fase nenhuma (faixa recém-criada) não tem em que fase entrar
        if (segmentation == null || segmentation.phases().isEmpty())
        {
            output.replyError("A faixa que está tocando não tem fases cadastradas. Cadastre pelo"
                    + " botão **Fases** (ou vincule esta fonte a uma faixa que já tenha).");
            return;
        }

        PhaseEntry entry = planEntry(segmentation, playing.getPosition());
        PhaseConfig.Phase phase = segmentation.phases().get(entry.phaseIndex);
        output.replySuccess("Decodificando `" + phase.name + "` para continuar de onde está...");

        bot.getSegmentCapture()
                .captureAsync(segmentation.identifier(), entry.captureStartMs, entry.captureEndMs)
                .whenComplete((segment, error) ->
                {
                    if (error != null)
                    {
                        LOG.warn("Falha ao entrar no modo fase de '{}'", segmentation.trackName(), error);
                        channel.sendMessage("Não consegui entrar no modo fase: "
                                + rootMessage(error)).queue();
                        return;
                    }

                    // decodificar leva segundos e a música não espera: se ela acabou (ou alguém
                    // entrou no modo fase enquanto isso), entrar agora tomaria o áudio de outra
                    // coisa e a posição capturada não teria mais nada a ver com o que está tocando
                    if (handler.getPlayer().getPlayingTrack() != playing || handler.getSegmentPlayer() != null)
                    {
                        channel.sendMessage("A reprodução mudou enquanto eu decodificava **"
                                + segmentation.trackName() + "**; não entrei no modo fase.").queue();
                        return;
                    }

                    // pausa antes de ler a posição: com o lavaplayer parado ela não corre mais,
                    // então a entrada cai exatamente onde o ouvinte parou de escutar
                    handler.getPlayer().setPaused(true);
                    handler.setSegmentPlayer(SegmentPlayer.resumingAt(
                            bot.getSegmentCapture()::captureAsync, segmentation, entry.phaseIndex,
                            segment, entry.captureStartMs, playing.getPosition(), CROSSFADE_MS,
                            volumeOf(guild), onPhaseChange(handler, guild, channel),
                            onFinish(handler, channel, segmentation.trackName())));

                    LOG.info("Modo fase assumido em reprodução: guild={}, faixa=\"{}\", fase=\"{}\"",
                            guild.getId(), segmentation.trackName(), phase.name);
                    refreshNowPlaying(handler, guild, channel);
                });
    }

    /**
     * Sai do modo fase mantendo a música: devolve a saída ao lavaplayer já posicionado onde o
     * loop estava. Se a faixa não estiver carregada — o modo fase começou direto pelo
     * {@code /play} ou pelo painel, sem passar pelo player normal — ela é carregada e
     * posicionada antes de voltar a tocar.
     */
    public void switchToNormal(Guild guild, MessageChannel channel, MusicService.OutputAdapter output)
    {
        AudioHandler handler = getHandler(guild);
        SegmentPlayer segments = handler == null ? null : handler.getSegmentPlayer();
        if (segments == null)
        {
            output.replyError("Nenhuma fase tocando.");
            return;
        }

        long position = segments.getPositionMs();
        PhaseConfig.Track track = segments.getSegmentation().track;
        Message previous = handler.getSegmentNowPlayingMessage();
        AudioTrack loaded = handler.getPlayer().getPlayingTrack();

        if (loaded != null && track.matches(loaded))
        {
            resumeNormal(handler, guild, channel, loaded, position, previous);
            output.replySuccess("Modo normal, seguindo de `" + TimeUtil.formatTime(position) + "`.");
            return;
        }

        output.replySuccess("Carregando **" + track.name + "** para seguir em modo normal...");
        bot.getPlayerManager().loadItemOrdered(guild, track.identifier(), new AudioLoadResultHandler()
        {
            @Override
            public void trackLoaded(AudioTrack loadedTrack)
            {
                resumeNormal(handler, guild, channel, loadedTrack, position, previous);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist)
            {
                AudioTrack single = playlist.getSelectedTrack() != null ? playlist.getSelectedTrack()
                        : playlist.getTracks().isEmpty() ? null : playlist.getTracks().get(0);
                if (single == null)
                    noMatches();
                else
                    resumeNormal(handler, guild, channel, single, position, previous);
            }

            @Override
            public void noMatches()
            {
                channel.sendMessage("Não achei a fonte de **" + track.name
                        + "** para voltar ao modo normal.").queue();
            }

            @Override
            public void loadFailed(FriendlyException exception)
            {
                LOG.warn("Falha ao carregar '{}' para sair do modo fase", track.name, exception);
                channel.sendMessage("Não consegui carregar **" + track.name + "**: "
                        + exception.getMessage()).queue();
            }
        });
    }

    /** Devolve a saída ao lavaplayer com a faixa posicionada onde o modo fase parou. */
    private void resumeNormal(AudioHandler handler, Guild guild, MessageChannel channel,
                              AudioTrack track, long positionMs, Message previous)
    {
        boolean alreadyPlaying = track == handler.getPlayer().getPlayingTrack();
        handler.setSegmentPlayer(null);
        handler.setSegmentNowPlayingMessage(null);

        if (track.isSeekable())
            track.setPosition(positionMs);
        handler.getPlayer().setPaused(false);
        if (!alreadyPlaying)
        {
            // a posição é guardada até o playback começar (o executor a aplica no primeiro frame)
            track.setUserData(RequestMetadata.EMPTY);
            handler.getPlayer().playTrack(track);
        }

        LOG.info("Modo fase encerrado seguindo normal: guild={}, posicao={}ms", guild.getId(), positionMs);
        MessageCreateData nowPlaying = handler.getNowPlaying(guild.getJDA());
        if (nowPlaying != null)
            editOrSend(previous, channel, nowPlaying, message -> { });
    }

    /**
     * Qual fase assumir a partir de uma posição qualquer da música, e o trecho a decodificar
     * para chegar nela sem cortar o áudio.
     */
    static PhaseEntry planEntry(PhaseConfig.Segmentation segmentation, long positionMs)
    {
        for (int i = 0; i < segmentation.phases().size(); i++)
        {
            PhaseConfig.Phase phase = segmentation.phases().get(i);
            if (positionMs >= phase.endMs())
                continue;
            // dentro da fase: captura ela inteira, senão o loop ficaria só no pedaço que falta
            if (positionMs >= phase.startMs())
                return new PhaseEntry(i, phase.startMs(), phase.endMs());
            // antes dela (intro, ou passagem entre duas fases): toca o que falta e cai no loop
            return new PhaseEntry(i, positionMs, phase.endMs());
        }
        // passou da última fase: não há o que emendar adiante, volta para o início dela
        int last = segmentation.phases().size() - 1;
        return new PhaseEntry(last, segmentation.phases().get(last).startMs(),
                segmentation.phases().get(last).endMs());
    }

    /** Fase escolhida por {@link #planEntry} e o trecho que precisa ser decodificado. */
    static class PhaseEntry
    {
        final int phaseIndex;
        final long captureStartMs;
        final long captureEndMs;

        PhaseEntry(int phaseIndex, long captureStartMs, long captureEndMs)
        {
            this.phaseIndex = phaseIndex;
            this.captureStartMs = captureStartMs;
            this.captureEndMs = captureEndMs;
        }
    }

    public void next(Guild guild, MusicService.OutputAdapter output)
    {
        SegmentPlayer player = getSegmentPlayer(guild);
        if (player == null)
        {
            output.replyError("Nenhuma fase tocando.");
            return;
        }

        boolean last = player.isOnLastPhase();
        player.unlockNext();

        if (last)
        {
            output.replySuccess("Liberado: `" + player.getPhaseName()
                    + "` toca até o fim e a música encerra.");
            return;
        }

        String upcoming = player.getSegmentation().phases().get(player.getPhaseIndex() + 1).name;
        output.replySuccess("Liberado: ao fim de `" + player.getPhaseName()
                + "` a música segue para `" + upcoming + "`.");
    }

    public void togglePause(Guild guild, MusicService.OutputAdapter output)
    {
        SegmentPlayer player = getSegmentPlayer(guild);
        if (player == null)
        {
            output.replyError("Nenhuma fase tocando.");
            return;
        }
        player.setPaused(!player.isPaused());
        output.replySuccess(player.isPaused() ? "Pausado." : "Retomado.");
    }

    public void stop(Guild guild, MusicService.OutputAdapter output)
    {
        AudioHandler handler = getHandler(guild);
        if (handler == null || handler.getSegmentPlayer() == null)
        {
            output.replyError("Nenhuma fase tocando.");
            return;
        }

        restoreNormalPlayback(handler);
        LOG.info("Modo fase encerrado: guild={}", guild.getId());
        output.replySuccess("Modo fase encerrado.");
    }

    public void list(Guild guild, MusicService.OutputAdapter output)
    {
        PhaseConfig config = loadOrReport(output);
        if (config == null)
            return;

        if (config.tracks.isEmpty())
        {
            output.replyWarning("Nenhuma faixa cadastrada. Use o botão **Fases** para criar.");
            return;
        }

        StringBuilder message = new StringBuilder("**Faixas com fases:**\n");
        for (PhaseConfig.Track track : config.tracks)
        {
            message.append("`").append(track.name).append("` — ");
            // TAREFA 2: preset escolhido, não o primeiro
            PhaseConfig.Segmentation segmentation = track.firstSegmentation();
            List<PhaseConfig.Phase> phases = segmentation == null ? List.of() : segmentation.phases();
            for (int i = 0; i < phases.size(); i++)
                message.append(i > 0 ? ", " : "").append(phases.get(i).name);
            message.append("\n");
        }

        SegmentPlayer playing = getSegmentPlayer(guild);
        if (playing != null)
            message.append("\nTocando agora: **").append(playing.getSegmentation().trackName())
                    .append("** — `").append(playing.getPhaseName()).append("`");

        output.replySuccess(message.toString());
    }

    // ── edição das segmentações ──────────────────────────────────────────────

    /** Cria ou atualiza uma faixa (nome + fonte). Devolve null se deu tudo certo. */
    public String saveTrack(String originalName, String name, String source)
    {
        if (name == null || name.isBlank())
            return "O nome da faixa não pode ficar vazio.";
        if (source == null || source.isBlank())
            return "A fonte não pode ficar vazia (URL do YouTube ou caminho do arquivo).";

        try
        {
            PhaseConfig config = PhaseConfig.load();
            PhaseConfig.Track track = originalName == null
                    ? config.findOrCreate(name.trim(), source.trim())
                    : rawFind(config, originalName);
            if (track == null)
                return "A faixa `" + originalName + "` sumiu do arquivo.";

            track.name = name.trim();
            track.source = source.trim();
            track.file = null;   // 'source' passa a mandar
            config.save();
            return null;
        }
        catch (IOException e)
        {
            return "Não consegui gravar o `" + PhaseConfig.FILE_NAME + "`: " + e.getMessage();
        }
    }

    /**
     * Vincula a fonte que está tocando agora como fonte alternativa da faixa — mesma música,
     * outro link (ex: cadastrada pelo YouTube, tocando agora pelo YouTube Music). A partir daí
     * a auto-detecção do painel e a oferta de modo fase no {@code /play} reconhecem essa fonte
     * também. Devolve a mensagem de erro, ou null se deu certo.
     */
    public String linkCurrentSource(String trackName, AudioTrack playing)
    {
        String uri = playing.getInfo() != null ? playing.getInfo().uri : null;
        if (uri == null || uri.isBlank())
            return "Essa faixa não tem uma URL utilizável.";

        try
        {
            PhaseConfig config = PhaseConfig.load();
            PhaseConfig.Track track = rawFind(config, trackName);
            if (track == null)
                return "A faixa `" + trackName + "` sumiu do arquivo.";

            int matched = config.indexMatchingPlayback(playing);
            int trackIndex = config.tracks.indexOf(track);
            if (matched == trackIndex)
                return "Essa já é uma fonte reconhecida de `" + trackName + "`.";
            if (matched >= 0)
                return "Essa fonte já está vinculada a `" + config.tracks.get(matched).name + "`.";

            track.aliases.add(uri);
            config.save();
            return null;
        }
        catch (IOException e)
        {
            return "Não consegui gravar o `" + PhaseConfig.FILE_NAME + "`: " + e.getMessage();
        }
    }

    /**
     * Grava uma fase. {@code phaseIndex} negativo cria uma nova. Devolve a mensagem de erro,
     * ou null se deu certo.
     */
    public String savePhase(String trackName, int phaseIndex, String name, String startText,
                            String endText, String fadeText)
    {
        if (name == null || name.isBlank())
            return "O nome da fase não pode ficar vazio.";

        Double start = parseSeconds(startText);
        Double end = parseSeconds(endText);
        if (start == null)
            return "Início inválido: `" + startText + "`. Use segundos, ex: `66.5`.";
        if (end == null)
            return "Fim inválido: `" + endText + "`. Use segundos, ex: `118`.";
        if (end <= start)
            return "O fim (`" + end + "`) precisa vir depois do início (`" + start + "`).";

        // vazio = segue o padrão; guardar null (e não o padrão) deixa a fase acompanhar caso o
        // padrão do bot mude depois
        Double fade = null;
        if (fadeText != null && !fadeText.isBlank())
        {
            fade = parseSeconds(fadeText);
            if (fade == null)
                return "Fade inválido: `" + fadeText + "`. Use segundos, ex: `0.5`,"
                        + " ou deixe vazio para o padrão (" + CROSSFADE_MS / 1000 + "s).";
            // o crossfade cruza o fim da fase com o começo dela: passando da metade, os dois
            // lados se sobreporiam consigo mesmos. O motor cortaria calado — melhor avisar.
            double half = (end - start) / 2;
            if (fade > half)
                return "O fade de `" + fade + "s` não cabe nesta fase: o máximo é metade dela (`"
                        + half + "s`).";
        }

        try
        {
            PhaseConfig config = PhaseConfig.load();
            PhaseConfig.Track track = rawFind(config, trackName);
            if (track == null)
                return "A faixa `" + trackName + "` sumiu do arquivo.";

            PhaseConfig.Preset preset = track.presets.get(0);   // TAREFA 2: preset escolhido, não o primeiro

            PhaseConfig.Phase phase;
            if (phaseIndex < 0 || phaseIndex >= preset.phases.size())
            {
                phase = new PhaseConfig.Phase();
                preset.phases.add(phase);
            }
            else
            {
                phase = preset.phases.get(phaseIndex);
            }
            phase.name = name.trim();
            phase.start = start;
            phase.end = end;
            phase.fade = fade;

            // a lógica de passagem entre fases assume ordem crescente
            preset.phases.sort(Comparator.comparingDouble(p -> p.start));
            config.save();
            return null;
        }
        catch (IOException e)
        {
            return "Não consegui gravar o `" + PhaseConfig.FILE_NAME + "`: " + e.getMessage();
        }
    }

    public String deletePhase(String trackName, int phaseIndex)
    {
        try
        {
            PhaseConfig config = PhaseConfig.load();
            PhaseConfig.Track track = rawFind(config, trackName);
            if (track == null)
                return "A faixa `" + trackName + "` sumiu do arquivo.";
            PhaseConfig.Preset preset = track.presets.get(0);   // TAREFA 2: preset escolhido, não o primeiro
            if (phaseIndex < 0 || phaseIndex >= preset.phases.size())
                return "Essa fase não existe mais.";

            preset.phases.remove(phaseIndex);
            config.save();
            return null;
        }
        catch (IOException e)
        {
            return "Não consegui gravar o `" + PhaseConfig.FILE_NAME + "`: " + e.getMessage();
        }
    }

    /**
     * Aplica uma posição marcada durante a reprodução. {@code target} é {@code "new"},
     * {@code "start:<i>"} ou {@code "end:<i>"}.
     */
    public String applyMark(String trackName, long positionMs, String target)
    {
        double seconds = Math.round(positionMs / 10.0) / 100.0;
        try
        {
            PhaseConfig config = PhaseConfig.load();
            PhaseConfig.Track track = rawFind(config, trackName);
            if (track == null)
                return "A faixa `" + trackName + "` sumiu do arquivo.";

            PhaseConfig.Preset preset = track.presets.get(0);   // TAREFA 2: preset escolhido, não o primeiro

            if ("new".equals(target))
            {
                PhaseConfig.Phase phase = new PhaseConfig.Phase();
                phase.name = "Fase " + (preset.phases.size() + 1);
                phase.start = seconds;
                phase.end = seconds + 30;   // provisório: o mestre ajusta marcando o fim
                preset.phases.add(phase);
            }
            else
            {
                String[] parts = target.split(":", 2);
                if (parts.length != 2)
                    return "Alvo inválido.";
                int index = Integer.parseInt(parts[1]);
                if (index < 0 || index >= preset.phases.size())
                    return "Essa fase não existe mais.";

                PhaseConfig.Phase phase = preset.phases.get(index);
                if ("start".equals(parts[0]))
                {
                    if (seconds >= phase.end)
                        return "Esse ponto (" + seconds + "s) está depois do fim da fase.";
                    phase.start = seconds;
                }
                else
                {
                    if (seconds <= phase.start)
                        return "Esse ponto (" + seconds + "s) está antes do início da fase.";
                    phase.end = seconds;
                }
            }

            preset.phases.sort(Comparator.comparingDouble(p -> p.start));
            config.save();
            return null;
        }
        catch (NumberFormatException e)
        {
            return "Alvo inválido.";
        }
        catch (IOException e)
        {
            return "Não consegui gravar o `" + PhaseConfig.FILE_NAME + "`: " + e.getMessage();
        }
    }

    /**
     * Se a faixa editada é a que está tocando, recarrega o player com a versão nova para que a
     * mudança valha na hora (o {@link SegmentPlayer} guarda a lista de fases que recebeu).
     */
    public void reloadIfPlaying(Guild guild, String trackName, MessageChannel channel)
    {
        SegmentPlayer player = getSegmentPlayer(guild);
        if (player == null || !player.getSegmentation().trackName().equalsIgnoreCase(trackName))
            return;

        try
        {
            PhaseConfig.Track updated = rawFind(PhaseConfig.load(), trackName);
            // TAREFA 2: preset escolhido, não o primeiro
            PhaseConfig.Segmentation segmentation = updated == null ? null : updated.firstSegmentation();
            if (segmentation == null || segmentation.phases().isEmpty())
                return;
            startAt(guild, channel, segmentation, player.getPhaseIndex(), silent());
        }
        catch (IOException e)
        {
            LOG.warn("Não consegui recarregar a faixa em reprodução", e);
        }
    }

    // ── acesso ao estado ─────────────────────────────────────────────────────

    public PhaseConfig loadConfig() throws IOException
    {
        return PhaseConfig.load();
    }

    /** O painel de edição como mensagem nova, para os comandos de texto e slash. */
    public net.dv8tion.jda.api.utils.messages.MessageCreateData buildPanelMessage()
    {
        PhaseConfig config;
        try
        {
            config = PhaseConfig.load();
        }
        catch (IOException e)
        {
            config = new PhaseConfig();
        }
        return net.dv8tion.jda.api.utils.messages.MessageCreateData.fromEditData(
                com.jagrosh.jmusicbot.utils.PhaseMessageFormatter.buildPanel(config, -1, null));
    }

    public SegmentPlayer getSegmentPlayer(Guild guild)
    {
        AudioHandler handler = getHandler(guild);
        return handler == null ? null : handler.getSegmentPlayer();
    }

    /**
     * true quando nada está tocando no guild (nem lavaplayer, nem modo fase) — ou seja, a
     * próxima faixa adicionada tocaria imediatamente (ver {@link AudioHandler#addTrack}).
     */
    public boolean isIdle(Guild guild)
    {
        AudioHandler handler = getHandler(guild);
        return handler != null
                && handler.getPlayer().getPlayingTrack() == null
                && handler.getSegmentPlayer() == null;
    }

    /**
     * A faixa de {@code phases.json} cujo {@code source}/{@code file} bate com o que o
     * lavaplayer acabou de resolver, se ela já tiver alguma segmentação cadastrada; senão null.
     */
    public PhaseConfig.Track findMatchingPhases(AudioTrack playing)
    {
        try
        {
            PhaseConfig config = PhaseConfig.load();
            int index = config.indexMatchingPlayback(playing);
            if (index < 0)
                return null;
            PhaseConfig.Track track = config.tracks.get(index);
            return track.presets.isEmpty() ? null : track;
        }
        catch (IOException e)
        {
            LOG.warn("Não consegui checar se a faixa tem fases cadastradas", e);
            return null;
        }
    }

    // ── internos ─────────────────────────────────────────────────────────────

    private PhaseConfig loadOrReport(MusicService.OutputAdapter output)
    {
        try
        {
            return PhaseConfig.load();
        }
        catch (IOException e)
        {
            output.replyError("Não consegui ler as fases: " + e.getMessage());
            return null;
        }
    }

    /** Busca só por nome de faixa (o find geral também casa nome de fase). */
    private static PhaseConfig.Track rawFind(PhaseConfig config, String name)
    {
        for (PhaseConfig.Track track : config.tracks)
        {
            if (track.name != null && track.name.equalsIgnoreCase(name))
                return track;
        }
        return null;
    }

    private static Double parseSeconds(String text)
    {
        if (text == null)
            return null;
        String cleaned = text.trim().replace(',', '.');
        try
        {
            // aceita também mm:ss, que é como o tempo aparece na tela
            if (cleaned.contains(":"))
            {
                String[] parts = cleaned.split(":");
                double total = 0;
                for (String part : parts)
                    total = total * 60 + Double.parseDouble(part);
                return total;
            }
            double value = Double.parseDouble(cleaned);
            return value < 0 ? null : value;
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private AudioHandler getHandler(Guild guild)
    {
        return (AudioHandler) guild.getAudioManager().getSendingHandler();
    }

    /**
     * Devolve a saída de áudio ao lavaplayer. Para tudo em vez de retomar o que tocava antes:
     * {@link #startAt} pausa (não para) a faixa do lavaplayer ao entrar no modo fase, pra não
     * disparar o fim-de-faixa no meio da captura — mas isso significa que, se o modo fase foi
     * iniciado por cima de uma música que já estava tocando, ela ficava escondida, pausada, e
     * voltava sozinha ao clicar em "Parar", como se o botão não tivesse funcionado.
     */
    private void restoreNormalPlayback(AudioHandler handler)
    {
        handler.setSegmentPlayer(null);
        handler.stopAndClearQueuePreserveHistory();
        handler.setSegmentNowPlayingMessage(null);
    }

    private IntSupplier volumeOf(Guild guild)
    {
        return () -> bot.getSettingsManager().getSettings(guild).getVolume();
    }

    /**
     * A troca de fase acontece na thread de áudio, sem uma interação do Discord à mão pra
     * editar — se só mandasse texto, a tela com os botões (e o "liberar próxima fase") ficaria
     * presa pra sempre na fase antiga.
     */
    private Consumer<String> onPhaseChange(AudioHandler handler, Guild guild, MessageChannel channel)
    {
        return nextPhaseName -> refreshNowPlaying(handler, guild, channel);
    }

    private Runnable onFinish(AudioHandler handler, MessageChannel channel, String trackName)
    {
        return () ->
        {
            restoreNormalPlayback(handler);
            channel.sendMessage("⏹ **" + trackName + "** terminou.").queue();
        };
    }

    /** Redesenha a tela do modo fase na própria mensagem, refletindo a fase atual. */
    private void refreshNowPlaying(AudioHandler handler, Guild guild, MessageChannel channel)
    {
        MessageCreateData nowPlaying = handler.getNowPlaying(guild.getJDA());
        if (nowPlaying != null)
            editOrSend(handler.getSegmentNowPlayingMessage(), channel, nowPlaying,
                    handler::setSegmentNowPlayingMessage);
    }

    /**
     * Edita a mensagem do player, ou manda uma nova se ela sumiu (apagada, permissão perdida) —
     * é dela que saem os controles do modo fase, então falhar calado deixaria os botões presos
     * num estado velho pelo resto da sessão.
     */
    private static void editOrSend(Message previous, MessageChannel channel, MessageCreateData content,
                                   Consumer<Message> remember)
    {
        if (previous == null)
        {
            channel.sendMessage(content).queue(remember::accept);
            return;
        }
        previous.editMessage(MessageEditData.fromCreateData(content))
                .queue(remember::accept, error -> channel.sendMessage(content).queue(remember::accept));
    }

    /** Para recargas internas, onde o usuário já teve resposta pela interação. */
    private static MusicService.OutputAdapter silent()
    {
        return new com.jagrosh.jmusicbot.commands.BaseOutputAdapter() { };
    }

    private static String rootMessage(Throwable error)
    {
        Throwable cause = error;
        while (cause.getCause() != null)
            cause = cause.getCause();
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }
}
