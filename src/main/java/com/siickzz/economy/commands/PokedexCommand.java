package com.siickzz.economy.commands;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import static net.minecraft.server.command.CommandManager.literal;

public final class PokedexCommand {
	private static final String POKEDEX_ITEM_ID = "cobblemon:pokedex_blue";

	private PokedexCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
				literal("pokedex")
					.executes(PokedexCommand::open)
			);
		});
	}

	private static int open(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();

		Item pokedexItem = resolveItem(POKEDEX_ITEM_ID);
		if (pokedexItem == null) {
			player.sendMessage(Text.literal("§cPokedex indisponible: item introuvable (" + POKEDEX_ITEM_ID + ")"), false);
			return 0;
		}

		ItemStack pokedex = new ItemStack(pokedexItem);
		boolean alreadyHas = hasItem(player, pokedexItem);
		if (!alreadyHas) {
			ItemStack toGive = pokedex.copy();
			boolean inserted = player.getInventory().insertStack(toGive);
			if (!inserted && !toGive.isEmpty()) {
				player.dropItem(toGive, false);
			}
		}

		// Best-effort attempt to open by simulating a right-click use of the Pokedex item.
		ItemStack previous = player.getMainHandStack();
		try {
			player.setStackInHand(Hand.MAIN_HAND, pokedex);
			player.getMainHandStack().use(player.getWorld(), player, Hand.MAIN_HAND);
		} catch (Throwable ignored) {
			// Some Cobblemon versions only open the UI client-side; that's fine.
		} finally {
			player.setStackInHand(Hand.MAIN_HAND, previous);
		}

		if (alreadyHas) {
			player.sendMessage(Text.literal("§ePokédex: prends ton §f" + POKEDEX_ITEM_ID + " §eet fais clic droit pour l'ouvrir."), false);
		} else {
			player.sendMessage(Text.literal("§aPokédex donné (§f" + POKEDEX_ITEM_ID + "§a). Mets-le en main et fais clic droit."), false);
		}

		return 1;
	}

	private static boolean hasItem(ServerPlayerEntity player, Item item) {
		var inv = player.getInventory();
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (!stack.isEmpty() && stack.isOf(item)) return true;
		}
		return false;
	}

	private static Item resolveItem(String itemId) {
		Identifier id = Identifier.tryParse(itemId);
		if (id == null) return null;
		Item item = Registries.ITEM.get(id);
		if (item == null) return null;
		if (item == net.minecraft.item.Items.AIR) return null;
		return item;
	}
}
