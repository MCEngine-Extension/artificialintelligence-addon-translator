package io.github.mcengine.extension.addon.translator.command;

import io.github.mcengine.extension.addon.translator.lang.LangRegistry;
import io.github.mcengine.extension.addon.translator.player.PlayerLangStore;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Handles the {@code /translator} command with subcommands:
 * <ul>
 *     <li>{@code set &lt;lang&gt;} — sets player's language (ISO 639-1)</li>
 *     <li>{@code get} — shows current player's language</li>
 *     <li>{@code clear} — clears player's language</li>
 * </ul>
 * Validates language codes via {@link LangRegistry}.
 */
public final class TranslatorCommand implements CommandExecutor {

    /** Usage message shown when arguments are missing or invalid. */
    private static final String USAGE = ChatColor.YELLOW + "Usage: /translator set <lang> | /translator get | /translator clear";
    /** Reference URL listing ISO 639-1 language codes. */
    private static final String ISO_URL = "https://www.andiamo.co.uk/resources/iso-language-codes/";

    /** Provider that yields the persistent store for a given player. */
    private final Function<Player, PlayerLangStore> storeProvider;
    /** Supplier for the language registry used to validate ISO codes. */
    private final Supplier<LangRegistry> langRegistryProvider;

    /**
     * Creates a new command handler.
     *
     * @param storeProvider        provides a {@link PlayerLangStore} for the caller
     * @param langRegistryProvider provides the ISO 639-1 registry validator
     */
    public TranslatorCommand(Function<Player, PlayerLangStore> storeProvider, Supplier<LangRegistry> langRegistryProvider) {
        this.storeProvider = storeProvider;
        this.langRegistryProvider = langRegistryProvider;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }
        final PlayerLangStore store = storeProvider.apply(player);
        final LangRegistry langRegistry = langRegistryProvider.get();

        if (args.length == 0) {
            player.sendMessage(USAGE);
            return true;
        }

        final String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "set": {
                if (args.length < 2) { player.sendMessage(USAGE); return true; }
                final String code = args[1].toLowerCase(Locale.ROOT);
                if (!langRegistry.isValid(code)) {
                    player.sendMessage(ChatColor.RED + "Invalid ISO 639-1 code. See: " + ISO_URL);
                    return true;
                }
                store.set(player.getUniqueId(), code);
                player.sendMessage(ChatColor.GREEN + "Language set to " + ChatColor.AQUA + code + ChatColor.GREEN + ".");
                return true;
            }
            case "get": {
                final String cur = store.get(player.getUniqueId());
                player.sendMessage(cur == null
                        ? ChatColor.YELLOW + "No language set. Use: /translator set <lang>"
                        : ChatColor.GREEN + "Your language: " + ChatColor.AQUA + cur);
                return true;
            }
            case "clear": {
                store.clear(player.getUniqueId());
                player.sendMessage(ChatColor.GREEN + "Cleared your language preference.");
                return true;
            }
            default:
                player.sendMessage(USAGE);
                return true;
        }
    }
}
