# Presets de Segmentação — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permitir várias segmentações salvas por música, escolhidas na hora do play.

**Architecture:** As fases descem um nível no `phases.json`: `Track.presets[].phases[]` em vez de
`Track.phases[]`. Um envelope resolvido `Segmentation(track, preset)` carrega o par pelo código,
substituindo `Track` nas assinaturas que precisavam das fases. A Tarefa 1 é um refactor puro
(comportamento idêntico, um preset por faixa); as tarefas seguintes são aditivas.

**Tech Stack:** Java 25+, JDA 6.4.2, lavaplayer 2.2.7 (`dev.arbjerg`), Jackson, JUnit 5,
Maven com maven-shade-plugin.

## Global Constraints

- **Não existe git neste projeto.** Onde um plano normal mandaria commitar, aqui o fecho de
  tarefa é: compilar + rodar a suíte + conferir que o `phases.json` real não foi tocado.
- **NUNCA rode os testes com o diretório de trabalho no projeto.** `PhaseEditingTest` apaga
  `phases.json` resolvido contra o diretório real do processo. Rode sempre de `$SCRATCH/testrun`.
  Este projeto já perdeu o `phases.json` uma vez exatamente assim.
- **NUNCA reconstrua o jar com o bot rodando.** A JVM carrega classes do jar sob demanda;
  trocar o arquivo embaixo do processo causa `NoClassDefFoundError` em classes ainda não
  carregadas. Peça ao usuário para parar o bot antes de qualquer `mvn package`.
- Build exige JDK 25+ (`maven-enforcer-plugin`). O `JAVA_HOME` padrão da máquina é JDK 21.
- Comentários e mensagens ao usuário em **português**, seguindo o estilo do código existente
  (comentários explicam *por quê*, não *o quê*).
- `@JsonIgnoreProperties(ignoreUnknown = true)` e `@JsonInclude(NON_NULL)` em toda classe
  serializada, como já é o padrão em `PhaseConfig`.

## Comandos

Defina uma vez por sessão de shell:

```bash
export JAVA_HOME="C:\Program Files\Java\jdk-26.0.1"
export PATH="$JAVA_HOME/bin:$PATH"
SCRATCH="/c/Users/55519/AppData/Local/Temp/claude/c--Users-55519-rpg-player/7977534e-7b20-4993-9f8c-a74fac56896c/scratchpad"
CPW=$(cat "$SCRATCH/cpw.txt")
OUTW=$(cygpath -w "$SCRATCH/out")
cd /c/Users/55519/rpg-player
find src/main/java -name "*.java" | sed 's|^|C:/Users/55519/rpg-player/|' > "$SCRATCH/srcs2.txt"
find src/test/java -name "*.java" | sed 's|^|C:/Users/55519/rpg-player/|' > "$SCRATCH/test_srcs2.txt"
```

**Compilar main:**
```bash
javac -nowarn -d "$SCRATCH/out" -cp "$CPW" @"$SCRATCH/srcs2.txt"
```

**Compilar testes:**
```bash
javac -nowarn -d "$SCRATCH/out" -cp "$OUTW;$CPW" @"$SCRATCH/test_srcs2.txt"
```

**Rodar a suíte** (de dentro do scratch, nunca do projeto):
```bash
cd "$SCRATCH/testrun" && for t in audio.SegmentPlayerTest audio.PhaseConfigTest service.PhaseEditingTest; do
  echo "--- $t"; java -cp "$OUTW;$CPW" RunTests com.jagrosh.jmusicbot.$t 2>&1 | tail -2
done
```

**Conferir que os dados reais sobreviveram** (obrigatório após cada rodada de testes):
```bash
ls -la --time-style=+%H:%M:%S /c/Users/55519/rpg-player/phases.json
```

Baseline atual: **52 testes** (16 + 13 + 23), `phases.json` com 2296 bytes.

## File Structure

| Arquivo | Responsabilidade após a mudança |
|---|---|
| `audio/PhaseConfig.java` | Modelo + migração + `Preset` + `Segmentation`. Cresce ~90 linhas. |
| `audio/SegmentPlayer.java` | Passa a receber `Segmentation`. Só troca de tipo. |
| `service/PhaseService.java` | Edição e play operam sobre preset. Já tem 860 linhas; as ações de preset (CRUD) vão para um arquivo novo para não engordá-lo. |
| `service/PresetService.java` | **Novo.** Criar, duplicar, renomear e excluir preset. Isolado porque é a única lógica realmente nova de dados. |
| `utils/PhaseMessageFormatter.java` | Selects com ações embutidas; painel em 5 linhas. |
| `listener/PhaseInteractionListener.java` | Roteamento dos novos IDs de componente. |
| `service/AudioLoadResultHandlers.java` | Oferta de play com menu suspenso. |
| `commands/v1/music/PhaseCmd.java`, `commands/v2/music/PhaseSlashCmd.java` | Aceitar nome de preset. |
| `utils/MessageFormatter.java` | Nada muda de tipo; só acompanha a nova regra de "faixa tem segmentação" (`presets`, não `phases`). |

---

### Task 1: Modelo aninhado, migração e refactor mecânico

Refactor puro: ao final, o bot se comporta **exatamente** como hoje, com um preset por faixa.
Nenhuma funcionalidade nova. Os 52 testes existentes continuam passando (com ajuste de tipo),
mais os novos de migração.

**Files:**
- Modify: `src/main/java/com/jagrosh/jmusicbot/audio/PhaseConfig.java`
- Modify: `src/main/java/com/jagrosh/jmusicbot/audio/SegmentPlayer.java` (linhas 87, 105, 247, 272, 284, 299, 346, 355)
- Modify: `src/main/java/com/jagrosh/jmusicbot/service/PhaseService.java` (linhas 78, 88, 106-107, 170, 179, 236, 312-327, 364, 411-418, 535-550, 568-571, 598-627, 648-654, 709-718)
- Modify: `src/main/java/com/jagrosh/jmusicbot/utils/PhaseMessageFormatter.java` (linhas 70-71, 84, 98, 118, 141-143, 227-251, 267, 276, 294-296, 387-396)
- Modify: `src/main/java/com/jagrosh/jmusicbot/listener/PhaseInteractionListener.java` (linhas 317-322, 368)
- Modify: `src/main/java/com/jagrosh/jmusicbot/service/AudioLoadResultHandlers.java` (linha 260)
- Modify: `src/main/java/com/jagrosh/jmusicbot/utils/MessageFormatter.java` (linha 278)
- Test: `src/test/java/com/jagrosh/jmusicbot/audio/PhaseConfigTest.java`
- Test: `src/test/java/com/jagrosh/jmusicbot/audio/SegmentPlayerTest.java` (helper `track(...)`)
- Test: `src/test/java/com/jagrosh/jmusicbot/service/PhaseEditingTest.java`

