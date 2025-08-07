package io.github.mcengine.extension.addon.translator.tabcompleter;

import io.github.mcengine.extension.addon.translator.lang.LangRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Static tab completion for /translator.
 */
public final class TranslatorTabCompleter {

    private TranslatorTabCompleter() {}

    public static List<String> complete(String[] args, LangRegistry registry) {
        final List<String> out = new ArrayList<>(16);
        if (args.length == 0) return out;
        if (args.length == 1) {
            final String p = args[0].toLowerCase(Locale.ROOT);
            if ("set".startsWith(p)) out.add("set");
            if ("get".startsWith(p)) out.add("get");
            if ("clear".startsWith(p)) out.add("clear");
            return out;
        }
        if (args.length == 2 && "set".equalsIgnoreCase(args[0])) {
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
        return out;
    }
}
