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

import com.jagrosh.jmusicbot.audio.PhaseConfig;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackState;
import com.sedmelluq.discord.lavaplayer.track.TrackMarker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A edição das segmentações grava um arquivo que o bot relê a cada comando. Um erro de validação
 * ou de ordenação aqui não estoura exceção — só produz uma segmentação silenciosamente quebrada
 * (fase invertida, passagem calculada errado, fade que não cabe na fase).
 *
 * <p>O {@link PhaseService} escreve em {@code phases.json} relativo ao diretório de trabalho,
 * então os testes rodam num diretório temporário.
 */
class PhaseEditingTest
{
    private Path workdir;
    private Path original;
    private PhaseService service;

    @BeforeEach
    void setUp() throws IOException
    {
        original = Paths.get("").toAbsolutePath();
        workdir = Files.createTempDirectory("phase-editing-test");
        System.setProperty("user.dir", workdir.toString());
        // PhaseConfig usa Paths.get("phases.json"), que resolve por user.dir só em JVMs novas;
        // para não depender disso, os testes escrevem e leem pelo caminho corrente do processo
        Files.deleteIfExists(Paths.get(PhaseConfig.FILE_NAME));
        service = new PhaseService(null);
    }

    @AfterEach
    void tearDown() throws IOException
    {
        Files.deleteIfExists(Paths.get(PhaseConfig.FILE_NAME));
        System.setProperty("user.dir", original.toString());
    }

    private void givenTrack(String name) throws IOException
    {
        assertNull(service.saveTrack(null, name, "https://example.com/x"));
    }

    @Test
    @DisplayName("cria a faixa e grava o arquivo do zero")
    void createsTrackAndFile() throws IOException
    {
        givenTrack("Batalha");

        PhaseConfig config = PhaseConfig.load();
        assertEquals(1, config.tracks.size());
        assertEquals("Batalha", config.tracks.get(0).name);
        assertEquals("https://example.com/x", config.tracks.get(0).identifier());
    }

    @Test
    @DisplayName("as fases ficam ordenadas por início, mesmo cadastradas fora de ordem")
    void keepsPhasesSorted() throws IOException
    {
        givenTrack("Batalha");
        assertNull(service.savePhase("Batalha", -1, "Fim", "120", "180", null));
        assertNull(service.savePhase("Batalha", -1, "Começo", "0", "60", null));

        PhaseConfig.Track track = PhaseConfig.load().tracks.get(0);
        assertEquals("Começo", track.phases.get(0).name,
                "a passagem entre fases assume ordem crescente de início");
        assertEquals("Fim", track.phases.get(1).name);
    }

    @Test
    @DisplayName("recusa fase com fim antes do início")
    void rejectsInvertedPhase() throws IOException
    {
        givenTrack("Batalha");
        String error = service.savePhase("Batalha", -1, "Ruim", "90", "30", null);
        assertNotNull(error);
        assertTrue(error.contains("depois do início"), error);
        assertTrue(PhaseConfig.load().tracks.get(0).phases.isEmpty());
    }

    @Test
    @DisplayName("aceita tempo em mm:ss além de segundos")
    void acceptsClockNotation() throws IOException
    {
        givenTrack("Batalha");
        assertNull(service.savePhase("Batalha", -1, "Refrão", "1:06", "1:58", null));

        PhaseConfig.Phase phase = PhaseConfig.load().tracks.get(0).phases.get(0);
        assertEquals(66.0, phase.start, 0.001);
        assertEquals(118.0, phase.end, 0.001);
    }

    @Test
    @DisplayName("recusa número inválido sem corromper o arquivo")
    void rejectsGarbageNumbers() throws IOException
    {
        givenTrack("Batalha");
        assertNotNull(service.savePhase("Batalha", -1, "X", "abc", "10", null));
        assertTrue(PhaseConfig.load().tracks.get(0).phases.isEmpty());
    }

