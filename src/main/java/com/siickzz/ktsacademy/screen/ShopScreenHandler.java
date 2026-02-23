package com.siickzz.ktsacademy.screen;

import com.siickzz.ktsacademy.shop.ShopItem;
import com.siickzz.ktsacademy.shop.ShopRegistry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Locale;

public class ShopScreenHandler extends ScreenHandler {
	public static final int ROWS = 6;
	public static final int SHOP_SIZE = ROWS * 9;

	private final Inventory shopInventory;
	private final PlayerInventory playerInventory;
	private final ShopItem[] slotItems = new ShopItem[SHOP_SIZE];

	public ShopScreenHandler(int syncId, PlayerInventory playerInventory) {
		super(ModScreenHandlers.SHOP, syncId);
		this.playerInventory = playerInventory;
		this.shopInventory = new SimpleInventory(SHOP_SIZE);

		// Shop slots
		for (int row = 0; row < ROWS; row++) {
			for (int col = 0; col < 9; col++) {
				int index = col + row * 9;
				this.addSlot(new ShopSlot(this.shopInventory, index, 8 + col * 18, 18 + row * 18));
			}
		}

		// Player inventory
		int y = 18 + ROWS * 18 + 14;
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, y + row * 18));
			}
		}

		// Hotbar
		int hotbarY = y + 58;
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(playerInventory, col, 8 + col * 18, hotbarY));
		}

		if (!playerInventory.player.getWorld().isClient) {
			populateShop();
		}
	}

	private void populateShop() {
		int slot = 0;
		for (ShopItem item : ShopRegistry.allItemsSorted()) {
			if (slot >= SHOP_SIZE) {
				break;
			}

			Item resolved = item.item();
			if (resolved == Items.AIR) {
				continue;
			}

			this.slotItems[slot] = item;
			ItemStack display = new ItemStack(resolved);
			// 1.21+ uses item components; setting a custom name is optional.
			display.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§b" + item.displayName()));
			this.shopInventory.setStack(slot, display);
			slot++;
		}
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slot) {
		// Disable shift-click transfers to avoid weird interactions.
		return ItemStack.EMPTY;
	}

	@Override
	public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
		if (player.getWorld().isClient) {
			super.onSlotClick(slotIndex, button, actionType, player);
			return;
		}

		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			super.onSlotClick(slotIndex, button, actionType, player);
			return;
		}

		// Shop slots are [0..SHOP_SIZE)
		if (slotIndex >= 0 && slotIndex < SHOP_SIZE && actionType == SlotActionType.PICKUP) {
			Slot slot = this.getSlot(slotIndex);
			if (!slot.hasStack()) {
				return;
			}
			ShopItem shopItem = (slotIndex >= 0 && slotIndex < SHOP_SIZE) ? this.slotItems[slotIndex] : null;
			if (shopItem == null) {
				return;
			}

			// button: 0 = left click, 1 = right click
			if (button == 0) {
				ShopScreens.openConfirm(serverPlayer, ShopScreens.ShopAction.BUY, shopItem);
				return;
			}
			if (button == 1) {
				ShopScreens.openConfirm(serverPlayer, ShopScreens.ShopAction.SELL, shopItem);
				return;
			}
			return;
		}

		super.onSlotClick(slotIndex, button, actionType, player);
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return true;
	}

	private static final class ShopSlot extends Slot {
		private ShopSlot(Inventory inventory, int index, int x, int y) {
			super(inventory, index, x, y);
		}

		@Override
		public boolean canInsert(ItemStack stack) {
			return false;
		}

		@Override
		public boolean canTakeItems(PlayerEntity playerEntity) {
			return false;
		}
	}

	private static String formatMoney(double value) {
		if (value == (long) value) {
			return Long.toString((long) value);
		}
		return String.format(Locale.ROOT, "%.2f", value);
	}
}
