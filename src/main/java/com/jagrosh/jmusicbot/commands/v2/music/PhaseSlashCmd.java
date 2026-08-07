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
package com.jagrosh.jmusicbot.commands.v2.music;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v2.MusicSlashCommand;
import com.jagrosh.jmusicbot.commands.v2.SlashOutputAdapters.SlashEventOutputAdapter;
import com.jagrosh.jmusicbot.service.PhaseService;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Collections;

/**
 * Versão slash do {@link com.jagrosh.jmusicbot.commands.v1.music.PhaseCmd}.
 */
public class PhaseSlashCmd extends MusicSlashCommand
{
    private final PhaseService phaseService;

    public PhaseSlashCmd(Bot bot)
    {
        super(bot);
        this.phaseService = bot.getPhaseService();
        this.name = "fase";
        this.help = "toca uma faixa em fases, loopando cada uma";
        this.options = java.util.List.of(
                new OptionData(OptionType.STRING, "acao", "nome da faixa, ou next / stop / normal / list", false),
                new OptionData(OptionType.STRING, "preset", "qual segmentação usar (padrão: a primeira da faixa)", false)
        );
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = true;
        this.bePlaying = false;
    }

    @Override
    public void doCommand(SlashCommandEvent event)
    {
        OptionMapping option = event.getOption("acao");
        String args = option == null ? "" : option.getAsString().trim();
        SlashEventOutputAdapter output = new SlashEventOutputAdapter(event);

        if (args.isEmpty() || args.equalsIgnoreCase("painel") || args.equalsIgnoreCase("panel"))
            event.reply(phaseService.buildPanelMessage()).setEphemeral(true).queue();
        else if (args.equalsIgnoreCase("list"))
            phaseService.list(event.getGuild(), output);
        else if (args.equalsIgnoreCase("next"))
            phaseService.next(event.getGuild(), output);
        else if (args.equalsIgnoreCase("stop"))
            phaseService.stop(event.getGuild(), output);
        else if (args.equalsIgnoreCase("normal"))
            phaseService.switchToNormal(event.getGuild(), event.getChannel(), output);
        else
        {
            OptionMapping preset = event.getOption("preset");
            phaseService.start(event.getGuild(), event.getChannel(), args,
                    preset == null ? null : preset.getAsString().trim(), output);
        }
    }
}
