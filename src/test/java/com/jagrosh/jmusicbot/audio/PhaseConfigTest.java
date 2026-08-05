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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link PhaseConfig#find} tem 4 ramos priorizados (faixa exata, fase exata, faixa parcial,
 * fase parcial) — é exatamente o tipo de lógica que erra em silêncio: acha *alguma coisa*,
 * só que a errada.
 */
class PhaseConfigTest
{
    private static PhaseConfig configWithTwoTracks()
    {
        PhaseConfig config = new PhaseConfig();

        PhaseConfig.Track pool = new PhaseConfig.Track();
        pool.name = "In The Pool (Synthwave)";
        pool.file = "pool.mp3";
        pool.phases.add(phase("Parte 1"));
        pool.phases.add(phase("Parte 2"));
        config.tracks.add(pool);

        PhaseConfig.Track paintress = new PhaseConfig.Track();
        paintress.name = "Paintress";
        paintress.file = "paintress.mp3";
        paintress.phases.add(phase("Tenso"));
        paintress.phases.add(phase("Calma"));
        config.tracks.add(paintress);

        return config;
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
        assertSame(config.tracks.get(1), match.track);
        assertEquals(0, match.phaseIndex);
    }

    @Test
    @DisplayName("nome de fase exato acha a faixa dona e a fase certa, não a fase 0")
    void exactPhaseNameAcrossTracks()
    {
        PhaseConfig config = configWithTwoTracks();
        PhaseConfig.Match match = config.find("Calma");
        assertSame(config.tracks.get(1), match.track);
        assertEquals(1, match.phaseIndex, "Calma é a segunda fase de Paintress, não a primeira");
    }

    @Test
    @DisplayName("nome de faixa parcial (case-insensitive) acha a faixa na fase 0")
    void partialTrackName()
    {
        PhaseConfig config = configWithTwoTracks();
        PhaseConfig.Match match = config.find("pool");
        assertSame(config.tracks.get(0), match.track);
        assertEquals(0, match.phaseIndex);
    }

    @Test
    @DisplayName("nome de fase parcial acha a faixa dona na fase certa")
    void partialPhaseName()
    {
        PhaseConfig config = configWithTwoTracks();
        PhaseConfig.Match match = config.find("Part");
        assertSame(config.tracks.get(0), match.track);
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
        same.phases.add(phase("Unica"));
        config.tracks.add(same);

        PhaseConfig.Match match = config.find("Calma");
        assertSame(same, match.track, "faixa chamada 'Calma' deve vencer a fase chamada 'Calma'");
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
