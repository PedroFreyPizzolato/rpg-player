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

import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
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
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PhaseConfig#find} tem 4 ramos priorizados (faixa exata, fase exata, faixa parcial,
 * fase parcial) — é exatamente o tipo de lógica que erra em silêncio: acha *alguma coisa*,
 * só que a errada.
 */
class PhaseConfigTest
{
    @TempDir Path dir;
    private String previousUserDir;

    @BeforeEach
    void isolate()
    {
        previousUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", dir.toString());
    }

    @AfterEach
    void restore()
    {
        System.setProperty("user.dir", previousUserDir);
    }

    private void write(String json) throws IOException
    {
        Files.writeString(dir.resolve(PhaseConfig.FILE_NAME), json);
    }

    private static PhaseConfig configWithTwoTracks()
    {
        PhaseConfig config = new PhaseConfig();

        PhaseConfig.Track pool = new PhaseConfig.Track();
        pool.name = "In The Pool (Synthwave)";
        pool.file = "pool.mp3";
        preset(pool).phases.add(phase("Parte 1"));
        preset(pool).phases.add(phase("Parte 2"));
        config.tracks.add(pool);

        PhaseConfig.Track paintress = new PhaseConfig.Track();
        paintress.name = "Paintress";
        paintress.file = "paintress.mp3";
        preset(paintress).phases.add(phase("Tenso"));
        preset(paintress).phases.add(phase("Calma"));
        config.tracks.add(paintress);

        return config;
    }

    /** O preset único da faixa, criado na primeira chamada. */
    private static PhaseConfig.Preset preset(PhaseConfig.Track track)
    {
        if (track.presets.isEmpty())
        {
            PhaseConfig.Preset preset = new PhaseConfig.Preset();
            preset.name = PhaseConfig.LEGACY_PRESET_NAME;
            track.presets.add(preset);
        }
        return track.presets.get(0);
    }

    private static PhaseConfig.Phase phase(String name)
    {
        PhaseConfig.Phase phase = new PhaseConfig.Phase();
        phase.name = name;
        phase.start = 0;
        phase.end = 10;
        return phase;
    }

    @Test
    @DisplayName("nome de faixa exato acha a faixa na fase 0")
    void exactTrackName()
    {
        PhaseConfig config = configWithTwoTracks();
        PhaseConfig.Match match = config.find("Paintress");
        assertSame(config.tracks.get(1), match.segmentation.track);
        assertEquals(0, match.phaseIndex);
    }

    @Test
    @DisplayName("nome de fase exato acha a faixa dona e a fase certa, não a fase 0")
    void exactPhaseNameAcrossTracks()
    {
        PhaseConfig config = configWithTwoTracks();
        PhaseConfig.Match match = config.find("Calma");
        assertSame(config.tracks.get(1), match.segmentation.track);
        assertEquals(1, match.phaseIndex, "Calma é a segunda fase de Paintress, não a primeira");
    }

    @Test
    @DisplayName("nome de faixa parcial (case-insensitive) acha a faixa na fase 0")
    void partialTrackName()
    {
        PhaseConfig config = configWithTwoTracks();
        PhaseConfig.Match match = config.find("pool");
        assertSame(config.tracks.get(0), match.segmentation.track);
        assertEquals(0, match.phaseIndex);
    }

    @Test
    @DisplayName("nome de fase parcial acha a faixa dona na fase certa")
    void partialPhaseName()
    {
        PhaseConfig config = configWithTwoTracks();
        PhaseConfig.Match match = config.find("Part");
        assertSame(config.tracks.get(0), match.segmentation.track);
        assertEquals(0, match.phaseIndex);
    }

    @Test
    @DisplayName("nome de faixa exato vence mesmo se também bater como substring de uma fase")
    void trackNameBeatsPhaseSubstring()
    {
        PhaseConfig config = configWithTwoTracks();
        // "Paintress" não é substring de nenhuma fase, mas o inverso importa: garantir que a
        // prioridade 1 (faixa exata) nunca é pulada em favor da prioridade 2 (fase exata)
        // quando os dois são candidatos válidos.
        PhaseConfig.Track same = new PhaseConfig.Track();
        same.name = "Calma";                 // mesmo nome de uma fase de Paintress
        same.file = "calma.mp3";
        preset(same).phases.add(phase("Unica"));
        config.tracks.add(same);

        PhaseConfig.Match match = config.find("Calma");
        assertSame(same, match.segmentation.track, "faixa chamada 'Calma' deve vencer a fase chamada 'Calma'");
        assertEquals(0, match.phaseIndex);
    }