**Interfaces:**
- Produces:
  - `PhaseConfig.Preset` — campos públicos `String name`, `List<Phase> phases`
  - `PhaseConfig.Track.presets` — `List<Preset>`
  - `PhaseConfig.Track.phases` — `List<Phase>`, legado, `null` após migrar
  - `PhaseConfig.Segmentation` — construtor `Segmentation(Track track, Preset preset)`;
    métodos `List<Phase> phases()`, `String identifier()`, `String trackName()`,
    `String presetName()`
  - `PhaseConfig.Track.preset(String name)` → `Preset` ou `null`
  - `PhaseConfig.Track.firstSegmentation()` → `Segmentation` do preset 0, ou `null` se não há preset
  - `PhaseConfig.Match` passa a expor `Segmentation segmentation` no lugar de `Track track`
  - `SegmentPlayer.getSegmentation()` substitui `getTrack()`
  - `PhaseService.findMatchingPhases(AudioTrack)` passa a devolver `PhaseConfig.Track`
    (inalterado no tipo — mas agora exige `!presets.isEmpty()` em vez de `!phases.isEmpty()`)

- [ ] **Step 1: Escrever os testes de migração que falham**

Em `src/test/java/com/jagrosh/jmusicbot/audio/PhaseConfigTest.java`, adicione:

```java
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
```

Se `PhaseConfigTest` ainda não tem um helper `write(String json)` nem `@BeforeEach`/`@AfterEach`
que isolem o diretório, adicione (espelhando o que `PhaseEditingTest` já faz):

```java
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
```

- [ ] **Step 2: Rodar para ver falhar**

```bash
javac -nowarn -d "$SCRATCH/out" -cp "$OUTW;$CPW" @"$SCRATCH/test_srcs2.txt"
```
Esperado: FALHA de compilação — `PhaseConfig.Preset` e `track.presets` não existem.

- [ ] **Step 3: Adicionar `Preset`, `Segmentation` e o campo `presets`**

Em `PhaseConfig.java`, dentro da classe, junto de `Phase`:

```java
    /** Uma segmentação nomeada da mesma música. Uma faixa pode ter várias. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Preset
    {
        public String name;
        public List<Phase> phases = new ArrayList<>();

        /** Cópia profunda: duplicar um preset não pode compartilhar as instâncias de fase. */
        public Preset copyAs(String newName)
        {
            Preset copy = new Preset();
            copy.name = newName;
            for (Phase phase : phases)
            {
                Phase clone = new Phase();
                clone.name = phase.name;
                clone.start = phase.start;
                clone.end = phase.end;
                clone.fade = phase.fade;
                copy.phases.add(clone);
            }
            return copy;
        }
    }

    /**
     * Faixa + preset já resolvidos. Existe só em memória: quem toca precisa da fonte (da faixa)
     * e das fases (do preset), e passar os dois soltos espalharia o par por seis assinaturas.
     */
    public static class Segmentation
    {
        public final Track track;
        public final Preset preset;

        public Segmentation(Track track, Preset preset)
        {
            this.track = track;
            this.preset = preset;
        }

        public List<Phase> phases()   { return preset.phases; }
        public String identifier()    { return track.identifier(); }
        public String trackName()     { return track.name; }
        public String presetName()    { return preset.name; }
    }
```

Em `Track`, troque o campo de fases:

```java
        public List<Preset> presets = new ArrayList<>();

        /**
         * Formato antigo, quando a faixa tinha uma lista única de fases. É lido para migrar e
         * anulado em seguida; com NON_NULL, deixa de ser gravado.
         */
        public List<Phase> phases;

        public Preset preset(String presetName)
        {
            for (Preset preset : presets)
                if (preset.name != null && preset.name.equalsIgnoreCase(presetName))
                    return preset;
            return null;
        }

        public Segmentation firstSegmentation()
        {
            return presets.isEmpty() ? null : new Segmentation(this, presets.get(0));
        }
```

- [ ] **Step 4: Migrar no `load()` e fazer o backup no `save()`**

Substitua `load()` e `save()` em `PhaseConfig.java`:

```java
    public static final String LEGACY_PRESET_NAME = "Padrão";

    /** true quando esta instância veio de um arquivo no formato antigo. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient boolean migrated;

    public static PhaseConfig load() throws IOException
    {
        Path path = resolveFile(FILE_NAME);
        if (!Files.exists(path))
            return new PhaseConfig();   // primeira faixa criada pelo bot cria o arquivo
        PhaseConfig config = new ObjectMapper().readValue(path.toFile(), PhaseConfig.class);
        config.migrateLegacyPhases();
        return config;
    }

    /**
     * Move as fases soltas da faixa para um preset. Roda a cada leitura, mas só faz algo em
     * arquivo antigo — faixa que já tem preset não é tocada.
     */
    private void migrateLegacyPhases()
    {
        for (Track track : tracks)
        {
            if (track.phases == null)
                continue;
            if (track.presets.isEmpty() && !track.phases.isEmpty())
            {
                Preset preset = new Preset();
                preset.name = LEGACY_PRESET_NAME;
                preset.phases = track.phases;
                track.presets.add(preset);
                migrated = true;
            }
            track.phases = null;
        }
    }

    public synchronized void save() throws IOException
    {
        Path path = resolveFile(FILE_NAME);
        // este projeto já perdeu o phases.json uma vez; antes da primeira gravação no formato
        // novo, o arquivo antigo fica guardado para o caso de a migração estar errada
        if (migrated)
        {
            Path backup = resolveFile(FILE_NAME + ".bak");
            if (Files.exists(path) && !Files.exists(backup))
                Files.copy(path, backup);
            migrated = false;
        }
        Path temp = resolveFile(FILE_NAME + ".tmp");
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), this);
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
    }
```

