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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O loop, o crossfade e a emenda de continuação do {@link SegmentPlayer} são a única lógica
 * que erra em silêncio: um offset trocado não estoura exceção, só produz ruído, eco, ou pula
 * um pedaço da música. Estes testes montam trechos com valores conhecidos e conferem as
 * amostras que saem.
 *
 * <p>Escala: 1ms de PCM = 192 bytes, 1 frame de 20ms = 3840 bytes.
 */
class SegmentPlayerTest
{
    private static final int FRAME = SegmentPlayer.FRAME_BYTES;
    private static final int FADE_MS = 20;                 // = 1 frame = 3840 bytes
    private static final short MIDDLE = 1000;
    private static final short TAIL = 8000;
    private static final short GAP = 3000;
    private static final short PHASE_B = 6000;

    /** Devolve trechos prontos e anota o que foi pedido, para conferir o intervalo capturado. */
    private static class FakeSource implements SegmentPlayer.SegmentSource
    {
        final List<long[]> requests = new ArrayList<>();
        byte[] response = new byte[FRAME];

        @Override
        public CompletableFuture<byte[]> fetch(String identifier, long startMs, long endMs)
        {
            requests.add(new long[] { startMs, endMs });
            return CompletableFuture.completedFuture(response);
        }
    }

    /** Sem fade próprio: a fase segue o padrão passado ao player. */
    private static PhaseConfig.Phase phase(String name, double start, double end)
    {
        PhaseConfig.Phase phase = new PhaseConfig.Phase();
        phase.name = name;
        phase.start = start;
        phase.end = end;
        return phase;
    }

    private static PhaseConfig.Phase phase(String name, double start, double end, double fade)
    {
        PhaseConfig.Phase phase = phase(name, start, end);
        phase.fade = fade;
        return phase;
    }

    private static PhaseConfig.Track track(List<PhaseConfig.Phase> phases)
    {
        PhaseConfig.Track track = new PhaseConfig.Track();
        track.name = "teste";
        track.source = "teste";
        track.phases = phases;
        return track;
    }

    /** Fase única de 4 frames: silêncio | MIDDLE | MIDDLE | TAIL (o último é a zona de fade). */
    private static SegmentPlayer newLoopingPlayer(FakeSource source)
    {
        byte[] segment = new byte[FRAME * 4];
        fill(segment, FRAME, FRAME * 3, MIDDLE);
        fill(segment, FRAME * 3, FRAME * 4, TAIL);
        return new SegmentPlayer(source, track(Collections.singletonList(phase("unica", 0, 0.08))),
                0, segment, FADE_MS, () -> 100, null, null);
    }

    // ── loop e crossfade ─────────────────────────────────────────────────────

    @Test
    @DisplayName("fora da zona de fade o PCM sai idêntico ao que entrou")
    void passesThroughOutsideFade()
    {
        SegmentPlayer player = newLoopingPlayer(new FakeSource());

        assertEquals(0, sampleAt(player.provide20MsAudio(), 0), "frame 0 é silêncio");
        assertEquals(MIDDLE, sampleAt(player.provide20MsAudio(), 0), "frame 1 é o miolo");
        assertEquals(MIDDLE, sampleAt(player.provide20MsAudio(), 1919), "frame 2 até a última amostra");
    }

    @Test
    @DisplayName("o crossfade do loop segue a curva de potência constante")
    void crossfadeFollowsEqualPowerCurve()
    {
        SegmentPlayer player = newLoopingPlayer(new FakeSource());
        for (int i = 0; i < 3; i++)
            player.provide20MsAudio();

        ByteBuffer fade = player.provide20MsAudio();

        assertEquals(TAIL, sampleAt(fade, 0), 1, "início do fade: só o trecho que sai");
        assertEquals(TAIL * Math.cos(Math.PI / 4), sampleAt(fade, 960), 1, "metade: -3dB");
        assertTrue(sampleAt(fade, 1919) < TAIL * 0.01, "fim do fade: praticamente zerado");
    }

    @Test
    @DisplayName("no fim do trecho volta pro início sem repetir o que já foi misturado")
    void loopsBackSkippingTheOverlap()
    {
        SegmentPlayer player = newLoopingPlayer(new FakeSource());
        for (int i = 0; i < 4; i++)
            player.provide20MsAudio();

        assertEquals(MIDDLE, sampleAt(player.provide20MsAudio(), 0), "reinicia depois do overlap");
    }

