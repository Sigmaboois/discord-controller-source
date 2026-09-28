package com.juicy.discordcontroller;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Plays a UI sound across Minecraft 1.21.9–1.21.11. The
 * {@code PositionedSoundInstance.ui(...)} factory and {@code SoundManager.play(...)}
 * overloads changed between these versions, so this resolves whatever variant the
 * running version exposes via reflection and fails silently if none matches.
 */
public final class Sounds {

    private Sounds() {
    }

    public static void ui(MinecraftClient mc, RegistryEntry<SoundEvent> sound, float pitch) {
        try {
            Object inst = createUi(sound, pitch);
            if (inst != null) {
                play(mc.getSoundManager(), inst);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object createUi(RegistryEntry<SoundEvent> sound, float pitch) throws Exception {
        Class<?> psi = Class.forName("net.minecraft.client.sound.PositionedSoundInstance");
        Object event = sound.value();
        for (Method m : psi.getMethods()) {
            if (!m.getName().equals("ui") || !Modifier.isStatic(m.getModifiers())) {
                continue;
            }
            Class<?>[] p = m.getParameterTypes();
            Object first = firstArg(p[0], sound, event);
            if (first == null) {
                continue;
            }
            if (p.length == 2 && p[1] == float.class) {
                return m.invoke(null, first, pitch);            // ui(sound, pitch)
            }
            if (p.length == 3 && p[1] == float.class && p[2] == float.class) {
                return m.invoke(null, first, 0.25f, pitch);      // ui(sound, volume, pitch)
            }
        }
        return null;
    }

    private static Object firstArg(Class<?> want, RegistryEntry<SoundEvent> entry, Object event) {
        if (want.isInstance(entry)) {
            return entry;
        }
        if (want.isInstance(event)) {
            return event;
        }
        return null;
    }

    private static void play(SoundManager sm, Object inst) throws Exception {
        for (Method m : sm.getClass().getMethods()) {
            if (!m.getName().equals("play")) {
                continue;
            }
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 1 && p[0].getName().endsWith("SoundInstance")) {
                m.invoke(sm, inst);
                return;
            }
            if (p.length == 2 && p[0].getName().endsWith("SoundInstance") && p[1] == int.class) {
                m.invoke(sm, inst, 0);
                return;
            }
        }
    }
}
