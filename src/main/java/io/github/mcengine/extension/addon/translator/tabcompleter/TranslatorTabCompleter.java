package io.github.mcengine.extension.addon.translator.tabcompleter;

import io.github.mcengine.extension.addon.translator.lang.LangRegistry;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Static tab completion for /translator.
 */
public final class TranslatorTabCompleter {

    private TranslatorTabCompleter() {}

    /**
     * Produces completion suggestions for {@code /translator}.
     * Shows {@code mode} only to OP players; {@code test} completes for anyone once {@code mode} is typed.
     *
     * @param sender   the command sender (to check OP)
     * @param args     current args
     * @param registry language registry for code listing
     * @return suggestions
     */
    public static List<String> complete(CommandSender sender, String[] args, LangRegistry registry) {
        final List<String> out = new ArrayList<>(16);
        final boolean isOp = (sender instanceof Player) && ((Player) sender).isOp();

        if (args.length == 0) return out;

        if (args.length == 1) {
            final String p = args[0].toLowerCase(Locale.ROOT);
            if ("set".startsWith(p)) out.add("set");
            if ("get".startsWith(p)) out.add("get");
            if ("clear".startsWith(p)) out.add("clear");
            // Gate only "mode" behind OP
            if (isOp && "mode".startsWith(p)) out.add("mode");
            return out;
        }

        if (args.length == 2) {
            // set <lang>
            if ("set".equalsIgnoreCase(args[0])) {
                final String p = args[1].toLowerCase(Locale.ROOT);
                int added = 0;
                for (String c : registry.codes()) {
                    if (c.startsWith(p)) {
                        out.add(c);
                        if (++added >= 50) break;
                    }
                }
                return out;
            }
            // mode test — "test" is visible to anyone once "mode" is typed
            if ("mode".equalsIgnoreCase(args[0])) {
                final String p = args[1].toLowerCase(Locale.ROOT);
                if ("test".startsWith(p)) out.add("test");
                return out;
            }
        }
        return out;
    }
}
