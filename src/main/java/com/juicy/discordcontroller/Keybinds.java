package com.juicy.discordcontroller;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;

public final class Keybinds {

    private static KeyBinding answerKey;
    private static KeyBinding alertKey;
    private static KeyBinding menuKey;
    private static KeyBinding armKey;

    private Keybinds() {
    }

    public static void register() {
        answerKey = makeKeyBinding("key.discordcontroller.answer");
        alertKey = makeKeyBinding("key.discordcontroller.help_alert");
        menuKey = makeKeyBinding("key.discordcontroller.menu");
        armKey = makeKeyBinding("key.discordcontroller.toggle_auto");
    }

    private static KeyBinding makeKeyBinding(String translationKey) {
        try {
            Class<?> catClass = Class.forName("net.minecraft.client.option.KeyBinding$Category");
            Method create = catClass.getMethod("create", Identifier.class);
            Object category = create.invoke(null, Identifier.of("discordcontroller", "controls"));
            return KeyBindingHelper.registerKeyBinding(
                    (KeyBinding) KeyBinding.class
                            .getConstructor(String.class, InputUtil.Type.class, int.class, catClass)
                            .newInstance(translationKey, InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        } catch (Throwable ignored) {
            try {
                return KeyBindingHelper.registerKeyBinding(
                        (KeyBinding) KeyBinding.class
                                .getConstructor(String.class, InputUtil.Type.class, int.class, String.class)
                                .newInstance(translationKey, InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN,
                                        "key.category.discordcontroller.controls"));
            } catch (Throwable ignored2) {
                return null;
            }
        }
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
