package com.siickzz.economy.shop;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public record ShopItem(String category, Identifier itemId, String displayName, double buyPrice, double sellPrice, int amount, String giveCommand) {
	public Item item() {
		return Registries.ITEM.get(itemId);
	}
}
