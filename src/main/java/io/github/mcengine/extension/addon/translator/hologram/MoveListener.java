package io.github.mcengine.extension.addon.translator.hologram;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Repositions active holograms whenever a player moves or rotates enough
 * to visibly change the subtitle location.
 */
public final class MoveListener implements Listener {

    /** Manager used to adjust positions for moving players. */
    private final HologramManager holo;

    /**
     * Constructs the listener.
     *
     * @param holo hologram manager responsible for positioning
     */
    public MoveListener(HologramManager holo) {
        this.holo = holo;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom().getWorld() != e.getTo().getWorld()) { holo.tickPosition(e.getPlayer()); return; }
        if (e.getFrom().distanceSquared(e.getTo()) > 0.01
                || Math.abs(e.getFrom().getYaw() - e.getTo().getYaw()) > 1.5F
                || Math.abs(e.getFrom().getPitch() - e.getTo().getPitch()) > 1.5F) {
            holo.tickPosition(e.getPlayer());
        }
    }
}
