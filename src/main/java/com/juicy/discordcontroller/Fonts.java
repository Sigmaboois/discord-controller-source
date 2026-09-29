package com.juicy.discordcontroller;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class Fonts {

    private static final Method WITH_FONT;
    private static final Constructor<?> FONT_CTOR;

    static {
        Method wf = null;
        Constructor<?> ctor = null;
        try {
            Class<?> source = Class.forName("net.minecraft.text.StyleSpriteSource");
            Class<?> font = Class.forName("net.minecraft.text.StyleSpriteSource$Font");
            wf = Style.class.getMethod("withFont", source);
            ctor = font.getConstructor(Identifier.class);
        } catch (Throwable ignored) {
            try {
                wf = Style.class.getMethod("withFont", Identifier.class);
            } catch (Throwable ignored2) {
                wf = null;
            }
        }
        WITH_FONT = wf;
        FONT_CTOR = ctor;
    }

    private Fonts() {
    }

    public static MutableText styled(String text, Identifier fontId) {
        MutableText t = Text.literal(text);
        if (WITH_FONT == null) {
            return t;
        }
        return t.styled(st -> {
            try {
                Object arg = FONT_CTOR != null ? FONT_CTOR.newInstance(fontId) : fontId;
                return (Style) WITH_FONT.invoke(st, arg);
            } catch (Throwable e) {
                return st;
            }
        });
    }
}
