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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * As fases de cada faixa, lidas de {@code phases.json} na pasta do bot.
 *
 * <p>O {@code source} (URL ou busca) é o que o lavaplayer resolve; sem ele, cai no {@code file},
 * um caminho local. Os dois convivem porque o arquivo nasceu do editor em Python que existia
 * antes, e faixas antigas ainda podem ter só {@code file}.
 *
 * <pre>
 * {
 *   "tracks": [
 *     {
 *       "name": "Watch the Crown Fall",
 *       "source": "https://www.youtube.com/watch?v=...",
 *       "phases": [
 *         { "name": "Inicio",   "start": 0,     "end": 66.5 },
 *         { "name": "Pre drop", "start": 77.0,  "end": 118.0 }
 *       ]
 *     }
 *   ]
 * }
 * </pre>
 *
 * <p>É relido a cada comando, então editar o arquivo não pede restart.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PhaseConfig
{
    public static final String FILE_NAME = "phases.json";

    /** Crossfade do loop para fases que não definem um próprio. */
    public static final int DEFAULT_FADE_MS = 2000;

    public List<Track> tracks = new ArrayList<>();

    public static PhaseConfig load() throws IOException
    {
        Path path = resolveFile(FILE_NAME);
        if (!Files.exists(path))
            return new PhaseConfig();   // primeira faixa criada pelo bot cria o arquivo
        return new ObjectMapper().readValue(path.toFile(), PhaseConfig.class);
    }

    /**
     * Grava o arquivo de volta, no mesmo formato em que foi lido.
     *
     * <p>Escreve num temporário e move por cima: o bot relê o arquivo a cada comando, e uma
     * leitura no meio de uma escrita pegaria JSON pela metade.
     */
    public synchronized void save() throws IOException
    {
        Path path = resolveFile(FILE_NAME);
        Path temp = resolveFile(FILE_NAME + ".tmp");
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), this);
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Resolve contra {@code user.dir} explicitamente, em vez de {@link Paths#get(String, String...)}
     * sozinho: esse lê o diretório padrão do processo, fixado uma vez na inicialização da JVM, e
     * ignora mudanças posteriores em {@code user.dir} — o que quebrava o isolamento dos testes
     * (achavam que trocavam a pasta, mas liam/escreviam no {@code phases.json} real do projeto).
     */
    private static Path resolveFile(String name)
    {
        return Paths.get(System.getProperty("user.dir"), name);
    }

    /** Acha a faixa pelo nome exato, ou cria uma nova já registrada no config. */
    public Track findOrCreate(String name, String source)
    {
        for (Track existing : tracks)
        {
            if (existing.name != null && existing.name.equalsIgnoreCase(name))
                return existing;
        }
        Track created = new Track();
        created.name = name;
        created.source = source;
        tracks.add(created);
        return created;
    }

    /**
     * Acha o que {@code !fase} deve tocar. Nessa ordem: nome de faixa exato (fase 0), nome de
     * fase exato em qualquer faixa (essa fase), nome de faixa que contenha o texto (fase 0),
     * nome de fase que contenha o texto (essa fase). Entre faixas diferentes com a mesma fase,
     * fica a primeira do arquivo.
     */
    public Match find(String query)
    {
        for (Track track : tracks)
            if (track.name != null && track.name.equalsIgnoreCase(query))
                return new Match(track, 0);

        Match exactPhase = findByPhaseName(query, true);
        if (exactPhase != null)
            return exactPhase;

        String lowered = query.toLowerCase();
        for (Track track : tracks)
            if (track.name != null && track.name.toLowerCase().contains(lowered))
                return new Match(track, 0);

        return findByPhaseName(query, false);
    }

    /**
     * Índice da faixa cujo {@code source}/{@code file} corresponde ao que o lavaplayer está
     * tocando agora, ou -1. Usa a URI (o mais confiável, quando bate exato) e, se não bater,
     * cai para o identifier do lavaplayer — no YouTube ele é só o ID do vídeo, então "o
     * source contém o identifier" cobre o caso comum de o {@code source} ser a URL completa.
     * Para arquivo local também aceita bater só pelo nome, caso um lado seja caminho relativo
     * e o outro absoluto.
     */
    public int indexMatchingPlayback(AudioTrack playing)
    {
        for (int i = 0; i < tracks.size(); i++)
            if (tracks.get(i).matches(playing))
                return i;
        return -1;
    }

    /** Índice da faixa pelo nome exato — para quando já se sabe qual {@link Track} é (modo fase). */
    public int indexOfName(String name)
    {
        for (int i = 0; i < tracks.size(); i++)
            if (tracks.get(i).name != null && tracks.get(i).name.equalsIgnoreCase(name))
                return i;
        return -1;
    }

    private static boolean matchesSource(String source, String uri, String identifier)
    {
        if (uri != null && normalizePath(source).equals(normalizePath(uri)))
            return true;
        if (identifier != null && !identifier.isBlank() && source.contains(identifier))
            return true;
        if (!source.contains("://") && uri != null && !uri.contains("://")
                && basename(source).equalsIgnoreCase(basename(uri)))
            return true;
        return false;
    }

    private static String normalizePath(String path)
    {
        return path.trim().replace('\\', '/').toLowerCase();
    }

    private static String basename(String path)
    {
        String normalized = normalizePath(path);
        int slash = normalized.lastIndexOf('/');
        return slash < 0 ? normalized : normalized.substring(slash + 1);
    }

    private Match findByPhaseName(String query, boolean exact)
    {
        String lowered = query.toLowerCase();
        for (Track track : tracks)
        {
            for (int i = 0; i < track.phases.size(); i++)
            {
                String name = track.phases.get(i).name;
                if (name == null)
                    continue;
                boolean matches = exact ? name.equalsIgnoreCase(query)
                                        : name.toLowerCase().contains(lowered);
                if (matches)
                    return new Match(track, i);
            }
        }
        return null;
    }

    /** Uma faixa e a fase, dentro dela, que {@link #find} decidiu tocar. */
    public static class Match
    {
        public final Track track;
        public final int phaseIndex;

        public Match(Track track, int phaseIndex)
        {
            this.track = track;
            this.phaseIndex = phaseIndex;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Track
    {
        public String name;
        public String source;
        public String file;
        /** Fontes extras que tocam a mesma música (ex: mesma faixa por YouTube e YouTube Music). */
        public List<String> aliases = new ArrayList<>();
        public List<Phase> phases = new ArrayList<>();

        /** O que mandar pro lavaplayer resolver. */
        public String identifier()
        {
            return source != null && !source.isEmpty() ? source : file;
        }

        /**
         * true se {@code playing} é esta faixa, pela fonte principal ou por alguma alternativa.
         * Fica aqui (e não só no {@link #indexMatchingPlayback}) para quem já tem a {@link Track}
         * em mãos poder conferir sem reler o arquivo — é o caso do modo fase, que guarda a faixa.
         */
        public boolean matches(AudioTrack playing)
        {
            if (playing == null)
                return false;
            String uri = playing.getInfo() != null ? playing.getInfo().uri : null;
            String identifier = playing.getIdentifier();

            String primary = identifier();
            if (primary != null && matchesSource(primary, uri, identifier))
                return true;
            for (String alias : aliases)
                if (alias != null && matchesSource(alias, uri, identifier))
                    return true;
            return false;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Phase
    {
        public String name;
        public double start;
        public double end;
        /**
         * Crossfade do loop desta fase, em segundos. Nulo (ausente do arquivo) segue o padrão —
         * é o que mantém as fases antigas, e as criadas por marcação, soando como sempre.
         */
        public Double fade;

        public long startMs()
        {
            return Math.round(start * 1000);
        }

        public long endMs()
        {
            return Math.round(end * 1000);
        }

        /** Crossfade em ms, ou {@code fallback} se esta fase não define um. */
        public int fadeMs(int fallback)
        {
            return fade == null ? fallback : (int) Math.max(0, Math.round(fade * 1000));
        }
    }
}
