package com.juicy.discordcontroller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Surfaces teammate alerts as a big coloured in-game chat message, even across
 * different servers or singleplayer. The bot posts alerts into a shared alert
 * channel; this polls that channel and renders any new alert (that isn't your
 * own) locally.
 */
public final class IncomingAlerts {

    private static final IncomingAlerts INSTANCE = new IncomingAlerts();

    private static final Pattern P_SERVER = Pattern.compile("\\*\\*Server:\\*\\* `([^`]+)`");
    private static final Pattern P_LOCATION = Pattern.compile("\\*\\*Location:\\*\\* `([^`]+)`\\s*·\\s*([^\\n]+)");
    private static final Pattern P_ATTACKER = Pattern.compile("\\*\\*(?:Attacker|Nearby player|Killed by):\\*\\* `([^`]+)`");

    private final Set<String> shown = ConcurrentHashMap.newKeySet();
    private volatile boolean polling = false;
    private String baseline = null;
    private String watchedChannel = null;
    private long lastPoll = 0L;

    private IncomingAlerts() {
    }

    public static IncomingAlerts get() {
        return INSTANCE;
    }

    public void onClientTick(MinecraftClient mc) {
        if (mc.player == null) {
            return;
        }
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        String channel = cfg.alertGroupChannelId;
        if (!cfg.receiveInGameAlerts || cfg.token.isEmpty() || channel == null || channel.isBlank() || polling) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastPoll < Math.max(3, cfg.inGameAlertPollSeconds) * 1000L) {
            return;
        }
        lastPoll = now;

        // Reset the baseline if the alert channel changed.
        if (!channel.equals(watchedChannel)) {
            watchedChannel = channel;
            baseline = null;
        }

        String token = cfg.token;
        String me = mc.player.getName().getString();
        polling = true;
        new Thread(() -> {
            try {
                poll(mc, token, channel, me);
            } finally {
                polling = false;
            }
        }, "dcontroller-incoming").start();
    }

    private void poll(MinecraftClient mc, String token, String channel, String me) {
        JsonArray msgs = DiscordClient.fetchMessages(token, channel, baseline, 10);
        if (msgs == null) {
            return;
        }
        if (baseline == null) {
            baseline = maxId(msgs, "0");
            return; // first poll just sets a baseline so old messages aren't replayed
        }

        String max = baseline;
        List<JsonObject> fresh = new ArrayList<>();
        for (JsonElement e : msgs) {
            JsonObject m = e.getAsJsonObject();
            String id = m.get("id").getAsString();
            if (cmpSnowflake(id, max) > 0) {
                max = id;
            }
            String content = m.has("content") && !m.get("content").isJsonNull()
                    ? m.get("content").getAsString() : "";
            if (isAlert(content) && !shown.contains(id)) {
                fresh.add(m);
            }
        }
        baseline = max;

        fresh.sort((a, b) -> cmpSnowflake(a.get("id").getAsString(), b.get("id").getAsString()));
        for (JsonObject m : fresh) {
            shown.add(m.get("id").getAsString());
            String content = m.get("content").getAsString();
            String victim = parseVictim(content, null);
            if (victim != null && victim.equalsIgnoreCase(me)) {
                continue; // don't echo your own alert back to you
            }
            mc.execute(() -> display(mc, content, victim));
        }
        if (shown.size() > 500) {
            shown.clear();
        }
    }

    private static boolean isAlert(String content) {
        return content.contains("**Location:**") && content.contains("**Server:**");
    }

    private void display(MinecraftClient mc, String content, String victim) {
        if (mc.player == null) {
            return;
        }
        String server = group(P_SERVER, content, 1);
        Matcher loc = P_LOCATION.matcher(content);
        String coords = loc.find() ? loc.group(1).trim() : "?";
        String dim = coords.equals("?") ? "?" : loc.group(2).trim();
        String attacker = group(P_ATTACKER, content, 1);

        String heading;
        String color;
        String subject;
        if (content.contains("UNDER ATTACK")) {
            heading = "TEAMMATE UNDER ATTACK";
            color = "§c";
            subject = "needs help!";
        } else if (content.contains("LOW HEALTH")) {
            heading = "TEAMMATE CRITICAL";
            color = "§4";
            subject = "is about to die!";
        } else if (content.contains("DOWNED")) {
            heading = "TEAMMATE DOWNED";
            color = "§4";
            subject = "was killed!";
        } else if (content.contains("HELP REQUEST")) {
            heading = "TEAMMATE NEEDS HELP";
            color = "§6";
            subject = "needs backup!";
        } else if (content.contains("TEST ALERT")) {
            heading = "TEST ALERT";
            color = "§b";
            subject = "(test)";
        } else {
            heading = "TEAMMATE ALERT";
            color = "§e";
            subject = "needs help!";
        }

        String sender = victim != null ? victim : "A teammate";
        String myServer = AlertManager.serverAddress(mc);
        boolean joinable = server != null && !server.isBlank()
                && !server.equalsIgnoreCase("Singleplayer") && !server.equalsIgnoreCase("Unknown");
        boolean sameServer = joinable && server.equalsIgnoreCase(myServer);

        String bar = "§8§m                                          ";
        chat(mc, bar);
        chat(mc, color + "§l  " + heading);
        chat(mc, "§f  " + sender + " §7" + subject);
        if (attacker != null) {
            chat(mc, "§7  Attacker: §f" + attacker);
        }
        chat(mc, "§7  Location: §f" + coords + " §7(" + dim + ")");
        if (sameServer) {
            chat(mc, "§a  ✔ Same server — get to them!");
        } else if (joinable) {
            chat(mc, "§7  Server: §e" + server);
        } else {
            chat(mc, "§8  (they are in singleplayer)");
        }
        chat(mc, "§8  " + DiscordControllerMod.getConfig().footer);
        chat(mc, bar);

        DiscordConfig cfg = DiscordControllerMod.getConfig();
        if (cfg.alertSound) {
            Sounds.ui(mc, net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BELL, 0.7f);
        }
        HudOverlay.toast(sender + " needs help!", 0xFFFF6B6B);
    }

    private static String parseVictim(String content, String fallback) {
        for (String line : content.split("\n")) {
            String l = line.trim();
            if (l.startsWith("# ")) {
                int dash = l.lastIndexOf('—');
                if (dash >= 0) {
                    String v = l.substring(dash + 1).trim();
                    return v.replaceAll("(?i)\\s*(needs help!|was downed!)$", "").trim();
                }
            }
        }
        return fallback;
    }

    private static String group(Pattern p, String s, int g) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(g).trim() : null;
    }

    private static String maxId(JsonArray msgs, String fallback) {
        String max = fallback;
        for (JsonElement e : msgs) {
            String id = e.getAsJsonObject().get("id").getAsString();
            if (cmpSnowflake(id, max) > 0) {
                max = id;
            }
        }
        return max;
    }

    private static int cmpSnowflake(String a, String b) {
        if (a.length() != b.length()) {
            return Integer.compare(a.length(), b.length());
        }
        return a.compareTo(b);
    }

    private static void chat(MinecraftClient mc, String line) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(line), false);
        }
    }
}
