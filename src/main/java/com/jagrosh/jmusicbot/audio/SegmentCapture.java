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
package com.jagrosh.jmusicbot.audio;

import com.jagrosh.jmusicbot.BotConfig;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Decodifica um trecho de uma faixa para PCM cru na memória.
 *
 * <p>Usa um {@link DefaultAudioPlayerManager} próprio configurado com saída PCM (o formato de
 * saída é por manager, não por player, por isso não dá pra reaproveitar o
 * {@link PlayerManager} do bot). Como as mesmas fontes são registradas, qualquer coisa que o
 * bot toca — YouTube, SoundCloud, arquivo local — pode virar segmento.
 *
 * <p>O lavaplayer decodifica mais rápido que tempo real, então drenar os frames num laço
 * apertado devolve o trecho em muito menos tempo que a duração dele.
 */
public class SegmentCapture
{
    /** 48000 Hz * 2 canais * 2 bytes por amostra = 192000 bytes por segundo. */
    public static final int BYTES_PER_MS = 192;

    /** Como {@code endMs}: vai até o fim do arquivo, seja lá onde ele estiver. */
    public static final long UNTIL_END = Long.MAX_VALUE;

    private static final Logger LOGGER = LoggerFactory.getLogger(SegmentCapture.class);

    /**
     * Decodifica um pouco antes do início pedido. O seek do decoder cai no limite do frame do
     * container (~26ms num MP3) e pode passar do alvo; a margem garante que as amostras do
     * ponto exato estejam no buffer para o corte fino depois.
     */
    private static final long PREROLL_MS = 200;

    /** Um frame nunca demora isso pra chegar; se demorar, a faixa travou. */
    private static final long FRAME_TIMEOUT_SECONDS = 30;

    private final DefaultAudioPlayerManager manager;
    private final ExecutorService pool;

    public SegmentCapture(BotConfig config)
    {
        this.manager = new DefaultAudioPlayerManager();
        this.manager.getConfiguration().setOutputFormat(StandardAudioDataFormats.DISCORD_PCM_S16_BE);
        this.pool = Executors.newCachedThreadPool(runnable ->
        {
            Thread thread = new Thread(runnable, "segment-capture");
            thread.setDaemon(true);
            return thread;
        });

        // mesmo proxy do PlayerManager: sem isto, capturar segmento ignoraria o proxy que o
        // play normal usa (ex.: contornar bloqueio de IP de servidor pelo YouTube) e falharia
        // onde o resto do bot funciona
        if (config.proxyLavaplayer() && config.hasProxy())
        {
            org.apache.http.HttpHost proxy = com.jagrosh.jmusicbot.utils.ProxyUtil.createApacheProxy(config);
            this.manager.setHttpBuilderConfigurator(builder -> builder.setProxy(proxy));
            LOGGER.info("Captura de segmentos usando o mesmo proxy do Lavaplayer: {}:{}",
                    config.getProxyHost(), config.getProxyPort());
        }

        for (AudioSource source : config.getEnabledAudioSources())
        {
            try
            {
                source.register(manager, config);
            }
            catch (Exception e)
            {
                LOGGER.error("Falha ao registrar a fonte '{}' na captura de segmentos: {}",
                        source.getConfigName(), e.getMessage(), e);
            }
        }
    }

