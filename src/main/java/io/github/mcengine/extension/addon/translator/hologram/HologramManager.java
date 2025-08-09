package io.github.mcengine.extension.addon.translator.hologram;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Manages per-player floating hologram text feeds:
 * <ul>
 *   <li>Max 10 lines</li>
 *   <li>Newest appended at bottom</li>
 *   <li>Oldest removed on overflow</li>
 *   <li>Positioned 1 block forward and a little left from player's eyes</li>
 * </ul>
 *
 * <p><strong>Note on implementation:</strong> Previously this class used a private nested
 * <code>Session</code> class. Some build pipelines (e.g., shading/minimization or file include
 * globs) can inadvertently exclude <code>$</code>-suffixed inner-class files from the final JAR,
 * causing {@link NoClassDefFoundError} at runtime. To make packaging robust, the per-player state
 * has been rewritten using plain maps without any nested classes.</p>
 */
public final class HologramManager {

    /** Maximum number of lines in a hologram buffer. */
    private static final int MAX_LINES = 10;
    /** Forward offset in blocks from player's eyes. */
    private static final double FORWARD = 1.0;
    /** Left offset in blocks from player's eyes. */
    private static final double LEFT = 0.35;
    /** Base vertical offset above eyes for the hologram stack. */
    private static final double BASE_Y_OFFSET = 0.2;
    /** Vertical spacing between hologram lines. */
    private static final double LINE_SPACING = 0.26;

    /** Owning plugin instance used for world operations and logging. */
    private final Plugin plugin;

    /**
     * Per-player rolling buffers (last {@value #MAX_LINES} lines).
     * <p>Keyed by player UUID.</p>
     */
    private final Map<UUID, Deque<String>> buffers = new HashMap<>();

    /**
     * Materialized lines copied from {@link #buffers} for 1:1 mapping with armor stands.
     * <p>Keyed by player UUID.</p>
     */
    private final Map<UUID, List<String>> lines = new HashMap<>();

    /**
     * Spawned armor stands per player, kept in the same order as {@link #lines}.
     * <p>Keyed by player UUID.</p>
     */
    private final Map<UUID, List<ArmorStand>> stands = new HashMap<>();

    /**
     * Simple sanity check logger (kept as provided by the user).
     *
     * @param logger the logger used to print a load confirmation
     */
    public static void check(MCEngineExtensionLogger logger) {
        logger.info("Loaded class: HologramManager");
    }

    /**
     * Creates a manager for in-front-of-player hologram feeds.
     *
     * @param plugin owning plugin, used for world operations and scheduler context
     */
    public HologramManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Appends a line to the player's hologram feed (rolling at 10). */
    public void addLine(Player player, String line) {
        final UUID id = player.getUniqueId();

        // Update rolling buffer
        Deque<String> buffer = buffers.computeIfAbsent(id, k -> new ArrayDeque<>(MAX_LINES));
        if (buffer.size() == MAX_LINES) buffer.removeFirst();
        buffer.addLast(line);

        // Refresh materialized lines
        List<String> ls = lines.computeIfAbsent(id, k -> new ArrayList<>(MAX_LINES));
        ls.clear();
        ls.addAll(buffer);

        // Sync stands and apply text
        List<ArmorStand> sts = stands.computeIfAbsent(id, k -> new ArrayList<>(MAX_LINES));
        ensureStands(player, ls, sts);
        applyTexts(ls, sts);

        // Position
        reposition(player, sts);
    }

    /** Clears and removes all holograms for the player. */
    public void clear(Player player) {
        final UUID id = player.getUniqueId();

        // Despawn stands
        List<ArmorStand> sts = stands.remove(id);
        if (sts != null) {
            for (ArmorStand as : sts) {
                if (as != null && as.isValid()) as.remove();
            }
        }

        // Clear state
        lines.remove(id);
        buffers.remove(id);
    }

    /** Repositions hologram for a moving/rotating player. */
    public void tickPosition(Player player) {
        final UUID id = player.getUniqueId();
        final List<ArmorStand> sts = stands.get(id);
        if (sts == null || sts.isEmpty()) return;
        reposition(player, sts);
    }

    /** Removes all holograms for all players. */
    public void shutdown() {
        for (List<ArmorStand> sts : stands.values()) {
            if (sts == null) continue;
            for (ArmorStand as : sts) {
                if (as != null && as.isValid()) as.remove();
            }
        }
        stands.clear();
        lines.clear();
        buffers.clear();
    }

    private void ensureStands(Player player, List<String> ls, List<ArmorStand> sts) {
        final World w = player.getWorld();
        if (w == null) return;

        int need = ls.size() - sts.size();
        for (int i = 0; i < need; i++) {
            ArmorStand as = w.spawn(player.getLocation(), ArmorStand.class, (stand) -> {
                stand.setInvisible(true);
                stand.setMarker(true);
                stand.setGravity(false);
                stand.setSmall(true);
                stand.setCustomNameVisible(true);
                stand.setAI(false);
                stand.setCollidable(false);
                stand.setBasePlate(false);
                stand.setArms(false);
            });
            sts.add(as);
        }
        while (sts.size() > ls.size()) {
            ArmorStand dead = sts.remove(0);
            if (dead != null && !dead.isDead()) dead.remove();
        }
    }

    private void applyTexts(List<String> ls, List<ArmorStand> sts) {
        for (int i = 0; i < ls.size(); i++) {
            ArmorStand as = sts.get(i);
            String text = ls.get(i);
            if (as != null && as.isValid()) as.setCustomName(text);
        }
    }

    private void reposition(Player player, List<ArmorStand> sts) {
        if (sts.isEmpty()) return;

        Location eye = player.getEyeLocation();
        Vector dir = player.getLocation().getDirection().normalize();
        Vector up = new Vector(0, 1, 0);
        Vector left = up.clone().crossProduct(dir).normalize();
        if (Double.isNaN(left.length())) left = new Vector(0, 0, 0);

        Location base = eye.clone()
                .add(dir.clone().multiply(FORWARD))
                .add(left.clone().multiply(LEFT));
        base.add(0, BASE_Y_OFFSET, 0);

        for (int i = 0; i < sts.size(); i++) {
            ArmorStand as = sts.get(i);
            if (as == null || !as.isValid()) continue;
            double y = base.getY() + (sts.size() - 1 - i) * LINE_SPACING;
            Location l = new Location(base.getWorld(), base.getX(), y, base.getZ(), eye.getYaw(), eye.getPitch());
            if (!same(aLoc(as), l)) as.teleport(l);
        }
    }

    private static Location aLoc(ArmorStand as) { return as.getLocation(); }

    private static boolean same(Location a, Location b) {
        return a.getWorld().equals(b.getWorld())
                && Math.abs(a.getX() - b.getX()) < 0.01
                && Math.abs(a.getY() - b.getY()) < 0.01
                && Math.abs(a.getZ() - b.getZ()) < 0.01
                && Math.abs(a.getYaw() - b.getYaw()) < 2.0f;
    }
}
