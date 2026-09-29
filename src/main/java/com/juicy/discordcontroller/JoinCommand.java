package com.juicy.discordcontroller;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class JoinCommand {

    private JoinCommand() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("djoin")
                .then(argument("address", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String address = StringArgumentType.getString(ctx, "address").trim();
                            MinecraftClient client = ctx.getSource().getClient();
                            client.execute(() -> connect(client, address));
                            return 1;
                        })));
    }

    public static void connect(MinecraftClient client, String address) {
        try {
            ServerAddress addr = ServerAddress.parse(address);
            ServerInfo info = new ServerInfo("Discord Alert", address, ServerInfo.ServerType.OTHER);
            CookieStorage cookies = newCookieStorage();
            ConnectScreen.connect(client.currentScreen, client, addr, info, false, cookies);
        } catch (Exception e) {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§cCould not join " + address), false);
            }
        }
    }

    private static CookieStorage newCookieStorage() {
        try {
            return (CookieStorage) CookieStorage.class.getConstructor(Map.class).newInstance(Map.of());
        } catch (Throwable ignored) {
            try {
                return (CookieStorage) CookieStorage.class
                        .getConstructor(Map.class, Map.class, boolean.class)
                        .newInstance(Map.of(), Map.of(), false);
            } catch (Throwable ignored2) {
                return null;
            }
        }
    }
}
