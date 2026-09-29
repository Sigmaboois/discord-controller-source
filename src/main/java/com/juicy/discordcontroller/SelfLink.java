package com.juicy.discordcontroller;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;

public final class SelfLink {

    private static final SelfLink INSTANCE = new SelfLink();
    private volatile boolean resolving = false;

    private SelfLink() {
    }

    public static SelfLink get() {
        return INSTANCE;
    }

    public void onClientTick(MinecraftClient mc) {
        if (mc.player == null) {
            return;
        }
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        if (cfg.token == null || cfg.token.isEmpty()) {
            return;
        }

        String uuid = mc.player.getUuid().toString().replace("-", "");
        String name = mc.player.getName().getString();

        boolean changed = false;
        if (!uuid.equals(cfg.selfMcUuid)) {
            cfg.selfMcUuid = uuid;
            changed = true;
        }
        if (name != null && !name.equals(cfg.selfMcName)) {
            cfg.selfMcName = name;
            changed = true;
        }
        if (changed) {
            cfg.save();
        }

        boolean hasDc = cfg.selfDiscordId != null && !cfg.selfDiscordId.isBlank();
        if (!hasDc && !resolving) {
            resolving = true;
            String token = cfg.token;
            new Thread(() -> {
                try {
                    JsonObject acc = DiscordClient.fetchCurrentUser(token);
                    if (acc != null && acc.has("id")) {
                        String id = acc.get("id").getAsString();
                        MinecraftClient.getInstance().execute(() -> {
                            DiscordConfig c = DiscordControllerMod.getConfig();
                            c.selfDiscordId = id;
                            c.save();
                        });
                    }
                } finally {
                    resolving = false;
                }
            }, "dcontroller-selflink").start();
        }
    }
}
