package com.juicy.discordcontroller.mixin;

import com.juicy.discordcontroller.ControllerScreen;
import net.minecraft.client.gui.Click;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ControllerScreen.class)
public abstract class ControllerScreenMouseMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void discordcontroller$onMouseClicked(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        ControllerScreen self = (ControllerScreen) (Object) this;
        if (self.blocksClicks()) {
            cir.setReturnValue(true);
            return;
        }
        if (click.button() == 0 && self.clickAt(click.x(), click.y(), click.button())) {
            cir.setReturnValue(true);
        }
    }
}
