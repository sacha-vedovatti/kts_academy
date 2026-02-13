package com.siickzz.ktsacademy.events;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Shared reward computation for Cobblemon Pokemon objects.
 * Uses reflection to stay compatible across Cobblemon versions.
 */
final class PokemonRewardUtil {
	private PokemonRewardUtil() {
	}

	/**
	 * Reward for defeating a Pokemon (battle/kill).
	 * Designed so legendaries/mythicals never pay "tiny" amounts.
	 */
	static double rewardForDefeat(Object pokemon) {
		int level = readLevel(pokemon);

		boolean legendary = isLegendaryOrMythical(pokemon);
		String rarityKey = rarityKey(pokemon);
		boolean shiny = isShiny(pokemon);

		double base;
		if (legendary || rarityKey.contains("legend") || rarityKey.contains("myth")) {
			base = 200.0;
		} else if (rarityKey.contains("ultra") || rarityKey.contains("rare")) {
			base = 30.0;
		} else if (rarityKey.contains("uncommon")) {
			base = 12.0;
		} else {
			base = 5.0;
		}

		int effectiveLevel = Math.max(1, level);
		double levelFactor = 0.5 + (Math.min(100, effectiveLevel) / 50.0);
		double reward = base * levelFactor;

		if (shiny) {
			reward += 50.0;
		}

		// Floor to avoid odd tiny values from missing data.
		return Math.max(5.0, Math.floor(reward));
	}

	static int readLevel(Object pokemon) {
		if (pokemon == null) return -1;
		Object v = callNoArg(pokemon, "getLevel");
		if (v instanceof Number n) return n.intValue();
		v = callNoArg(pokemon, "level");
		if (v instanceof Number n) return n.intValue();
		return -1;
	}

	private static boolean isLegendaryOrMythical(Object pokemon) {
		if (pokemon == null) return false;

		Object species = callNoArg(pokemon, "getSpecies");

		return isTrue(callNoArg(pokemon, "isLegendary"))
			|| isTrue(callNoArg(pokemon, "isMythical"))
			|| isTrue(callNoArg(species, "isLegendary"))
			|| isTrue(callNoArg(species, "isMythical"))
			|| hasLabel(species, "legendary")
			|| hasLabel(species, "mythical");
	}

	private static String rarityKey(Object pokemon) {
		if (pokemon == null) return "common";
		Object species = callNoArg(pokemon, "getSpecies");

		Object rarity = callNoArg(pokemon, "getRarity");
		if (rarity == null && species != null) {
			rarity = callNoArg(species, "getRarity");
		}
		if (rarity == null) return "common";
		return toKey(rarity);
	}

	private static boolean isShiny(Object pokemon) {
		if (pokemon == null) return false;
		Object v = callNoArg(pokemon, "isShiny");
		if (v instanceof Boolean b) return b;
		v = callNoArg(pokemon, "getShiny");
		if (v instanceof Boolean b) return b;
		v = callNoArg(pokemon, "is_shiny");
		if (v instanceof Boolean b) return b;
		if (v != null) {
			String s = v.toString().trim().toLowerCase(Locale.ROOT);
			return s.equals("true");
		}
		return false;
	}

	private static boolean isTrue(Object v) {
		if (v instanceof Boolean b) return b;
		if (v == null) return false;
		String s = v.toString().trim().toLowerCase(Locale.ROOT);
		return s.equals("true");
	}

	private static boolean hasLabel(Object species, String needle) {
		if (species == null) return false;
		Object labels = callNoArg(species, "getLabels");
		if (labels instanceof Iterable<?> it) {
			for (Object o : it) {
				if (o == null) continue;
				String s = o.toString().toLowerCase(Locale.ROOT);
				if (s.contains(needle)) return true;
			}
		}
		return false;
	}

	private static String toKey(Object rarity) {
		if (rarity == null) return "";
		try {
			Method name = rarity.getClass().getMethod("name");
			Object v = name.invoke(rarity);
			if (v != null) {
				return v.toString().trim().toLowerCase(Locale.ROOT);
			}
		} catch (Throwable ignored) {
		}
		return rarity.toString().trim().toLowerCase(Locale.ROOT);
	}

	private static Object callNoArg(Object target, String methodName) {
		if (target == null) return null;
		try {
			Method method = target.getClass().getMethod(methodName);
			return method.invoke(target);
		} catch (Throwable ignored) {
			return null;
		}
	}
}
