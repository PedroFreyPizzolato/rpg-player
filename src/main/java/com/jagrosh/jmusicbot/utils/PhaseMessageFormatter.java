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
package com.jagrosh.jmusicbot.utils;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.NowPlayingInfo;
import com.jagrosh.jmusicbot.audio.PhaseConfig;
import com.jagrosh.jmusicbot.audio.SegmentPlayer;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.util.ArrayList;
import java.util.List;

/**
 * As telas do modo fase: o now-playing enquanto um segmento está em loop, e o painel de
 * edição das segmentações.
 *
 * <p>Os ids de componente seguem o mesmo esquema do resto do bot (prefixo + ação, separados
 * por {@code :}), para o {@code PhaseInteractionListener} rotear.
 */
public class PhaseMessageFormatter
{
    public static final String PREFIX = "phase:";
    /** Limite do Discord para opções de um select menu. */
    public static final int MAX_SELECT_OPTIONS = 25;

    private static final int BAR_SLOTS = 12;

    // ── now playing do modo fase ─────────────────────────────────────────────

    public static MessageCreateData buildPhaseNowPlaying(Bot bot, NowPlayingInfo info)
    {
        SegmentPlayer player = info.segmentPlayer;
        MessageCreateBuilder mb = new MessageCreateBuilder();
        mb.setEmbeds(phaseEmbed(bot, info, player));
        mb.setComponents(phaseComponents(player));
        return mb.build();
    }

    private static net.dv8tion.jda.api.entities.MessageEmbed phaseEmbed(Bot bot, NowPlayingInfo info,
                                                                        SegmentPlayer player)
    {
        PhaseConfig.Segmentation segmentation = player.getSegmentation();
        PhaseConfig.Phase phase = segmentation.phases().get(player.getPhaseIndex());

        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(info.guild.getSelfMember().getColors().getPrimary());
        eb.setAuthor("Modo fase", null, info.guild.getIconUrl());
        eb.setTitle(FormatUtil.filter(segmentation.trackName()));

        String status = player.isPaused() ? AudioHandler.PAUSE_EMOJI : AudioHandler.PLAY_EMOJI;
        long position = player.getPositionMs();
        eb.setDescription(status + " " + phaseBar(phase, position) + " `["
                + TimeUtil.formatTime(position) + "]` " + FormatUtil.volumeIcon(info.volume));

        eb.addField("Fase", "`" + phase.name + "` (" + (player.getPhaseIndex() + 1) + "/"
                + segmentation.phases().size() + ")", true);
        eb.addField("Trecho", TimeUtil.formatTime(phase.startMs()) + " – "
                + TimeUtil.formatTime(phase.endMs()), true);
        eb.addField("Volume", info.volume + "%", true);

        String state;
        if (player.isBridging())
            state = "▶ tocando a passagem — não volta mais para esta fase";
        else if (player.isUnlocked())
            state = "⏩ liberado: ao fim do trecho a música segue adiante";
        else
            state = "🔁 em loop até liberarem a continuação";
        eb.addField("Estado", state, false);

        eb.setFooter(segmentation.phases().size() + " fases • edite com o botão Fases");
        return eb.build();
    }

