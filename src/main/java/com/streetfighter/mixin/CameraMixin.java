package com.streetfighter.mixin;

import com.streetfighter.client.world.FightCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Puts the real game camera at the fight's cinematic side view while a street fight is running. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void streetfighter$fightCamera(BlockGetter level, Entity entity, boolean detachedIn, boolean mirror,
                                          float partialTick, CallbackInfo ci) {
        if (!FightCamera.active) return;
        this.detached = true; // so the player's own body is rendered
        this.setRotation(FightCamera.yaw, FightCamera.pitch);
        this.setPosition(FightCamera.x, FightCamera.y, FightCamera.z);
    }
}