Adicione os imports que faltarem: `java.nio.file.Files` já existe; confirme
`com.fasterxml.jackson.annotation.JsonIgnore`.

- [ ] **Step 5: Atualizar `Match`, `find` e `findOrCreate`**

`Match` passa a carregar a segmentação resolvida:

```java
    public static class Match
    {
        public final Segmentation segmentation;
        public final int phaseIndex;

        public Match(Segmentation segmentation, int phaseIndex)
        {
            this.segmentation = segmentation;
            this.phaseIndex = phaseIndex;
        }
    }
```

Em `find(String query)`, todo `new Match(track, 0)` vira `new Match(track.firstSegmentation(), 0)`,
pulando faixa sem preset:

```java
    public Match find(String query)
    {
        for (Track track : tracks)
            if (track.name != null && track.name.equalsIgnoreCase(query)
                    && track.firstSegmentation() != null)
                return new Match(track.firstSegmentation(), 0);

        Match exactPhase = findByPhaseName(query, true);
        if (exactPhase != null)
            return exactPhase;

        String lowered = query.toLowerCase();
        for (Track track : tracks)
            if (track.name != null && track.name.toLowerCase().contains(lowered)
                    && track.firstSegmentation() != null)
                return new Match(track.firstSegmentation(), 0);

        return findByPhaseName(query, false);
    }
```

`findByPhaseName` passa a varrer todos os presets:

```java
    private Match findByPhaseName(String query, boolean exact)
    {
        String lowered = query.toLowerCase();
        for (Track track : tracks)
        {
            for (Preset preset : track.presets)
            {
                for (int i = 0; i < preset.phases.size(); i++)
                {
                    String name = preset.phases.get(i).name;
                    if (name == null)
                        continue;
                    boolean matches = exact ? name.equalsIgnoreCase(query)
                                            : name.toLowerCase().contains(lowered);
                    if (matches)
                        return new Match(new Segmentation(track, preset), i);
                }
            }
        }
        return null;
    }
```

`findOrCreate` passa a garantir um preset (ver spec: faixa nunca fica sem preset):

```java
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
        Preset preset = new Preset();
        preset.name = LEGACY_PRESET_NAME;
        created.presets.add(preset);   // faixa sem preset não teria onde guardar fase
        tracks.add(created);
        return created;
    }
```

- [ ] **Step 6: `SegmentPlayer` recebe `Segmentation`**

Troque o campo e o getter:

```java
    private final PhaseConfig.Segmentation segmentation;
```

E cada uso, nesta ordem exata de linhas atuais:

| linha atual | de | para |
|---|---|---|
| 87 | `track.phases.get(phaseIndex).startMs()` | `segmentation.phases().get(phaseIndex).startMs()` |
| 105 | `track.phases.get(phaseIndex).startMs()` | `segmentation.phases().get(phaseIndex).startMs()` |
| 247 | `track.phases.size()` | `segmentation.phases().size()` |
| 272 | `public PhaseConfig.Track getTrack()` | `public PhaseConfig.Segmentation getSegmentation()` |
| 284 | `track.phases.get(phaseIndex).name` | `segmentation.phases().get(phaseIndex).name` |
| 299 | `track.phases.get(phaseIndex).fadeMs(...)` | `segmentation.phases().get(phaseIndex).fadeMs(...)` |
| 346 | `track.phases.get(phaseIndex)` | `segmentation.phases().get(phaseIndex)` |
| 355 | `track.phases.get(phaseIndex + 1)` | `segmentation.phases().get(phaseIndex + 1)` |

Os parâmetros `PhaseConfig.Track track` do construtor e de `resumingAt` viram
`PhaseConfig.Segmentation segmentation`. O `LOGGER.error(... track.name ...)` em `chooseNext()`
e o `LOGGER.info` de `finish()` passam a usar `segmentation.trackName()`.

- [ ] **Step 7: Propagar nos demais arquivos**

Padrão mecânico em todos os call sites listados em **Files**: onde havia uma `Track` usada só
para chegar nas fases, passe uma `Segmentation`; `track.phases` vira `segmentation.phases()`.

Pontos que exigem decisão e não são busca-e-troca:

- `PhaseService.start(...)` linha 78: `startAt(guild, channel, match.track, match.phaseIndex, output)`
  vira `startAt(guild, channel, match.segmentation, match.phaseIndex, output)`.
- `PhaseService.startAt(...)`: o parâmetro `PhaseConfig.Track track` vira
  `PhaseConfig.Segmentation segmentation`. A guarda da linha 88 passa a ser
  `if (segmentation.phases().isEmpty())` — mantida por ora; a Tarefa 3 a substitui.
- `PhaseService.planEntry(PhaseConfig.Track track, long positionMs)` vira
  `planEntry(PhaseConfig.Segmentation segmentation, long positionMs)`.
- `PhaseService.findMatchingPhases` (linha 718): `track.phases.isEmpty()` vira
  `track.presets.isEmpty()`.
- `PhaseService.savePhase/deletePhase/applyMark` continuam recebendo `String trackName` e
  passam a operar em `track.presets.get(0).phases`. **Isto é temporário** e a Tarefa 2 troca
  por um preset escolhido; deixe um comentário `// TAREFA 2: preset escolhido, não o primeiro`.
- `PhaseInteractionListener` linha 368: `config.indexOfName(segments.getTrack().name)` vira
  `config.indexOfName(segments.getSegmentation().trackName())`.
- `AudioLoadResultHandlers` linha 260: `phased.phases.size()` vira
  `phased.presets.get(0).phases.size()`.
- `PhaseMessageFormatter.describeTrack` e `phaseModal` recebem a lista de fases do preset 0.

- [ ] **Step 8: Atualizar os testes existentes**

`SegmentPlayerTest`: o helper `track(List<Phase>)` passa a devolver `Segmentation`:

```java
    private static PhaseConfig.Segmentation track(List<PhaseConfig.Phase> phases)
    {
        PhaseConfig.Track track = new PhaseConfig.Track();
        track.name = "teste";
        track.source = "teste";
        PhaseConfig.Preset preset = new PhaseConfig.Preset();
        preset.name = "Padrão";
        preset.phases = phases;
        track.presets.add(preset);
        return new PhaseConfig.Segmentation(track, preset);
    }
```

