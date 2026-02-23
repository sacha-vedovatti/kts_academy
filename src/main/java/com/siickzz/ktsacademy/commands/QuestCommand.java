package com.siickzz.ktsacademy.commands;

import com.mojang.brigadier.context.CommandContext;
import com.siickzz.ktsacademy.quests.QuestManager;
import com.siickzz.ktsacademy.quests.gui.QuestGui;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

public final class QuestCommand {
	private QuestCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
				literal("quests")
					.executes(QuestCommand::open)
					.then(
						literal("reload")
							.requires(src -> src.hasPermissionLevel(3))
							.executes(QuestCommand::reload)
					)
			);
		});
	}

	private static int open(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
		QuestGui.open(player);
		return 1;
	}

	private static int reload(CommandContext<ServerCommandSource> ctx) {
		QuestManager.reload();
		ctx.getSource().sendFeedback(() -> Text.literal("§aQuêtes: reload OK."), true);
		return 1;
	}
}
