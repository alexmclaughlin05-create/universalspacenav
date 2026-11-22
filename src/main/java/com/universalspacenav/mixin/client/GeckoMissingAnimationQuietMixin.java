package com.universalspacenav.mixin.client;

import com.universalspacenav.UsnConfig;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Log-flood fix (changes nothing in how mobs look). When a mob's animation cannot be found, GeckoLib logs an error and
 * prints a 70-line stack trace on EVERY frame (about 120 times a second, about 1 GB of log per 40 minutes). This keeps
 * the first message and then repeats it at most once a minute with a count, and drops the repeated stack traces.
 */
@Pseudo
@Mixin(targets = "software.bernie.geckolib.animation.AnimationProcessor", remap = false)
public abstract class GeckoMissingAnimationQuietMixin {
    private static final ConcurrentHashMap<String, long[]> SEEN = new ConcurrentHashMap<>();

    @Redirect(method = "buildAnimationQueue", at = @At(value = "INVOKE", target = "Ljava/lang/RuntimeException;printStackTrace()V"), require = 0, remap = false)
    private void usn$noRepeatedStackTrace(RuntimeException e) {
        if (!on()) {
            e.printStackTrace();
            return;
        }
        String key = String.valueOf(e.getMessage());
        long[] st = SEEN.computeIfAbsent(key, k -> new long[]{0, 0});
        synchronized (st) {
            if (st[0] == 0) {
                st[0] = System.currentTimeMillis();
                e.printStackTrace(); // the first one is kept so the cause stays visible
            }
        }
    }

    @Redirect(method = "buildAnimationQueue", at = @At(value = "INVOKE", target = "Lorg/apache/logging/log4j/Logger;log(Lorg/apache/logging/log4j/Level;Ljava/lang/String;)V"), require = 0, remap = false)
    private void usn$throttledLog(Logger logger, Level level, String message) {
        if (!on()) {
            logger.log(level, message);
            return;
        }
        long[] st = SEEN.computeIfAbsent(message, k -> new long[]{0, 0});
        long now = System.currentTimeMillis();
        synchronized (st) {
            st[1]++;
            if (st[0] == 0 || now - st[0] >= 60_000L) {
                logger.log(level, message + (st[1] > 1 ? " (repeated " + st[1] + " times in the last minute; USN is hiding repeats)" : ""));
                st[0] = now;
                st[1] = 0;
            }
        }
    }

    private static boolean on() {
        try {
            return UsnConfig.CLIENT.quietGeckoMissing.get();
        } catch (Throwable t) {
            return false;
        }
    }
}
