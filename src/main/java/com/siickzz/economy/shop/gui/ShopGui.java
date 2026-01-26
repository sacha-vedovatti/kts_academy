package com.siickzz.economy.shop.gui;

import com.siickzz.economy.economy.EconomyManager;
import com.siickzz.economy.quests.QuestManager;
import com.siickzz.economy.shop.ShopItem;
import com.siickzz.economy.shop.ShopRegistry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Locale;

public final class ShopGui {
	private static final int SHOP_ROWS = 6;
	private static final int SHOP_SIZE = SHOP_ROWS * 9;
	private static final int MENU_ROWS = 3;
	private static final int MENU_SIZE = MENU_ROWS * 9;
	private static final int BACK_SLOT = 49;
	private static final int PREV_SLOT = 18;
	private static final int NEXT_SLOT = 26;

	private static final int QTY_SIZE = 9;
	private static final int QTY_MINUS_10 = 0;
	private static final int QTY_MINUS_1 = 1;
	private static final int QTY_CANCEL = 3;
	private static final int QTY_ITEM = 4;
	private static final int QTY_CONFIRM = 5;
	private static final int QTY_PLUS_1 = 7;
	private static final int QTY_PLUS_10 = 8;

	private ShopGui() {
	}

	public static void openShop(ServerPlayerEntity player) {
		openCategoryMenu(player);
	}

	private static void openCategoryMenu(ServerPlayerEntity player) {
		openCategoryMenu(player, 0);
	}

