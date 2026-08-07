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
import com.jagrosh.jmusicbot.audio.SegmentCapture;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackState;
import com.sedmelluq.discord.lavaplayer.track.TrackMarker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
    /** O preset que o {@code findOrCreate} dá a toda faixa nova — é nele que estes testes editam. */
    private static final String PRESET = PhaseConfig.LEGACY_PRESET_NAME;

    @TempDir Path dir;
    private String previousUserDir;
    private PhaseService service;

    /**
     * Nada é apagado aqui: {@code @TempDir} já entrega uma pasta vazia por teste, e apagar
     * {@code phases.json} por caminho relativo apagaria o arquivo REAL do bot — {@code Paths.get}
     * resolve pelo diretório fixado na inicialização da JVM e ignora este {@code user.dir}
     * (é o mesmo motivo do {@code PhaseConfig.resolveFile}).
     */
    @BeforeEach
    void setUp()
    {
        previousUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", dir.toString());
        service = new PhaseService(null);
    }

    @AfterEach
    void tearDown()
    {
        System.setProperty("user.dir", previousUserDir);
    }

    private void givenTrack(String name) throws IOException
    {
        assertNull(service.saveTrack(null, name, "https://example.com/x"));
    }

    private void write(String json) throws IOException
    {
        Files.writeString(dir.resolve(PhaseConfig.FILE_NAME), json);
    }

    @Test
    @DisplayName("faixa sem preset nenhum responde com erro em vez de derrubar a interação")
    void editingATrackWithoutPresetsAnswersWithAnError() throws IOException
    {
        // o findOrCreate antigo gravava "phases": [] para a faixa criada antes da primeira fase;
        // na migração ela fica sem preset nenhum, e um get(0) aqui subiria pelo listener do JDA
        write("""
            { "tracks": [ { "name": "Batalha", "source": "s", "phases": [ ] } ] }
            """);

        assertNotNull(service.savePhase("Batalha", PRESET, -1, "A", "0", "30", null));
        assertNotNull(service.deletePhase("Batalha", PRESET, 0));
        assertNotNull(service.applyMark("Batalha", PRESET, 12_000, "new"));
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
        assertNull(service.savePhase("Batalha", PRESET, -1, "Fim", "120", "180", null));
        assertNull(service.savePhase("Batalha", PRESET, -1, "Começo", "0", "60", null));

        PhaseConfig.Track track = PhaseConfig.load().tracks.get(0);
        assertEquals("Começo", track.presets.get(0).phases.get(0).name,
                "a passagem entre fases assume ordem crescente de início");
        assertEquals("Fim", track.presets.get(0).phases.get(1).name);
    }

    @Test
    @DisplayName("recusa fase com fim antes do início")
    void rejectsInvertedPhase() throws IOException
    {
        givenTrack("Batalha");
        String error = service.savePhase("Batalha", PRESET, -1, "Ruim", "90", "30", null);
        assertNotNull(error);
        assertTrue(error.contains("depois do início"), error);
        assertTrue(PhaseConfig.load().tracks.get(0).presets.get(0).phases.isEmpty());
    }

    @Test
    @DisplayName("aceita tempo em mm:ss além de segundos")
    void acceptsClockNotation() throws IOException
    {
        givenTrack("Batalha");
        assertNull(service.savePhase("Batalha", PRESET, -1, "Refrão", "1:06", "1:58", null));

        PhaseConfig.Phase phase = PhaseConfig.load().tracks.get(0).presets.get(0).phases.get(0);
        assertEquals(66.0, phase.start, 0.001);
        assertEquals(118.0, phase.end, 0.001);
    }

    @Test
    @DisplayName("recusa número inválido sem corromper o arquivo")
    void rejectsGarbageNumbers() throws IOException
    {
        givenTrack("Batalha");
        assertNotNull(service.savePhase("Batalha", PRESET, -1, "X", "abc", "10", null));
        assertTrue(PhaseConfig.load().tracks.get(0).presets.get(0).phases.isEmpty());
    }

    @Test
    @DisplayName("editar uma fase existente não cria outra")
    void editsInPlace() throws IOException
    {
        givenTrack("Batalha");
        assertNull(service.savePhase("Batalha", PRESET, -1, "Intro", "0", "30", null));
        assertNull(service.savePhase("Batalha", PRESET, 0, "Intro longa", "0", "45", null));

        PhaseConfig.Track track = PhaseConfig.load().tracks.get(0);
        assertEquals(1, track.presets.get(0).phases.size());
        assertEquals("Intro longa", track.presets.get(0).phases.get(0).name);
        assertEquals(45.0, track.presets.get(0).phases.get(0).end, 0.001);
    }

    @Test
    @DisplayName("excluir remove só a fase escolhida")
    void deletesOnlyTheChosenPhase() throws IOException
    {
        givenTrack("Batalha");
        assertNull(service.savePhase("Batalha", PRESET, -1, "A", "0", "30", null));
        assertNull(service.savePhase("Batalha", PRESET, -1, "B", "30", "60", null));

        assertNull(service.deletePhase("Batalha", PRESET, 0));

        PhaseConfig.Track track = PhaseConfig.load().tracks.get(0);
        assertEquals(1, track.presets.get(0).phases.size());
        assertEquals("B", track.presets.get(0).phases.get(0).name);
    }

    @Test
    @DisplayName("marcar posição ajusta o início da fase escolhida")
    void markAdjustsPhaseStart() throws IOException
    {
        givenTrack("Batalha");
        assertNull(service.savePhase("Batalha", PRESET, -1, "A", "10", "60", null));

        assertNull(service.applyMark("Batalha", PRESET, 25_400, "start:0"));

        PhaseConfig.Phase phase = PhaseConfig.load().tracks.get(0).presets.get(0).phases.get(0);
        assertEquals(25.4, phase.start, 0.001);
        assertEquals(60.0, phase.end, 0.001);
    }

    @Test
    @DisplayName("marcar recusa ponto que inverteria a fase")
    void markRejectsInvalidPoint() throws IOException
    {
        givenTrack("Batalha");
        assertNull(service.savePhase("Batalha", PRESET, -1, "A", "10", "60", null));

        String error = service.applyMark("Batalha", PRESET, 90_000, "start:0");
        assertNotNull(error);
        assertEquals(10.0, PhaseConfig.load().tracks.get(0).presets.get(0).phases.get(0).start, 0.001);
    }

    @Test
    @DisplayName("marcar cria fase nova a partir do ponto")
    void markCreatesNewPhase() throws IOException
    {
        givenTrack("Batalha");
        assertNull(service.applyMark("Batalha", PRESET, 12_000, "new"));

        PhaseConfig.Track track = PhaseConfig.load().tracks.get(0);
        assertEquals(1, track.presets.get(0).phases.size());
        assertEquals(12.0, track.presets.get(0).phases.get(0).start, 0.001);
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
        assertNull(service.savePhase("Batalha", PRESET, -1, "A", "0", "60", "  "));

        PhaseConfig.Phase phase = PhaseConfig.load().tracks.get(0).presets.get(0).phases.get(0);
        assertNull(phase.fade, "sem valor próprio a fase acompanha o padrão do bot");
        assertEquals(PhaseConfig.DEFAULT_FADE_MS, phase.fadeMs(PhaseConfig.DEFAULT_FADE_MS));
    }

    @Test
    @DisplayName("fade próprio é gravado e vence o padrão")
    void ownFadeOverridesTheDefault() throws IOException
    {
        givenTrack("Batalha");
        assertNull(service.savePhase("Batalha", PRESET, -1, "A", "0", "60", "0.5"));

        PhaseConfig.Phase phase = PhaseConfig.load().tracks.get(0).presets.get(0).phases.get(0);
        assertEquals(0.5, phase.fade, 0.001);
        assertEquals(500, phase.fadeMs(PhaseConfig.DEFAULT_FADE_MS));
    }

    @Test
    @DisplayName("fade 0 é corte seco, e não 'sem valor'")
    void zeroFadeIsAHardCut() throws IOException
    {
        givenTrack("Batalha");
        assertNull(service.savePhase("Batalha", PRESET, -1, "A", "0", "60", "0"));

        PhaseConfig.Phase phase = PhaseConfig.load().tracks.get(0).presets.get(0).phases.get(0);
        assertNotNull(phase.fade, "0 é uma escolha, não a ausência de escolha");
        assertEquals(0, phase.fadeMs(PhaseConfig.DEFAULT_FADE_MS));
    }

    @Test
    @DisplayName("recusa fade maior que metade da fase em vez de cortar calado")
    void rejectsFadeLongerThanHalfThePhase() throws IOException
    {
        givenTrack("Batalha");
        // fase de 10s: acima de 5s o crossfade cruzaria a fase consigo mesma
        String error = service.savePhase("Batalha", PRESET, -1, "A", "0", "10", "6");
        assertNotNull(error);
        assertTrue(error.contains("metade"), error);
        assertTrue(PhaseConfig.load().tracks.get(0).presets.get(0).phases.isEmpty());
    }

    @Test
    @DisplayName("recusa fade sem número sem corromper o arquivo")
    void rejectsGarbageFade() throws IOException
    {
        givenTrack("Batalha");
        assertNotNull(service.savePhase("Batalha", PRESET, -1, "A", "0", "60", "abc"));
        assertTrue(PhaseConfig.load().tracks.get(0).presets.get(0).phases.isEmpty());
    }

    @Test
    @DisplayName("editar uma fase pode devolver o fade ao padrão")
    void clearingTheFadeReturnsToDefault() throws IOException
    {
        givenTrack("Batalha");
        assertNull(service.savePhase("Batalha", PRESET, -1, "A", "0", "60", "0.5"));
        assertNull(service.savePhase("Batalha", PRESET, 0, "A", "0", "60", ""));

        assertNull(PhaseConfig.load().tracks.get(0).presets.get(0).phases.get(0).fade);
    }

    // ── preset vazio ─────────────────────────────────────────────────────────
    //
    // Um preset sem nenhuma fase é tocável: vale a música inteira em loop, para o mestre marcar
    // as fases ouvindo. Essa fase existe só na reprodução — se escapar para o arquivo, vira uma
    // fase de verdade que ninguém pediu e que passa a aparecer no painel.

    /** Faixa cadastrada com um preset ainda sem nenhuma fase — o caso da segmentação ao vivo. */
    private static final String EMPTY_PRESET_FILE = """
        { "tracks": [ { "name": "Crown", "source": "s",
            "presets": [ { "name": "Do zero", "phases": [] } ] } ] }
        """;

    @Test
    @DisplayName("preset vazio vira uma fase implícita cobrindo a música inteira")
    void emptyPresetBecomesOneImplicitPhase()
    {
        PhaseConfig.Phase implicit = PhaseService.implicitPhase(263_000);

        assertEquals(0.0, implicit.start, "começa no 0:00");
        assertEquals(263.0, implicit.end, "termina no fim do arquivo");
        assertNull(implicit.fade, "segue o fade padrão do bot");
    }

    @Test
    @DisplayName("a fase implícita não é gravada no arquivo")
    void implicitPhaseIsNeverPersisted() throws Exception
    {
        write(EMPTY_PRESET_FILE);

        PhaseService.implicitPhase(263_000);

        PhaseConfig.Track track = PhaseConfig.load().tracks.get(0);
        assertTrue(track.preset("Do zero").phases.isEmpty(),
                "a fase implícita existe só na reprodução; gravá-la viraria uma fase de verdade");
    }

    @Test
    @DisplayName("improvisar não encosta no preset que veio do arquivo")
    void improvisingLeavesTheStoredPresetEmpty() throws Exception
    {
        write(EMPTY_PRESET_FILE);
        PhaseConfig config = PhaseConfig.load();
        PhaseConfig.Segmentation empty = config.tracks.get(0).firstSegmentation();

        PhaseConfig.Segmentation improvised = PhaseService.improvise(empty, 263_000);

        assertEquals(1, improvised.phases().size(), "o preset vazio toca uma fase só");
        assertEquals(263.0, improvised.phases().get(0).end);
        assertTrue(empty.phases().isEmpty(), "o preset do arquivo não pode ganhar a fase implícita");

        // a gravação seguinte usa o mesmo objeto em memória: é aqui que a fase implícita
        // escaparia para o arquivo se ela tivesse sido pendurada no preset original
        config.save();
        assertTrue(PhaseConfig.load().tracks.get(0).preset("Do zero").phases.isEmpty());
    }

    @Test
    @DisplayName("a segmentação improvisada mantém o nome do preset, para a marcação achá-lo")
    void improvisingKeepsThePresetName() throws Exception
    {
        write(EMPTY_PRESET_FILE);
        PhaseConfig.Segmentation empty = PhaseConfig.load().tracks.get(0).firstSegmentation();

        PhaseConfig.Segmentation improvised = PhaseService.improvise(empty, 263_000);

        assertEquals("Do zero", improvised.presetName());
        // é este o motivo do nome: marcar durante o preset vazio é justamente o que a fase
        // implícita existe para permitir, e o applyMark acha o preset pelo nome que está tocando
        assertNull(service.applyMark("Crown", improvised.presetName(), 12_000, "new"));
        assertEquals(1, PhaseConfig.load().tracks.get(0).preset("Do zero").phases.size());
    }

    @Test
    @DisplayName("sem duração o preset vazio é recusado, em vez de tocar uma fase de tamanho zero")
    void emptyPresetWithoutDurationIsRefused() throws Exception
    {
        write(EMPTY_PRESET_FILE);
        PhaseConfig.Segmentation empty = PhaseConfig.load().tracks.get(0).firstSegmentation();
        Replies replies = new Replies();

        service.startAt(null, null, empty, 0, 0, replies);

        assertNotNull(replies.error);
        assertTrue(replies.error.contains("Crown"), replies.error);
        assertTrue(replies.error.contains("preset sem nenhuma fase"),
                "a recusa é sobre o preset vazio, não sobre um defeito interno: " + replies.error);
        assertTrue(replies.error.contains("/play"),
                "e tem que dizer por onde tocar: " + replies.error);
    }

    @Test
    @DisplayName("transmissão ao vivo não tem duração para a fase implícita")
    void liveStreamHasNoUsableDuration()
    {
        assertEquals(0, PhaseService.knownDurationMs(fakeTrack("http://radio/x", "x", 600_000, true)),
                "stream não tem fim: a fase implícita não teria onde terminar");
    }

    @Test
    @DisplayName("a duração 'não sei' do lavaplayer não vira uma captura sem fim")
    void unknownDurationNeverBecomesAnEndlessCapture()
    {
        // Units.DURATION_MS_UNKNOWN é Long.MAX_VALUE e passa direto por uma guarda de "<= 0":
        // a fase implícita sairia com endMs() saturado em SegmentCapture.UNTIL_END, e a captura
        // decodificaria sem limite superior até derrubar a JVM
        assertEquals(SegmentCapture.UNTIL_END,
                PhaseService.implicitPhase(Units.DURATION_MS_UNKNOWN).endMs(),
                "é este o estrago que a leitura da duração tem que evitar");
        assertEquals(0, PhaseService.knownDurationMs(
                fakeTrack("https://example.com/x", "x", Units.DURATION_MS_UNKNOWN, false)));
    }

    @Test
    @DisplayName("faixa comum entrega a duração de verdade")
    void ordinaryTrackReportsItsDuration()
    {
        assertEquals(263_000, PhaseService.knownDurationMs(
                fakeTrack("https://example.com/x", "x", 263_000, false)));
    }

    @Test
    @DisplayName("com duração o preset vazio passa da guarda de fases e segue tocando")
    void emptyPresetWithDurationGetsPastTheGuard() throws Exception
    {
        // sem fonte: a checagem seguinte responde e para antes de precisar de um Guild de verdade
        write("""
            { "tracks": [ { "name": "Crown", "presets": [ { "name": "Do zero", "phases": [] } ] } ] }
            """);
        PhaseConfig.Segmentation empty = PhaseConfig.load().tracks.get(0).firstSegmentation();
        Replies replies = new Replies();

        service.startAt(null, null, empty, 0, 263_000, replies);

        assertNotNull(replies.error);
        assertTrue(replies.error.contains("fonte definida"),
                "preset vazio não pode mais ser recusado por falta de fase: " + replies.error);
    }

    /** Guarda o que o serviço respondeu, para os ramos de erro que param antes do áudio. */
    private static final class Replies extends com.jagrosh.jmusicbot.commands.BaseOutputAdapter
    {
        String error;

        @Override
        public void replyError(String content)
        {
            error = content;
        }
    }

    // ── reloadIfPlaying ──────────────────────────────────────────────────────
    //
    // Depois de editar uma fase da faixa que está tocando, o player é recarregado para a mudança
    // valer na hora. Recarregar pelo preset errado não estoura nada: a música simplesmente vira
    // outra segmentação no meio da sessão, e o mestre só descobre pelo áudio.

    /** Duas segmentações da mesma música; a que interessa é a segunda. */
    private static final String TWO_PRESETS_FILE = """
        { "tracks": [ { "name": "Crown", "source": "s", "presets": [
            { "name": "Combate",    "phases": [ { "name": "A", "start": 0,  "end": 30 } ] },
            { "name": "Exploração", "phases": [ { "name": "B", "start": 60, "end": 90 } ] } ] } ] }
        """;

    @Test
    @DisplayName("recarrega o preset que está tocando, não o primeiro da faixa")
    void reloadKeepsThePlayingPreset() throws Exception
    {
        write(TWO_PRESETS_FILE);

        PhaseConfig.Segmentation target =
                PhaseService.reloadTarget(PhaseConfig.load(), "Crown", "Exploração");

        assertNotNull(target);
        assertEquals("Exploração", target.presetName(),
                "marcar durante o preset 1 não pode jogar a reprodução no preset 0");
        assertEquals("B", target.phases().get(0).name);
    }

    @Test
    @DisplayName("preset renomeado no meio do caminho não troca a reprodução de segmentação")
    void reloadStopsWhenThePlayingPresetWasRenamed() throws Exception
    {
        write(TWO_PRESETS_FILE);
        assertNull(new PresetService().rename("Crown", "Exploração", "Viagem"));

        assertNull(PhaseService.reloadTarget(PhaseConfig.load(), "Crown", "Exploração"),
                "sem o preset antigo, cair no primeiro trocaria a música por outra segmentação");
    }

    @Test
    @DisplayName("preset excluído no meio do caminho não troca a reprodução de segmentação")
    void reloadStopsWhenThePlayingPresetWasDeleted() throws Exception
    {
        write(TWO_PRESETS_FILE);
        assertNull(new PresetService().delete("Crown", "Exploração"));

        assertNull(PhaseService.reloadTarget(PhaseConfig.load(), "Crown", "Exploração"));
    }

    @Test
    @DisplayName("preset que ficou sem fases não é recarregado")
    void reloadStopsWhenThePresetRanOutOfPhases() throws Exception
    {
        write(EMPTY_PRESET_FILE);

        assertNull(PhaseService.reloadTarget(PhaseConfig.load(), "Crown", "Do zero"),
                "não há fase nenhuma para onde voltar");
    }

    @Test
    @DisplayName("faixa que sumiu do arquivo não recarrega nem estoura")
    void reloadStopsWhenTheTrackIsGone() throws Exception
    {
        write(TWO_PRESETS_FILE);

        assertNull(PhaseService.reloadTarget(PhaseConfig.load(), "Sumiu", "Combate"));
        assertNull(PhaseService.reloadTarget(PhaseConfig.load(), "Crown", null));
    }

    // ── planEntry ────────────────────────────────────────────────────────────
    //
    // Decide em que fase o modo fase entra quando a troca acontece com a música tocando, e que
    // trecho decodificar pra isso. Errar aqui não estoura nada: só faz a música pular, ou o loop
    // ficar preso no pedaço final de uma fase em vez da fase inteira.

    /** A = [10s, 20s], vão de 10s, B = [30s, 40s]. */
    private static PhaseConfig.Segmentation trackWithGap()
    {
        PhaseConfig.Track track = new PhaseConfig.Track();
        track.name = "Batalha";
        track.source = "https://example.com/x";
        PhaseConfig.Preset preset = new PhaseConfig.Preset();
        preset.name = PhaseConfig.LEGACY_PRESET_NAME;
        preset.phases.add(inMemoryPhase("A", 10, 20));
        preset.phases.add(inMemoryPhase("B", 30, 40));
        track.presets.add(preset);
        return new PhaseConfig.Segmentation(track, preset);
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
        return fakeTrack(uri, identifier, 1000, false);
    }

    /** Idem, com a duração e o "é transmissão ao vivo" que a fase implícita consulta. */
    private static AudioTrack fakeTrack(String uri, String identifier, long durationMs, boolean stream)
    {
        AudioTrackInfo info = new AudioTrackInfo("título", "autor", durationMs, identifier, stream, uri);
        return new AudioTrack()
        {
            public AudioTrackInfo getInfo() { return info; }
            public String getIdentifier() { return identifier; }
            public long getDuration() { return durationMs; }
            public AudioTrackState getState() { throw new UnsupportedOperationException(); }
            public void stop() { throw new UnsupportedOperationException(); }
            public boolean isSeekable() { throw new UnsupportedOperationException(); }
            public long getPosition() { throw new UnsupportedOperationException(); }
            public void setPosition(long position) { throw new UnsupportedOperationException(); }
            public void setMarker(TrackMarker marker) { throw new UnsupportedOperationException(); }
            public void addMarker(TrackMarker marker) { throw new UnsupportedOperationException(); }
            public void removeMarker(TrackMarker marker) { throw new UnsupportedOperationException(); }
            public AudioTrack makeClone() { throw new UnsupportedOperationException(); }
            public AudioSourceManager getSourceManager() { throw new UnsupportedOperationException(); }
            public void setUserData(Object data) { throw new UnsupportedOperationException(); }
            public Object getUserData() { throw new UnsupportedOperationException(); }
            public <T> T getUserData(Class<T> klass) { throw new UnsupportedOperationException(); }
        };
    }
}
