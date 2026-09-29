package com.juicy.discordcontroller;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class ControllerCommand {

    private ControllerCommand() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("dcontroller")
                .executes(ControllerCommand::openMenu)
                .then(literal("menu").executes(ControllerCommand::openMenu))
                .then(literal("password")
                        .then(argument("password", StringArgumentType.greedyString())
                                .executes(ControllerCommand::setPasswordCmd)))
                .then(literal("unlock")
                        .then(argument("password", StringArgumentType.greedyString())
                                .executes(ControllerCommand::unlockCmd)))
                .then(literal("lock").executes(ControllerCommand::lockCmd))
                .then(literal("token")
                        .then(argument("token", StringArgumentType.greedyString())
                                .executes(ControllerCommand::pasteToken)))
                .then(literal("detect").executes(ControllerCommand::detectToken))
                .then(literal("account").executes(ControllerCommand::showAccount))
                .then(literal("receive")
                        .then(literal("on").executes(c -> setReceive(c, true)))
                        .then(literal("off").executes(c -> setReceive(c, false))))
                .then(literal("recipients")
                        .then(literal("add")
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(ControllerCommand::suggestRecipientNames)
                                        .then(argument("id", StringArgumentType.greedyString())
                                                .executes(ControllerCommand::addRecipient))))
                        .then(literal("remove")
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(ControllerCommand::suggestRecipientNames)
                                        .executes(ControllerCommand::removeRecipient)))
                        .then(literal("mc")
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(ControllerCommand::suggestRecipientNames)
                                        .then(argument("mcname", StringArgumentType.word())
                                                .executes(ControllerCommand::linkMc))))
                        .then(literal("list").executes(ControllerCommand::listRecipients)))
                .then(literal("alert")
                        .then(literal("now").executes(ControllerCommand::alertNow))
                        .then(literal("test").executes(ControllerCommand::alertTest))
                        .then(literal("auto")
                                .then(literal("on").executes(c -> setAuto(c, true)))
                                .then(literal("off").executes(c -> setAuto(c, false))))
                        .then(literal("ping")
                                .then(literal("on").executes(c -> setPing(c, true)))
                                .then(literal("off").executes(c -> setPing(c, false))))
                        .then(literal("lowhealth")
                                .then(literal("on").executes(c -> setLowHealth(c, true)))
                                .then(literal("off").executes(c -> setLowHealth(c, false))))
                        .then(literal("death")
                                .then(literal("on").executes(c -> setDeath(c, true)))
                                .then(literal("off").executes(c -> setDeath(c, false))))
                        .then(literal("sound")
                                .then(literal("on").executes(c -> setSound(c, true)))
                                .then(literal("off").executes(c -> setSound(c, false))))
                        .then(literal("targets")
                                .then(literal("add")
                                        .then(argument("ids", StringArgumentType.greedyString())
                                                .suggests(ControllerCommand::suggestRecipientNames)
                                                .executes(ControllerCommand::addTarget)))
                                .then(literal("remove")
                                        .then(argument("ids", StringArgumentType.greedyString())
                                                .suggests(ControllerCommand::suggestTargets)
                                                .executes(ControllerCommand::removeTarget)))
                                .then(literal("enable")
                                        .then(argument("ids", StringArgumentType.greedyString())
                                                .suggests(ControllerCommand::suggestTargets)
                                                .executes(c -> setTargetEnabled(c, true))))
                                .then(literal("disable")
                                        .then(argument("ids", StringArgumentType.greedyString())
                                                .suggests(ControllerCommand::suggestTargets)
                                                .executes(c -> setTargetEnabled(c, false))))
                                .then(literal("list").executes(ControllerCommand::listTargets)))
                        .then(literal("group")
                                .then(literal("clear").executes(ControllerCommand::clearGroup))
                                .then(argument("id", StringArgumentType.word())
                                        .executes(ControllerCommand::setGroup)))
                        .then(literal("webhook")
                                .then(literal("clear").executes(ControllerCommand::clearWebhooks))
                                .then(literal("list").executes(ControllerCommand::listWebhooks))
                                .then(literal("remove")
                                        .then(argument("url", StringArgumentType.greedyString())
                                                .executes(ControllerCommand::removeWebhook)))
                                .then(literal("add")
                                        .then(argument("url", StringArgumentType.greedyString())
                                                .executes(ControllerCommand::addWebhook)))
                                .then(argument("url", StringArgumentType.greedyString())
                                        .executes(ControllerCommand::addWebhook)))
                        .then(literal("threshold")
                                .then(literal("hits")
                                        .then(argument("n", IntegerArgumentType.integer(1))
                                                .suggests((c, b) -> suggestValues(b,
                                                        String.valueOf(DiscordControllerMod.getConfig().alertHitThreshold),
                                                        "3", "5", "8", "10"))
                                                .executes(ControllerCommand::setHits)))
                                .then(literal("health")
                                        .then(argument("hearts", DoubleArgumentType.doubleArg(0.5))
                                                .suggests((c, b) -> suggestValues(b,
                                                        trimNum(DiscordControllerMod.getConfig().alertHealthLoss / 2.0),
                                                        "2", "3", "5", "10"))
                                                .executes(ControllerCommand::setHealth)))
                                .then(literal("radius")
                                        .then(argument("blocks", DoubleArgumentType.doubleArg(1.0))
                                                .suggests((c, b) -> suggestValues(b,
                                                        trimNum(DiscordControllerMod.getConfig().attackRadius),
                                                        "4", "5", "6", "8"))
                                                .executes(ControllerCommand::setRadius)))
                                .then(literal("window")
                                        .then(argument("seconds", IntegerArgumentType.integer(1))
                                                .suggests((c, b) -> suggestValues(b,
                                                        String.valueOf(DiscordControllerMod.getConfig().alertWindowSeconds),
                                                        "5", "8", "10", "15"))
                                                .executes(ControllerCommand::setWindow)))
                                .then(literal("cooldown")
                                        .then(argument("seconds", IntegerArgumentType.integer(0))
                                                .suggests((c, b) -> suggestValues(b,
                                                        String.valueOf(DiscordControllerMod.getConfig().alertCooldownSeconds),
                                                        "0", "30", "60", "120"))
                                                .executes(ControllerCommand::setCooldown)))
                                .then(literal("manualcooldown")
                                        .then(argument("seconds", IntegerArgumentType.integer(0))
                                                .suggests((c, b) -> suggestValues(b,
                                                        String.valueOf(DiscordControllerMod.getConfig().alertManualCooldownSeconds),
                                                        "0", "5", "10", "30"))
                                                .executes(ControllerCommand::setManualCooldown)))
                                .then(literal("lowhealth")
                                        .then(argument("hearts", DoubleArgumentType.doubleArg(0.5))
                                                .suggests((c, b) -> suggestValues(b,
                                                        trimNum(DiscordControllerMod.getConfig().alertLowHealthHp / 2.0),
                                                        "2", "3", "4", "5"))
                                                .executes(ControllerCommand::setLowHealthValue))))
                        .then(literal("config").executes(ControllerCommand::showAlertConfig))));

        dispatcher.register(literal("dcontroller")
                .then(literal("clan")
                        .then(literal("name")
                                .then(argument("name", StringArgumentType.greedyString())
                                        .executes(ControllerCommand::setClanName)))
                        .then(literal("webhook")
                                .then(literal("clear").executes(ControllerCommand::clearClanWebhook))
                                .then(argument("url", StringArgumentType.greedyString())
                                        .executes(ControllerCommand::setClanWebhook)))
                        .then(literal("announce")
                                .then(argument("text", StringArgumentType.greedyString())
                                        .executes(ControllerCommand::clanAnnounce)))
                        .then(literal("war")
                                .then(argument("enemy", StringArgumentType.word())
                                        .then(argument("time", StringArgumentType.word())
                                                .executes(ControllerCommand::clanWar)
                                                .then(argument("notes", StringArgumentType.greedyString())
                                                        .executes(ControllerCommand::clanWar)))))));
    }

    private static int openMenu(CommandContext<FabricClientCommandSource> ctx) {
        var client = ctx.getSource().getClient();
        client.execute(() -> client.setScreen(new ControllerScreen()));
        return 1;
    }

    private static int setPasswordCmd(CommandContext<FabricClientCommandSource> ctx) {
        String pw = StringArgumentType.getString(ctx, "password").trim();
        if (pw.length() < 4) {
            ctx.getSource().sendError(Text.literal("§cPassword must be at least 4 characters."));
            return 0;
        }
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        if (cfg.setPassword(pw)) {
            ctx.getSource().sendFeedback(Text.literal("§aMaster password saved. The token is encrypted with it."));
            return 1;
        }
        ctx.getSource().sendError(Text.literal("§cCould not set password — if a token is stored, unlock first with §f/dcontroller unlock <password>§c."));
        return 0;
    }

    private static int unlockCmd(CommandContext<FabricClientCommandSource> ctx) {
        String pw = StringArgumentType.getString(ctx, "password").trim();
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        if (cfg.unlock(pw)) {
            ctx.getSource().sendFeedback(Text.literal("§aUnlocked. Token decrypted for this session."));
            return 1;
        }
        ctx.getSource().sendError(Text.literal("§cWrong password, or no password set."));
        return 0;
    }

    private static int lockCmd(CommandContext<FabricClientCommandSource> ctx) {
        DiscordControllerMod.getConfig().lock();
        ctx.getSource().sendFeedback(Text.literal("§aLocked. Token cleared from memory."));
        return 1;
    }

    private static int pasteToken(CommandContext<FabricClientCommandSource> ctx) {
        applyToken(ctx.getSource(), StringArgumentType.getString(ctx, "token").trim());
        return 1;
    }

    private static int detectToken(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        source.sendFeedback(Text.literal("§7Detecting Discord token..."));
        new Thread(() -> {
            String token = TokenDetector.detect();
            source.getClient().execute(() -> {
                if (token == null) {
                    source.sendError(Text.literal("§cNo Discord token found. Is Discord installed?"));
                } else {
                    applyToken(source, token);
                }
            });
        }, "dcontroller-detect").start();
        return 1;
    }

    private static void applyToken(FabricClientCommandSource source, String token) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        if (!cfg.hasPassword()) {
            source.sendError(Text.literal("§cSet a master password first: §f/dcontroller password <password>§c."));
            return;
        }
        if (cfg.isLocked()) {
            source.sendError(Text.literal("§cToken is locked. Unlock first: §f/dcontroller unlock <password>§c."));
            return;
        }
        source.sendFeedback(Text.literal("§7Checking account..."));
        new Thread(() -> {
            var account = DiscordClient.fetchCurrentUser(token);
            source.getClient().execute(() -> {
                if (account == null) {
                    source.sendError(Text.literal("§cThat token is invalid."));
                    return;
                }
                if (!cfg.setToken(token)) {
                    source.sendError(Text.literal("§cCould not encrypt the token. Unlock first."));
                    return;
                }
                source.sendFeedback(Text.literal("§aSaved (encrypted). Logged in as " + describe(account)));
            });
        }, "dcontroller-confirm").start();
    }

    private static int showAccount(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        if (cfg.isLocked()) {
            source.sendError(Text.literal("§eToken is locked. Unlock with §f/dcontroller unlock <password>§e."));
            return 0;
        }
        if (cfg.token == null || cfg.token.isEmpty()) {
            source.sendError(Text.literal("§cNo token configured."));
            return 0;
        }
        new Thread(() -> {
            var account = DiscordClient.fetchCurrentUser(cfg.token);
            source.getClient().execute(() -> {
                if (account == null) {
                    source.sendError(Text.literal("§cStored token is invalid."));
                } else {
                    source.sendFeedback(Text.literal("§aLogged in as " + describe(account)));
                    if (cfg.selfMcName != null && !cfg.selfMcName.isBlank()) {
                        source.sendFeedback(Text.literal("§7  Minecraft: §f" + cfg.selfMcName
                                + " §7(§f" + cfg.selfMcUuid + "§7)"));
                    } else {
                        source.sendFeedback(Text.literal("§7  Minecraft: §7linking automatically once you're in a world."));
                    }
                }
            });
        }, "dcontroller-account").start();
        return 1;
    }

    private static String describe(com.google.gson.JsonObject account) {
        String username = account.has("username") ? account.get("username").getAsString() : "?";
        String global = account.has("global_name") && !account.get("global_name").isJsonNull()
                ? account.get("global_name").getAsString() : username;
        String id = account.has("id") ? account.get("id").getAsString() : "?";
        return "§f" + global + "§7 (@§f" + username + "§7) §7[§f" + id + "§7]";
    }

    private static int addRecipient(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        String id = StringArgumentType.getString(ctx, "id").trim();
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        if (!id.matches("\\d{15,}")) {
            ctx.getSource().sendError(Text.literal("§cID must be a Discord snowflake (digits only)."));
            return 0;
        }
        cfg.recipients.removeIf(r -> r.name.equalsIgnoreCase(name));
        cfg.recipients.add(new DiscordConfig.Recipient(name, id));
        cfg.save();
        ctx.getSource().sendFeedback(Text.literal("§aAdded recipient §f" + name + "§7 (" + id + ")"));
        return 1;
    }

    private static int removeRecipient(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        boolean removed = cfg.recipients.removeIf(r -> r.name.equalsIgnoreCase(name));
        if (removed) {
            cfg.save();
            ctx.getSource().sendFeedback(Text.literal("§aRemoved recipient §f" + name));
        } else {
            ctx.getSource().sendError(Text.literal("§cNo recipient named '" + name + "'."));
        }
        return removed ? 1 : 0;
    }

    private static int listRecipients(CommandContext<FabricClientCommandSource> ctx) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        if (cfg.recipients.isEmpty()) {
            ctx.getSource().sendFeedback(Text.literal("§7No recipients configured."));
            return 1;
        }
        ctx.getSource().sendFeedback(Text.literal("§aRecipients:"));
        for (DiscordConfig.Recipient r : cfg.recipients) {
            String mc = (r.mcName != null && !r.mcName.isBlank())
                    ? " §7· MC: §f" + r.mcName
                    : (r.mcUuid != null && !r.mcUuid.isBlank() ? " §7· MC UUID: §f" + r.mcUuid : "");
            ctx.getSource().sendFeedback(Text.literal("§f  " + r.name + "§7 (" + r.id + ")" + mc));
        }
        return 1;
    }

    private static int linkMc(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        String mc = StringArgumentType.getString(ctx, "mcname").trim();
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        DiscordConfig.Recipient rec = null;
        for (DiscordConfig.Recipient r : cfg.recipients) {
            if (r.name.equalsIgnoreCase(name)) {
                rec = r;
            }
        }
        if (rec == null) {
            ctx.getSource().sendError(Text.literal("§cNo recipient named '" + name + "'."));
            return 0;
        }
        if (mc.matches("(?i)[0-9a-f]{32}")) {
            rec.mcUuid = mc.toLowerCase();
            cfg.save();
            ctx.getSource().sendFeedback(Text.literal("§aLinked §f" + name + "§a to Minecraft UUID §f" + rec.mcUuid + "§a."));
            return 1;
        }
        rec.mcName = mc;
        cfg.save();
        resolveMcUuid(rec.id, mc);
        ctx.getSource().sendFeedback(Text.literal("§aLinked §f" + name + "§a to Minecraft §f" + mc + "§a."));
        return 1;
    }

    private static int setReceive(CommandContext<FabricClientCommandSource> ctx, boolean on) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        cfg.receiveInGameAlerts = on;
        cfg.save();
        ctx.getSource().sendFeedback(Text.literal("§aIn-game teammate alerts §f" + (on ? "enabled" : "disabled") + "§a."));
        return 1;
    }

    static void resolveMcUuid(String discordId, String mcName) {
        if (mcName == null || mcName.isBlank()) {
            return;
        }
        new Thread(() -> {
            String uuid = DiscordClient.fetchMinecraftUuid(mcName);
            if (uuid == null) {
                return;
            }
            MinecraftClient.getInstance().execute(() -> {
                DiscordConfig cfg = DiscordControllerMod.getConfig();
                for (DiscordConfig.Recipient r : cfg.recipients) {
                    if (r.id.equals(discordId)) {
                        r.mcUuid = uuid;
                    }
                }
                cfg.save();
            });
        }, "dcontroller-mcuuid").start();
    }

    private static int alertNow(CommandContext<FabricClientCommandSource> ctx) {
        AlertManager.get().sendManualAlert(ctx.getSource().getClient());
        return 1;
    }

    private static int alertTest(CommandContext<FabricClientCommandSource> ctx) {
        AlertManager.get().sendTestAlert(ctx.getSource().getClient());
        return 1;
    }

    private static int setAuto(CommandContext<FabricClientCommandSource> ctx, boolean on) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        cfg.autoAlertEnabled = on;
        cfg.save();
        ctx.getSource().sendFeedback(Text.literal("§aAuto attack-alert §f" + (on ? "enabled" : "disabled") + "§a."));
        return 1;
    }

    private static int setPing(CommandContext<FabricClientCommandSource> ctx, boolean on) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        cfg.alertPingEveryone = on;
        cfg.save();
        ctx.getSource().sendFeedback(Text.literal("§aWebhook §f@everyone§a ping " + (on ? "enabled" : "disabled") + "."));
        return 1;
    }

    private static int addTarget(CommandContext<FabricClientCommandSource> ctx) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        List<String> added = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (String tok : StringArgumentType.getString(ctx, "ids").trim().split("\\s+")) {
            if (tok.isEmpty()) {
                continue;
            }
            String id = resolveId(cfg, tok);
            if (id == null) {
                unknown.add(tok);
            } else if (!cfg.alertTargetIds.contains(id)) {
                cfg.alertTargetIds.add(id);
                added.add(id);
            }
        }
        if (!added.isEmpty()) {
            cfg.save();
        }
        FabricClientCommandSource s = ctx.getSource();
        if (!added.isEmpty()) {
            s.sendFeedback(Text.literal("§aAdded §f" + added.size()
                    + "§a alert target" + (added.size() == 1 ? "" : "s") + ". Now "
                    + cfg.alertTargetIds.size() + " total."));
        }
        if (!unknown.isEmpty()) {
            s.sendError(Text.literal("§cNot a snowflake ID or known recipient: §f" + String.join(", ", unknown)));
        }
        if (added.isEmpty() && unknown.isEmpty()) {
            s.sendFeedback(Text.literal("§7Those targets were already added."));
        }
        return added.isEmpty() ? 0 : 1;
    }

    private static int removeTarget(CommandContext<FabricClientCommandSource> ctx) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        int removed = 0;
        for (String tok : StringArgumentType.getString(ctx, "ids").trim().split("\\s+")) {
            if (tok.isEmpty()) {
                continue;
            }
            String id = resolveId(cfg, tok);
            boolean r = id != null && cfg.alertTargetIds.remove(id);
            r |= cfg.alertTargetIds.remove(tok);
            if (r) {
                removed++;
            }
        }
        if (removed > 0) {
            cfg.save();
            ctx.getSource().sendFeedback(Text.literal("§aRemoved §f" + removed
                    + "§a alert target" + (removed == 1 ? "" : "s") + ". Now "
                    + cfg.alertTargetIds.size() + " total."));
            return 1;
        }
        ctx.getSource().sendError(Text.literal("§cNo matching alert target."));
        return 0;
    }

    private static int listTargets(CommandContext<FabricClientCommandSource> ctx) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        if (cfg.alertTargetIds.isEmpty()) {
            ctx.getSource().sendFeedback(Text.literal("§7No alert targets. Add with §f/dcontroller alert targets add <id>§7."));
            return 1;
        }
        ctx.getSource().sendFeedback(Text.literal("§aAlert targets (DM'd on alert):"));
        for (String id : cfg.alertTargetIds) {
            String name = recipientName(cfg, id);
            ctx.getSource().sendFeedback(Text.literal("§f  " + id + (name != null ? " §7(" + name + ")" : "")));
        }
        return 1;
    }

    private static int setGroup(CommandContext<FabricClientCommandSource> ctx) {
        String id = StringArgumentType.getString(ctx, "id").trim();
        if (!id.matches("\\d{15,}")) {
            ctx.getSource().sendError(Text.literal("§cThat must be a group-DM channel ID (digits). "
                    + "Enable Discord Developer Mode, right-click the group DM → Copy Channel ID."));
            return 0;
        }
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        cfg.alertGroupChannelId = id;
        cfg.save();
        ctx.getSource().sendFeedback(Text.literal("§aGroup-DM set. Alerts post there and §f@everyone§a pings everyone in it."));
        return 1;
    }

    private static int clearGroup(CommandContext<FabricClientCommandSource> ctx) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        cfg.alertGroupChannelId = "";
        cfg.save();
        ctx.getSource().sendFeedback(Text.literal("§aGroup-DM alert cleared."));
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
        String url = StringArgumentType.getString(ctx, "url").trim();
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        if (cfg.alertWebhooks.remove(url)) {
            cfg.save();
            ctx.getSource().sendFeedback(Text.literal("§aWebhook removed."));
            return 1;
        }
        ctx.getSource().sendError(Text.literal("§cNo matching webhook. Use §f/dcontroller alert webhook list§c."));
        return 0;
    }

    private static int listWebhooks(CommandContext<FabricClientCommandSource> ctx) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        if (cfg.alertWebhooks.isEmpty()) {
            ctx.getSource().sendFeedback(Text.literal("§7No webhooks. Add with §f/dcontroller alert webhook add <url>§7."));
            return 1;
        }
        ctx.getSource().sendFeedback(Text.literal("§aWebhooks:"));
        for (String url : cfg.alertWebhooks) {
            ctx.getSource().sendFeedback(Text.literal("§f  " + (url.length() > 50 ? url.substring(0, 50) + "…" : url)));
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

    private static int setTargetEnabled(CommandContext<FabricClientCommandSource> ctx, boolean enabled) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        int changed = 0;
        for (String tok : StringArgumentType.getString(ctx, "ids").trim().split("\\s+")) {
            if (tok.isEmpty()) {
                continue;
            }
            String id = resolveId(cfg, tok);
            if (id == null || !cfg.alertTargetIds.contains(id)) {
                continue;
            }
            if (enabled) {
                if (cfg.disabledTargetIds.remove(id)) {
                    changed++;
                }
            } else if (!cfg.disabledTargetIds.contains(id)) {
                cfg.disabledTargetIds.add(id);
                changed++;
            }
        }
        if (changed > 0) {
            cfg.save();
        }
        ctx.getSource().sendFeedback(Text.literal("§a" + (enabled ? "Enabled " : "Muted ") + changed + " target(s)."));
        return changed > 0 ? 1 : 0;
    }

    private static int setManualCooldown(CommandContext<FabricClientCommandSource> ctx) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        cfg.alertManualCooldownSeconds = IntegerArgumentType.getInteger(ctx, "seconds");
        cfg.save();
        ctx.getSource().sendFeedback(Text.literal("§aManual/help cooldown: §f" + cfg.alertManualCooldownSeconds + "§as."));
        return 1;
    }

    private static int setLowHealth(CommandContext<FabricClientCommandSource> ctx, boolean on) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        cfg.alertLowHealthEnabled = on;
        cfg.save();
        ctx.getSource().sendFeedback(Text.literal("§aLow-health emergency alert §f" + (on ? "enabled" : "disabled") + "§a."));
        return 1;
    }

    private static int setDeath(CommandContext<FabricClientCommandSource> ctx, boolean on) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        cfg.alertOnDeath = on;
        cfg.save();
        ctx.getSource().sendFeedback(Text.literal("§aDeath alert §f" + (on ? "enabled" : "disabled") + "§a."));
        return 1;
    }

    private static int setSound(CommandContext<FabricClientCommandSource> ctx, boolean on) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        cfg.alertSound = on;
        cfg.save();
        ctx.getSource().sendFeedback(Text.literal("§aAlert sound §f" + (on ? "enabled" : "disabled") + "§a."));
        return 1;
    }

    private static int setLowHealthValue(CommandContext<FabricClientCommandSource> ctx) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        double hearts = DoubleArgumentType.getDouble(ctx, "hearts");
        cfg.alertLowHealthHp = hearts * 2.0;
        cfg.save();
        ctx.getSource().sendFeedback(Text.literal("§aEmergency fires at §f" + hearts + "§a hearts."));
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
        double hearts = DoubleArgumentType.getDouble(ctx, "hearts");
        cfg.alertHealthLoss = hearts * 2.0;
        cfg.save();
        ctx.getSource().sendFeedback(Text.literal("§aAuto-alert needs §f" + hearts + "§a hearts lost in the window."));
        return 1;
    }

    private static int setRadius(CommandContext<FabricClientCommandSource> ctx) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        cfg.attackRadius = DoubleArgumentType.getDouble(ctx, "blocks");
        cfg.save();
        ctx.getSource().sendFeedback(Text.literal("§aAttacker detection radius: §f" + cfg.attackRadius + "§a blocks."));
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
        ctx.getSource().sendFeedback(Text.literal("§aAuto-alert cooldown: §f" + cfg.alertCooldownSeconds + "§as."));
        return 1;
    }

    private static int showAlertConfig(CommandContext<FabricClientCommandSource> ctx) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        FabricClientCommandSource s = ctx.getSource();
        s.sendFeedback(Text.literal("§6=== Alert settings ==="));
        s.sendFeedback(Text.literal("§7Auto attack-alert: " + (cfg.autoAlertEnabled ? "§aON" : "§cOFF")));
        s.sendFeedback(Text.literal("§7Trigger: §f> " + cfg.alertHitThreshold + " hits §7and §f≥ "
                + (cfg.alertHealthLoss / 2.0) + " hearts §7in §f" + cfg.alertWindowSeconds + "s"));
        s.sendFeedback(Text.literal("§7Attacker radius: §f" + cfg.attackRadius + " §7blocks · cooldown §f"
                + cfg.alertCooldownSeconds + "s"));
        s.sendFeedback(Text.literal("§7DM targets: §f" + cfg.alertTargetIds.size()
                + " §7(" + cfg.disabledTargetIds.size() + " muted) · group-DM: "
                + (cfg.alertGroupChannelId.isBlank() ? "§cnone" : "§aset")
                + " §7· webhooks: §f" + cfg.alertWebhooks.size()
                + " §7· @everyone: " + (cfg.alertPingEveryone ? "§aon" : "§coff")));
        s.sendFeedback(Text.literal("§7Low-health: " + (cfg.alertLowHealthEnabled ? "§aon" : "§coff")
                + " §7(" + (cfg.alertLowHealthHp / 2.0) + "♥) · death: " + (cfg.alertOnDeath ? "§aon" : "§coff")
                + " §7· sound: " + (cfg.alertSound ? "§aon" : "§coff")));
        return 1;
    }

    private static final Gson CLAN_GSON = new Gson();

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
        String text = StringArgumentType.getString(ctx, "text").trim();
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "📣 " + (cfg.clanName.isBlank() ? "Clan" : cfg.clanName) + " announcement");
        embed.addProperty("description", text);
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
        String notes = optString(ctx, "notes", "");
        String our = cfg.clanName.isBlank() ? "Our clan" : cfg.clanName;
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "⚔ CLAN WAR");
        embed.addProperty("description", "**" + our + "**  vs  **" + enemy + "**");
        embed.addProperty("color", 0xE74C3C);
        JsonArray fields = new JsonArray();
        fields.add(clanField("Enemy", "`" + enemy + "`", true));
        fields.add(clanField("When", "`" + time + "`", true));
        if (!notes.isBlank()) {
            fields.add(clanField("Details", notes, false));
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
        String json = CLAN_GSON.toJson(root);
        String url = cfg.clanWebhookUrl;
        FabricClientCommandSource source = ctx.getSource();
        source.sendFeedback(Text.literal("§7Posting to clan webhook..."));
        new Thread(() -> {
            boolean ok = DiscordClient.sendWebhook(url, json);
            source.getClient().execute(() -> {
                if (ok) {
                    source.sendFeedback(Text.literal("§a" + okMsg));
                } else {
                    source.sendError(Text.literal("§cFailed to post (check the webhook URL)."));
                }
            });
        }, "dcontroller-clan").start();
    }

    private static JsonObject clanField(String name, String value, boolean inline) {
        JsonObject f = new JsonObject();
        f.addProperty("name", name);
        f.addProperty("value", value);
        f.addProperty("inline", inline);
        return f;
    }

    private static String optString(CommandContext<FabricClientCommandSource> ctx, String name, String def) {
        try {
            return StringArgumentType.getString(ctx, name);
        } catch (Exception e) {
            return def;
        }
    }

    private static String resolveId(DiscordConfig cfg, String arg) {
        if (arg.matches("\\d{15,}")) {
            return arg;
        }
        return cfg.findRecipientId(arg);
    }

    private static String recipientName(DiscordConfig cfg, String id) {
        for (DiscordConfig.Recipient r : cfg.recipients) {
            if (r.id.equals(id)) {
                return r.name;
            }
        }
        return null;
    }

    private static CompletableFuture<Suggestions> suggestRecipientNames(
            CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        Set<String> names = new LinkedHashSet<>();
        for (DiscordConfig.Recipient r : cfg.recipients) {
            names.add(r.name);
        }
        return suggestTokens(builder, names);
    }

    private static CompletableFuture<Suggestions> suggestTargets(
            CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        Set<String> opts = new LinkedHashSet<>();
        for (String id : cfg.alertTargetIds) {
            opts.add(id);
            String name = recipientName(cfg, id);
            if (name != null) {
                opts.add(name);
            }
        }
        return suggestTokens(builder, opts);
    }

    private static CompletableFuture<Suggestions> suggestValues(SuggestionsBuilder builder, String... values) {
        return suggestTokens(builder, new LinkedHashSet<>(List.of(values)));
    }

    private static CompletableFuture<Suggestions> suggestTokens(SuggestionsBuilder builder, Collection<String> options) {
        String remaining = builder.getRemaining();
        int lastSpace = remaining.lastIndexOf(' ');
        SuggestionsBuilder b = builder;
        String token = remaining;
        if (lastSpace >= 0) {
            b = builder.createOffset(builder.getStart() + lastSpace + 1);
            token = remaining.substring(lastSpace + 1);
        }
        String tl = token.toLowerCase();
        for (String o : options) {
            if (o.toLowerCase().startsWith(tl)) {
                b.suggest(o);
            }
        }
        return b.buildFuture();
    }

    private static String trimNum(double d) {
        return (d == Math.floor(d)) ? String.valueOf((int) d) : String.valueOf(d);
    }
}
