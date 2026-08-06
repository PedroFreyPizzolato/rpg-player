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
