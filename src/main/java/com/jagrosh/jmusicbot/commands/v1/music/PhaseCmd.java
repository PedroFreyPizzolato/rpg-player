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
package com.jagrosh.jmusicbot.commands.v1.music;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v1.MusicCommand;
import com.jagrosh.jmusicbot.commands.v1.TextOutputAdapters.SimpleOutputAdapter;
import com.jagrosh.jmusicbot.service.PhaseService;

/**
 * Toca uma faixa em fases, loopando cada uma até liberarem a próxima.
 */
public class PhaseCmd extends MusicCommand
{
    private final PhaseService phaseService;

    public PhaseCmd(Bot bot)
    {
        super(bot);
        this.phaseService = bot.getPhaseService();
        this.name = "fase";
        this.help = "toca uma faixa em fases, loopando cada uma";
        this.arguments = "<nome da faixa | next | stop | normal | list>";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = true;
        this.bePlaying = false;
    }

    @Override
    public void doCommand(CommandEvent event)
    {
        String args = event.getArgs().trim();
        SimpleOutputAdapter output = new SimpleOutputAdapter(event);

        if (args.isEmpty() || args.equalsIgnoreCase("painel") || args.equalsIgnoreCase("panel"))
            event.getChannel().sendMessage(phaseService.buildPanelMessage()).queue();
        else if (args.equalsIgnoreCase("list"))
            phaseService.list(event.getGuild(), output);
        else if (args.equalsIgnoreCase("next"))
            phaseService.next(event.getGuild(), output);
        else if (args.equalsIgnoreCase("stop"))
            phaseService.stop(event.getGuild(), output);
        else if (args.equalsIgnoreCase("normal"))
            phaseService.switchToNormal(event.getGuild(), event.getTextChannel(), output);
        else
            phaseService.start(event.getGuild(), event.getTextChannel(), args, output);
    }
}
