package com.juicy.discordcontroller;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class HudOverlay {

    private static final Identifier FONT = Identifier.of("discordcontroller", "gui");
    private static final net.minecraft.text.StyleSpriteSource FONT_SRC =
            new net.minecraft.text.StyleSpriteSource.Font(FONT);

    private static final int NEON_GREEN = 0xFF00FFA3;
    private static final int NEON_AMBER = 0xFFFFC24B;
    private static final int NEON_RED = 0xFFFF3B5C;

    private static String toastMsg = "";
    private static int toastColor = 0xFFFFFFFF;
    private static long toastAt = 0L;

    private HudOverlay() {
    }

    public static void toast(String msg, int color) {
        toastMsg = msg;
        toastColor = color;
        toastAt = System.currentTimeMillis();
    }

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return;
        }
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        if (!cfg.hudEnabled) {
            return;
        }
        var tr = mc.textRenderer;
        int sw = ctx.getScaledWindowWidth();
        long now = System.currentTimeMillis();

        AlertManager am = AlertManager.get();
        int y = 4;
        if (mc.currentScreen == null) {
            long cd = am.autoCooldownRemainingMs();
            String chip = null;
            int neon = NEON_GREEN;
            if (cd > 0) {
                chip = "COOLDOWN " + (cd / 1000 + 1) + "s";
                neon = NEON_AMBER;
            } else if (cfg.autoAlertEnabled) {
                chip = "ALERT ARMED";
                neon = NEON_GREEN;
            }
            if (chip != null && cfg.hudArmedChip) {
                drawChip(ctx, tr, 4, y, chip, neon, now, 300.0);
                y += 18;
            }
            if (am.inCombat()) {
                String atk = am.combatAttacker();
                int cnt = am.combatAttackerCount();
                String line = "⚔ " + (atk != null ? atk : "Unknown") + " ×" + am.combatHits()
                        + (cnt > 1 ? "  (" + cnt + " players)" : "");
                drawChip(ctx, tr, 4, y, line, NEON_RED, now, 200.0);
            }
        }

        long age = now - toastAt;
        if (!toastMsg.isEmpty() && age < 4000) {
            float t = age / 4000f;
            float a = t < 0.1f ? t / 0.1f : (t > 0.8f ? (1 - t) / 0.2f : 1f);
            int w = tr.getWidth(toastMsg) + 24;
            int x = (sw - w) / 2;
            int ty = 18 - (int) ((1 - Math.min(1, age / 200f)) * 6);
            glow(ctx, x, ty, w, 18, toastColor, 0.18f * a);
            chipBg(ctx, x, ty, w, 18, alpha(0xE6000000, a));
            ctx.fill(x, ty, x + 3, ty + 18, alpha(toastColor, a));
            ctx.drawTextWithShadow(tr, ft(toastMsg), x + 12, ty + 5, alpha(0xFFFFFFFF, a));
        }
    }

    private static void drawChip(DrawContext ctx, net.minecraft.client.font.TextRenderer tr,
                                 int x, int y, String text, int neon, long now, double period) {
        int w = tr.getWidth(text) + 20;
        glow(ctx, x, y, w, 14, neon, 0.16f);
        chipBg(ctx, x, y, w, 14, 0xE6000000);
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / period);
        ctx.fill(x + 6, y + 5, x + 10, y + 9, alpha(neon, 0.35f + 0.65f * pulse));
        ctx.drawTextWithShadow(tr, ft(text), x + 14, y + 3, neon);
    }

    private static net.minecraft.text.MutableText ft(String s) {
        return Text.literal(s).styled(st -> st.withFont(FONT_SRC));
    }

    private static void glow(DrawContext ctx, int x, int y, int w, int h, int color, float a) {
        for (int i = 3; i >= 1; i--) {
            ctx.fill(x - i * 2, y - i * 2, x + w + i * 2, y + h + i * 2, alpha(color, a / i));
        }
    }

    private static void chipBg(DrawContext ctx, int x, int y, int w, int h, int c) {
        ctx.fill(x + 1, y, x + w - 1, y + h, c);
        ctx.fill(x, y + 1, x + w, y + h - 1, c);
    }

    private static int alpha(int c, float m) {
        int a = (int) (((c >>> 24) & 0xFF) * Math.max(0, Math.min(1, m)));
        return (a << 24) | (c & 0xFFFFFF);
    }
}