    @Test
    @DisplayName("o volume do guild é aplicado no PCM")
    void appliesGuildVolume()
    {
        byte[] segment = new byte[FRAME * 4];
        fill(segment, 0, segment.length, MIDDLE);
        SegmentPlayer player = new SegmentPlayer(new FakeSource(),
                track(Collections.singletonList(phase("unica", 0, 0.08))),
                0, segment, FADE_MS, () -> 50, null, null);

        assertEquals(MIDDLE / 2, sampleAt(player.provide20MsAudio(), 0), 1);
    }

    // ── continuação entre fases ──────────────────────────────────────────────

    /**
     * Fase A = [0, 80ms] (4 frames), fase B = [120ms, 240ms]. Entre elas há um vão de 40ms
     * que a música precisa tocar. A ponte é [80ms, 240ms] = 8 frames, com o loop voltando ao
     * offset de 120ms (= 2 frames dentro dela).
     */
    private static SegmentPlayer newAdvancingPlayer(FakeSource source)
    {
        byte[] bridge = new byte[FRAME * 8];
        fill(bridge, 0, FRAME * 2, GAP);                   // o vão entre as fases
        fill(bridge, FRAME * 2, FRAME * 8, PHASE_B);       // a fase B propriamente dita
        source.response = bridge;

        byte[] segment = new byte[FRAME * 4];
        fill(segment, 0, segment.length, MIDDLE);
        return new SegmentPlayer(source,
                track(Arrays.asList(phase("A", 0, 0.08), phase("B", 0.12, 0.24))),
                0, segment, FADE_MS, () -> 100, null, null);
    }

    @Test
    @DisplayName("o trecho pré-carregado começa no fim da fase atual, não no início da próxima")
    void prefetchesTheBridgeNotJustTheNextPhase()
    {
        FakeSource source = new FakeSource();
        newAdvancingPlayer(source);

        assertEquals(1, source.requests.size());
        assertArrayEquals(new long[] { 80, 240 }, source.requests.get(0),
                "tem que capturar a partir do fim da fase A (80ms), senão o vão some");
    }

    @Test
    @DisplayName("a emenda da continuação é seca: nada de fade no fim da fase liberada")
    void advanceJoinsWithoutCrossfade()
    {
        FakeSource source = new FakeSource();
        SegmentPlayer player = newAdvancingPlayer(source);
        player.unlockNext();

        for (int i = 0; i < 3; i++)
            assertEquals(MIDDLE, sampleAt(player.provide20MsAudio(), 0), "frames 0-2 da fase A");

        // frame 3 é a zona de fade — mas como a emenda é contígua, sai sem atenuação nenhuma
        ByteBuffer lastOfPhaseA = player.provide20MsAudio();
        assertEquals(MIDDLE, sampleAt(lastOfPhaseA, 0), "sem fade no começo do frame");
        assertEquals(MIDDLE, sampleAt(lastOfPhaseA, 1919), "sem fade no fim do frame");

        assertEquals(GAP, sampleAt(player.provide20MsAudio(), 0),
                "logo depois vem o vão entre as fases, que antes era pulado");
    }

    @Test
    @DisplayName("depois da ponte o loop pega só a fase nova, não o vão de novo")
    void loopsTheNewPhaseWithoutReplayingTheGap()
    {
        FakeSource source = new FakeSource();
        SegmentPlayer player = newAdvancingPlayer(source);
        player.unlockNext();

        for (int i = 0; i < 4; i++)                        // fase A inteira
            player.provide20MsAudio();
        for (int i = 0; i < 8; i++)                        // ponte inteira (vão + fase B)
            player.provide20MsAudio();

        assertEquals(PHASE_B, sampleAt(player.provide20MsAudio(), 0),
                "o loop tem que voltar pro início da fase B, não pro início do vão");
    }

    @Test
    @DisplayName("fade 0 é corte seco, e não uma fase que nunca avança")
    void zeroFadeStillAdvances()
    {
        FakeSource source = new FakeSource();
        byte[] bridge = new byte[FRAME * 8];
        fill(bridge, 0, FRAME * 2, GAP);
        fill(bridge, FRAME * 2, FRAME * 8, PHASE_B);
        source.response = bridge;

        byte[] segment = new byte[FRAME * 4];
        fill(segment, 0, segment.length, MIDDLE);
        SegmentPlayer player = new SegmentPlayer(source,
                track(Arrays.asList(phase("A", 0, 0.08, 0), phase("B", 0.12, 0.24))),
                0, segment, FADE_MS, () -> 100, null, null);
        player.unlockNext();

        for (int i = 0; i < 4; i++)                        // fase A inteira
            assertEquals(MIDDLE, sampleAt(player.provide20MsAudio(), 0), "frame " + i + " da fase A");

        assertEquals(GAP, sampleAt(player.provide20MsAudio(), 0),
                "com fade 0 a janela de decisão tinha 4 bytes e a reprodução, que anda de "
                + FRAME + " em " + FRAME + ", passava por cima dela: a fase loopava pra sempre");
    }

