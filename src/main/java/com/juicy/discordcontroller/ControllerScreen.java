package com.juicy.discordcontroller;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Animated OLED + neon menu (webhook-only). Immediate-mode with hotspot hit-testing. */
public class ControllerScreen extends Screen {

    private static final int BG = 0xF0000000;
    private static final int PANEL = 0xFF060609;
    private static final int CARD = 0xFF0C0D13;
    private static final int CARD_HI = 0xFF15171F;
    private static final int NAV_SEL = 0xFF121420;
    private static final int ACCENT = 0xFF00E6FF;
    private static final int ACCENT2 = 0xFFFF3DDA;
    private static final int BTN = 0xFF101219;
    private static final int BTN_HI = 0xFF191C27;
    private static final int TXT = 0xFFF3F6FF;
    private static final int TXT_DIM = 0xFF9AA0B4;
    private static final int TXT_MUTE = 0xFF565C6E;
    private static final int OK = 0xFF00FFA3;
    private static final int DANGER = 0xFFFF3B5C;
    private static final int WARN = 0xFFFFC24B;
    private static final int TRACK_OFF = 0xFF22252F;

    private static final int[] NAV_ICON = {0xFF00E6FF, 0xFFFF3DDA, 0xFF00FFA3, 0xFFB05CFF};

    private static final Identifier LOGO = Identifier.of("discordcontroller", "textures/gui/logo.png");
    private static final Identifier FONT = Identifier.of("discordcontroller", "gui");
    private static final net.minecraft.text.StyleSpriteSource FONT_SRC =
            new net.minecraft.text.StyleSpriteSource.Font(FONT);

    private static net.minecraft.text.MutableText ft(String s) {
        return Text.literal(s).styled(st -> st.withFont(FONT_SRC));
    }

    private enum Tab {
        DELIVERY("Webhooks"), TUNING("Tuning"), ALERTS("Alerts"), OPTIONS("Options");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private record Hotspot(String id, int x, int y, int w, int h, Runnable action) {
    }

    private record Row(String title, String sub, Runnable onRemove) {
    }

    private final DiscordConfig cfg = DiscordControllerMod.getConfig();
    private final List<Hotspot> hotspots = new ArrayList<>();
    private final Map<String, Float> anim = new HashMap<>();
    private final Map<String, Boolean> hoverPrev = new HashMap<>();

    private Tab tab = Tab.DELIVERY;
    private int px, py, pw, ph, navW, contentX, contentW;

    private final long openTime = System.currentTimeMillis();
    private long lastFrame = openTime;
    private float dt;
    private float ea;
    private long tabSwitchTime = openTime;
    private String flashId;
    private long flashTime;

    private TextFieldWidget webhookField, brandField, footerField;
    private int scroll;
    private int[] viewport;
    private int listMax;

    private String status = "";
    private int statusColor = TXT_DIM;
    private boolean modal;

    public ControllerScreen() {
        super(Text.literal("Discord Controller"));
    }

    @Override
    protected void init() {
        pw = Math.min(460, width - 40);
        ph = Math.min(300, height - 40);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        navW = 120;
        contentX = px + navW + 6;
        contentW = pw - navW - 12;
        rebuildWidgets();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private int contentTop() {
        return py + 50;
    }

    private void rebuildWidgets() {
        clearChildren();
        webhookField = brandField = footerField = null;
        scroll = 0;
        int fx = contentX + 20;
        int fw = contentW - 28;
        switch (tab) {
            case DELIVERY -> {
                webhookField = field(fx, contentTop() + 22, fw - 46, 18, "Paste a webhook URL");
                addSelectableChild(webhookField);
            }
            case OPTIONS -> {
                brandField = field(fx, contentTop() + 78, fw - 52, 18, "Brand");
                brandField.setText(cfg.brand);
                footerField = field(fx, contentTop() + 120, fw - 52, 18, "Footer");
                footerField.setText(cfg.footer);
                addSelectableChild(brandField);
                addSelectableChild(footerField);
            }
            default -> {
            }
        }
    }

    private TextFieldWidget field(int x, int y, int w, int h, String placeholder) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, h, Text.literal(placeholder));
        f.setMaxLength(2000);
        f.setDrawsBackground(false);
        f.setEditableColor(TXT);
        f.setPlaceholder(Text.literal("§8" + placeholder));
        return f;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        ea = ease(clamp01((now - openTime) / 220f));
        hotspots.clear();
        viewport = null;
        modal = false;

