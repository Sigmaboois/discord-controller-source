package com.juicy.discordcontroller;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ControllerScreen extends Screen {

    private static final int BG_TOP = 0xF0000000;
    private static final int BG_BOT = 0xF0000000;
    private static final int PANEL = 0xFF060609;
    private static final int PANEL_EDGE = 0xFF1B1F2B;
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

    private static final int[] AV_PALETTE = {
            0xFF00E6FF, 0xFFFF3DDA, 0xFF00FFA3, 0xFF4EA8FF, 0xFFFFC24B, 0xFFB05CFF, 0xFFFF7A3D, 0xFF3EF0D8
    };

    private static final Identifier LOGO = Identifier.of("discordcontroller", "textures/gui/logo.png");
    private static final Identifier FONT = Identifier.of("discordcontroller", "gui");

    private static net.minecraft.text.MutableText ft(String s) {
        return Fonts.styled(s, FONT);
    }

    private enum Tab {
        ACCOUNT("Account"), RECIPIENTS("Recipients"), TARGETS("Targets"),
        DELIVERY("Delivery"), TUNING("Tuning"), ALERTS("Alerts"), OPTIONS("Options");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private record Hotspot(String id, int x, int y, int w, int h, Runnable action) {
    }

    private record Row(String title, String sub, String avatarId, boolean showToggle,
                       boolean enabled, Runnable onToggle, Runnable onRemove) {
    }

    private final DiscordConfig cfg = DiscordControllerMod.getConfig();
    private final List<Hotspot> hotspots = new ArrayList<>();
    private final Map<String, Float> anim = new HashMap<>();
    private final Map<String, Boolean> hoverPrev = new HashMap<>();

    private Tab tab = Tab.ACCOUNT;

    private int px, py, pw, ph, navW, contentX, contentW;

    private final long openTime = System.currentTimeMillis();
    private long lastFrame = openTime;
    private float dt;
    private float ea;

    private TextFieldWidget tokenField, passwordField, recipName, recipId, recipMc, targetInput, groupField, webhookField, brandField, footerField;

    private int scroll;
    private int[] viewport;
    private int listMax;

    private String status = "";
    private int statusColor = TXT_DIM;
    private String accountLine = null;
    private String accountId = null;
    private boolean modal;

    private long tabSwitchTime = openTime;
    private String flashId;
    private long flashTime;

    public ControllerScreen() {
        super(ft("Discord Controller"));
    }

    @Override
    protected void init() {
        pw = Math.min(470, width - 40);
        ph = Math.min(306, height - 40);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        navW = 128;
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
        tokenField = passwordField = recipName = recipId = recipMc = targetInput = groupField = webhookField = brandField = footerField = null;
        scroll = 0;
        int fx = contentX + 20;
        int fw = contentW - 28;

        switch (tab) {
            case ACCOUNT -> {
                tokenField = field(fx, contentTop() + 22, fw, 18, "Paste your Discord token");
                passwordField = field(fx, contentTop() + 66, fw, 18, "Master password");
                tokenField.setText(cfg.token);
                addSelectableChild(tokenField);
                addSelectableChild(passwordField);
                fetchAccountAsync();
            }
            case RECIPIENTS -> {
                int rowY = contentTop() + 8;
                recipName = field(fx, rowY, 96, 18, "Name");
                recipId = field(fx + 104, rowY, fw - 104, 18, "Discord user ID");
                recipMc = field(fx, contentTop() + 34, fw - 44, 18, "Minecraft name or UUID (optional)");
                addSelectableChild(recipName);
                addSelectableChild(recipId);
                addSelectableChild(recipMc);
            }
            case TARGETS -> {
                targetInput = field(fx, contentTop() + 8, fw - 44, 18, "User ID or recipient name");
                addSelectableChild(targetInput);
            }
            case DELIVERY -> {
                groupField = field(fx, contentTop() + 22, fw - 52, 18, "Group-DM channel ID");
                groupField.setText(cfg.alertGroupChannelId);
                webhookField = field(fx, contentTop() + 66, fw - 52, 18, "Add a webhook URL");
                addSelectableChild(groupField);
                addSelectableChild(webhookField);
            }
            case OPTIONS -> {
                brandField = field(fx, contentTop() + 132, fw - 52, 18, "Brand");
                brandField.setText(cfg.brand);
                footerField = field(fx, contentTop() + 174, fw - 52, 18, "Footer");
                footerField.setText(cfg.footer);
                addSelectableChild(brandField);
                addSelectableChild(footerField);
            }
            default -> {
            }
        }
    }

    private TextFieldWidget field(int x, int y, int w, int h, String placeholder) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, h, ft(placeholder));
        f.setMaxLength(2000);
        f.setDrawsBackground(false);
        f.setEditableColor(TXT);
        f.setPlaceholder(ft("§8" + placeholder));
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

        ctx.fillGradient(0, 0, width, height, argbA(BG_TOP, ea), argbA(BG_BOT, ea));

        for (int i = 6; i >= 1; i--) {
            roundRect(ctx, px - i, py - i, pw + i * 2, ph + i * 2, 9 + i, argbA(ACCENT, ea * 0.14f / i));
        }
        roundRect(ctx, px, py, pw, ph, 8, argbA(PANEL, ea));
        roundRect(ctx, px, py, pw, 1, 0, argbA(ACCENT, ea * 0.5f));
        ctx.fillGradient(px + 1, py + 1, px + pw - 1, py + 44, argbA(0x3300E6FF, ea * 0.35f), 0);

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
        ctx.fill(px + navW, py + 10, px + navW + 1, py + ph - 10, argbA(PANEL_EDGE, ea));

        float baseEa = ea;
        ea = baseEa * ease(clamp01((now - tabSwitchTime) / 180f));
        switch (tab) {
            case ACCOUNT -> drawAccount(ctx, mouseX, mouseY, delta);
            case RECIPIENTS -> drawRecipients(ctx, mouseX, mouseY, delta);
            case TARGETS -> drawTargets(ctx, mouseX, mouseY, delta);
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
            roundRect(ctx, cx, cy + 1, 14, 14, 3, argbA(ACCENT, ea));
            roundRect(ctx, cx + 4, cy + 5, 6, 6, 2, argbA(ACCENT2, ea));
        }
        ctx.drawTextWithShadow(textRenderer, ft("Discord Controller"), cx + 24, py + 12, argbA(TXT, ea));
        ctx.drawTextWithShadow(textRenderer, ft("§8" + cfg.brand), cx + 24, py + 24, argbA(TXT_MUTE, ea));

        int uy = py + 38;
        float phase = (float) ((System.currentTimeMillis() % 3000) / 3000.0);
        int mid = lerpColor(ACCENT, ACCENT2, (float) (0.5 + 0.5 * Math.sin(phase * Math.PI * 2)));
        glow(ctx, px + 14, uy - 1, navW - 22, 3, 1, ACCENT, 0.22f);
        ctx.fillGradient(px + 14, uy, px + navW - 6, uy + 1, argbA(ACCENT, ea), argbA(mid, ea));

        drawButton(ctx, "close", px + pw - 26, py + 12, 16, 16, "×", false, mouseX, mouseY, this::close);
    }

    private void drawNav(DrawContext ctx, int mouseX, int mouseY) {
        int y = py + 46;
        int h = 22;
        int gap = 3;
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
                roundRect(ctx, px + 10, rowY, navW - 16, h, 5, argbA(NAV_SEL, ea));
            } else if (hv > 0.01f) {
                roundRect(ctx, px + 10, rowY, navW - 16, h, 5, argbA(NAV_SEL, ea * hv * 0.6f));
            }
            int ic = AV_PALETTE[t.ordinal() % AV_PALETTE.length];
            roundRect(ctx, px + 18, rowY + (h - 8) / 2 - 1, 8, 8, 2,
                    argbA(t == tab ? ic : lerpColor(ic, TXT_MUTE, 0.45f), ea));
            int tc = lerpColor(TXT_DIM, TXT, hv);
            ctx.drawTextWithShadow(textRenderer, ft(t.label), px + 32, rowY + (h - 8) / 2, argbA(tc, ea));
            Tab tt = t;
            addHotspot("nav:" + tt, px + 10, rowY, navW - 16, h, () -> switchTab(tt));
            i++;
        }
        float iy = approach("nav-indicator", target, 18);
        roundRect(ctx, px + 10, (int) iy + 4, 3, h - 8, 2, argbA(ACCENT, ea));
    }

    private void switchTab(Tab t) {
        if (t != tab) {
            tab = t;
            tabSwitchTime = System.currentTimeMillis();
            status = "";
            accountLine = null;
            accountId = null;
            rebuildWidgets();
        }
    }

    private void drawAccount(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int x = contentX + 14;
        label(ctx, "DISCORD TOKEN", x, contentTop() + 4);
        fieldCard(ctx, tokenField, delta, mouseX, mouseY);

        label(ctx, "MASTER PASSWORD", x, contentTop() + 48);
        fieldCard(ctx, passwordField, delta, mouseX, mouseY);

        int by = contentTop() + 94;
        int bw = (contentW - 28 - 8) / 2;
        drawButton(ctx, "detect", x, by, bw, 20, "Detect", false, mouseX, mouseY, this::detect);
        drawButton(ctx, "unlock", x + bw + 8, by, bw, 20, "Unlock", true, mouseX, mouseY, this::unlock);
        drawButton(ctx, "save", x, by + 26, contentW - 28, 20, "Save & Confirm", true, mouseX, mouseY, this::saveConfirm);

        if (!status.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, ft(status), x, contentTop() + 146, argbA(statusColor, ea));
        }
        if (accountLine != null) {
            int cy = contentTop() + 166;
            roundRect(ctx, x, cy, contentW - 28, 30, 5, argbA(CARD, ea));
            roundRect(ctx, x, cy, 3, 30, 2, argbA(OK, ea));
            int textX = x + 12;
            if (accountId != null) {
                drawAvatar(ctx, x + 6, cy + 5, 20, accountId, accountLine);
                textX = x + 32;
            }
            ctx.drawTextWithShadow(textRenderer, ft(accountLine), textX, cy + 11, argbA(TXT, ea));
        }
        String lockHint = cfg.hasEncryptedToken()
                ? (cfg.isLocked() ? "§eLocked — /dcontroller unlock <password>" : "§aUnlocked (in memory)")
                : "§8Stored locally only. Encrypted, never shared.";
        ctx.drawTextWithShadow(textRenderer, ft(lockHint), x, py + ph - 18, argbA(TXT_MUTE, ea));
    }

    private void drawRecipients(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int x = contentX + 14;
        fieldCard(ctx, recipName, delta, mouseX, mouseY);
        fieldCard(ctx, recipId, delta, mouseX, mouseY);
        fieldCard(ctx, recipMc, delta, mouseX, mouseY);
        drawButton(ctx, "recadd", x + contentW - 28 - 38, contentTop() + 8, 38, 18, "Add", true, mouseX, mouseY, this::addRecipient);

        List<Row> rows = new ArrayList<>();
        for (DiscordConfig.Recipient r : cfg.recipients) {
            String linked = (r.mcName != null && !r.mcName.isBlank())
                    ? "MC: " + r.mcName
                    : (r.mcUuid != null && !r.mcUuid.isBlank() ? "MC: " + r.mcUuid : "");
            String sub = r.id + (linked.isEmpty() ? "" : "  ·  " + linked);
            rows.add(new Row(r.name, sub, r.id, false, true, null,
                    () -> { cfg.recipients.removeIf(rr -> rr.name.equals(r.name)); cfg.save(); }));
        }
        int top = contentTop() + 62;
        drawList(ctx, x, top, contentW - 28, py + ph - 14 - top, rows, mouseX, mouseY, "No recipients yet.");
    }

    private void drawTargets(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int x = contentX + 14;
        fieldCard(ctx, targetInput, delta, mouseX, mouseY);
        drawButton(ctx, "tgtadd", x + contentW - 28 - 38, contentTop() + 8, 38, 18, "Add", true, mouseX, mouseY, this::addTarget);
        ctx.drawTextWithShadow(textRenderer,
                ft("§8DM'd + pinged on an alert. Toggle to mute one."), x, contentTop() + 32, argbA(TXT_MUTE, ea));

        List<Row> rows = new ArrayList<>();
        for (String id : cfg.alertTargetIds) {
            String nm = null;
            for (DiscordConfig.Recipient r : cfg.recipients) {
                if (r.id.equals(id)) {
                    nm = r.name;
                }
            }
            rows.add(new Row(nm != null ? nm : "User", id, id, true, cfg.isTargetEnabled(id),
                    () -> { toggleTarget(id); },
                    () -> { cfg.alertTargetIds.remove(id); cfg.disabledTargetIds.remove(id); cfg.save(); }));
        }
        drawList(ctx, x, contentTop() + 48, contentW - 28, py + ph - 14 - (contentTop() + 48), rows, mouseX, mouseY, "No alert targets yet.");
    }

    private void toggleTarget(String id) {
        if (cfg.disabledTargetIds.contains(id)) {
            cfg.disabledTargetIds.remove(id);
        } else {
            cfg.disabledTargetIds.add(id);
        }
        cfg.save();
    }

    private void drawDelivery(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int x = contentX + 14;
        label(ctx, "GROUP-DM CHANNEL  §8(@everyone pings the group)", x, contentTop() + 4);
        fieldCard(ctx, groupField, delta, mouseX, mouseY);
        drawButton(ctx, "gset", x + contentW - 28 - 44, contentTop() + 22, 44, 18, "Set", true, mouseX, mouseY,
                () -> { cfg.alertGroupChannelId = digits(groupField.getText()); cfg.save(); flash("Saved group-DM.", OK); });

        label(ctx, "WEBHOOKS  §8(server channels)", x, contentTop() + 48);
        fieldCard(ctx, webhookField, delta, mouseX, mouseY);
        drawButton(ctx, "wadd", x + contentW - 28 - 44, contentTop() + 66, 44, 18, "Add", true, mouseX, mouseY, this::addWebhook);

        List<Row> rows = new ArrayList<>();
        for (String url : cfg.alertWebhooks) {
            rows.add(new Row("Webhook", shorten(url), null, false, true, null,
                    () -> { cfg.alertWebhooks.remove(url); cfg.save(); }));
        }
        int listTop = contentTop() + 92;
        int listBottom = py + ph - 56;
        drawList(ctx, x, listTop, contentW - 28, listBottom - listTop, rows, mouseX, mouseY, "No webhooks added.");

        toggleRow(ctx, "ping", x, py + ph - 52, "Prepend @everyone", cfg.alertPingEveryone, mouseX, mouseY,
                () -> { cfg.alertPingEveryone = !cfg.alertPingEveryone; cfg.save(); });
        drawButton(ctx, "dtest", x, py + ph - 26, 110, 20, "Send Test", true, mouseX, mouseY,
                () -> AlertManager.get().sendTestAlert(MinecraftClient.getInstance()));
        if (!status.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, ft(status), x + 120, py + ph - 20, argbA(statusColor, ea));
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
        int step = 26;
        stepper(ctx, "hits", x, y, w, "Trigger above hits", String.valueOf(cfg.alertHitThreshold), mouseX, mouseY,
                () -> { cfg.alertHitThreshold = Math.max(1, cfg.alertHitThreshold - 1); cfg.save(); },
                () -> { cfg.alertHitThreshold++; cfg.save(); });
        stepper(ctx, "hp", x, y + step, w, "Health lost (hearts)", trim(cfg.alertHealthLoss / 2.0), mouseX, mouseY,
                () -> { cfg.alertHealthLoss = Math.max(1, cfg.alertHealthLoss - 1); cfg.save(); },
                () -> { cfg.alertHealthLoss += 1; cfg.save(); });
        stepper(ctx, "rad", x, y + step * 2, w, "Attacker radius (blocks)", trim(cfg.attackRadius), mouseX, mouseY,
                () -> { cfg.attackRadius = Math.max(1, cfg.attackRadius - 1); cfg.save(); },
                () -> { cfg.attackRadius += 1; cfg.save(); });
        stepper(ctx, "win", x, y + step * 3, w, "Time window (seconds)", String.valueOf(cfg.alertWindowSeconds), mouseX, mouseY,
                () -> { cfg.alertWindowSeconds = Math.max(1, cfg.alertWindowSeconds - 1); cfg.save(); },
                () -> { cfg.alertWindowSeconds++; cfg.save(); });
        stepper(ctx, "cd", x, y + step * 4, w, "Auto cooldown (seconds)", String.valueOf(cfg.alertCooldownSeconds), mouseX, mouseY,
                () -> { cfg.alertCooldownSeconds = Math.max(0, cfg.alertCooldownSeconds - 5); cfg.save(); },
                () -> { cfg.alertCooldownSeconds += 5; cfg.save(); });

        int bw = (w - 8) / 2;
        drawButton(ctx, "now", x, py + ph - 28, bw, 20, "Alert Now", false, mouseX, mouseY,
                () -> AlertManager.get().sendManualAlert(MinecraftClient.getInstance()));
        drawButton(ctx, "test", x + bw + 8, py + ph - 28, bw, 20, "Send Test", true, mouseX, mouseY,
                () -> AlertManager.get().sendTestAlert(MinecraftClient.getInstance()));
    }

    private void drawAlerts(DrawContext ctx, int mouseX, int mouseY) {
        int x = contentX + 14;
        toggleRow(ctx, "lowhp", x, contentTop() + 6, "Low-health emergency alert", cfg.alertLowHealthEnabled, mouseX, mouseY,
                () -> { cfg.alertLowHealthEnabled = !cfg.alertLowHealthEnabled; cfg.save(); });
        stepper(ctx, "lowhpv", x, contentTop() + 34, contentW - 28, "Emergency at (hearts)",
                trim(cfg.alertLowHealthHp / 2.0), mouseX, mouseY,
                () -> { cfg.alertLowHealthHp = Math.max(1, cfg.alertLowHealthHp - 1); cfg.save(); },
                () -> { cfg.alertLowHealthHp += 1; cfg.save(); });
        toggleRow(ctx, "death", x, contentTop() + 62, "Alert when you are killed", cfg.alertOnDeath, mouseX, mouseY,
                () -> { cfg.alertOnDeath = !cfg.alertOnDeath; cfg.save(); });
        toggleRow(ctx, "asnd", x, contentTop() + 90, "Play a sound when an alert fires", cfg.alertSound, mouseX, mouseY,
                () -> { cfg.alertSound = !cfg.alertSound; cfg.save(); });
        toggleRow(ctx, "armhud", x, contentTop() + 118, "Show 'ARMED' HUD chip", cfg.hudArmedChip, mouseX, mouseY,
                () -> { cfg.hudArmedChip = !cfg.hudArmedChip; cfg.save(); });
        ctx.drawTextWithShadow(textRenderer,
                ft("§8Emergency + death fire even if auto-alert is off."),
                x, contentTop() + 148, argbA(TXT_MUTE, ea));
    }

    private void drawOptions(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int x = contentX + 14;
        toggleRow(ctx, "hud", x, contentTop() + 6, "On-screen HUD indicator", cfg.hudEnabled, mouseX, mouseY,
                () -> { cfg.hudEnabled = !cfg.hudEnabled; cfg.save(); });
        toggleRow(ctx, "snd", x, contentTop() + 34, "Menu sounds", cfg.uiSounds, mouseX, mouseY,
                () -> { cfg.uiSounds = !cfg.uiSounds; cfg.save(); });
        toggleRow(ctx, "recv", x, contentTop() + 62, "Receive teammate alerts in chat", cfg.receiveInGameAlerts, mouseX, mouseY,
                () -> { cfg.receiveInGameAlerts = !cfg.receiveInGameAlerts; cfg.save(); });
        stepper(ctx, "mcd", x, contentTop() + 90, contentW - 28, "Manual cooldown (seconds)",
                String.valueOf(cfg.alertManualCooldownSeconds), mouseX, mouseY,
                () -> { cfg.alertManualCooldownSeconds = Math.max(0, cfg.alertManualCooldownSeconds - 1); cfg.save(); },
                () -> { cfg.alertManualCooldownSeconds++; cfg.save(); });

        label(ctx, "BRAND", x, contentTop() + 120);
        fieldCard(ctx, brandField, delta, mouseX, mouseY);
        label(ctx, "FOOTER", x, contentTop() + 162);
        fieldCard(ctx, footerField, delta, mouseX, mouseY);
        drawButton(ctx, "brsave", x + contentW - 28 - 52, contentTop() + 132, 52, 40, "Save", true, mouseX, mouseY,
                () -> {
                    if (!brandField.getText().isBlank()) {
                        cfg.brand = brandField.getText().trim();
                    }
                    if (!footerField.getText().isBlank()) {
                        cfg.footer = footerField.getText().trim();
                    }
                    cfg.save();
                    flash("Saved branding.", OK);
                });
        if (!status.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, ft(status), x, py + ph - 18, argbA(statusColor, ea));
        }
    }

    private void drawSetupOverlay(DrawContext ctx, int mouseX, int mouseY) {
        modal = true;
        hotspots.clear();
        ctx.fill(px, py, px + pw, py + ph, argbA(0xE60B0D18, ea));
        int ow = 320;
        int oh = 168;
        int ox = px + (pw - ow) / 2;
        int oy = py + (ph - oh) / 2;
        roundRect(ctx, ox, oy, ow, oh, 8, argbA(PANEL_EDGE, ea));
        roundRect(ctx, ox + 1, oy + 1, ow - 2, oh - 2, 8, argbA(PANEL, ea));
        roundRect(ctx, ox, oy, ow, 3, 2, argbA(ACCENT, ea));

        int cx = ox + ow / 2;
        ctx.drawCenteredTextWithShadow(textRenderer, ft("§lWelcome to Discord Controller"), cx, oy + 16, argbA(TXT, ea));
        String[] lines = {
                "§71. §fAccount §7— paste or detect your token",
                "§72. §fTargets §7— who gets pinged for help",
                "§73. §fDelivery §7— group DM / webhooks for @everyone",
                "§74. §fTuning §7— when the auto attack-alert fires",
        };
        int ly = oy + 40;
        for (String l : lines) {
            ctx.drawTextWithShadow(textRenderer, ft(l), ox + 22, ly, argbA(TXT_DIM, ea));
            ly += 16;
        }
        ctx.drawCenteredTextWithShadow(textRenderer,
                ft("§8Bind a key in Options → Controls to open this fast."), cx, oy + oh - 40, argbA(TXT_MUTE, ea));
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

    private void drawAvatar(DrawContext ctx, int x, int y, int size, String userId, String title) {
        Identifier tex = AvatarCache.get(userId);
        if (tex != null) {
            try {
                ctx.drawTexturedQuad(tex, x, y, x + size, y + size, 0f, 0f, 1f, 1f);
                return;
            } catch (Exception ignored) {
            }
        }
        int c = AV_PALETTE[Math.floorMod(userId == null ? 0 : userId.hashCode(), AV_PALETTE.length)];
        roundRect(ctx, x, y, size, size, 5, argbA(c, ea));
        String ini = (title == null || title.isBlank()) ? "?" : title.substring(0, 1).toUpperCase();
        ctx.drawCenteredTextWithShadow(textRenderer, ft(ini), x + size / 2, y + (size - 8) / 2, argbA(0xFFFFFFFF, ea));
    }

    private void fieldCard(DrawContext ctx, TextFieldWidget f, float delta, int mouseX, int mouseY) {
        if (f == null) {
            return;
        }
        int cx = f.getX() - 8;
        int cy = f.getY() - 5;
        int cw = f.getWidth() + 14;
        int chh = 28;
        roundRect(ctx, cx, cy, cw, chh, 6, argbA(CARD, ea));
        if (f.isFocused()) {
            glow(ctx, cx, cy, cw, chh, 6, ACCENT, 0.10f);
            roundRect(ctx, cx, cy, cw, 1, 0, argbA(ACCENT, ea));
            roundRect(ctx, cx, cy + chh - 1, cw, 1, 0, argbA(ACCENT, ea));
        }
        f.render(ctx, mouseX, mouseY, delta);
    }

    private void drawButton(DrawContext ctx, String id, int x, int y, int w, int h, String text,
                            boolean accent, int mouseX, int mouseY, Runnable action) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        hoverSound("btn:" + id, hover);
        float hv = approach("btn:" + id, hover ? 1 : 0, 16);
        int base = accent ? lerpColor(ACCENT, lerpColor(ACCENT, ACCENT2, 0.35f), hv) : lerpColor(BTN, BTN_HI, hv);
        float flash = (flashId != null && flashId.equals("btn:" + id))
                ? clamp01(1f - (System.currentTimeMillis() - flashTime) / 220f) : 0f;
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

    private void toggleRow(DrawContext ctx, String id, int x, int y, String labelText, boolean on,
                           int mouseX, int mouseY, Runnable toggle) {
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

    private void stepper(DrawContext ctx, String id, int x, int y, int w, String labelText, String value,
                         int mouseX, int mouseY, Runnable minus, Runnable plus) {
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

    private void drawList(DrawContext ctx, int x, int y, int w, int h, List<Row> rows,
                          int mouseX, int mouseY, String empty) {
        roundRect(ctx, x, y, w, h, 5, argbA(0x40000000, ea));
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
                roundRect(ctx, x + pad, ry, w - pad * 2, rowH, 4, argbA(lerpColor(CARD, CARD_HI, hover ? 1 : 0), ea));
                roundRect(ctx, x + pad, ry, 2, rowH, 1, argbA(row.enabled ? ACCENT : TXT_MUTE, ea * 0.7f));

                int tX = x + pad + 8;
                if (row.avatarId != null) {
                    drawAvatar(ctx, x + pad + 5, ry + 3, 20, row.avatarId, row.title);
                    tX = x + pad + 30;
                }
                int titleCol = row.enabled ? TXT : TXT_MUTE;
                ctx.drawTextWithShadow(textRenderer, ft(row.title), tX, ry + 5, argbA(titleCol, ea));
                if (row.sub != null && !row.sub.isEmpty()) {
                    ctx.drawTextWithShadow(textRenderer, ft("§8" + row.sub), tX, ry + 15, argbA(TXT_MUTE, ea));
                }

                int rbx = x + w - pad - 18;
                int rby = ry + (rowH - 16) / 2;
                boolean rh = inside(mouseX, mouseY, rbx, rby, 16, 16) && inView;
                roundRect(ctx, rbx, rby, 16, 16, 4, argbA(lerpColor(BTN, DANGER, rh ? 1 : 0), ea));
                ctx.drawCenteredTextWithShadow(textRenderer, ft("×"), rbx + 8, rby + 4, argbA(TXT, ea));
                boolean rowInBounds = rby >= y && rby + 16 <= y + h;
                if (rowInBounds) {
                    addHotspot("rm:" + row.sub, rbx, rby, 16, 16, row.onRemove);
                }

                if (row.showToggle && row.onToggle != null) {
                    int gx = rbx - 38;
                    int gy = ry + (rowH - 14) / 2;
                    float p = approach("rtgl:" + row.sub, row.enabled ? 1 : 0, 14);
                    roundRect(ctx, gx, gy, 30, 14, 5, argbA(lerpColor(TRACK_OFF, ACCENT, p), ea));
                    int knob = gx + 2 + (int) ((30 - 14) * p);
                    roundRect(ctx, knob, gy + 2, 10, 10, 3, argbA(0xFFFFFFFF, ea));
                    if (rowInBounds) {
                        addHotspot("rtgl:" + row.sub, gx, gy, 30, 14, row.onToggle);
                    }
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

    private void addRecipient() {
        String n = recipName.getText().trim();
        String id = digits(recipId.getText());
        String mc = recipMc != null ? recipMc.getText().trim() : "";
        if (n.isEmpty() || id.length() < 15) {
            flash("Enter a name and a numeric user ID.", DANGER);
            return;
        }
        cfg.recipients.removeIf(r -> r.name.equalsIgnoreCase(n));
        DiscordConfig.Recipient rec = new DiscordConfig.Recipient(n, id);
        if (mc.matches("(?i)[0-9a-f]{32}")) {
            rec.mcUuid = mc.toLowerCase();
        } else {
            rec.mcName = mc;
        }
        cfg.recipients.add(rec);
        cfg.save();
        if (!mc.isBlank() && !mc.matches("(?i)[0-9a-f]{32}")) {
            ControllerCommand.resolveMcUuid(id, mc);
        }
        recipName.setText("");
        recipId.setText("");
        recipMc.setText("");
        flash("Added " + n + ".", OK);
    }

    private void addTarget() {
        String raw = targetInput.getText().trim();
        if (raw.isEmpty()) {
            return;
        }
        int added = 0;
        for (String tok : raw.split("\\s+")) {
            String id = tok.matches("\\d{15,}") ? tok : cfg.findRecipientId(tok);
            if (id != null && !cfg.alertTargetIds.contains(id)) {
                cfg.alertTargetIds.add(id);
                added++;
            }
        }
        if (added > 0) {
            cfg.save();
            targetInput.setText("");
            flash("Added " + added + " target(s).", OK);
        } else {
            flash("Unknown ID or recipient.", DANGER);
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

    private void detect() {
        flash("Detecting token...", TXT_DIM);
        new Thread(() -> {
            String t = TokenDetector.detect();
            MinecraftClient.getInstance().execute(() -> {
                if (t == null) {
                    flash("No Discord token found.", DANGER);
                } else if (tokenField != null) {
                    tokenField.setText(t);
                    flash("Detected. Click Save & Confirm.", OK);
                }
            });
        }, "dcontroller-detect").start();
    }

    private void saveConfirm() {
        String t = tokenField.getText().trim();
        String pw = passwordField != null ? passwordField.getText().trim() : "";
        if (t.isEmpty()) {
            flash("Paste a token or press Detect.", DANGER);
            return;
        }
        if (!cfg.hasPassword()) {
            if (pw.isEmpty()) {
                flash("Set a master password first.", DANGER);
                return;
            }
            if (!cfg.setPassword(pw)) {
                flash("Could not set password.", DANGER);
                return;
            }
        } else if (cfg.isLocked()) {
            if (pw.isEmpty()) {
                flash("Token locked — unlock first.", DANGER);
                return;
            }
            if (!cfg.unlock(pw)) {
                flash("Wrong password.", DANGER);
                return;
            }
        }
        flash("Checking account...", TXT_DIM);
        new Thread(() -> {
            JsonObject acc = DiscordClient.fetchCurrentUser(t);
            MinecraftClient.getInstance().execute(() -> {
                if (acc == null) {
                    flash("Invalid token.", DANGER);
                } else if (!cfg.setToken(t)) {
                    flash("Token locked — unlock first.", DANGER);
                } else {
                    flash("Saved (encrypted).", OK);
                    accountLine = describe(acc);
                    accountId = acc.has("id") ? acc.get("id").getAsString() : null;
                }
            });
        }, "dcontroller-save").start();
    }

    private void unlock() {
        String pw = passwordField != null ? passwordField.getText().trim() : "";
        if (pw.isEmpty()) {
            flash("Enter the master password.", DANGER);
            return;
        }
        if (cfg.unlock(pw)) {
            if (tokenField != null) {
                tokenField.setText(cfg.token);
            }
            flash("Unlocked for this session.", OK);
        } else {
            flash("Wrong password.", DANGER);
        }
    }

    private void fetchAccountAsync() {
        if (cfg.token.isEmpty()) {
            return;
        }
        new Thread(() -> {
            JsonObject acc = DiscordClient.fetchCurrentUser(cfg.token);
            MinecraftClient.getInstance().execute(() -> {
                if (acc != null && tab == Tab.ACCOUNT) {
                    accountLine = describe(acc);
                    accountId = acc.has("id") ? acc.get("id").getAsString() : null;
                }
            });
        }, "dcontroller-acct").start();
    }

    private static String describe(JsonObject a) {
        String u = a.has("username") ? a.get("username").getAsString() : "?";
        String g = a.has("global_name") && !a.get("global_name").isJsonNull() ? a.get("global_name").getAsString() : u;
        return "§a" + g + " §7@" + u;
    }

    private void flash(String msg, int color) {
        status = msg;
        statusColor = color;
    }

    public boolean clickAt(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = hotspots.size() - 1; i >= 0; i--) {
                Hotspot h = hotspots.get(i);
                if (mouseX >= h.x && mouseX < h.x + h.w && mouseY >= h.y && mouseY < h.y + h.h) {
                    flashId = h.id;
                    flashTime = System.currentTimeMillis();
                    playClick();
                    h.action.run();
                    return true;
                }
            }
        }
        return false;
    }

    public boolean blocksClicks() {
        return modal;
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
        int oa = (int) (aa + (ba - aa) * t);
        int or = (int) (ar + (br - ar) * t);
        int og = (int) (ag + (bg - ag) * t);
        int ob = (int) (ab + (bb - ab) * t);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
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

    private static String digits(String s) {
        return s.replaceAll("[^0-9]", "");
    }

    private static String shorten(String s) {
        return s != null && s.length() > 40 ? s.substring(0, 40) + "…" : s;
    }

    private static String trim(double d) {
        return d == Math.floor(d) ? String.valueOf((int) d) : String.valueOf(d);
    }
}
