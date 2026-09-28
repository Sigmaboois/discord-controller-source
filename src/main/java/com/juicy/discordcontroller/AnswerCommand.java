package com.juicy.discordcontroller;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import java.awt.Robot;
import java.awt.event.KeyEvent;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class AnswerCommand {

    private AnswerCommand() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("answer")
                .executes(ctx -> {
                    DiscordConfig cfg = DiscordControllerMod.getConfig();
                    boolean ok = pressAnswerHotkey(cfg);
                    ctx.getSource().sendFeedback(ok
                            ? Text.literal("§aAnswer hotkey sent. (Discord's Answer Call keybind must be set to Global)")
                            : Text.literal("§cFailed to send answer hotkey."));
                    return ok ? 1 : 0;
                }));
    }

    static boolean pressAnswerHotkey(DiscordConfig cfg) {
        try {
            Robot robot = new Robot();
            if (cfg.answerCtrl) {
                robot.keyPress(KeyEvent.VK_CONTROL);
            }
            if (cfg.answerShift) {
                robot.keyPress(KeyEvent.VK_SHIFT);
            }
            if (cfg.answerAlt) {
                robot.keyPress(KeyEvent.VK_ALT);
            }
            if (cfg.answerVk != 0) {
                robot.keyPress(cfg.answerVk);
                robot.keyRelease(cfg.answerVk);
            }
            if (cfg.answerAlt) {
                robot.keyRelease(KeyEvent.VK_ALT);
            }
            if (cfg.answerShift) {
                robot.keyRelease(KeyEvent.VK_SHIFT);
            }
            if (cfg.answerCtrl) {
                robot.keyRelease(KeyEvent.VK_CONTROL);
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