Nenhuma asserção de áudio muda — se alguma mudar, é bug do refactor, não do teste.

`PhaseEditingTest`: onde monta `track.phases`, monte `track.presets.get(0).phases`.
`PhaseConfigTest`: idem nos testes de `matches`/`indexMatchingPlayback` que criem faixas.

- [ ] **Step 9: Compilar e rodar tudo**

```bash
javac -nowarn -d "$SCRATCH/out" -cp "$CPW" @"$SCRATCH/srcs2.txt"
javac -nowarn -d "$SCRATCH/out" -cp "$OUTW;$CPW" @"$SCRATCH/test_srcs2.txt"
cd "$SCRATCH/testrun" && for t in audio.SegmentPlayerTest audio.PhaseConfigTest service.PhaseEditingTest; do
  echo "--- $t"; java -cp "$OUTW;$CPW" RunTests com.jagrosh.jmusicbot.$t 2>&1 | tail -2
done
```
Esperado: **56 passando, 0 falhando** (52 antigos + 4 de migração).

- [ ] **Step 10: Conferir os dados reais e fechar**

```bash
ls -la --time-style=+%H:%M:%S /c/Users/55519/rpg-player/phases.json
```
Esperado: 2296 bytes, horário inalterado. Se mudou, os testes rodaram no diretório errado —
restaure de `phases.json.bak` ou do backup em `$SCRATCH` e corrija o isolamento antes de seguir.

---

### Task 2: CRUD de presets

**Files:**
- Create: `src/main/java/com/jagrosh/jmusicbot/service/PresetService.java`
- Modify: `src/main/java/com/jagrosh/jmusicbot/service/PhaseService.java` (savePhase, deletePhase, applyMark — aceitar nome de preset)
- Test: `src/test/java/com/jagrosh/jmusicbot/service/PresetEditingTest.java` (novo)

**Interfaces:**
- Consumes: `PhaseConfig.Preset`, `PhaseConfig.Track.preset(String)`, `Preset.copyAs(String)` (Tarefa 1)
- Produces:
  - `PresetService.create(String trackName, String presetName, String copyFrom)` → `String` (mensagem de erro, ou `null` em sucesso). `copyFrom` nulo/vazio = preset vazio.
  - `PresetService.rename(String trackName, String oldName, String newName)` → `String`
  - `PresetService.delete(String trackName, String presetName)` → `String`
  - `PhaseService.savePhase(String trackName, String presetName, int phaseIndex, String name, String startText, String endText, String fadeText)` → `String`
  - `PhaseService.deletePhase(String trackName, String presetName, int phaseIndex)` → `String`
  - `PhaseService.applyMark(String trackName, String presetName, long positionMs, String target)` → `String`

- [ ] **Step 1: Escrever os testes que falham**

Crie `src/test/java/com/jagrosh/jmusicbot/service/PresetEditingTest.java`. Copie o
`@BeforeEach`/`@AfterEach`/`@TempDir` de `PhaseEditingTest` — o isolamento de diretório é
obrigatório.

```java
/**
 * Presets compartilham a mesma faixa e, se as listas de fases forem compartilhadas por
 * referência, editar um altera o outro em silêncio — nenhuma exceção, só dados corrompidos.
 * É por isso que o isolamento é o foco destes testes.
 */
class PresetEditingTest
{
    private PresetService presets;
    private PhaseService phases;

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
```

- [ ] **Step 2: Rodar para ver falhar**

```bash
javac -nowarn -d "$SCRATCH/out" -cp "$OUTW;$CPW" @"$SCRATCH/test_srcs2.txt"
```
Esperado: FALHA — `PresetService` não existe.

- [ ] **Step 3: Escrever o `PresetService`**

```java
package com.jagrosh.jmusicbot.service;

import com.jagrosh.jmusicbot.audio.PhaseConfig;

import java.io.IOException;

/**
 * Cria, duplica, renomeia e exclui as segmentações de uma faixa. Fica fora do
 * {@link PhaseService}, que já passa das 800 linhas, porque é a única lógica de dados
 * realmente nova — o resto do serviço só ganhou um parâmetro.
 *
 * <p>Todo método devolve a mensagem de erro para o usuário, ou {@code null} em sucesso,
 * seguindo o padrão do {@link PhaseService}.
 */
public class PresetService
{
    public String create(String trackName, String presetName, String copyFrom)
    {
        if (presetName == null || presetName.isBlank())
            return "O nome do preset não pode ficar vazio.";

        try
        {
            PhaseConfig config = PhaseConfig.load();
            PhaseConfig.Track track = find(config, trackName);
            if (track == null)
                return "A faixa `" + trackName + "` sumiu do arquivo.";

            String name = presetName.trim();
            PhaseConfig.Preset clash = track.preset(name);
            if (clash != null)
                return "`" + track.name + "` já tem um preset chamado `" + clash.name + "`.";

            PhaseConfig.Preset created;
            if (copyFrom == null || copyFrom.isBlank())
            {
                created = new PhaseConfig.Preset();
                created.name = name;
            }
            else
            {
                PhaseConfig.Preset origin = track.preset(copyFrom);
                if (origin == null)
                    return "O preset `" + copyFrom + "` não existe em `" + track.name + "`.";
                created = origin.copyAs(name);
            }

            track.presets.add(created);
            config.save();
            return null;
        }
        catch (IOException e)
        {
            return "Não consegui gravar o `" + PhaseConfig.FILE_NAME + "`: " + e.getMessage();
        }
    }

    public String rename(String trackName, String oldName, String newName)
    {
        if (newName == null || newName.isBlank())
            return "O nome do preset não pode ficar vazio.";

        try
        {
            PhaseConfig config = PhaseConfig.load();
            PhaseConfig.Track track = find(config, trackName);
            if (track == null)
                return "A faixa `" + trackName + "` sumiu do arquivo.";

            PhaseConfig.Preset preset = track.preset(oldName);
            if (preset == null)
                return "O preset `" + oldName + "` não existe em `" + track.name + "`.";

            String name = newName.trim();
            PhaseConfig.Preset clash = track.preset(name);
            if (clash != null && clash != preset)
                return "`" + track.name + "` já tem um preset chamado `" + clash.name + "`.";

            preset.name = name;
            config.save();
            return null;
        }
        catch (IOException e)
        {
            return "Não consegui gravar o `" + PhaseConfig.FILE_NAME + "`: " + e.getMessage();
        }
    }

    public String delete(String trackName, String presetName)
    {
        try
        {
            PhaseConfig config = PhaseConfig.load();
            PhaseConfig.Track track = find(config, trackName);
            if (track == null)
                return "A faixa `" + trackName + "` sumiu do arquivo.";

            PhaseConfig.Preset preset = track.preset(presetName);
            if (preset == null)
                return "O preset `" + presetName + "` não existe em `" + track.name + "`.";
            // sem preset nenhum a faixa deixaria de ser reconhecida pela detecção de modo fase
            if (track.presets.size() <= 1)
                return "`" + preset.name + "` é o único preset de `" + track.name
                        + "`. Apague a faixa inteira, ou crie outro preset antes.";

            track.presets.remove(preset);
            config.save();
            return null;
        }
        catch (IOException e)
        {
            return "Não consegui gravar o `" + PhaseConfig.FILE_NAME + "`: " + e.getMessage();
        }
    }

    private static PhaseConfig.Track find(PhaseConfig config, String name)
    {
        for (PhaseConfig.Track track : config.tracks)
            if (track.name != null && track.name.equalsIgnoreCase(name))
                return track;
        return null;
    }
}
```

- [ ] **Step 4: Estender as assinaturas de edição de fase no `PhaseService`**

`savePhase`, `deletePhase` e `applyMark` ganham `String presetName` logo após `trackName`.
Onde a Tarefa 1 deixou `track.presets.get(0).phases`, resolva o preset:

```java
            PhaseConfig.Preset preset = track.preset(presetName);
            if (preset == null)
                return "O preset `" + presetName + "` não existe em `" + track.name + "`.";
            List<PhaseConfig.Phase> phases = preset.phases;
