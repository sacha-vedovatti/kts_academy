package com.siickzz.ktsacademy.mixin;

import com.siickzz.ktsacademy.mystery.MysteryChestManager;
import com.siickzz.ktsacademy.mystery.MysteryChestState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class BlockItemMixin {

    @Inject(method = "place", at = @At("RETURN"))
    private void ktsacademy$afterPlace(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir)
    {
        if (context == null || !cir.getReturnValue().isAccepted())
            return;
        if (!(context.getWorld() instanceof ServerWorld serverWorld))
            return;

        String crateId = MysteryChestManager.crateIdFromStack(context.getStack());
        if (crateId == null)
            return;

        MysteryChestState.get(serverWorld).set(context.getBlockPos(), crateId);
    }
}