    @Test
    @DisplayName("editar uma fase existente não cria outra")
    void editsInPlace() throws IOException
    {
        givenTrack("Batalha");
        assertNull(service.savePhase("Batalha", -1, "Intro", "0", "30", null));
        assertNull(service.savePhase("Batalha", 0, "Intro longa", "0", "45", null));

        PhaseConfig.Track track = PhaseConfig.load().tracks.get(0);
        assertEquals(1, track.phases.size());
        assertEquals("Intro longa", track.phases.get(0).name);
        assertEquals(45.0, track.phases.get(0).end, 0.001);
    }

    @Test
    @DisplayName("excluir remove só a fase escolhida")
    void deletesOnlyTheChosenPhase() throws IOException
    {
        givenTrack("Batalha");
        assertNull(service.savePhase("Batalha", -1, "A", "0", "30", null));
        assertNull(service.savePhase("Batalha", -1, "B", "30", "60", null));

        assertNull(service.deletePhase("Batalha", 0));

        PhaseConfig.Track track = PhaseConfig.load().tracks.get(0);
        assertEquals(1, track.phases.size());
        assertEquals("B", track.phases.get(0).name);
    }

    @Test
    @DisplayName("marcar posição ajusta o início da fase escolhida")
    void markAdjustsPhaseStart() throws IOException
    {
        givenTrack("Batalha");
        assertNull(service.savePhase("Batalha", -1, "A", "10", "60", null));

        assertNull(service.applyMark("Batalha", 25_400, "start:0"));

        PhaseConfig.Phase phase = PhaseConfig.load().tracks.get(0).phases.get(0);
        assertEquals(25.4, phase.start, 0.001);
        assertEquals(60.0, phase.end, 0.001);
    }

    @Test
    @DisplayName("marcar recusa ponto que inverteria a fase")
    void markRejectsInvalidPoint() throws IOException
    {
        givenTrack("Batalha");
        assertNull(service.savePhase("Batalha", -1, "A", "10", "60", null));

        String error = service.applyMark("Batalha", 90_000, "start:0");
        assertNotNull(error);
        assertEquals(10.0, PhaseConfig.load().tracks.get(0).phases.get(0).start, 0.001);
    }

    @Test
    @DisplayName("marcar cria fase nova a partir do ponto")
    void markCreatesNewPhase() throws IOException
    {
        givenTrack("Batalha");
        assertNull(service.applyMark("Batalha", 12_000, "new"));

        PhaseConfig.Track track = PhaseConfig.load().tracks.get(0);
        assertEquals(1, track.phases.size());
        assertEquals(12.0, track.phases.get(0).start, 0.001);
    }

    // ── linkCurrentSource ────────────────────────────────────────────────────
    //
    // Vincula uma segunda fonte (ex: mesma música por YouTube Music) à faixa já cadastrada,
    // para que a auto-detecção do painel e a oferta de modo fase no /play reconheçam as duas.

    @Test
    @DisplayName("vincula a fonte tocando agora como alias da faixa")
    void linksCurrentSourceAsAlias() throws IOException
    {
        givenTrack("Batalha");
        assertNull(service.linkCurrentSource("Batalha", fakeTrack("https://music.youtube.com/watch?v=abc", "abc")));

        PhaseConfig.Track track = PhaseConfig.load().tracks.get(0);
        assertEquals(1, track.aliases.size());
        assertEquals("https://music.youtube.com/watch?v=abc", track.aliases.get(0));
    }

    @Test
    @DisplayName("recusa vincular uma fonte já vinculada a outra faixa")
    void refusesToLinkSourceAlreadyOwnedByAnotherTrack() throws IOException
    {
        givenTrack("Batalha");
        assertNull(service.saveTrack(null, "Calma", "https://music.youtube.com/watch?v=abc"));

        String error = service.linkCurrentSource("Batalha", fakeTrack("https://music.youtube.com/watch?v=abc", "abc"));
        assertNotNull(error);
        assertTrue(error.contains("Calma"), error);
        assertTrue(PhaseConfig.load().tracks.get(0).aliases.isEmpty());
    }

