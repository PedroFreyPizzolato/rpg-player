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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Toca um segmento em loop a partir do PCM em memória, com crossfade amostra a amostra.
 *
 * <p>Existem dois tipos de emenda, e a diferença importa:
 * <ul>
 *   <li><b>Loop</b> (fim do trecho → início dele de novo): o áudio <i>não</i> é contíguo, então
 *       leva crossfade de potência constante ({@code cos}/{@code sin}). Ganho linear afundaria
 *       ~3dB no meio, porque os dois lados são sinais diferentes somados.</li>
 *   <li><b>Avanço</b> (liberou a próxima fase): a música precisa <i>seguir tocando</i> o trecho
 *       entre o fim de uma fase e o começo da outra. Esse áudio é contíguo, então a emenda é
 *       seca, no sample exato — crossfade aqui só produziria eco de dois trechos diferentes.</li>
 * </ul>
 *
 * <p>Por isso o avanço decodifica {@code [fim da fase atual, fim da próxima]} em vez de só a
 * próxima fase: o miolo é a passagem que o mestre quer ouvir, e o loop volta a acontecer
 * apenas a partir de {@link Segment#loopStart} (o início real da nova fase).
 *
 * <p>Os frames são pedidos pela thread de áudio do JDA; só {@link #unlockNext()} vem de fora.
 */
public class SegmentPlayer
{
    /** 20ms de PCM s16be 48kHz estéreo: o que o JDA pede por pacote. */
    public static final int FRAME_BYTES = 3840;

    /** De onde o player tira PCM. Interface (e não a classe) para o teste injetar trechos prontos. */
    public interface SegmentSource
    {
        CompletableFuture<byte[]> fetch(String identifier, long startMs, long endMs);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SegmentPlayer.class);

    private final SegmentSource source;
    private final PhaseConfig.Track track;
    private final IntSupplier volume;
    /** Vale para as fases que não definem um crossfade próprio. */
    private final int defaultFadeMs;
    /** Avisa quando a tela precisa ser redesenhada: fase nova, ou fim de uma passagem. */
    private final Consumer<String> onPhaseChange;
    /** Avisa que a música acabou (o trecho final tocou até o fim do arquivo). */
    private final Runnable onFinish;

    private Segment current;
    /** Para onde vamos quando o trecho atual acabar; decidido ao entrar na zona de transição. */
    private Segment upcoming;
    /** true = emenda seca (áudio contíguo); false = crossfade. */
    private boolean upcomingJoinsDry;
    private int position;
    private int fadeBytes;

    private volatile int phaseIndex;
    private volatile boolean unlocked;
    private volatile boolean paused;
    private volatile CompletableFuture<Segment> prefetch;
    private volatile boolean finished;

    /** Começa no início da fase, com o trecho já decodificado sendo a fase inteira. */
    public SegmentPlayer(SegmentSource source, PhaseConfig.Track track, int phaseIndex,
                         byte[] firstSegment, int fadeMs, IntSupplier volume,
                         Consumer<String> onPhaseChange, Runnable onFinish)
    {
        this(source, track, phaseIndex, firstSegment, 0, track.phases.get(phaseIndex).startMs(), 0,
                fadeMs, volume, onPhaseChange, onFinish);
    }

    /**
     * Entra no modo fase no meio da música, sem cortar o que já estava tocando: {@code data} é
     * o PCM a partir de {@code dataStartMs}, e a reprodução começa em {@code enterAtMs} dentro
     * dele.
     *
     * <p>Se {@code data} começa antes da fase (o mestre trocou de modo durante a passagem entre
     * duas fases), esse miolo toca uma vez e o loop passa a valer só a partir do início da fase
     * — mesma regra da passagem quando se libera a fase seguinte.
     */
    public static SegmentPlayer resumingAt(SegmentSource source, PhaseConfig.Track track, int phaseIndex,
                                           byte[] data, long dataStartMs, long enterAtMs, int fadeMs,
                                           IntSupplier volume, Consumer<String> onPhaseChange,
                                           Runnable onFinish)
    {
        int loopStart = bytesInto(data, dataStartMs, track.phases.get(phaseIndex).startMs());
        int enterAt = bytesInto(data, dataStartMs, enterAtMs);
        return new SegmentPlayer(source, track, phaseIndex, data, loopStart, dataStartMs, enterAt,
                fadeMs, volume, onPhaseChange, onFinish);
    }

    /** Offset de {@code targetMs} dentro do trecho, sempre com pelo menos um frame pela frente. */
    private static int bytesInto(byte[] data, long dataStartMs, long targetMs)
    {
        long offset = Math.max(0, (targetMs - dataStartMs) * SegmentCapture.BYTES_PER_MS);
        return align4((int) Math.min(offset, Math.max(0, data.length - FRAME_BYTES)));
    }

    private SegmentPlayer(SegmentSource source, PhaseConfig.Track track, int phaseIndex,
                          byte[] firstSegment, int firstLoopStart, long firstStartMs, int startOffset,
                          int defaultFadeMs, IntSupplier volume,
                          Consumer<String> onPhaseChange, Runnable onFinish)
    {
        this.source = source;
        this.track = track;
        this.phaseIndex = phaseIndex;
        this.defaultFadeMs = defaultFadeMs;
        this.volume = volume;
        this.onPhaseChange = onPhaseChange;
        this.onFinish = onFinish;
        setCurrent(new Segment(firstSegment, firstLoopStart, false, firstStartMs));
        this.position = startOffset;
        startPrefetch();
    }

    public boolean canProvide()
    {
        return current != null && !paused;
    }

    public boolean isPaused()
    {
        return paused;
    }

    public void setPaused(boolean paused)
    {
        this.paused = paused;
    }

    public ByteBuffer provide20MsAudio()
    {
        Segment segment = current;
        if (segment == null)
            return null;

        boolean wasBridging = isBridging();

        if (position >= segment.data.length)
        {
            int carry = position - segment.data.length;
            if (upcoming != null)
            {
                // na emenda seca o frame anterior já consumiu 'carry' bytes do buffer novo;
                // no crossfade ele já adiantou fadeBytes a partir do ponto de loop
                position = upcomingJoinsDry ? carry : upcoming.loopStart + fadeBytes + carry;
                setCurrent(upcoming);
            }
            else if (segment.isTerminal())
            {
                finish();
                return null;
            }
            else
            {
                position = segment.loopStart + fadeBytes + carry;
            }
            segment = current;
            position = Math.min(position, segment.data.length);
        }

        // a janela para decidir o que vem depois nunca é menor que um frame: o fade dita a
        // mistura do áudio, não o prazo da decisão, e com fade 0 ela teria 4 bytes — a
        // reprodução, que anda de frame em frame, passaria por cima e a fase nunca avançaria
        if (upcoming == null && !segment.isTerminal()
                && segment.data.length - position <= Math.max(fadeBytes, FRAME_BYTES))
        {
            Segment next = chooseNext();
            upcoming = next;
            upcomingJoinsDry = next != segment && next.continuesPrevious;
        }

        byte[] data = segment.data;
        Segment target = upcoming != null ? upcoming : segment;
        byte[] targetData = target.data;
        int targetLoop = Math.max(0, target.loopStart);

        byte[] out = new byte[FRAME_BYTES];
        double gain = Math.max(0, volume.getAsInt()) / 100.0;

        for (int i = 0; i < FRAME_BYTES; i += 2)
        {
            int remaining = data.length - (position + i);
            double value;
            if (upcomingJoinsDry)
            {
                // continuação contígua: nada de mistura, o áudio só segue no buffer novo
                value = remaining > 0 ? sample(data, position + i) : sample(targetData, -remaining);
            }
            // o trecho terminal (o final da música) não volta pra lugar nenhum: cruzar aqui
            // misturaria o fim da música com o começo do próprio trecho final
            else if (remaining > fadeBytes || segment.isTerminal())
            {
                value = sample(data, position + i);
            }
            else
            {
                double progress = Math.min(1.0, Math.max(0.0, (double) (fadeBytes - remaining) / fadeBytes));
                value = sample(data, position + i) * Math.cos(progress * Math.PI / 2)
                      + sample(targetData, targetLoop + fadeBytes - remaining) * Math.sin(progress * Math.PI / 2);
            }
            putSample(out, i, value * gain);
        }

        position += FRAME_BYTES;

        // a passagem acabou de virar loop da fase (às vezes sem trocar de índice, como a intro
        // antes da fase 1): nada mais dispara isto sozinho, e sem avisar a tela fica presa
        // mostrando o botão "Liberar fase" como se já estivesse liberado
        if (wasBridging && !isBridging() && onPhaseChange != null)
            onPhaseChange.accept(getPhaseName());

        return ByteBuffer.wrap(out);
    }

    /**
     * Libera a continuação: ao fim do trecho atual, em vez de loopar, a música segue tocando
     * até a próxima fase (ou até o fim do arquivo, se já estamos na última).
     */
    public boolean unlockNext()
    {
        unlocked = true;
        return true;
    }

    public boolean isOnLastPhase()
    {
        return phaseIndex + 1 >= track.phases.size();
    }

    /**
     * Onde a reprodução está dentro do arquivo, em ms. É a posição real na música — durante a
     * passagem entre duas fases ela cai fora dos limites da fase atual, que é justamente o
     * ponto de tocar o miolo.
     */
    public long getPositionMs()
    {
        Segment segment = current;
        if (segment == null)
            return 0;
        return segment.startMs + (long) (Math.min(position, segment.data.length) / SegmentCapture.BYTES_PER_MS);
    }

    /** true enquanto o trecho tocando é a passagem/final, e não o corpo da fase em loop. */
    public boolean isBridging()
    {
        Segment segment = current;
        if (segment == null)
            return false;
        return segment.isTerminal() || position < segment.loopStart;
    }

    public PhaseConfig.Track getTrack()
    {
        return track;
    }

    public int getPhaseIndex()
    {
        return phaseIndex;
    }

    public String getPhaseName()
    {
        return track.phases.get(phaseIndex).name;
    }

    public boolean isUnlocked()
    {
        return unlocked;
    }

    private void setCurrent(Segment segment)
    {
        current = segment;
        upcoming = null;
        upcomingJoinsDry = false;
        // cada fase tem o seu crossfade; quem manda é a fase que está entrando, e o phaseIndex
        // já foi avançado por chooseNext() quando este trecho é o da fase seguinte
        int fadeMs = track.phases.get(phaseIndex).fadeMs(defaultFadeMs);
        // o fade cabe na região que loopa, não no trecho inteiro (a ponte tem um miolo que
        // só toca uma vez); nunca zero, senão o progresso do fade divide por zero
        int loopRegion = segment.data.length - Math.max(0, segment.loopStart);
        fadeBytes = Math.max(4, align4(Math.min(fadeMs * SegmentCapture.BYTES_PER_MS, loopRegion / 2)));
    }

    /**
     * O que tocar quando o trecho atual terminar. Roda na thread de áudio, então nunca
     * bloqueia: se a decodificação ainda não chegou, loopa a fase atual mais uma vez.
     */
    private Segment chooseNext()
    {
        if (!unlocked)
            return current;

        CompletableFuture<Segment> pending = prefetch;
        if (pending == null)
            return isOnLastPhase() ? Segment.END : current;
        if (!pending.isDone())
            return current;

        prefetch = null;
        Segment ready;
        try
        {
            ready = pending.get();
        }
        catch (Exception e)
        {
            LOGGER.error("Falha ao decodificar a continuação de '{}'", track.name, e);
            return isOnLastPhase() ? Segment.END : current;
        }

        unlocked = false;
        if (!isOnLastPhase())
        {
            phaseIndex++;
            startPrefetch();
            if (onPhaseChange != null)
                onPhaseChange.accept(getPhaseName());
        }
        return ready;
    }

    private void startPrefetch()
    {
        PhaseConfig.Phase from = track.phases.get(phaseIndex);

        if (isOnLastPhase())
        {
            // depois da última fase vem o resto do arquivo, tocado uma vez e encerrando
            prefetch = fetch(from.endMs(), SegmentCapture.UNTIL_END, Segment.NO_LOOP, true);
            return;
        }

        PhaseConfig.Phase to = track.phases.get(phaseIndex + 1);
        // se a próxima fase começa depois desta acabar, o miolo entre elas é a passagem que
        // deve ser tocada; se ela começa antes (fases fora de ordem), não há o que emendar
        boolean contiguous = to.startMs() >= from.endMs();
        long captureStart = contiguous ? from.endMs() : to.startMs();
        int loopStart = align4((int) ((to.startMs() - captureStart) * SegmentCapture.BYTES_PER_MS));
        prefetch = fetch(captureStart, to.endMs(), loopStart, contiguous);
    }

    private CompletableFuture<Segment> fetch(long startMs, long endMs, int loopStart, boolean continuesPrevious)
    {
        return source.fetch(track.identifier(), startMs, endMs)
                .thenApply(data -> new Segment(data, loopStart, continuesPrevious, startMs));
    }

    private void finish()
    {
        if (finished)
            return;
        finished = true;
        current = null;
        LOGGER.info("Modo fase terminou naturalmente: faixa=\"{}\"", track.name);
        if (onFinish != null)
            onFinish.run();
    }

    /** Amostra s16 big-endian; fora do buffer devolve silêncio. */
    private static int sample(byte[] buffer, int index)
    {
        if (buffer == null || index < 0 || index + 1 >= buffer.length)
            return 0;
        return (short) ((buffer[index] << 8) | (buffer[index + 1] & 0xFF));
    }

    private static void putSample(byte[] buffer, int index, double value)
    {
        int rounded = (int) Math.round(value);
        if (rounded > Short.MAX_VALUE)
            rounded = Short.MAX_VALUE;
        else if (rounded < Short.MIN_VALUE)
            rounded = Short.MIN_VALUE;
        buffer[index] = (byte) (rounded >> 8);
        buffer[index + 1] = (byte) rounded;
    }

    /** Mantém os offsets no mesmo canal (L/R) dos dois buffers durante o crossfade. */
    private static int align4(int value)
    {
        return value - (value % 4);
    }

    /**
     * PCM já decodificado e o ponto para onde ele volta ao loopar.
     */
    private static class Segment
    {
        /** {@link #loopStart} assim: o trecho toca uma vez e a reprodução acaba. */
        static final int NO_LOOP = -1;

        /** Trecho vazio e terminal: usado quando não há nada depois da última fase. */
        static final Segment END = new Segment(new byte[0], NO_LOOP, true, 0);

        final byte[] data;
        /** Offset de retorno no loop, ou {@link #NO_LOOP}. */
        final int loopStart;
        /** O áudio começa exatamente onde o trecho anterior terminou (emenda seca, sem fade). */
        final boolean continuesPrevious;
        /** Onde este trecho começa dentro do arquivo, para saber a posição real na música. */
        final long startMs;

        Segment(byte[] data, int loopStart, boolean continuesPrevious, long startMs)
        {
            this.data = data;
            this.loopStart = loopStart;
            this.continuesPrevious = continuesPrevious;
            this.startMs = startMs;
        }

        boolean isTerminal()
        {
            return loopStart < 0;
        }
    }
}
