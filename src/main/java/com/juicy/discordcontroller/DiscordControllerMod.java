package com.juicy.discordcontroller;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class DiscordControllerMod implements ClientModInitializer {

    private static DiscordConfig config;

    @Override
    public void onInitializeClient() {
        config = DiscordConfig.load();

        Keybinds.register();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            DmsgCommand.register(dispatcher);
            AnswerCommand.register(dispatcher);
            ControllerCommand.register(dispatcher);
            JoinCommand.register(dispatcher);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Keybinds.onEndTick(client);
            AlertManager.get().onClientTick(client);
            IncomingAlerts.get().onClientTick(client);
            SelfLink.get().onClientTick(client);
        });

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> HudOverlay.render(ctx));
    }

    public static DiscordConfig getConfig() {
        return config;
    }
}
