/*
** EPITECH PROJECT, 2026
** Economy
** File description:
** Ore Mine Listener class file
*/

package com.siickzz.ktsacademy.events;

import com.siickzz.ktsacademy.quests.QuestManager;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OreMineListener {
	private static final Logger LOGGER = LoggerFactory.getLogger("CobbleEconomy");

	private OreMineListener() {
	}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (world == null || world.isClient) {
				return;
			}
			if (!(player instanceof ServerPlayerEntity serverPlayer)) {
				return;
			}

			String oreKey = oreKey(state);
			if (oreKey == null) {
				return;
			}
			QuestManager.onOreMined(serverPlayer, oreKey);
		});
		LOGGER.info("[CobbleEconomy] Ore mine listener registered");
	}

	private static String oreKey(BlockState state) {
		if (state == null) return null;

		if (state.isOf(Blocks.DIAMOND_ORE) || state.isOf(Blocks.DEEPSLATE_DIAMOND_ORE)) {
			return "diamond";
		}
		if (state.isOf(Blocks.IRON_ORE) || state.isOf(Blocks.DEEPSLATE_IRON_ORE)) {
			return "iron";
		}
		if (state.isOf(Blocks.GOLD_ORE) || state.isOf(Blocks.DEEPSLATE_GOLD_ORE)) {
			return "gold";
		}
		return null;
	}
}
