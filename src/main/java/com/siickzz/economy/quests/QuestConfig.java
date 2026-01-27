package com.siickzz.economy.quests;

import java.util.Map;
import java.util.List;

public final class QuestConfig {
	public List<QuestCategoryConfig> categories;
	public LegendaryCategoryConfig legendaryCategory;
	public List<QuestDefConfig> quests;

	public static final class QuestCategoryConfig {
		public String name;
		public String displayName;
		public String iconItemId;
	}

	public static final class LegendaryCategoryConfig {
		public boolean enabled = true;
		public String category = "LEGENDARIES";
		public String displayName = "Légendaires";
		public String iconItemId = "minecraft:nether_star";
		// If true and no explicit speciesIcons entry exists, the system may pick an icon
		// from config/academy/legendary_items.json occurrences (deterministic per species).
		public boolean usePoolFallback = false;
		public double reward = 2500.0;
		public List<String> species;
		// Optional per-species icon override, e.g. {"lugia": "cobblemon:silver_wing"}
		public Map<String, String> speciesIcons;
	}

	public static final class QuestDefConfig {
		public String id;
		public String category;
		public String type; // CAPTURE_ANY, CAPTURE_SPECIES, BATTLE_WIN_ANY, TRADE_ANY, FISH_POKEMON_ANY, SHOP_BUY_ANY, SHOP_SELL_ANY, HARVEST_ITEM, MINE_ORE, POKEDEX_CAUGHT
		public String target; // species name/id for CAPTURE_SPECIES, ore key for MINE_ORE (e.g. diamond/iron/gold), item id/pattern for HARVEST_ITEM (e.g. cobblemon:black_apricorn or *apricorn*)
		public String iconItemId;
		public Integer goal;
		public Double reward;
		public String title;
		public String description;
		public List<QuestTierConfig> tiers;
	}

	public static final class QuestTierConfig {
		public Integer goal;
		public Double reward;
		public String title;
		public String description;
	}
}
