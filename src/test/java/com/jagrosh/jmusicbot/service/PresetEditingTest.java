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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Presets compartilham a mesma faixa e, se as listas de fases forem compartilhadas por
 * referência, editar um altera o outro em silêncio — nenhuma exceção, só dados corrompidos.
 * É por isso que o isolamento é o foco destes testes.
 */
class PresetEditingTest
{
    @TempDir Path dir;
    private String previousUserDir;
    private PresetService presets;
    private PhaseService phases;

    /**
     * Mesmo isolamento do {@code PhaseEditingTest}: {@code @TempDir} entrega uma pasta vazia por
     * teste, e é para ela que o {@code user.dir} aponta enquanto o teste roda — sem isso a
     * escrita cairia no {@code phases.json} REAL do bot.
     */
    @BeforeEach
    void setUp()
    {
        previousUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", dir.toString());
        presets = new PresetService();
        phases = new PhaseService(null);
    }

    @AfterEach
    void tearDown()
    {
        System.setProperty("user.dir", previousUserDir);
    }

    private void seed() throws Exception
    {
        Files.writeString(dir.resolve(PhaseConfig.FILE_NAME), """
            { "tracks": [ { "name": "Crown", "source": "https://youtu.be/x",
                "presets": [ { "name": "Combate", "phases": [
                    { "name": "Inicio", "start": 0, "end": 30, "fade": 0.5 },
                    { "name": "Drop",   "start": 40, "end": 90 } ] } ] } ] }
            """);
    }

    @Test
    @DisplayName("duplicar copia as fases sem compartilhá-las")
    void duplicatingCopiesPhasesByValue() throws Exception
    {
        seed();
        assertNull(presets.create("Crown", "Exploração", "Combate"));

        // muda o original
        assertNull(phases.savePhase("Crown", "Combate", 0, "Renomeada", "0", "30", null));

        PhaseConfig.Track track = PhaseConfig.load().tracks.get(0);
        assertEquals("Renomeada", track.preset("Combate").phases.get(0).name);
        assertEquals("Inicio", track.preset("Exploração").phases.get(0).name,
                "editar um preset não pode alterar a cópia");
    }

    @Test
    @DisplayName("duplicar preserva o fade por fase")
    void duplicatingKeepsPerPhaseFade() throws Exception
    {
        seed();
        presets.create("Crown", "Exploração", "Combate");

        PhaseConfig.Preset copy = PhaseConfig.load().tracks.get(0).preset("Exploração");
        assertEquals(0.5, copy.phases.get(0).fade);
        assertNull(copy.phases.get(1).fade, "fade ausente continua ausente");
    }

    @Test
    @DisplayName("criar vazio não copia nada")
    void createsAnEmptyPreset() throws Exception
    {
        seed();
        assertNull(presets.create("Crown", "Do zero", null));

        assertTrue(PhaseConfig.load().tracks.get(0).preset("Do zero").phases.isEmpty());
    }

    @Test
    @DisplayName("recusa nome de preset repetido na mesma faixa")
    void rejectsDuplicateName() throws Exception
    {
        seed();
        String error = presets.create("Crown", "combate", null);
        assertNotNull(error, "comparação é sem diferenciar maiúsculas");
        assertTrue(error.contains("Combate"), "a mensagem tem que dizer qual já existe");
    }

    @Test
    @DisplayName("recusa nome vazio")
    void rejectsBlankName() throws Exception
    {
        seed();
        assertNotNull(presets.create("Crown", "   ", null));
    }

    @Test
    @DisplayName("recusa excluir o último preset da faixa")
    void refusesToDeleteTheLastPreset() throws Exception
    {
        seed();
        String error = presets.delete("Crown", "Combate");
        assertNotNull(error, "sem preset a faixa sumiria da detecção de modo fase");

        assertEquals(1, PhaseConfig.load().tracks.get(0).presets.size());
    }

    @Test
    @DisplayName("exclui quando sobra outro")
    void deletesWhenAnotherRemains() throws Exception
    {
        seed();
        presets.create("Crown", "Exploração", null);
        assertNull(presets.delete("Crown", "Combate"));

        PhaseConfig.Track track = PhaseConfig.load().tracks.get(0);
        assertEquals(1, track.presets.size());
        assertEquals("Exploração", track.presets.get(0).name);
    }

    @Test
    @DisplayName("renomear mantém as fases")
    void renameKeepsPhases() throws Exception
    {
        seed();
        assertNull(presets.rename("Crown", "Combate", "Batalha"));

        PhaseConfig.Track track = PhaseConfig.load().tracks.get(0);
        assertNull(track.preset("Combate"));
        assertEquals(2, track.preset("Batalha").phases.size());
    }

