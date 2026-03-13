package com.siickzz.ktsacademy.mixin;

import com.siickzz.ktsacademy.events.HarvestListener;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Locale;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class ServerPlayerInteractionManagerApricornMixin {
	@Inject(method = "interactBlock", at = @At("HEAD"))
	private void cobbleEconomy$beforeInteractBlock(ServerPlayerEntity player, World world, ItemStack stack, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<?> cir)
	{
		if (world == null || world.isClient)
			return;
		if (player == null || hitResult == null)
			return;

		BlockState state = world.getBlockState(hitResult.getBlockPos());
		if (!looksLikeApricornBlock(state))
			return;
		HarvestListener.onApricornBlockInteracted(player);
	}

	@Unique
	private static boolean looksLikeApricornBlock(BlockState state)
	{
		if (state == null)
			return false;

		Identifier id = Registries.BLOCK.getId(state.getBlock());
		if (id == null)
			return false;

		String path = id.getPath().toLowerCase(Locale.ROOT);
		return path.contains("apricorn") || path.contains("noigrume");
	}
}
