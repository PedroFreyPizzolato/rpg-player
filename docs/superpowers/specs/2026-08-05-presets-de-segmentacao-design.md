# Presets de segmentação por música

Uma música pode ter mais de uma segmentação salva. Quem dá play escolhe qual usar.

## Problema

Hoje cada faixa do `phases.json` tem uma lista única de fases. Segmentar a mesma música de
outro jeito — um corte para combate, outro para exploração — exige sobrescrever a anterior.

## Modelo de dados

As fases descem um nível. Nome, fonte e aliases ficam onde estão: são propriedades da música,
não da segmentação.

```json
{ "tracks": [ {
    "name": "Watch the Crown Fall",
    "source": "https://youtu.be/gV_uJpcuq5U",
    "aliases": [ "https://music.youtube.com/watch?v=w9ZM-7VzQvc" ],
    "presets": [
      { "name": "Padrão", "phases": [
          { "name": "Inicio", "start": 10.0, "end": 66.0, "fade": 0.5 } ] },
      { "name": "Exploração", "phases": [ ] }
    ] } ] }
```

```java
public static class Preset
{
    public String name;
    public List<Phase> phases = new ArrayList<>();
}

public static class Track
{
    public String name, source, file;
    public List<String> aliases = new ArrayList<>();
    public List<Preset> presets = new ArrayList<>();
    /** Formato antigo; migrado no load e nunca gravado de volta. */
    public List<Phase> phases;
}
```

### Migração

Em `PhaseConfig.load()`: faixa com `phases` preenchido e `presets` vazio vira um preset
chamado `Padrão` com aquelas fases, e `phases` é anulado. Com `@JsonInclude(NON_NULL)` o campo
antigo deixa de ser gravado.

Na primeira gravação após migrar, o arquivo original é copiado para `phases.json.bak` — uma
vez só, sem sobrescrever um `.bak` existente. Este projeto já perdeu o `phases.json` uma vez;
a cópia é o custo mínimo de não repetir isso.

Faixa cujo `presets` já vem preenchido não é tocada.

### Faixa nova

Criar uma faixa pelo painel cria junto um preset `Padrão` vazio. Assim nunca existe faixa sem
preset, e "Adicionar fase" sempre tem destino — sem um caso especial de "primeira fase cria o
preset". A detecção de modo fase exige pelo menos um preset; um preset vazio é tocável (fase
implícita), então a faixa aparece na oferta desde que exista.

### Como as fases circulam

`SegmentPlayer` e `PhaseService` precisam da faixa (fonte, nome) e do preset (fases). Um
envelope resolvido carrega os dois, em vez de espalhar o par por seis assinaturas:

```java
public static class Segmentation      // resolvida em memória, não serializada
{
    public final Track track;
    public final Preset preset;
    public List<Phase> phases() { return preset.phases; }
    public String identifier()  { return track.identifier(); }
}
```

`SegmentPlayer` recebe `Segmentation` no lugar de `Track`. Dentro dele é troca mecânica:
`track.phases` vira `segmentation.phases()`.

## Fluxo de play

Um formato só, independente da quantidade de presets:

```
🔁 Watch the Crown Fall tem 2 segmentações. Como tocar?

[ Escolher segmentação...                    ▾ ]
     Combate       — 3 fases
     Exploração    — 2 fases
     ➕ Começar do zero
[ Tocar normalmente ]
```

Com **exatamente 1 preset** a oferta não muda: `[Tocar normalmente] [Tocar com Fases]`, e o
único preset carrega direto. "Começar do zero" fica acessível pelo painel nesse caso.

### Preset vazio

"Começar do zero" abre o modal de preset novo, cria vazio e entra em modo fase.

Um preset sem fases toca como **uma fase implícita `[0:00, fim da música]` em loop**. A fase
implícita existe só em memória — não é gravada. Ela desaparece assim que a primeira fase real
é marcada. Isso dá ao "Marcar aqui" um destino desde o primeiro clique, sem estado temporário
paralelo.

A duração vem de `AudioTrack.getInfo().length`, disponível no ponto onde a oferta é montada.

## Painel de edição

O Discord permite 5 linhas de componentes e o painel já usa as 5. Em vez de cortar
funcionalidade, os dois selects passam a carregar também suas ações:

```
┌ Faixa:   [ Watch the Crown Fall            ▾ ]   + "➕ Nova faixa"
├ Preset:  [ Combate                         ▾ ]   + "➕ Novo" "✏ Renomear" "🗑 Excluir"
├ [Adicionar fase] [Tocar] [Editar fonte] [Vincular fonte] [Atualizar]
├ Editar fase:  [ 2. Pre drop                ▾ ]
└ Apagar fase:  [ 3. Drop                    ▾ ]
```

Cabe em 5 linhas sem remover nada. A única regressão de ergonomia é "Nova faixa", que deixa de
ser botão e vira item no fim do select de faixas.

Ações de preset:

| ação | comportamento |
|---|---|
| ➕ Novo | modal: nome + "copiar de *Combate*" ou "começar vazio" |
| ✏ Renomear | modal com o nome atual preenchido |
| 🗑 Excluir | recusa se for o último preset da faixa |

Todas as edições de fase (adicionar, editar, apagar, marcar ao vivo) passam a operar sobre o
preset selecionado.

## Erros

Recusas explícitas, no padrão de mensagem já usado pelo serviço:

- excluir o último preset de uma faixa — deixaria a música sem segmentação e fora da detecção
- nome de preset vazio
- nome de preset repetido dentro da mesma faixa
- tocar ou editar um preset que sumiu do arquivo entre o menu abrir e o clique

## Testes

O foco é onde o erro é silencioso — não estoura exceção, só corrompe dados ou o áudio.

**Migração**
- arquivo legado vira um preset `Padrão` preservando fase, fade e alias
- arquivo já migrado não é alterado numa segunda leitura
- `.bak` é escrito uma vez e não sobrescreve um `.bak` existente

**Isolamento entre presets** — o erro mais provável desta feature
- editar uma fase no preset A não altera o preset B
- duplicar um preset produz fases independentes, não as mesmas instâncias

**Preset vazio**
- toca como fase implícita `[0, fim]`
- marcar a primeira fase substitui a implícita e grava no arquivo

**Play**
- 1 preset carrega direto, sem perguntar
- 2+ presets abrem o menu com todos, mais "Começar do zero"

Os testes existentes de `SegmentPlayer` passam a construir `Segmentation` em vez de `Track`.
É troca mecânica: nenhum comportamento de áudio muda, então nenhuma asserção muda.

## Fora de escopo

- Compartilhar preset entre músicas diferentes
- Reordenar presets
- Preset padrão marcado por faixa (com 2+ presets sempre pergunta)