```

E remova o comentário `// TAREFA 2:` deixado na Tarefa 1. Atualize os call sites em
`PhaseInteractionListener` e nos comandos para passar o preset selecionado (na Tarefa 4 ele vem
do painel; por ora passe `track.presets.get(0).name`).

- [ ] **Step 5: Registrar o serviço no `Bot`**

Em `Bot.java`, ao lado de `phaseService`, adicione `private final PresetService presetService;`
inicializado no construtor e exposto por `public PresetService getPresetService()`. Siga
exatamente o padrão de `getPhaseService()`.

- [ ] **Step 6: Rodar tudo**

```bash
javac -nowarn -d "$SCRATCH/out" -cp "$CPW" @"$SCRATCH/srcs2.txt"
javac -nowarn -d "$SCRATCH/out" -cp "$OUTW;$CPW" @"$SCRATCH/test_srcs2.txt"
cd "$SCRATCH/testrun" && for t in audio.SegmentPlayerTest audio.PhaseConfigTest service.PhaseEditingTest service.PresetEditingTest; do
  echo "--- $t"; java -cp "$OUTW;$CPW" RunTests com.jagrosh.jmusicbot.$t 2>&1 | tail -2
done
ls -la --time-style=+%H:%M:%S /c/Users/55519/rpg-player/phases.json
```
Esperado: 56 + 10 = **66 passando**, `phases.json` intacto.

---

### Task 3: Preset vazio toca como fase implícita

**Files:**
- Modify: `src/main/java/com/jagrosh/jmusicbot/service/PhaseService.java` (`startAt`, linhas 85-134)
- Test: `src/test/java/com/jagrosh/jmusicbot/service/PhaseEditingTest.java`

**Interfaces:**
- Consumes: `PhaseConfig.Segmentation` (Tarefa 1)
- Produces:
  - `PhaseService.implicitPhase(long durationMs)` → `PhaseConfig.Phase` estático, package-private
  - `PhaseService.startAt(Guild, MessageChannel, PhaseConfig.Segmentation, int phaseIndex, long durationMs, MusicService.OutputAdapter)` — o novo parâmetro `durationMs` é usado só quando o preset está vazio; passe `0` quando a duração não for conhecida e o preset tiver fases.

- [ ] **Step 1: Escrever o teste que falha**

Em `PhaseEditingTest`:

```java
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
        Files.writeString(dir.resolve(PhaseConfig.FILE_NAME), """
            { "tracks": [ { "name": "Crown", "source": "s",
                "presets": [ { "name": "Do zero", "phases": [] } ] } ] }
            """);

        PhaseService.implicitPhase(263_000);

        PhaseConfig.Track track = PhaseConfig.load().tracks.get(0);
        assertTrue(track.preset("Do zero").phases.isEmpty(),
                "a fase implícita existe só na reprodução; gravá-la viraria uma fase de verdade");
    }
```

- [ ] **Step 2: Rodar para ver falhar**

```bash
javac -nowarn -d "$SCRATCH/out" -cp "$OUTW;$CPW" @"$SCRATCH/test_srcs2.txt"
```
Esperado: FALHA — `implicitPhase` não existe.

- [ ] **Step 3: Implementar**

Em `PhaseService`:

```java
    /**
     * A fase que um preset vazio toca: a música inteira em loop. Existe só em memória — quem
     * está montando a segmentação ao vivo ainda não marcou nada, e gravar isto criaria uma
     * fase de verdade que a pessoa não pediu.
     */
    static PhaseConfig.Phase implicitPhase(long durationMs)
    {
        PhaseConfig.Phase phase = new PhaseConfig.Phase();
        phase.name = "Música inteira";
        phase.start = 0;
        phase.end = durationMs / 1000.0;
        return phase;
    }
```

Em `startAt`, substitua a guarda da linha 88:

```java
        List<PhaseConfig.Phase> phases = segmentation.phases();
        boolean improvising = phases.isEmpty();
        if (improvising)
        {
            if (durationMs <= 0)
            {
                output.replyError("Não consegui descobrir a duração de `"
                        + segmentation.trackName() + "` para tocar sem preset.");
                return;
            }
            phases = List.of(implicitPhase(durationMs));
            segmentation = new PhaseConfig.Segmentation(segmentation.track, improvised(phases));
        }
```

Onde `improvised` monta um `Preset` descartável:

```java
    private static PhaseConfig.Preset improvised(List<PhaseConfig.Phase> phases)
    {
        PhaseConfig.Preset preset = new PhaseConfig.Preset();
        preset.name = "sem preset";
        preset.phases = new ArrayList<>(phases);
        return preset;
    }