        ctx.fill(0, 0, width, height, argbA(BG, ea));

        for (int i = 6; i >= 1; i--) {
            roundRect(ctx, px - i, py - i, pw + i * 2, ph + i * 2, 9 + i, argbA(ACCENT, ea * 0.14f / i));
        }
        roundRect(ctx, px, py, pw, ph, 8, argbA(PANEL, ea));
        roundRect(ctx, px, py, pw, 1, 0, argbA(ACCENT, ea * 0.5f));

        for (int i = 0; i < 14; i++) {
            double t = now / 1000.0;
            float fxp = (float) (Math.sin(t * 0.3 + i * 1.7) * 0.5 + 0.5);
            float fyp = (float) (Math.cos(t * 0.23 + i * 2.1) * 0.5 + 0.5);
            int ppx = px + 8 + (int) (fxp * (pw - 16));
            int ppy = py + 46 + (int) (fyp * (ph - 54));
            int sz = 1 + (i % 3);
            ctx.fill(ppx, ppy, ppx + sz, ppy + sz, argbA((i % 2 == 0) ? ACCENT : ACCENT2, ea * 0.10f));
        }

        drawHeader(ctx, mouseX, mouseY);
        drawNav(ctx, mouseX, mouseY);
        ctx.fill(px + navW, py + 10, px + navW + 1, py + ph - 10, argbA(0xFF1B1F2B, ea));

        float baseEa = ea;
        ea = baseEa * ease(clamp01((now - tabSwitchTime) / 180f));
        switch (tab) {
            case DELIVERY -> drawDelivery(ctx, mouseX, mouseY, delta);
            case TUNING -> drawTuning(ctx, mouseX, mouseY);
            case ALERTS -> drawAlerts(ctx, mouseX, mouseY);
            case OPTIONS -> drawOptions(ctx, mouseX, mouseY, delta);
        }
        ea = baseEa;

        if (!cfg.seenSetup) {
            drawSetupOverlay(ctx, mouseX, mouseY);
        }
    }

    private void drawHeader(DrawContext ctx, int mouseX, int mouseY) {
        int cx = px + 14;
        int cy = py + 12;
        boolean logo = false;
        try {
            ctx.drawTexturedQuad(LOGO, cx, cy, cx + 18, cy + 18, 0f, 0f, 1f, 1f);
            logo = true;
        } catch (Exception ignored) {
        }
        if (!logo) {
            roundRect(ctx, cx, cy + 1, 14, 14, 4, argbA(ACCENT, ea));
        }
        ctx.drawTextWithShadow(textRenderer, ft("Discord Controller"), cx + 24, py + 12, argbA(TXT, ea));
        ctx.drawTextWithShadow(textRenderer, ft("§8" + cfg.brand), cx + 24, py + 24, argbA(TXT_MUTE, ea));

        int uy = py + 38;
        float phase = (float) ((System.currentTimeMillis() % 3000) / 3000.0);
        int mid = lerpColor(ACCENT, ACCENT2, (float) (0.5 + 0.5 * Math.sin(phase * Math.PI * 2)));
        glow(ctx, px + 14, uy - 1, navW - 22, 3, 1, ACCENT, 0.22f);
        ctx.fillGradient(px + 14, uy, px + navW - 8, uy + 1, argbA(ACCENT, ea), argbA(mid, ea));

        drawButton(ctx, "close", px + pw - 26, py + 12, 16, 16, "×", false, mouseX, mouseY, this::close);
    }