    @Test
    @DisplayName("nada bate: null")
    void noMatch()
    {
        PhaseConfig config = configWithTwoTracks();
        assertNull(config.find("não existe"));
    }

    // ── indexMatchingPlayback / indexOfName ─────────────────────────────────
    //
    // Usados pra pré-selecionar a faixa no painel e pra oferecer o modo fase ao dar play.
    // Um falso positivo aqui oferece fases da música errada; um falso negativo simplesmente
    // não oferece — os dois erram calados, sem exceção.

    @Test
    @DisplayName("YouTube: casa pelo identifier (ID do vídeo) contido na URL completa do source")
    void matchesYoutubeByIdentifierInUrl()
    {
        PhaseConfig config = new PhaseConfig();
        PhaseConfig.Track track = new PhaseConfig.Track();
        track.name = "Batalha";
        track.source = "https://www.youtube.com/watch?v=jNQXAC9IVRw";
        config.tracks.add(track);

        AudioTrack playing = fakeTrack("https://www.youtube.com/watch?v=jNQXAC9IVRw&list=xyz", "jNQXAC9IVRw");
        assertEquals(0, config.indexMatchingPlayback(playing));
    }

    @Test
    @DisplayName("local: casa por URI normalizada mesmo com barras diferentes e maiúsculas diferentes")
    void matchesLocalFileIgnoringSlashesAndCase()
    {
        PhaseConfig config = new PhaseConfig();
        PhaseConfig.Track track = new PhaseConfig.Track();
        track.name = "Batalha";
        track.file = "C:/Users/Pedro/Musicas/Batalha.mp3";
        config.tracks.add(track);

        AudioTrack playing = fakeTrack("c:\\users\\pedro\\musicas\\batalha.mp3", "c:\\users\\pedro\\musicas\\batalha.mp3");
        assertEquals(0, config.indexMatchingPlayback(playing));
    }

    @Test
    @DisplayName("local: casa só pelo nome do arquivo quando um lado é caminho relativo")
    void matchesLocalFileByBasenameWhenOnePathIsRelative()
    {
        PhaseConfig config = new PhaseConfig();
        PhaseConfig.Track track = new PhaseConfig.Track();
        track.name = "Batalha";
        track.file = "C:/Users/Pedro/Musicas/Batalha.mp3";
        config.tracks.add(track);

        AudioTrack playing = fakeTrack("Batalha.mp3", "Batalha.mp3");
        assertEquals(0, config.indexMatchingPlayback(playing));
    }

    @Test
    @DisplayName("não casa faixa não cadastrada")
    void doesNotMatchUnrelatedTrack()
    {
        PhaseConfig config = configWithTwoTracks();
        AudioTrack playing = fakeTrack("https://www.youtube.com/watch?v=someOtherId", "someOtherId");
        assertEquals(-1, config.indexMatchingPlayback(playing));
    }

    @Test
    @DisplayName("não casa arquivo local só porque o nome aparece como substring de uma URL")
    void doesNotFalsePositiveOnPartialUrlContainment()
    {
        PhaseConfig config = new PhaseConfig();
        PhaseConfig.Track track = new PhaseConfig.Track();
        track.name = "Batalha";
        track.source = "abc";   // identifier curto, poderia aparecer por acaso em outra uri
        config.tracks.add(track);

        AudioTrack playing = fakeTrack("https://www.youtube.com/watch?v=xyz789", "xyz789");
        assertEquals(-1, config.indexMatchingPlayback(playing));
    }

    @Test
    @DisplayName("indexOfName acha pelo nome exato, sem diferenciar maiúsculas")
    void indexOfNameIsCaseInsensitive()
    {
        PhaseConfig config = configWithTwoTracks();
        assertEquals(1, config.indexOfName("PAINTRESS"));
        assertEquals(-1, config.indexOfName("não existe"));
    }

    @Test
    @DisplayName("casa por uma fonte alternativa (alias), não só pela fonte principal")
    void matchesByAlias()
    {
        PhaseConfig config = new PhaseConfig();
        PhaseConfig.Track track = new PhaseConfig.Track();
        track.name = "Batalha";
        track.source = "https://www.youtube.com/watch?v=jNQXAC9IVRw";
        track.aliases.add("https://music.youtube.com/watch?v=zzzOtherId");
        config.tracks.add(track);

        AudioTrack playing = fakeTrack("https://music.youtube.com/watch?v=zzzOtherId", "zzzOtherId");
        assertEquals(0, config.indexMatchingPlayback(playing));
    }

