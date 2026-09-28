package com.juicy.discordcontroller;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** On-screen armed/cooldown chip plus transient alert toasts, drawn over the HUD. */
public final class HudOverlay {

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

        AlertManager am = AlertManager.get();
        int y = 4;
        if (mc.currentScreen == null) {
            long cd = am.autoCooldownRemainingMs();
            String chip = null;
            int col = 0xFFFFFFFF;
            int dot = 0xFF57E28A;
            if (cd > 0) {
                chip = "COOLDOWN " + (cd / 1000 + 1) + "s";
                col = 0xFFAAB0C8;
                dot = 0xFFFFC857;
            } else if (cfg.autoAlertEnabled) {
                chip = "ALERT ARMED";
                col = 0xFF57E28A;
                dot = 0xFF57E28A;
            }
            if (chip != null) {
                int w = tr.getWidth(chip) + 18;
                chipBg(ctx, 4, y, w, 14, 0xC8121422);
                float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 300.0);
                ctx.fill(4 + 6, y + 5, 4 + 10, y + 9, alpha(dot, 0.4f + 0.6f * pulse));
                ctx.drawTextWithShadow(tr, Text.literal(chip), 4 + 14, y + 3, col);
                y += 18;
            }

            if (am.inCombat()) {
                String atk = am.combatAttacker();
                int hits = am.combatHits();
                int cnt = am.combatAttackerCount();
                String line = "⚔ " + (atk != null ? atk : "Unknown") + " ×" + hits
                        + (cnt > 1 ? "  (" + cnt + " players)" : "");
                int w = tr.getWidth(line) + 18;
                chipBg(ctx, 4, y, w, 14, 0xC8121422);
                float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 200.0);
                ctx.fill(4 + 6, y + 5, 4 + 10, y + 9, alpha(0xFFFF6B6B, 0.4f + 0.6f * pulse));
                ctx.drawTextWithShadow(tr, Text.literal(line), 4 + 14, y + 3, 0xFFFF9AA2);
            }
        }

        long age = System.currentTimeMillis() - toastAt;
        if (!toastMsg.isEmpty() && age < 4000) {
            float t = age / 4000f;
            float a = t < 0.1f ? t / 0.1f : (t > 0.8f ? (1 - t) / 0.2f : 1f);
            int w = tr.getWidth(toastMsg) + 24;
            int x = (sw - w) / 2;
            int ty = 18 - (int) ((1 - Math.min(1, age / 200f)) * 6);
            chipBg(ctx, x, ty, w, 18, alpha(0xE6121422, a));
            ctx.fill(x, ty, x + 3, ty + 18, alpha(toastColor, a));
            ctx.drawTextWithShadow(tr, Text.literal(toastMsg), x + 12, ty + 5, alpha(0xFFFFFFFF, a));
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
