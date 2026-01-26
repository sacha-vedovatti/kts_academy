package com.siickzz.economy.quests.gui;

import com.siickzz.economy.quests.QuestDef;
import com.siickzz.economy.quests.QuestManager;
import com.siickzz.economy.quests.QuestProgress;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class QuestGui {
	private static final int MENU_ROWS = 3;
	private static final int MENU_SIZE = MENU_ROWS * 9;
	private static final int LIST_ROWS = 6;
	private static final int LIST_SIZE = LIST_ROWS * 9;
	private static final int LIST_PAGE_SIZE = 45; // reserve bottom row (9 slots) for controls
	private static final int BACK_SLOT = 49;
	private static final int PREV_SLOT = 18;
	private static final int NEXT_SLOT = 26;
	private static final int LIST_PREV_SLOT = 48;
	private static final int LIST_NEXT_SLOT = 50;
	private static final ItemStack FILLER_MENU = pane(Items.BLACK_STAINED_GLASS_PANE);
	private static final ItemStack FILLER_LIST = pane(Items.GRAY_STAINED_GLASS_PANE);

	private QuestGui() {
	}

	public static void open(ServerPlayerEntity player) {
		openCategoryMenu(player, 0);
	}

	private static void openCategoryMenu(ServerPlayerEntity player, int page) {
		Inventory inv = new SimpleInventory(MENU_SIZE);
		String[] slotCategory = new String[MENU_SIZE];
		fill(inv, FILLER_MENU);

		List<QuestManager.QuestCategory> cats = QuestManager.categories();
		int pageSize = MENU_SIZE - 2;
		int start = Math.max(0, page) * pageSize;
		int end = Math.min(cats.size(), start + pageSize);

		if (page > 0) {
			inv.setStack(PREV_SLOT, named(Items.ARROW, "§ePrécédent"));
			slotCategory[PREV_SLOT] = "__prev__";
		}
		if (end < cats.size()) {
			inv.setStack(NEXT_SLOT, named(Items.ARROW, "§eSuivant"));
			slotCategory[NEXT_SLOT] = "__next__";
		}

		int slot = 0;
		for (int i = start; i < end; i++) {
			while (slot == PREV_SLOT || slot == NEXT_SLOT) {
				slot++;
				if (slot >= MENU_SIZE) break;
			}
			if (slot >= MENU_SIZE) break;

			QuestManager.QuestCategory cat = cats.get(i);
			Item icon = iconOrFallback(cat.iconItemId(), Items.BOOK);

			int total = QuestManager.questsByCategory(cat.name()).size();
			int done = countDone(player, cat.name());

			ItemStack stack = new ItemStack(icon);
			stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§b" + cat.displayName()));
			List<Text> lore = new ArrayList<>();
			lore.add(Text.literal("§7Quêtes: §f" + total));
			lore.add(Text.literal("§7Terminées: §a" + done + "§7/§f" + total));
			lore.add(Text.literal("§8Clique pour ouvrir"));
			stack.set(DataComponentTypes.LORE, new LoreComponent(lore));

			inv.setStack(slot, stack);
			slotCategory[slot] = cat.name();
			slot++;
		}

		player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
			(syncId, playerInv, p) -> new CategoryMenuHandler(syncId, playerInv, inv, slotCategory, page),
			Text.literal("Quêtes - Catégories")
		));
	}

	private static int countDone(ServerPlayerEntity player, String category) {
		int done = 0;
		for (QuestDef q : QuestManager.questsByCategory(category)) {
			QuestProgress qp = QuestManager.progress(player, q.id());
			int goal;
			if (q.isTiered() && q.tiers() != null && !q.tiers().isEmpty()) {
				goal = q.tiers().get(q.tiers().size() - 1).goal();
			} else {
				goal = q.goal();
			}
			if (qp.progress >= goal) {
				done++;
			}
		}
		return done;
	}

	private static void openCategory(ServerPlayerEntity player, String category) {
		openCategory(player, category, 0);
	}

	private static void openCategory(ServerPlayerEntity player, String category, int page) {
		Inventory inv = new SimpleInventory(LIST_SIZE);
		String[] slotQuestId = new String[LIST_SIZE];
		fill(inv, FILLER_LIST);

		List<QuestDef> quests = new ArrayList<>(QuestManager.questsByCategory(category));
		quests.sort((a, b) -> {
			QuestProgress pa = QuestManager.progress(player, a.id());
			QuestProgress pb = QuestManager.progress(player, b.id());
			int sa = statusRank(pa, QuestManager.effectiveGoal(a, pa));
			int sb = statusRank(pb, QuestManager.effectiveGoal(b, pb));
			if (sa != sb) return Integer.compare(sa, sb);
			return String.CASE_INSENSITIVE_ORDER.compare(QuestManager.effectiveTitle(a, pa), QuestManager.effectiveTitle(b, pb));
		});

		int safePage = Math.max(0, page);
		int start = safePage * LIST_PAGE_SIZE;
		int end = Math.min(quests.size(), start + LIST_PAGE_SIZE);

		if (safePage > 0) {
			inv.setStack(LIST_PREV_SLOT, named(Items.ARROW, "§ePrécédent"));
			slotQuestId[LIST_PREV_SLOT] = "__prev__";
		}
		if (end < quests.size()) {
			inv.setStack(LIST_NEXT_SLOT, named(Items.ARROW, "§eSuivant"));
			slotQuestId[LIST_NEXT_SLOT] = "__next__";
		}

		int slot = 0;
		for (int idx = start; idx < end; idx++) {
			QuestDef q = quests.get(idx);
			if (slot >= LIST_SIZE) break;
			if (slot == BACK_SLOT || slot == LIST_PREV_SLOT || slot == LIST_NEXT_SLOT) {
				slot++;
				if (slot >= LIST_SIZE) break;
			}

			QuestProgress qp = QuestManager.progress(player, q.id());
			int goalNow = QuestManager.effectiveGoal(q, qp);
			double rewardNow = QuestManager.effectiveReward(q, qp);
			String titleNow = QuestManager.effectiveTitle(q, qp);
			String descNow = QuestManager.effectiveDescription(q, qp);
			boolean complete = qp.progress >= goalNow;
			boolean claimed = qp.claimed;

			Item icon = questIcon(category, q, qp);
			ItemStack stack = new ItemStack(icon);
			String status = claimed ? "§8Réclamée" : (complete ? "§aRéclamer" : "§eEn cours");
			stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§b" + titleNow));

			List<Text> lore = new ArrayList<>();
			lore.add(Text.literal("§7Statut: " + status));
			if (descNow != null && !descNow.isBlank()) {
				lore.add(Text.literal("§7" + descNow));
			}
			int shown = Math.min(goalNow, Math.max(0, qp.progress));
			lore.add(Text.literal("§7Progression: §f" + shown + "§7/§f" + goalNow + " " + progressBar(shown, goalNow)));
			lore.add(Text.literal("§7Récompense: §e" + formatMoney(rewardNow) + " ₽"));
			if (complete && !claimed) {
				lore.add(Text.literal("§aClique pour réclamer la récompense"));
			} else if (claimed) {
				lore.add(Text.literal("§8Récompense déjà récupérée"));
			} else {
				lore.add(Text.literal("§8Continue à progresser"));
			}
			stack.set(DataComponentTypes.LORE, new LoreComponent(lore));

			inv.setStack(slot, stack);
			slotQuestId[slot] = q.id();
			slot++;
		}

		inv.setStack(BACK_SLOT, named(Items.ARROW, "§eRetour"));

		String title = category;
		QuestManager.QuestCategory def = QuestManager.category(category);
		if (def != null && def.displayName() != null && !def.displayName().isBlank()) {
			title = def.displayName();
		}

		player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
			(syncId, playerInv, p) -> new QuestListHandler(syncId, playerInv, inv, slotQuestId, category, safePage),
			Text.literal("Quêtes - " + title)
		));
	}

	private static Item questIcon(String category, QuestDef quest, QuestProgress progress) {
		boolean complete = progress.progress >= QuestManager.effectiveGoal(quest, progress);
		boolean claimed = progress.claimed;
		if (claimed) return Items.GRAY_DYE;
		if (complete) return Items.LIME_DYE;
		if (category != null && category.toUpperCase(Locale.ROOT).contains("LEGEND")) {
			if (quest.type() == QuestDef.QuestType.CAPTURE_SPECIES && quest.target() != null && !quest.target().isBlank()) {
				Item icon = resolveLegendaryIcon(quest.target());
				if (icon != Items.AIR) return icon;
			}
			return Items.NETHER_STAR;
		}
		if (quest.type() == QuestDef.QuestType.BATTLE_WIN_ANY) return Items.IRON_SWORD;
		if (quest.type() == QuestDef.QuestType.TRADE_ANY) return Items.EMERALD;
		if (quest.type() == QuestDef.QuestType.FISH_POKEMON_ANY) return Items.FISHING_ROD;
		if (quest.type() == QuestDef.QuestType.SHOP_BUY_ANY) return Items.CHEST;
		if (quest.type() == QuestDef.QuestType.SHOP_SELL_ANY) return Items.HOPPER;
		if (quest.type() == QuestDef.QuestType.HARVEST_ITEM) {
			String t = quest.target() == null ? "" : quest.target().trim();
			if (!t.isBlank() && t.contains(":")) {
				Item icon = iconOrFallback(t, Items.SWEET_BERRIES);
				if (icon != Items.AIR) return icon;
			}
			return Items.SWEET_BERRIES;
		}
		if (quest.type() == QuestDef.QuestType.POKEDEX_CAUGHT) return Items.KNOWLEDGE_BOOK;
		if (quest.type() == QuestDef.QuestType.MINE_ORE) {
			String t = quest.target() == null ? "" : quest.target().trim().toLowerCase(Locale.ROOT);
			t = t.replace('-', '_').replace(' ', '_');
			return switch (t) {
				case "diamond" -> Items.DIAMOND_ORE;
				case "iron" -> Items.IRON_ORE;
				case "gold" -> Items.GOLD_ORE;
				default -> Items.IRON_PICKAXE;
			};
		}
		if (quest.type() == QuestDef.QuestType.CAPTURE_SPECIES) return Items.NAME_TAG;
		return Items.PAPER;
	}

	private static Item resolveLegendaryIcon(String speciesKey) {
		if (speciesKey == null || speciesKey.isBlank()) return Items.AIR;

		// Preferred: academy legendary_items.json mapping/pool (deterministic per species)
		String configured = QuestManager.legendaryIconItemIdForSpecies(speciesKey);
		if (configured != null && !configured.isBlank()) {
			Item item = iconOrFallback(configured, Items.AIR);
			if (item != Items.AIR) return item;
		}

		// Fallback: try common Cobblemon patterns
		String key = speciesKey.trim().toLowerCase(Locale.ROOT);
		String underscored = key.replace('-', '_').replace(' ', '_');
		String compact = underscored.replace("_", "");
		String[] candidates = {
			"cobblemon:" + underscored,
			"cobblemon:" + underscored + "_icon",
			"cobblemon:" + underscored + "_sprite",
			"cobblemon:" + underscored + "_spawn_egg",
			"cobblemon:" + underscored + "_egg",
			"cobblemon:" + compact,
			"cobblemon:" + compact + "_icon",
			"cobblemon:" + compact + "_sprite",
			"cobblemon:" + compact + "_spawn_egg",
			"cobblemon:" + compact + "_egg"
		};
		for (String id : candidates) {
			Item item = iconOrFallback(id, Items.AIR);
			if (item != Items.AIR) return item;
		}
		return Items.AIR;
	}

	private static int statusRank(QuestProgress p, int goal) {
		boolean complete = p.progress >= goal;
		if (complete && !p.claimed) return 0; // claimable first
		if (!complete) return 1; // in progress
		return 2; // claimed
	}

	private static String progressBar(int progress, int goal) {
		int g = Math.max(1, goal);
		int p = Math.max(0, Math.min(progress, g));
		int width = 10;
		int filled = (int) Math.round((p / (double) g) * width);
		filled = Math.max(0, Math.min(width, filled));
		StringBuilder sb = new StringBuilder("§7[");
		for (int i = 0; i < width; i++) {
			sb.append(i < filled ? "§a■" : "§8■");
		}
		sb.append("§7]");
		return sb.toString();
	}

	private static void fill(Inventory inv, ItemStack stack) {
		for (int i = 0; i < inv.size(); i++) {
			inv.setStack(i, stack.copy());
		}
	}

	private static ItemStack pane(Item item) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
		return stack;
	}

	private static ItemStack named(Item item, String name) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
		return stack;
	}

	private static Item iconOrFallback(String itemId, Item fallback) {
		if (itemId == null || itemId.isBlank()) return fallback;
		try {
			Identifier id = Identifier.of(itemId);
			Item item = Registries.ITEM.get(id);
			return item == Items.AIR ? fallback : item;
		} catch (Throwable ignored) {
			return fallback;
		}
	}

	private static String formatMoney(double value) {
		if (value == (long) value) return Long.toString((long) value);
		return String.format(Locale.ROOT, "%.2f", value);
	}

	private static final class CategoryMenuHandler extends GenericContainerScreenHandler {
		private final String[] slotCategory;
		private final int page;

		private CategoryMenuHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, String[] slotCategory, int page) {
			super(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, inventory, MENU_ROWS);
			this.slotCategory = slotCategory;
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
					String cat = slotCategory[slotIndex];
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

	private static final class QuestListHandler extends GenericContainerScreenHandler {
		private final String[] slotQuestId;
		private final String category;
		private final int page;

		private QuestListHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, String[] slotQuestId, String category, int page) {
			super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inventory, LIST_ROWS);
			this.slotQuestId = slotQuestId;
			this.category = category;
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
			if (slotIndex >= 0 && slotIndex < LIST_SIZE) {
				if (slotIndex == BACK_SLOT && actionType == SlotActionType.PICKUP) {
					openCategoryMenu(serverPlayer, 0);
					return;
				}
				if (slotIndex == LIST_PREV_SLOT && actionType == SlotActionType.PICKUP) {
					openCategory(serverPlayer, category, Math.max(0, page - 1));
					return;
				}
				if (slotIndex == LIST_NEXT_SLOT && actionType == SlotActionType.PICKUP) {
					openCategory(serverPlayer, category, page + 1);
					return;
				}

				if (actionType == SlotActionType.PICKUP) {
					String questId = slotQuestId[slotIndex];
					if (questId == null) return;
					if ("__prev__".equals(questId)) {
						openCategory(serverPlayer, category, Math.max(0, page - 1));
						return;
					}
					if ("__next__".equals(questId)) {
						openCategory(serverPlayer, category, page + 1);
						return;
					}

					QuestDef quest = QuestManager.quest(questId);
					if (quest == null) return;
					QuestProgress qp = QuestManager.progress(serverPlayer, questId);
					boolean complete = qp.progress >= QuestManager.effectiveGoal(quest, qp);
					if (complete && !qp.claimed) {
						boolean ok = QuestManager.tryClaim(serverPlayer, questId);
						if (ok) {
							openCategory(serverPlayer, category, page);
						}
						return;
					}
					int goalNow = QuestManager.effectiveGoal(quest, qp);
					serverPlayer.sendMessage(Text.literal("§7Progression: §f" + Math.min(goalNow, qp.progress) + "§7/§f" + goalNow), false);
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
	}
}
