package com.juicy.discordcontroller;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class ControllerCommand {

    private static final Gson GSON = new Gson();

    private ControllerCommand() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("dcontroller")
                .executes(ControllerCommand::openMenu)
                .then(literal("menu").executes(ControllerCommand::openMenu))
                .then(literal("alert")
                        .then(literal("now").executes(ControllerCommand::alertNow))
                        .then(literal("test").executes(ControllerCommand::alertTest))
                        .then(literal("auto")
                                .then(literal("on").executes(c -> flag(c, "auto", true)))
                                .then(literal("off").executes(c -> flag(c, "auto", false))))
                        .then(literal("ping")
                                .then(literal("on").executes(c -> flag(c, "ping", true)))
                                .then(literal("off").executes(c -> flag(c, "ping", false))))
                        .then(literal("lowhealth")
                                .then(literal("on").executes(c -> flag(c, "lowhealth", true)))
                                .then(literal("off").executes(c -> flag(c, "lowhealth", false))))
                        .then(literal("death")
                                .then(literal("on").executes(c -> flag(c, "death", true)))
                                .then(literal("off").executes(c -> flag(c, "death", false))))
                        .then(literal("sound")
                                .then(literal("on").executes(c -> flag(c, "sound", true)))
                                .then(literal("off").executes(c -> flag(c, "sound", false))))
                        .then(literal("webhook")
                                .then(literal("clear").executes(ControllerCommand::clearWebhooks))
                                .then(literal("list").executes(ControllerCommand::listWebhooks))
                                .then(literal("remove")
                                        .then(argument("url", StringArgumentType.greedyString()).executes(ControllerCommand::removeWebhook)))
                                .then(literal("add")
                                        .then(argument("url", StringArgumentType.greedyString()).executes(ControllerCommand::addWebhook)))
                                .then(argument("url", StringArgumentType.greedyString()).executes(ControllerCommand::addWebhook)))
                        .then(literal("threshold")
                                .then(literal("hits").then(argument("n", IntegerArgumentType.integer(1)).executes(ControllerCommand::setHits)))
                                .then(literal("health").then(argument("hearts", DoubleArgumentType.doubleArg(0.5)).executes(ControllerCommand::setHealth)))
                                .then(literal("radius").then(argument("blocks", DoubleArgumentType.doubleArg(1.0)).executes(ControllerCommand::setRadius)))
                                .then(literal("window").then(argument("seconds", IntegerArgumentType.integer(1)).executes(ControllerCommand::setWindow)))
                                .then(literal("cooldown").then(argument("seconds", IntegerArgumentType.integer(0)).executes(ControllerCommand::setCooldown)))
                                .then(literal("manualcooldown").then(argument("seconds", IntegerArgumentType.integer(0)).executes(ControllerCommand::setManualCd)))
                                .then(literal("lowhealth").then(argument("hearts", DoubleArgumentType.doubleArg(0.5)).executes(ControllerCommand::setLowHealthValue))))
                        .then(literal("config").executes(ControllerCommand::showConfig)))
                .then(literal("clan")
                        .then(literal("name").then(argument("name", StringArgumentType.greedyString()).executes(ControllerCommand::setClanName)))
                        .then(literal("webhook")
                                .then(literal("clear").executes(ControllerCommand::clearClanWebhook))
                                .then(argument("url", StringArgumentType.greedyString()).executes(ControllerCommand::setClanWebhook)))
                        .then(literal("announce").then(argument("text", StringArgumentType.greedyString()).executes(ControllerCommand::clanAnnounce)))
                        .then(literal("war")
                                .then(argument("enemy", StringArgumentType.word())
                                        .then(argument("time", StringArgumentType.word())
                                                .executes(ControllerCommand::clanWar)
                                                .then(argument("notes", StringArgumentType.greedyString()).executes(ControllerCommand::clanWar)))))));
    }

    private static int openMenu(CommandContext<FabricClientCommandSource> ctx) {
        var client = ctx.getSource().getClient();
        client.execute(() -> client.setScreen(new ControllerScreen()));
        return 1;
    }

    private static int alertNow(CommandContext<FabricClientCommandSource> ctx) {
        AlertManager.get().sendManualAlert(ctx.getSource().getClient());
        return 1;
    }

    private static int alertTest(CommandContext<FabricClientCommandSource> ctx) {
        AlertManager.get().sendTestAlert(ctx.getSource().getClient());
        return 1;
    }

    private static int flag(CommandContext<FabricClientCommandSource> ctx, String which, boolean on) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        String label;
        switch (which) {
            case "auto" -> { cfg.autoAlertEnabled = on; label = "Auto attack-alert"; }
            case "ping" -> { cfg.alertPingEveryone = on; label = "@everyone ping"; }
            case "lowhealth" -> { cfg.alertLowHealthEnabled = on; label = "Low-health emergency"; }
            case "death" -> { cfg.alertOnDeath = on; label = "Death alert"; }
            default -> { cfg.alertSound = on; label = "Alert sound"; }
        }
        cfg.save();
        ctx.getSource().sendFeedback(Text.literal("§a" + label + " §f" + (on ? "enabled" : "disabled") + "§a."));
        return 1;
    }

    private static int addWebhook(CommandContext<FabricClientCommandSource> ctx) {
        String url = StringArgumentType.getString(ctx, "url").trim();
        if (!url.startsWith("https://") || !url.contains("/webhooks/")) {
            ctx.getSource().sendError(Text.literal("§cThat doesn't look like a Discord webhook URL."));
            return 0;
        }
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        if (!cfg.alertWebhooks.contains(url)) {
            cfg.alertWebhooks.add(url);
            cfg.save();
        }
        ctx.getSource().sendFeedback(Text.literal("§aWebhook added. Now " + cfg.alertWebhooks.size() + " total."));
        return 1;
    }

    private static int removeWebhook(CommandContext<FabricClientCommandSource> ctx) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        if (cfg.alertWebhooks.remove(StringArgumentType.getString(ctx, "url").trim())) {
            cfg.save();
            ctx.getSource().sendFeedback(Text.literal("§aWebhook removed."));
            return 1;
        }
        ctx.getSource().sendError(Text.literal("§cNo matching webhook (see §f/dcontroller alert webhook list§c)."));
        return 0;
    }

    private static int listWebhooks(CommandContext<FabricClientCommandSource> ctx) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        if (cfg.alertWebhooks.isEmpty()) {
            ctx.getSource().sendFeedback(Text.literal("§7No webhooks. Add one with §f/dcontroller alert webhook add <url>§7."));
            return 1;
        }
        ctx.getSource().sendFeedback(Text.literal("§aWebhooks:"));
        for (String url : cfg.alertWebhooks) {
            ctx.getSource().sendFeedback(Text.literal("§f  " + (url.length() > 52 ? url.substring(0, 52) + "…" : url)));
        }
        return 1;
    }

    private static int clearWebhooks(CommandContext<FabricClientCommandSource> ctx) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        cfg.alertWebhooks.clear();
        cfg.save();
        ctx.getSource().sendFeedback(Text.literal("§aAll webhooks cleared."));
        return 1;
    }

    private static int setHits(CommandContext<FabricClientCommandSource> ctx) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        cfg.alertHitThreshold = IntegerArgumentType.getInteger(ctx, "n");
        cfg.save();
        ctx.getSource().sendFeedback(Text.literal("§aAuto-alert fires above §f" + cfg.alertHitThreshold + "§a hits."));
        return 1;
    }

    private static int setHealth(CommandContext<FabricClientCommandSource> ctx) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        cfg.alertHealthLoss = DoubleArgumentType.getDouble(ctx, "hearts") * 2.0;
        cfg.save();
        ctx.getSource().sendFeedback(Text.literal("§aAuto-alert needs §f" + (cfg.alertHealthLoss / 2.0) + "§a hearts lost."));
        return 1;
    }

    private static int setRadius(CommandContext<FabricClientCommandSource> ctx) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        cfg.attackRadius = DoubleArgumentType.getDouble(ctx, "blocks");
        cfg.save();
        ctx.getSource().sendFeedback(Text.literal("§aAttacker radius: §f" + cfg.attackRadius + "§a blocks."));
        return 1;
    }

    private static int setWindow(CommandContext<FabricClientCommandSource> ctx) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        cfg.alertWindowSeconds = IntegerArgumentType.getInteger(ctx, "seconds");
        cfg.save();
        ctx.getSource().sendFeedback(Text.literal("§aHit window: §f" + cfg.alertWindowSeconds + "§as."));
        return 1;
    }

    private static int setCooldown(CommandContext<FabricClientCommandSource> ctx) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        cfg.alertCooldownSeconds = IntegerArgumentType.getInteger(ctx, "seconds");
        cfg.save();
        ctx.getSource().sendFeedback(Text.literal("§aAuto cooldown: §f" + cfg.alertCooldownSeconds + "§as."));
        return 1;
    }

    private static int setManualCd(CommandContext<FabricClientCommandSource> ctx) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        cfg.alertManualCooldownSeconds = IntegerArgumentType.getInteger(ctx, "seconds");
        cfg.save();
        ctx.getSource().sendFeedback(Text.literal("§aManual cooldown: §f" + cfg.alertManualCooldownSeconds + "§as."));
        return 1;
    }

    private static int setLowHealthValue(CommandContext<FabricClientCommandSource> ctx) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        cfg.alertLowHealthHp = DoubleArgumentType.getDouble(ctx, "hearts") * 2.0;
        cfg.save();
        ctx.getSource().sendFeedback(Text.literal("§aEmergency fires at §f" + (cfg.alertLowHealthHp / 2.0) + "§a hearts."));
        return 1;
    }

    private static int showConfig(CommandContext<FabricClientCommandSource> ctx) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        FabricClientCommandSource s = ctx.getSource();
        s.sendFeedback(Text.literal("§6=== Alert settings ==="));
        s.sendFeedback(Text.literal("§7Webhooks: §f" + cfg.alertWebhooks.size()
                + " §7· @everyone: " + (cfg.alertPingEveryone ? "§aon" : "§coff")));
        s.sendFeedback(Text.literal("§7Auto: " + (cfg.autoAlertEnabled ? "§aon" : "§coff")
                + " §7(> " + cfg.alertHitThreshold + " hits, ≥ " + (cfg.alertHealthLoss / 2.0) + "❤ in " + cfg.alertWindowSeconds + "s)"));
        s.sendFeedback(Text.literal("§7Low-health: " + (cfg.alertLowHealthEnabled ? "§aon" : "§coff")
                + " §7(" + (cfg.alertLowHealthHp / 2.0) + "❤) · death: " + (cfg.alertOnDeath ? "§aon" : "§coff")
                + " · sound: " + (cfg.alertSound ? "§aon" : "§coff")));
        return 1;
    }

    private static int setClanName(CommandContext<FabricClientCommandSource> ctx) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        cfg.clanName = StringArgumentType.getString(ctx, "name").trim();
        cfg.save();
        ctx.getSource().sendFeedback(Text.literal("§aClan name set to §f" + cfg.clanName + "§a."));
        return 1;
    }

    private static int setClanWebhook(CommandContext<FabricClientCommandSource> ctx) {
        String url = StringArgumentType.getString(ctx, "url").trim();
        if (!url.startsWith("https://") || !url.contains("/webhooks/")) {
            ctx.getSource().sendError(Text.literal("§cThat isn't a Discord webhook URL."));
            return 0;
        }
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        cfg.clanWebhookUrl = url;
        cfg.save();
        ctx.getSource().sendFeedback(Text.literal("§aClan webhook set."));
        return 1;
    }

    private static int clearClanWebhook(CommandContext<FabricClientCommandSource> ctx) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        cfg.clanWebhookUrl = "";
        cfg.save();
        ctx.getSource().sendFeedback(Text.literal("§aClan webhook cleared."));
        return 1;
    }

    private static int clanAnnounce(CommandContext<FabricClientCommandSource> ctx) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        if (cfg.clanWebhookUrl.isBlank()) {
            ctx.getSource().sendError(Text.literal("§cSet a clan webhook first: §f/dcontroller clan webhook <url>§c."));
            return 0;
        }
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "📣 " + (cfg.clanName.isBlank() ? "Clan" : cfg.clanName) + " announcement");
        embed.addProperty("description", StringArgumentType.getString(ctx, "text").trim());
        embed.addProperty("color", 0x9B59B6);
        postClan(ctx, cfg, embed, "Announcement posted.");
        return 1;
    }

    private static int clanWar(CommandContext<FabricClientCommandSource> ctx) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        if (cfg.clanWebhookUrl.isBlank()) {
            ctx.getSource().sendError(Text.literal("§cSet a clan webhook first: §f/dcontroller clan webhook <url>§c."));
            return 0;
        }
        String enemy = StringArgumentType.getString(ctx, "enemy");
        String time = StringArgumentType.getString(ctx, "time");
        String notes = optString(ctx, "notes");
        String our = cfg.clanName.isBlank() ? "Our clan" : cfg.clanName;
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "⚔ CLAN WAR");
        embed.addProperty("description", "**" + our + "**  vs  **" + enemy + "**");
        embed.addProperty("color", 0xE74C3C);
        JsonArray fields = new JsonArray();
        fields.add(field("Enemy", "`" + enemy + "`", true));
        fields.add(field("When", "`" + time + "`", true));
        if (!notes.isBlank()) {
            fields.add(field("Details", notes, false));
        }
        embed.add("fields", fields);
        postClan(ctx, cfg, embed, "Clan war posted: vs " + enemy + " @ " + time + ".");
        return 1;
    }

    private static void postClan(CommandContext<FabricClientCommandSource> ctx, DiscordConfig cfg, JsonObject embed, String okMsg) {
        JsonObject footer = new JsonObject();
        footer.addProperty("text", cfg.footer + " | " + cfg.brand);
        embed.add("footer", footer);
        JsonObject root = new JsonObject();
        if (cfg.clanPingEveryone) {
            root.addProperty("content", "@everyone");
            JsonObject am = new JsonObject();
            JsonArray parse = new JsonArray();
            parse.add("everyone");
            am.add("parse", parse);
            root.add("allowed_mentions", am);
        }
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        root.add("embeds", embeds);
        String json = GSON.toJson(root);
        String url = cfg.clanWebhookUrl;
        FabricClientCommandSource source = ctx.getSource();
        source.sendFeedback(Text.literal("§7Posting to clan webhook..."));
        new Thread(() -> {
            boolean ok = DiscordClient.sendWebhook(url, json);
            source.getClient().execute(() -> source.sendFeedback(Text.literal(ok ? "§a" + okMsg : "§cFailed to post (check the webhook URL).")));
        }, "dcontroller-clan").start();
    }

    private static JsonObject field(String name, String value, boolean inline) {
        JsonObject f = new JsonObject();
        f.addProperty("name", name);
        f.addProperty("value", value);
        f.addProperty("inline", inline);
        return f;
    }

    private static String optString(CommandContext<FabricClientCommandSource> ctx, String name) {
        try {
            return StringArgumentType.getString(ctx, name);
        } catch (Exception e) {
            return "";
        }
    }

    static CompletableFuture<Suggestions> suggestValues(SuggestionsBuilder builder, String... values) {
        String remaining = builder.getRemaining().toLowerCase();
        for (String v : new LinkedHashSet<>(List.of(values))) {
            if (v.toLowerCase().startsWith(remaining)) {
                builder.suggest(v);
            }
        }
        return builder.buildFuture();
    }
}
