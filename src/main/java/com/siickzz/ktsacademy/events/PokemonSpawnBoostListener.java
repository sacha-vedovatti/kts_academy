package com.siickzz.ktsacademy.events;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;

public final class PokemonSpawnBoostListener {
    private static volatile boolean REGISTERED = false;
    private static volatile boolean TICK_HANDLER_REGISTERED = false;
    private static final java.util.Map<java.util.UUID, Integer> WILD_RECHECK_TICKS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, net.minecraft.server.world.ServerWorld> WILD_RECHECK_WORLDS = new java.util.concurrent.ConcurrentHashMap<>();

    private PokemonSpawnBoostListener() {
    }

    public static void register() {
        if (REGISTERED) return;
        synchronized (PokemonSpawnBoostListener.class) {
            if (REGISTERED) return;
            ensureTickHandlerRegistered();
            ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
                if (!(world instanceof ServerWorld serverWorld)) return;
                if (!(entity instanceof PokemonEntity pokemonEntity)) return;
                if (pokemonEntity.getPokemon() == null) return;
                if (pokemonEntity.getPokemon().isWild()) {
                    CaptureStreakManager.onPokemonSpawned(pokemonEntity, serverWorld);
                } else {
                    scheduleWildRecheck(pokemonEntity, serverWorld);
                }
            });
            tryHookCobblemonEvent("BOBBER_SPAWN_POKEMON_MODIFY");
            tryHookCobblemonEvent("BOBBER_SPAWN_POKEMON_POST");
            tryHookCobblemonEvent("BAIT_SPAWN_POKEMON_MODIFY");
            tryHookCobblemonEvent("BAIT_SPAWN_POKEMON_POST");
            tryHookCobblemonEvent("HATCH_EGG_POST");
            REGISTERED = true;
        }
    }

    private static void ensureTickHandlerRegistered() {
        if (TICK_HANDLER_REGISTERED) return;
        synchronized (PokemonSpawnBoostListener.class) {
            if (TICK_HANDLER_REGISTERED) return;
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(PokemonSpawnBoostListener::onEndServerTick);
            TICK_HANDLER_REGISTERED = true;
        }
    }

    private static void scheduleWildRecheck(PokemonEntity entity, ServerWorld world) {
        if (entity == null || world == null) return;
        WILD_RECHECK_TICKS.put(entity.getUuid(), 3);
        WILD_RECHECK_WORLDS.put(entity.getUuid(), world);
    }

    private static void onEndServerTick(net.minecraft.server.MinecraftServer server) {
        if (WILD_RECHECK_TICKS.isEmpty()) return;
        for (var it = WILD_RECHECK_TICKS.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            int left = entry.getValue() - 1;
            if (left > 0) {
                entry.setValue(left);
                continue;
            }
            java.util.UUID id = entry.getKey();
            it.remove();
            ServerWorld world = WILD_RECHECK_WORLDS.remove(id);
            if (world == null) continue;
            var entity = world.getEntity(id);
            if (!(entity instanceof PokemonEntity pokemonEntity)) continue;
            if (pokemonEntity.getPokemon() == null) continue;
            if (!pokemonEntity.getPokemon().isWild()) continue;
            CaptureStreakManager.onPokemonSpawned(pokemonEntity, world);
        }
    }

    private static boolean tryHookCobblemonEvent(String name) {
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
                new EventHandler()
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
        String[] candidates = {"register", "subscribe", "add", "listen"};
        for (String name : candidates) {
            for (Method method : event.getClass().getMethods()) {
                if (!method.getName().equals(name)) continue;
                if (method.getParameterCount() != 1) continue;
                return method;
            }
        }
        for (Method method : event.getClass().getMethods()) {
            if (method.getParameterCount() != 1) continue;
            String n = method.getName().toLowerCase();
            if (n.contains("subscr") || n.contains("regist") || n.contains("listen")) {
                return method;
            }
        }
        throw new NoSuchMethodException("No listener registration method found on: " + event.getClass());
    }

    private static final class EventHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (args == null || args.length == 0 || args[0] == null) {
                return defaultReturn(method);
            }
            Object event = args[0];
            try {
                ServerPlayerEntity player = asPlayer(callNoArg(event, "getPlayer"));
                Object pokemon = resolvePokemon(event);
                if (player != null && pokemon != null) {
                    CaptureStreakManager.applyTierBonusesForPlayer(player, pokemon, getContext());
                }
            } catch (Throwable ignored) {
            }
            return defaultReturn(method);
        }

        private static CaptureStreakManager.BonusContext getContext() {
            return CaptureStreakManager.BonusContext.SPAWN_PRE;
        }

        private static Object resolvePokemon(Object event) {
            Object pokemon = callNoArg(event, "getPokemon");
            if (pokemon != null) return pokemon;
            Object entity = callNoArg(event, "getPokemonEntity");
            if (entity instanceof PokemonEntity pokemonEntity) {
                return pokemonEntity.getPokemon();
            }
            Object entity2 = callNoArg(event, "getEntity");
            if (entity2 instanceof PokemonEntity pokemonEntity) {
                return pokemonEntity.getPokemon();
            }
            return null;
        }

        private static ServerPlayerEntity asPlayer(Object candidate) {
            return candidate instanceof ServerPlayerEntity player ? player : null;
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
