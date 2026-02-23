package com.siickzz.ktsacademy.mixin;

import com.siickzz.ktsacademy.events.HarvestListener;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityPickupMixin {
	@Unique
	private static final Logger COBBLE_ECONOMY_LOGGER = LoggerFactory.getLogger("CobbleEconomy");
	@Unique
	private static final java.util.Map<java.util.UUID, Long> COBBLE_ECONOMY_LAST_PICKUP_LOG = new java.util.concurrent.ConcurrentHashMap<>();
	@Unique
	private static final long COBBLE_ECONOMY_PICKUP_LOG_MS = 2000L;

	@Unique
	private int cobbleEconomy$preInvCount = -1;
	@Unique
	private String cobbleEconomy$prePickupItemId = null;
	@Unique
	private Item cobbleEconomy$prePickupItem = null;

	@Inject(method = "onPlayerCollision", at = @At("HEAD"))
	private void cobbleEconomy$onPlayerCollisionHead(PlayerEntity player, CallbackInfo ci) {
		ItemEntity self = (ItemEntity) (Object) this;
		ItemStack stack = self.getStack();
		if (stack == null || stack.isEmpty()) {
			cobbleEconomy$preInvCount = -1;
			cobbleEconomy$prePickupItemId = null;
			cobbleEconomy$prePickupItem = null;
			return;
		}
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			cobbleEconomy$preInvCount = -1;
			cobbleEconomy$prePickupItemId = null;
			cobbleEconomy$prePickupItem = null;
			return;
		}
		Identifier id = Registries.ITEM.getId(stack.getItem());
		cobbleEconomy$prePickupItem = stack.getItem();
		cobbleEconomy$prePickupItemId = id == null ? null : id.toString();
		cobbleEconomy$preInvCount = countItemInInventory(serverPlayer, cobbleEconomy$prePickupItem);
	}

	@Inject(method = "onPlayerCollision", at = @At("TAIL"))
	private void cobbleEconomy$onPlayerCollisionTail(PlayerEntity player, CallbackInfo ci) {
		if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
		if (cobbleEconomy$preInvCount < 0 || cobbleEconomy$prePickupItemId == null || cobbleEconomy$prePickupItem == null) return;

		int afterInv = countItemInInventory(serverPlayer, cobbleEconomy$prePickupItem);
		int delta = afterInv - cobbleEconomy$preInvCount;
		if (delta <= 0) return;

		String itemId = cobbleEconomy$prePickupItemId;
		String lower = itemId.toLowerCase(java.util.Locale.ROOT);
		String path = lower;
		int idx = lower.indexOf(':');
		if (idx >= 0 && idx + 1 < lower.length()) {
			path = lower.substring(idx + 1);
		}

		// Count only actual apricorn/noigrume items (all colors), not seeds/saplings.
		boolean looksLikeApricorn = path.endsWith("apricorn") || path.contains("_apricorn");
		if (!looksLikeApricorn) return;
		if (path.contains("seed") || path.contains("sapling")) return;
		if (HarvestListener.shouldSkipApricornPickup(serverPlayer)) return;

		// Log occasionally so we can confirm the hook is active on the server.
		long now = System.currentTimeMillis();
		java.util.UUID uuid = serverPlayer.getUuid();
		Long last = COBBLE_ECONOMY_LAST_PICKUP_LOG.get(uuid);
		if (last == null || now - last > COBBLE_ECONOMY_PICKUP_LOG_MS) {
			COBBLE_ECONOMY_LAST_PICKUP_LOG.put(uuid, now);
			COBBLE_ECONOMY_LOGGER.info("[CobbleEconomy] Apricorn pickup detected: player={} item={} amount={}", serverPlayer.getName().getString(), itemId, delta);
		}

		// Quest progression is credited via the harvest window started on right-click.
		// We keep this mixin only for occasional logging/diagnostics.
	}

	@Unique
	private static int countItemInInventory(ServerPlayerEntity player, Item item) {
		if (player == null || item == null) return 0;
		int total = 0;
		var inv = player.getInventory();
		for (int i = 0; i < inv.size(); i++) {
			ItemStack st = inv.getStack(i);
			if (st == null || st.isEmpty()) continue;
			if (st.getItem() != item) continue;
			total += st.getCount();
		}
		return total;
	}
}