    private void drawNav(DrawContext ctx, int mouseX, int mouseY) {
        int y = py + 50;
        int h = 26;
        int gap = 4;
        float target = 0;
        int i = 0;
        for (Tab t : Tab.values()) {
            int rowY = y + i * (h + gap);
            if (t == tab) {
                target = rowY;
            }
            boolean hover = inside(mouseX, mouseY, px + 10, rowY, navW - 16, h);
            hoverSound("nav:" + t, hover);
            float hv = approach("nav:" + t, hover || t == tab ? 1 : 0, 16);
            if (t == tab) {
                roundRect(ctx, px + 10, rowY, navW - 16, h, 6, argbA(NAV_SEL, ea));
            } else if (hv > 0.01f) {
                roundRect(ctx, px + 10, rowY, navW - 16, h, 6, argbA(NAV_SEL, ea * hv * 0.6f));
            }
            int ic = NAV_ICON[i % NAV_ICON.length];
            roundRect(ctx, px + 18, rowY + (h - 8) / 2 - 1, 8, 8, 4, argbA(t == tab ? ic : lerpColor(ic, TXT_MUTE, 0.45f), ea));
            ctx.drawTextWithShadow(textRenderer, ft(t.label), px + 32, rowY + (h - 8) / 2, argbA(lerpColor(TXT_DIM, TXT, hv), ea));
            Tab tt = t;
            addHotspot("nav:" + tt, px + 10, rowY, navW - 16, h, () -> switchTab(tt));
            i++;
        }
        float iy = approach("nav-indicator", target, 18);
        glow(ctx, px + 10, (int) iy + 5, 3, h - 10, 1, ACCENT, 0.3f);
        roundRect(ctx, px + 10, (int) iy + 5, 3, h - 10, 1, argbA(ACCENT, ea));
    }

    private void switchTab(Tab t) {
        if (t != tab) {
            tab = t;
            tabSwitchTime = System.currentTimeMillis();
            status = "";
            rebuildWidgets();
        }
    }

