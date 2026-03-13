package com.siickzz.ktsacademy.quests;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class QuestManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("KTSAcademy-Quests");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STORAGE_TYPE = new TypeToken<Map<String, QuestProfile>>() {}.getType();

    private static final Map<String, QuestDef> QUESTS = new LinkedHashMap<>();
    private static final Map<String, QuestCategory> CATEGORIES = new LinkedHashMap<>();
    private static final Map<UUID, QuestProfile> PROFILES = new ConcurrentHashMap<>();
    private static final Map<String, String> LEGENDARY_ICON_BY_SPECIES = new HashMap<>();

    private static volatile boolean INITIALIZED  = false;
    private static Path questsFile;
    private static Path progressFile;
    private static volatile QuestConfig.TierDropsConfig TIER_DROPS = null;

    private static final Set<String> LOGGED_BAD_DROP_IDS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final java.util.random.RandomGenerator DROP_RNG = java.util.random.RandomGenerator.getDefault();

    private static final Map<UUID, Long> LAST_HARVEST_LOG_MS = new ConcurrentHashMap<>();
    private static final long HARVEST_LOG_DEBOUNCE = 2000L;

    public record QuestCategory(String name, String displayName, String iconItemId) {}

    private QuestManager() {}

    public static void init()
    {
        if (INITIALIZED)
            return;
        synchronized (QuestManager.class) {
            if (INITIALIZED)
                return;
            Path configDir = FabricLoader.getInstance().getConfigDir().resolve("ktsacademy");
            questsFile   = configDir.resolve("quests.json");
            progressFile = configDir.resolve("quests_progress.json");
            try {
                Files.createDirectories(configDir);
            } catch (IOException ignored) {}
            if (!Files.exists(questsFile))
                writeDefaultConfig(questsFile);
            reload();
            loadProgress();
            INITIALIZED = true;
        }
    }

    public static void reload()
    {
        synchronized (QuestManager.class) {
            loadConfig();
        }
    }

    public static List<QuestCategory> categories()
    {
        init();
        return CATEGORIES.values().stream().sorted(Comparator.comparing(QuestCategory::displayName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public static QuestCategory category(String name)
    {
        init();
        return CATEGORIES.get(normalizeCategory(name));
    }

    public static List<QuestDef> questsByCategory(String category)
    {
        init();
        String cat = normalizeCategory(category);

        return QUESTS.values().stream().filter(q -> Objects.equals(q.category(), cat)).sorted(Comparator.comparing(QuestDef::title, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public static QuestDef quest(String id)
    {
        init();
        return QUESTS.get(id);
    }

    public static QuestProfile profile(ServerPlayerEntity player)
    {
        init();
        return PROFILES.computeIfAbsent(player.getUuid(), id -> new QuestProfile());
    }

    public static QuestProgress progress(ServerPlayerEntity player, String questId)
    {
        init();
        return profile(player).quests.computeIfAbsent(questId, k -> new QuestProgress());
    }

    public static String legendaryIconItemIdForSpecies(String speciesKey)
    {
        init();
        return LEGENDARY_ICON_BY_SPECIES.get(normalizeSpeciesKey(speciesKey));
    }

    public static int effectiveGoal(QuestDef quest, QuestProgress progress)
    {
        if (quest == null)
            return 1;
        if (quest.isTiered() && progress != null) {
            int idx = Math.max(0, Math.min(progress.tier, quest.tiers().size() - 1));
            return Math.max(1, quest.tiers().get(idx).goal());
        }
        return Math.max(1, quest.goal());
    }

    public static double effectiveReward(QuestDef quest, QuestProgress progress)
    {
        if (quest == null)
            return 0;
        if (quest.isTiered() && progress != null) {
            int idx = Math.max(0, Math.min(progress.tier, quest.tiers().size() - 1));
            return Math.max(0, quest.tiers().get(idx).reward());
        }
        return Math.max(0, quest.reward());
    }

    public static String effectiveTitle(QuestDef quest, QuestProgress progress)
    {
        if (quest == null)
            return "";
        if (quest.isTiered() && progress != null) {
            int idx = Math.max(0, Math.min(progress.tier, quest.tiers().size() - 1));
            return quest.title() + " §8(" + (idx + 1) + "/" + quest.tiers().size() + ")";
        }
        return quest.title();
    }

    public static String effectiveDescription(QuestDef quest, QuestProgress progress)
    {
        if (quest == null)
            return "";
        return quest.description() == null ? "" : quest.description();
    }

    public static void onPokemonCaptured(ServerPlayerEntity player, Object pokemon) {
        init();
        String speciesKey = normalizeSpeciesKey(extractSpeciesKey(pokemon));
        boolean isShiny = isPokemonShiny(pokemon);

        QuestProfile profile = profile(player);
        boolean changed = false;
        if (profile.capturedSpecies != null && profile.capturedSpecies.add(speciesKey)) {
            onPokedexCaughtProgress(player, profile.capturedSpecies.size());
            changed = true;
        }
        for (QuestDef quest : QUESTS.values()) {
            QuestProgress qp = profile.quests.computeIfAbsent(quest.id(), k -> new QuestProgress());
            if (qp.claimed && !quest.isTiered())
                continue;
            if (qp.claimed && quest.isTiered() && qp.tier >= quest.tiers().size() - 1 && qp.claimed)
                continue;

            int goalNow = effectiveGoal(quest, qp);
            switch (quest.type()) {
                case CAPTURE_ANY -> {
                    if (qp.progress < goalNow) {
                        int before = qp.progress;
                        qp.progress++;
                        changed = true;
                        if (before < goalNow && qp.progress >= goalNow)
                            onQuestCompleted(player, quest, qp);
                    }
                }
                case CAPTURE_SHINY_ANY -> {
                    if (isShiny && qp.progress < goalNow) {
                        int before = qp.progress;
                        qp.progress++;
                        changed = true;
                        if (before < goalNow && qp.progress >= goalNow)
                            onQuestCompleted(player, quest, qp);
                    }
                }
                case CAPTURE_SPECIES -> {
                    String target = normalizeSpeciesKey(quest.target());
                    if (!target.isBlank() && target.equals(speciesKey) && qp.progress < goalNow) {
                        int before = qp.progress;
                        qp.progress = goalNow;
                        changed = true;
                        if (before < goalNow)
                            onQuestCompleted(player, quest, qp);
                    }
                }
                default -> {}
            }
        }
        if (changed)
            save();
    }

    public static void onBattleWon(ServerPlayerEntity player)
    {
        init();
        incrementQuests(player, QuestDef.QuestType.BATTLE_WIN_ANY, 1);
    }

    public static void onTradeCompleted(ServerPlayerEntity player)
    {
        init();
        incrementQuests(player, QuestDef.QuestType.TRADE_ANY, 1);
    }

    public static void onPokemonFished(ServerPlayerEntity player)
    {
        init();
        incrementQuests(player, QuestDef.QuestType.FISH_POKEMON_ANY, 1);
    }

    public static void onShopBought(ServerPlayerEntity player, int count)
    {
        init();
        if (count > 0)
            incrementQuests(player, QuestDef.QuestType.SHOP_BUY_ANY, count);
    }

    public static void onShopSold(ServerPlayerEntity player, int count)
    {
        init();
        if (count > 0)
            incrementQuests(player, QuestDef.QuestType.SHOP_SELL_ANY, count);
    }

    public static void onOreMined(ServerPlayerEntity player, String oreKey)
    {
        init();
        if (player == null)
            return;

        String mined = normalizeOreKey(oreKey);
        if (mined.isBlank())
            return;

        QuestProfile profile = profile(player);
        boolean changed = false;
        for (QuestDef quest : QUESTS.values()) {
            if (quest.type() != QuestDef.QuestType.MINE_ORE)
                continue;
            if (!normalizeOreKey(quest.target()).equals(mined))
                continue;

            QuestProgress qp = profile.quests.computeIfAbsent(quest.id(), k -> new QuestProgress());
            if (qp.claimed)
                continue;

            int goalNow = effectiveGoal(quest, qp);
            if (qp.progress < goalNow) {
                int before = qp.progress;
                qp.progress = Math.min(goalNow, qp.progress + 1);
                changed = true;
                if (before < goalNow && qp.progress >= goalNow)
                    onQuestCompleted(player, quest, qp);
            }
        }
        if (changed)
            save();
    }

    public static void onHarvested(ServerPlayerEntity player, String itemId, int amount)
    {
        init();
        if (player == null || amount <= 0)
            return;

        String harvested = normalizeItemId(itemId);
        QuestProfile profile = profile(player);
        boolean changed = false;
        int matched = 0;
        for (QuestDef quest : QUESTS.values()) {
            if (quest.type() != QuestDef.QuestType.HARVEST_ITEM)
                continue;
            if (!targetMatchesItem(normalizeItemTarget(quest.target()), harvested))
                continue;
            matched++;

            QuestProgress qp = profile.quests.computeIfAbsent(quest.id(), k -> new QuestProgress());
            if (qp.claimed)
                continue;

            int goalNow = effectiveGoal(quest, qp);
            if (qp.progress < goalNow) {
                int before = qp.progress;
                qp.progress = Math.min(goalNow, qp.progress + amount);
                changed = true;
                if (before < goalNow && qp.progress >= goalNow)
                    onQuestCompleted(player, quest, qp);
            }
        }
        if (matched == 0) {
            long now = System.currentTimeMillis();
            Long last = LAST_HARVEST_LOG_MS.get(player.getUuid());

            if (last == null || now - last > HARVEST_LOG_DEBOUNCE) {
                LAST_HARVEST_LOG_MS.put(player.getUuid(), now);
                LOGGER.debug("[KTSAcademy-Quests] Aucune quête HARVEST_ITEM ne correspond à '{}'", harvested);
            }
        }
        if (changed)
            save();
    }

    public static void onPokedexCaughtProgress(ServerPlayerEntity player, int caughtCount)
    {
        init();
        if (player == null || caughtCount <= 0)
            return;

        QuestProfile profile = profile(player);
        boolean changed = false;
        for (QuestDef quest : QUESTS.values()) {
            if (quest.type() != QuestDef.QuestType.POKEDEX_CAUGHT)
                continue;

            QuestProgress qp = profile.quests.computeIfAbsent(quest.id(), k -> new QuestProgress());
            if (qp.claimed)
                continue;

            int goalNow = effectiveGoal(quest, qp);
            int next = Math.max(qp.progress, caughtCount);
            if (next != qp.progress) {
                int before = qp.progress;
                qp.progress = next;
                changed = true;
                if (before < goalNow && qp.progress >= goalNow)
                    onQuestCompleted(player, quest, qp);
            }
        }
        if (changed)
            save();
    }

    // =========================================================================
    // Réclamer une récompense — utilise la commande Cobblemon Economy
    // =========================================================================

    public static boolean tryClaim(ServerPlayerEntity player, String questId)
    {
        init();

        QuestDef quest = quest(questId);
        if (quest == null)
            return false;

        QuestProfile profile = profile(player);
        QuestProgress qp = profile.quests.computeIfAbsent(questId, k -> new QuestProgress());
        if (qp.claimed)
            return false;

        int goalNow = effectiveGoal(quest, qp);
        if (qp.progress < goalNow)
            return false;

        double rewardNow = effectiveReward(quest, qp);
        String titleNow  = effectiveTitle(quest, qp);
        giveMoneyViaCobblemonEconomy(player, rewardNow);
        qp.claimed = true;
        player.sendMessage(Text.literal("§aRécompense réclamée : §e+" + formatMoney(rewardNow) + " $ §7(" + titleNow + ")"), false);
        if (quest.isTiered()) {
            int maxTier = quest.tiers().size() - 1;
            if (qp.tier < maxTier) {
                qp.tier++;
                qp.claimed = false;
            }
        }
        tryRollTierDrops(player, quest, qp.tier);
        save();
        return true;
    }

    private static void giveMoneyViaCobblemonEconomy(ServerPlayerEntity player, double amount)
    {
        if (player == null || amount <= 0)
            return;

        MinecraftServer server = player.getServer();
        if (server == null)
            return;

        String playerName = player.getName().getString();
        long rounded = Math.round(amount);
        String command = "balance " + playerName + " add " + rounded;
        try {
            ServerCommandSource source = server.getCommandSource();
            server.getCommandManager().executeWithPrefix(source, command);
        } catch (Exception e) {
            LOGGER.error("[KTSAcademy-Quests] Échec de la commande '{}': {}", command, e.getMessage());
        }
    }

    public static void save()
    {
        if (progressFile == null)
            return;

        Map<String, QuestProfile> storage = new HashMap<>();
        for (var e : PROFILES.entrySet())
            storage.put(e.getKey().toString(), e.getValue());
        try (BufferedWriter writer = Files.newBufferedWriter(progressFile, StandardCharsets.UTF_8)) {
            GSON.toJson(storage, STORAGE_TYPE, writer);
        } catch (IOException ignored) {}
    }

    public static void resetAllProgress()
    {
        init();
        PROFILES.clear();
        try {
            if (progressFile != null)
                Files.deleteIfExists(progressFile);
        } catch (IOException ignored) {}
        LOGGER.warn("[KTSAcademy-Quests] Progression de TOUS les joueurs réinitialisée.");
    }

    public static void resetProgress(UUID playerId)
    {
        init();
        if (playerId == null)
            return;
        PROFILES.remove(playerId);
        save();
    }

    public static void resetConfigToDefaults()
    {
        init();
        if (questsFile == null)
            return;
        writeDefaultConfig(questsFile);
        reload();
    }

    private static void loadConfig()
    {
        QUESTS.clear();
        CATEGORIES.clear();
        LEGENDARY_ICON_BY_SPECIES.clear();

        QuestConfig cfg = null;
        try (BufferedReader reader = Files.newBufferedReader(questsFile, StandardCharsets.UTF_8)) {
            cfg = GSON.fromJson(reader, QuestConfig.class);
        } catch (Exception e) {
            LOGGER.warn("[KTSAcademy-Quests] Impossible de lire quests.json : {}", e.getMessage());
        }
        if (cfg == null)
            cfg = defaultConfig();
        TIER_DROPS = cfg.tierDrops;
        if (cfg.categories != null) {
            for (QuestConfig.QuestCategoryConfig cat : cfg.categories) {
                if (cat == null || cat.name == null)
                    continue;

                String name = normalizeCategory(cat.name);
                if (name.isBlank())
                    continue;

                String display = (cat.displayName != null && !cat.displayName.isBlank()) ? cat.displayName : cat.name;
                CATEGORIES.put(name, new QuestCategory(name, display, cat.iconItemId));
            }
        }
        if (cfg.legendaryCategory != null && cfg.legendaryCategory.enabled) {
            QuestConfig.LegendaryCategoryConfig leg = cfg.legendaryCategory;
            if (leg.speciesIcons != null) {
                for (var e : leg.speciesIcons.entrySet()) {
                    if (e == null || e.getKey() == null || e.getValue() == null)
                        continue;
                    LEGENDARY_ICON_BY_SPECIES.put(normalizeSpeciesKey(e.getKey()), e.getValue().trim());
                }
            }

            String catName = normalizeCategory(leg.category);
            CATEGORIES.putIfAbsent(catName, new QuestCategory(catName, leg.displayName != null ? leg.displayName : "Légendaires", leg.iconItemId));

            List<String> species = leg.species != null ? leg.species : List.of();
            for (String s : species) {
                String key = normalizeSpeciesKey(s);
                if (key.isBlank())
                    continue;

                String pretty = prettySpeciesName(s);
                String icon = LEGENDARY_ICON_BY_SPECIES.getOrDefault(key, leg.iconItemId != null ? leg.iconItemId : "minecraft:nether_star");
                addQuest(new QuestDef("legendary_capture_" + key, catName, QuestDef.QuestType.CAPTURE_SPECIES, key, icon, 1, leg.reward, "Capturer " + pretty, "Capture " + pretty + ".", null));
            }
        }

        if (cfg.quests != null) {
            for (QuestConfig.QuestDefConfig q : cfg.quests) {
                if (q == null || q.id == null || q.id.isBlank())
                    continue;

                String category = normalizeCategory(q.category);
                if (category.isBlank())
                    category = "GENERAL";
                CATEGORIES.putIfAbsent(category, new QuestCategory(category, category, null));

                QuestDef.QuestType type = QuestDef.QuestType.parse(q.type);
                String title = q.title != null ? q.title : q.id;
                String desc  = q.description != null ? q.description : "";
                List<QuestDef.QuestTier> tiers = null;
                if (q.tiers != null && !q.tiers.isEmpty()) {
                    tiers = new ArrayList<>();
                    for (QuestConfig.QuestTierConfig t : q.tiers) {
                        if (t == null)
                            continue;

                        int tg = t.goal != null ? Math.max(1, t.goal) : 1;
                        double tr = t.reward != null ? Math.max(0, t.reward) : 0;
                        tiers.add(new QuestDef.QuestTier(tg, tr));
                    }
                    if (tiers.isEmpty())
                        tiers = null;
                }

                int goal = (tiers == null) ? (q.goal != null ? Math.max(1, q.goal) : 1) : 1;
                double reward = (tiers == null) ? (q.reward != null ? Math.max(0, q.reward) : 0) : 0;
                addQuest(new QuestDef(q.id, category, type, q.target, q.iconItemId, goal, reward, title, desc, tiers));
            }
        }
        LOGGER.info("[KTSAcademy-Quests] {} quêtes chargées dans {} catégories.", QUESTS.size(), CATEGORIES.size());
    }

    private static void loadProgress()
    {
        if (progressFile == null || !Files.exists(progressFile))
            return;
        try (BufferedReader reader = Files.newBufferedReader(progressFile, StandardCharsets.UTF_8)) {
            Map<String, QuestProfile> storage = GSON.fromJson(reader, STORAGE_TYPE);

            if (storage == null)
                return;
            for (var e : storage.entrySet()) {
                try {
                    UUID id = UUID.fromString(e.getKey());
                    QuestProfile profile = e.getValue() != null ? e.getValue() : new QuestProfile();

                    if (profile.quests == null)
                        profile.quests = new HashMap<>();
                    if (profile.capturedSpecies == null)
                        profile.capturedSpecies = new HashSet<>();
                    PROFILES.put(id, profile);
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (IOException ignored) {}
    }

    private static void writeDefaultConfig(Path path)
    {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8))
        {
            GSON.toJson(defaultConfig(), writer);
            LOGGER.info("[KTSAcademy-Quests] Config par défaut écrite dans {}", path);
        } catch (IOException e) {
            LOGGER.warn("[KTSAcademy-Quests] Impossible d'écrire la config par défaut : {}", e.getMessage());
        }
    }

    private static void incrementQuests(ServerPlayerEntity player, QuestDef.QuestType type, int amount)
    {
        if (player == null)
            return;

        QuestProfile profile = profile(player);
        boolean changed = false;
        for (QuestDef quest : QUESTS.values()) {
            if (quest.type() != type)
                continue;

            QuestProgress qp = profile.quests.computeIfAbsent(quest.id(), k -> new QuestProgress());
            if (qp.claimed)
                continue;

            int goalNow = effectiveGoal(quest, qp);
            if (qp.progress < goalNow) {
                int before = qp.progress;
                qp.progress = Math.min(goalNow, qp.progress + amount);
                changed = true;
                if (before < goalNow && qp.progress >= goalNow)
                    onQuestCompleted(player, quest, qp);
            }
        }
        if (changed)
            save();
    }

    private static void onQuestCompleted(ServerPlayerEntity player, QuestDef quest, QuestProgress qp)
    {
        String title = effectiveTitle(quest, qp);
        double reward = effectiveReward(quest, qp);
        MinecraftServer server = player.getServer();
        Text msg = Text.literal("§e§lQuests » §e" + player.getName().getString()  + " §7a complété : §b" + title + " §7(§e+" + formatMoney(reward) + " $§7)");

        if (server != null)
            server.getPlayerManager().broadcast(msg, false);
        else
            player.sendMessage(Text.literal("§d§lQuests terminée » §7Vous avez terminé la quête \"§a" + title + "§7\" (§f/quests§7)."), false);
    }

    private static void tryRollTierDrops(ServerPlayerEntity player, QuestDef quest, int tierIdx)
    {
        QuestConfig.TierDropsConfig cfg = TIER_DROPS;
        if (cfg == null || !cfg.enabled || cfg.drops == null || cfg.drops.isEmpty())
            return;

        int rolls = cfg.rollsPerTier != null ? Math.max(0, cfg.rollsPerTier) : 1;
        for (int r = 0; r < rolls; r++) {
            for (QuestConfig.TierDropEntryConfig entry : cfg.drops) {
                if (entry == null || entry.itemId == null || entry.itemId.isBlank())
                    continue;

                double chance = normalizeChance(entry.chance);
                if (chance <= 0)
                    continue;
                if (chance < 1.0 && DROP_RNG.nextDouble() >= chance)
                    continue;

                Item item = resolveItem(entry.itemId.trim());
                if (item == null) {
                    if (LOGGED_BAD_DROP_IDS.add(entry.itemId.trim().toLowerCase(Locale.ROOT)))
                        LOGGER.warn("[KTSAcademy-Quests] tierDrops: item inconnu '{}'", entry.itemId);
                    continue;
                }

                int min = entry.min != null ? Math.max(1, entry.min) : 1;
                int max = entry.max != null ? Math.max(min, entry.max) : min;
                int count = min == max ? min : min + DROP_RNG.nextInt(max - min + 1);
                ItemStack stack = new ItemStack(item, count);
                String itemName = stack.getName().getString();
                if (!player.getInventory().insertStack(stack) && cfg.dropOnGroundIfFull && !stack.isEmpty())
                    player.dropItem(stack, false);
                if (cfg.notifyPlayer)
                    player.sendMessage(Text.literal("§d§lQuests » §7Bonus : §f" + itemName + " §7x" + count), false);
            }
        }
    }

    private static void addQuest(QuestDef def)
    {
        if (def == null || def.id() == null || def.id().isBlank())
            return;
        QUESTS.put(def.id(), def);
    }

    private static QuestConfig defaultConfig()
    {
        QuestConfig cfg = new QuestConfig();
        cfg.categories = new ArrayList<>();
        cfg.categories.add(cat("POKEMON",    "Pokemon",      "cobblemon:poke_ball"));
        cfg.categories.add(cat("FARM",       "Farm",         "minecraft:diamond_hoe"));
        cfg.categories.add(cat("SHOP",       "Shop",         "cobblemon:relic_coin_pouch"));
        cfg.categories.add(cat("LEGENDARIES","Légendaires",  "cobblemon:master_ball"));

        QuestConfig.LegendaryCategoryConfig leg = new QuestConfig.LegendaryCategoryConfig();
        leg.enabled     = true;
        leg.category    = "LEGENDARIES";
        leg.displayName = "Légendaires";
        leg.iconItemId  = "cobblemon:master_ball";
        leg.reward      = 2500.0;
        leg.species     = List.of("mewtwo", "mew", "lugia", "ho_oh", "celebi");
        leg.speciesIcons = new LinkedHashMap<>();
        leg.speciesIcons.put("mewtwo", "cobblemon:cloning_cable");
        leg.speciesIcons.put("lugia",  "cobblemon:silver_wing");
        leg.speciesIcons.put("ho_oh",  "cobblemon:rainbow_wing");
        cfg.legendaryCategory = leg;

        cfg.quests = new ArrayList<>();
        cfg.quests.add(tieredQuest("capture_10", "POKEMON", "CAPTURE_ANY",
            "cobblemon:poke_ball", null, "Collectionneur", "Capture des Pokémon.",
            tiers(200,10, 500,50, 1000,100, 2500,250, 5000,500, 10000,1000)));
        cfg.quests.add(tieredQuest("capture_shiny", "POKEMON", "CAPTURE_SHINY_ANY",
            "cobblemon:master_ball", null, "Shiny Hunter", "Capture des Pokémon shiny.",
            tiers(500,1, 1500,5, 5000,25, 10000,50, 25000,100)));
        cfg.quests.add(tieredQuest("battle_win", "POKEMON", "BATTLE_WIN_ANY",
            "minecraft:iron_sword", null, "Dresseur", "Remporte des combats Pokémon.",
            tiers(200,10, 500,50, 1000,100, 2500,250, 5000,500, 10000,1000)));
        cfg.quests.add(tieredQuest("fish_pokemon", "POKEMON", "FISH_POKEMON_ANY",
            "minecraft:fishing_rod", null, "Pêcheur", "Pêche des Pokémon.",
            tiers(100,10, 250,25, 500,50, 1000,100, 2500,250, 5000,500)));
        cfg.quests.add(tieredQuest("pokedex", "POKEMON", "POKEDEX_CAUGHT",
            "minecraft:knowledge_book", null, "Pokédex", "Complète ton Pokédex.",
            tiers(500,50, 1200,100, 3000,250, 7000,500, 15000,1000)));
        cfg.quests.add(tieredQuest("harvest_apricorns", "FARM", "HARVEST_ITEM",
            "cobblemon:yellow_apricorn", "noigrume", "Noigrumier", "Récolte des noigrumes.",
            tiers(200,64, 500,128, 1000,320, 2500,640, 5000,1280, 10000,2560)));
        cfg.quests.add(tieredQuest("mine_diamond", "FARM", "MINE_ORE",
            "minecraft:diamond", "diamond", "Mineur Diamant", "Mine des diamants.",
            tiers(500,64, 1500,128, 2500,256, 5000,512, 10000,1024)));
        cfg.quests.add(tieredQuest("mine_iron", "FARM", "MINE_ORE",
            "minecraft:iron_ingot", "iron", "Mineur Fer", "Mine du fer.",
            tiers(200,64, 500,128, 1000,256, 2000,512, 5000,1024)));
        cfg.quests.add(tieredQuest("shop_sell", "SHOP", "SHOP_SELL_ANY",
            "cobblemon:relic_coin_pouch", null, "Marchand", "Vends des objets au shop.",
            tiers(200,50, 500,100, 1000,250, 2500,500, 5000,1000, 10000,5000)));
        cfg.quests.add(tieredQuest("shop_buy", "SHOP", "SHOP_BUY_ANY",
            "cobblemon:relic_coin_pouch", null, "Acheteur", "Achète des objets au shop.",
            tiers(200,50, 500,100, 1500,500, 5000,1000, 10000,2500)));

        QuestConfig.TierDropsConfig drops = new QuestConfig.TierDropsConfig();
        drops.enabled           = true;
        drops.rollsPerTier      = 1;
        drops.dropOnGroundIfFull = true;
        drops.notifyPlayer      = true;
        drops.drops             = new ArrayList<>();
        drops.drops.add(drop("cobblemon:rare_candy", 100.0, 1, 3));
        drops.drops.add(drop("cobblemon:booster_pack", 25.0, 1, 2));
        drops.drops.add(drop("cobblemon:mega_bracelet", 1.5, 1, 1));
        cfg.tierDrops = drops;
        return cfg;
    }

    private static QuestConfig.QuestCategoryConfig cat(String name, String display, String icon)
    {
        QuestConfig.QuestCategoryConfig c = new QuestConfig.QuestCategoryConfig();

        c.name = name;
        c.displayName = display;
        c.iconItemId = icon;
        return c;
    }

    private static QuestConfig.QuestDefConfig tieredQuest(String id, String category, String type, String icon, String target, String title, String desc, int[][] tiers)
    {
        QuestConfig.QuestDefConfig q = new QuestConfig.QuestDefConfig();

        q.id = id; q.category = category; q.type = type;
        q.iconItemId = icon; q.target = target;
        q.title = title; q.description = desc;
        q.tiers = new ArrayList<>();
        for (int[] t : tiers) {
            QuestConfig.QuestTierConfig tc = new QuestConfig.QuestTierConfig();

            tc.reward = (double) t[0]; tc.goal = t[1];
            q.tiers.add(tc);
        }
        return q;
    }

    private static int[][] tiers(int... values)
    {
        int[][] result = new int[values.length / 2][2];

        for (int i = 0; i < values.length / 2; i++) {
            result[i][0] = values[i * 2]; // reward
            result[i][1] = values[i * 2 + 1]; // goal
        }
        return result;
    }

    private static QuestConfig.TierDropEntryConfig drop(String itemId, double chance, int min, int max)
    {
        QuestConfig.TierDropEntryConfig d = new QuestConfig.TierDropEntryConfig();

        d.itemId = itemId; d.chance = chance; d.min = min; d.max = max;
        return d;
    }

    private static double normalizeChance(Double raw)
    {
        if (raw == null || raw <= 0)
            return 0;
        return Math.min(1.0, raw > 1.0 ? raw / 100.0 : raw);
    }

    private static Item resolveItem(String itemId)
    {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null)
            return null;

        Item item = Registries.ITEM.get(id);
        return (item == null || item == Items.AIR) ? null : item;
    }

    public static String formatMoney(double value)
    {
        if (value == (long) value)
            return Long.toString((long) value);
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String normalizeCategory(String raw)
    {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeSpeciesKey(String raw)
    {
        if (raw == null)
            return "";
        return raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static String normalizeOreKey(String raw)
    {
        if (raw == null)
            return "";
        return raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static String normalizeItemId(String raw)
    {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeItemTarget(String raw)
    {
        if (raw == null)
            return "";

        String s = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (s.endsWith("s") && s.length() > 1)
            s = s.substring(0, s.length() - 1);
        if (s.equals("noigrume") || s.equals("noixgrume") || s.equals("apricorn") || s.equals("apricorne"))
            return "*apricorn*";
        return s;
    }

    private static boolean targetMatchesItem(String target, String itemId)
    {
        if (itemId == null || itemId.isBlank())
            return false;
        if (target == null || target.isBlank() || target.equals("*") || target.equals("any"))
            return true;

        String path = itemId;
        int idx = itemId.indexOf(':');
        if (idx >= 0)
            path = itemId.substring(idx + 1);
        if (target.contains("*")) {
            String regex = wildcardToRegex(target);
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE);
            return p.matcher(target.contains(":") ? itemId : path).matches();
        }
        return target.contains(":") ? itemId.equalsIgnoreCase(target) : path.equalsIgnoreCase(target);
    }

    private static String wildcardToRegex(String w)
    {
        StringBuilder sb = new StringBuilder("^");
        int last = 0;

        for (int i = 0; i < w.length(); i++) {
            if (w.charAt(i) != '*')
                continue;
            if (last < i)
                sb.append(java.util.regex.Pattern.quote(w.substring(last, i)));
            sb.append(".*");
            last = i + 1;
        }
        if (last < w.length())
            sb.append(java.util.regex.Pattern.quote(w.substring(last)));
        return sb.append('$').toString();
    }

    private static String prettySpeciesName(String raw)
    {
        if (raw == null || raw.isBlank())
            return "?";

        String key = normalizeSpeciesKey(raw);
        if (key.equals("ho_oh"))
            return "Ho-Oh";
        if (key.equals("mr_mime"))
            return "Mr. Mime";
        if (key.equals("porygon_z"))
            return "Porygon-Z";

        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank())
                continue;
            if (!sb.isEmpty())
                sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.isEmpty() ? raw : sb.toString();
    }

    private static String extractSpeciesKey(Object pokemon)
    {
        if (pokemon == null)
            return "";

        Object species = callNoArg(pokemon, "getSpecies");
        if (species != null) {
            Object rid = callNoArg(species, "getResourceIdentifier");
            if (rid instanceof Identifier id)
                return id.getPath();
            if (rid != null) {
                try {
                    return Identifier.of(rid.toString()).getPath();
                } catch (Throwable ignored) {}
            }

            Object name = callNoArg(species, "getName");
            if (name != null)
                return name.toString();
        }

        Object display = callNoArg(pokemon, "getDisplayName");
        if (display instanceof Text t)
            return t.getString();
        return display != null ? display.toString() : "";
    }

    private static boolean isPokemonShiny(Object pokemon)
    {
        if (pokemon == null)
            return false;
        for (String m : new String[]{"isShiny","getShiny","getIsShiny","shiny"}) {
            Object v = callNoArg(pokemon, m);

            if (v instanceof Boolean b)
                return b;
            if (v != null && "true".equalsIgnoreCase(v.toString().trim()))
                return true;
        }
        return false;
    }

    private static Object callNoArg(Object target, String method) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
