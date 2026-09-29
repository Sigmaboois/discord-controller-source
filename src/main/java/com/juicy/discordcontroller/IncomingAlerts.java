package com.juicy.discordcontroller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IncomingAlerts {

    private static final IncomingAlerts INSTANCE = new IncomingAlerts();
    private static final int MAX_CONTACTS = 12;

    private static final Pattern P_SERVER = Pattern.compile("\\*\\*Server:\\*\\* `([^`]+)`");
    private static final Pattern P_LOCATION = Pattern.compile("\\*\\*Location:\\*\\* `([^`]+)`\\s*·\\s*([^\\n]+)");
    private static final Pattern P_ATTACKER = Pattern.compile("\\*\\*(?:Attacker|Nearby player|Killed by):\\*\\* `([^`]+)`");

    private final Map<String, String> channelCache = new ConcurrentHashMap<>();
    private final Map<String, String> baseline = new ConcurrentHashMap<>();
    private final Set<String> shown = ConcurrentHashMap.newKeySet();
    private volatile boolean polling = false;
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
        if (!cfg.receiveInGameAlerts || cfg.isLocked() || cfg.token == null || cfg.token.isEmpty() || polling) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastPoll < Math.max(3, cfg.inGameAlertPollSeconds) * 1000L) {
            return;
        }
        lastPoll = now;

        List<DiscordConfig.Recipient> team = new ArrayList<>();
        for (DiscordConfig.Recipient r : cfg.recipients) {
            if (r.id != null && r.id.matches("\\d{15,}")) {
                team.add(r);
            }
        }
        if (team.isEmpty()) {
            return;
        }

        String token = cfg.token;
        polling = true;
        new Thread(() -> {
            try {
                int n = 0;
                for (DiscordConfig.Recipient r : team) {
                    if (n++ >= MAX_CONTACTS) {
                        break;
                    }
                    pollContact(mc, token, r);
                }
            } finally {
                polling = false;
            }
        }, "dcontroller-incoming").start();
    }

    private void pollContact(MinecraftClient mc, String token, DiscordConfig.Recipient r) {
        String ch = channelCache.computeIfAbsent(r.id, id -> DiscordClient.openDmChannel(token, id));
        if (ch == null) {
            channelCache.remove(r.id);
            return;
        }
        String after = baseline.get(ch);
        JsonArray msgs = DiscordClient.fetchMessages(token, ch, after, 8);
        if (msgs == null) {
            return;
        }

        if (after == null) {
            baseline.put(ch, maxId(msgs, "0"));
            return;
        }

        String max = after;
        List<JsonObject> fresh = new ArrayList<>();
        for (JsonElement e : msgs) {
            JsonObject m = e.getAsJsonObject();
            String id = m.get("id").getAsString();
            if (cmpSnowflake(id, max) > 0) {
                max = id;
            }
            String authorId = m.getAsJsonObject("author").get("id").getAsString();
            String content = m.has("content") && !m.get("content").isJsonNull()
                    ? m.get("content").getAsString() : "";
            if (r.id.equals(authorId) && isAlert(content) && !shown.contains(id)) {
                fresh.add(m);
            }
        }
        baseline.put(ch, max);

        fresh.sort((a, b) -> cmpSnowflake(a.get("id").getAsString(), b.get("id").getAsString()));
        for (JsonObject m : fresh) {
            shown.add(m.get("id").getAsString());
            String content = m.get("content").getAsString();
            mc.execute(() -> display(mc, r, content));
        }
        if (shown.size() > 500) {
            shown.clear();
        }
    }

    private static boolean isAlert(String content) {
        return content.contains("**Location:**") && content.contains("**Server:**");
    }

    private void display(MinecraftClient mc, DiscordConfig.Recipient from, String content) {
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

        String sender = from.mcName != null && !from.mcName.isBlank()
                ? from.mcName
                : parseVictim(content, from.name);

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
            Text line = Text.literal("§7  Server: §e" + server + "  ")
                    .append(joinButton(server));
            mc.player.sendMessage(line, false);
        } else {
            chat(mc, "§8  (they are in singleplayer)");
        }
        chat(mc, "§8  Discord HUD Controller");
        chat(mc, bar);

        DiscordConfig cfg = DiscordControllerMod.getConfig();
        if (cfg.alertSound) {
            Sounds.ui(mc, net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BELL, 0.7f);
        }
        HudOverlay.toast(sender + " needs help!", 0xFFFF6B6B);
    }

    private static Text joinButton(String server) {
        return Text.literal("[ JOIN ]")
                .styled(s -> s.withColor(0x55FF55)
                        .withBold(true)
                        .withClickEvent(new ClickEvent.RunCommand("/djoin " + server))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Text.literal("§aClick to join §f" + server))));
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
