package com.streetfighter.mixin;

import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(QuadrupedModel.class)
public interface QuadrupedModelAccessor {
    @Accessor("head")
    ModelPart streetfighter$head();

    @Accessor("body")
    ModelPart streetfighter$body();

    @Accessor("rightHindLeg")
    ModelPart streetfighter$rightHindLeg();

    @Accessor("leftHindLeg")
    ModelPart streetfighter$leftHindLeg();

    @Accessor("rightFrontLeg")
    ModelPart streetfighter$rightFrontLeg();

    @Accessor("leftFrontLeg")
    ModelPart streetfighter$leftFrontLeg();
}