    // ── fim da música ────────────────────────────────────────────────────────

    @Test
    @DisplayName("na última fase o pré-carregado é o resto do arquivo")
    void prefetchesTheOutroOnTheLastPhase()
    {
        FakeSource source = new FakeSource();
        newLoopingPlayer(source);

        assertEquals(1, source.requests.size());
        assertArrayEquals(new long[] { 80, SegmentCapture.UNTIL_END }, source.requests.get(0),
                "a última fase continua até o fim do arquivo");
    }

    @Test
    @DisplayName("liberar na última fase toca o final e encerra")
    void playsTheOutroThenFinishes()
    {
        FakeSource source = new FakeSource();
        byte[] outro = new byte[FRAME * 2];
        fill(outro, 0, outro.length, TAIL);
        source.response = outro;

        AtomicBoolean finished = new AtomicBoolean();
        byte[] segment = new byte[FRAME * 4];
        fill(segment, 0, segment.length, MIDDLE);
        SegmentPlayer player = new SegmentPlayer(source,
                track(Collections.singletonList(phase("unica", 0, 0.08))),
                0, segment, FADE_MS, () -> 100, null, () -> finished.set(true));

        player.unlockNext();
        for (int i = 0; i < 4; i++)                        // a fase toda
            player.provide20MsAudio();

        assertEquals(TAIL, sampleAt(player.provide20MsAudio(), 0), "entra no final da música");
        player.provide20MsAudio();                         // segundo (e último) frame do final

        assertFalse(finished.get(), "ainda não acabou: o final tem 2 frames");
        assertEquals(null, player.provide20MsAudio(), "acabou o PCM");
        assertTrue(finished.get(), "avisa que a música terminou");
        assertFalse(player.canProvide(), "e para de entregar frames");
    }

    // ── fade por fase ────────────────────────────────────────────────────────
    //
    // O crossfade deixou de ser um número só do bot: cada fase pode ter o seu, e quem manda no
    // trecho é a fase que está entrando. Errar isso não estoura nada — só aplica o fade errado.

    @Test
    @DisplayName("o fade da fase vence o padrão do player")
    void perPhaseFadeOverridesTheDefault()
    {
        byte[] segment = new byte[FRAME * 4];
        fill(segment, FRAME, FRAME * 3, MIDDLE);
        fill(segment, FRAME * 3, FRAME * 4, TAIL);

        SegmentPlayer player = new SegmentPlayer(new FakeSource(),
                track(Collections.singletonList(phase("unica", 0, 0.08, 0.0))),
                0, segment, FADE_MS, () -> 100, null, null);

        for (int i = 0; i < 3; i++)
            player.provide20MsAudio();

        // com o fade padrão (FADE_MS) esta amostra já estaria quase muda — ver crossfadeFollowsEqualPowerCurve
        assertEquals(TAIL, sampleAt(player.provide20MsAudio(), 1900),
                "fade 0: a cauda da fase sai inteira, sem atenuação");
    }

    @Test
    @DisplayName("ao avançar de fase, vale o fade da fase que entrou")
    void advancingUsesTheFadeOfThePhaseBeingEntered()
    {
        FakeSource source = new FakeSource();
        byte[] bridge = new byte[FRAME * 8];
        fill(bridge, 0, FRAME * 2, GAP);
        fill(bridge, FRAME * 2, FRAME * 8, PHASE_B);
        source.response = bridge;

        byte[] segment = new byte[FRAME * 4];
        fill(segment, 0, segment.length, MIDDLE);
        SegmentPlayer player = new SegmentPlayer(source,
                track(Arrays.asList(phase("A", 0, 0.08), phase("B", 0.12, 0.24, 0.0))),
                0, segment, FADE_MS, () -> 100, null, null);

        player.unlockNext();
        for (int i = 0; i < 4; i++)                        // fase A inteira
            player.provide20MsAudio();
        for (int i = 0; i < 7; i++)                        // ponte, menos o último frame
            player.provide20MsAudio();

        assertEquals(PHASE_B, sampleAt(player.provide20MsAudio(), 1900),
                "a fase B pediu fade 0, mesmo o player tendo outro padrão");
    }

    // ── entrar no meio da música (trocar de modo sem cortar o áudio) ─────────

