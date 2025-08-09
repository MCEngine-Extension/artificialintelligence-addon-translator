package io.github.mcengine.extension.addon.translator.listener;

import io.github.mcengine.extension.addon.translator.Translator;
import io.github.mcengine.extension.addon.translator.hologram.HologramManager;
import io.github.mcengine.extension.addon.translator.player.PlayerLangStore;
import io.github.mcengine.extension.addon.translator.translate.TranslationService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Translates incoming chat once per target language and shows each recipient
 * a single ephemeral line under their own name (auto-clears after 10s and
 * is replaced by the next message).
 *
 * <p>When Test Mode is enabled for the sender, they also see their own
 * translated text under their name. Otherwise they see a raw "You" line.</p>
 */
public class ChatListener implements Listener {

    /** Owning plugin instance for scheduler access. */
    private final Plugin plugin;
    /** Store that holds players' language preferences. */
    private final PlayerLangStore store;
    /** Translation orchestrator backed by AI + cache. */
    private final TranslationService translations;
    /** Hologram manager used to render translated lines. */
    private final HologramManager holograms;
    /** Per-player Test Mode flag provider. */
    private final Translator.TestMode testMode;

    public ChatListener(Plugin plugin,
                        PlayerLangStore store,
                        TranslationService translations,
                        HologramManager holograms,
                        Translator.TestMode testMode) {
        this.plugin = plugin;
        this.store = store;
        this.translations = translations;
        this.holograms = holograms;
        this.testMode = testMode;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        final Player sender = e.getPlayer();
        final String original = e.getMessage();

        final boolean testEnabled = testMode != null && testMode.isEnabled(sender.getUniqueId());

        // Materialize online players into a concrete list.
        final List<Player> allPlayers = new ArrayList<>(Bukkit.getOnlinePlayers());

        // Include sender only if Test Mode is enabled; otherwise exclude as before.
        final Map<String, List<Player>> byLang = allPlayers.stream()
                .filter(p -> testEnabled || !p.equals(sender))
                .collect(Collectors.groupingBy(
                        p -> Optional.ofNullable(store.get(p.getUniqueId())).orElse(""),
                        Collectors.toList()
                ));

        final Set<String> targetLangs = byLang.keySet().stream()
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());

        if (targetLangs.isEmpty()) {
            // No target languages in use; let vanilla chat through (unchanged).
            return;
        }

        // We'll handle delivery ourselves via holograms.
        e.setCancelled(true);

        final CompletableFuture<Map<String, String>> fut =
                translations.translateOncePerLanguage(original, targetLangs);

        fut.handle((result, err) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (err != null || result == null) {
                    // Fallback: raw line under each recipient's name.
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        holograms.showEphemeral(
                                p,
                                ChatColor.GRAY + "<" + sender.getName() + "> " + ChatColor.WHITE + original
                        );
                    }
                    // Sender "You" line only when not testing
                    if (!testEnabled) {
                        holograms.showEphemeral(
                                sender,
                                ChatColor.GRAY + "<You> " + ChatColor.WHITE + original
                        );
                    }
                    return;
                }

                // Deliver per-language result to recipients who use that language
                for (Map.Entry<String, List<Player>> entry : byLang.entrySet()) {
                    final String lang = entry.getKey();
                    final List<Player> players = entry.getValue();

                    final String translated = (lang == null || lang.isBlank())
                            ? original
                            : result.getOrDefault(lang, original);

                    final String line = ChatColor.AQUA + "<" + sender.getName() + "> "
                            + ChatColor.WHITE + translated;

                    for (Player p : players) {
                        holograms.showEphemeral(p, line);
                    }
                }

                // Only add the raw "You" line when NOT in Test Mode.
                if (!testEnabled) {
                    holograms.showEphemeral(
                            sender,
                            ChatColor.GRAY + "<You> " + ChatColor.WHITE + original
                    );
                }
            });
            return null;
        });
    }
}
