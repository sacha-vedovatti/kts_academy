package com.siickzz.ktsacademy.quests;

import java.util.Map;
import java.util.List;

public final class QuestConfig {
	public List<QuestCategoryConfig> categories;
	public LegendaryCategoryConfig legendaryCategory;
	public List<QuestDefConfig> quests;
	public TierDropsConfig tierDrops;

	public static final class TierDropsConfig {
		public boolean enabled = false;
		public Integer rollsPerTier = 1;
		public boolean dropOnGroundIfFull = true;
		public boolean notifyPlayer = true;
		public List<TierDropEntryConfig> drops;
	}

	public static final class TierDropEntryConfig {
		public String itemId;
		public Double chance; // 0..1 or 0..100 (treated as percent if > 1)
		public Integer min = 1;
		public Integer max = 1;
	}

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