    /**
     * Decodifica [startMs, endMs) de uma faixa. Bloqueante — chamar fora da thread de eventos.
     *
     * @return PCM s16be 48kHz estéreo, do tamanho exato do trecho pedido
     * @throws IllegalStateException se a faixa não carregar, não permitir seek ou não render áudio
     */
    public byte[] capture(String identifier, long startMs, long endMs)
    {
        if (endMs <= startMs)
            throw new IllegalStateException("O fim do segmento precisa vir depois do início.");

        AudioTrack track = resolve(identifier);
        if (startMs > 0 && !track.isSeekable())
            throw new IllegalStateException("`" + track.getInfo().title + "` não permite seek, "
                    + "não dá pra recortar um segmento no meio dela.");

        long from = Math.max(0, startMs - PREROLL_MS);
        // aplicado quando o playback começa (PrimordialAudioTrackExecutor guarda a posição)
        track.setPosition(from);

        AudioPlayer player = manager.createPlayer();
        // com UNTIL_END não dá pra dimensionar o buffer: começa pequeno e deixa crescer
        int capacity = endMs == UNTIL_END ? 1 << 20
                : (int) Math.min(Integer.MAX_VALUE, (endMs - from) * BYTES_PER_MS);
        ByteArrayOutputStream raw = new ByteArrayOutputStream(capacity);
        long firstTimecode = -1;

        try
        {
            player.playTrack(track);
            while (true)
            {
                AudioFrame frame;
                try
                {
                    frame = player.provide(FRAME_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }
                catch (TimeoutException e)
                {
                    throw new IllegalStateException("A decodificação de `" + identifier + "` travou.");
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Captura interrompida.");
                }

                if (frame == null)
                {
                    if (player.getPlayingTrack() == null)
                        break;   // a faixa acabou antes do fim pedido
                    continue;
                }

                if (firstTimecode < 0)
                    firstTimecode = frame.getTimecode();
                raw.write(frame.getData(), 0, frame.getDataLength());

                if (frame.getTimecode() >= endMs)
                    break;
            }
        }
        finally
        {
            player.destroy();
        }

        return trim(raw.toByteArray(), firstTimecode, startMs, endMs, identifier);
    }

    /** Mesma coisa, na thread de captura. Usado pelo prefetch da próxima fase. */
    public CompletableFuture<byte[]> captureAsync(String identifier, long startMs, long endMs)
    {
        return CompletableFuture.supplyAsync(() -> capture(identifier, startMs, endMs), pool);
    }

    public void shutdown()
    {
        pool.shutdownNow();
        manager.shutdown();
    }

    /**
     * Corta o PCM decodificado no ponto exato pedido. Os frames vêm em blocos de 20ms e o seek
     * cai onde o container deixa, então o buffer cru começa antes e termina depois do alvo; o
     * timecode do primeiro frame diz onde ele realmente começou.
     */
    private static byte[] trim(byte[] data, long firstTimecode, long startMs, long endMs, String identifier)
    {
        if (firstTimecode < 0 || data.length == 0)
            throw new IllegalStateException("Nada foi decodificado de `" + identifier + "`.");

        if (firstTimecode > startMs)
            LOGGER.warn("O seek de `{}` caiu em {}ms, depois do início pedido ({}ms); "
                    + "o segmento começa {}ms adiantado.",
                    identifier, firstTimecode, startMs, firstTimecode - startMs);

        int begin = clampOffset((startMs - firstTimecode) * BYTES_PER_MS, data.length);
        // UNTIL_END: fica tudo que veio até a faixa acabar (a conta em ms estouraria o long)
        int end = endMs == UNTIL_END ? clampOffset(data.length, data.length)
                : clampOffset((endMs - firstTimecode) * BYTES_PER_MS, data.length);

        if (end - begin < SegmentPlayer.FRAME_BYTES)
            throw new IllegalStateException("O segmento pedido de `" + identifier + "` saiu vazio "
                    + "(a faixa acaba antes de " + endMs + "ms?).");

        return Arrays.copyOfRange(data, begin, end);
    }

    /** Prende o offset ao buffer e alinha no par de amostras L/R (4 bytes). */
    private static int clampOffset(long offset, int length)
    {
        long clamped = Math.max(0, Math.min(offset, length));
        return (int) (clamped - (clamped % 4));
    }

    private AudioTrack resolve(String identifier)
    {
        AudioItem item;
        try
        {
            item = manager.loadItemSync(identifier);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Não consegui carregar `" + identifier + "`: " + e.getMessage());
        }

        if (item instanceof AudioTrack)
            return (AudioTrack) item;

        if (item instanceof AudioPlaylist)
        {
            List<AudioTrack> tracks = ((AudioPlaylist) item).getTracks();
            if (!tracks.isEmpty())
                return tracks.get(0);
        }

        throw new IllegalStateException("Nada encontrado para `" + identifier + "`.");
    }
}