    private void drawDelivery(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int x = contentX + 14;
        label(ctx, "WEBHOOKS", x, contentTop() + 4);
        fieldCard(ctx, webhookField, delta, mouseX, mouseY);
        drawButton(ctx, "wadd", x + contentW - 28 - 40, contentTop() + 22, 40, 18, "Add", true, mouseX, mouseY, this::addWebhook);
        ctx.drawTextWithShadow(textRenderer,
                ft("§8Channel → Edit → Integrations → Webhooks → Copy URL"), x, contentTop() + 46, argbA(TXT_MUTE, ea));

        List<Row> rows = new ArrayList<>();
        for (String url : cfg.alertWebhooks) {
            rows.add(new Row("Webhook", shorten(url), () -> { cfg.alertWebhooks.remove(url); cfg.save(); }));
        }
        int top = contentTop() + 60;
        int bottom = py + ph - 52;
        drawList(ctx, x, top, contentW - 28, bottom - top, rows, mouseX, mouseY, "No webhooks yet.");

        toggleRow(ctx, "ping", x, py + ph - 48, "Prepend @everyone", cfg.alertPingEveryone, mouseX, mouseY,
                () -> { cfg.alertPingEveryone = !cfg.alertPingEveryone; cfg.save(); });
        drawButton(ctx, "dtest", x, py + ph - 24, 100, 18, "Send Test", true, mouseX, mouseY,
                () -> AlertManager.get().sendTestAlert(MinecraftClient.getInstance()));
        if (!status.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, ft(status), x + 110, py + ph - 20, argbA(statusColor, ea));
        }
    }

    private void drawTuning(DrawContext ctx, int mouseX, int mouseY) {
        int x = contentX + 14;
        int w = contentW - 28;
        toggleRow(ctx, "auto", x, contentTop() + 6, "Automatic attack-alert", cfg.autoAlertEnabled, mouseX, mouseY,
                () -> { cfg.autoAlertEnabled = !cfg.autoAlertEnabled; cfg.save(); });
        long cd = AlertManager.get().autoCooldownRemainingMs();
        if (cd > 0) {
            drawPill(ctx, x, contentTop() + 34, "COOLDOWN " + (cd / 1000 + 1) + "s", WARN);
        } else if (cfg.autoAlertEnabled) {
            drawPill(ctx, x, contentTop() + 34, "ARMED", OK);
        } else {
            drawPill(ctx, x, contentTop() + 34, "OFF", TXT_MUTE);
        }
        int y = contentTop() + 54;
        int s = 26;
        stepper(ctx, "hits", x, y, w, "Trigger above hits", String.valueOf(cfg.alertHitThreshold), mouseX, mouseY,
                () -> { cfg.alertHitThreshold = Math.max(1, cfg.alertHitThreshold - 1); cfg.save(); },
                () -> { cfg.alertHitThreshold++; cfg.save(); });
        stepper(ctx, "hp", x, y + s, w, "Health lost (hearts)", trim(cfg.alertHealthLoss / 2.0), mouseX, mouseY,
                () -> { cfg.alertHealthLoss = Math.max(1, cfg.alertHealthLoss - 1); cfg.save(); },
                () -> { cfg.alertHealthLoss += 1; cfg.save(); });
        stepper(ctx, "rad", x, y + s * 2, w, "Attacker radius (blocks)", trim(cfg.attackRadius), mouseX, mouseY,
                () -> { cfg.attackRadius = Math.max(1, cfg.attackRadius - 1); cfg.save(); },
                () -> { cfg.attackRadius += 1; cfg.save(); });
        stepper(ctx, "win", x, y + s * 3, w, "Time window (seconds)", String.valueOf(cfg.alertWindowSeconds), mouseX, mouseY,
                () -> { cfg.alertWindowSeconds = Math.max(1, cfg.alertWindowSeconds - 1); cfg.save(); },
                () -> { cfg.alertWindowSeconds++; cfg.save(); });
        stepper(ctx, "cd", x, y + s * 4, w, "Auto cooldown (seconds)", String.valueOf(cfg.alertCooldownSeconds), mouseX, mouseY,
                () -> { cfg.alertCooldownSeconds = Math.max(0, cfg.alertCooldownSeconds - 5); cfg.save(); },
                () -> { cfg.alertCooldownSeconds += 5; cfg.save(); });
        int bw = (w - 8) / 2;
        drawButton(ctx, "now", x, py + ph - 26, bw, 20, "Alert Now", false, mouseX, mouseY,
                () -> AlertManager.get().sendManualAlert(MinecraftClient.getInstance()));
        drawButton(ctx, "test", x + bw + 8, py + ph - 26, bw, 20, "Send Test", true, mouseX, mouseY,
                () -> AlertManager.get().sendTestAlert(MinecraftClient.getInstance()));
    }

    private void drawAlerts(DrawContext ctx, int mouseX, int mouseY) {
        int x = contentX + 14;
        toggleRow(ctx, "lowhp", x, contentTop() + 6, "Low-health emergency alert", cfg.alertLowHealthEnabled, mouseX, mouseY,
                () -> { cfg.alertLowHealthEnabled = !cfg.alertLowHealthEnabled; cfg.save(); });
        stepper(ctx, "lowhpv", x, contentTop() + 34, contentW - 28, "Emergency at (hearts)", trim(cfg.alertLowHealthHp / 2.0), mouseX, mouseY,
                () -> { cfg.alertLowHealthHp = Math.max(1, cfg.alertLowHealthHp - 1); cfg.save(); },
                () -> { cfg.alertLowHealthHp += 1; cfg.save(); });
        toggleRow(ctx, "death", x, contentTop() + 62, "Alert when you are killed", cfg.alertOnDeath, mouseX, mouseY,
                () -> { cfg.alertOnDeath = !cfg.alertOnDeath; cfg.save(); });
        toggleRow(ctx, "asnd", x, contentTop() + 90, "Play a sound when an alert fires", cfg.alertSound, mouseX, mouseY,
                () -> { cfg.alertSound = !cfg.alertSound; cfg.save(); });
        toggleRow(ctx, "armhud", x, contentTop() + 118, "Show 'ARMED' HUD chip", cfg.hudArmedChip, mouseX, mouseY,
                () -> { cfg.hudArmedChip = !cfg.hudArmedChip; cfg.save(); });
        ctx.drawTextWithShadow(textRenderer,
                ft("§8Emergency + death fire even if auto-alert is off."), x, contentTop() + 148, argbA(TXT_MUTE, ea));
    }

    private void drawOptions(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int x = contentX + 14;
        toggleRow(ctx, "hud", x, contentTop() + 6, "On-screen HUD", cfg.hudEnabled, mouseX, mouseY,
                () -> { cfg.hudEnabled = !cfg.hudEnabled; cfg.save(); });
        toggleRow(ctx, "snd", x, contentTop() + 34, "Menu sounds", cfg.uiSounds, mouseX, mouseY,
                () -> { cfg.uiSounds = !cfg.uiSounds; cfg.save(); });
        label(ctx, "BRAND", x, contentTop() + 66);
        fieldCard(ctx, brandField, delta, mouseX, mouseY);
        label(ctx, "FOOTER", x, contentTop() + 108);
        fieldCard(ctx, footerField, delta, mouseX, mouseY);
        drawButton(ctx, "brsave", x + contentW - 28 - 50, contentTop() + 78, 50, 40, "Save", true, mouseX, mouseY,
                () -> {
                    if (!brandField.getText().isBlank()) {
                        cfg.brand = brandField.getText().trim();
                    }
                    if (!footerField.getText().isBlank()) {
                        cfg.footer = footerField.getText().trim();
                    }
                    cfg.save();
                    flash("Saved.", OK);
                });
        if (!status.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, ft(status), x, py + ph - 16, argbA(statusColor, ea));
        }
    }

    private void drawSetupOverlay(DrawContext ctx, int mouseX, int mouseY) {
        modal = true;
        hotspots.clear();
        ctx.fill(px, py, px + pw, py + ph, argbA(0xE6000000, ea));
        int ow = 320;
        int oh = 150;
        int ox = px + (pw - ow) / 2;
        int oy = py + (ph - oh) / 2;
        for (int i = 5; i >= 1; i--) {
            roundRect(ctx, ox - i, oy - i, ow + i * 2, oh + i * 2, 9 + i, argbA(ACCENT, ea * 0.12f / i));
        }
        roundRect(ctx, ox, oy, ow, oh, 8, argbA(PANEL, ea));
        roundRect(ctx, ox, oy, ow, 1, 0, argbA(ACCENT, ea));
        int cx = ox + ow / 2;
        ctx.drawCenteredTextWithShadow(textRenderer, ft("§lWelcome to Discord Controller"), cx, oy + 16, argbA(TXT, ea));
        String[] lines = {
                "§71. §fMake a webhook in your Discord channel",
                "§72. §fWebhooks tab §7→ paste it → Add",
                "§73. §fTuning §7→ arm the alert, hit Send Test",
        };
        int ly = oy + 40;
        for (String l : lines) {
            ctx.drawTextWithShadow(textRenderer, ft(l), ox + 24, ly, argbA(TXT_DIM, ea));
            ly += 16;
        }
        ctx.drawCenteredTextWithShadow(textRenderer, ft("§8Bind a key in Options → Controls to open fast."), cx, oy + oh - 40, argbA(TXT_MUTE, ea));
        drawButton(ctx, "setupdone", cx - 70, oy + oh - 28, 140, 20, "Get Started", true, mouseX, mouseY,
                () -> { cfg.seenSetup = true; cfg.save(); });
    }

    private void label(DrawContext ctx, String s, int x, int y) {
        ctx.drawTextWithShadow(textRenderer, ft("§l" + s), x, y, argbA(TXT_MUTE, ea));
    }

    private void drawPill(DrawContext ctx, int x, int y, String text, int color) {
        int w = textRenderer.getWidth(text) + 18;
        glow(ctx, x, y, w, 14, 7, color, 0.16f);
        roundRect(ctx, x, y, w, 14, 7, argbA(0xCC060609, ea));
        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 300.0);
        ctx.fill(x + 6, y + 5, x + 10, y + 9, argbA(color, ea * (0.4f + 0.6f * pulse)));
        ctx.drawTextWithShadow(textRenderer, ft(text), x + 14, y + 3, argbA(color, ea));
    }

    private void fieldCard(DrawContext ctx, TextFieldWidget f, float delta, int mouseX, int mouseY) {
        if (f == null) {
            return;
        }
        int cx = f.getX() - 8;
        int cy = f.getY() - 5;
        int cw = f.getWidth() + 14;
        roundRect(ctx, cx, cy, cw, 28, 6, argbA(CARD, ea));
        if (f.isFocused()) {
            glow(ctx, cx, cy, cw, 28, 6, ACCENT, 0.10f);
            roundRect(ctx, cx, cy, cw, 1, 0, argbA(ACCENT, ea));
            roundRect(ctx, cx, cy + 27, cw, 1, 0, argbA(ACCENT, ea));
        }
        f.render(ctx, mouseX, mouseY, delta);
    }

    private void drawButton(DrawContext ctx, String id, int x, int y, int w, int h, String text,
                            boolean accent, int mouseX, int mouseY, Runnable action) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        hoverSound("btn:" + id, hover);
        float hv = approach("btn:" + id, hover ? 1 : 0, 16);
        int base = accent ? lerpColor(ACCENT, lerpColor(ACCENT, ACCENT2, 0.35f), hv) : lerpColor(BTN, BTN_HI, hv);
        float flash = (flashId != null && flashId.equals("btn:" + id)) ? clamp01(1f - (System.currentTimeMillis() - flashTime) / 220f) : 0f;
        int br = h / 2;
        if (accent) {
            glow(ctx, x, y, w, h, br, lerpColor(ACCENT, ACCENT2, 0.3f), 0.10f + 0.12f * hv);
        }
        if (flash > 0f) {
            base = lerpColor(base, 0xFFFFFFFF, flash * 0.35f);
        }
        roundRect(ctx, x, y, w, h, br, argbA(base, ea));
        ctx.drawCenteredTextWithShadow(textRenderer, ft(text), x + w / 2, y + (h - 8) / 2, argbA(accent ? 0xFF04060A : TXT, ea));
        addHotspot("btn:" + id, x, y, w, h, action);
    }

    private void toggleRow(DrawContext ctx, String id, int x, int y, String labelText, boolean on, int mouseX, int mouseY, Runnable toggle) {
        int w = contentW - 28;
        boolean hover = inside(mouseX, mouseY, x, y, w, 24);
        hoverSound("tglrow:" + id, hover);
        roundRect(ctx, x, y, w, 24, 6, argbA(lerpColor(CARD, CARD_HI, hover ? 1 : 0), ea));
        ctx.drawTextWithShadow(textRenderer, ft(labelText), x + 10, y + 8, argbA(TXT, ea));
        drawToggle(ctx, id, x + w - 42, y + 4, on);
        addHotspot("tglrow:" + id, x, y, w, 24, toggle);
    }

    private void drawToggle(DrawContext ctx, String id, int tx, int ty, boolean on) {
        int tw = 34;
        int th = 16;
        float p = approach("tgl:" + id, on ? 1 : 0, 14);
        if (p > 0.05f) {
            glow(ctx, tx, ty, tw, th, th / 2, ACCENT, 0.20f * p);
        }
        roundRect(ctx, tx, ty, tw, th, th / 2, argbA(lerpColor(TRACK_OFF, ACCENT, p), ea));
        int knob = tx + 2 + (int) ((tw - th) * p);
        roundRect(ctx, knob, ty + 2, th - 4, th - 4, (th - 4) / 2, argbA(0xFFFFFFFF, ea));
    }

    private void stepper(DrawContext ctx, String id, int x, int y, int w, String labelText, String value, int mouseX, int mouseY, Runnable minus, Runnable plus) {
        roundRect(ctx, x, y, w, 22, 6, argbA(CARD, ea));
        int bw = 18;
        int by = y + 3;
        int px2 = x + w - bw - 6;
        int vx = px2 - 42;
        int mx = vx - bw - 4;
        String lbl = textRenderer.trimToWidth(labelText, Math.max(20, mx - (x + 10) - 4));
        ctx.drawTextWithShadow(textRenderer, ft(lbl), x + 10, y + 7, argbA(TXT, ea));
        drawButton(ctx, id + "-", mx, by, bw, 16, "-", false, mouseX, mouseY, minus);
        roundRect(ctx, vx, by, 38, 16, 6, argbA(CARD_HI, ea));
        ctx.drawCenteredTextWithShadow(textRenderer, ft(value), vx + 19, by + 4, argbA(ACCENT2, ea));
        drawButton(ctx, id + "+", px2, by, bw, 16, "+", false, mouseX, mouseY, plus);
    }

    private void drawList(DrawContext ctx, int x, int y, int w, int h, List<Row> rows, int mouseX, int mouseY, String empty) {
        roundRect(ctx, x, y, w, h, 6, argbA(0x40000000, ea));
        viewport = new int[]{x, y, w, h};
        int rowH = 26;
        int pad = 4;
        int contentH = rows.size() * (rowH + pad);
        listMax = Math.max(0, contentH - h + pad);
        scroll = Math.max(0, Math.min(scroll, listMax));
        if (rows.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, ft("§8" + empty), x + w / 2, y + h / 2 - 4, argbA(TXT_MUTE, ea));
            return;
        }
        ctx.enableScissor(x, y, x + w, y + h);
        int ry = y + pad - scroll;
        for (Row row : rows) {
            if (ry + rowH >= y && ry <= y + h) {
                boolean inView = inside(mouseX, mouseY, x, y, w, h);
                boolean hover = inside(mouseX, mouseY, x + pad, ry, w - pad * 2, rowH) && inView;
                roundRect(ctx, x + pad, ry, w - pad * 2, rowH, 5, argbA(lerpColor(CARD, CARD_HI, hover ? 1 : 0), ea));
                roundRect(ctx, x + pad, ry, 2, rowH, 0, argbA(ACCENT, ea * 0.7f));
                ctx.drawTextWithShadow(textRenderer, ft(row.title), x + pad + 8, ry + 5, argbA(TXT, ea));
                if (row.sub != null && !row.sub.isEmpty()) {
                    ctx.drawTextWithShadow(textRenderer, ft("§8" + row.sub), x + pad + 8, ry + 15, argbA(TXT_MUTE, ea));
                }
                int rbx = x + w - pad - 18;
                int rby = ry + (rowH - 16) / 2;
                boolean rh = inside(mouseX, mouseY, rbx, rby, 16, 16) && inView;
                roundRect(ctx, rbx, rby, 16, 16, 8, argbA(lerpColor(BTN, DANGER, rh ? 1 : 0), ea));
                ctx.drawCenteredTextWithShadow(textRenderer, ft("×"), rbx + 8, rby + 4, argbA(TXT, ea));
                if (rby >= y && rby + 16 <= y + h) {
                    addHotspot("rm:" + row.sub, rbx, rby, 16, 16, row.onRemove);
                }
            }
            ry += rowH + pad;
        }
        ctx.disableScissor();
        if (listMax > 0) {
            int trackH = h - 8;
            int barH = Math.max(20, (int) (trackH * (h / (float) contentH)));
            int barY = y + 4 + (int) ((trackH - barH) * (scroll / (float) listMax));
            roundRect(ctx, x + w - 4, y + 4, 2, trackH, 1, argbA(0x30FFFFFF, ea));
            roundRect(ctx, x + w - 4, barY, 2, barH, 1, argbA(ACCENT, ea));
        }
    }

    private void addWebhook() {
        String u = webhookField.getText().trim();
        if (!u.startsWith("https://") || !u.contains("/webhooks/")) {
            flash("That isn't a webhook URL.", DANGER);
            return;
        }
        if (!cfg.alertWebhooks.contains(u)) {
            cfg.alertWebhooks.add(u);
            cfg.save();
        }
        webhookField.setText("");
        flash("Webhook added.", OK);
    }

    private void flash(String msg, int color) {
        status = msg;
        statusColor = color;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            double mx = click.x();
            double my = click.y();
            for (int i = hotspots.size() - 1; i >= 0; i--) {
                Hotspot h = hotspots.get(i);
                if (mx >= h.x && mx < h.x + h.w && my >= h.y && my < h.y + h.h) {
                    flashId = h.id;
                    flashTime = System.currentTimeMillis();
                    playClick();
                    h.action.run();
                    return true;
                }
            }
        }
        if (modal) {
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        if (viewport != null && inside((int) mx, (int) my, viewport[0], viewport[1], viewport[2], viewport[3]) && listMax > 0) {
            scroll = Math.max(0, Math.min(listMax, scroll - (int) (vAmount * 18)));
            return true;
        }
        return super.mouseScrolled(mx, my, hAmount, vAmount);
    }

    private void addHotspot(String id, int x, int y, int w, int h, Runnable action) {
        hotspots.add(new Hotspot(id, x, y, w, h, action));
    }

    private void hoverSound(String id, boolean hover) {
        Boolean prev = hoverPrev.put(id, hover);
        if (hover && (prev == null || !prev)) {
            playHover();
        }
    }

    private void playClick() {
        playSound(1.0f);
    }

    private void playHover() {
        playSound(1.5f);
    }

    private void playSound(float pitch) {
        if (cfg.uiSounds) {
            Sounds.ui(MinecraftClient.getInstance(), SoundEvents.UI_BUTTON_CLICK, pitch);
        }
    }

    private void glow(DrawContext ctx, int x, int y, int w, int h, int r, int color, float strength) {
        for (int i = 3; i >= 1; i--) {
            roundRect(ctx, x - i * 2, y - i * 2, w + i * 4, h + i * 4, r + i * 2, argbA(color, ea * strength / i));
        }
    }

    private void roundRect(DrawContext ctx, int x, int y, int w, int h, int r, int argb) {
        if (w <= 0 || h <= 0) {
            return;
        }
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r <= 1) {
            ctx.fill(x, y, x + w, y + h, argb);
            return;
        }
        ctx.fill(x, y + r, x + w, y + h - r, argb);
        for (int dy = 0; dy < r; dy++) {
            int inset = (int) Math.round(r - Math.sqrt((double) r * r - (double) (r - dy) * (r - dy)));
            ctx.fill(x + inset, y + dy, x + w - inset, y + dy + 1, argb);
            ctx.fill(x + inset, y + h - 1 - dy, x + w - inset, y + h - dy, argb);
        }
    }

    private float approach(String key, float target, float speed) {
        float cur = anim.getOrDefault(key, target);
        cur += (target - cur) * (1f - (float) Math.exp(-dt * speed));
        anim.put(key, cur);
        return cur;
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int lerpColor(int a, int b, float t) {
        t = clamp01(t);
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    private static int argbA(int c, float mul) {
        int a = (int) (((c >>> 24) & 0xFF) * clamp01(mul));
        return (a << 24) | (c & 0xFFFFFF);
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    private static float ease(float t) {
        return 1f - (float) Math.pow(1 - t, 3);
    }

    private static String shorten(String s) {
        return s != null && s.length() > 38 ? s.substring(0, 38) + "…" : s;
    }

    private static String trim(double d) {
        return d == Math.floor(d) ? String.valueOf((int) d) : String.valueOf(d);
    }
}