	private static void openCategoryMenu(ServerPlayerEntity player, int page) {
		Inventory inv = new SimpleInventory(MENU_SIZE);
		String[] slotCategories = new String[MENU_SIZE];

		java.util.List<ShopRegistry.ShopCategoryDef> categories = ShopRegistry.categoriesSorted();
		int pageSize = MENU_SIZE - 2; // reserve prev/next
		int start = Math.max(0, page) * pageSize;
		int end = Math.min(categories.size(), start + pageSize);

		if (page > 0) {
			inv.setStack(PREV_SLOT, named(Items.ARROW, "§ePrécédent"));
			slotCategories[PREV_SLOT] = "__prev__";
		}
		if (end < categories.size()) {
			inv.setStack(NEXT_SLOT, named(Items.ARROW, "§eSuivant"));
			slotCategories[NEXT_SLOT] = "__next__";
		}

		int slot = 0;
		for (int i = start; i < end; i++) {
			// Skip reserved nav slots
			while (slot == PREV_SLOT || slot == NEXT_SLOT) {
				slot++;
				if (slot >= MENU_SIZE) break;
			}
			if (slot >= MENU_SIZE) break;
			ShopRegistry.ShopCategoryDef cat = categories.get(i);
			Item icon = resolveCategoryIcon(cat);
			inv.setStack(slot, categoryIcon(icon, "§b" + cat.displayName(), "§7Clique pour ouvrir"));
			slotCategories[slot] = cat.name();
			slot++;
		}

		player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
			(syncId, playerInv, p) -> new CategoryMenuHandler(syncId, playerInv, inv, slotCategories, page),
			Text.literal("Shop - Catégories")
		));
	}

	private static void openCategory(ServerPlayerEntity player, String category) {
		Inventory shopInv = new SimpleInventory(SHOP_SIZE);
		ShopItem[] slotItems = new ShopItem[SHOP_SIZE];
		populateCategory(shopInv, slotItems, category);
		shopInv.setStack(BACK_SLOT, named(Items.ARROW, "§eRetour"));
		String title = category;
		ShopRegistry.ShopCategoryDef def = ShopRegistry.category(category);
		if (def != null && def.displayName() != null && !def.displayName().isBlank()) {
			title = def.displayName();
		}

		player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
			(syncId, inv, p) -> new CategoryItemsHandler(syncId, inv, shopInv, slotItems, category),
			Text.literal("Shop - " + title)
		));
	}

	static void openConfirm(ServerPlayerEntity player, Action action, ShopItem item, String returnCategory) {
		Inventory inv = new SimpleInventory(9);
		inv.setStack(3, named(Items.RED_WOOL, "§cAnnuler"));
		inv.setStack(5, named(Items.LIME_WOOL, "§aConfirmer"));

		ItemStack center;
		if (item != null && item.item() != Items.AIR) {
			int count = Math.max(1, item.amount());
			center = new ItemStack(item.item(), Math.min(64, count));
			double price = action == Action.BUY ? item.buyPrice() : item.sellPrice();
			String verb = action == Action.BUY ? "Acheter" : "Vendre";
			center.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§b" + verb + " §f" + item.displayName()));
			java.util.List<Text> lore = new java.util.ArrayList<>();
			lore.add(Text.literal("§7Prix: §e" + formatMoney(price) + " ₽"));
			if (count > 1) {
				lore.add(Text.literal("§7Quantité: §fx" + count));
			}
			center.set(DataComponentTypes.LORE, new LoreComponent(lore));
		} else {
			center = named(Items.BARRIER, "§cItem invalide");
		}
		inv.setStack(4, center);

		player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
			(syncId, playerInv, p) -> new ConfirmHandler(syncId, playerInv, inv, action, item, returnCategory),
			Text.literal(action == Action.BUY ? "Confirmer l'achat" : "Confirmer la vente")
		));
	}

	static void openQuantitySelector(ServerPlayerEntity player, ShopItem item, String returnCategory) {
		Inventory inv = new SimpleInventory(QTY_SIZE);
		player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
			(syncId, playerInv, p) -> new QuantityHandler(syncId, playerInv, inv, Action.BUY, item, returnCategory),
			Text.literal("Choisir quantité")
		));
	}

	static void openQuantitySelector(ServerPlayerEntity player, Action action, ShopItem item, String returnCategory) {
		Inventory inv = new SimpleInventory(QTY_SIZE);
		player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
			(syncId, playerInv, p) -> new QuantityHandler(syncId, playerInv, inv, action, item, returnCategory),
			Text.literal("Choisir quantité")
		));
	}

	private static void populateCategory(Inventory shopInv, ShopItem[] slotItems, String category) {
		java.util.HashSet<String> alreadyAdded = new java.util.HashSet<>();
		int slot = 0;

		for (ShopItem item : ShopRegistry.itemsByCategory(category)) {
			if (slot >= SHOP_SIZE)
                break;

			boolean invalid = item.item() == Items.AIR;
			if (!invalid) {
				int count = Math.max(1, item.amount());
				String signature = item.itemId().toString()
					+ "|" + item.buyPrice()
					+ "|" + item.sellPrice()
					+ "|" + count
					+ "|" + (item.giveCommand() == null ? "" : item.giveCommand());

				if (!alreadyAdded.add(signature))
                    continue;
			}
			if (slot == BACK_SLOT) {
				slot++;
				if (slot >= SHOP_SIZE)
                    break;
			}
			slotItems[slot] = item;

			int count = Math.max(1, item.amount());
			ItemStack stack;

			if (invalid) {
				stack = new ItemStack(Items.BARRIER);
				stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§cItem introuvable"));
			} else {
				stack = new ItemStack(item.item(), Math.min(64, count));
				stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§b" + item.displayName()));
			}
			java.util.List<Text> lore = new java.util.ArrayList<>();
			if (invalid) {
				lore.add(Text.literal("§7Id: §f" + item.itemId()));
				lore.add(Text.literal("§7Corrige l'itemId dans le shop.json"));
				lore.add(Text.literal("§8(La catégorie s'ouvre, mais l'item n'existe pas)"));
			} else {
				lore.add(Text.literal("§7Achat: §e" + formatMoney(item.buyPrice()) + " ₽"));
				lore.add(Text.literal("§7Vente: §a" + formatMoney(item.sellPrice()) + " ₽"));
				if (count > 1) {
					lore.add(Text.literal("§7Quantité: §fx" + count));
				}
				lore.add(Text.literal("§8Clic gauche: acheter"));
				lore.add(Text.literal("§8Clic droit: vendre"));
			}
			stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
			shopInv.setStack(slot, stack);
			slot++;
		}
	}

	private static Item resolveCategoryIcon(ShopRegistry.ShopCategoryDef def) {
		if (def != null) {
			if (def.iconItemId() != null && !def.iconItemId().isBlank()) {
				return iconOrFallback(def.iconItemId(), Items.CHEST);
			}
			// Fallback to first item of the category
			for (ShopItem item : ShopRegistry.itemsByCategory(def.name())) {
				if (item != null && item.item() != Items.AIR) {
					return item.item();
				}
			}
		}
		return Items.CHEST;
	}

	private static ItemStack categoryIcon(Item item, String title, String... lore) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(title));
		java.util.List<Text> lines = new java.util.ArrayList<>();
		for (String s : lore) {
			lines.add(Text.literal(s));
		}
		stack.set(DataComponentTypes.LORE, new LoreComponent(lines));
		return stack;
	}

	private static ItemStack named(Item item, String name) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
		return stack;
	}

	private static Item iconOrFallback(String itemId, Item fallback) {
		try {
			Identifier id = Identifier.of(itemId);
			Item item = Registries.ITEM.get(id);
			if (item == Items.AIR) {
				return fallback;
			}
			return item;
		} catch (Throwable ignored) {
			return fallback;
		}
	}

	public enum Action {
		BUY,
		SELL
	}

	private static final class CategoryMenuHandler extends GenericContainerScreenHandler {
		private final String[] slotCategories;
		private final int page;

		private CategoryMenuHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, String[] slotCategories, int page) {
			super(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, inventory, MENU_ROWS);
			this.slotCategories = slotCategories;
			this.page = page;
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

			// Never allow taking/inserting items in the GUI inventory.
			if (slotIndex >= 0 && slotIndex < MENU_SIZE) {
				if (actionType == SlotActionType.PICKUP) {
					String cat = slotCategories[slotIndex];
					if (cat == null) return;
					if ("__prev__".equals(cat)) {
						openCategoryMenu(serverPlayer, Math.max(0, page - 1));
						return;
					}
					if ("__next__".equals(cat)) {
						openCategoryMenu(serverPlayer, page + 1);
						return;
					}
					openCategory(serverPlayer, cat);
				}
				return;
			}

			super.onSlotClick(slotIndex, button, actionType, player);
		}

		@Override
		public boolean canUse(PlayerEntity player) {
			return true;
		}
	}

	private static final class CategoryItemsHandler extends GenericContainerScreenHandler {
		private final ShopItem[] slotItems;
		private final String category;

		private CategoryItemsHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, ShopItem[] slotItems, String category) {
			super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inventory, SHOP_ROWS);
			this.slotItems = slotItems;
			this.category = category;
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

			// Never allow taking/inserting items in the GUI inventory.
			if (slotIndex >= 0 && slotIndex < SHOP_SIZE) {
				if (slotIndex == BACK_SLOT && actionType == SlotActionType.PICKUP) {
					openCategoryMenu(serverPlayer);
					return;
				}

				if (actionType == SlotActionType.PICKUP) {
					ShopItem item = slotItems[slotIndex];
					if (item == null) return;

					if (button == 0) {
						if (ShopRegistry.useQuantitySelectorForCategory(category)) {
							openQuantitySelector(serverPlayer, Action.BUY, item, category);
							return;
						}
						openConfirm(serverPlayer, Action.BUY, item, category);
						return;
					}
					if (button == 1) {
						if (ShopRegistry.useQuantitySelectorForCategory(category)) {
							openQuantitySelector(serverPlayer, Action.SELL, item, category);
							return;
						}
						openConfirm(serverPlayer, Action.SELL, item, category);
						return;
					}
				}
				return;
			}

			super.onSlotClick(slotIndex, button, actionType, player);
		}

		@Override
		public boolean canUse(PlayerEntity player) {
			return true;
		}
	}

	private static final class QuantityHandler extends GenericContainerScreenHandler {
		private final Inventory inv;
		private final Action action;
		private final ShopItem item;
		private final String returnCategory;
		private int qtyPacks;
		private int maxPacks;

		private QuantityHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, Action action, ShopItem item, String returnCategory) {
			super(ScreenHandlerType.GENERIC_9X1, syncId, playerInventory, inventory, 1);
			this.inv = inventory;
			this.action = action == null ? Action.BUY : action;
			this.item = item;
			this.returnCategory = returnCategory;
			this.qtyPacks = 1;
			this.maxPacks = 0;
			if (playerInventory.player instanceof ServerPlayerEntity serverPlayer) {
				refresh(serverPlayer);
			}
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

			// Never allow taking/inserting items in the GUI inventory.
			if (slotIndex >= 0 && slotIndex < QTY_SIZE) {
				if (actionType == SlotActionType.PICKUP) {
					switch (slotIndex) {
						case QTY_CANCEL -> {
							returnTo(serverPlayer);
							return;
						}
						case QTY_CONFIRM -> {
							execute(serverPlayer);
							returnTo(serverPlayer);
							return;
						}
						case QTY_MINUS_1 -> {
							adjust(serverPlayer, -1);
							return;
						}
						case QTY_MINUS_10 -> {
							adjust(serverPlayer, -10);
							return;
						}
						case QTY_PLUS_1 -> {
							adjust(serverPlayer, +1);
							return;
						}
						case QTY_PLUS_10 -> {
							adjust(serverPlayer, +10);
							return;
						}
						default -> {
							// Ignore clicks on the item preview
							return;
						}
					}
				}
				return;
			}

			super.onSlotClick(slotIndex, button, actionType, player);
		}

		private void adjust(ServerPlayerEntity player, int delta) {
			refresh(player);
			int min = maxPacks <= 0 ? 0 : 1;
			qtyPacks = Math.max(min, Math.min(maxPacks, qtyPacks + delta));
			refresh(player);
		}

		private void refresh(ServerPlayerEntity player) {
			if (item == null || item.item() == Items.AIR) {
				this.inv.setStack(QTY_ITEM, named(Items.BARRIER, "§cItem invalide"));
				this.inv.setStack(QTY_CANCEL, named(Items.RED_WOOL, "§cAnnuler"));
				this.inv.setStack(QTY_CONFIRM, named(Items.GRAY_WOOL, "§8Confirmer"));
				return;
			}

			double unitPrice = Math.max(0.0, action == Action.BUY ? item.buyPrice() : item.sellPrice());
			int packSize = Math.max(1, item.amount());
			if (action == Action.BUY) {
				double balance = EconomyManager.get(player).getBalance();
				this.maxPacks = unitPrice <= 0.0 ? 0 : (int) Math.floor(balance / unitPrice);
			} else {
				int availableItems = countInInventory(player.getInventory(), item.item());
				this.maxPacks = packSize <= 0 ? 0 : (availableItems / packSize);
			}
			if (this.maxPacks < 0) this.maxPacks = 0;

			int min = maxPacks <= 0 ? 0 : 1;
			if (qtyPacks < min) qtyPacks = min;
			if (qtyPacks > maxPacks) qtyPacks = maxPacks;

			this.inv.setStack(QTY_MINUS_10, named(Items.RED_STAINED_GLASS_PANE, "§c-10"));
			this.inv.setStack(QTY_MINUS_1, named(Items.RED_STAINED_GLASS_PANE, "§c-1"));
			this.inv.setStack(QTY_PLUS_1, named(Items.LIME_STAINED_GLASS_PANE, "§a+1"));
			this.inv.setStack(QTY_PLUS_10, named(Items.LIME_STAINED_GLASS_PANE, "§a+10"));
			this.inv.setStack(QTY_CANCEL, named(Items.RED_WOOL, "§cAnnuler"));

			boolean canProceed = qtyPacks > 0 && unitPrice > 0.0;
			this.inv.setStack(QTY_CONFIRM, named(canProceed ? Items.LIME_WOOL : Items.GRAY_WOOL, canProceed ? "§aConfirmer" : "§8Confirmer"));

			int totalGive = qtyPacks * packSize;
			double totalPrice = unitPrice * qtyPacks;
			int shownCount = Math.max(1, Math.min(64, totalGive));
			ItemStack center = new ItemStack(item.item(), shownCount);
			center.set(DataComponentTypes.CUSTOM_NAME, Text.literal((action == Action.BUY ? "§bAcheter §f" : "§bVendre §f") + item.displayName()));
			java.util.List<Text> lore = new java.util.ArrayList<>();
			lore.add(Text.literal("§7Prix/unité: §e" + formatMoney(unitPrice) + " ₽"));
			lore.add(Text.literal("§7Pack: §fx" + packSize));
			lore.add(Text.literal("§7Quantité: §f" + qtyPacks + " §8(packs)"));
			lore.add(Text.literal("§7Total: §fx" + totalGive));
			lore.add(Text.literal((action == Action.BUY ? "§7Coût: §e" : "§7Gain: §a") + formatMoney(totalPrice) + " ₽"));
			lore.add(Text.literal("§7Max: §f" + maxPacks + " §8(packs)"));
			center.set(DataComponentTypes.LORE, new LoreComponent(lore));
			this.inv.setStack(QTY_ITEM, center);
		}

		private void returnTo(ServerPlayerEntity player) {
			if (returnCategory != null) {
				openCategory(player, returnCategory);
				return;
			}
			openCategoryMenu(player);
		}

		private void execute(ServerPlayerEntity player) {
			if (item == null || item.item() == Items.AIR) {
				player.sendMessage(Text.literal("§cItem invalide."), false);
				return;
			}

			int packSize = Math.max(1, item.amount());
			double unitPrice = Math.max(0.0, action == Action.BUY ? item.buyPrice() : item.sellPrice());
			if (unitPrice <= 0.0) {
				player.sendMessage(Text.literal(action == Action.BUY ? "§cCet objet ne peut pas être acheté." : "§cCet objet ne peut pas être vendu."), false);
				return;
			}

			int maxNow;
			if (action == Action.BUY) {
				double balance = EconomyManager.get(player).getBalance();
				maxNow = (int) Math.floor(balance / unitPrice);
			} else {
				int availableItems = countInInventory(player.getInventory(), item.item());
				maxNow = packSize <= 0 ? 0 : (availableItems / packSize);
			}
			int min = maxNow <= 0 ? 0 : 1;
			int packs = Math.max(min, Math.min(maxNow, qtyPacks));
			if (packs <= 0) {
				player.sendMessage(Text.literal(action == Action.BUY ? "§cFonds insuffisants." : "§cTu n'as pas assez d'objets."), false);
				return;
			}

			int totalItems = packs * packSize;
			double totalValue = unitPrice * packs;
			if (action == Action.BUY) {
				var account = EconomyManager.get(player);
				if (!account.remove(totalValue)) {
					player.sendMessage(Text.literal("§cFonds insuffisants. Coût: §e" + formatMoney(totalValue) + " ₽"), false);
					return;
				}

				if (item.giveCommand() != null && !item.giveCommand().isBlank()) {
					String cmd = item.giveCommand()
						.replace("{player}", player.getName().getString())
						.replace("{count}", Integer.toString(totalItems));
					player.getServer().getCommandManager().executeWithPrefix(player.getServer().getCommandSource(), cmd);
				} else {
					int remaining = totalItems;
					while (remaining > 0) {
						int stackSize = Math.min(64, remaining);
						ItemStack stack = new ItemStack(item.item(), stackSize);
						boolean inserted = player.getInventory().insertStack(stack);
						if (!inserted && !stack.isEmpty()) {
							player.dropItem(stack, false);
						}
						remaining -= stackSize;
					}
				}

				player.sendMessage(Text.literal("§aAchat: §f" + item.displayName() + " x" + totalItems + " §7(-" + formatMoney(totalValue) + " ₽)"), false);
				QuestManager.onShopBought(player, totalItems);
				return;
			}

			// SELL
			if (!removeFromInventory(player.getInventory(), item.item(), totalItems)) {
				player.sendMessage(Text.literal("§cTu n'as pas assez d'objets."), false);
				return;
			}
			EconomyManager.get(player).add(totalValue);
			player.sendMessage(Text.literal("§aVente: §f" + item.displayName() + " x" + totalItems + " §7(+" + formatMoney(totalValue) + " ₽)"), false);
			QuestManager.onShopSold(player, totalItems);
		}

		@Override
		public boolean canUse(PlayerEntity player) {
			return true;
		}
	}

	private static final class ConfirmHandler extends GenericContainerScreenHandler {
		private final Action action;
		private final ShopItem item;
		private final String returnCategory;

		private ConfirmHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, Action action, ShopItem item, String returnCategory) {
			super(ScreenHandlerType.GENERIC_9X1, syncId, playerInventory, inventory, 1);
			this.action = action;
			this.item = item;
			this.returnCategory = returnCategory;
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

			// Never allow taking/inserting items in the GUI inventory.
			if (slotIndex >= 0 && slotIndex < 9) {
				if (actionType == SlotActionType.PICKUP) {
					if (slotIndex == 3) {
						returnTo(serverPlayer);
						return;
					}
					if (slotIndex == 5) {
						execute(serverPlayer);
						returnTo(serverPlayer);
						return;
					}
				}
				return;
			}

			super.onSlotClick(slotIndex, button, actionType, player);
		}

		private void returnTo(ServerPlayerEntity player) {
			if (returnCategory != null) {
				openCategory(player, returnCategory);
				return;
			}
			openCategoryMenu(player);
		}

		private void execute(ServerPlayerEntity player) {
			if (item == null || item.item() == Items.AIR) {
				player.sendMessage(Text.literal("§cItem invalide."), false);
				return;
			}

			int packSize = Math.max(1, item.amount());

			if (action == Action.BUY) {
				double cost = item.buyPrice();
				var account = EconomyManager.get(player);
				if (!account.remove(cost)) {
					player.sendMessage(Text.literal("§cFonds insuffisants. Coût: §e" + formatMoney(cost) + " ₽"), false);
					return;
				}

				if (item.giveCommand() != null && !item.giveCommand().isBlank()) {
					String cmd = item.giveCommand()
						.replace("{player}", player.getName().getString())
						.replace("{count}", Integer.toString(packSize));
					player.getServer().getCommandManager().executeWithPrefix(player.getServer().getCommandSource(), cmd);
				} else {
					ItemStack stack = new ItemStack(item.item(), packSize);
					boolean inserted = player.getInventory().insertStack(stack);
					if (!inserted && !stack.isEmpty()) {
						player.dropItem(stack, false);
					}
				}
				String suffix = packSize > 1 ? " x" + packSize : "";
				player.sendMessage(Text.literal("§aAchat: §f" + item.displayName() + suffix + " §7(-" + formatMoney(cost) + " ₽)"), false);
				QuestManager.onShopBought(player, packSize);
				return;
			}

			double gain = item.sellPrice();
			if (gain <= 0) {
				player.sendMessage(Text.literal("§cCet objet ne peut pas être vendu."), false);
				return;
			}

			Item toSell = item.item();
			if (!removeFromInventory(player.getInventory(), toSell, packSize)) {
				player.sendMessage(Text.literal("§cTu n'as pas cet objet dans ton inventaire."), false);
				return;
			}

			EconomyManager.get(player).add(gain);
			String suffix = packSize > 1 ? " x" + packSize : "";
			player.sendMessage(Text.literal("§aVente: §f" + item.displayName() + suffix + " §7(+" + formatMoney(gain) + " ₽)"), false);
			QuestManager.onShopSold(player, packSize);
		}

		@Override
		public boolean canUse(PlayerEntity player) {
			return true;
		}
	}

	private static boolean removeFromInventory(PlayerInventory inv, Item item, int count) {
		int remaining = count;
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (stack.isEmpty() || !stack.isOf(item)) continue;
			int take = Math.min(remaining, stack.getCount());
			stack.decrement(take);
			remaining -= take;
			if (remaining <= 0) return true;
		}
		return false;
	}

	private static int countInInventory(PlayerInventory inv, Item item) {
		if (inv == null || item == null) return 0;
		int total = 0;
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (stack.isEmpty() || !stack.isOf(item)) continue;
			total += stack.getCount();
		}
		return total;
	}

	private static String formatMoney(double value) {
		if (value == (long) value) {
			return Long.toString((long) value);
		}
		return String.format(Locale.ROOT, "%.2f", value);
	}
}
