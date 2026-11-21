package com.universalspacenav.client;

import com.universalspacenav.UniversalSpaceNavigation;
import com.universalspacenav.UsnConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * DIAGNOSTIC ONLY. Remembers the previous ClientLevel / LocalPlayer with weak references (they never keep the object
 * alive) and, a few seconds after a change, reads a fixed list of static fields in other mods to see which of them
 * still point at the old object. It never writes, clears or replaces anything, and never forces a garbage collection
 * unless the player explicitly runs "/usnleak gc confirm".
 * <p>
 * Nothing here stores a world or player strongly: watches hold WeakReferences plus plain strings/numbers, and the
 * scan keeps its references in local variables only.
 */
public final class LeakWatch {
    private static final class Watch {
        final int id;
        final String kind;
        final String what;
        final long createdTick;
        final WeakReference<Object> ref;
        boolean scanned;
        boolean releasedLogged;

        Watch(int id, String kind, String what, long tick, Object o) {
            this.id = id;
            this.kind = kind;
            this.what = what;
            this.createdTick = tick;
            this.ref = new WeakReference<>(o);
        }
    }

    private static final List<Watch> WATCHES = new ArrayList<>();
    private static WeakReference<ClientLevel> lastLevel = new WeakReference<>(null);
    private static WeakReference<LocalPlayer> lastPlayer = new WeakReference<>(null);
    private static boolean hadLevel;
    private static boolean hadPlayer;
    private static int nextId = 1;
    private static int releasedQuickly;
    private static long ticks;
    private static int gcCountdown = -1;
    private static List<Field> fields;
    private static int missingClasses;
    private static int unreadable;

    private LeakWatch() {
    }

