package com.streetfighter.mixin;

import com.streetfighter.server.PickaxeFights;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stone pickaxes and iron swords in a crafting result slot come alive and fight you first. */
@Mixin(Slot.class)
public abstract class SlotMixin {
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void streetfighter$pickaxeChallenge(Player player, CallbackInfoReturnable<Boolean> cir) {
        Slot self = (Slot) (Object) this;
        if (self instanceof ResultSlot && (self.getItem().is(Items.STONE_PICKAXE) || self.getItem().is(Items.IRON_SWORD))
                && PickaxeFights.blocksPickup(player, self.getItem().is(Items.IRON_SWORD))) {
            cir.setReturnValue(false);
        }
    }
}