```

**Cuidado:** este `Preset` improvisado nunca pode chegar ao `config.save()`. Ele só vive dentro
da `Segmentation` entregue ao `SegmentPlayer`; a edição sempre recarrega do arquivo.

- [ ] **Step 4: Rodar tudo**

```bash
javac -nowarn -d "$SCRATCH/out" -cp "$CPW" @"$SCRATCH/srcs2.txt"
javac -nowarn -d "$SCRATCH/out" -cp "$OUTW;$CPW" @"$SCRATCH/test_srcs2.txt"
cd "$SCRATCH/testrun" && for t in audio.SegmentPlayerTest audio.PhaseConfigTest service.PhaseEditingTest service.PresetEditingTest; do
  echo "--- $t"; java -cp "$OUTW;$CPW" RunTests com.jagrosh.jmusicbot.$t 2>&1 | tail -2
done
ls -la --time-style=+%H:%M:%S /c/Users/55519/rpg-player/phases.json
```
Esperado: **68 passando**, `phases.json` intacto.

---

### Task 4: Painel com seletor de preset

**Files:**
- Modify: `src/main/java/com/jagrosh/jmusicbot/utils/PhaseMessageFormatter.java` (`panelComponents`, linhas 260-320; `describeTrack`, linhas 220-255)
- Modify: `src/main/java/com/jagrosh/jmusicbot/listener/PhaseInteractionListener.java` (roteamento de botão e select)
- Modify: `src/main/java/com/jagrosh/jmusicbot/service/PhaseService.java` (`buildPanelMessage` passa o preset selecionado)

**Interfaces:**
- Consumes: `PresetService.create/rename/delete` (Tarefa 2)
- Produces: novos IDs de componente, todos com o `PREFIX` já existente:
  - select `selecttrack` — opções `0..n` de faixa, mais `"newtrack"`
  - select `selectpreset:<trackIndex>` — opções `0..n` de preset, mais `"new"`, `"rename"`, `"delete"`
  - modal `presetmodal:<trackIndex>` — campos `name` e `copyfrom`
  - todos os IDs que hoje levam `:<trackIndex>` passam a levar `:<trackIndex>:<presetIndex>`

- [ ] **Step 1: Reorganizar o painel em 5 linhas**

O Discord permite 5 `ActionRow`. O painel já usa as 5, então as ações de "nova faixa" e de
preset entram como opções no fim dos próprios selects:

```
┌ Faixa:   [ Watch the Crown Fall  ▾ ]   + "➕ Nova faixa"
├ Preset:  [ Combate               ▾ ]   + "➕ Novo" "✏ Renomear" "🗑 Excluir"
├ [Adicionar fase] [Tocar] [Editar fonte] [Vincular fonte] [Atualizar]
├ Editar fase:  [ 2. Pre drop      ▾ ]
└ Apagar fase:  [ 3. Drop          ▾ ]
```

Em `panelComponents`, o select de faixas ganha ao fim:

```java
        options.add(SelectOption.of("➕ Nova faixa", "newtrack")
                .withDescription("Cadastrar outra música"));
```

E o select de presets, novo:

```java
        List<SelectOption> presetOptions = new ArrayList<>();
        for (int i = 0; i < Math.min(track.presets.size(), MAX_SELECT_OPTIONS - 3); i++)
        {
            PhaseConfig.Preset preset = track.presets.get(i);
            presetOptions.add(SelectOption.of(cut(preset.name, 100), String.valueOf(i))
                    .withDescription(preset.phases.size() + " fase(s)")
                    .withDefault(i == presetIndex));
        }
        presetOptions.add(SelectOption.of("➕ Novo preset", "new"));
        presetOptions.add(SelectOption.of("✏ Renomear este preset", "rename"));
        presetOptions.add(SelectOption.of("🗑 Excluir este preset", "delete"));
        rows.add(ActionRow.of(StringSelectMenu.create(id("selectpreset:" + trackIndex))
                .setPlaceholder("Preset: " + track.presets.get(presetIndex).name)
                .addOptions(presetOptions).build()));
```

O botão `newtrack` sai da linha de baixo, que passa a ser:
`[Adicionar fase] [Tocar] [Editar fonte] [Vincular fonte atual] [Atualizar]`.

- [ ] **Step 2: Modal de preset**

```java
    /**
     * Criar e renomear usam o mesmo modal. O campo "copiar de" só aparece ao criar — é a
     * ausência dele que o listener usa para saber qual das duas ações executar.
     */
    public static Modal presetModal(int trackIndex, String currentName)
    {
        boolean renaming = currentName != null;
        TextInput name = TextInput.create("name", TextInputStyle.SHORT)
                .setValue(currentName)
                .setPlaceholder("Combate, Exploração, Tensão...")
                .setRequired(true)
                .build();

        Modal.Builder modal = Modal.create(id("presetmodal:" + trackIndex),
                        renaming ? "Renomear preset" : "Novo preset")
                .addComponents(Label.of("Nome", name));

        if (!renaming)
        {
            TextInput copyFrom = TextInput.create("copyfrom", TextInputStyle.SHORT)
                    .setPlaceholder("Nome do preset a copiar — vazio começa do zero")
                    .setRequired(false)
                    .build();
            modal.addComponents(Label.of("Copiar de", copyFrom));
        }
        return modal.build();
    }
```

- [ ] **Step 3: Rotear os novos componentes**

Em `PhaseInteractionListener.onStringSelectInteraction`, adicione:

```java
            case "selectpreset" -> selectPreset(event, argInt(parts, 1, -1), value);
```

```java
    /**
     * O select de preset acumula seleção e ações porque o Discord só dá 5 linhas e o painel já
     * usava todas — as três últimas opções são comandos, não presets.
     */
    private void selectPreset(StringSelectInteractionEvent event, int trackIndex, String value)
    {
        PhaseConfig config = load(event);
        if (config == null)
            return;
        if (trackIndex < 0 || trackIndex >= config.tracks.size())
        {
            reply(event, "Essa faixa não existe mais.");
            return;
        }
        PhaseConfig.Track track = config.tracks.get(trackIndex);

        switch (value)
        {
            case "new" -> event.replyModal(
                    PhaseMessageFormatter.presetModal(trackIndex, null)).queue();
            case "rename" -> event.replyModal(
                    PhaseMessageFormatter.presetModal(trackIndex,
                            track.presets.get(selectedPreset(trackIndex)).name)).queue();
            case "delete" -> {
                String error = bot.getPresetService().delete(track.name,
                        track.presets.get(selectedPreset(trackIndex)).name);
                if (error != null)
                    reply(event, error);
                else
                    refreshPanel(event, trackIndex, 0);
            }
            default -> refreshPanel(event, trackIndex, parseInt(value, 0));
        }
    }
