package com.siickzz.ktsacademy.commands;

import com.mojang.brigadier.context.CommandContext;
import com.siickzz.ktsacademy.economy.EconomyManager;
import com.siickzz.ktsacademy.quests.QuestManager;
import com.siickzz.ktsacademy.shop.ShopRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;

public final class EconomyAdminCommand
{
	private EconomyAdminCommand()
	{}

	public static void register()
	{
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
				literal("economy")
					.requires(src -> src.hasPermissionLevel(3))
					.then(literal("reload").executes(EconomyAdminCommand::reload))
					.then(
						literal("quests")
							.then(
								literal("reset")
									.executes(EconomyAdminCommand::resetAllQuests)
									.then(argument("player", EntityArgumentType.player()).executes(EconomyAdminCommand::resetPlayerQuests))
							)
							.then(
								literal("resetconfig")
									.executes(EconomyAdminCommand::resetQuestConfig)
							)
					)
			);
		});
	}

	private static int reload(CommandContext<ServerCommandSource> ctx)
	{
		EconomyManager.save();
		QuestManager.save();
		ShopRegistry.reload();
		EconomyManager.reload();
		QuestManager.reload();
		ctx.getSource().sendFeedback(() -> Text.literal("§aEconomy: reload OK (shop + accounts + quests)."), true);
		return 1;
	}

	private static int resetAllQuests(CommandContext<ServerCommandSource> ctx)
	{
		QuestManager.resetAllProgress();
		ctx.getSource().sendFeedback(() -> Text.literal("§eQuêtes: progression reset pour tous les joueurs."), true);
		return 1;
	}

	private static int resetPlayerQuests(CommandContext<ServerCommandSource> ctx)
	{
		try {
			var player = EntityArgumentType.getPlayer(ctx, "player");

			QuestManager.resetProgress(player.getUuid());
			ctx.getSource().sendFeedback(() -> Text.literal("§eQuêtes: progression reset pour " + player.getName().getString() + "."), true);
			return 1;
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			ctx.getSource().sendError(Text.literal("§cJoueur introuvable."));
			return 0;
		}
	}

	private static int resetQuestConfig(CommandContext<ServerCommandSource> ctx)
	{
		QuestManager.resetConfigToDefaults();
		ctx.getSource().sendFeedback(() -> Text.literal("§eQuêtes: config quests.json régénérée (défauts)."), true);
		return 1;
	}
}
