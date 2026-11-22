package dev.vanta.autobuilder.mixin;

import dev.vanta.autobuilder.AutoBuilderMod;
import dev.vanta.autobuilder.movement.MovementController;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Overrides vanilla WASD/jump AFTER KeyboardInput.tick() populates
 * the Input fields — so the bot's intent wins this frame.
 * Descriptor (ZF)V = tick(boolean slowDown, float movementMultiplier)
 */
@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {

    @Inject(method = "tick(ZF)V", at = @At("TAIL"))
    private void injectBotMovement(boolean slowDown, float mult, CallbackInfo ci) {
        if (!AutoBuilderMod.BUILDER.isActive()) return;

        MovementController m = AutoBuilderMod.BUILDER.getMovement();
        Input self = (Input)(Object)this;
        self.movementForward  = m.forward;
        self.movementSideways = m.sideways;
        self.jumping          = m.jump;
        self.sneaking         = false;
    }
}
