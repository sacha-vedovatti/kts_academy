package com.siickzz.ktsacademy.events;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.siickzz.ktsacademy.quests.QuestManager;
import com.siickzz.ktsacademy.quests.QuestProfile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.random.RandomGenerator;

public final class CaptureStreakManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("KTSAcademy-CaptureStreak");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final RandomGenerator RNG = RandomGenerator.getDefault();
    private static final String SPAWN_BONUS_TAG = "ktsacademy:streak_spawn_bonus";

    private static volatile boolean INITIALIZED = false;
    private static Path configPath;
    private static CaptureStreakConfig CONFIG;

    private CaptureStreakManager() {}

    public static void init()
    {
        if (INITIALIZED)
            return;
        synchronized (CaptureStreakManager.class) {
            if (INITIALIZED)
                return;

            Path configDir = FabricLoader.getInstance().getConfigDir();
            Path legacyPath = configDir.resolve("capture_streak.json");
            Path scopedPath = configDir.resolve("ktsacademy").resolve("capture_streak.json");
            configPath = Files.exists(legacyPath) ? legacyPath : scopedPath;
            if (!Files.exists(configPath))
                createDefault();
            CONFIG = load();
            LOGGER.info("[KTS Streak] Using config: {}", configPath.toAbsolutePath());
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("[KTS Streak] Config loaded. enabled={} spawnRadius={}", CONFIG.enabled, CONFIG.spawnBoostRadius);
            INITIALIZED = true;
        }
    }

    public static void onPokemonCaptured(ServerPlayerEntity player, Object pokemon) {
        if (player == null || pokemon == null)
            return;
        init();
        if (CONFIG == null || !CONFIG.enabled)
            return;

        String speciesKey = speciesKey(pokemon);
        if (speciesKey == null || speciesKey.isBlank())
            return;

        QuestProfile profile = QuestManager.profile(player);
        boolean sameSpecies = speciesKey.equals(profile.captureStreakSpecies);
        boolean changed = false;

        if (sameSpecies) {
            profile.captureStreakCount = Math.max(1, profile.captureStreakCount + 1);
            changed = true;
        } else {
            if (CONFIG.resetOnDifferentSpecies) {
                profile.captureStreakCount = 1;
                profile.captureStreakSpecies = speciesKey;
                changed = true;
            } else if (profile.captureStreakSpecies == null || profile.captureStreakSpecies.isBlank()) {
                profile.captureStreakCount = 1;
                profile.captureStreakSpecies = speciesKey;
                changed = true;
            }
        }
        if (profile.captureStreakCount >= CONFIG.minCountForMessage && sameSpecies) {
            String msg = renderMessage(CONFIG.message, pokemon, profile.captureStreakCount);

            if (msg != null && !msg.isBlank())
                player.sendMessage(Text.literal(msg), false);
        }
        if (sameSpecies && CONFIG.applyBonusesOnCapture)
            applyTierBonuses(pokemon, profile.captureStreakCount, BonusContext.CAPTURE);
        if (changed)
            QuestManager.save();
    }

    public static void onPokemonSpawned(PokemonEntity entity, ServerWorld world) {
        if (entity == null || world == null) return;
        init();
        if (CONFIG == null || !CONFIG.enabled) return;
        if (CONFIG.tiers == null || CONFIG.tiers.isEmpty()) return;

        Pokemon pokemon = entity.getPokemon();
        if (pokemon == null || !pokemon.isWild()) return;

        String speciesKey = speciesKey(pokemon);
        if (speciesKey == null || speciesKey.isBlank()) return;

        NbtCompound data = pokemon.getPersistentData();
        if (data != null && data.getBoolean(SPAWN_BONUS_TAG)) return;

        double radius = CONFIG.spawnBoostRadius;
        if (radius <= 0) return;
        double radiusSq = radius * radius;

        int bestStreak = 0;
        ServerPlayerEntity bestPlayer = null;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player == null) continue;
            if (player.squaredDistanceTo(entity) > radiusSq) continue;
            QuestProfile profile = QuestManager.profile(player);
            if (profile == null) continue;
            if (!speciesKey.equals(profile.captureStreakSpecies)) continue;
            if (profile.captureStreakCount <= 0) continue;
            if (profile.captureStreakCount > bestStreak) {
                bestStreak = profile.captureStreakCount;
                bestPlayer = player;
            }
        }
        if (bestStreak <= 0) return;

        Tier tier = bestTierFor(bestStreak, CONFIG.tiers);
        if (tier == null) return;

        if (LOGGER.isDebugEnabled()) {
            String playerName = bestPlayer != null ? bestPlayer.getName().getString() : "?";
            LOGGER.debug("[CaptureStreak] Spawn boost: species={} streak={} player={} radius={}", speciesKey, bestStreak, playerName, radius);
        }
        if (CONFIG.logSpawnBoost) {
            String playerName = bestPlayer != null ? bestPlayer.getName().getString() : "?";
            LOGGER.info("[CaptureStreak] Applied spawn boost: species={} streak={} player={} radius={}", speciesKey, bestStreak, playerName, radius);
        }

        boolean appearanceChanged = applyTierBonuses(pokemon, bestStreak, BonusContext.SPAWN_POST);
        if (appearanceChanged) {
            trySyncPokemonEntity(entity);
            if (CONFIG.logSpawnBoost) {
                LOGGER.info("[CaptureStreak] Appearance updated for {}", speciesKey);
            }
        }
        if (data != null) data.putBoolean(SPAWN_BONUS_TAG, true);
    }

    public static void applyTierBonusesForPlayer(ServerPlayerEntity player, Object pokemon) {
        applyTierBonusesForPlayer(player, pokemon, BonusContext.SPAWN_PRE);
    }

    public static void applyTierBonusesForPlayer(ServerPlayerEntity player, Object pokemon, BonusContext context) {
        if (player == null || pokemon == null) return;
        init();
        if (CONFIG == null || !CONFIG.enabled) return;

        String speciesKey = speciesKey(pokemon);
        if (speciesKey == null || speciesKey.isBlank()) return;

        QuestProfile profile = QuestManager.profile(player);
        if (profile == null) return;
        if (!speciesKey.equals(profile.captureStreakSpecies)) return;
        if (profile.captureStreakCount <= 0) return;

        applyTierBonuses(pokemon, profile.captureStreakCount, context);
    }

    private static int maxStreakForSpecies(MinecraftServer server, String speciesKey) {
        if (server == null || speciesKey == null) return 0;
        int max = 0;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            QuestProfile profile = QuestManager.profile(player);
            if (profile == null) continue;
            if (!speciesKey.equals(profile.captureStreakSpecies)) continue;
            max = Math.max(max, profile.captureStreakCount);
        }
        return max;
    }

    private static boolean applyTierBonuses(Object pokemon, int streakCount, BonusContext context)
    {
        if (CONFIG == null || CONFIG.tiers == null || CONFIG.tiers.isEmpty())
            return false;

        Tier tier = bestTierFor(streakCount, CONFIG.tiers);
        if (tier == null)
            return false;

        boolean allowShiny = context != BonusContext.SPAWN_POST || CONFIG.applyShinyOnSpawnPost;
        boolean allowRadiant = context != BonusContext.SPAWN_POST || CONFIG.applyRadiantOnSpawnPost;

        boolean appearanceChanged = false;
        boolean isRadiant = isRadiant(pokemon);
        boolean isShiny = isShiny(pokemon);

        if (isRadiant) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[CaptureStreak] Pokemon already radiant; forcing shiny + 3 IVs. streak={}", streakCount);
            }
            if (allowRadiant) {
                appearanceChanged = setRadiant(pokemon, true) || appearanceChanged;
            }
            if (allowShiny) {
                appearanceChanged = setShiny(pokemon, true) || appearanceChanged;
            }
            setRandomPerfectIvs(pokemon, 3, CONFIG.perfectIvValue);
            return appearanceChanged;
        }
        if (allowRadiant && rollPercent(tier.radiantChance)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[CaptureStreak] Radiant roll success. streak={} chance={}", streakCount, tier.radiantChance);
            }
            appearanceChanged = setRadiant(pokemon, true) || appearanceChanged;
            if (allowShiny) {
                appearanceChanged = setShiny(pokemon, true) || appearanceChanged;
            }
            setRandomPerfectIvs(pokemon, 3, CONFIG.perfectIvValue);
            return appearanceChanged;
        }
        if (allowShiny && !isShiny && rollPercent(tier.shinyChance)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[CaptureStreak] Shiny roll success. streak={} chance={}", streakCount, tier.shinyChance);
            }
            appearanceChanged = setShiny(pokemon, true) || appearanceChanged;
        }
        if (tier.perfectIvCount > 0 && rollPercent(tier.perfectIvChance)) {
            int count = Math.min(6, tier.perfectIvCount);
            if (count > 0) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[CaptureStreak] Perfect IV roll success. streak={} count={} chance={}", streakCount, count, tier.perfectIvChance);
                }
                setRandomPerfectIvs(pokemon, count, CONFIG.perfectIvValue);
            }
        }
        if (tier.ivBoostMultiplier != null && tier.ivBoostMultiplier > 1.0 && rollPercent(tier.ivBoostChance)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[CaptureStreak] IV boost roll success. streak={} mult={} chance={}", streakCount, tier.ivBoostMultiplier, tier.ivBoostChance);
            }
            applyIvBoost(pokemon, tier.ivBoostMultiplier, CONFIG.perfectIvValue);
        }
        return appearanceChanged;
    }

    private static boolean rollPercent(Double chance)
    {
        if (chance == null || chance <= 0)
            return false;

        double clamped = Math.min(100.0, chance);
        return RNG.nextDouble() * 100.0 < clamped;
    }

    private static Tier bestTierFor(int streakCount, List<Tier> tiers)
    {
        if (tiers == null || tiers.isEmpty())
            return null;
        return tiers.stream().filter(t -> t != null && t.minStreak > 0 && streakCount >= t.minStreak).max(Comparator.comparingInt(t -> t.minStreak)).orElse(null);
    }

    private static String renderMessage(String template, Object pokemon, int count)
    {
        if (template == null)
            return null;

        String species = pokemonName(pokemon);
        return template.replace("{species}", species).replace("{count}", Integer.toString(count));
    }

    private static String speciesKey(Object pokemon)
    {
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

        Object species = callNoArg(pokemon, "getSpecies");
        Object name = callNoArg(species, "getName");
        if (name != null)
            return name.toString();

        Object n2 = callNoArg(pokemon, "getName");
        return n2 != null ? n2.toString() : "?";
    }

    private static boolean isShiny(Object pokemon)
    {
        if (pokemon instanceof Pokemon p) {
            return p.getShiny();
        }
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

    private static boolean isRadiant(Object pokemon) {
        if (pokemon instanceof Pokemon p) {
            if (hasRadiantAspect(p)) return true;
        }
        Object v = callNoArg(pokemon, "isRadiant");
        if (v instanceof Boolean b)
            return b;

        v = callNoArg(pokemon, "getRadiant");
        if (v instanceof Boolean b)
            return b;

        v = callNoArg(pokemon, "is_radiant");
        if (v instanceof Boolean b)
            return b;

        if (v != null) {
            String s = v.toString().trim().toLowerCase(Locale.ROOT);
            return s.equals("true");
        }
        return false;
    }

    private static boolean setShiny(Object pokemon, boolean value)
    {
        if (pokemon instanceof Pokemon p) {
            p.setShiny(value);
            return true;
        }
        return callSetter(pokemon, value, "setShiny", "set_isShiny", "setIsShiny");
    }

    private static boolean setRadiant(Object pokemon, boolean value) {
        if (pokemon instanceof Pokemon p) {
            if (callSetter(p, value, "setRadiant", "setIsRadiant", "set_radiant", "setSparkly"))
                return true;
            return setRadiantAspect(p, value);
        }
        return callSetter(pokemon, value,
            "setRadiant",
            "setIsRadiant",
            "set_radiant",
            "setSparkly"
        );
    }

    private static boolean hasRadiantAspect(Pokemon pokemon) {
        if (pokemon == null)
            return false;

        List<String> keys = radiantAspectKeys();
        Set<String> aspects = pokemon.getAspects();
        if (aspects != null) {
            for (String key : keys) {
                if (key != null && aspects.contains(key))
                    return true;
            }
        }

        Set<String> forced = pokemon.getForcedAspects();
        if (forced != null) {
            for (String key : keys) {
                if (key != null && forced.contains(key))
                    return true;
            }
        }
        return false;
    }

    private static boolean setRadiantAspect(Pokemon pokemon, boolean value)
    {
        if (pokemon == null)
            return false;

        List<String> keys = radiantApplyAspects();
        Set<String> forced = pokemon.getForcedAspects();
        if (forced == null)
            forced = new HashSet<>();
        else
            forced = new HashSet<>(forced);

        boolean changed = false;
        for (String key : keys) {
            if (key == null || key.isBlank())
                continue;
            changed = value ? forced.add(key) : forced.remove(key) || changed;
        }
        if (changed)
            pokemon.setForcedAspects(forced);

        Set<String> aspects = pokemon.getAspects();
        if (aspects != null) {
            try {
                for (String key : keys) {
                    if (key == null || key.isBlank())
                        continue;
                    if (value)
                        aspects.add(key);
                    else
                        aspects.remove(key);
                }
            } catch (Throwable ignored) {}
        }
        return changed;
    }

    private static List<String> radiantApplyAspects()
    {
        List<String> keys = new ArrayList<>(radiantAspectKeys());

        if (CONFIG != null && CONFIG.radiantExtraAspects != null)
            keys.addAll(CONFIG.radiantExtraAspects);
        return keys;
    }

    private static List<String> radiantAspectKeys()
    {
        if (CONFIG == null || CONFIG.radiantAspects == null || CONFIG.radiantAspects.isEmpty())
            return List.of("radiant", "radiant=radiant");
        return CONFIG.radiantAspects;
    }

    private static boolean callSetter(Object target, boolean value, String... names)
    {
        if (target == null || names == null)
            return false;
        for (String name : names) {
            if (name == null || name.isBlank())
                continue;
            try {
                Method m = target.getClass().getMethod(name, boolean.class);
                m.invoke(target, value);
                return true;
            } catch (Throwable ignored) {}
            try {
                Method m = target.getClass().getMethod(name, Boolean.class);
                m.invoke(target, value);
                return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private static void setRandomPerfectIvs(Object pokemon, int count, int perfectIvValue)
    {
        if (pokemon == null || count <= 0)
            return;
        if (pokemon instanceof Pokemon p) {
            setRandomPerfectIvs(p, count, perfectIvValue);
            return;
        }

        Object ivs = callNoArg(pokemon, "getIvs");
        if (ivs == null)
            ivs = callNoArg(pokemon, "getIVs");
        if (ivs == null)
            ivs = callNoArg(pokemon, "ivs");
        if (ivs == null)
            return;

        Method setter = findIvsSetter(ivs);
        if (setter == null)
            return;

        Class<?> statType = setter.getParameterTypes()[0];
        List<Object> stats = resolveStatValues(statType);
        if (stats.isEmpty())
            return;

        Collections.shuffle(stats, new java.util.Random());
        int limit = Math.min(count, stats.size());
        for (int i = 0; i < limit; i++) {
            Object stat = stats.get(i);
            try {
                if (setter.getParameterTypes()[1] == int.class)
                    setter.invoke(ivs, stat, perfectIvValue);
                else
                    setter.invoke(ivs, stat, Integer.valueOf(perfectIvValue));
            } catch (Throwable ignored) {}
        }
    }

    private static void setRandomPerfectIvs(Pokemon pokemon, int count, int perfectIvValue)
    {
        if (pokemon == null || count <= 0)
            return;

        List<Object> stats = resolveCobblemonStats();
        if (stats.isEmpty())
            return;
        Collections.shuffle(stats, new java.util.Random());

        Method setter = findPokemonIvSetter(pokemon);
        if (setter == null)
            return;

        int limit = Math.min(count, stats.size());
        for (int i = 0; i < limit; i++) {
            try {
                setter.invoke(pokemon, stats.get(i), perfectIvValue);
            } catch (Throwable ignored) {}
        }
    }

    private static void applyIvBoost(Object pokemon, double multiplier, int maxIv)
    {
        if (pokemon == null || multiplier <= 1.0)
            return;
        if (pokemon instanceof Pokemon p) {
            applyIvBoost(p, multiplier, maxIv);
            return;
        }

        Object ivs = callNoArg(pokemon, "getIvs");
        if (ivs == null)
            ivs = callNoArg(pokemon, "getIVs");
        if (ivs == null)
            ivs = callNoArg(pokemon, "ivs");

        List<Object> stats = resolveCobblemonStats();
        if (stats.isEmpty())
            return;

        Method setter = findIvsSetter(ivs);
        Method getter = findIvsGetter(ivs);
        if (setter == null || getter == null)
            return;
        for (Object stat : stats) {
            Integer current = readIv(getter, ivs, stat);
            if (current == null)
                continue;

            int boosted = boostIv(current, multiplier, maxIv);
            try {
                if (setter.getParameterTypes()[1] == int.class)
                    setter.invoke(ivs, stat, boosted);
                else
                    setter.invoke(ivs, stat, Integer.valueOf(boosted));
            } catch (Throwable ignored) {}
        }
    }

    private static void applyIvBoost(Pokemon pokemon, double multiplier, int maxIv)
    {
        List<Object> stats = resolveCobblemonStats();
        if (stats.isEmpty())
            return;

        Method setter = findPokemonIvSetter(pokemon);
        Method getter = findPokemonIvGetter(pokemon);
        if (setter == null || getter == null)
            return;
        for (Object stat : stats) {
            Integer current = readIv(getter, pokemon, stat);
            if (current == null)
                continue;

            int boosted = boostIv(current, multiplier, maxIv);
            try {
                setter.invoke(pokemon, stat, boosted);
            } catch (Throwable ignored) {}
        }
    }

    private static Integer readIv(Method getter, Object target, Object stat)
    {
        try {
            Object v = getter.invoke(target, stat);

            if (v instanceof Integer i)
                return i;
            if (v instanceof Number n)
                return n.intValue();
        } catch (Throwable ignored) {}
        return null;
    }

    private static int boostIv(int current, double multiplier, int maxIv)
    {
        int boosted = (int) Math.ceil(current * multiplier);

        if (boosted > maxIv)
            boosted = maxIv;
        if (boosted < current)
            boosted = current;
        return boosted;
    }

    private static Method findPokemonIvGetter(Pokemon pokemon)
    {
        for (Method m : pokemon.getClass().getMethods()) {
            if (!m.getName().equals("getIV"))
                continue;
            if (m.getParameterCount() != 1)
                continue;

            Class<?> rt = m.getReturnType();
            if (rt != int.class && rt != Integer.class)
                continue;
            return m;
        }
        return null;
    }

    private static Method findPokemonIvSetter(Pokemon pokemon)
    {
        for (Method m : pokemon.getClass().getMethods()) {
            if (!m.getName().equals("setIV"))
                continue;
            if (m.getParameterCount() != 2)
                continue;

            Class<?>[] types = m.getParameterTypes();
            if (types[1] != int.class && types[1] != Integer.class)
                continue;
            return m;
        }
        return null;
    }

    private static Method findIvsGetter(Object ivs)
    {
        if (ivs == null)
            return null;
        for (Method m : ivs.getClass().getMethods()) {
            if (m.getParameterCount() != 1)
                continue;

            Class<?> rt = m.getReturnType();
            if (rt != int.class && rt != Integer.class)
                continue;

            String name = m.getName().toLowerCase(Locale.ROOT);
            if (name.contains("get"))
                return m;
        }
        return null;
    }

    private static Method findIvsSetter(Object ivs)
    {
        if (ivs == null)
            return null;
        for (Method m : ivs.getClass().getMethods()) {
            if (m.getParameterCount() != 2)
                continue;

            Class<?>[] types = m.getParameterTypes();
            if (!(types[1] == int.class || types[1] == Integer.class))
                continue;

            String name = m.getName().toLowerCase(Locale.ROOT);
            if (name.contains("set"))
                return m;
        }
        return null;
    }

    private static List<Object> resolveStatValues(Class<?> statType)
    {
        if (statType == null)
            return List.of();

        Object[] constants = statType.getEnumConstants();
        if (constants != null && constants.length > 0) {
            List<Object> list = new ArrayList<>();

            for (Object c : constants) {
                if (c != null)
                    list.add(c);
            }
            return list;
        }
        if (statType == String.class) {
            List<Object> list = new ArrayList<>();

            list.add("hp");
            list.add("attack");
            list.add("defense");
            list.add("special_attack");
            list.add("special_defense");
            list.add("speed");
            return list;
        }
        return List.of();
    }

    private static List<Object> resolveCobblemonStats()
    {
        List<Object> list = new ArrayList<>();

        try {
            Class<?> statClass = Class.forName("com.cobblemon.mod.common.api.pokemon.stats.Stat");
            Object[] constants = statClass.getEnumConstants();

            if (constants != null) {
                for (Object c : constants) {
                    if (c != null)
                        list.add(c);
                }
            }
        } catch (Throwable ignored) {}
        if (list.isEmpty()) {
            try {
                Class<?> statsClass = Class.forName("com.cobblemon.mod.common.api.pokemon.stats.Stats");

                for (var field : statsClass.getFields()) {
                    Object v = field.get(null);

                    if (v != null)
                        list.add(v);
                }
            } catch (Throwable ignored) {}
        }
        if (!list.isEmpty()) {
            List<Object> filtered = new ArrayList<>();

            for (Object stat : list) {
                String name = stat.toString().toLowerCase(Locale.ROOT);

                if (name.contains("hp") || name.contains("attack") || name.contains("defense") || name.contains("special_attack") || name.contains("special_defense") || name.contains("speed"))
                    filtered.add(stat);
            }
            return filtered.isEmpty() ? list : filtered;
        }
        return list;
    }

    private static void trySyncPokemonEntity(PokemonEntity entity)
    {
        if (entity == null)
            return;

        String[] candidates = {"syncToClients", "syncToTracking", "sendUpdate", "markDirty", "sync"};
        for (String name : candidates) {
            try {
                Method m = entity.getClass().getMethod(name);

                m.invoke(entity);
                return;
            } catch (Throwable ignored) {}
        }
    }

    private static Object callNoArg(Object target, String methodName)
    {
        if (target == null)
            return null;
        try {
            Method method = target.getClass().getMethod(methodName);

            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static CaptureStreakConfig load()
    {
        try (Reader r = Files.newBufferedReader(configPath)) {
            CaptureStreakConfig cfg = GSON.fromJson(r, CaptureStreakConfig.class);

            if (cfg == null)
                return defaultConfig();
            if (cfg.tiers == null)
                cfg.tiers = new ArrayList<>();
            if (cfg.radiantAspects == null || cfg.radiantAspects.isEmpty()) {
                cfg.radiantAspects = new ArrayList<>();
                cfg.radiantAspects.add("radiant");
                cfg.radiantAspects.add("radiant=radiant");
            }
            if (cfg.radiantExtraAspects == null || cfg.radiantExtraAspects.isEmpty()) {
                cfg.radiantExtraAspects = new ArrayList<>();
                cfg.radiantExtraAspects.add("min_perfect_ivs=3");
            }
            return cfg;
        } catch (IOException e) {
            LOGGER.warn("[KTSAcademy-CaptureStreak] Cannot read capture_streak.json: {}", e.getMessage());
            return defaultConfig();
        }
    }

    private static void createDefault()
    {
        try {
            Files.createDirectories(configPath.getParent());
            CaptureStreakConfig def = defaultConfig();

            try (Writer w = Files.newBufferedWriter(configPath)) {
                GSON.toJson(def, w);
            }
            LOGGER.info("[KTSAcademy-CaptureStreak] Default capture_streak.json created.");
        } catch (IOException e) {
            LOGGER.warn("[KTSAcademy-CaptureStreak] Cannot create capture_streak.json: {}", e.getMessage());
        }
    }

    private static CaptureStreakConfig defaultConfig()
    {
        CaptureStreakConfig cfg = new CaptureStreakConfig();

        cfg.enabled = true;
        cfg.resetOnDifferentSpecies = true;
        cfg.minCountForMessage = 2;
        cfg.message = "\u00A76[Chaine] \u00A7f{species} \u00A77x{count}";
        cfg.applyBonusesOnCapture = false;
        cfg.applyShinyOnSpawnPost = false;
        cfg.applyRadiantOnSpawnPost = false;
        cfg.logSpawnBoost = false;
        cfg.perfectIvValue = 31;
        cfg.spawnBoostRadius = 64.0;
        cfg.tiers = new ArrayList<>();
        cfg.tiers.add(new Tier(5, 0.2, 0.05, 1, 15.0, 1.1, 30.0));
        cfg.tiers.add(new Tier(10, 0.6, 0.15, 2, 25.0, 1.2, 40.0));
        cfg.tiers.add(new Tier(20, 1.5, 0.5, 3, 40.0, 1.3, 50.0));
        cfg.radiantAspects = new ArrayList<>();
        cfg.radiantAspects.add("radiant");
        cfg.radiantAspects.add("radiant=radiant");
        cfg.radiantExtraAspects = new ArrayList<>();
        cfg.radiantExtraAspects.add("min_perfect_ivs=3");
        return cfg;
    }

    public static enum BonusContext {
        CAPTURE,
        SPAWN_PRE,
        SPAWN_POST
    }

    public static final class CaptureStreakConfig
    {
        public boolean enabled = true;
        public boolean resetOnDifferentSpecies = true;
        public int minCountForMessage = 2;
        public String message = "\u00A76[Chaine] \u00A7f{species} \u00A77x{count}";
        public boolean applyBonusesOnCapture = false;
        public boolean applyShinyOnSpawnPost = false;
        public boolean applyRadiantOnSpawnPost = false;
        public boolean logSpawnBoost = false;
        public int perfectIvValue = 31;
        public double spawnBoostRadius = 64.0;
        public List<Tier> tiers = new ArrayList<>();
        public List<String> radiantAspects = new ArrayList<>();
        public List<String> radiantExtraAspects = new ArrayList<>();
    }

    public static final class Tier
    {
        public int minStreak;
        public Double shinyChance;
        public Double radiantChance;
        public int perfectIvCount;
        public Double perfectIvChance;
        public Double ivBoostMultiplier;
        public Double ivBoostChance;

        public Tier() {}

        public Tier(int minStreak, Double shinyChance, Double radiantChance)
        {
            this.minStreak = minStreak;
            this.shinyChance = shinyChance;
            this.radiantChance = radiantChance;
            this.perfectIvCount = 0;
            this.perfectIvChance = 0.0;
            this.ivBoostMultiplier = 1.0;
            this.ivBoostChance = 0.0;
        }

        public Tier(int minStreak, Double shinyChance, Double radiantChance, int perfectIvCount, Double perfectIvChance)
        {
            this.minStreak = minStreak;
            this.shinyChance = shinyChance;
            this.radiantChance = radiantChance;
            this.perfectIvCount = perfectIvCount;
            this.perfectIvChance = perfectIvChance;
            this.ivBoostMultiplier = 1.0;
            this.ivBoostChance = 0.0;
        }

        public Tier(int minStreak, Double shinyChance, Double radiantChance, int perfectIvCount, Double perfectIvChance,
                    Double ivBoostMultiplier, Double ivBoostChance)
        {
            this.minStreak = minStreak;
            this.shinyChance = shinyChance;
            this.radiantChance = radiantChance;
            this.perfectIvCount = perfectIvCount;
            this.perfectIvChance = perfectIvChance;
            this.ivBoostMultiplier = ivBoostMultiplier;
            this.ivBoostChance = ivBoostChance;
        }
    }
}
