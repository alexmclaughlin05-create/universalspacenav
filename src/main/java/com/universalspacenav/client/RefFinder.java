package com.universalspacenav.client;

import com.universalspacenav.UniversalSpaceNavigation;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * DIAGNOSTIC ONLY, opt-in (/usnleak find). Searches for what is keeping old worlds/players alive: it starts from the
 * static fields of every installed mod (found by reading the class files, without starting any class) and follows
 * references (fields, maps, lists, arrays) looking for the old world/player objects. It only READS memory, on a
 * background thread, and holds the old objects strongly only while it runs. Hidden (lambda) classes cannot be read
 * and are skipped.
 */
public final class RefFinder {
    private static volatile boolean running;
    private static final int MAX_NODES = 9_000_000;
    private static final int MAX_DEPTH = 24;
    private static final long TIME_BUDGET_MS = 300_000;

    private RefFinder() {
    }

    public static boolean running() {
        return running;
    }

    private static final class Node {
        final Object obj;
        final Node parent;
        final String via;
        final int depth;

        Node(Object obj, Node parent, String via, int depth) {
            this.obj = obj;
            this.parent = parent;
            this.via = via;
            this.depth = depth;
        }
    }

    public static boolean start(List<Object> targets, List<String> names) {
        if (running) return false;
        running = true;
        Thread t = new Thread(() -> run(targets, names), "USN-leak-finder");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
        return true;
    }

