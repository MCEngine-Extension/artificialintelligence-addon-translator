package io.github.mcengine.extension.addon.translator;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tab completer for the /aiaddonexample command.
 */
public class TranslatorTabCompleter implements TabCompleter {

    /**
     * Provides tab-completion for the /aiaddonexample command.
     *
     * @param sender  The command sender.
     * @param command The command object.
     * @param alias   The alias used.
     * @param args    The command arguments.
     * @return A list of completion strings.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("start", "stop", "status");
        }
        return Collections.emptyList();
    }
}
