package com.siickzz.ktsacademy.quests;

import java.util.List;
import java.util.Locale;

public record QuestDef(
		String id,
		String category,
		QuestType type,
		String target,
		String iconItemId,
		int goal,
		double reward,
		String title,
		String description,
		List<QuestTier> tiers
	) {

	public record QuestTier(int goal, double reward) {}

	public boolean isTiered() {
		return tiers != null && !tiers.isEmpty();
	}

	public enum QuestType {
		CAPTURE_ANY,
		CAPTURE_SPECIES,
		CAPTURE_SHINY_ANY,
		BATTLE_WIN_ANY,
		TRADE_ANY,
		FISH_POKEMON_ANY,
		SHOP_BUY_ANY,
		SHOP_SELL_ANY,
		HARVEST_ITEM,
		MINE_ORE,
		POKEDEX_CAUGHT;

		public static QuestType parse(String raw)
		{
			if (raw == null)
				return CAPTURE_ANY;

			String s = raw.trim().toUpperCase(Locale.ROOT);
			return switch (s) {
				case "CAPTURE_SPECIES" -> CAPTURE_SPECIES;
				case "CAPTURE_ANY" -> CAPTURE_ANY;
				case "CAPTURE_SHINY_ANY", "CAPTURE_SHINY", "SHINY_CAPTURE_ANY", "SHINY_ANY" -> CAPTURE_SHINY_ANY;
				case "BATTLE_WIN_ANY" -> BATTLE_WIN_ANY;
				case "TRADE_ANY" -> TRADE_ANY;
				case "FISH_POKEMON_ANY", "POKEMON_FISHED_ANY", "POKEFISH_ANY" -> FISH_POKEMON_ANY;
				case "SHOP_BUY_ANY" -> SHOP_BUY_ANY;
				case "SHOP_SELL_ANY" -> SHOP_SELL_ANY;
				case "HARVEST_ITEM", "HARVEST", "RECOLTE", "RECOLTE_ITEM" -> HARVEST_ITEM;
				case "MINE_ORE", "MINE", "MINER" -> MINE_ORE;
				case "POKEDEX_CAUGHT", "POKEDEX" -> POKEDEX_CAUGHT;
				default -> CAPTURE_ANY;
			};
		}
	}
}
