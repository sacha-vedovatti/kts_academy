package com.siickzz.ktsacademy.screen;

import com.siickzz.ktsacademy.economy.EconomyManager;
import com.siickzz.ktsacademy.shop.ShopItem;
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

public class ConfirmScreenHandler extends ScreenHandler {
	private static final int ROWS = 1;
	private static final int SIZE = ROWS * 9;

	private final Inventory inventory;
	private final PlayerInventory playerInventory;
	private final ShopScreens.ShopAction action;
	private final ShopItem item;

	public ConfirmScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, ShopScreens.ShopAction.BUY, null);
	}

	public ConfirmScreenHandler(int syncId, PlayerInventory playerInventory, ShopScreens.ShopAction action, ShopItem item) {
		super(ModScreenHandlers.CONFIRM, syncId);
		this.playerInventory = playerInventory;
		this.action = action;
		this.item = item;
		this.inventory = new SimpleInventory(SIZE);

		for (int col = 0; col < 9; col++) {
			this.addSlot(new ReadonlySlot(this.inventory, col, 8 + col * 18, 18));
		}

		// Player inventory
		int y = 18 + ROWS * 18 + 14;
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, y + row * 18));
			}
		}
		int hotbarY = y + 58;
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(playerInventory, col, 8 + col * 18, hotbarY));
		}

		if (!playerInventory.player.getWorld().isClient) {
			populate();
		}
	}

	private void populate() {
		// Layout: [3]=Cancel, [4]=Item, [5]=Confirm
		ItemStack cancel = new ItemStack(Items.RED_WOOL);
		cancel.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§cAnnuler"));
		this.inventory.setStack(3, cancel);

		ItemStack center;
		if (item != null && item.item() != Items.AIR) {
			center = new ItemStack(item.item());
			String verb = action == ShopScreens.ShopAction.BUY ? "Acheter" : "Vendre";
			double price = action == ShopScreens.ShopAction.BUY ? item.buyPrice() : item.sellPrice();
			center.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§b" + verb + " §f" + item.displayName() + " §7(" + formatMoney(price) + " ₽)"));
		} else {
			center = new ItemStack(Items.BARRIER);
			center.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§cItem invalide"));
		}
		this.inventory.setStack(4, center);

		ItemStack confirm = new ItemStack(Items.LIME_WOOL);
		confirm.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§aConfirmer"));
		this.inventory.setStack(5, confirm);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slot) {
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

		// Our confirm row is slots [0..8]
		if (slotIndex >= 0 && slotIndex < 9 && actionType == SlotActionType.PICKUP) {
			if (slotIndex == 3) {
				ShopScreens.openShop(serverPlayer);
				return;
			}
			if (slotIndex == 5) {
				execute(serverPlayer);
				ShopScreens.openShop(serverPlayer);
				return;
			}
			return;
		}

		super.onSlotClick(slotIndex, button, actionType, player);
	}

	private void execute(ServerPlayerEntity player) {
		if (item == null || item.item() == Items.AIR) {
			player.sendMessage(Text.literal("§cItem invalide."), false);
			return;
		}

		if (action == ShopScreens.ShopAction.BUY) {
			double cost = item.buyPrice();
			var account = EconomyManager.get(player);
			if (!account.remove(cost)) {
				player.sendMessage(Text.literal("§cFonds insuffisants. Coût: §e" + formatMoney(cost) + " ₽"), false);
				return;
			}

			ItemStack stack = new ItemStack(item.item(), 1);
			boolean inserted = player.getInventory().insertStack(stack);
			if (!inserted && !stack.isEmpty()) {
				player.dropItem(stack, false);
			}
			player.sendMessage(Text.literal("§aAchat: §f" + item.displayName() + " §7(-" + formatMoney(cost) + " ₽)"), false);
			return;
		}

		// SELL
		double gain = item.sellPrice();
		if (gain <= 0) {
			player.sendMessage(Text.literal("§cCet objet ne peut pas être vendu."), false);
			return;
		}

		Item toSell = item.item();
		if (!removeFromInventory(player.getInventory(), toSell, 1)) {
			player.sendMessage(Text.literal("§cTu n'as pas cet objet dans ton inventaire."), false);
			return;
		}

		EconomyManager.get(player).add(gain);
		player.sendMessage(Text.literal("§aVente: §f" + item.displayName() + " §7(+" + formatMoney(gain) + " ₽)"), false);
	}

	private static boolean removeFromInventory(PlayerInventory inv, Item item, int count) {
		int remaining = count;
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (stack.isEmpty() || !stack.isOf(item)) {
				continue;
			}
			int take = Math.min(remaining, stack.getCount());
			stack.decrement(take);
			remaining -= take;
			if (remaining <= 0) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return true;
	}

	private static final class ReadonlySlot extends Slot {
		private ReadonlySlot(Inventory inventory, int index, int x, int y) {
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
