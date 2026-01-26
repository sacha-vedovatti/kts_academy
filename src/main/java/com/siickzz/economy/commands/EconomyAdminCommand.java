package com.siickzz.economy.commands;

import com.mojang.brigadier.context.CommandContext;
import com.siickzz.economy.economy.EconomyManager;
import com.siickzz.economy.quests.QuestManager;
import com.siickzz.economy.shop.ShopRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

public final class EconomyAdminCommand {
	private EconomyAdminCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
				literal("economy")
					.requires(src -> src.hasPermissionLevel(3))
					.then(literal("reload").executes(EconomyAdminCommand::reload))
			);
		});
	}

	private static int reload(CommandContext<ServerCommandSource> ctx) {
		// Save first to avoid losing in-memory progress/balances.
		EconomyManager.save();
		QuestManager.save();
		ShopRegistry.reload();
		EconomyManager.reload();
		QuestManager.reload();
		ctx.getSource().sendFeedback(() -> Text.literal("§aEconomy: reload OK (shop + accounts + quests)."), true);
		return 1;
	}
}