    @Test
    @DisplayName("recusa vincular a própria fonte principal da faixa")
    void refusesToLinkTracksOwnPrimarySource() throws IOException
    {
        givenTrack("Batalha");
        String error = service.linkCurrentSource("Batalha", fakeTrack("https://example.com/x", "https://example.com/x"));
        assertNotNull(error);
        assertTrue(PhaseConfig.load().tracks.get(0).aliases.isEmpty());
    }

    // ── fade por fase ────────────────────────────────────────────────────────

    @Test
    @DisplayName("fade vazio fica sem valor no arquivo, para seguir o padrão")
    void blankFadeStaysUnset() throws IOException
    {
        givenTrack("Batalha");
        assertNull(service.savePhase("Batalha", -1, "A", "0", "60", "  "));

        PhaseConfig.Phase phase = PhaseConfig.load().tracks.get(0).phases.get(0);
        assertNull(phase.fade, "sem valor próprio a fase acompanha o padrão do bot");
        assertEquals(PhaseConfig.DEFAULT_FADE_MS, phase.fadeMs(PhaseConfig.DEFAULT_FADE_MS));
    }

    @Test
    @DisplayName("fade próprio é gravado e vence o padrão")
    void ownFadeOverridesTheDefault() throws IOException
    {
        givenTrack("Batalha");
        assertNull(service.savePhase("Batalha", -1, "A", "0", "60", "0.5"));

        PhaseConfig.Phase phase = PhaseConfig.load().tracks.get(0).phases.get(0);
        assertEquals(0.5, phase.fade, 0.001);
        assertEquals(500, phase.fadeMs(PhaseConfig.DEFAULT_FADE_MS));
    }

    @Test
    @DisplayName("fade 0 é corte seco, e não 'sem valor'")
    void zeroFadeIsAHardCut() throws IOException
    {
        givenTrack("Batalha");
        assertNull(service.savePhase("Batalha", -1, "A", "0", "60", "0"));

        PhaseConfig.Phase phase = PhaseConfig.load().tracks.get(0).phases.get(0);
        assertNotNull(phase.fade, "0 é uma escolha, não a ausência de escolha");
        assertEquals(0, phase.fadeMs(PhaseConfig.DEFAULT_FADE_MS));
    }

    @Test
    @DisplayName("recusa fade maior que metade da fase em vez de cortar calado")
    void rejectsFadeLongerThanHalfThePhase() throws IOException
    {
        givenTrack("Batalha");
        // fase de 10s: acima de 5s o crossfade cruzaria a fase consigo mesma
        String error = service.savePhase("Batalha", -1, "A", "0", "10", "6");
        assertNotNull(error);
        assertTrue(error.contains("metade"), error);
        assertTrue(PhaseConfig.load().tracks.get(0).phases.isEmpty());
    }

    @Test
    @DisplayName("recusa fade sem número sem corromper o arquivo")
    void rejectsGarbageFade() throws IOException
    {
        givenTrack("Batalha");
        assertNotNull(service.savePhase("Batalha", -1, "A", "0", "60", "abc"));
        assertTrue(PhaseConfig.load().tracks.get(0).phases.isEmpty());
    }

    @Test
    @DisplayName("editar uma fase pode devolver o fade ao padrão")
    void clearingTheFadeReturnsToDefault() throws IOException
    {
        givenTrack("Batalha");
        assertNull(service.savePhase("Batalha", -1, "A", "0", "60", "0.5"));
        assertNull(service.savePhase("Batalha", 0, "A", "0", "60", ""));

        assertNull(PhaseConfig.load().tracks.get(0).phases.get(0).fade);
    }

    // ── planEntry ────────────────────────────────────────────────────────────
    //
    // Decide em que fase o modo fase entra quando a troca acontece com a música tocando, e que
    // trecho decodificar pra isso. Errar aqui não estoura nada: só faz a música pular, ou o loop
    // ficar preso no pedaço final de uma fase em vez da fase inteira.

