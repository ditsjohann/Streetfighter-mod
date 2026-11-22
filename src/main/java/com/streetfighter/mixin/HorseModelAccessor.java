package com.streetfighter.mixin;

import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HorseModel.class)
public interface HorseModelAccessor {
    @Accessor("headParts")
    ModelPart streetfighter$headParts();

    @Accessor("rightHindLeg")
    ModelPart streetfighter$rightHindLeg();

    @Accessor("leftHindLeg")
    ModelPart streetfighter$leftHindLeg();

    @Accessor("rightFrontLeg")
    ModelPart streetfighter$rightFrontLeg();

    @Accessor("leftFrontLeg")
    ModelPart streetfighter$leftFrontLeg();
}
