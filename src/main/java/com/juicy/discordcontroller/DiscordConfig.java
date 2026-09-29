package com.juicy.discordcontroller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class DiscordConfig {

    public transient String token = "";
    private transient byte[] sessionKey;

    public String encToken = "";
    public String encIv = "";
    public String pwSalt = "";
    public String pwVerifier = "";

    public List<Recipient> recipients = new ArrayList<>();
    public String footer = "Discord HUD Controller";
    public String brand = "Juicy Launcher";
    public int answerVk = 0x27;
    public boolean answerCtrl = false;
    public boolean answerShift = false;
    public boolean answerAlt = false;
    public boolean autoAlertEnabled = true;
    public List<String> alertTargetIds = new ArrayList<>();
    public List<String> disabledTargetIds = new ArrayList<>();
    public List<String> alertWebhooks = new ArrayList<>();
    public String alertWebhookUrl = "";
    public String alertGroupChannelId = "";
    public boolean alertPingEveryone = true;
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
    public boolean receiveInGameAlerts = true;
    public int inGameAlertPollSeconds = 5;
    public String clanName = "";
    public String clanWebhookUrl = "";
    public boolean clanPingEveryone = true;

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
                    if (cfg.recipients == null) cfg.recipients = new ArrayList<>();
                    if (cfg.alertTargetIds == null) cfg.alertTargetIds = new ArrayList<>();
                    if (cfg.disabledTargetIds == null) cfg.disabledTargetIds = new ArrayList<>();
                    if (cfg.alertWebhooks == null) cfg.alertWebhooks = new ArrayList<>();
                    if (cfg.encToken == null) cfg.encToken = "";
                    if (cfg.encIv == null) cfg.encIv = "";
                    if (cfg.pwSalt == null) cfg.pwSalt = "";
                    if (cfg.pwVerifier == null) cfg.pwVerifier = "";
                    cfg.token = "";
                    cfg.sessionKey = null;
                    if (cfg.alertWebhookUrl != null && !cfg.alertWebhookUrl.isBlank()
                            && !cfg.alertWebhooks.contains(cfg.alertWebhookUrl)) {
                        cfg.alertWebhooks.add(cfg.alertWebhookUrl);
                        cfg.alertWebhookUrl = "";
                    }
                    if (cfg.footer == null || cfg.footer.isBlank()
                            || cfg.footer.equals("sent through the Discord Controller Mod | Juicy Launcher")) {
                        cfg.footer = "Discord HUD Controller";
                    }
                    if (cfg.brand == null || cfg.brand.isBlank()) {
                        cfg.brand = "Juicy Launcher";
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

    public boolean hasPassword() {
        return pwVerifier != null && !pwVerifier.isBlank();
    }

    public boolean hasEncryptedToken() {
        return encToken != null && !encToken.isBlank();
    }

    public boolean isLocked() {
        return hasEncryptedToken() && (token == null || token.isBlank());
    }

    public boolean setPassword(String password) {
        try {
            if (hasEncryptedToken() && (token == null || token.isBlank())) {
                return false;
            }
            byte[] salt = TokenCrypto.random(16);
            byte[] key = TokenCrypto.deriveKey(password, salt);
            pwSalt = Base64.getEncoder().encodeToString(salt);
            pwVerifier = TokenCrypto.verifier(key);
            if (token != null && !token.isBlank()) {
                String[] enc = TokenCrypto.encrypt(key, token);
                encIv = enc[0];
                encToken = enc[1];
            }
            sessionKey = key;
            save();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean unlock(String password) {
        if (!hasPassword()) {
            return false;
        }
        try {
            byte[] salt = Base64.getDecoder().decode(pwSalt);
            byte[] key = TokenCrypto.deriveKey(password, salt);
            if (!TokenCrypto.matches(key, pwVerifier)) {
                return false;
            }
            sessionKey = key;
            if (hasEncryptedToken()) {
                token = TokenCrypto.decrypt(key, encIv, encToken);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void lock() {
        token = "";
        sessionKey = null;
    }

    public boolean setToken(String value) {
        if (sessionKey == null) {
            return false;
        }
        try {
            String[] enc = TokenCrypto.encrypt(sessionKey, value);
            encIv = enc[0];
            encToken = enc[1];
            token = value;
            save();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isTargetEnabled(String id) {
        return disabledTargetIds == null || !disabledTargetIds.contains(id);
    }

    public String findRecipientId(String name) {
        for (Recipient r : recipients) {
            if (r.name.equalsIgnoreCase(name)) {
                return r.id;
            }
        }
        return null;
    }
}
