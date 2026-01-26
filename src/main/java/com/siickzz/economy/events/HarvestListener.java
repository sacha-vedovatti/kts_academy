/*
** EPITECH PROJECT, 2026
** Economy
** File description:
** Harvest Listener class file
*/

package com.siickzz.economy.events;

import com.siickzz.economy.quests.QuestManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HarvestListener {
	private static final Logger LOGGER = LoggerFactory.getLogger("CobbleEconomy");
	private static volatile boolean LOGGED_COBBLEMON_HOOK_ERROR = false;
	private static volatile boolean LOGGED_APRICORN_EVENT_RECEIVED = false;
	private static final Map<UUID, Integer> WINDOW_TICKS = new ConcurrentHashMap<>();
	private static final Map<UUID, Map<String, Integer>> BASELINE_COUNTS = new ConcurrentHashMap<>();
	private static final Map<UUID, Boolean> WINDOW_SAW_DELTA = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> PENDING_APRICORN_FALLBACK = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_APRICORN_HARVEST_MS = new ConcurrentHashMap<>();
	private static final long HARVEST_DEDUP_MS = 1500L;
	private static volatile boolean TICK_HANDLER_REGISTERED = false;
	private static final int HARVEST_WINDOW_TICKS = 200;

	private HarvestListener() {
	}

	public static void register() {
		ensureTickHandlerRegistered();

		if (tryHookCobblemonApricornHarvest()) {
			LOGGER.info("[CobbleEconomy] Harvest listener hooked successfully (APRICORN_HARVESTED)");
		}

		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (world == null || world.isClient) return ActionResult.PASS;
			if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;

			BlockState state = world.getBlockState(pos);
			if (!looksLikeApricornBlock(state)) return ActionResult.PASS;

			scheduleRecheck(serverPlayer);
			return ActionResult.PASS;
		});

		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world == null || world.isClient) return ActionResult.PASS;
			if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
			if (!(hitResult instanceof BlockHitResult bhr)) return ActionResult.PASS;

			BlockPos pos = bhr.getBlockPos();
			BlockState state = world.getBlockState(pos);
			if (!looksLikeApricornBlock(state)) return ActionResult.PASS;

			scheduleRecheck(serverPlayer);
			return ActionResult.PASS;
		});

		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (world == null || world.isClient) return;
			if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
			if (!looksLikeApricornBlock(state)) return;

			scheduleRecheck(serverPlayer);
		});

		LOGGER.info("[CobbleEconomy] Harvest listener registered");
	}

	private static boolean tryHookCobblemonApricornHarvest() {
		return tryHookEvent("com.cobblemon.mod.common.api.events.CobblemonEvents", "APRICORN_HARVESTED");
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
				new ApricornHarvestHandler()
			);

			registerMethod.invoke(event, listener);
			return true;
		} catch (Throwable t) {
			if (!LOGGED_COBBLEMON_HOOK_ERROR) {
				LOGGED_COBBLEMON_HOOK_ERROR = true;
				LOGGER.warn("[CobbleEconomy] Could not hook Cobblemon APRICORN_HARVESTED event", t);
			}
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

	private static final class ApricornHarvestHandler implements InvocationHandler {
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) {
			Object event = (args == null || args.length == 0) ? null : args[0];
			try {
				if (!LOGGED_APRICORN_EVENT_RECEIVED && event != null) {
					LOGGED_APRICORN_EVENT_RECEIVED = true;
					LOGGER.info("[CobbleEconomy] Received Cobblemon ApricornHarvestEvent: {}", event.getClass().getName());
				}
				Object playerObj = callNoArg(event, "getPlayer");
				if (playerObj instanceof ServerPlayerEntity player) {
					// Best-effort: count harvest immediately from the apricorn type.
					String itemId = resolveApricornItemId(event);
					if (itemId != null && !itemId.isBlank()) {
						LAST_APRICORN_HARVEST_MS.put(player.getUuid(), System.currentTimeMillis());
						QuestManager.onHarvested(player, itemId, 1);
					} else {
						// Fallback: start/refresh the window; some packs insert the item directly.
						scheduleRecheck(player, true);
						PENDING_APRICORN_FALLBACK.merge(player.getUuid(), 1, Integer::sum);
					}
				}
			} catch (Throwable ignored) {
				// best-effort only
			}
			return defaultReturn(method);
		}

		private static String resolveApricornItemId(Object event) {
			Object apricorn = callNoArg(event, "getApricorn");
			Object itemObj = firstNonNull(
				callNoArg(apricorn, "item"),
				callNoArg(apricorn, "getItem")
			);
			if (itemObj instanceof Item item) {
				Identifier id = Registries.ITEM.getId(item);
				return id == null ? null : id.toString();
			}
			// As a last resort, return a generic target that will match *apricorn*.
			return "apricorn";
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
			// Kotlin event observables often expect kotlin.Unit
			try {
				Class<?> unit = Class.forName("kotlin.Unit");
				if (rt == unit || rt == Object.class) {
					Field f = unit.getField("INSTANCE");
					return f.get(null);
				}
			} catch (Throwable ignored) {
			}
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

	private static void ensureTickHandlerRegistered() {
		if (TICK_HANDLER_REGISTERED) return;
		synchronized (HarvestListener.class) {
			if (TICK_HANDLER_REGISTERED) return;
			ServerTickEvents.END_SERVER_TICK.register(HarvestListener::onEndServerTick);
			TICK_HANDLER_REGISTERED = true;
		}
	}

	private static void scheduleRecheck(ServerPlayerEntity player) {
		scheduleRecheck(player, false);
	}

	private static void scheduleRecheck(ServerPlayerEntity player, boolean resetBaseline) {
		UUID id = player.getUuid();
		if (resetBaseline || !BASELINE_COUNTS.containsKey(id)) {
			BASELINE_COUNTS.put(id, countApricornItems(player));
		}
		WINDOW_SAW_DELTA.put(id, Boolean.FALSE);
		// Apricorns can drop as item entities and be picked up a bit later.
		// Keep a short window where we track inventory deltas.
		WINDOW_TICKS.merge(id, HARVEST_WINDOW_TICKS, Math::max);
	}

	public static void onApricornPickedUp(ServerPlayerEntity player, String itemId, int amount) {
		if (player == null) return;
		if (amount <= 0) return;

		UUID id = player.getUuid();
		if (shouldSkipApricornPickup(player)) return;
		WINDOW_SAW_DELTA.put(id, Boolean.TRUE);
		Integer pending = PENDING_APRICORN_FALLBACK.get(id);
		if (pending != null && pending > 0) {
			int next = pending - amount;
			if (next <= 0) {
				PENDING_APRICORN_FALLBACK.remove(id);
			} else {
				PENDING_APRICORN_FALLBACK.put(id, next);
			}
		}

		QuestManager.onHarvested(player, itemId, amount);
	}

	public static boolean shouldSkipApricornPickup(ServerPlayerEntity player) {
		if (player == null) return false;
		Long last = LAST_APRICORN_HARVEST_MS.get(player.getUuid());
		if (last == null) return false;
		return System.currentTimeMillis() - last < HARVEST_DEDUP_MS;
	}

	private static void onEndServerTick(MinecraftServer server) {
		if (WINDOW_TICKS.isEmpty()) return;

		for (var it = WINDOW_TICKS.entrySet().iterator(); it.hasNext(); ) {
			var entry = it.next();
			UUID playerId = entry.getKey();
			int left = entry.getValue();

			ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
			if (player == null) {
				it.remove();
				BASELINE_COUNTS.remove(playerId);
				WINDOW_SAW_DELTA.remove(playerId);
				continue;
			}

			Map<String, Integer> before = BASELINE_COUNTS.get(playerId);
			if (before == null) before = Map.of();
			Map<String, Integer> after = countApricornItems(player);

			// compute deltas since last tick we checked
			int totalApricornDelta = 0;
			for (Map.Entry<String, Integer> e : after.entrySet()) {
				String itemId = e.getKey();
				int newCount = e.getValue();
				int oldCount = before.getOrDefault(itemId, 0);
				int delta = newCount - oldCount;
				if (delta > 0) {
					WINDOW_SAW_DELTA.put(playerId, Boolean.TRUE);
					totalApricornDelta += delta;
					QuestManager.onHarvested(player, itemId, delta);
				}
			}

			// If we got any apricorn delta, consume pending fallbacks proportionally.
			if (totalApricornDelta > 0) {
				Integer pending = PENDING_APRICORN_FALLBACK.get(playerId);
				if (pending != null && pending > 0) {
					int next = pending - totalApricornDelta;
					if (next <= 0) {
						PENDING_APRICORN_FALLBACK.remove(playerId);
					} else {
						PENDING_APRICORN_FALLBACK.put(playerId, next);
					}
				}
			}

			BASELINE_COUNTS.put(playerId, after);
			left -= 1;
			if (left <= 0) {
				boolean sawDelta = Boolean.TRUE.equals(WINDOW_SAW_DELTA.remove(playerId));
				Integer pending = PENDING_APRICORN_FALLBACK.remove(playerId);
				if (pending != null && pending > 0) {
					// Fallback: event fired but inventory delta wasn't observed (timing / direct insertion).
					QuestManager.onHarvested(player, "apricorn", pending);
				}
				if (!sawDelta) {
					LOGGER.debug("[CobbleEconomy] Harvest window ended with no apricorn delta for player={}", player.getName().getString());
				}
				it.remove();
				BASELINE_COUNTS.remove(playerId);
			} else {
				entry.setValue(left);
			}
		}
	}

	private static boolean looksLikeApricornBlock(BlockState state) {
		if (state == null) return false;
		Identifier id = Registries.BLOCK.getId(state.getBlock());
		if (id == null) return false;
		String path = id.getPath().toLowerCase(Locale.ROOT);
		return path.contains("apricorn") || path.contains("noigrume");
	}

	private static Map<String, Integer> countApricornItems(ServerPlayerEntity player) {
		Map<String, Integer> counts = new HashMap<>();
		if (player == null) return counts;

		for (int i = 0; i < player.getInventory().size(); i++) {
			ItemStack stack = player.getInventory().getStack(i);
			if (stack == null || stack.isEmpty()) continue;
			Identifier id = Registries.ITEM.getId(stack.getItem());
			if (id == null) continue;
			String path = id.getPath().toLowerCase(Locale.ROOT);
			if (!(path.contains("apricorn") || path.contains("noigrume"))) continue;

			String key = id.toString();
			counts.merge(key, stack.getCount(), Integer::sum);
		}
		return counts;
	}
}
