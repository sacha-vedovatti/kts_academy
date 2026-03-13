package com.siickzz.ktsacademy.shop;

import java.util.ArrayList;
import java.util.List;

public final class ShopConfig {
	/**
	 * Optional shop UI settings.
	 */
	public QuantitySelectorConfig quantitySelector = new QuantitySelectorConfig();

	/**
	 * format:
	 * {
	 *   "categories": [ { "name": "BALLS", "items": [ ... ] }, ... ]
	 * }
	 */
	public List<ShopConfigCategory> categories = new ArrayList<>();

	/**
	 * Legacy format:
	 * {
	 *   "items": [ { "category": "BALLS", ... }, ... ]
	 * }
	 */
	public List<ShopConfigItem> items = new ArrayList<>();

	public static final class ShopConfigCategory {
		public String name;
		public String displayName;
		public String iconItemId;
		public List<ShopConfigItem> items = new ArrayList<>();
	}

	public static final class ShopConfigItem {
		public String key;
		/**
		 * Optional when the item lives inside a category block.
		 */
		public String category;
		public String itemId;
		public String displayName;
		public Integer amount;
		/**
		 * Optional server command template to give the item.
		 * Supports placeholders: {player} and {count}.
		 * Example: "give {player} academy:booster_pack[academy:booster_pack=\"base\"] {count}"
		 */
		public String giveCommand;
		public double buyPrice;
		public double sellPrice;
	}

	public static final class QuantitySelectorConfig {
		/**
		 * Category names (case-insensitive) that should use the quantity selector when buying in the GUI.
		 * Example: ["BALLS", "HEAL"].
		 */
		public List<String> enabledCategories = new ArrayList<>();
	}
}
