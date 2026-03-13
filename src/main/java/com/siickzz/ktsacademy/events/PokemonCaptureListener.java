/*
** EPITECH PROJECT, 2026
** Economy
** File description:
** Pokemon Capture Listener class file
*/

package com.siickzz.ktsacademy.events;

import com.siickzz.ktsacademy.economy.EconomyManager;
import com.siickzz.ktsacademy.quests.QuestManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PokemonCaptureListener {
    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleEconomy");
    private static final Map<UUID, Integer> POKEDEX_RECHECK_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, String> POKEDEX_RECHECK_SPECIES = new ConcurrentHashMap<>();
    private static volatile boolean TICK_HANDLER_REGISTERED = false;

    private PokemonCaptureListener() {}

    public static void register()
    {
        ensureTickHandlerRegistered();

        boolean hooked = tryHookCobblemonEvents("POKEMON_CAPTURED");
        if (!hooked)
            hooked = tryHookCobblemonEvents("POKEMON_CAPTURE_PRE");
        if (hooked)
            LOGGER.info("[CobbleEconomy] Capture listener hooked successfully");
        else
            LOGGER.warn("[CobbleEconomy] Could not hook Cobblemon capture event; no money will be awarded on capture.");
    }

    private static void ensureTickHandlerRegistered()
    {
        if (TICK_HANDLER_REGISTERED)
            return;
        synchronized (PokemonCaptureListener.class) {
            if (TICK_HANDLER_REGISTERED)
                return;
            ServerTickEvents.END_SERVER_TICK.register(PokemonCaptureListener::onEndServerTick);
            TICK_HANDLER_REGISTERED = true;
        }
    }

    private static void onEndServerTick(MinecraftServer server)
    {
        if (POKEDEX_RECHECK_TICKS.isEmpty())
            return;
        for (var it = POKEDEX_RECHECK_TICKS.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            int left = entry.getValue() - 1;
            if (left > 0) {
                entry.setValue(left);
                continue;
            }

            UUID playerId = entry.getKey();
            it.remove();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player == null)
                continue;

            int caught = PokedexMilestoneListener.computeCaughtCountForPlayer(player);
            if (caught > 0) {
                PokedexMilestoneListener.applyPokedexUpdate(player, caught);
            } else {
                String speciesKey = POKEDEX_RECHECK_SPECIES.remove(playerId);
                if (speciesKey != null && !speciesKey.isBlank()) {
                    var profile = QuestManager.profile(player);
                    if (profile.capturedSpecies != null && profile.capturedSpecies.add(speciesKey))
                        QuestManager.onPokedexCaughtProgress(player, profile.capturedSpecies.size());
                }
            }
        }
    }

    private static void schedulePokedexRecheck(ServerPlayerEntity player, Object pokemon)
    {
        if (player == null)
            return;
        POKEDEX_RECHECK_TICKS.put(player.getUuid(), 2);

        String speciesKey = speciesKey(pokemon);
        if (speciesKey != null && !speciesKey.isBlank())
            POKEDEX_RECHECK_SPECIES.put(player.getUuid(), speciesKey);
    }

    private static boolean tryHookCobblemonEvents(String name)
    {
        return tryHookEvent("com.cobblemon.mod.common.api.events.CobblemonEvents", name);
    }

    private static boolean tryHookEvent(String eventHolderClassName, String eventFieldName)
    {
        try {
            Class<?> holder = Class.forName(eventHolderClassName);
            Object event = getStaticMember(holder, eventFieldName);
            if (event == null)
                return false;

            Method registerMethod = findRegisterMethod(event);
            Class<?> listenerType = registerMethod.getParameterTypes()[0];
            Object listener = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[]{listenerType}, new CaptureHandler());
            registerMethod.invoke(event, listener);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object getStaticMember(Class<?> holder, String name)
    {
        try {
            Field field = holder.getField(name);
            return field.get(null);
        } catch (Throwable ignored) {}
        try {
            Field field = holder.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        } catch (Throwable ignored) {}

        String getter = "get" + name;
        try {
            Method method = holder.getMethod(getter);
            return method.invoke(null);
        } catch (Throwable ignored) {}
        try {
            Method method = holder.getDeclaredMethod(getter);
            method.setAccessible(true);
            return method.invoke(null);
        } catch (Throwable ignored) {}
        return null;
    }

    private static Method findRegisterMethod(Object event) throws NoSuchMethodException
    {
        String[] candidates = {"register", "subscribe", "add", "listen"};

        for (String name : candidates) {
            for (Method method : event.getClass().getMethods()) {
                if (!method.getName().equals(name))
                    continue;
                if (method.getParameterCount() != 1)
                    continue;
                return method;
            }
        }
        for (Method method : event.getClass().getMethods()) {
            if (method.getParameterCount() != 1)
                continue;

            String n = method.getName().toLowerCase(Locale.ROOT);
            if (n.contains("subscr") || n.contains("regist") || n.contains("listen"))
                return method;
        }
        throw new NoSuchMethodException("No listener registration method found on: " + event.getClass());
    }

    private static final class CaptureHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (args == null || args.length == 0 || args[0] == null)
                return defaultReturn(method);

            Object event = args[0];
            try {
                Object playerObj = callNoArg(event, "getPlayer");
                if (!(playerObj instanceof ServerPlayerEntity player))
                    return defaultReturn(method);

                Object pokemon = callNoArg(event, "getPokemon");
                /* DISABLED */
//                double reward = calculateReward(pokemon);
//                EconomyManager.get(player).add(reward);
//                player.sendMessage(Text.literal("§a+ " + formatMoney(reward) + " ₽ §7pour la capture de §f" + pokemonName(pokemon)),false);

                QuestManager.onPokemonCaptured(player, pokemon);
                CaptureStreakManager.onPokemonCaptured(player, pokemon);
				schedulePokedexRecheck(player, pokemon);
            } catch (Throwable ignored) {}
            return defaultReturn(method);
        }

        private static Object defaultReturn(Method method)
        {
            Class<?> rt = method.getReturnType();

            if (rt == boolean.class)
                return false;
            if (rt == byte.class)
                return (byte) 0;
            if (rt == short.class)
                return (short) 0;
            if (rt == int.class)
                return 0;
            if (rt == long.class)
                return 0L;
            if (rt == float.class)
                return 0f;
            if (rt == double.class)
                return 0d;
            if (rt == char.class)
                return (char) 0;
            return null;
        }
    }

    private static Object callNoArg(Object target, String methodName)
    {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String speciesKey(Object pokemon)
    {
        if (pokemon == null)
            return null;

        Object species = callNoArg(pokemon, "getSpecies");
        Object name = callNoArg(species, "getName");
        if (name != null)
            return name.toString().trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return null;
    }

    private static String pokemonName(Object pokemon)
    {
        if (pokemon == null)
            return "?";

        Object display = callNoArg(pokemon, "getDisplayName");
        if (display instanceof Text text)
            return text.getString();
        if (display != null)
            return display.toString();

        Object species = callNoArg(pokemon, "getSpecies");
        if (species != null) {
            Object name = callNoArg(species, "getName");
            if (name != null)
                return name.toString();
        }
        return pokemon.toString();
    }

    private static double calculateReward(Object pokemon)
    {
        if (pokemon == null)
            return 0;

        Object species = callNoArg(pokemon, "getSpecies");
        boolean legendary = isTrue(callNoArg(pokemon, "isLegendary")) || isTrue(callNoArg(pokemon, "isMythical")) || isTrue(callNoArg(species, "isLegendary")) || isTrue(callNoArg(species, "isMythical")) || hasLabel(species, "legendary") || hasLabel(species, "mythical");
        String rarityKey = "common";
        Object rarity = callNoArg(pokemon, "getRarity");
        if (rarity == null && species != null)
            rarity = callNoArg(species, "getRarity");
        if (rarity != null)
            rarityKey = toKey(rarity);

        double base = 0.0;
        if (legendary || rarityKey.contains("legend") || rarityKey.contains("myth"))
            base = 1000;
        else if (rarityKey.contains("rare"))
            base = 150;
        else if (rarityKey.contains("uncommon"))
            base = 50;
        else
            base = 20;
        if (isShiny(pokemon))
            base += 200;
        return base;
    }

    private static boolean isTrue(Object v)
    {
        if (v instanceof Boolean b)
            return b;
        if (v == null)
            return false;

        String s = v.toString().trim().toLowerCase(Locale.ROOT);
        return s.equals("true");
    }

    private static boolean hasLabel(Object species, String needle)
    {
        if (species == null)
            return false;

        Object labels = callNoArg(species, "getLabels");
        if (labels instanceof Iterable<?> it) {
            for (Object o : it) {
                if (o == null)
                    continue;

                String s = o.toString().toLowerCase(Locale.ROOT);
                if (s.contains(needle))
                    return true;
            }
        }
        return false;
    }

    private static String toKey(Object rarity)
    {
        if (rarity == null)
            return "";
        try {
            Method name = rarity.getClass().getMethod("name");
            Object v = name.invoke(rarity);

            if (v != null)
                return v.toString().trim().toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {}
        return rarity.toString().trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isShiny(Object pokemon)
    {
        if (pokemon == null)
            return false;

        Object v = callNoArg(pokemon, "isShiny");
        if (v instanceof Boolean b)
            return b;
        v = callNoArg(pokemon, "getShiny");
        if (v instanceof Boolean b)
            return b;
        v = callNoArg(pokemon, "is_shiny");
        if (v instanceof Boolean b)
            return b;
        if (v != null) {
            String s = v.toString().trim().toLowerCase(Locale.ROOT);
            return s.equals("true");
        }
        return false;
    }

    private static String formatMoney(double value)
    {
        if (value == (long) value)
            return Long.toString((long) value);
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
