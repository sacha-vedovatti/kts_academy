package com.siickzz.economy.events;

import com.siickzz.economy.quests.QuestManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public final class PokemonFishingListener {
	private static final Logger LOGGER = LoggerFactory.getLogger("CobbleEconomy");
	private static volatile boolean LOGGED_HANDLER_ERROR = false;
	private static volatile boolean LOGGED_PLAYER_RESOLUTION = false;
	private static final Map<UUID, Long> LAST_FISH_COUNT_MS = new ConcurrentHashMap<>();
	private static final long FISH_DEDUP_MS = 1200L;
	private static final Map<UUID, Integer> FISH_RECHECK_TICKS = new ConcurrentHashMap<>();
	private static volatile boolean TICK_HANDLER_REGISTERED = false;

	private PokemonFishingListener() {
	}

	public static void register() {
		ensureTickHandlerRegistered();

		boolean hookedAny = false;

		// Primary: reel event (counts on actual reel timing).
		if (tryHookCobblemonEvents("POKEROD_REEL")) {
			LOGGER.info("[CobbleEconomy] Fishing listener hooked successfully (POKEROD_REEL)");
			hookedAny = true;
		}

		// Backup: some MC/Yarn versions make it hard to resolve the bobber from the player at reel time.
		// This event provides both bobber and pokemon explicitly.
		if (tryHookCobblemonEvents("BOBBER_SPAWN_POKEMON_POST")) {
			LOGGER.info("[CobbleEconomy] Fishing listener hooked successfully (BOBBER_SPAWN_POKEMON_POST)");
			hookedAny = true;
		}

		if (!hookedAny) {
			LOGGER.warn("[CobbleEconomy] Could not hook Cobblemon fishing events; fishing quests will not progress.");
		}
	}

	private static void ensureTickHandlerRegistered() {
		if (TICK_HANDLER_REGISTERED) return;
		synchronized (PokemonFishingListener.class) {
			if (TICK_HANDLER_REGISTERED) return;
			ServerTickEvents.END_SERVER_TICK.register(PokemonFishingListener::onEndServerTick);
			TICK_HANDLER_REGISTERED = true;
		}
	}

	private static void onEndServerTick(MinecraftServer server) {
		if (FISH_RECHECK_TICKS.isEmpty()) return;

		for (var it = FISH_RECHECK_TICKS.entrySet().iterator(); it.hasNext(); ) {
			var entry = it.next();
			int left = entry.getValue() - 1;
			if (left > 0) {
				entry.setValue(left);
				continue;
			}

			UUID playerId = entry.getKey();
			it.remove();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
			if (player == null) continue;

			if (shouldCountForPlayerBobber(player)) {
				QuestManager.onPokemonFished(player);
			}
		}
	}

	private static boolean tryHookCobblemonEvents(String name) {
		return tryHookEvent("com.cobblemon.mod.common.api.events.CobblemonEvents", name);
	}

	private static boolean tryHookEvent(String eventHolderClassName, String eventFieldName) {
		try {
			Class<?> holder = Class.forName(eventHolderClassName);
			Object event = getStaticMember(holder, eventFieldName);
			if (event == null) return false;

			Method registerMethod = findRegisterMethod(event);
			Class<?> listenerType = registerMethod.getParameterTypes()[0];

			Object listener = Proxy.newProxyInstance(
				listenerType.getClassLoader(),
				new Class<?>[]{listenerType},
				new FishingHandler()
			);

			registerMethod.invoke(event, listener);
			return true;
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static Object getStaticMember(Class<?> holder, String name) {
		try {
			Field field = holder.getField(name);
			return field.get(null);
		} catch (Throwable ignored) {
		}
		try {
			Field field = holder.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(null);
		} catch (Throwable ignored) {
		}

		String getter = "get" + name;
		try {
			Method method = holder.getMethod(getter);
			return method.invoke(null);
		} catch (Throwable ignored) {
		}
		try {
			Method method = holder.getDeclaredMethod(getter);
			method.setAccessible(true);
			return method.invoke(null);
		} catch (Throwable ignored) {
		}

		return null;
	}

	private static Method findRegisterMethod(Object event) throws NoSuchMethodException {
		String[] candidates = {"subscribe", "register", "add", "listen"};
		for (String name : candidates) {
			for (Method method : event.getClass().getMethods()) {
				if (!method.getName().equals(name)) continue;
				if (method.getParameterCount() != 1) continue;
				return method;
			}
		}
		for (Method method : event.getClass().getMethods()) {
			if (method.getParameterCount() != 1) continue;
			String n = method.getName().toLowerCase(java.util.Locale.ROOT);
			if (n.contains("subscr") || n.contains("regist") || n.contains("listen")) return method;
		}
		throw new NoSuchMethodException("No listener registration method found on: " + event.getClass());
	}

	private static final class FishingHandler implements InvocationHandler {
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) {
			if (args == null || args.length == 0 || args[0] == null) {
				return defaultReturn(method);
			}

			Object event = args[0];
			try {
				ServerPlayerEntity player = resolvePlayer(event);
				if (player != null) {
					scheduleFishingRecheck(player);
					// Try an immediate count as well if data is already present.
					if (shouldCountFishingEvent(event, player)) {
						QuestManager.onPokemonFished(player);
					}
				} else if (!LOGGED_PLAYER_RESOLUTION) {
					LOGGED_PLAYER_RESOLUTION = true;
					LOGGER.warn("[CobbleEconomy] Fishing event received but player could not be resolved. EventClass={}", event.getClass().getName());
					Object bobber = callNoArg(event, "getBobber");
					if (bobber != null) {
						LOGGER.warn("[CobbleEconomy] Fishing bobber class={}", bobber.getClass().getName());
					}
				}
			} catch (Throwable t) {
				if (!LOGGED_HANDLER_ERROR) {
					LOGGED_HANDLER_ERROR = true;
					LOGGER.warn("[CobbleEconomy] Fishing handler error (logged once)", t);
				}
			}

			return defaultReturn(method);
		}

		private static Object defaultReturn(Method method) {
			Class<?> rt = method.getReturnType();
			if (rt == boolean.class) return false;
			if (rt == byte.class) return (byte) 0;
			if (rt == short.class) return (short) 0;
			if (rt == int.class) return 0;
			if (rt == long.class) return 0L;
			if (rt == float.class) return 0f;
			if (rt == double.class) return 0d;
			if (rt == char.class) return (char) 0;
			return null;
		}
	}

	private static boolean shouldCountFishingEvent(Object event, ServerPlayerEntity player) {
		String eventName = event.getClass().getName().toLowerCase(Locale.ROOT);
		boolean isReel = eventName.contains("pokerodreel");
		boolean isSpawnPost = eventName.contains("bobberspawnpokemonevent$post");
		if (!isReel && !isSpawnPost) return false;

		Object bobber = resolvePokerodBobberFromEventOrPlayer(event, player);
		if (isWithinDedupWindow(player)) return false;
		if (isSpawnPost) {
			// Post event should only fire when a Pokemon is spawned as the fishing outcome.
			Object pokemon = callNoArg(event, "getPokemon");
			if (pokemon == null) return false;
			markCounted(player);
			return true;
		}
		if (!bobberHasPokemonHooked(bobber, player)) return false;
		markCounted(player);
		return true;
	}

	private static void scheduleFishingRecheck(ServerPlayerEntity player) {
		if (player == null) return;
		// Cobblemon can populate bobber caught data slightly after the reel callback.
		// Recheck after a few ticks for reliability.
		FISH_RECHECK_TICKS.put(player.getUuid(), 3);
	}

	private static boolean shouldCountForPlayerBobber(ServerPlayerEntity player) {
		if (isWithinDedupWindow(player)) return false;
		Object bobber = resolvePlayerFishingBobber(player);
		if (!bobberHasPokemonHooked(bobber, player)) return false;
		markCounted(player);
		return true;
	}

	private static boolean isWithinDedupWindow(ServerPlayerEntity player) {
		UUID playerId = player.getUuid();
		long now = System.currentTimeMillis();
		Long last = LAST_FISH_COUNT_MS.get(playerId);
		return last != null && now - last < FISH_DEDUP_MS;
	}

	private static void markCounted(ServerPlayerEntity player) {
		LAST_FISH_COUNT_MS.put(player.getUuid(), System.currentTimeMillis());
	}

	private static boolean bobberHasPokemonHooked(Object bobber, ServerPlayerEntity player) {
		if (bobber == null) return false;
		String cls = bobber.getClass().getName();
		if (!cls.contains("PokeRodFishingBobberEntity")) return false;

		// Strong signal in Cobblemon 1.7.x: planned spawn action present when a Pokemon result is pending.
		Object plannedSpawn = firstNonNull(
			callNoArg(bobber, "getPlannedSpawnAction"),
			getFieldValue(bobber, "plannedSpawnAction")
		);
		if (plannedSpawn != null) return true;

		// Best signal: typeCaught == POKEMON
		Object typeCaught = firstNonNull(
			callNoArg(bobber, "getTypeCaught"),
			getFieldValue(bobber, "typeCaught")
		);
		if (typeCaught != null) {
			String s = typeCaught.toString().toUpperCase(Locale.ROOT);
			if (s.contains("POKEMON")) return true;
		}

		// Next: hooked entity is a Cobblemon PokemonEntity
		Object hooked = firstNonNull(
			callNoArg(bobber, "getHookedEntity"),
			getFieldValue(bobber, "hookedEntity")
		);
		if (hooked instanceof Entity e) {
			if (e.getClass().getName().contains("PokemonEntity")) return true;
		}

		// Fallback: some versions keep the spawned Pokemon entity in a getter.
		Object pokemonEntity = callNoArg(bobber, "getPokemon");
		if (pokemonEntity != null) return true;

		// Last resort: check state
		Object state = firstNonNull(callNoArg(bobber, "getState"), getFieldValue(bobber, "state"));
		if (state != null) {
			String st = state.toString().toUpperCase(Locale.ROOT);
			if (st.contains("HOOKED_IN_ENTITY")) {
				// If we're hooked in entity but couldn't resolve which one, assume success.
				return true;
			}
		}

		return false;
	}

	private static Object resolvePokerodBobberFromEventOrPlayer(Object event, ServerPlayerEntity player) {
		Object directBobber = firstNonNull(
			callNoArg(event, "getBobber"),
			getFieldValue(event, "bobber")
		);
		if (directBobber != null) return directBobber;

		Object ctx = callNoArg(event, "getContext");
		Object fromCtx = firstNonNull(
			callNoArg(ctx, "getBobber"),
			callNoArg(ctx, "bobber"),
			getFieldValue(ctx, "bobber")
		);
		if (fromCtx != null) return fromCtx;
		return resolvePlayerFishingBobber(player);
	}

	private static Object resolvePlayerFishingBobber(ServerPlayerEntity player) {
		// Yarn names vary across MC versions; use reflection.
		Object bobber = firstNonNull(
			getFieldValue(player, "fishHook"),
			getFieldValue(player, "fishingBobber"),
			callNoArg(player, "getFishHook"),
			callNoArg(player, "getFishingBobber")
		);
		if (bobber != null) return bobber;

		// Last-resort: scan fields for any FishingBobberEntity instance.
		Class<?> c = player.getClass();
		while (c != null && c != Object.class) {
			try {
				for (Field f : c.getDeclaredFields()) {
					f.setAccessible(true);
					Object v = f.get(player);
					if (v instanceof FishingBobberEntity) return v;
				}
			} catch (Throwable ignored) {
				// best-effort only
			}
			c = c.getSuperclass();
		}
		return bobber;
	}

	private static ServerPlayerEntity resolvePlayer(Object event) {
		Object direct = callNoArg(event, "getPlayer");
		if (direct instanceof ServerPlayerEntity sp) return sp;

		// BobberSpawnPokemonEvent.Post has getBobber(); bobber should be a PokeRodFishingBobberEntity.
		Object bobber = callNoArg(event, "getBobber");
		if (bobber != null) {
			Object owner = firstNonNull(
				callNoArg(bobber, "getPlayerOwner"),
				callNoArg(bobber, "getOwner"),
				callNoArg(bobber, "getOwnerEntity"),
				callNoArg(bobber, "getPlayer"),
				callNoArg(bobber, "getThrower"),
				callNoArg(bobber, "getAngler"),
				getFieldValue(bobber, "player"),
				getFieldValue(bobber, "owner"),
				getFieldValue(bobber, "playerEntity"),
				getFieldValue(bobber, "thrower"),
				getFieldValue(bobber, "bobberOwner")
			);
			if (owner instanceof ServerPlayerEntity sp) return sp;
			if (owner instanceof Entity e && e instanceof ServerPlayerEntity sp2) return sp2;
			if (owner instanceof UUID uuid) {
				ServerPlayerEntity resolved = resolvePlayerFromBobberWorld(bobber, uuid);
				if (resolved != null) return resolved;
			}
			if (owner != null) {
				UUID uuid = coerceUuid(owner);
				if (uuid != null) {
					ServerPlayerEntity resolved = resolvePlayerFromBobberWorld(bobber, uuid);
					if (resolved != null) return resolved;
				}
			}
		}

		// Some spawn actions include a context with player.
		Object spawnAction = callNoArg(event, "getSpawnAction");
		Object context = firstNonNull(callNoArg(spawnAction, "getContext"), callNoArg(event, "getContext"));
		Object fromCtx = firstNonNull(
			callNoArg(context, "getPlayer"),
			callNoArg(context, "player"),
			getFieldValue(context, "player")
		);
		if (fromCtx instanceof ServerPlayerEntity sp3) return sp3;
		return null;
	}

	private static ServerPlayerEntity resolvePlayerFromBobberWorld(Object bobber, UUID uuid) {
		if (bobber instanceof Entity e) {
			if (e.getWorld() instanceof ServerWorld sw) {
				return sw.getServer().getPlayerManager().getPlayer(uuid);
			}
		}
		return null;
	}

	private static UUID coerceUuid(Object value) {
		if (value instanceof UUID u) return u;
		if (value == null) return null;
		try {
			return UUID.fromString(value.toString());
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static Object firstNonNull(Object... values) {
		if (values == null) return null;
		for (Object v : values) if (v != null) return v;
		return null;
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

	private static Object getFieldValue(Object target, String fieldName) {
		if (target == null) return null;
		try {
			Field f = target.getClass().getField(fieldName);
			return f.get(target);
		} catch (Throwable ignored) {
		}
		try {
			Field f = target.getClass().getDeclaredField(fieldName);
			f.setAccessible(true);
			return f.get(target);
		} catch (Throwable ignored) {
			return null;
		}
	}
}
