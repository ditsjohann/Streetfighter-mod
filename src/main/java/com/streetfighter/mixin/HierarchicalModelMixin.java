package com.streetfighter.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.streetfighter.client.world.FightAnimator;
import net.minecraft.client.model.HierarchicalModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Villagers, golems and other hierarchical models: same hook as {@link AgeableListModelMixin}. */
@Mixin(HierarchicalModel.class)
public abstract class HierarchicalModelMixin {
    @Inject(method = "renderToBuffer", at = @At("HEAD"))
    private void streetfighter$pose(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color,
                                   CallbackInfo ci) {
        FightAnimator.applyModelPose(this);
    }
}
