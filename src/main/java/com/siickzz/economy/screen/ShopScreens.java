package com.siickzz.economy.screen;

import com.siickzz.economy.shop.ShopItem;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class ShopScreens {
	private ShopScreens() {
	}

	public static void openShop(ServerPlayerEntity player) {
		player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
			(syncId, inv, p) -> new ShopScreenHandler(syncId, inv),
			Text.literal("Shop")
		));
	}

	public static void openConfirm(ServerPlayerEntity player, ShopAction action, ShopItem item) {
		player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
			(syncId, inv, p) -> new ConfirmScreenHandler(syncId, inv, action, item),
			Text.literal(action == ShopAction.BUY ? "Confirmer l'achat" : "Confirmer la vente")
		));
	}

	public enum ShopAction {
		BUY,
		SELL
	}
}
