package com.juicy.discordcontroller;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public final class DiscordClient {

    public static final String API = "https://discord.com/api/v10";

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private DiscordClient() {
    }

    private static JsonObject get(String token, String endpoint) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(API + endpoint))
                .header("Authorization", token)
                .header("User-Agent", "DiscordControllerMod/1.0 (Juicy Launcher)")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new IllegalStateException("Discord returned HTTP " + resp.statusCode());
        }
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }

    private static JsonObject post(String token, String endpoint, String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(API + endpoint))
                .header("Authorization", token)
                .header("Content-Type", "application/json")
                .header("User-Agent", "DiscordControllerMod/1.0 (Juicy Launcher)")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new IllegalStateException("Discord returned HTTP " + resp.statusCode());
        }
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }

    public static JsonObject fetchCurrentUser(String token) {
        try {
            JsonObject o = get(token, "/users/@me");
            return o.has("id") ? o : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static String openDmChannel(String token, String recipientId) {
        try {
            JsonObject o = post(token, "/users/@me/channels",
                    GSON.toJson(Map.of("recipient_id", recipientId)));
            return o.has("id") ? o.get("id").getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean sendMessage(String token, String channelId, String content) {
        try {
            post(token, "/channels/" + channelId + "/messages",
                    GSON.toJson(Map.of("content", content)));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static JsonObject fetchUser(String token, String userId) {
        try {
            JsonObject o = get(token, "/users/" + userId);
            return o.has("id") ? o : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static JsonArray fetchMessages(String token, String channelId, String afterId, int limit) {
        try {
            StringBuilder ep = new StringBuilder("/channels/").append(channelId)
                    .append("/messages?limit=").append(limit);
            if (afterId != null && !afterId.isBlank() && !afterId.equals("0")) {
                ep.append("&after=").append(afterId);
            }
            HttpRequest req = HttpRequest.newBuilder(URI.create(API + ep))
                    .header("Authorization", token)
                    .header("User-Agent", "DiscordControllerMod/1.0 (Juicy Launcher)")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                return null;
            }
            return JsonParser.parseString(resp.body()).getAsJsonArray();
        } catch (Exception e) {
            return null;
        }
    }

    public static JsonObject httpGetJson(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "DiscordControllerMod/1.0 (Juicy Launcher)")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400 || resp.body() == null || resp.body().isBlank()) {
                return null;
            }
            return JsonParser.parseString(resp.body()).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    public static String fetchMinecraftUuid(String name) {
        JsonObject o = httpGetJson("https://api.mojang.com/users/profiles/minecraft/" + name);
        return o != null && o.has("id") && !o.get("id").isJsonNull() ? o.get("id").getAsString() : null;
    }

    public static byte[] httpGetBytes(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "DiscordControllerMod/1.0 (Juicy Launcher)")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
            return resp.statusCode() < 400 ? resp.body() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean sendWebhook(String webhookUrl, String json) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "DiscordControllerMod/1.0 (Juicy Launcher)")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() < 400;
        } catch (Exception e) {
            return false;
        }
    }
}
