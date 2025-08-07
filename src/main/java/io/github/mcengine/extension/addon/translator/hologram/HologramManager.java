package io.github.mcengine.extension.addon.translator.hologram;

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
    /** Active hologram sessions keyed by player UUID. */
    private final Map<UUID, Session> sessions = new HashMap<>();

    public HologramManager(Plugin plugin) { this.plugin = plugin; }

    /** Appends a line to the player's hologram feed (rolling at 10). */
    public void addLine(Player player, String line) {
        Session s = sessions.computeIfAbsent(player.getUniqueId(), id -> new Session());
        s.enqueue(line);
        ensureStands(player, s);
        applyTexts(s);
        reposition(player, s);
    }

    /** Clears and removes all holograms for the player. */
    public void clear(Player player) {
        Session s = sessions.remove(player.getUniqueId());
        if (s != null) s.despawn();
    }

    /** Repositions hologram for a moving/rotating player. */
    public void tickPosition(Player player) {
        Session s = sessions.get(player.getUniqueId());
        if (s == null || s.stands.isEmpty()) return;
        reposition(player, s);
    }

    /** Removes all holograms for all players. */
    public void shutdown() {
        for (Session s : sessions.values()) s.despawn();
        sessions.clear();
    }

    private void ensureStands(Player player, Session s) {
        World w = player.getWorld();
        int need = s.lines.size() - s.stands.size();
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
            s.stands.add(as);
        }
        while (s.stands.size() > s.lines.size()) {
            ArmorStand dead = s.stands.remove(0);
            if (!dead.isDead()) dead.remove();
        }
    }

    private void applyTexts(Session s) {
        for (int i = 0; i < s.lines.size(); i++) {
            ArmorStand as = s.stands.get(i);
            String text = s.lines.get(i);
            if (as != null && as.isValid()) as.setCustomName(text);
        }
    }

    private void reposition(Player player, Session s) {
        Location eye = player.getEyeLocation();
        Vector dir = player.getLocation().getDirection().normalize();
        Vector up = new Vector(0, 1, 0);
        Vector left = up.clone().crossProduct(dir).normalize();
        if (Double.isNaN(left.length())) left = new Vector(0, 0, 0);

        Location base = eye.clone()
                .add(dir.clone().multiply(FORWARD))
                .add(left.clone().multiply(LEFT));
        base.add(0, BASE_Y_OFFSET, 0);

        for (int i = 0; i < s.stands.size(); i++) {
            ArmorStand as = s.stands.get(i);
            if (as == null || !as.isValid()) continue;
            double y = base.getY() + (s.stands.size() - 1 - i) * LINE_SPACING;
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

    /** Per-player hologram session with rolling buffer and spawned stands. */
    private static final class Session {
        /** Fixed-size rolling buffer of last messages. */
        final Deque<String> buffer = new ArrayDeque<>(MAX_LINES);
        /** Materialized lines (copy of {@link #buffer}) to map one-to-one with stands. */
        final List<String> lines = new ArrayList<>(MAX_LINES);
        /** Spawned armor stands corresponding to lines. */
        final List<ArmorStand> stands = new ArrayList<>(MAX_LINES);
        void enqueue(String text) {
            if (buffer.size() == MAX_LINES) buffer.removeFirst();
            buffer.addLast(text);
            lines.clear();
            lines.addAll(buffer);
        }
        void despawn() {
            for (ArmorStand as : stands) { if (as != null && as.isValid()) as.remove(); }
            stands.clear(); lines.clear(); buffer.clear();
        }
    }
}