    @Test
    @DisplayName("entra no ponto pedido da fase, não no começo dela")
    void resumesInsideThePhase()
    {
        // 4 frames com valores distintos, pra saber exatamente em qual deles a entrada caiu
        byte[] segment = new byte[FRAME * 4];
        fill(segment, 0, FRAME, (short) 100);
        fill(segment, FRAME, FRAME * 2, (short) 200);
        fill(segment, FRAME * 2, FRAME * 3, (short) 300);
        fill(segment, FRAME * 3, FRAME * 4, (short) 400);

        SegmentPlayer player = SegmentPlayer.resumingAt(new FakeSource(),
                track(Collections.singletonList(phase("unica", 0, 0.08))), 0,
                segment, 0, 40, FADE_MS, () -> 100, null, null);

        assertEquals(40, player.getPositionMs(), "a posição na música é a de onde entrou");
        assertEquals(300, sampleAt(player.provide20MsAudio(), 0),
                "40ms dentro do trecho é o terceiro frame");
    }

    @Test
    @DisplayName("entrando no vão, ele toca uma vez e o loop pega só a fase")
    void resumesInsideTheGapThenLoopsOnlyThePhase()
    {
        // trecho capturado = [20ms, 100ms]: 2 frames de vão + a fase B, que é [60ms, 100ms]
        byte[] data = new byte[FRAME * 4];
        fill(data, 0, FRAME * 2, GAP);
        fill(data, FRAME * 2, FRAME * 4, PHASE_B);

        SegmentPlayer player = SegmentPlayer.resumingAt(new FakeSource(),
                track(Arrays.asList(phase("A", 0, 0.02), phase("B", 0.06, 0.10))), 1,
                data, 20, 20, FADE_MS, () -> 100, null, null);

        assertEquals(GAP, sampleAt(player.provide20MsAudio(), 0), "começa no vão");
        player.provide20MsAudio();                         // segundo frame do vão
        assertEquals(PHASE_B, sampleAt(player.provide20MsAudio(), 0), "emenda na fase B");
        player.provide20MsAudio();                         // último frame da fase B

        assertEquals(PHASE_B, sampleAt(player.provide20MsAudio(), 0),
                "ao loopar volta pra fase B, não pro vão que já passou");
    }

    @Test
    @DisplayName("saindo da passagem pro loop da fase, avisa pra redesenhar a tela")
    void bridgingEndingTriggersARefresh()
    {
        // mesmo desenho do teste acima: vão de 2 frames antes da fase, sem trocar de índice
        byte[] data = new byte[FRAME * 4];
        fill(data, 0, FRAME * 2, GAP);
        fill(data, FRAME * 2, FRAME * 4, PHASE_B);

        List<String> refreshes = new ArrayList<>();
        SegmentPlayer player = SegmentPlayer.resumingAt(new FakeSource(),
                track(Collections.singletonList(phase("unica", 0.04, 0.08))), 0,
                data, 0, 0, FADE_MS, () -> 100, refreshes::add, null);

        assertTrue(player.isBridging(), "começa na passagem (intro)");
        player.provide20MsAudio();                         // primeiro frame do vão
        assertTrue(refreshes.isEmpty(), "ainda na passagem, nada mudou pra avisar");

        player.provide20MsAudio();                         // cruza pro início da fase
        assertFalse(player.isBridging(), "a fase começou a valer");
        assertEquals(1, refreshes.size(),
                "sem isso o botão 'Liberar fase' fica preso mostrando o estado antigo (desabilitado)");

        player.provide20MsAudio();
        assertEquals(1, refreshes.size(), "não avisa de novo enquanto segue dentro da fase");
    }

    // ── pausa ────────────────────────────────────────────────────────────────
    //
    // O AudioHandler só entrega frame quando canProvide() diz que pode; se pausar não desligar
    // isso, a música ignora o pause e só segue tocando (foi exatamente o que aconteceu: nada
    // aqui checava isPaused() no caminho que o JDA de fato usa).

    @Test
    @DisplayName("pausado, não entrega mais frame — e volta a entregar ao despausar")
    void pausingStopsProvidingFrames()
    {
        SegmentPlayer player = newLoopingPlayer(new FakeSource());
        assertTrue(player.canProvide(), "toca normalmente antes de pausar");

        player.setPaused(true);
        assertFalse(player.canProvide(), "pausado não deve ter frame pra entregar");

        player.setPaused(false);
        assertTrue(player.canProvide(), "despausado volta a entregar");
    }

    /** Escreve um valor s16 big-endian em todas as amostras do intervalo. */
    private static void fill(byte[] buffer, int from, int to, short value)
    {
        for (int i = from; i < to; i += 2)
        {
            buffer[i] = (byte) (value >> 8);
            buffer[i + 1] = (byte) value;
        }
    }

    /** Lê a amostra de índice n (não byte) do frame produzido. */
    private static int sampleAt(ByteBuffer frame, int index)
    {
        byte[] data = frame.array();
        return (short) ((data[index * 2] << 8) | (data[index * 2 + 1] & 0xFF));
    }
}
