package com.siickzz.economy.mixin;

import com.siickzz.economy.events.HarvestListener;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cobblemon distributes its own ApricornBlock. In some server stacks, intercepting vanilla
 * interaction layers is unreliable; this mixin guarantees we see the right-click.
 */
@Mixin(targets = "com.cobblemon.mod.common.block.ApricornBlock")
public abstract class CobblemonApricornBlockUseMixin
{
	@Inject(method = "onUse", at = @At("HEAD"), require = 0)
	private void cobbleEconomy$onUseHead(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
		if (world == null || world.isClient)
            return;
		if (!(player instanceof ServerPlayerEntity serverPlayer))
            return;
		HarvestListener.onApricornBlockInteracted(serverPlayer);
	}
}