    public static boolean enabled() {
        try {
            return UsnConfig.CLIENT.leakWatch.get();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Called every client tick (cheap: two reference compares). */
    public static void tick() {
        if (!enabled()) return;
        ticks++;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        LocalPlayer pl = mc.player;

        ClientLevel prevLevel = lastLevel.get();
        if (lv != prevLevel) {
            if (prevLevel != null) {
                add("level", prevLevel.dimension().location().toString(), prevLevel);
            } else if (hadLevel) {
                releasedQuickly++;
            }
            lastLevel = new WeakReference<>(lv);
            hadLevel = lv != null;
        }
        LocalPlayer prevPlayer = lastPlayer.get();
        if (pl != prevPlayer) {
            if (prevPlayer != null) {
                add("player", "LocalPlayer", prevPlayer);
            } else if (hadPlayer) {
                releasedQuickly++;
            }
            lastPlayer = new WeakReference<>(pl);
            hadPlayer = pl != null;
        }

        if (gcCountdown >= 0 && --gcCountdown < 0) {
            UniversalSpaceNavigation.LOGGER.info("[USN leak] manual GC finished, scanning");
            report(true, "after manual GC");
        }
        if (ticks % 20 == 0 && !WATCHES.isEmpty()) {
            int delay = UsnConfig.CLIENT.leakWatchDelay.get() * 20;
            boolean due = false;
            for (Watch w : WATCHES) {
                if (!w.scanned && ticks - w.createdTick >= delay && w.ref.get() != null) due = true;
                if (w.ref.get() == null && !w.releasedLogged) {
                    w.releasedLogged = true;
                    UniversalSpaceNavigation.LOGGER.info("[USN leak] old " + w.kind + " #" + w.id + " (" + w.what + ") has been RELEASED (garbage collected)");
                }
            }
            if (due) report(false, "automatic, " + UsnConfig.CLIENT.leakWatchDelay.get() + "s after a change");
        }
    }

    private static void add(String kind, String what, Object o) {
        Watch w = new Watch(nextId++, kind, what, ticks, o);
        WATCHES.add(w);
        while (WATCHES.size() > 12) WATCHES.remove(0);
        UniversalSpaceNavigation.LOGGER.info("[USN leak] watching old " + kind + " #" + w.id + " (" + what + ")");
    }

    public static void requestGc() {
        UniversalSpaceNavigation.LOGGER.warn("[USN leak] running System.gc() on request; the game may pause briefly");
        System.gc();
        gcCountdown = 40; // look again two seconds later
    }

    public static String status() {
        int alive = 0;
        for (Watch w : WATCHES) if (w.ref.get() != null) alive++;
        return "watching " + WATCHES.size() + " old object(s), " + alive + " still alive, "
                + releasedQuickly + " released before they could be checked";
    }

    /** Manual or automatic scan. Returns the summary lines (also written to the log). */
    public static List<String> report(boolean manual, String why) {
        List<String> lines = new ArrayList<>();
        try {
            loadFields();
            Minecraft mc = Minecraft.getInstance();
            Object curLevel = mc.level;
            Object curPlayer = mc.player;
            List<Watch> alive = new ArrayList<>();
            for (Watch w : WATCHES) {
                if (w.ref.get() != null) alive.add(w);
            }
            lines.add("scan (" + why + "): " + status() + "; " + fields.size() + " fields checked"
                    + (missingClasses > 0 ? ", " + missingClasses + " classes not installed" : "")
                    + (unreadable > 0 ? ", " + unreadable + " unreadable" : ""));
            List<List<String>> holders = new ArrayList<>();
            for (int i = 0; i < alive.size(); i++) holders.add(new ArrayList<>());
            List<String> otherStale = new ArrayList<>();
            for (Field f : fields) {
                Object v;
                try {
                    v = f.get(null);
                } catch (Throwable t) {
                    unreadable++;
                    continue;
                }
                if (v == null) continue;
                String name = f.getDeclaringClass().getName() + "." + f.getName();
                boolean matched = false;
                for (int i = 0; i < alive.size(); i++) {
                    if (alive.get(i).ref.get() == v) {
                        holders.get(i).add(name);
                        matched = true;
                    }
                }
                if (!matched) {
                    if (v instanceof ClientLevel && v != curLevel) otherStale.add(name + " (a world that is not the current one)");
                    else if (v instanceof Player && v != curPlayer && v instanceof LocalPlayer) otherStale.add(name + " (a LocalPlayer that is not the current one)");
                }
                v = null;
            }
            for (int i = 0; i < alive.size(); i++) {
                Watch w = alive.get(i);
                w.scanned = true;
                long age = (ticks - w.createdTick) / 20;
                List<String> h = holders.get(i);
                if (h.isEmpty()) {
                    lines.add("old " + w.kind + " #" + w.id + " (" + w.what + ", " + age + "s ago) is still alive, but NO watched field holds it"
                            + " (it may just not be collected yet, or the holder is not on the list)");
                } else {
                    lines.add("old " + w.kind + " #" + w.id + " (" + w.what + ", " + age + "s ago) is held by " + h.size() + " field(s):");
                    for (String s : h) lines.add("    " + s);
                }
            }
            if (!otherStale.isEmpty()) {
                lines.add("fields holding some other old world/player:");
                for (String s : otherStale) lines.add("    " + s);
            }
            if (alive.isEmpty()) lines.add("no old world or player is currently alive: nothing is being retained");
        } catch (Throwable t) {
            lines.add("scan failed: " + t);
        }
        for (String s : lines) UniversalSpaceNavigation.LOGGER.info("[USN leak] " + s);
        return lines;
    }

    private static void loadFields() {
        if (fields != null) return;
        List<Field> out = new ArrayList<>();
        try (InputStream in = LeakWatch.class.getResourceAsStream("/universalspacenav/leakwatch_fields.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] p = line.split(" ");
                    if (p.length != 2) continue;
                    try {
                        Class<?> c = Class.forName(p[0], false, LeakWatch.class.getClassLoader());
                        Field f = c.getDeclaredField(p[1]);
                        if (!Modifier.isStatic(f.getModifiers())) continue;
                        f.setAccessible(true);
                        out.add(f);
                    } catch (ClassNotFoundException | NoSuchFieldException e) {
                        missingClasses++;
                    } catch (Throwable t) {
                        unreadable++;
                    }
                }
            }
        } catch (Throwable t) {
            UniversalSpaceNavigation.LOGGER.warn("[USN leak] could not read the field list", t);
        }
        fields = out;
    }
}