    /** A = [10s, 20s], vão de 10s, B = [30s, 40s]. */
    private static PhaseConfig.Track trackWithGap()
    {
        PhaseConfig.Track track = new PhaseConfig.Track();
        track.name = "Batalha";
        track.source = "https://example.com/x";
        track.phases.add(inMemoryPhase("A", 10, 20));
        track.phases.add(inMemoryPhase("B", 30, 40));
        return track;
    }

    private static PhaseConfig.Phase inMemoryPhase(String name, double start, double end)
    {
        PhaseConfig.Phase phase = new PhaseConfig.Phase();
        phase.name = name;
        phase.start = start;
        phase.end = end;
        return phase;
    }

    @Test
    @DisplayName("dentro de uma fase captura a fase inteira, não só o que falta dela")
    void entryInsidePhaseCapturesTheWholePhase()
    {
        PhaseService.PhaseEntry entry = PhaseService.planEntry(trackWithGap(), 15_000);
        assertEquals(0, entry.phaseIndex);
        assertEquals(10_000, entry.captureStartMs, "o loop tem que pegar a fase toda");
        assertEquals(20_000, entry.captureEndMs);
    }

    @Test
    @DisplayName("no vão entre fases captura do ponto atual até o fim da fase seguinte")
    void entryInGapBridgesIntoTheNextPhase()
    {
        PhaseService.PhaseEntry entry = PhaseService.planEntry(trackWithGap(), 25_000);
        assertEquals(1, entry.phaseIndex, "cai na fase seguinte");
        assertEquals(25_000, entry.captureStartMs, "sem cortar: segue de onde a música está");
        assertEquals(40_000, entry.captureEndMs);
    }

    @Test
    @DisplayName("antes da primeira fase toca a intro que falta e cai no loop dela")
    void entryBeforeFirstPhaseKeepsTheIntro()
    {
        PhaseService.PhaseEntry entry = PhaseService.planEntry(trackWithGap(), 3_000);
        assertEquals(0, entry.phaseIndex);
        assertEquals(3_000, entry.captureStartMs);
        assertEquals(20_000, entry.captureEndMs);
    }

    @Test
    @DisplayName("depois da última fase volta para o início dela")
    void entryPastTheLastPhaseFallsBackToIt()
    {
        PhaseService.PhaseEntry entry = PhaseService.planEntry(trackWithGap(), 90_000);
        assertEquals(1, entry.phaseIndex);
        assertEquals(30_000, entry.captureStartMs);
        assertEquals(40_000, entry.captureEndMs);
    }

    /** AudioTrack mínimo: só getInfo()/getIdentifier() importam pro matching. */
    private static AudioTrack fakeTrack(String uri, String identifier)
    {
        AudioTrackInfo info = new AudioTrackInfo("título", "autor", 1000, identifier, false, uri);
        return new AudioTrack()
        {
            public AudioTrackInfo getInfo() { return info; }
            public String getIdentifier() { return identifier; }
            public AudioTrackState getState() { throw new UnsupportedOperationException(); }
            public void stop() { throw new UnsupportedOperationException(); }
            public boolean isSeekable() { throw new UnsupportedOperationException(); }
            public long getPosition() { throw new UnsupportedOperationException(); }
            public void setPosition(long position) { throw new UnsupportedOperationException(); }
            public void setMarker(TrackMarker marker) { throw new UnsupportedOperationException(); }
            public void addMarker(TrackMarker marker) { throw new UnsupportedOperationException(); }
            public void removeMarker(TrackMarker marker) { throw new UnsupportedOperationException(); }
            public long getDuration() { throw new UnsupportedOperationException(); }
            public AudioTrack makeClone() { throw new UnsupportedOperationException(); }
            public AudioSourceManager getSourceManager() { throw new UnsupportedOperationException(); }
            public void setUserData(Object data) { throw new UnsupportedOperationException(); }
            public Object getUserData() { throw new UnsupportedOperationException(); }
            public <T> T getUserData(Class<T> klass) { throw new UnsupportedOperationException(); }
        };
    }
}