```

`presetModal(int trackIndex, String currentName)` tem dois campos quando `currentName` é nulo
(criar: nome + copiar de) e só o campo nome quando não é (renomear) — é o mesmo método do
Step 2, com a lista de opções de cópia removida da assinatura porque o usuário digita o nome.

`refreshPanel` ganha o parâmetro `presetIndex`; `selectedPreset(trackIndex)` lê o índice do
preset em exibição a partir do ID do componente que disparou a interação (os IDs passam a
carregar `:<trackIndex>:<presetIndex>`), com fallback para `0`.

Em `onModalInteraction`, trate `presetmodal`:

```java
            case "presetmodal" -> {
                int trackIndex = argInt(parts, 1, -1);
                PhaseConfig config = load(event);
                if (config == null || trackIndex < 0 || trackIndex >= config.tracks.size())
                {
                    reply(event, "Essa faixa não existe mais.");
                    return;
                }
                String trackName = config.tracks.get(trackIndex).name;
                String name = field(event, "name");
                String copyFrom = field(event, "copyfrom");
                // o modal de renomear não tem o campo de cópia; é assim que os dois se distinguem
                String error = copyFrom == null
                        ? bot.getPresetService().rename(trackName,
                                config.tracks.get(trackIndex).presets.get(selectedPreset(trackIndex)).name, name)
                        : bot.getPresetService().create(trackName, name, copyFrom);
                if (error != null)
                    reply(event, error);
                else
                    refreshPanel(event, trackIndex, 0);
            }
```

- [ ] **Step 4: Verificação manual**

Não há teste automatizado de UI do Discord neste projeto — o painel é verificado à mão.
Compile e confirme:

```bash
javac -nowarn -d "$SCRATCH/out" -cp "$CPW" @"$SCRATCH/srcs2.txt"
```

Depois peça ao usuário para parar o bot, reconstrua o jar e valide no Discord:
1. painel abre com o select de preset mostrando o preset atual
2. `➕ Novo preset` → modal → criar copiando e criar vazio
3. trocar de preset troca a lista de fases exibida
4. `🗑 Excluir` recusa quando é o único
5. as 5 linhas cabem (o Discord recusa a mensagem se passar)

- [ ] **Step 5: Rodar a suíte para garantir que nada regrediu**

```bash
javac -nowarn -d "$SCRATCH/out" -cp "$OUTW;$CPW" @"$SCRATCH/test_srcs2.txt"
cd "$SCRATCH/testrun" && for t in audio.SegmentPlayerTest audio.PhaseConfigTest service.PhaseEditingTest service.PresetEditingTest; do
  echo "--- $t"; java -cp "$OUTW;$CPW" RunTests com.jagrosh.jmusicbot.$t 2>&1 | tail -2
done
ls -la --time-style=+%H:%M:%S /c/Users/55519/rpg-player/phases.json
```
Esperado: **68 passando**, `phases.json` intacto.

---

### Task 5: Oferta de play com menu suspenso

**Files:**
- Modify: `src/main/java/com/jagrosh/jmusicbot/service/AudioLoadResultHandlers.java` (`offerPhaseMode`, linhas 252-295)
- Modify: `src/main/java/com/jagrosh/jmusicbot/listener/PhaseInteractionListener.java` (botão `play` do painel)

**Interfaces:**
- Consumes: `PhaseService.startAt(..., Segmentation, int, long durationMs, ...)` (Tarefa 3)
- Produces: `PhaseService.needsPresetChoice(PhaseConfig.Track)` → `boolean`, público e estático.

- [ ] **Step 1: Escrever o teste da regra de escolha**

A decisão "perguntar ou carregar direto" é regra de negócio; o resto da oferta é montagem de
componentes do Discord, que este projeto não testa automatizadamente. Extraia a regra para
poder testá-la. Em `PhaseEditingTest`:

```java
    private static PhaseConfig.Track withPresets(int howMany)
    {
        PhaseConfig.Track track = new PhaseConfig.Track();
        track.name = "Crown";
        track.source = "s";
        for (int i = 0; i < howMany; i++)
        {
            PhaseConfig.Preset preset = new PhaseConfig.Preset();
            preset.name = "P" + i;
            track.presets.add(preset);
        }
        return track;
    }

    @Test
    @DisplayName("um preset só carrega direto, sem perguntar")
    void singlePresetSkipsTheQuestion()
    {
        assertFalse(PhaseService.needsPresetChoice(withPresets(1)));
    }

    @Test
    @DisplayName("dois ou mais presets abrem a escolha")
    void multiplePresetsAskWhichOne()
    {
        assertTrue(PhaseService.needsPresetChoice(withPresets(2)));
        assertTrue(PhaseService.needsPresetChoice(withPresets(5)));
    }

    @Test
    @DisplayName("faixa sem preset não chega a perguntar")
    void noPresetNeverAsks()
    {
        assertFalse(PhaseService.needsPresetChoice(withPresets(0)),
                "sem preset a faixa nem aparece na detecção; perguntar seria menu vazio");
    }
```

- [ ] **Step 2: Rodar para ver falhar**

```bash
javac -nowarn -d "$SCRATCH/out" -cp "$OUTW;$CPW" @"$SCRATCH/test_srcs2.txt"
```
Esperado: FALHA — `needsPresetChoice` não existe.

- [ ] **Step 3: Implementar a regra**

Em `PhaseService`:

```java
    /** Só faz sentido perguntar quando há de fato mais de uma segmentação para escolher. */
    public static boolean needsPresetChoice(PhaseConfig.Track track)
    {
        return track != null && track.presets.size() > 1;
    }
