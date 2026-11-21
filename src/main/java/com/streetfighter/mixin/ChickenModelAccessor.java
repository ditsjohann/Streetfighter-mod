package com.streetfighter.mixin;

import net.minecraft.client.model.ChickenModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChickenModel.class)
public interface ChickenModelAccessor {
    @Accessor("head")
    ModelPart streetfighter$head();

    @Accessor("beak")
    ModelPart streetfighter$beak();

    @Accessor("redThing")
    ModelPart streetfighter$redThing();

    @Accessor("rightLeg")
    ModelPart streetfighter$rightLeg();

    @Accessor("leftLeg")
    ModelPart streetfighter$leftLeg();

    @Accessor("rightWing")
    ModelPart streetfighter$rightWing();

    @Accessor("leftWing")
    ModelPart streetfighter$leftWing();
}
