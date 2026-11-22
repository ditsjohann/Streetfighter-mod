package com.streetfighter.mixin;

import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CreeperModel.class)
public interface CreeperModelAccessor {
    @Accessor("head")
    ModelPart streetfighter$head();

    @Accessor("rightHindLeg")
    ModelPart streetfighter$rightHindLeg();

    @Accessor("leftHindLeg")
    ModelPart streetfighter$leftHindLeg();

    @Accessor("rightFrontLeg")
    ModelPart streetfighter$rightFrontLeg();

    @Accessor("leftFrontLeg")
    ModelPart streetfighter$leftFrontLeg();
}
