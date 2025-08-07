package io.github.mcengine.extension.addon.translator.hologram;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Cleans up holograms on player quit.
 */
public final class QuitListener implements Listener {

    /** Hologram manager used to clear player state on quit. */
    private final HologramManager holo;

    public QuitListener(HologramManager holo) { this.holo = holo; }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        holo.clear(e.getPlayer());
    }
}