    @Test
    @DisplayName("renomear para um nome já usado é recusado")
    void renameRejectsDuplicate() throws Exception
    {
        seed();
        presets.create("Crown", "Exploração", null);
        assertNotNull(presets.rename("Crown", "Combate", "Exploração"));
    }

    /**
     * O painel do Discord fica aberto por tempo indefinido, então o preset pode ser renomeado ou
     * excluído entre abrir o painel e enviar o modal. Sem o ramo de preset inexistente o
     * {@code preset.phases} estoura NullPointerException dentro do listener do JDA — a interação
     * morre calada, que é o modo de falha que as guardas existem para evitar.
     */
    @Test
    @DisplayName("editar com preset inexistente responde com erro em vez de estourar")
    void editingAMissingPresetAnswersWithAnError() throws Exception
    {
        seed();

        assertNotNull(phases.savePhase("Crown", "Sumido", -1, "A", "0", "30", null));
        assertNotNull(phases.deletePhase("Crown", "Sumido", 0));
        assertNotNull(phases.applyMark("Crown", "Sumido", 12_000, "new"));

        assertEquals(1, PhaseConfig.load().tracks.get(0).presets.size(),
                "recusar não pode ter criado nem mexido em preset nenhum");
        assertEquals(2, PhaseConfig.load().tracks.get(0).preset("Combate").phases.size());
    }

    /**
     * O {@code editingOnePresetLeavesTheOtherAlone} cobre o {@code deletePhase}, mas os testes de
     * {@code savePhase} acima editam o preset 0 — onde o {@code presets.get(0)} da Tarefa 1 dá
     * exatamente o mesmo resultado. Aqui os dois métodos escrevem no preset de trás, que é o
     * único jeito de a escolha do preset aparecer no resultado.
     */
    @Test
    @DisplayName("gravar e marcar caem no preset pedido, não no primeiro da faixa")
    void editingWritesToTheRequestedPreset() throws Exception
    {
        seed();
        assertNull(presets.create("Crown", "Exploração", null));

        assertNull(phases.savePhase("Crown", "Exploração", -1, "Calmaria", "5", "25", null));
        assertNull(phases.applyMark("Crown", "Exploração", 60_000, "new"));

        PhaseConfig.Track track = PhaseConfig.load().tracks.get(0);
        assertEquals(2, track.preset("Combate").phases.size(), "o preset 0 não foi tocado");
        assertEquals(2, track.preset("Exploração").phases.size());
        assertEquals("Calmaria", track.preset("Exploração").phases.get(0).name);
        assertEquals(60.0, track.preset("Exploração").phases.get(1).start, 0.001);
    }

    /**
     * Os testes acima passam por {@code phases.json}, e a ida e volta pelo JSON desfaz qualquer
     * compartilhamento: o Jackson grava as fases duas vezes e a releitura devolve instâncias
     * novas. Ou seja, eles <i>não</i> reprovariam uma cópia rasa — só este reprova, porque olha
     * as duas segmentações vivas na mesma instância de config, que é o estado em que o
     * {@code create} deixa o objeto antes de gravar.
     */
    @Test
    @DisplayName("a duplicata não compartilha as instâncias de fase com o original")
    void duplicatingSharesNothingInMemory()
    {
        PhaseConfig.Preset origin = new PhaseConfig.Preset();
        origin.name = "Combate";
        PhaseConfig.Phase phase = new PhaseConfig.Phase();
        phase.name = "Inicio";
        phase.start = 0;
        phase.end = 30;
        origin.phases.add(phase);

        PhaseConfig.Preset copy = origin.copyAs("Exploração");
        copy.phases.get(0).name = "Renomeada";
        copy.phases.remove(0);

        assertEquals(1, origin.phases.size(), "mexer na cópia não pode encolher o original");
        assertEquals("Inicio", origin.phases.get(0).name);
    }

    @Test
    @DisplayName("editar fase de um preset não mexe no outro")
    void editingOnePresetLeavesTheOtherAlone() throws Exception
    {
        seed();
        presets.create("Crown", "Exploração", "Combate");
        assertNull(phases.deletePhase("Crown", "Exploração", 0));

        PhaseConfig.Track track = PhaseConfig.load().tracks.get(0);
        assertEquals(2, track.preset("Combate").phases.size(), "o original fica intacto");
        assertEquals(1, track.preset("Exploração").phases.size());
    }
}
