package io.github.mcengine.extension.addon.translator.hologram;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Displays ephemeral hologram text under a player's vanilla nameplate.
 *
 * <p>Modes:</p>
 * <ul>
 *   <li><b>Single-line:</b> Show a single custom line under the vanilla name.</li>
 *   <li><b>Two-line:</b> Show a top line (usually "&lt;name&gt;") and a second line (message)
 *       stacked underneath, producing:
 *       <pre>
 *       | <name>  |
 *       | message |
 *       </pre>
 *   </li>
 * </ul>
 *
 * <p>Backwards compatible:</p>
 * <ul>
 *   <li>{@link #showEphemeral(Player, String)}: unchanged API. If the provided text matches
 *       pattern {@code ^\\s*<...>\\s+...}, it is automatically split into two lines;</li>
 *   <li>{@link #showEphemeral(Player, String, String)}: explicit two-line API;</li>
 *   <li>{@link #addLine(Player, String)}: alias to {@link #showEphemeral(Player, String)}.</li>
 * </ul>
 *
 * <p>Each player's hologram auto-clears after {@link #EPHEMERAL_TICKS}.</p>
 */
public final class HologramManager {

    /** How long the text stays visible (10 seconds at 20 TPS). */
    private static final long EPHEMERAL_TICKS = 200L;

    /**
     * Approximate vertical offset from the top of the player model to the vanilla nameplate.
     * Used as the anchor for the top hologram line when rendering two lines.
     */
    private static final double NAMEPLATE_OFFSET = 0.28;

    /**
     * Vertical gap between stacked hologram lines. Increase for more spacing,
     * decrease for tighter stacking.
     */
    private static final double LINE_GAP = 0.32;

    /** Default single-line offset relative to the top of the player model. */
    private static final double SINGLE_LINE_Y_OFFSET = 0.12;

    /** Owning plugin instance used for scheduler and world operations. */
    private final Plugin plugin;

    /** Active armor stand for the top line per player (e.g., "&lt;name&gt;"). */
    private final Map<UUID, ArmorStand> topStandByPlayer = new HashMap<>();

    /** Active armor stand for the bottom line per player (the message). */
    private final Map<UUID, ArmorStand> bottomStandByPlayer = new HashMap<>();

    /** Pending auto-clear task id per player; cancelled when a new line is shown. */
    private final Map<UUID, Integer> clearTaskByPlayer = new HashMap<>();

    /**
     * Simple sanity check logger (kept as provided by the user).
     *
     * @param logger the logger used to print a load confirmation
     */
    public static void check(MCEngineExtensionLogger logger) {
        logger.info("Loaded class: HologramManager");
    }

    /**
     * Creates a manager for per-player holograms shown under the name.
     *
     * @param plugin owning plugin (scheduler and world context)
     */
    public HologramManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Backwards-compatible alias used by existing listeners.
     * Delegates to {@link #showEphemeral(Player, String)}.
     *
     * @param player target player
     * @param text   text to display
     */
    public void addLine(Player player, String text) {
        showEphemeral(player, text);
    }

    /**
     * Shows (or replaces) the player's hologram. If {@code text} looks like {@code "<name> message"},
     * it automatically splits into two stacked lines; otherwise it renders a single line.
     *
     * @param player target player
     * @param text   custom text to display
     */
    public void showEphemeral(Player player, String text) {
        // Try to auto-split formats like "<Hon1199> สวัสดีครับ"
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("<") && trimmed.contains(">")) {
            int end = trimmed.indexOf('>');
            if (end >= 1 && end + 1 < trimmed.length()) {
                String first = trimmed.substring(0, end + 1);                 // "<Hon1199>"
                String rest  = trimmed.substring(end + 1).trim();              // "สวัสดีครับ"
                if (!rest.isEmpty()) {
                    showEphemeral(player, first, rest);
                    return;
                }
            }
        }
        // Fallback: single line under nameplate
        renderSingleLine(player, trimmed);
    }

    /**
     * Explicit two-line API: renders the top line (e.g., "&lt;name&gt;") and the bottom line (message).
     *
     * @param player target player
     * @param top    text for the top line
     * @param bottom text for the bottom line
     */
    public void showEphemeral(Player player, String top, String bottom) {
        final UUID id = player.getUniqueId();

        // Cancel any pending clear for this player
        Integer taskId = clearTaskByPlayer.remove(id);
        if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);

        // Ensure stands exist
        ArmorStand topAs = ensureStand(player, topStandByPlayer, true);
        ArmorStand botAs = ensureStand(player, bottomStandByPlayer, true);

        // Apply text + reposition
        topAs.setCustomName(top);
        botAs.setCustomName(bottom);
        repositionTwoLines(player, topAs, botAs);

        // Schedule auto clear
        int newTask = Bukkit.getScheduler()
                .runTaskLater(plugin, () -> clear(player), EPHEMERAL_TICKS)
                .getTaskId();
        clearTaskByPlayer.put(id, newTask);
    }

    /**
     * Clears and removes the player's hologram, cancelling any scheduled removal.
     *
     * @param player whose hologram to clear
     */
    public void clear(Player player) {
        final UUID id = player.getUniqueId();

        Integer taskId = clearTaskByPlayer.remove(id);
        if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);

        ArmorStand top = topStandByPlayer.remove(id);
        if (top != null && top.isValid()) top.remove();

        ArmorStand bot = bottomStandByPlayer.remove(id);
        if (bot != null && bot.isValid()) bot.remove();
    }

    /**
     * Repositions any active holograms for this player to keep them stacked correctly.
     *
     * @param player the player whose hologram position should be updated
     */
    public void tickPosition(Player player) {
        final UUID id = player.getUniqueId();
        ArmorStand top = topStandByPlayer.get(id);
        ArmorStand bot = bottomStandByPlayer.get(id);

        if (top != null || bot != null) {
            repositionTwoLines(player, top, bot);
            return;
        }

        // If only a single line exists (legacy behavior)
        ArmorStand single = bottomStandByPlayer.get(id); // may not exist in this branch
        if (single != null && single.isValid()) {
            repositionSingleLine(player, single);
        }
    }

    /** Removes all holograms for all players and cancels pending tasks. */
    public void shutdown() {
        for (ArmorStand as : topStandByPlayer.values()) {
            if (as != null && as.isValid()) as.remove();
        }
        topStandByPlayer.clear();

        for (ArmorStand as : bottomStandByPlayer.values()) {
            if (as != null && as.isValid()) as.remove();
        }
        bottomStandByPlayer.clear();

        for (Integer taskId : clearTaskByPlayer.values()) {
            if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);
        }
        clearTaskByPlayer.clear();
    }

    // ---------- internals ----------

    /** Single-line render path used by {@link #showEphemeral(Player, String)} when not splitting. */
    private void renderSingleLine(Player player, String line) {
        final UUID id = player.getUniqueId();

        Integer taskId = clearTaskByPlayer.remove(id);
        if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);

        // We reuse the "bottom" map for single-line mode.
        ArmorStand as = ensureStand(player, bottomStandByPlayer, true);
        as.setCustomName(line);
        repositionSingleLine(player, as);

        int newTask = Bukkit.getScheduler()
                .runTaskLater(plugin, () -> clear(player), EPHEMERAL_TICKS)
                .getTaskId();
        clearTaskByPlayer.put(id, newTask);
    }

    /** Ensures an armor stand exists for the player; creates a new one if absent/invalid. */
    private ArmorStand ensureStand(Player player, Map<UUID, ArmorStand> map, boolean small) {
        ArmorStand as = map.get(player.getUniqueId());
        if (as == null || !as.isValid()) {
            World w = player.getWorld();
            if (w == null) return null;
            as = w.spawn(player.getLocation(), ArmorStand.class, (stand) -> {
                stand.setInvisible(true);
                stand.setMarker(true);
                stand.setGravity(false);
                stand.setSmall(small);
                stand.setCustomNameVisible(true);
                stand.setAI(false);
                stand.setCollidable(false);
                stand.setBasePlate(false);
                stand.setArms(false);
            });
            map.put(player.getUniqueId(), as);
        }
        return as;
    }

    /** Places a single-line hologram under the vanilla nameplate (legacy behavior). */
    private void repositionSingleLine(Player player, ArmorStand as) {
        if (as == null || !as.isValid()) return;
        Location base = player.getLocation();
        double y = base.getY() + player.getHeight() + SINGLE_LINE_Y_OFFSET;
        Location target = new Location(base.getWorld(), base.getX(), y, base.getZ(), base.getYaw(), 0f);
        Location cur = as.getLocation();
        if (!same(cur, target)) as.teleport(target);
    }

    /** Places two stacked holograms: top line anchored near the nameplate, bottom line below it. */
    private void repositionTwoLines(Player player, ArmorStand top, ArmorStand bottom) {
        Location base = player.getLocation();
        double topY = base.getY() + player.getHeight() + NAMEPLATE_OFFSET;
        double botY = topY - LINE_GAP;

        if (top != null && top.isValid()) {
            Location targetTop = new Location(base.getWorld(), base.getX(), topY, base.getZ(), base.getYaw(), 0f);
            Location curTop = top.getLocation();
            if (!same(curTop, targetTop)) top.teleport(targetTop);
        }
        if (bottom != null && bottom.isValid()) {
            Location targetBot = new Location(base.getWorld(), base.getX(), botY, base.getZ(), base.getYaw(), 0f);
            Location curBot = bottom.getLocation();
            if (!same(curBot, targetBot)) bottom.teleport(targetBot);
        }
    }

    /** Tiny epsilon check to avoid needless teleports. */
    private static boolean same(Location a, Location b) {
        return a.getWorld().equals(b.getWorld())
                && Math.abs(a.getX() - b.getX()) < 0.01
                && Math.abs(a.getY() - b.getY()) < 0.01
                && Math.abs(a.getZ() - b.getZ()) < 0.01
                && Math.abs(a.getYaw() - b.getYaw()) < 2.0f;
    }
}
