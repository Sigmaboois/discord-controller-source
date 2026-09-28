package com.juicy.discordcontroller;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AlertManager {

    private static final Gson GSON = new Gson();
    private static final AlertManager INSTANCE = new AlertManager();
    private static final long COMBAT_TIMEOUT_MS = 6000L;

    public static AlertManager get() {
        return INSTANCE;
    }

    public enum Kind {
        AUTO("🚨 UNDER ATTACK", 0xE74C3C),
        EMERGENCY("🩸 LOW HEALTH", 0xC0392B),
        MANUAL("🆘 HELP REQUEST", 0xF39C12),
        DEATH("☠ DOWNED", 0x7F1D1D),
        TEST("🧪 TEST ALERT", 0x3498DB);

        final String title;
        final int color;

        Kind(String title, int color) {
            this.title = title;
            this.color = color;
        }
    }

    private static final class Hit {
        final long time;
        final double dmg;

        Hit(long time, double dmg) {
            this.time = time;
            this.dmg = dmg;
        }
    }

    private static final class Window {
        String name = "?";
        final Deque<Hit> hits = new ArrayDeque<>();

        double total() {
            double t = 0;
            for (Hit h : hits) {
                t += h.dmg;
            }
            return t;
        }
    }

    private final Map<UUID, Window> windows = new HashMap<>();
    private float lastHealth = -1f;
    private long lastAutoAlert = 0L;
    private long lastEmergencyAlert = 0L;
    private long lastManualAlert = 0L;
    private long lastDeathAlert = 0L;
    private long lastPlayerHitTime = 0L;

    private AlertManager() {
    }

    public void onClientTick(MinecraftClient mc) {
        ClientPlayerEntity me = mc.player;
        if (me == null || mc.world == null) {
            lastHealth = -1f;
            windows.clear();
            return;
        }

        DiscordConfig cfg = DiscordControllerMod.getConfig();
        long now = System.currentTimeMillis();
        long windowMs = Math.max(1, cfg.alertWindowSeconds) * 1000L;
        float hp = me.getHealth();

        if (lastHealth < 0f) {
            lastHealth = hp;
            return;
        }

        if (hp <= 0f) {
            if (lastHealth > 0f && cfg.alertOnDeath && isConfigured(cfg) && now - lastDeathAlert >= 3000L) {
                lastDeathAlert = now;
                fire(mc, cfg, Kind.DEATH, topAttackerName(), topHitCount(), topDamage());
            }
            lastHealth = hp;
            return;
        }

        double delta = lastHealth - hp;
        lastHealth = hp;
        prune(now, windowMs);
        if (delta <= 0.01) {
            return;
        }

        PlayerEntity attacker = resolveAttacker(mc, cfg.attackRadius);
        if (attacker == null) {
            return;
        }

        lastPlayerHitTime = now;
        Window w = windows.computeIfAbsent(attacker.getUuid(), k -> new Window());
        w.name = attacker.getName().getString();
        w.hits.addLast(new Hit(now, delta));
        prune(now, windowMs);

        if (!isConfigured(cfg)) {
            return;
        }

        int count = w.hits.size();
        double sum = w.total();
        boolean enoughHits = count > cfg.alertHitThreshold;
        boolean enoughDamage = sum >= cfg.alertHealthLoss;

        if (cfg.autoAlertEnabled && enoughHits && enoughDamage
                && now - lastAutoAlert >= Math.max(0, cfg.alertCooldownSeconds) * 1000L) {
            lastAutoAlert = now;
            w.hits.clear();
            fire(mc, cfg, Kind.AUTO, w.name, count, sum);
        } else if (cfg.alertLowHealthEnabled && hp <= cfg.alertLowHealthHp
                && now - lastEmergencyAlert >= Math.max(0, cfg.alertCooldownSeconds) * 1000L) {
            lastEmergencyAlert = now;
            fire(mc, cfg, Kind.EMERGENCY, w.name, count, sum);
        }
    }

    private void prune(long now, long windowMs) {
        windows.values().forEach(w -> {
            while (!w.hits.isEmpty() && now - w.hits.peekFirst().time > windowMs) {
                w.hits.removeFirst();
            }
        });
        windows.values().removeIf(w -> w.hits.isEmpty());
    }

    private PlayerEntity resolveAttacker(MinecraftClient mc, double radius) {
        ClientPlayerEntity me = mc.player;
        double radiusSq = radius * radius;

        LivingEntity a = me.getAttacker();
        if (a instanceof PlayerEntity p && p != me && p.isAlive()
                && me.squaredDistanceTo(p) <= radiusSq * 4) {
            return p;
        }

        Vec3d vel = me.getVelocity();
        double vx = vel.x;
        double vz = vel.z;
        double kb = Math.sqrt(vx * vx + vz * vz);
        boolean hasKb = kb > 0.08;

        PlayerEntity best = null;
        double bestScore = -1e9;
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == me || !p.isAlive()) {
                continue;
            }
            double dx = p.getX() - me.getX();
            double dz = p.getZ() - me.getZ();
            double d2 = dx * dx + dz * dz;
            if (d2 > radiusSq) {
                continue;
            }
            double dist = Math.sqrt(d2) + 0.001;
            double score = -dist;
            if (hasKb) {
                double align = -(dx * vx + dz * vz) / (dist * kb);
                score += align * radius * 1.5;
            }
            if (best == null || score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }

    public boolean inCombat() {
        return System.currentTimeMillis() - lastPlayerHitTime < COMBAT_TIMEOUT_MS;
    }

    public String combatAttacker() {
        return topAttackerName();
    }

    public int combatHits() {
        return topHitCount();
    }

    public int combatAttackerCount() {
        int c = 0;
        for (Window w : windows.values()) {
            if (!w.hits.isEmpty()) {
                c++;
            }
        }
        return c;
    }

    private Window topWindow() {
        Window top = null;
        for (Window w : windows.values()) {
            if (!w.hits.isEmpty() && (top == null || w.hits.size() > top.hits.size())) {
                top = w;
            }
        }
        return top;
    }

    private String topAttackerName() {
        Window t = topWindow();
        return t == null ? null : t.name;
    }

    private int topHitCount() {
        Window t = topWindow();
        return t == null ? 0 : t.hits.size();
    }

    private double topDamage() {
        Window t = topWindow();
        return t == null ? 0 : t.total();
    }

    public void sendManualAlert(MinecraftClient mc) {
        manual(mc, Kind.MANUAL);
    }

    public void sendTestAlert(MinecraftClient mc) {
        manual(mc, Kind.TEST);
    }

    private void manual(MinecraftClient mc, Kind kind) {
        if (mc.player == null || mc.world == null) {
            chat(mc, "§cJoin a world before sending an alert.");
            return;
        }
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        if (!isConfigured(cfg)) {
            chat(mc, "§cNo webhook set. Add one in §f/dcontroller§c (Webhooks tab) or §f/dcontroller alert webhook add <url>§c.");
            return;
        }
        if (kind == Kind.MANUAL) {
            long rem = Math.max(0, cfg.alertManualCooldownSeconds) * 1000L - (System.currentTimeMillis() - lastManualAlert);
            if (rem > 0) {
                chat(mc, "§eHelp alert on cooldown (" + (rem / 1000 + 1) + "s).");
                return;
            }
            lastManualAlert = System.currentTimeMillis();
        }
        PlayerEntity near = resolveAttacker(mc, cfg.attackRadius);
        fire(mc, cfg, kind, near != null ? near.getName().getString() : null, topHitCount(), topDamage());
    }

    public static boolean isConfigured(DiscordConfig cfg) {
        return cfg.alertWebhooks != null && !cfg.alertWebhooks.isEmpty();
    }

    public long autoCooldownRemainingMs() {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        return Math.max(0, Math.max(0, cfg.alertCooldownSeconds) * 1000L - (System.currentTimeMillis() - lastAutoAlert));
    }

    private void fire(MinecraftClient mc, DiscordConfig cfg, Kind kind, String attacker, int hits, double dmgLost) {
        String victim = mc.player.getName().getString();
        String coords = coords(mc);
        String dim = dimension(mc);
        String server = serverAddress(mc);
        int others = Math.max(0, combatAttackerCount() - 1);
        String json = buildWebhookJson(cfg, kind, victim, attacker, coords, dim, server, hits, dmgLost, others);

        int count = 0;
        for (String url : new ArrayList<>(cfg.alertWebhooks)) {
            if (url == null || url.isBlank()) {
                continue;
            }
            String u = url;
            new Thread(() -> DiscordClient.sendWebhook(u, json), "dcontroller-alert").start();
            count++;
        }

        if (cfg.alertSound) {
            Sounds.ui(mc, SoundEvents.BLOCK_NOTE_BLOCK_BELL, kind == Kind.TEST ? 1.4f : 0.8f);
        }

        String prefix = switch (kind) {
            case AUTO -> "§c⚠ Attack alert";
            case EMERGENCY -> "§4🩸 Low-health alert";
            case MANUAL -> "§6Help alert";
            case DEATH -> "§4☠ Death alert";
            case TEST -> "§bTest alert";
        };
        chat(mc, prefix + " §7sent to §f" + count + "§7 webhook" + (count == 1 ? "" : "s") + ".");

        HudOverlay.toast(switch (kind) {
            case AUTO -> "Attack alert sent";
            case EMERGENCY -> "Low-health alert sent";
            case MANUAL -> "Help alert sent";
            case DEATH -> "Death alert sent";
            case TEST -> "Test alert sent";
        }, kind.color | 0xFF000000);

        if (kind == Kind.AUTO) {
            cfg.autoAlertEnabled = false;
            cfg.save();
            windows.clear();
            chat(mc, "§7Auto-alert §cturned off§7 after firing — re-arm with §f/dcontroller alert auto on§7.");
        }
    }

    private String buildWebhookJson(DiscordConfig cfg, Kind kind, String victim, String attacker,
                                    String coords, String dim, String server, int hits, double dmgLost, int others) {
        boolean combat = kind == Kind.AUTO || kind == Kind.EMERGENCY;
        JsonObject root = new JsonObject();
        if (cfg.alertPingEveryone && kind != Kind.TEST) {
            root.addProperty("content", "@everyone");
            JsonObject mentions = new JsonObject();
            JsonArray parse = new JsonArray();
            parse.add("everyone");
            mentions.add("parse", parse);
            root.add("allowed_mentions", mentions);
        }

        JsonObject embed = new JsonObject();
        embed.addProperty("title", kind.title);
        embed.addProperty("description", switch (kind) {
            case AUTO -> "**" + victim + "** is under attack!";
            case EMERGENCY -> "**" + victim + "** is critically low on health!";
            case MANUAL -> "**" + victim + "** needs backup!";
            case DEATH -> "**" + victim + "** was downed!";
            case TEST -> "Test alert from **" + victim + "** — delivery is working.";
        });
        embed.addProperty("color", kind.color);

        JsonArray fields = new JsonArray();
        if (attacker != null) {
            String lbl = switch (kind) {
                case DEATH -> "Killed by";
                default -> "Attacker";
            };
            fields.add(field(lbl, "`" + attacker + "`" + (others > 0 ? " (+" + others + " more)" : ""), true));
        }
        fields.add(field("Server", "`" + server + "`", true));
        fields.add(field("Location", "`" + coords + "`  ·  " + dim, false));
        if (combat) {
            fields.add(field("Damage",
                    hits + " hits · " + fmtHearts(dmgLost) + " ❤ lost (last " + cfg.alertWindowSeconds + "s)", false));
        }
        embed.add("fields", fields);

        JsonObject footer = new JsonObject();
        footer.addProperty("text", cfg.footer + " | " + cfg.brand);
        embed.add("footer", footer);

        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        root.add("embeds", embeds);
        return GSON.toJson(root);
    }

    private static JsonObject field(String name, String value, boolean inline) {
        JsonObject f = new JsonObject();
        f.addProperty("name", name);
        f.addProperty("value", value);
        f.addProperty("inline", inline);
        return f;
    }

    private static String coords(MinecraftClient mc) {
        PlayerEntity p = mc.player;
        return (int) Math.floor(p.getX()) + ", " + (int) Math.floor(p.getY()) + ", " + (int) Math.floor(p.getZ());
    }

    private static String dimension(MinecraftClient mc) {
        String path = mc.world.getRegistryKey().getValue().getPath();
        return switch (path) {
            case "overworld" -> "Overworld";
            case "the_nether" -> "Nether";
            case "the_end" -> "The End";
            default -> path;
        };
    }

    public static String serverAddress(MinecraftClient mc) {
        ServerInfo si = mc.getCurrentServerEntry();
        if (si != null && si.address != null && !si.address.isBlank()) {
            return si.address;
        }
        if (mc.isInSingleplayer()) {
            return "Singleplayer";
        }
        return "Unknown";
    }

    private static String fmtHearts(double hp) {
        double hearts = hp / 2.0;
        return (hearts == Math.floor(hearts)) ? String.valueOf((int) hearts) : String.format("%.1f", hearts);
    }

    private static void chat(MinecraftClient mc, String msg) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(msg), false);
        }
    }
}
