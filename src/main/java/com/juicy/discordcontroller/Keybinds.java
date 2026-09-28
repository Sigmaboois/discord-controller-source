package com.juicy.discordcontroller;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class Keybinds {

    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("discordcontroller", "controls"));

    private static KeyBinding answerKey;
    private static KeyBinding alertKey;
    private static KeyBinding menuKey;
    private static KeyBinding armKey;

    private Keybinds() {
    }

    public static void register() {
        answerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.discordcontroller.answer",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                CATEGORY));

        alertKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.discordcontroller.help_alert",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                CATEGORY));

        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.discordcontroller.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                CATEGORY));

        armKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.discordcontroller.toggle_auto",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                CATEGORY));
    }

    public static void onEndTick(MinecraftClient client) {
        if (menuKey != null) {
            while (menuKey.wasPressed()) {
                client.setScreen(new ControllerScreen());
            }
        }
        if (armKey != null) {
            while (armKey.wasPressed()) {
                DiscordConfig cfg = DiscordControllerMod.getConfig();
                cfg.autoAlertEnabled = !cfg.autoAlertEnabled;
                cfg.save();
                if (client.player != null) {
                    client.player.sendMessage(net.minecraft.text.Text.literal(cfg.autoAlertEnabled
                            ? "§aAuto attack-alert ARMED."
                            : "§cAuto attack-alert OFF."), true);
                }
            }
        }
        if (answerKey != null) {
            while (answerKey.wasPressed()) {
                boolean ok = AnswerCommand.pressAnswerHotkey(DiscordControllerMod.getConfig());
                if (client.player != null) {
                    client.player.sendMessage(net.minecraft.text.Text.literal(ok
                            ? "§aAnswer hotkey sent."
                            : "§cFailed to send answer hotkey."), true);
                }
            }
        }
        if (alertKey != null) {
            while (alertKey.wasPressed()) {
                AlertManager.get().sendManualAlert(client);
            }
        }
    }
}
