package com.juicy.discordcontroller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DiscordConfig {

    public String brand = "Juicy Launcher";
    public String footer = "Discord HUD Controller";

    public List<String> alertWebhooks = new ArrayList<>();
    public String alertWebhookUrl = "";
    public boolean alertPingEveryone = true;

    public int answerVk = 0x27;
    public boolean answerCtrl = false;
    public boolean answerShift = false;
    public boolean answerAlt = false;

    public boolean autoAlertEnabled = true;
    public int alertHitThreshold = 5;
    public double alertHealthLoss = 6.0;
    public int alertWindowSeconds = 8;
    public int alertCooldownSeconds = 60;
    public double attackRadius = 6.0;
    public int alertManualCooldownSeconds = 10;

    public boolean alertLowHealthEnabled = true;
    public double alertLowHealthHp = 6.0;
    public boolean alertOnDeath = false;
    public boolean alertSound = true;

    public boolean hudEnabled = true;
    public boolean hudArmedChip = true;
    public boolean uiSounds = true;
    public boolean seenSetup = false;

    public String clanName = "";
    public String clanWebhookUrl = "";
    public boolean clanPingEveryone = true;

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
                    if (cfg.alertWebhooks == null) {
                        cfg.alertWebhooks = new ArrayList<>();
                    }
                    if (cfg.alertWebhookUrl != null && !cfg.alertWebhookUrl.isBlank()
                            && !cfg.alertWebhooks.contains(cfg.alertWebhookUrl)) {
                        cfg.alertWebhooks.add(cfg.alertWebhookUrl);
                        cfg.alertWebhookUrl = "";
                    }
                    if (cfg.brand == null || cfg.brand.isBlank()) {
                        cfg.brand = "Juicy Launcher";
                    }
                    if (cfg.footer == null || cfg.footer.isBlank()) {
                        cfg.footer = "Discord HUD Controller";
                    }
                    return cfg;
                }
            } catch (Exception ignored) {
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
}