    /** Barra de progresso dentro da fase; fora dos limites dela vira a passagem. */
    private static String phaseBar(PhaseConfig.Phase phase, long positionMs)
    {
        long span = Math.max(1, phase.endMs() - phase.startMs());
        double progress = (positionMs - phase.startMs()) / (double) span;
        progress = Math.max(0, Math.min(1, progress));
        int filled = (int) Math.round(progress * BAR_SLOTS);

        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < BAR_SLOTS; i++)
            bar.append(i == filled ? "🔘" : "▬");   // 🔘 e ▬
        return bar.toString();
    }

    private static List<ActionRow> phaseComponents(SegmentPlayer player)
    {
        List<PhaseConfig.Phase> phases = player.getSegmentation().phases();
        boolean bridging = player.isBridging();

        Button pause = player.isPaused()
                ? Button.primary(id("pause"), "Retomar").withEmoji(Emoji.fromUnicode("▶"))
                : Button.primary(id("pause"), "Pausar").withEmoji(Emoji.fromUnicode("⏸"));

        Button next = (player.isUnlocked() || bridging
                ? Button.success(id("next"), player.isOnLastPhase() ? "Até o fim" : "Liberado")
                : Button.secondary(id("next"), player.isOnLastPhase() ? "Tocar até o fim" : "Liberar fase"))
                .withEmoji(Emoji.fromUnicode("⏩"))            // ⏩
                .withDisabled(player.isUnlocked() || bridging);

        List<ActionRow> rows = new ArrayList<>();
        rows.add(ActionRow.of(
                pause,
                next,
                Button.danger(id("stop"), "Parar").withEmoji(Emoji.fromUnicode("⏹")),
                Button.secondary(id("mark"), "Marcar aqui").withEmoji(Emoji.fromUnicode("📍"))
        ));

        // pular direto para qualquer fase, sem passar pelas anteriores
        List<SelectOption> options = new ArrayList<>();
        for (int i = 0; i < Math.min(phases.size(), MAX_SELECT_OPTIONS); i++)
        {
            PhaseConfig.Phase phase = phases.get(i);
            options.add(SelectOption.of(cut(phase.name, 100), String.valueOf(i))
                    .withDescription(TimeUtil.formatTime(phase.startMs()) + " – "
                            + TimeUtil.formatTime(phase.endMs()))
                    .withDefault(i == player.getPhaseIndex()));
        }
        rows.add(ActionRow.of(StringSelectMenu.create(id("jump"))
                .setPlaceholder("Pular para uma fase...")
                .addOptions(options)
                .build()));

        rows.add(ActionRow.of(openPanelButton(), switchToNormalButton()));
        return rows;
    }

    /** Sai do modo fase seguindo a música de onde ela está, sem cortar o áudio. */
    public static Button switchToNormalButton()
    {
        return Button.primary(id("tonormal"), "Modo normal").withEmoji(Emoji.fromUnicode("🎵"));
    }

    /** O inverso: assume o modo fase a partir do ponto que a música normal já alcançou. */
    public static Button switchToPhasesButton()
    {
        return Button.success(id("tophases"), "Modo fase").withEmoji(Emoji.fromUnicode("🔁"));
    }

    // ── painel de edição ─────────────────────────────────────────────────────

    /** Botão que abre o painel de fases a partir do now-playing normal. */
    public static Button openPanelButton()
    {
        return Button.secondary(id("panel"), "Fases").withEmoji(Emoji.fromUnicode("🎬"));
    }

    /**
     * O painel do editor. As faixas e os presets são referenciados pelo índice no arquivo, não
     * pelo nome: o id de componente do Discord tem 100 caracteres e nome de faixa pode passar
     * disso (ou conter o separador). O painel é redesenhado depois de cada alteração, então o
     * índice nunca fica velho na prática.
     */
    public static MessageEditData buildPanel(PhaseConfig config, int selectedTrack, int selectedPreset,
                                             String notice)
    {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("🎬 Segmentações");

        PhaseConfig.Track track = selectedTrack >= 0 && selectedTrack < config.tracks.size()
                ? config.tracks.get(selectedTrack)
                : null;
        // um índice velho cai no primeiro preset, então o painel e os ids que ele monta precisam
        // concordar sobre qual preset é esse — daí o índice sair do objeto já resolvido
        PhaseConfig.Preset preset = track == null ? null : track.presetAt(selectedPreset);
        int presetIndex = preset == null ? 0 : track.presets.indexOf(preset);

        if (config.tracks.isEmpty())
            eb.setDescription("Nenhuma faixa cadastrada ainda. Use **➕ Nova faixa**, na lista de"
                    + " faixas, para criar a primeira: informe o nome e a URL (ou caminho do arquivo).");
        else if (track == null)
            eb.setDescription("Escolha uma faixa abaixo para ver e editar as fases dela.");
        else
            eb.setDescription(describeTrack(track, preset, presetIndex));

        if (notice != null && !notice.isEmpty())
            eb.setFooter(notice);

        MessageEditBuilder mb = new MessageEditBuilder();
        mb.setEmbeds(eb.build());
        mb.setComponents(panelComponents(config, selectedTrack, track, preset, presetIndex));
        return mb.build();
    }

    private static String describeTrack(PhaseConfig.Track track, PhaseConfig.Preset preset, int presetIndex)
    {
        List<PhaseConfig.Phase> phases = preset == null ? List.of() : preset.phases;
        StringBuilder sb = new StringBuilder();
        sb.append("**Fonte:** `").append(cut(track.identifier() == null ? "—" : track.identifier(), 300))
                .append("`\n");

        // de qual das segmentações da faixa são as fases listadas abaixo: o select mostra o nome
        // do preset escolhido, mas não que existem outros ao lado dele
        if (preset == null)
            sb.append("**Preset:** _nenhum_ — crie um para poder guardar fases.\n");
        else
            sb.append("**Preset:** `").append(cut(preset.name, 100)).append("` (")
                    .append(presetIndex + 1).append("/").append(track.presets.size()).append(")\n");

        if (track.aliases.isEmpty())
            sb.append("_Toca a mesma música por outro link (YouTube Music, outra URL)? Dê play nela e"
                    + " use **Vincular fonte atual** para que o bot reconheça as duas como a mesma faixa._\n\n");
        else
        {
            sb.append("**Outras fontes vinculadas** (reconhecidas como a mesma faixa):\n");
            for (String alias : track.aliases)
                sb.append("`").append(cut(alias, 300)).append("`\n");
            sb.append("\n");
        }

        if (phases.isEmpty())
        {
            sb.append("_Sem fases. Use **Adicionar fase**._");
            return sb.toString();
        }

        for (int i = 0; i < phases.size(); i++)
        {
            PhaseConfig.Phase phase = phases.get(i);
            sb.append("`").append(i + 1).append(".` **").append(phase.name).append("** — ")
                    .append(TimeUtil.formatTime(phase.startMs())).append(" → ")
                    .append(TimeUtil.formatTime(phase.endMs()))
                    .append(" · fade ").append(trim(phase.fadeMs(PhaseConfig.DEFAULT_FADE_MS) / 1000.0))
                    .append("s").append(phase.fade == null ? "*" : "");

            if (i + 1 < phases.size())
            {
                long gap = phases.get(i + 1).startMs() - phase.endMs();
                if (gap > 0)
                    sb.append("  _(+").append(TimeUtil.formatTime(gap)).append(" de passagem)_");
            }
            sb.append("\n");
        }

        if (phases.stream().anyMatch(phase -> phase.fade == null))
            sb.append("\n_* fade no padrão. Edite a fase para dar um valor próprio a ela._");
        return sb.toString();
    }

    /**
     * As cinco linhas do painel. O Discord só aceita cinco {@code ActionRow} por mensagem, e
     * faixa escolhida já ocupa as cinco — por isso "nova faixa" e as ações de preset viajam como
     * opções no fim dos próprios selects, em vez de virarem uma sexta linha de botões.
     */
    private static List<ActionRow> panelComponents(PhaseConfig config, int trackIndex,
                                                   PhaseConfig.Track track, PhaseConfig.Preset preset,
                                                   int presetIndex)
    {
        List<ActionRow> rows = new ArrayList<>();
        // faixa e preset acompanham cada componente: é por eles que a interação seguinte sabe o
        // que o painel estava mostrando quando o mestre clicou
        String scope = ":" + trackIndex + ":" + presetIndex;

        List<SelectOption> tracks = new ArrayList<>();
        for (int i = 0; i < Math.min(config.tracks.size(), MAX_SELECT_OPTIONS - 1); i++)
        {
            PhaseConfig.Track option = config.tracks.get(i);
            tracks.add(SelectOption.of(cut(option.name, 100), String.valueOf(i))
                    .withDescription(option.presets.size() + " preset(s)")
                    .withDefault(i == trackIndex));
        }
        tracks.add(SelectOption.of("➕ Nova faixa", "newtrack")
                .withDescription("Cadastrar outra música"));
        rows.add(ActionRow.of(StringSelectMenu.create(id("selecttrack"))
                .setPlaceholder("Escolha uma faixa...")
                .addOptions(tracks)
                .build()));

        if (track == null)
        {
            // sem faixa escolhida a linha de ações não existe, e é lá que o Atualizar mora
            rows.add(ActionRow.of(Button.secondary(id("refresh" + scope), "Atualizar")
                    .withEmoji(Emoji.fromUnicode("🔄"))));
            return rows;
        }

        List<PhaseConfig.Phase> trackPhases = preset == null ? List.of() : preset.phases;
        boolean hasPhases = !trackPhases.isEmpty();

        List<SelectOption> presets = new ArrayList<>();
        for (int i = 0; i < Math.min(track.presets.size(), MAX_SELECT_OPTIONS - 3); i++)
        {
            PhaseConfig.Preset option = track.presets.get(i);
            presets.add(SelectOption.of(cut(option.name, 100), String.valueOf(i))
                    .withDescription(option.phases.size() + " fase(s)")
                    .withDefault(i == presetIndex));
        }
        presets.add(SelectOption.of("➕ Novo preset", "new")
                .withDescription("Do zero, ou copiando as fases de outro"));
        presets.add(SelectOption.of("✏ Renomear este preset", "rename"));
        presets.add(SelectOption.of("🗑 Excluir este preset", "delete"));
        rows.add(ActionRow.of(StringSelectMenu.create(id("selectpreset" + scope))
                .setPlaceholder(preset == null
                        ? "Nenhum preset — crie um aqui" : "Preset: " + cut(preset.name, 80))
                .addOptions(presets)
                .build()));

        rows.add(ActionRow.of(
                Button.success(id("add" + scope), "Adicionar fase")
                        .withEmoji(Emoji.fromUnicode("➕")),
                Button.primary(id("play" + scope), "Tocar")
                        .withEmoji(Emoji.fromUnicode("▶"))
                        .withDisabled(!hasPhases),
                Button.secondary(id("editsrc" + scope), "Editar fonte")
                        .withEmoji(Emoji.fromUnicode("🔗")),
                Button.secondary(id("linksrc" + scope), "Vincular fonte atual")
                        .withEmoji(Emoji.fromUnicode("📎")),
                Button.secondary(id("refresh" + scope), "Atualizar")
                        .withEmoji(Emoji.fromUnicode("🔄"))
        ));

        if (hasPhases)
        {
            List<SelectOption> phases = new ArrayList<>();
            for (int i = 0; i < Math.min(trackPhases.size(), MAX_SELECT_OPTIONS); i++)
            {
                PhaseConfig.Phase phase = trackPhases.get(i);
                phases.add(SelectOption.of(cut(phase.name, 100), String.valueOf(i))
                        .withDescription(TimeUtil.formatTime(phase.startMs()) + " – "
                                + TimeUtil.formatTime(phase.endMs())));
            }
            rows.add(ActionRow.of(StringSelectMenu.create(id("editphase" + scope))
                    .setPlaceholder("Editar uma fase...")
                    .addOptions(phases)
                    .build()));
            rows.add(ActionRow.of(StringSelectMenu.create(id("delphase" + scope))
                    .setPlaceholder("Excluir uma fase...")
                    .addOptions(phases)
                    .build()));
        }
        return rows;
    }

    // ── modais ───────────────────────────────────────────────────────────────

    /**
     * Modal de fase. {@code phaseIndex} negativo cria uma nova; os valores pré-preenchidos
     * vêm em segundos, o mesmo formato em que o arquivo guarda.
     */
    public static Modal phaseModal(int trackIndex, int presetIndex, int phaseIndex,
                                   PhaseConfig.Phase existing,
                                   double suggestedStart, double suggestedEnd)
    {
        boolean creating = phaseIndex < 0;
        TextInput name = TextInput.create("name", TextInputStyle.SHORT)
                .setPlaceholder("Nome da fase")
                .setValue(existing != null ? existing.name : null)
                .setRequired(true)
                .build();
        TextInput start = TextInput.create("start", TextInputStyle.SHORT)
                .setPlaceholder("Ex: 66.5 ou 1:06")
                .setValue(existing != null ? trim(existing.start) : trim(suggestedStart))
                .setRequired(true)
                .build();
        TextInput end = TextInput.create("end", TextInputStyle.SHORT)
                .setPlaceholder("Ex: 118 ou 1:58")
                .setValue(existing != null ? trim(existing.end) : trim(suggestedEnd))
                .setRequired(true)
                .build();
        String defaultFade = trim(PhaseConfig.DEFAULT_FADE_MS / 1000.0);
        TextInput fade = TextInput.create("fade", TextInputStyle.SHORT)
                .setPlaceholder("Vazio = padrão (" + defaultFade + "s)")
                .setValue(existing != null && existing.fade != null ? trim(existing.fade) : null)
                .setRequired(false)
                .build();

        return Modal.create(id("phasemodal:" + trackIndex + ":" + presetIndex + ":" + phaseIndex),
                        creating ? "Nova fase" : "Editar fase")
                .addComponents(
                        Label.of("Nome", name),
                        Label.of("Início", "Segundos (66.5) ou mm:ss (1:06)", start),
                        Label.of("Fim", "Segundos (118) ou mm:ss (1:58)", end),
                        Label.of("Fade do loop", "Segundos de crossfade ao repetir esta fase."
                                + " Vazio = padrão (" + defaultFade + "s), 0 = corte seco", fade))
                .build();
    }

    public static Modal trackModal(int trackIndex, int presetIndex, String existingName, String existingSource)
    {
        boolean creating = trackIndex < 0;
        TextInput name = TextInput.create("name", TextInputStyle.SHORT)
                .setPlaceholder("Nome da faixa")
                .setValue(existingName)
                .setRequired(true)
                .build();
        TextInput source = TextInput.create("source", TextInputStyle.SHORT)
                .setPlaceholder("URL do YouTube ou caminho do arquivo")
                .setValue(existingSource)
                .setRequired(true)
                .build();

        return Modal.create(id("trackmodal:" + trackIndex + ":" + presetIndex),
                        creating ? "Nova faixa" : "Editar fonte")
                .addComponents(Label.of("Nome", name),
                        Label.of("Fonte principal", "Outras fontes da mesma música se vinculam"
                                + " depois, pelo botão \"Vincular fonte atual\" no painel", source))
                .build();
    }

    /**
     * Criar e renomear usam o mesmo modal. O campo "copiar de" só aparece ao criar — é a
     * ausência dele que o listener usa para saber qual das duas ações executar.
     */
    public static Modal presetModal(int trackIndex, int presetIndex, String currentName)
    {
        boolean renaming = currentName != null;
        TextInput name = TextInput.create("name", TextInputStyle.SHORT)
                .setValue(currentName)
                .setPlaceholder("Combate, Exploração, Tensão...")
                .setRequired(true)
                .build();

        // a ação vai no id, e não é deduzida da ausência do campo "copiar de": campo opcional
        // vazio pode voltar do Discord como string vazia, e confundir criar com renomear
        // trocaria o nome do preset em vez de fazer um novo
        Modal.Builder modal = Modal.create(
                        id("presetmodal:" + trackIndex + ":" + presetIndex
                                + (renaming ? ":rename" : ":new")),
                        renaming ? "Renomear preset" : "Novo preset")
                .addComponents(Label.of("Nome", name));

        if (!renaming)
        {
            TextInput copyFrom = TextInput.create("copyfrom", TextInputStyle.SHORT)
                    .setPlaceholder("Nome do preset a copiar — vazio começa do zero")
                    .setRequired(false)
                    .build();
            modal.addComponents(Label.of("Copiar de", "Nome de um preset desta faixa, para começar"
                    + " com as fases dele já prontas", copyFrom));
        }
        return modal.build();
    }

    /**
     * A pergunta de qual segmentação usar ao trocar para o modo fase com a música tocando.
     * Só aparece quando a faixa tem mais de uma — com uma só não há escolha a fazer.
     */
    public static MessageCreateData buildSwitchPresetPrompt(PhaseConfig.Track track)
    {
        List<SelectOption> options = new ArrayList<>();
        for (int i = 0; i < Math.min(track.presets.size(), MAX_SELECT_OPTIONS); i++)
        {
            PhaseConfig.Preset preset = track.presets.get(i);
            options.add(SelectOption.of(cut(preset.name, 100), preset.name)
                    .withDescription(preset.phases.size() + " fase(s)"));
        }

        return new MessageCreateBuilder()
                .setContent("🔁 **" + FormatUtil.filter(track.name) + "** tem "
                        + track.presets.size() + " segmentações. Qual usar?")
                .setComponents(ActionRow.of(StringSelectMenu.create(id("switchpreset"))
                        .setPlaceholder("Escolher segmentação...")
                        .addOptions(options)
                        .build()))
                .build();
    }

    /** Onde guardar a posição marcada: início ou fim de qual fase, ou uma fase nova. */
    public static MessageCreateData buildMarkPrompt(SegmentPlayer player, long positionMs)
    {
        List<PhaseConfig.Phase> phases = player.getSegmentation().phases();
        List<SelectOption> options = new ArrayList<>();
        options.add(SelectOption.of("➕ Criar fase nova começando aqui", "new")
                .withDescription("Dura 30s até você ajustar o fim"));

        // duas opções por fase (início e fim) dentro do teto de 25 do Discord
        int limit = Math.min(phases.size(), (MAX_SELECT_OPTIONS - 1) / 2);
        for (int i = 0; i < limit; i++)
        {
            PhaseConfig.Phase phase = phases.get(i);
            options.add(SelectOption.of(cut("⏮ Início de " + phase.name, 100), "start:" + i)
                    .withDescription("Era " + TimeUtil.formatTime(phase.startMs())));
            options.add(SelectOption.of(cut("⏭ Fim de " + phase.name, 100), "end:" + i)
                    .withDescription("Era " + TimeUtil.formatTime(phase.endMs())));
        }

        return new MessageCreateBuilder()
                .setContent("📍 Posição marcada: **" + TimeUtil.formatTime(positionMs)
                        + "**. Onde aplicar?")
                .setComponents(ActionRow.of(StringSelectMenu.create(id("markto:" + positionMs))
                        .setPlaceholder("Aplicar a...")
                        .addOptions(options)
                        .build()))
                .build();
    }

    // ── util ─────────────────────────────────────────────────────────────────

    static String id(String action)
    {
        return PREFIX + action;
    }

    /** Segundos sem o ".0" pendurado, que é como o arquivo guarda. */
    private static String trim(double seconds)
    {
        return seconds == Math.rint(seconds)
                ? String.valueOf((long) seconds)
                : String.valueOf(Math.round(seconds * 100) / 100.0);
    }

    private static String cut(String value, int max)
    {
        if (value == null)
            return "—";
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