```

- [ ] **Step 4: Ramificar por quantidade de presets**

Com **1 preset**, nada muda — os dois botões atuais, carregando o único direto. Com **2+**,
troque a linha de botões por um select mais o botão de tocar normal:

```java
        // com um preset só não há o que perguntar; a escolha entre segmentações só aparece
        // quando ela existe de verdade
        if (!PhaseService.needsPresetChoice(phased))
        {
            // ... mantém exatamente o fluxo de dois botões que já existe
            return;
        }

        List<SelectOption> options = new ArrayList<>();
        for (int i = 0; i < Math.min(phased.presets.size(), 24); i++)
        {
            PhaseConfig.Preset preset = phased.presets.get(i);
            options.add(SelectOption.of(preset.name, "preset:" + i)
                    .withDescription(preset.phases.size() + " fase(s)"));
        }
        options.add(SelectOption.of("➕ Começar do zero", "blank")
                .withDescription("Modo fase sem segmentação, para marcar ao vivo"));
```

O `waitForEvent` passa a esperar `StringSelectInteractionEvent` **e**
`ButtonInteractionEvent` (o "Tocar normalmente" continua botão). Mantenha o timeout de 30s e o
fallback para `loadSingle(track, null)` — o comportamento de expirar não muda.

A duração para o caso `blank` vem de `track.getInfo().length`, disponível aqui.

- [ ] **Step 5: O botão "Tocar" do painel usa o preset selecionado**

Em `PhaseInteractionListener.playTrack`, passe a `Segmentation` do preset em exibição, não
`firstSegmentation()`:

```java
        PhaseConfig.Track track = config.tracks.get(trackIndex);
        int presetIndex = selectedPreset(trackIndex);
        PhaseConfig.Segmentation segmentation = new PhaseConfig.Segmentation(
                track, track.presets.get(presetIndex));

        event.deferReply(true).queue();
        bot.getPhaseService().startAt(event.getGuild(), event.getChannel(),
                segmentation, 0, 0, OutputAdapters.forPhaseDeferred(event));
```

O `0` de duração é seguro aqui porque o painel só oferece "Tocar" para preset com fases; um
preset vazio tocado pelo painel cai na mensagem de erro da Tarefa 3, que é o comportamento
desejado — pelo painel a pessoa deve usar "Adicionar fase" primeiro.

- [ ] **Step 6: Compilar e rodar a suíte**

```bash
javac -nowarn -d "$SCRATCH/out" -cp "$CPW" @"$SCRATCH/srcs2.txt"
javac -nowarn -d "$SCRATCH/out" -cp "$OUTW;$CPW" @"$SCRATCH/test_srcs2.txt"
cd "$SCRATCH/testrun" && for t in audio.SegmentPlayerTest audio.PhaseConfigTest service.PhaseEditingTest service.PresetEditingTest; do
  echo "--- $t"; java -cp "$OUTW;$CPW" RunTests com.jagrosh.jmusicbot.$t 2>&1 | tail -2
done
ls -la --time-style=+%H:%M:%S /c/Users/55519/rpg-player/phases.json
```
Esperado: **71 passando**, `phases.json` intacto.

- [ ] **Step 7: Validar no Discord**

Com o bot parado, reconstrua o jar e confirme:
1. música com 1 preset → dois botões, carrega direto
2. música com 2 presets → menu suspenso com os dois mais "Começar do zero"
3. "Começar do zero" → modal de nome → toca a música inteira em loop
4. "Marcar aqui" durante o modo em branco cria a primeira fase real

---

### Task 6: Comandos de texto e barra

**Files:**
- Modify: `src/main/java/com/jagrosh/jmusicbot/commands/v1/music/PhaseCmd.java`
- Modify: `src/main/java/com/jagrosh/jmusicbot/commands/v2/music/PhaseSlashCmd.java`

**Interfaces:**
- Consumes: `PhaseService.start(...)`, `PresetService` (Tarefas 2-3)
- Produces: nenhum.

- [ ] **Step 1: Aceitar preset na busca**

`!fase <busca>` continua funcionando: sem preset informado, usa o primeiro da faixa. A sintaxe
`!fase <busca> | <preset>` escolhe.

Em `PhaseCmd`, antes de chamar o serviço:

```java
        // "| preset" no fim escolhe a segmentação; sem isso, vale a primeira da faixa
        String preset = null;
        int bar = query.lastIndexOf('|');
        if (bar >= 0)
        {
            preset = query.substring(bar + 1).trim();
            query = query.substring(0, bar).trim();
        }
```

Some ao texto de `help` da classe, no mesmo tom das opções já listadas:

```
        + "\n`<busca> | <preset>` — escolhe a segmentação, quando a música tem mais de uma"
```

Em `PhaseSlashCmd`, adicione a opção ao lado das existentes:

```java
        options.add(new OptionData(OptionType.STRING, "preset",
                "Qual segmentação usar (padrão: a primeira da faixa)", false));
```

e leia com `event.getOption("preset", null, OptionMapping::getAsString)`.

- [ ] **Step 2: Compilar e rodar a suíte**

```bash
javac -nowarn -d "$SCRATCH/out" -cp "$CPW" @"$SCRATCH/srcs2.txt"
javac -nowarn -d "$SCRATCH/out" -cp "$OUTW;$CPW" @"$SCRATCH/test_srcs2.txt"
cd "$SCRATCH/testrun" && for t in audio.SegmentPlayerTest audio.PhaseConfigTest service.PhaseEditingTest service.PresetEditingTest; do
  echo "--- $t"; java -cp "$OUTW;$CPW" RunTests com.jagrosh.jmusicbot.$t 2>&1 | tail -2
done
ls -la --time-style=+%H:%M:%S /c/Users/55519/rpg-player/phases.json
```
Esperado: **71 passando**, `phases.json` intacto.

- [ ] **Step 3: Fechamento**

Peça ao usuário para parar o bot. Só então:

```bash
export JAVA_HOME="C:\Program Files\Java\jdk-26.0.1" && export PATH="$JAVA_HOME/bin:$PATH"
"/c/Users/55519/tools/apache-maven-3.9.9/bin/mvn.cmd" -q -o -DskipTests package
ls -la --time-style=+%H:%M:%S target/JMusicBot-0.7.0-All.jar
```

Na primeira execução do bot com o jar novo, confirme que `phases.json.bak` apareceu e que as
4 faixas continuam com suas fases e aliases.