    private static void say(String s) {
        UniversalSpaceNavigation.LOGGER.info("[USN leak] " + s);
        try {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.player != null) mc.player.displayClientMessage(Component.literal("[USN leak] " + s), false);
            });
        } catch (Throwable ignored) {
        }
    }

    private static void run(List<Object> targets, List<String> names) {
        long t0 = System.currentTimeMillis();
        try {
            sun.misc.Unsafe u = unsafe();
            if (u == null) {
                say("find: cannot read memory directly on this Java, giving up");
                return;
            }
            say("find: reading mod class files (about a minute, the game keeps running)...");
            List<String[]> roots = candidateStatics();
            say("find: " + roots.size() + " static fields to follow");
            IdentityHashMap<Object, Node> seen = new IdentityHashMap<>();
            ArrayDeque<Node> queue = new ArrayDeque<>();
            IdentityHashMap<Object, Integer> want = new IdentityHashMap<>();
            for (int i = 0; i < targets.size(); i++) want.put(targets.get(i), i);
            Minecraft mc = Minecraft.getInstance();
            Object curLevel = mc.level, curPlayer = mc.player;
            ClassLoader cl = RefFinder.class.getClassLoader();
            Map<Class<?>, Field[]> fieldCache = new HashMap<>();
            for (String[] r : roots) {
                try {
                    Class<?> c = Class.forName(r[0], false, cl);
                    Field f = findField(c, r[1], fieldCache);
                    if (f == null || !Modifier.isStatic(f.getModifiers())) continue;
                    Object v = u.getObject(u.staticFieldBase(f), u.staticFieldOffset(f));
                    if (v != null && !seen.containsKey(v)) {
                        Node n = new Node(v, null, "static " + r[0] + "." + r[1], 0);
                        seen.put(v, n);
                        queue.add(n);
                    }
                } catch (Throwable ignored) {
                }
            }
            // also the Minecraft instance itself (its direct fields only reach shallow structures first)
            Node mcNode = new Node(mc, null, "static Minecraft.instance", 0);
            seen.put(mc, mcNode);
            queue.add(mcNode);

            boolean[] found = new boolean[targets.size()];
            int foundCount = 0;
            Map<Class<?>, long[]> offCache = new HashMap<>();
            Map<Class<?>, String[]> nameCache = new HashMap<>();
            long visited = 0;
            while (!queue.isEmpty() && visited < MAX_NODES && System.currentTimeMillis() - t0 < TIME_BUDGET_MS) {
                if ((visited & 0xFFFF) == 0 && lowMemory()) {
                    say("find: stopping early, the game is running low on memory");
                    break;
                }
                Node n = queue.poll();
                visited++;
                Object o = n.obj;
                Integer hit = want.get(o);
                if (hit != null && !found[hit]) {
                    found[hit] = true;
                    foundCount++;
                    say("PATH to " + names.get(hit) + ": " + path(n));
                }
                if (foundCount == targets.size()) break;
                if (n.depth >= MAX_DEPTH) continue;
                if (o == curLevel || o == curPlayer) continue;
                Class<?> c = o.getClass();
                if (leaf(c)) continue;
                try {
                    if (o instanceof Object[] arr) {
                        int lim = Math.min(arr.length, 20000);
                        for (int i = 0; i < lim; i++) push(arr[i], n, "[" + i + "]", seen, queue);
                    } else if (c.isArray()) {
                        continue;
                    } else if (o instanceof Map<?, ?> m) {
                        int k = 0;
                        boolean weakKeys = m instanceof java.util.WeakHashMap;
                        if (!weakKeys && c.getName().startsWith("java.util.Collections$")) {
                            // synchronized/unmodifiable wrappers: look at the real map inside
                            try {
                                Field wf = c.getDeclaredField("m");
                                Object inner = unsafe().getObject(o, unsafe().objectFieldOffset(wf));
                                weakKeys = inner instanceof java.util.WeakHashMap;
                            } catch (Throwable ignore) {
                            }
                        }
                        for (Map.Entry<?, ?> e : new ArrayList<>(m.entrySet())) {
                            if (k++ > 20000) break;
                            if (!weakKeys) push(e.getKey(), n, "{key}", seen, queue);
                            push(e.getValue(), n, "{value of " + brief(e.getKey()) + "}", seen, queue);
                        }
                    } else if (o instanceof Iterable<?> it && c.getName().startsWith("java.util.")) {
                        int k = 0;
                        for (Object e : new ArrayList<Object>(toList(it))) {
                            if (k++ > 20000) break;
                            push(e, n, "[" + (k - 1) + "]", seen, queue);
                        }
                    } else if (o instanceof Reference<?> ref) {
                        // weak/soft references do not keep an object alive: do not follow them,
                        // except a thread-local slot, whose VALUE is strongly held by the thread
                        if (c.getName().equals("java.lang.ThreadLocal$ThreadLocalMap$Entry")) {
                            Field vf = c.getDeclaredField("value");
                            push(u.getObject(o, u.objectFieldOffset(vf)), n, ".threadLocalValue", seen, queue);
                        }
                    } else if (o instanceof AtomicReference<?> ar) {
                        push(ar.get(), n, ".get()", seen, queue);
                    } else if (o instanceof Optional<?> op) {
                        push(op.orElse(null), n, ".get()", seen, queue);
                    } else if (c.isHidden()) {
                        // lambdas / generated classes: read captured values through reflection
                        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
                            for (Field f : k.getDeclaredFields()) {
                                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                                try {
                                    f.setAccessible(true);
                                    push(f.get(o), n, "." + f.getName() + " (lambda " + shortName(c) + ")", seen, queue);
                                } catch (Throwable ignore) {
                                }
                            }
                        }
                    } else {
                        long[] offs = offCache.get(c);
                        String[] nms = nameCache.get(c);
                        if (offs == null) {
                            List<Long> ol = new ArrayList<>();
                            List<String> nl = new ArrayList<>();
                            for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
                                Field[] fs;
                                try {
                                    fs = k.getDeclaredFields();
                                } catch (Throwable t) {
                                    continue;
                                }
                                for (Field f : fs) {
                                    if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                                    try {
                                        ol.add(u.objectFieldOffset(f));
                                        nl.add(f.getName());
                                    } catch (Throwable ignored) {
                                    }
                                }
                            }
                            offs = new long[ol.size()];
                            nms = new String[ol.size()];
                            for (int i = 0; i < offs.length; i++) {
                                offs[i] = ol.get(i);
                                nms[i] = nl.get(i);
                            }
                            offCache.put(c, offs);
                            nameCache.put(c, nms);
                        }
                        for (int i = 0; i < offs.length; i++) push(u.getObject(o, offs[i]), n, "." + nms[i] + " (" + shortName(c) + ")", seen, queue);
                    }
                } catch (Throwable ignored) {
                }
            }
            int missing = targets.size() - foundCount;
            say("find finished: looked at " + visited + " objects in " + (System.currentTimeMillis() - t0) / 1000 + "s; "
                    + foundCount + " of " + targets.size() + " old objects traced to a holder, " + missing + " not found"
                    + (queue.isEmpty() ? "" : " (search limit reached)"));
        } catch (Throwable t) {
            UniversalSpaceNavigation.LOGGER.warn("[USN leak] find failed", t);
            say("find failed: " + t);
        } finally {
            targets.clear();
            running = false;
        }
    }

    private static List<Object> toList(Iterable<?> it) {
        List<Object> l = new ArrayList<>();
        int k = 0;
        for (Object o : it) {
            l.add(o);
            if (++k > 20000) break;
        }
        return l;
    }

    private static void push(Object v, Node parent, String via, IdentityHashMap<Object, Node> seen, ArrayDeque<Node> queue) {
        if (v == null || seen.containsKey(v)) return;
        if (seen.size() >= MAX_NODES) return;
        Node n = new Node(v, parent, via, parent.depth + 1);
        seen.put(v, n);
        queue.add(n);
    }

    private static boolean lowMemory() {
        Runtime r = Runtime.getRuntime();
        return r.maxMemory() - (r.totalMemory() - r.freeMemory()) < 450L * 1024 * 1024;
    }

    private static boolean leaf(Class<?> c) {
        if (c.isPrimitive() || c.isEnum() || c == String.class || Number.class.isAssignableFrom(c) || c == Boolean.class
                || c == Class.class || c == Character.class) return true;
        String n = c.getName();
        return n.startsWith("java.lang.invoke.") || n.startsWith("java.lang.ThreadGroup") || n.startsWith("java.lang.ClassLoader")
                || n.startsWith("java.lang.Module") || n.startsWith("jdk.") || n.startsWith("sun.")
                || n.startsWith("net.minecraft.resources.ResourceLocation") || n.startsWith("net.minecraft.core.MappedRegistry")
                || n.startsWith("net.minecraft.core.RegistryAccess") || n.startsWith("cpw.mods.") || n.endsWith("ClassLoader")
                || n.startsWith("java.nio.") || n.startsWith("io.netty.buffer") || n.startsWith("io.netty.util.internal");
    }

    private static String shortName(Class<?> c) {
        String n = c.getName();
        int i = n.lastIndexOf('.');
        return i >= 0 ? n.substring(i + 1) : n;
    }

    private static String brief(Object k) {
        if (k == null) return "null";
        String n = k.getClass().getSimpleName();
        if (k instanceof String || k instanceof Number || k instanceof Enum) return String.valueOf(k);
        return n;
    }

    private static String path(Node n) {
        List<String> parts = new ArrayList<>();
        for (Node p = n; p != null; p = p.parent) parts.add(p.via + " = " + shortName(p.obj.getClass()));
        StringBuilder sb = new StringBuilder();
        for (int i = parts.size() - 1; i >= 0; i--) {
            sb.append(parts.get(i));
            if (i > 0) sb.append("  ->  ");
        }
        return sb.toString();
    }

    private static Field findField(Class<?> c, String name, Map<Class<?>, Field[]> cache) {
        Field[] fs = cache.get(c);
        if (fs == null) {
            try {
                fs = c.getDeclaredFields();
            } catch (Throwable t) {
                fs = new Field[0];
            }
            cache.put(c, fs);
        }
        for (Field f : fs) if (f.getName().equals(name)) return f;
        return null;
    }

    private static sun.misc.Unsafe unsafe() {
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (sun.misc.Unsafe) f.get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------------------------------------------------------- class file reading (no classes are started)

    private static boolean interesting(String desc, String owner) {
        if (desc.length() < 3 || desc.charAt(0) != 'L') return false;
        if (desc.startsWith("Ljava/util/")) {
            return !(desc.startsWith("Ljava/util/regex/") || desc.startsWith("Ljava/util/function/") || desc.startsWith("Ljava/util/stream/")
                    || desc.equals("Ljava/util/UUID;") || desc.equals("Ljava/util/Random;") || desc.equals("Ljava/util/Locale;")
                    || desc.equals("Ljava/util/Comparator;"));
        }
        if (desc.startsWith("Lcom/google/common/collect/") || desc.startsWith("Lit/unimi/dsi/fastutil/")
                || desc.startsWith("Ljava/lang/ref/") || desc.startsWith("Lcom/google/common/cache/")) return true;
        if (desc.startsWith("Lnet/minecraft/client/") || desc.startsWith("Lnet/minecraft/server/level/")
                || desc.startsWith("Lnet/minecraft/world/level/Level") || desc.startsWith("Lnet/minecraft/world/level/chunk/")
                || desc.startsWith("Lnet/minecraft/world/entity/player/")) return true;
        int a = nth(owner, '/', 3), b = nth(desc.substring(1), '/', 3);
        return a > 0 && b > 0 && owner.regionMatches(0, desc, 1, a) && desc.regionMatches(1, owner, 0, a);
    }

    private static int nth(String s, char c, int n) {
        int idx = -1;
        for (int i = 0; i < n; i++) {
            idx = s.indexOf(c, idx + 1);
            if (idx < 0) return -1;
        }
        return idx;
    }

    private static List<String[]> candidateStatics() {
        List<String[]> out = new ArrayList<>();
        List<Path> jars = new ArrayList<>();
        try {
            for (var info : ModList.get().getModFiles()) {
                try {
                    Path p = info.getFile().getFilePath();
                    if (p != null && !jars.contains(p)) jars.add(p);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        for (Path p : jars) {
            if (!p.toString().endsWith(".jar")) continue;
            try (ZipFile z = new ZipFile(p.toFile())) {
                var en = z.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    String nm = e.getName();
                    if (!nm.endsWith(".class") || nm.startsWith("META-INF/") || nm.equals("module-info.class")) continue;
                    try (InputStream in = new BufferedInputStream(z.getInputStream(e), 8192)) {
                        parse(new DataInputStream(in), out);
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    private static void parse(DataInputStream d, List<String[]> out) throws Exception {
        if (d.readInt() != 0xCAFEBABE) return;
        d.readUnsignedShort();
        d.readUnsignedShort();
        int n = d.readUnsignedShort();
        String[] utf = new String[n];
        int[] cls = new int[n];
        for (int i = 1; i < n; i++) {
            int tag = d.readUnsignedByte();
            switch (tag) {
                case 1 -> utf[i] = d.readUTF();
                case 3, 4, 9, 10, 11, 12, 17, 18 -> d.skipBytes(4);
                case 5, 6 -> {
                    d.skipBytes(8);
                    i++;
                }
                case 7 -> cls[i] = d.readUnsignedShort();
                case 8, 16, 19, 20 -> d.skipBytes(2);
                case 15 -> d.skipBytes(3);
                default -> {
                    return;
                }
            }
        }
        d.readUnsignedShort();
        int thisIdx = d.readUnsignedShort();
        d.readUnsignedShort();
        int ic = d.readUnsignedShort();
        d.skipBytes(2 * ic);
        String owner = utf[cls[thisIdx]];
        if (owner == null) return;
        int fc = d.readUnsignedShort();
        for (int i = 0; i < fc; i++) {
            int acc = d.readUnsignedShort();
            int ni = d.readUnsignedShort();
            int di = d.readUnsignedShort();
            int ac = d.readUnsignedShort();
            for (int a = 0; a < ac; a++) {
                d.readUnsignedShort();
                d.skipBytes(d.readInt());
            }
            if ((acc & 8) != 0 && utf[di] != null && interesting(utf[di], owner)) {
                out.add(new String[]{owner.replace('/', '.'), utf[ni]});
            }
        }
    }
}
