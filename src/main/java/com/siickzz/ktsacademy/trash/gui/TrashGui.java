package com.siickzz.ktsacademy.trash.gui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class TrashGui {
	private static final int ROWS = 6;
	private static final Text TITLE = Text.literal("Poubelle");

	private TrashGui() {
	}

	public static void open(ServerPlayerEntity player) {
		SimpleInventory inventory = new SimpleInventory(ROWS * 9);

		player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInv, playerEntity) ->
			new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, playerInv, inventory, ROWS) {
				@Override
				public void onClosed(PlayerEntity player) {
					super.onClosed(player);
					for (int i = 0; i < inventory.size(); i++) {
						inventory.setStack(i, ItemStack.EMPTY);
					}
				}
			}, TITLE));
	}
}
