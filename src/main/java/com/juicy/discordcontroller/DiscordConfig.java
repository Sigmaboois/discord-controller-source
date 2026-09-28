package com.juicy.discordcontroller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Mod settings, persisted as JSON under config/discord-controller.json. */
public class DiscordConfig {

    /** Discord BOT token (from the Developer Portal). Stored locally only. */
    public String token = "";

    public List<Recipient> recipients = new ArrayList<>();

    /** Product line shown in the small footer tag of every message. */
    public String footer = "Discord HUD Controller";

    /** Brand shown in headings / footer. */
    public String brand = "Juicy Launcher";

    /** Virtual-key code for the /answer hotkey (0 = disabled). */
    public int answerVk = 0x27; // VK_RIGHT; bind Discord's Global "Answer Call" to match

    public boolean answerCtrl = false;
    public boolean answerShift = false;
    public boolean answerAlt = false;

    /** Master switch for the automatic "under attack" alert. */
    public boolean autoAlertEnabled = true;

    /** Discord user IDs that get DM'd when an alert fires. */
    public List<String> alertTargetIds = new ArrayList<>();

    /** User IDs kept in the list but skipped on alert (per-target off switch). */
    public List<String> disabledTargetIds = new ArrayList<>();

    /** Webhook URLs to POST alerts to (each can @everyone in its own channel). */
    public List<String> alertWebhooks = new ArrayList<>();

    /** Legacy single webhook, migrated into {@link #alertWebhooks} on load. */
    public String alertWebhookUrl = "";

    /** Alert channel ID the bot posts into (Developer Mode -> Copy Channel ID). @everyone pings it. */
    public String alertGroupChannelId = "";

    /** Whether the channel / webhook alert prepends {@code @everyone}. */
    public boolean alertPingEveryone = true;

    /** Auto-alert fires when hits within the window are STRICTLY greater than this. */
    public int alertHitThreshold = 5;

    /** ...and at least this much health (in HP; 2 HP = 1 heart) was lost in the window. */
    public double alertHealthLoss = 6.0;

    /** Sliding window, in seconds, over which hits/damage are counted. */
    public int alertWindowSeconds = 8;

    /** Minimum gap, in seconds, between two automatic alerts. */
    public int alertCooldownSeconds = 60;

    /** How close (blocks) a player must be to be blamed for a hit. */
    public double attackRadius = 6.0;

    /** Minimum gap, in seconds, between manual/help alerts. */
    public int alertManualCooldownSeconds = 10;

    /** Fire an emergency alert the instant health drops to/below this HP while a player is on you. */
    public boolean alertLowHealthEnabled = true;
    public double alertLowHealthHp = 6.0;

    /** Fire an alert when you are killed. */
    public boolean alertOnDeath = false;

    /** Play a sound in-game when an alert fires. */
    public boolean alertSound = true;

    /** Show the on-screen armed/cooldown indicator and alert toasts. */
    public boolean hudEnabled = true;

    /** Show the persistent ARMED / COOLDOWN chip (combat + toasts stay under hudEnabled). */
    public boolean hudArmedChip = true;

    /** Play UI click/hover sounds in the menu. */
    public boolean uiSounds = true;

    /** Whether the first-run setup overlay has been dismissed. */
    public boolean seenSetup = false;

    /** Surface alerts sent to you by linked teammates as an in-game chat message. */
    public boolean receiveInGameAlerts = true;

    /** How often (seconds) to poll Discord DMs for incoming teammate alerts. */
    public int inGameAlertPollSeconds = 5;

    /** Your clan's name, used in clan announcements / war posts. */
    public String clanName = "";

    /** Webhook the clan owner posts announcements / war calls to. */
    public String clanWebhookUrl = "";

    /** Whether clan posts prepend {@code @everyone}. */
    public boolean clanPingEveryone = true;

    /**
     * A pre-configured contact: a friendly name + Discord snowflake id, and an
     * optional linked Minecraft identity so their alerts can be surfaced in-game.
     */
    public static class Recipient {
        public String name = "";
        public String id = "";
        public String mcName = "";
        public String mcUuid = "";

        public Recipient() {
        }

        public Recipient(String name, String id) {
            this.name = name;
            this.id = id;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Path configFile() {
        return MinecraftClient.getInstance().runDirectory.toPath()
                .resolve("config").resolve("discord-controller.json");
    }

    public static DiscordConfig load() {
        Path path = configFile();
        if (Files.exists(path)) {
            try {
                DiscordConfig cfg = GSON.fromJson(Files.readString(path), DiscordConfig.class);
                if (cfg != null) {
                    if (cfg.recipients == null) {
                        cfg.recipients = new ArrayList<>();
                    }
                    if (cfg.alertTargetIds == null) {
                        cfg.alertTargetIds = new ArrayList<>();
                    }
                    if (cfg.disabledTargetIds == null) {
                        cfg.disabledTargetIds = new ArrayList<>();
                    }
                    if (cfg.alertWebhooks == null) {
                        cfg.alertWebhooks = new ArrayList<>();
                    }
                    if (cfg.alertWebhookUrl != null && !cfg.alertWebhookUrl.isBlank()
                            && !cfg.alertWebhooks.contains(cfg.alertWebhookUrl)) {
                        cfg.alertWebhooks.add(cfg.alertWebhookUrl); // migrate single -> list
                        cfg.alertWebhookUrl = "";
                    }
                    if (cfg.footer == null || cfg.footer.isBlank()
                            || cfg.footer.equals("sent through the Discord Controller Mod | Juicy Launcher")) {
                        cfg.footer = "Discord HUD Controller"; // migrate the old default
                    }
                    if (cfg.brand == null || cfg.brand.isBlank()) {
                        cfg.brand = "Juicy Launcher";
                    }
                    return cfg;
                }
            } catch (Exception ignored) {
                // corrupt config -> fall through to defaults
            }
        }
        return new DiscordConfig();
    }

    public void save() {
        try {
            Path path = configFile();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this));
        } catch (Exception ignored) {
        }
    }

    /** Whether a target id is currently enabled (not in the disabled list). */
    public boolean isTargetEnabled(String id) {
        return disabledTargetIds == null || !disabledTargetIds.contains(id);
    }

    /** Resolve a recipient name to its Discord id, or null if unknown. */
    public String findRecipientId(String name) {
        for (Recipient r : recipients) {
            if (r.name.equalsIgnoreCase(name)) {
                return r.id;
            }
        }
        return null;
    }
}
