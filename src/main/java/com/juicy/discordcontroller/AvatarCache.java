package com.juicy.discordcontroller;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Loads Discord avatar PNGs into GUI textures, cached per user id. Failures are silent. */
public final class AvatarCache {

    private static final Map<String, Identifier> READY = new ConcurrentHashMap<>();
    private static final Set<String> BUSY = ConcurrentHashMap.newKeySet();

    private AvatarCache() {
    }

    /** Texture id for a user's avatar if loaded, else null (and starts a one-shot load). */
    public static Identifier get(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        Identifier id = READY.get(userId);
        if (id != null) {
            return id;
        }
        if (BUSY.add(userId)) {
            load(userId);
        }
        return null;
    }

    private static void load(String userId) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        if (cfg.token.isEmpty()) {
            return;
        }
        new Thread(() -> {
            try {
                JsonObject user = DiscordClient.fetchUser(cfg.token, userId);
                if (user == null || !user.has("avatar") || user.get("avatar").isJsonNull()) {
                    return;
                }
                String hash = user.get("avatar").getAsString();
                byte[] png = DiscordClient.httpGetBytes(
                        "https://cdn.discordapp.com/avatars/" + userId + "/" + hash + ".png?size=64");
                if (png == null) {
                    return;
                }
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.execute(() -> {
                    try {
                        NativeImage img = NativeImage.read(png);
                        NativeImageBackedTexture tex =
                                new NativeImageBackedTexture(() -> "juicy-avatar-" + userId, img);
                        Identifier tid = Identifier.of("discordcontroller", "avatars/" + userId);
                        mc.getTextureManager().registerTexture(tid, tex);
                        READY.put(userId, tid);
                    } catch (Exception ignored) {
                    }
                });
            } catch (Exception ignored) {
            }
        }, "dcontroller-avatar").start();
    }
}
