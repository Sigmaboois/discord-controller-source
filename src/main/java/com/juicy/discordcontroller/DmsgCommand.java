package com.juicy.discordcontroller;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import java.util.concurrent.CompletableFuture;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class DmsgCommand {

    private DmsgCommand() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("dmsg")
                .then(argument("recipient", StringArgumentType.word())
                        .suggests(DmsgCommand::suggestRecipients)
                        .then(argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String recipient = StringArgumentType.getString(ctx, "recipient");
                                    String message = StringArgumentType.getString(ctx, "message");
                                    return run(ctx.getSource(), recipient, message);
                                }))));
    }

    private static CompletableFuture<Suggestions> suggestRecipients(
            com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> ctx,
            SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        DiscordConfig cfg = DiscordControllerMod.getConfig();
        for (DiscordConfig.Recipient r : cfg.recipients) {
            if (r.name.toLowerCase().startsWith(remaining)) {
                builder.suggest(r.name);
            }
        }
        return builder.buildFuture();
    }

    private static int run(FabricClientCommandSource source, String recipientName, String message) {
        DiscordConfig cfg = DiscordControllerMod.getConfig();

        if (cfg.token.isEmpty()) {
            source.sendError(Text.literal("§cNo bot token configured. Use §f/dcontroller token <bot token>§c."));
            return 0;
        }

        String recipientId = cfg.findRecipientId(recipientName);
        if (recipientId == null) {
            source.sendError(Text.literal("§cUnknown recipient '" + recipientName
                    + "'. Add them via §f/dcontroller recipients add <name> <id>§c."));
            return 0;
        }

        String content = "### 📨 Message via " + cfg.brand + "\n"
                + message + "\n"
                + "-# ⛏️ Sent by the " + cfg.footer + " | " + cfg.brand;

        new Thread(() -> {
            String channelId = DiscordClient.openDmChannel(cfg.token, recipientId);
            boolean ok = channelId != null && DiscordClient.sendMessage(cfg.token, channelId, content);
            // Hop back onto the client thread to post feedback safely.
            source.getClient().execute(() -> {
                if (ok) {
                    source.sendFeedback(Text.literal("§aDM sent to §f" + recipientName + "§a."));
                } else {
                    source.sendError(Text.literal("§cFailed to send DM (bad token or blocked recipient)."));
                }
            });
        }, "dcontroller-dmsg").start();

        return 1;
    }
}
