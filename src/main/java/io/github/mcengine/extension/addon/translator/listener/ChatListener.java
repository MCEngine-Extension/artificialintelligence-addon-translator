package io.github.mcengine.extension.addon.translator.listener;

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
 * Intercepts chat, translates once per language in-use, and renders
 * per-player hologram lines (rolling 10-line feed).
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

    public ChatListener(Plugin plugin, PlayerLangStore store, TranslationService translations, HologramManager holograms) {
        this.plugin = plugin;
        this.store = store;
        this.translations = translations;
        this.holograms = holograms;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player sender = e.getPlayer();
        String original = e.getMessage();

        Map<String, List<Player>> byLang = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(sender))
                .collect(Collectors.groupingBy(
                        p -> Optional.ofNullable(store.get(p.getUniqueId())).orElse(""),
                        Collectors.toList()
                ));

        Set<String> targetLangs = byLang.keySet().stream()
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());

        if (targetLangs.isEmpty()) {
            // No target languages in use; let vanilla chat through (unchanged).
            return;
        }

        e.setCancelled(true);

        CompletableFuture<Map<String, String>> fut = translations.translateOncePerLanguage(original, targetLangs);
        fut.handle((result, err) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (err != null || result == null) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        holograms.addLine(p, ChatColor.GRAY + "<" + sender.getName() + "> " + original);
                    }
                    return;
                }

                for (Map.Entry<String, List<Player>> entry : byLang.entrySet()) {
                    final String lang = entry.getKey();
                    final List<Player> players = entry.getValue();

                    final String text = (lang == null || lang.isBlank())
                            ? ChatColor.GRAY + "<" + sender.getName() + "> " + original
                            : ChatColor.AQUA + "[T:" + lang + "] " + ChatColor.WHITE +
                              ChatColor.GRAY + "<" + sender.getName() + "> " + ChatColor.WHITE +
                              result.getOrDefault(lang, original);

                    for (Player p : players) {
                        holograms.addLine(p, text);
                    }
                }

                holograms.addLine(sender, ChatColor.GRAY + "<You> " + original);
            });
            return null;
        });
    }
}