    // ── migração do formato antigo ──────────────────────────────────────────
    //
    // O arquivo em produção nasceu com uma lista única de fases por faixa. Ler um desses sem
    // migrar não estoura nada: a faixa simplesmente aparece sem segmentação nenhuma.

    @Test
    @DisplayName("arquivo no formato antigo vira um preset Padrão sem perder nada")
    void migratesLegacyPhasesIntoADefaultPreset() throws Exception
    {
        write("""
            { "tracks": [ {
                "name": "Watch the Crown Fall",
                "source": "https://youtu.be/gV_uJpcuq5U",
                "aliases": [ "https://music.youtube.com/watch?v=w9ZM-7VzQvc" ],
                "phases": [ { "name": "Inicio", "start": 10.0, "end": 66.0, "fade": 0.5 } ]
            } ] }
            """);

        PhaseConfig config = PhaseConfig.load();
        PhaseConfig.Track track = config.tracks.get(0);

        assertEquals(1, track.presets.size(), "as fases antigas viram um preset");
        assertEquals("Padrão", track.presets.get(0).name);
        assertNull(track.phases, "o campo antigo é anulado para não ser gravado de volta");

        PhaseConfig.Phase phase = track.presets.get(0).phases.get(0);
        assertEquals("Inicio", phase.name);
        assertEquals(10.0, phase.start);
        assertEquals(66.0, phase.end);
        assertEquals(0.5, phase.fade, "o fade por fase tem que sobreviver à migração");
        assertEquals(1, track.aliases.size(), "o alias é da faixa, não da segmentação");
    }

    @Test
    @DisplayName("arquivo já migrado não é mexido de novo")
    void doesNotRemigrateAnAlreadyMigratedFile() throws Exception
    {
        write("""
            { "tracks": [ {
                "name": "Crown Fall",
                "source": "https://youtu.be/x",
                "presets": [
                  { "name": "Combate", "phases": [ { "name": "A", "start": 0, "end": 10 } ] },
                  { "name": "Exploração", "phases": [ ] } ]
            } ] }
            """);

        PhaseConfig.Track track = PhaseConfig.load().tracks.get(0);

        assertEquals(2, track.presets.size());
        assertEquals("Combate", track.presets.get(0).name, "não inventa um preset Padrão");
        assertTrue(track.presets.get(1).phases.isEmpty(), "preset vazio continua vazio");
    }

    @Test
    @DisplayName("gravar depois de migrar guarda uma cópia do arquivo antigo, uma vez só")
    void backsUpTheLegacyFileOnceOnFirstSave() throws Exception
    {
        write("""
            { "tracks": [ { "name": "A", "source": "s",
                "phases": [ { "name": "F", "start": 0, "end": 5 } ] } ] }
            """);

        Path backup = Paths.get(System.getProperty("user.dir"), PhaseConfig.FILE_NAME + ".bak");
        assertFalse(Files.exists(backup), "ainda não gravamos nada");

        PhaseConfig config = PhaseConfig.load();
        config.save();
        assertTrue(Files.exists(backup), "a cópia do formato antigo tem que existir");
        String firstBackup = Files.readString(backup);
        assertTrue(firstBackup.contains("\"phases\""), "a cópia é do arquivo ANTES da migração");

        // segunda gravação não pode sobrescrever a cópia original
        PhaseConfig again = PhaseConfig.load();
        again.tracks.get(0).presets.get(0).phases.clear();
        again.save();
        assertEquals(firstBackup, Files.readString(backup),
                "sobrescrever o .bak destruiria o único registro do estado anterior");
    }

    @Test
    @DisplayName("o formato novo não grava mais o campo antigo")
    void neverWritesTheLegacyPhasesField() throws Exception
    {
        write("""
            { "tracks": [ { "name": "A", "source": "s",
                "phases": [ { "name": "F", "start": 0, "end": 5 } ] } ] }
            """);

        PhaseConfig config = PhaseConfig.load();
        config.save();

        String written = Files.readString(Paths.get(System.getProperty("user.dir"),
                PhaseConfig.FILE_NAME));
        assertTrue(written.contains("\"presets\""), "grava no formato novo");
        assertFalse(written.contains("\"phases\" : null"), "não grava o campo legado");
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
